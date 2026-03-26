package com.weeklycommit.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EmbeddingService}.
 *
 * <p>
 * Uses a mock {@link HttpClient} so no real HTTP calls are made. Verifies
 * request format, response parsing, and graceful-degradation paths.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

	private static final String BASE_URL = "https://openrouter.ai/api/v1";
	private static final String MODEL = "openai/text-embedding-3-small";
	private static final String API_KEY = "sk-test-key";

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private HttpClient httpClient;

	@Mock
	@SuppressWarnings("unchecked")
	private HttpResponse<String> httpResponse;

	private EmbeddingService service;

	@BeforeEach
	void setUp() {
		service = new EmbeddingService(API_KEY, BASE_URL, MODEL, objectMapper, httpClient);
	}

	// ── isAvailable ──────────────────────────────────────────────────────

	@Test
	void isAvailable_withKey_returnsTrue() {
		assertThat(service.isAvailable()).isTrue();
	}

	@Test
	void isAvailable_blankKey_returnsFalse() {
		EmbeddingService noKey = new EmbeddingService("", BASE_URL, MODEL, objectMapper, httpClient);
		assertThat(noKey.isAvailable()).isFalse();
	}

	@Test
	void isAvailable_whitespaceKey_returnsFalse() {
		EmbeddingService wsKey = new EmbeddingService("   ", BASE_URL, MODEL, objectMapper, httpClient);
		assertThat(wsKey.isAvailable()).isFalse();
	}

	// ── embed — short-circuit paths ──────────────────────────────────────

	@Test
	void embed_blankApiKey_returnsEmptyArrayWithoutHttpCall() {
		EmbeddingService noKey = new EmbeddingService("", BASE_URL, MODEL, objectMapper, httpClient);

		float[] result = noKey.embed("some text");

		assertThat(result).isEmpty();
		verifyNoInteractions(httpClient);
	}

	@Test
	void embed_nullText_returnsEmptyArrayWithoutHttpCall() {
		float[] result = service.embed(null);

		assertThat(result).isEmpty();
		verifyNoInteractions(httpClient);
	}

	@Test
	void embed_blankText_returnsEmptyArrayWithoutHttpCall() {
		float[] result = service.embed("   ");

		assertThat(result).isEmpty();
		verifyNoInteractions(httpClient);
	}

	// ── embed — happy path ───────────────────────────────────────────────

	@Test
	void embed_successfulResponse_returnsCorrectSizedVector() throws Exception {
		String responseJson = buildEmbeddingResponse(1536);
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(responseJson);
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		float[] result = service.embed("hello world");

		assertThat(result).hasSize(1536);
	}

	@Test
	void embed_successfulResponse_vectorValuesAreCorrect() throws Exception {
		String responseJson = buildEmbeddingResponse(4);
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(responseJson);
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		float[] result = service.embed("test");

		// Vector is [0/4, 1/4, 2/4, 3/4] = [0.0, 0.25, 0.5, 0.75]
		assertThat(result[0]).isCloseTo(0.0f, offset(0.0001f));
		assertThat(result[1]).isCloseTo(0.25f, offset(0.0001f));
		assertThat(result[2]).isCloseTo(0.5f, offset(0.0001f));
		assertThat(result[3]).isCloseTo(0.75f, offset(0.0001f));
	}

	// ── embed — request format verification ─────────────────────────────

	@Test
	void embed_requestTargetsEmbeddingsEndpoint() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(buildEmbeddingResponse(1536));
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		service.embed("test input");

		HttpRequest captured = captor.getValue();
		assertThat(captured.uri().toString()).isEqualTo(BASE_URL + "/embeddings");
	}

	@Test
	void embed_requestHasAuthorizationHeader() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(buildEmbeddingResponse(1536));
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		service.embed("test input");

		HttpRequest captured = captor.getValue();
		assertThat(captured.headers().firstValue("Authorization")).hasValue("Bearer " + API_KEY);
	}

	@Test
	void embed_requestHasContentTypeHeader() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(buildEmbeddingResponse(1536));
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		service.embed("test input");

		HttpRequest captured = captor.getValue();
		assertThat(captured.headers().firstValue("Content-Type")).hasValue("application/json");
	}

	@Test
	void embed_requestBodyContainsModelAndInputArray() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(buildEmbeddingResponse(1536));
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		service.embed("test input");

		JsonNode body = objectMapper.readTree(readRequestBody(captor.getValue()));
		assertThat(body.path("model").asText()).isEqualTo(MODEL);
		assertThat(body.path("input")).isNotNull();
		assertThat(body.path("input")).hasSize(1);
		assertThat(body.path("input").get(0).asText()).isEqualTo("test input");
	}

	@Test
	void embed_requestIsPost() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(buildEmbeddingResponse(1536));
		ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpClient.send(captor.capture(), any())).thenAnswer(inv -> httpResponse);

		service.embed("test input");

		assertThat(captor.getValue().method()).isEqualTo("POST");
	}

	// ── embed — error paths ──────────────────────────────────────────────

	@Test
	void embed_http500_returnsEmptyArray() throws Exception {
		when(httpResponse.statusCode()).thenReturn(500);
		when(httpResponse.body()).thenReturn("{\"error\":\"internal server error\"}");
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		float[] result = service.embed("some text");

		assertThat(result).isEmpty();
	}

	@Test
	void embed_http429_returnsEmptyArray() throws Exception {
		when(httpResponse.statusCode()).thenReturn(429);
		when(httpResponse.body()).thenReturn("{\"error\":\"rate limited\"}");
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		float[] result = service.embed("some text");

		assertThat(result).isEmpty();
	}

	@Test
	void embed_networkException_returnsEmptyArray() throws Exception {
		when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("Connection refused"));

		float[] result = service.embed("some text");

		assertThat(result).isEmpty();
	}

	@Test
	void embed_malformedJson_returnsEmptyArray() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn("not-json");
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		float[] result = service.embed("some text");

		assertThat(result).isEmpty();
	}

	@Test
	void embed_responseMissingDataField_returnsEmptyArray() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn("{\"object\":\"list\"}");
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		float[] result = service.embed("some text");

		assertThat(result).isEmpty();
	}

	@Test
	void embed_responseMissingEmbeddingField_returnsEmptyArray() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn("{\"data\":[{\"index\":0}]}");
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		float[] result = service.embed("some text");

		assertThat(result).isEmpty();
	}

	@Test
	void embed_makesExactlyOneHttpCall() throws Exception {
		when(httpResponse.statusCode()).thenReturn(200);
		when(httpResponse.body()).thenReturn(buildEmbeddingResponse(1536));
		when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(inv -> httpResponse);

		service.embed("text");

		verify(httpClient).send(any(HttpRequest.class), any());
	}

	// ── helpers ──────────────────────────────────────────────────────────

	/**
	 * Builds a synthetic OpenAI-compatible embeddings response with {@code dims}
	 * dimensions. The embedding values are {@code i / dims} for index {@code i}.
	 */
	private String buildEmbeddingResponse(int dims) throws Exception {
		ObjectNode root = objectMapper.createObjectNode();
		root.put("object", "list");
		ArrayNode dataArray = root.putArray("data");
		ObjectNode entry = dataArray.addObject();
		entry.put("object", "embedding");
		entry.put("index", 0);
		ArrayNode embeddingArray = entry.putArray("embedding");
		for (int i = 0; i < dims; i++) {
			embeddingArray.add((float) i / dims);
		}
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
