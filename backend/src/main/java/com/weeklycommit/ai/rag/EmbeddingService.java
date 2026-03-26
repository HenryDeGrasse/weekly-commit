package com.weeklycommit.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Generates vector embeddings via the OpenRouter embeddings endpoint.
 *
 * <p>
 * Used by the RAG pipeline to convert text chunks and query strings into dense
 * vectors for Pinecone storage and retrieval.
 *
 * <p>
 * Gracefully degrades: returns an empty {@code float[0]} on any error so
 * callers can decide how to handle absent embeddings without propagating
 * exceptions.
 */
@Service
public class EmbeddingService {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
	private static final float[] EMPTY = new float[0];

	private final String apiKey;
	private final String baseUrl;
	private final String model;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	/**
	 * Spring-managed constructor. {@link HttpClient} is created internally with a
	 * 10-second connect timeout.
	 */
	public EmbeddingService(@Value("${ai.openrouter.api-key:}") String apiKey,
			@Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
			@Value("${ai.embedding.model:openai/text-embedding-3-small}") String model, ObjectMapper objectMapper) {
		this(apiKey, baseUrl, model, objectMapper,
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
	}

	/**
	 * Package-private constructor for unit tests — allows injecting a mock
	 * {@link HttpClient}.
	 */
	EmbeddingService(String apiKey, String baseUrl, String model, ObjectMapper objectMapper, HttpClient httpClient) {
		this.apiKey = apiKey;
		this.baseUrl = baseUrl;
		this.model = model;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;

		if (apiKey == null || apiKey.isBlank()) {
			log.warn("EmbeddingService: API key is blank — embeddings will be unavailable");
		} else {
			log.info("EmbeddingService initialized: model={}", model);
		}
	}

	// ── Public API ───────────────────────────────────────────────────────

	/**
	 * Returns {@code true} when the API key is configured and non-blank. Callers
	 * should check this before invoking {@link #embed(String)} to avoid unnecessary
	 * processing.
	 */
	public boolean isAvailable() {
		return apiKey != null && !apiKey.isBlank();
	}

	/**
	 * Generates a vector embedding for the given {@code text}.
	 *
	 * <p>
	 * POSTs to {@code {baseUrl}/embeddings} with body {@code {"model": "...",
	 * "input": ["text"]}} and extracts {@code data[0].embedding} as a
	 * {@code float[]}.
	 *
	 * @param text
	 *            the text to embed; {@code null} or blank returns {@code float[0]}
	 * @return the embedding vector, or {@code float[0]} on any error
	 */
	public float[] embed(String text) {
		if (!isAvailable()) {
			log.debug("EmbeddingService unavailable — API key is blank");
			return EMPTY;
		}
		if (text == null || text.isBlank()) {
			return EMPTY;
		}
		try {
			ObjectNode body = objectMapper.createObjectNode();
			body.put("model", model);
			ArrayNode inputArray = body.putArray("input");
			inputArray.add(text);
			String json = objectMapper.writeValueAsString(body);

			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/embeddings"))
					.header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
					.header("HTTP-Referer", "https://weeklycommit.dev").header("X-Title", "Weekly Commit")
					.timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofString(json)).build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				log.warn("Embedding API returned HTTP {}: {}", response.statusCode(), response.body());
				return EMPTY;
			}

			return parseEmbedding(response.body());
		} catch (Exception e) {
			log.warn("Embedding request failed: {}", e.getMessage());
			return EMPTY;
		}
	}

	// ── Private helpers ──────────────────────────────────────────────────

	private float[] parseEmbedding(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode data = root.get("data");
			if (data == null || data.isEmpty()) {
				log.warn("Embedding response missing 'data' field");
				return EMPTY;
			}
			JsonNode embeddingNode = data.get(0).get("embedding");
			if (embeddingNode == null || !embeddingNode.isArray()) {
				log.warn("Embedding response missing 'embedding' in data[0]");
				return EMPTY;
			}
			float[] result = new float[embeddingNode.size()];
			for (int i = 0; i < embeddingNode.size(); i++) {
				result[i] = (float) embeddingNode.get(i).asDouble();
			}
			return result;
		} catch (Exception e) {
			log.warn("Failed to parse embedding response: {}", e.getMessage());
			return EMPTY;
		}
	}
}
