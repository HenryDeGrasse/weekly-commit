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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Generates vector embeddings via OpenRouter (OpenAI models) or directly via
 * the Voyage AI API (voyage-* models).
 *
 * <p>
 * Model routing: any model whose name starts with {@code "voyage"} is sent to
 * {@code https://api.voyageai.com/v1} with the Voyage API key. All other models
 * are sent to the configured OpenRouter base URL. The Voyage AI embeddings
 * endpoint uses the same OpenAI-compatible request/response format, so no
 * separate parsing logic is required.
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
	private final String voyageApiKey;
	private final String voyageBaseUrl;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	/** Per-model call counters for observability. */
	private final ConcurrentHashMap<String, AtomicLong> modelCallCounters = new ConcurrentHashMap<>();

	/**
	 * Spring-managed constructor. {@link HttpClient} is created internally with a
	 * 10-second connect timeout.
	 */
	@Autowired
	public EmbeddingService(@Value("${ai.openrouter.api-key:}") String apiKey,
			@Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
			@Value("${ai.embedding.model:openai/text-embedding-3-small}") String model,
			@Value("${ai.voyage.api-key:}") String voyageApiKey,
			@Value("${ai.voyage.base-url:https://api.voyageai.com/v1}") String voyageBaseUrl,
			ObjectMapper objectMapper) {
		this(apiKey, baseUrl, model, voyageApiKey, voyageBaseUrl, objectMapper,
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
	}

	/**
	 * Package-private constructor for unit tests — allows injecting a mock
	 * {@link HttpClient}.
	 */
	EmbeddingService(String apiKey, String baseUrl, String model, String voyageApiKey, String voyageBaseUrl,
			ObjectMapper objectMapper, HttpClient httpClient) {
		this.apiKey = apiKey;
		this.baseUrl = baseUrl;
		this.model = model;
		this.voyageApiKey = voyageApiKey;
		this.voyageBaseUrl = voyageBaseUrl;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;

		if (apiKey == null || apiKey.isBlank()) {
			log.warn("EmbeddingService: OpenRouter API key is blank — OpenAI embeddings will be unavailable");
		}
		if (voyageApiKey == null || voyageApiKey.isBlank()) {
			log.info("EmbeddingService: Voyage API key not configured — voyage-* models will be unavailable");
		} else {
			log.info("EmbeddingService initialized: defaultModel={}, voyageAvailable=true", model);
		}
	}

	// ── Public API ───────────────────────────────────────────────────────

	/**
	 * Returns {@code true} when the OpenRouter API key is configured and non-blank.
	 * Callers should check this before invoking {@link #embed(String)} to avoid
	 * unnecessary processing. Voyage-model availability is checked separately in
	 * {@link #isVoyageAvailable()}.
	 */
	public boolean isAvailable() {
		// Check the key that matches the default model
		if (model != null && model.startsWith("voyage")) {
			return isVoyageAvailable();
		}
		return apiKey != null && !apiKey.isBlank();
	}

	/**
	 * Returns {@code true} when the Voyage AI API key is configured and non-blank.
	 */
	public boolean isVoyageAvailable() {
		return voyageApiKey != null && !voyageApiKey.isBlank();
	}

	/**
	 * Generates a vector embedding for the given {@code text} using the configured
	 * default model.
	 *
	 * @param text
	 *            the text to embed; {@code null} or blank returns {@code float[0]}
	 * @return the embedding vector, or {@code float[0]} on any error
	 */
	public float[] embed(String text) {
		return embed(text, this.model);
	}

	/**
	 * Generates a vector embedding for the given {@code text} using
	 * {@code modelOverride} instead of the configured default model.
	 *
	 * <p>
	 * <strong>Intended for offline evaluation only</strong> (e.g.
	 * {@code AbComparisonRunner}). Do <em>not</em> use this in the live
	 * {@code SemanticQueryService} path: the Pinecone index stores 1536-d vectors
	 * (text-embedding-3-small) and querying with 3072-d vectors
	 * (text-embedding-3-large) would cause a dimension mismatch.
	 *
	 * @param text
	 *            the text to embed; {@code null} or blank returns {@code float[0]}
	 * @param modelOverride
	 *            the embedding model to use for this call
	 * @return the embedding vector, or {@code float[0]} on any error
	 */
	public float[] embed(String text, String modelOverride) {
		String effectiveModel = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : this.model;
		boolean isVoyageModel = effectiveModel.startsWith("voyage");

		if (isVoyageModel) {
			if (!isVoyageAvailable()) {
				log.debug("EmbeddingService: voyage model requested but Voyage API key is blank");
				return EMPTY;
			}
		} else {
			if (!isAvailable()) {
				log.debug("EmbeddingService unavailable — OpenRouter API key is blank");
				return EMPTY;
			}
		}
		if (text == null || text.isBlank()) {
			return EMPTY;
		}

		// Route voyage-* models to api.voyageai.com; all others go through OpenRouter
		String effectiveApiKey = isVoyageModel ? voyageApiKey : apiKey;
		String effectiveBaseUrl = isVoyageModel ? voyageBaseUrl : baseUrl;

		try {
			ObjectNode body = objectMapper.createObjectNode();
			body.put("model", effectiveModel);
			ArrayNode inputArray = body.putArray("input");
			inputArray.add(text);
			String json = objectMapper.writeValueAsString(body);

			HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(effectiveBaseUrl + "/embeddings"))
					.header("Authorization", "Bearer " + effectiveApiKey).header("Content-Type", "application/json")
					.timeout(Duration.ofSeconds(30));
			// OpenRouter wants attribution headers; Voyage AI ignores them (harmless to
			// omit)
			if (!isVoyageModel) {
				reqBuilder.header("HTTP-Referer", "https://weeklycommit.dev").header("X-Title", "Weekly Commit");
			}
			HttpRequest request = reqBuilder.POST(HttpRequest.BodyPublishers.ofString(json)).build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				log.warn("Embedding API returned HTTP {}: {}", response.statusCode(), response.body());
				return EMPTY;
			}

			float[] result = parseEmbedding(response.body());
			if (result.length > 0) {
				modelCallCounters.computeIfAbsent(effectiveModel, k -> new AtomicLong(0)).incrementAndGet();
			}
			return result;
		} catch (Exception e) {
			log.warn("Embedding request failed: {}", e.getMessage());
			return EMPTY;
		}
	}

	/**
	 * Returns the output vector dimension for a known embedding model.
	 *
	 * <ul>
	 * <li>{@code openai/text-embedding-3-small} → 1536</li>
	 * <li>{@code openai/text-embedding-3-large} → 3072</li>
	 * </ul>
	 *
	 * Returns 1536 for any unrecognised model (safe default matching the primary
	 * Pinecone index dimension).
	 *
	 * @param model
	 *            the embedding model identifier
	 * @return expected output dimension
	 */
	public int getDimensions(String model) {
		if (model == null) {
			return 1536;
		}
		return switch (model) {
			case "openai/text-embedding-3-large" -> 3072;
			case "voyage-4-large" -> 2048;
			case "voyage-4", "voyage-4-lite" -> 1024;
			default -> 1536;
		};
	}

	/**
	 * Returns the total successful call count for the given model, or {@code 0} if
	 * no calls have been made yet.
	 *
	 * @param model
	 *            the embedding model identifier
	 */
	public long getCallCount(String model) {
		AtomicLong counter = modelCallCounters.get(model);
		return counter != null ? counter.get() : 0L;
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
