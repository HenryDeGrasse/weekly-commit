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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Thin HTTP client for the Pinecone vector database.
 *
 * <p>
 * Handles control-plane operations (index creation/lookup via
 * {@code api.pinecone.io}) and data-plane operations (upsert, query, delete)
 * against the resolved index host.
 *
 * <p>
 * The index host is resolved lazily on the first data-plane call via
 * {@link #ensureIndexExists()}. All methods gracefully degrade on errors
 * (catch, log, return empty results) so the rest of the RAG pipeline is never
 * blocked by Pinecone unavailability.
 */
@Service
public class PineconeClient {

	private static final Logger log = LoggerFactory.getLogger(PineconeClient.class);
	private static final String CONTROL_PLANE_URL = "https://api.pinecone.io";

	private final String apiKey;
	private final String indexName;
	private final String environment;
	private final int dimension;
	private final String voyageIndexName;
	private final int voyageDimension;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	/** Resolved at runtime by {@link #ensureIndexExists()}. */
	private volatile String indexHost;

	/** Resolved at runtime by {@link #ensureVoyageIndexExists()}. */
	private volatile String voyageIndexHost;

	// ── Inner records ────────────────────────────────────────────────────

	/**
	 * A single vector to be stored in Pinecone.
	 *
	 * <p>
	 * {@code sparseValues} is optional: when non-null, the vector is stored as a
	 * hybrid dense+sparse vector for Pinecone's native hybrid search.
	 */
	public record PineconeVector(String id, float[] values, Map<String, Object> metadata,
			Map<Integer, Float> sparseValues) {

		/**
		 * Backward-compatible 3-arg constructor — {@code sparseValues} defaults to
		 * {@code null} (dense-only).
		 */
		public PineconeVector(String id, float[] values, Map<String, Object> metadata) {
			this(id, values, metadata, null);
		}
	}

	/** A single match returned from a Pinecone query. */
	public record PineconeMatch(String id, double score, Map<String, Object> metadata) {
	}

	// ── Constructors ─────────────────────────────────────────────────────

	/**
	 * Spring-managed constructor. {@link HttpClient} is created internally with a
	 * 10-second connect timeout.
	 */
	@Autowired
	public PineconeClient(@Value("${ai.pinecone.api-key:}") String apiKey,
			@Value("${ai.pinecone.index-name:weekly-commit}") String indexName,
			@Value("${ai.pinecone.environment:us-east-1}") String environment,
			@Value("${ai.embedding.dimensions:1536}") int dimension,
			@Value("${ai.pinecone.voyage-index-name:weekly-commit-voyage}") String voyageIndexName,
			@Value("${ai.pinecone.voyage-dimensions:1024}") int voyageDimension,
			ObjectMapper objectMapper) {
		this(apiKey, indexName, environment, dimension, voyageIndexName, voyageDimension, objectMapper,
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
	}

	/**
	 * Package-private constructor for unit tests — allows injecting a mock
	 * {@link HttpClient}. Uses default dimensions (1536 primary, 1024 voyage).
	 */
	PineconeClient(String apiKey, String indexName, String environment, ObjectMapper objectMapper,
			HttpClient httpClient) {
		this(apiKey, indexName, environment, 1536, "weekly-commit-voyage", 1024, objectMapper, httpClient);
	}

	/**
	 * Full constructor used by the Spring-managed constructor and tests that need
	 * custom dimensions.
	 */
	PineconeClient(String apiKey, String indexName, String environment, int dimension, String voyageIndexName,
			int voyageDimension, ObjectMapper objectMapper, HttpClient httpClient) {
		this.apiKey = apiKey;
		this.indexName = indexName;
		this.environment = environment;
		this.dimension = dimension;
		this.voyageIndexName = voyageIndexName;
		this.voyageDimension = voyageDimension;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;

		if (apiKey == null || apiKey.isBlank()) {
			log.warn("PineconeClient: API key is blank — vector operations will be no-ops");
		} else {
			log.info("PineconeClient initialized: indexName={} ({}d), voyageIndex={} ({}d), environment={}",
					indexName, dimension, voyageIndexName, voyageDimension, environment);
		}
	}

	// ── Public API ───────────────────────────────────────────────────────

	/**
	 * Returns {@code true} when the API key is configured and non-blank.
	 */
	public boolean isAvailable() {
		return apiKey != null && !apiKey.isBlank();
	}

	/**
	 * Lazily ensures the primary Pinecone index exists and populates
	 * {@link #indexHost}.
	 *
	 * <p>
	 * On first call: issues GET {@code /indexes/{indexName}} to the Pinecone
	 * control plane. If the index does not exist (HTTP 404), creates a serverless
	 * index at the configured {@link #dimension}. Subsequent calls are no-ops once
	 * {@code indexHost} has been resolved.
	 */
	public void ensureIndexExists() {
		if (!isAvailable() || indexHost != null) {
			return;
		}
		try {
			indexHost = resolveOrCreateIndex(indexName, dimension);
		} catch (Exception e) {
			log.warn("PineconeClient: ensureIndexExists failed: {}", e.getMessage());
		}
	}

	/**
	 * Lazily ensures the Voyage AI secondary index exists and populates
	 * {@link #voyageIndexHost}.
	 *
	 * <p>
	 * Creates a serverless index at {@link #voyageDimension} (default 1024) if it
	 * does not already exist.
	 */
	public void ensureVoyageIndexExists() {
		if (!isAvailable() || voyageIndexHost != null) {
			return;
		}
		try {
			voyageIndexHost = resolveOrCreateIndex(voyageIndexName, voyageDimension);
		} catch (Exception e) {
			log.warn("PineconeClient: ensureVoyageIndexExists failed: {}", e.getMessage());
		}
	}

	/**
	 * Upserts vectors into the given namespace.
	 *
	 * @param namespace
	 *            Pinecone namespace (use empty string for the default namespace)
	 * @param vectors
	 *            vectors to upsert
	 */
	public void upsert(String namespace, List<PineconeVector> vectors) {
		if (!isAvailable() || vectors == null || vectors.isEmpty()) {
			return;
		}
		ensureIndexExists();
		if (indexHost == null) {
			log.warn("PineconeClient: upsert skipped — index host not resolved");
			return;
		}
		upsertToHost(indexHost, namespace, vectors);
	}

	/**
	 * Queries the index for the nearest neighbours of {@code vector} (dense-only).
	 *
	 * @param namespace
	 *            Pinecone namespace
	 * @param vector
	 *            query vector
	 * @param topK
	 *            maximum number of results
	 * @param filter
	 *            optional metadata filter (may be null or empty)
	 * @return list of matches, empty on error
	 */
	public List<PineconeMatch> query(String namespace, float[] vector, int topK, Map<String, Object> filter) {
		return executeQuery(namespace, vector, topK, filter, null);
	}

	/**
	 * Queries the index using hybrid dense+sparse search.
	 *
	 * <p>
	 * When {@code sparseVector} is non-null and non-empty, both the dense vector
	 * and sparse vector are sent to Pinecone for hybrid retrieval. When
	 * {@code sparseVector} is {@code null} or empty, this behaves identically to
	 * the dense-only overload.
	 *
	 * @param namespace
	 *            Pinecone namespace
	 * @param vector
	 *            dense query vector
	 * @param topK
	 *            maximum number of results
	 * @param filter
	 *            optional metadata filter (may be null or empty)
	 * @param sparseVector
	 *            optional sparse vector (token index → weight); may be null
	 * @return list of matches, empty on error
	 */
	public List<PineconeMatch> query(String namespace, float[] vector, int topK, Map<String, Object> filter,
			Map<Integer, Float> sparseVector) {
		return executeQuery(namespace, vector, topK, filter, sparseVector);
	}

	/** Shared implementation for both {@code query()} overloads. */
	private List<PineconeMatch> executeQuery(String namespace, float[] vector, int topK, Map<String, Object> filter,
			Map<Integer, Float> sparseVector) {
		if (!isAvailable() || vector == null || vector.length == 0) {
			return Collections.emptyList();
		}
		ensureIndexExists();
		if (indexHost == null) {
			log.warn("PineconeClient: query skipped — index host not resolved");
			return Collections.emptyList();
		}
		return executeQueryOnHost(indexHost, namespace, vector, topK, filter, sparseVector);
	}

	/**
	 * Upserts vectors into the Voyage AI secondary index (1024-d).
	 *
	 * @param namespace
	 *            Pinecone namespace (same org-based namespace as primary)
	 * @param vectors
	 *            dense vectors — must be 1024-d voyage-4 embeddings
	 */
	public void upsertVoyage(String namespace, List<PineconeVector> vectors) {
		if (!isAvailable() || vectors == null || vectors.isEmpty()) {
			return;
		}
		ensureVoyageIndexExists();
		if (voyageIndexHost == null) {
			log.warn("PineconeClient: voyage upsert skipped — index host not resolved");
			return;
		}
		upsertToHost(voyageIndexHost, namespace, vectors);
	}

	/**
	 * Queries the Voyage AI secondary index (1024-d) for the nearest neighbours of
	 * {@code vector}.
	 *
	 * @param namespace
	 *            Pinecone namespace
	 * @param vector
	 *            1024-d voyage-4 query vector
	 * @param topK
	 *            maximum number of results
	 * @param filter
	 *            optional metadata filter (may be null or empty)
	 * @return list of matches, empty on error
	 */
	public List<PineconeMatch> queryVoyage(String namespace, float[] vector, int topK,
			Map<String, Object> filter) {
		if (!isAvailable() || vector == null || vector.length == 0) {
			return Collections.emptyList();
		}
		ensureVoyageIndexExists();
		if (voyageIndexHost == null) {
			log.warn("PineconeClient: voyage query skipped — index host not resolved");
			return Collections.emptyList();
		}
		return executeQueryOnHost(voyageIndexHost, namespace, vector, topK, filter, null);
	}

	/** Shared upsert logic for any resolved index host. */
	private void upsertToHost(String host, String namespace, List<PineconeVector> vectors) {
		try {
			ObjectNode body = objectMapper.createObjectNode();
			body.put("namespace", namespace != null ? namespace : "");
			ArrayNode vectorsNode = body.putArray("vectors");
			for (PineconeVector v : vectors) {
				ObjectNode vNode = vectorsNode.addObject();
				vNode.put("id", v.id());
				ArrayNode valuesNode = vNode.putArray("values");
				for (float f : v.values()) {
					valuesNode.add(f);
				}
				if (v.metadata() != null && !v.metadata().isEmpty()) {
					vNode.set("metadata", objectMapper.valueToTree(v.metadata()));
				}
				if (v.sparseValues() != null && !v.sparseValues().isEmpty()) {
					vNode.set("sparse_values", buildSparseValuesNode(v.sparseValues()));
				}
			}
			String json = objectMapper.writeValueAsString(body);
			HttpRequest req = hostRequest(host, "POST", "/vectors/upsert", json);
			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				log.warn("PineconeClient: upsert to {} returned {}: {}", host, resp.statusCode(), resp.body());
			} else {
				log.debug("PineconeClient: upserted {} vectors to namespace='{}' on {}", vectors.size(), namespace,
						host);
			}
		} catch (Exception e) {
			log.warn("PineconeClient: upsert to {} failed: {}", host, e.getMessage());
		}
	}

	/** Shared query logic for any resolved index host. */
	private List<PineconeMatch> executeQueryOnHost(String host, String namespace, float[] vector, int topK,
			Map<String, Object> filter, Map<Integer, Float> sparseVector) {
		try {
			ObjectNode body = objectMapper.createObjectNode();
			body.put("namespace", namespace != null ? namespace : "");
			body.put("topK", topK);
			body.put("includeMetadata", true);
			ArrayNode vectorNode = body.putArray("vector");
			for (float f : vector) {
				vectorNode.add(f);
			}
			if (filter != null && !filter.isEmpty()) {
				body.set("filter", objectMapper.valueToTree(filter));
			}
			if (sparseVector != null && !sparseVector.isEmpty()) {
				body.set("sparse_vector", buildSparseValuesNode(sparseVector));
			}
			String json = objectMapper.writeValueAsString(body);
			HttpRequest req = hostRequest(host, "POST", "/query", json);
			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				log.warn("PineconeClient: query on {} returned {}: {}", host, resp.statusCode(), resp.body());
				return Collections.emptyList();
			}
			return parseMatches(resp.body());
		} catch (Exception e) {
			log.warn("PineconeClient: query on {} failed: {}", host, e.getMessage());
			return Collections.emptyList();
		}
	}

	/**
	 * Deletes vectors by ID from the given namespace.
	 *
	 * @param namespace
	 *            Pinecone namespace
	 * @param ids
	 *            IDs to delete
	 */
	public void deleteByIds(String namespace, List<String> ids) {
		if (!isAvailable() || ids == null || ids.isEmpty()) {
			return;
		}
		ensureIndexExists();
		if (indexHost == null) {
			log.warn("PineconeClient: deleteByIds skipped — index host not resolved");
			return;
		}
		try {
			ObjectNode body = objectMapper.createObjectNode();
			body.put("namespace", namespace != null ? namespace : "");
			ArrayNode idsNode = body.putArray("ids");
			ids.forEach(idsNode::add);
			String json = objectMapper.writeValueAsString(body);
			HttpRequest req = hostRequest(indexHost, "POST", "/vectors/delete", json);
			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				log.warn("PineconeClient: deleteByIds returned {}: {}", resp.statusCode(), resp.body());
			} else {
				log.debug("PineconeClient: deleted {} vectors from namespace='{}'", ids.size(), namespace);
			}
		} catch (Exception e) {
			log.warn("PineconeClient: deleteByIds failed: {}", e.getMessage());
		}
	}

	// ── Private helpers ──────────────────────────────────────────────────

	/**
	 * Resolves the host for {@code name}, creating the index if it does not exist.
	 * Returns the resolved host URL, or throws on unrecoverable failure.
	 */
	private String resolveOrCreateIndex(String name, int dims) throws Exception {
		HttpRequest getReq = controlRequest("GET", "/indexes/" + name, null);
		HttpResponse<String> getResp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());

		if (getResp.statusCode() == 200) {
			String host = parseIndexHost(getResp.body());
			log.info("PineconeClient: resolved index '{}' host={}", name, host);
			return host;
		}

		if (getResp.statusCode() == 404) {
			log.info("PineconeClient: index '{}' not found — creating serverless index ({}d)", name, dims);
			createIndex(name, dims);
			HttpResponse<String> refetchResp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
			if (refetchResp.statusCode() == 200) {
				String host = parseIndexHost(refetchResp.body());
				log.info("PineconeClient: index '{}' created, host={}", name, host);
				return host;
			} else {
				log.warn("PineconeClient: index '{}' creation may still be pending (status={})", name,
						refetchResp.statusCode());
				return null;
			}
		}

		log.warn("PineconeClient: unexpected status {} when checking index '{}'", getResp.statusCode(), name);
		return null;
	}

	private void createIndex(String name, int dims) throws Exception {
		ObjectNode body = objectMapper.createObjectNode();
		body.put("name", name);
		body.put("dimension", dims);
		body.put("metric", "dotproduct");
		ObjectNode spec = body.putObject("spec");
		ObjectNode serverless = spec.putObject("serverless");
		serverless.put("cloud", "aws");
		serverless.put("region", environment);
		String json = objectMapper.writeValueAsString(body);
		HttpRequest req = controlRequest("POST", "/indexes", json);
		HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 201 && resp.statusCode() != 200) {
			log.warn("PineconeClient: createIndex '{}' returned {}: {}", name, resp.statusCode(), resp.body());
		}
	}

	private String parseIndexHost(String responseBody) throws Exception {
		JsonNode root = objectMapper.readTree(responseBody);
		String host = root.path("host").asText(null);
		if (host == null || host.isBlank()) {
			throw new IllegalStateException("Pinecone index response missing 'host' field");
		}
		// Ensure scheme is present
		return host.startsWith("http") ? host : "https://" + host;
	}

	private List<PineconeMatch> parseMatches(String responseBody) throws Exception {
		JsonNode root = objectMapper.readTree(responseBody);
		JsonNode matches = root.get("matches");
		if (matches == null || matches.isEmpty()) {
			return Collections.emptyList();
		}
		List<PineconeMatch> result = new ArrayList<>(matches.size());
		for (JsonNode m : matches) {
			String id = m.path("id").asText();
			double score = m.path("score").asDouble();
			Map<String, Object> metadata = Collections.emptyMap();
			if (m.has("metadata")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> meta = objectMapper.treeToValue(m.get("metadata"), Map.class);
				metadata = meta;
			}
			result.add(new PineconeMatch(id, score, metadata));
		}
		return result;
	}

	private HttpRequest controlRequest(String method, String path, String jsonBody) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(CONTROL_PLANE_URL + path))
				.header("Api-Key", apiKey).header("Content-Type", "application/json").timeout(Duration.ofSeconds(30));
		if ("POST".equals(method) && jsonBody != null) {
			builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
		} else {
			builder.method(method, HttpRequest.BodyPublishers.noBody());
		}
		return builder.build();
	}

	private HttpRequest hostRequest(String host, String method, String path, String jsonBody) throws Exception {
		String hostUrl = host.startsWith("http") ? host : "https://" + host;
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(hostUrl + path)).header("Api-Key", apiKey)
				.header("Content-Type", "application/json").timeout(Duration.ofSeconds(30));
		if ("POST".equals(method) && jsonBody != null) {
			builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
		} else {
			builder.method(method, HttpRequest.BodyPublishers.noBody());
		}
		return builder.build();
	}

	/**
	 * Serialises a sparse vector ({@code index → weight}) into the Pinecone JSON
	 * format: {@code {"indices":[...], "values":[...]}}.
	 *
	 * <p>
	 * Entries are sorted by index for deterministic output.
	 */
	private com.fasterxml.jackson.databind.node.ObjectNode buildSparseValuesNode(Map<Integer, Float> sparseValues) {
		com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
		ArrayNode indicesNode = node.putArray("indices");
		ArrayNode valuesNode = node.putArray("values");
		sparseValues.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
			indicesNode.add(e.getKey());
			valuesNode.add(e.getValue());
		});
		return node;
	}

	/** Exposed for testing — allows pre-seeding the resolved index host. */
	void setIndexHostForTesting(String host) {
		this.indexHost = host;
	}
}
