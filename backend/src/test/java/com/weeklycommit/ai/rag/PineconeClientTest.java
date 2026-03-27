package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link PineconeClient}.
 *
 * <p>
 * Uses a mock {@link HttpClient} so no real HTTP calls are made. Verifies
 * request format, response parsing, graceful-degradation, and the lazy index
 * initialisation path.
 */
@ExtendWith(MockitoExtension.class)
class PineconeClientTest {

	private static final String API_KEY = "pc-test-key";
	private static final String INDEX_NAME = "weekly-commit-test";
	private static final String ENVIRONMENT = "us-east-1";
	private static final String INDEX_HOST = "weekly-commit-test-abc.svc.us-east-1.pinecone.io";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private HttpClient httpClient;

	@Mock
	@SuppressWarnings("unchecked")
	private HttpResponse<String> httpResponse;

	private PineconeClient client;

	@BeforeEach
	void setUp() {
		client = new PineconeClient(API_KEY, INDEX_NAME, ENVIRONMENT, objectMapper, httpClient);
		// Pre-seed index host so data-plane tests skip ensureIndexExists
		client.setIndexHostForTesting(INDEX_HOST);
	}

	// ── isAvailable ──────────────────────────────────────────────────────

	@Test
	void isAvailable_withKey_returnsTrue() {
		assertThat(client.isAvailable()).isTrue();
	}

	@Test
	void isAvailable_blankKey_returnsFalse() {
		PineconeClient noKey = new PineconeClient("", INDEX_NAME, ENVIRONMENT, objectMapper, httpClient);
		assertThat(noKey.isAvailable()).isFalse();
	}

	// ── blank API key — all methods are no-ops ────────────────────────────

	@Test
	void upsert_blankKey_doesNotCallHttp() {
		PineconeClient noKey = new PineconeClient("", INDEX_NAME, ENVIRONMENT, objectMapper, httpClient);
		noKey.upsert("ns", List.of(new PineconeClient.PineconeVector("v1", new float[]{0.1f}, Map.of())));
		verifyNoInteractions(httpClient);
	}

	@Test
	void query_blankKey_returnsEmptyList() {
		PineconeClient noKey = new PineconeClient("", INDEX_NAME, ENVIRONMENT, objectMapper, httpClient);
		List<PineconeClient.PineconeMatch> result = noKey.query("ns", new float[]{0.1f}, 5, null);
		assertThat(result).isEmpty();
		verifyNoInteractions(httpClient);
	}

	@Test
	void deleteByIds_blankKey_doesNotCallHttp() {
		PineconeClient noKey = new PineconeClient("", INDEX_NAME, ENVIRONMENT, objectMapper, httpClient);
		noKey.deleteByIds("ns", List.of("id1"));
		verifyNoInteractions(httpClient);
	}

	// ── upsert — request format ───────────────────────────────────────────

	@Test
	void upsert_buildsCorrectJsonBody() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		float[] values = {0.1f, 0.2f, 0.3f};
		PineconeClient.PineconeVector vec = new PineconeClient.PineconeVector("doc-1", values, Map.of("type", "plan"));
		client.upsert("test-ns", List.of(vec));

		JsonNode body = objectMapper.readTree(readRequestBody(captor.getValue()));
		assertThat(body.path("namespace").asText()).isEqualTo("test-ns");
		JsonNode vectors = body.path("vectors");
		assertThat(vectors).hasSize(1);
		assertThat(vectors.get(0).path("id").asText()).isEqualTo("doc-1");
		assertThat(vectors.get(0).path("values")).hasSize(3);
		assertThat(vectors.get(0).path("values").get(0).floatValue()).isEqualTo(0.1f);
		assertThat(vectors.get(0).path("metadata").path("type").asText()).isEqualTo("plan");
	}

	@Test
	void upsert_targetsCorrectEndpoint() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		client.upsert("ns", List.of(new PineconeClient.PineconeVector("v1", new float[]{0.1f}, Map.of())));

		HttpRequest req = captor.getValue();
		assertThat(req.uri().toString()).isEqualTo("https://" + INDEX_HOST + "/vectors/upsert");
		assertThat(req.method()).isEqualTo("POST");
		assertThat(req.headers().firstValue("Api-Key")).hasValue(API_KEY);
	}

	@Test
	void upsert_acceptsResolvedHostThatAlreadyIncludesScheme() throws Exception {
		client.setIndexHostForTesting("https://" + INDEX_HOST);
		when(httpResponse.statusCode()).thenReturn(200);
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		client.upsert("ns", List.of(new PineconeClient.PineconeVector("v1", new float[]{0.1f}, Map.of())));

		assertThat(captor.getValue().uri().toString()).isEqualTo("https://" + INDEX_HOST + "/vectors/upsert");
	}

	@Test
	void upsert_httpError_doesNotThrow() throws Exception {
		when(httpResponse.statusCode()).thenReturn(500);
		when(httpResponse.body()).thenReturn("{\"error\":\"server error\"}");
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		// Should not throw
		client.upsert("ns", List.of(new PineconeClient.PineconeVector("v1", new float[]{0.1f}, Map.of())));
	}

	@Test
	void upsert_networkException_doesNotThrow() throws Exception {
		when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("Connection refused"));

		// Should not throw
		client.upsert("ns", List.of(new PineconeClient.PineconeVector("v1", new float[]{0.1f}, Map.of())));
	}

	@Test
	void upsert_emptyVectorList_doesNotCallHttp() {
		client.upsert("ns", List.of());
		verifyNoInteractions(httpClient);
	}

	@Test
	void upsert_nullVectorList_doesNotCallHttp() {
		client.upsert("ns", null);
		verifyNoInteractions(httpClient);
	}

	// ── query — request format and response parsing ───────────────────────

	@Test
	void query_buildsCorrectJsonBody() throws Exception {
		String responseJson = buildQueryResponse(List.of("id-1", "id-2"), List.of(0.95, 0.80));
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(responseJson);
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		float[] vector = {0.1f, 0.2f};
		client.query("test-ns", vector, 5, Map.of("source", "plan"));

		JsonNode body = objectMapper.readTree(readRequestBody(captor.getValue()));
		assertThat(body.path("namespace").asText()).isEqualTo("test-ns");
		assertThat(body.path("topK").asInt()).isEqualTo(5);
		assertThat(body.path("includeMetadata").asBoolean()).isTrue();
		assertThat(body.path("vector")).hasSize(2);
		assertThat(body.path("filter").path("source").asText()).isEqualTo("plan");
	}

	@Test
	void query_targetsCorrectEndpoint() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(buildQueryResponse(List.of("id-1"), List.of(0.9)));
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		client.query("ns", new float[]{0.1f}, 1, null);

		HttpRequest req = captor.getValue();
		assertThat(req.uri().toString()).isEqualTo("https://" + INDEX_HOST + "/query");
		assertThat(req.method()).isEqualTo("POST");
		assertThat(req.headers().firstValue("Api-Key")).hasValue(API_KEY);
	}

	@Test
	void query_parsesMatchesCorrectly() throws Exception {
		String responseJson = buildQueryResponse(List.of("doc-a", "doc-b"), List.of(0.95, 0.80));
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(responseJson);
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		List<PineconeClient.PineconeMatch> matches = client.query("ns", new float[]{0.1f}, 10, null);

		assertThat(matches).hasSize(2);
		assertThat(matches.get(0).id()).isEqualTo("doc-a");
		assertThat(matches.get(0).score()).isEqualTo(0.95);
		assertThat(matches.get(1).id()).isEqualTo("doc-b");
		assertThat(matches.get(1).score()).isEqualTo(0.80);
	}

	@Test
	void query_emptyMatches_returnsEmptyList() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn("{\"matches\":[]}");
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		List<PineconeClient.PineconeMatch> matches = client.query("ns", new float[]{0.1f}, 10, null);

		assertThat(matches).isEmpty();
	}

	@Test
	void query_httpError_returnsEmptyList() throws Exception {
		when(httpResponse.statusCode()).thenReturn(500);
		when(httpResponse.body()).thenReturn("{\"error\":\"server error\"}");
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		List<PineconeClient.PineconeMatch> result = client.query("ns", new float[]{0.1f}, 5, null);

		assertThat(result).isEmpty();
	}

	@Test
	void query_networkException_returnsEmptyList() throws Exception {
		when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("Connection refused"));

		List<PineconeClient.PineconeMatch> result = client.query("ns", new float[]{0.1f}, 5, null);

		assertThat(result).isEmpty();
	}

	@Test
	void query_nullVector_returnsEmptyList() {
		List<PineconeClient.PineconeMatch> result = client.query("ns", null, 5, null);
		assertThat(result).isEmpty();
		verifyNoInteractions(httpClient);
	}

	@Test
	void query_noFilterInBody_whenFilterIsNull() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn("{\"matches\":[]}");
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		client.query("ns", new float[]{0.1f}, 5, null);

		JsonNode body = objectMapper.readTree(readRequestBody(captor.getValue()));
		assertThat(body.has("filter")).isFalse();
	}

	// ── deleteByIds ───────────────────────────────────────────────────────

	@Test
	void deleteByIds_buildsCorrectJsonBody() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		client.deleteByIds("test-ns", List.of("id-1", "id-2", "id-3"));

		JsonNode body = objectMapper.readTree(readRequestBody(captor.getValue()));
		assertThat(body.path("namespace").asText()).isEqualTo("test-ns");
		assertThat(body.path("ids")).hasSize(3);
		assertThat(body.path("ids").get(0).asText()).isEqualTo("id-1");
		assertThat(body.path("ids").get(2).asText()).isEqualTo("id-3");
	}

	@Test
	void deleteByIds_targetsCorrectEndpoint() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		client.deleteByIds("ns", List.of("id-1"));

		HttpRequest req = captor.getValue();
		assertThat(req.uri().toString()).isEqualTo("https://" + INDEX_HOST + "/vectors/delete");
		assertThat(req.method()).isEqualTo("POST");
	}

	@Test
	void deleteByIds_httpError_doesNotThrow() throws Exception {
		when(httpResponse.statusCode()).thenReturn(500);
		when(httpResponse.body()).thenReturn("{\"error\":\"server error\"}");
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		// Should not throw
		client.deleteByIds("ns", List.of("id-1"));
	}

	@Test
	void deleteByIds_emptyList_doesNotCallHttp() {
		client.deleteByIds("ns", List.of());
		verifyNoInteractions(httpClient);
	}

	// ── ensureIndexExists ─────────────────────────────────────────────────

	@Test
	void ensureIndexExists_indexAlreadyPresent_skipsHttpCall() {
		// indexHost pre-seeded in setUp — ensureIndexExists must not call http again
		client.ensureIndexExists();
		verifyNoInteractions(httpClient);
	}

	@Test
	void ensureIndexExists_blankKey_skipsHttpCall() {
		PineconeClient noKey = new PineconeClient("", INDEX_NAME, ENVIRONMENT, objectMapper, httpClient);
		noKey.ensureIndexExists();
		verifyNoInteractions(httpClient);
	}

	@Test
	@SuppressWarnings("unchecked")
	void ensureIndexExists_indexFound_storesHost() throws Exception {
		PineconeClient freshClient = new PineconeClient(API_KEY, INDEX_NAME, ENVIRONMENT, objectMapper, httpClient);
		String indexResponse = buildIndexResponse(INDEX_HOST);

		// Use a dedicated response mock to avoid shared-state interference
		HttpResponse<String> getResp = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
		when(getResp.statusCode()).thenReturn(200);
		when(getResp.body()).thenReturn(indexResponse);
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> getResp);

		freshClient.ensureIndexExists();

		// Index host resolved — upsert should make one more data-plane call (status
		// 200, body not read on success path)
		HttpResponse<String> upsertResp = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
		when(upsertResp.statusCode()).thenReturn(200);
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> upsertResp);
		freshClient.upsert("ns", List.of(new PineconeClient.PineconeVector("v1", new float[]{0.1f}, Map.of())));

		// 1 call for ensureIndexExists GET + 1 call for upsert
		verify(httpClient, times(2)).send(any(HttpRequest.class), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void ensureIndexExists_indexNotFound_createsIndex() throws Exception {
		PineconeClient freshClient = new PineconeClient(API_KEY, INDEX_NAME, ENVIRONMENT, objectMapper, httpClient);

		// Dedicated response mocks per HTTP call to avoid stub interference
		HttpResponse<String> notFoundResp = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
		when(notFoundResp.statusCode()).thenReturn(404);
		// body() not called on 404 path — no stub needed

		HttpResponse<String> createResp = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
		when(createResp.statusCode()).thenReturn(201);

		HttpResponse<String> refetchResp = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
		when(refetchResp.statusCode()).thenReturn(200);
		when(refetchResp.body()).thenReturn(buildIndexResponse(INDEX_HOST));

		final int[] callCount = {0};
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> {
			callCount[0]++;
			return switch (callCount[0]) {
				case 1 -> notFoundResp; // GET /indexes/{name} → 404
				case 2 -> createResp; // POST /indexes → 201
				default -> refetchResp; // GET /indexes/{name} refetch → 200
			};
		});

		freshClient.ensureIndexExists();

		// 3 HTTP calls: GET (404) + POST create + GET refetch
		verify(httpClient, times(3)).send(any(HttpRequest.class), any());
	}

	@Test
	void ensureIndexExists_networkError_doesNotThrow() throws Exception {
		PineconeClient freshClient = new PineconeClient(API_KEY, INDEX_NAME, ENVIRONMENT, objectMapper, httpClient);
		when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("Connection refused"));

		// Should not throw
		freshClient.ensureIndexExists();
	}

	// ── helpers ──────────────────────────────────────────────────────────

	private String buildQueryResponse(List<String> ids, List<Double> scores) throws Exception {
		ObjectNode root = objectMapper.createObjectNode();
		ArrayNode matches = root.putArray("matches");
		for (int i = 0; i < ids.size(); i++) {
			ObjectNode match = matches.addObject();
			match.put("id", ids.get(i));
			match.put("score", scores.get(i));
			match.putObject("metadata").put("source", "test");
		}
		return objectMapper.writeValueAsString(root);
	}

	private String buildIndexResponse(String host) throws Exception {
		ObjectNode root = objectMapper.createObjectNode();
		root.put("name", INDEX_NAME);
		root.put("host", host);
		root.put("dimension", 1536);
		root.put("metric", "dotproduct");
		return objectMapper.writeValueAsString(root);
	}

	private String readRequestBody(HttpRequest request) {
		HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		CompletableFuture<Void> completion = new CompletableFuture<>();
		publisher.subscribe(new Flow.Subscriber<>() {
			@Override
			public void onSubscribe(Flow.Subscription subscription) {
				subscription.request(Long.MAX_VALUE);
			}

			@Override
			public void onNext(ByteBuffer item) {
				byte[] bytes = new byte[item.remaining()];
				item.get(bytes);
				output.write(bytes, 0, bytes.length);
			}

			@Override
			public void onError(Throwable throwable) {
				completion.completeExceptionally(throwable);
			}

			@Override
			public void onComplete() {
				completion.complete(null);
			}
		});
		completion.join();
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}
}
