package baritone.qwenui.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.Test;

/** Offline tests for GeminiClient's asynchronous HTTP flows. */
public class GeminiHttpFlowTest {
    private static final String TEST_KEY = "offline-test-key-only";

    @Test
    public void listModelsFollowsEncodedPageTokenAndPreservesMetadataAndMethods() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        http.enqueue(200, "{\"models\":[{\"name\":\"models/first\",\"displayName\":\"First\","
                + "\"description\":\"first model description\",\"supportedGenerationMethods\":[\"countTokens\",\"generateContent\"]}],"
                + "\"nextPageToken\":\"page +/?&\"}");
        http.enqueue(200, "{\"models\":[{\"name\":\"models/second\",\"displayName\":\"Second\","
                + "\"description\":\"embedding model\",\"supportedGenerationMethods\":[\"embedContent\"]}]}");

        List<GeminiClient.ModelInfo> models = client(http).listModels().get(5, TimeUnit.SECONDS);

        assertEquals(2, models.size());
        assertEquals("models/first", models.get(0).name());
        assertEquals("First", models.get(0).displayName());
        assertEquals("first model description", models.get(0).description());
        assertEquals(List.of("countTokens", "generateContent"), models.get(0).methods());
        assertTrue(models.get(0).supportsGenerateContent());
        assertEquals("models/second", models.get(1).name());
        assertEquals("Second", models.get(1).displayName());
        assertEquals("embedding model", models.get(1).description());
        assertEquals(List.of("embedContent"), models.get(1).methods());
        assertFalse(models.get(1).supportsGenerateContent());
        assertEquals(2, http.requests.size());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",
                http.requests.get(0).uri().toString());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000&pageToken=page+%2B%2F%3F%26",
                http.requests.get(1).uri().toString());
        for (HttpRequest request : http.requests) {
            assertEquals("GET", request.method());
            assertEquals(List.of(TEST_KEY), request.headers().allValues("x-goog-api-key"));
            assertFalse(request.uri().toString().contains(TEST_KEY));
            assertFalse(request.uri().getRawQuery().contains("key="));
        }
    }

    @Test
    public void listModelsRejectsRepeatedPageToken() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        http.enqueue(200, "{\"models\":[],\"nextPageToken\":\"repeat\"}");
        http.enqueue(200, "{\"models\":[],\"nextPageToken\":\"repeat\"}");

        try {
            client(http).listModels().get(5, TimeUnit.SECONDS);
            fail("Expected repeated page token to fail");
        } catch (ExecutionException failure) {
            assertTrue(failure.getCause() instanceof GeminiClient.GeminiException);
            assertTrue(failure.getCause().getMessage().contains("repeated or excessive model pages"));
        }
        assertEquals("A repeated token is rejected before another request is made", 2, http.requests.size());
    }

    @Test
    public void askRetriesWithoutJsonModeOnlyForSpecificUnsupportedMode400AndParsesAction() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        http.enqueue(400, "{\"error\":{\"message\":\"responseMimeType is unsupported\"}}");
        http.enqueue(200, generationResponse("{\"message\":\"Ready\",\"actions\":["
                + "{\"type\":\"command\",\"name\":\"goto\",\"value\":\"1 2 3\"}]}"));

        GeminiClient.Reply reply = client(http).ask("models/gemini-test", "English", "commands: goto",
                List.of(new GeminiClient.ChatMessage("user", "hello")), "please go")
                .get(5, TimeUnit.SECONDS);

        assertEquals("Ready", reply.message());
        assertEquals(1, reply.actions().size());
        assertEquals("command", reply.actions().get(0).type());
        assertEquals("goto", reply.actions().get(0).name());
        assertEquals("1 2 3", reply.actions().get(0).value());
        assertEquals(2, http.requests.size());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent",
                http.requests.get(0).uri().toString());
        JsonObject firstPayload = payload(http.requests.get(0));
        JsonObject retryPayload = payload(http.requests.get(1));
        assertEquals("application/json", firstPayload.getAsJsonObject("generationConfig")
                .get("responseMimeType").getAsString());
        assertFalse(retryPayload.has("generationConfig"));
        assertEquals(1, retryPayload.getAsJsonArray("contents").size());
        assertEquals("hello\n\nplease go", retryPayload.getAsJsonArray("contents").get(0).getAsJsonObject()
                .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
        for (HttpRequest request : http.requests) {
            assertEquals("POST", request.method());
            assertEquals(List.of(TEST_KEY), request.headers().allValues("x-goog-api-key"));
            assertFalse(request.uri().toString().contains(TEST_KEY));
            assertTrue(request.uri().getRawQuery() == null || !request.uri().getRawQuery().contains("key="));
        }
    }

    @Test
    public void askDoesNotRetryUnauthorizedOrRateLimitedResponses() throws Exception {
        List<Integer> requestCounts = new ArrayList<>();
        for (int status : List.of(401, 429)) {
            FakeHttpClient http = new FakeHttpClient();
            String message = status == 401 ? "API key not valid" : "Resource exhausted";
            http.enqueue(status, "{\"error\":{\"message\":"
                    + new com.google.gson.Gson().toJson(message) + "}}");
            // If an erroneous retry occurs, return another error so the future completes
            // deterministically and the request-count assertion below exposes the retry.
            http.enqueue(status, "{\"error\":{\"message\":\"still unauthorized or rate limited\"}}");
            try {
                client(http).ask("models/gemini-test", "English", "", List.of(), "hello")
                        .get(5, TimeUnit.SECONDS);
                fail("Expected HTTP " + status + " failure");
            } catch (ExecutionException failure) {
                assertTrue(failure.getCause() instanceof GeminiClient.GeminiApiException);
                assertEquals(status, ((GeminiClient.GeminiApiException) failure.getCause()).statusCode());
            }
            requestCounts.add(http.requests.size());
        }
        assertEquals("Neither unauthorized nor rate-limited errors should trigger fallback",
                List.of(1, 1), requestCounts);
    }

    @Test
    public void askDoesNotFallbackForUnrelatedBadRequest() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        http.enqueue(400, "{\"error\":{\"message\":\"invalid API key\"}}");
        try {
            client(http).ask("models/gemini-test", "English", "", List.of(), "hello")
                    .get(5, TimeUnit.SECONDS);
            fail("Expected unrelated HTTP 400 failure");
        } catch (ExecutionException failure) {
            assertTrue(failure.getCause() instanceof GeminiClient.GeminiApiException);
            assertEquals(400, ((GeminiClient.GeminiApiException) failure.getCause()).statusCode());
        }
        assertEquals(1, http.requests.size());
    }

    @Test
    public void testConnectionUsesMinimalPromptAndNeverReturnsActions() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        http.enqueue(200, generationResponse("{\"message\":\"Connectivity is OK\",\"actions\":["
                + "{\"type\":\"command\",\"name\":\"goto\",\"value\":\"1 2 3\"}],"
                + "\"controls\":[{\"type\":\"page\",\"name\":\"commands\",\"label\":\"Open commands\"}]}"));

        GeminiClient.Reply reply = client(http).testConnection("models/gemini-test", "English")
                .get(5, TimeUnit.SECONDS);

        assertEquals("Connectivity is OK", reply.message());
        assertTrue("Test-connection replies must never expose model actions", reply.actions().isEmpty());
        assertTrue("Test-connection replies must never expose model controls", reply.controls().isEmpty());
        assertEquals(1, http.requests.size());
        HttpRequest request = http.requests.get(0);
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent",
                request.uri().toString());
        JsonObject payload = payload(request);
        assertEquals(1, payload.getAsJsonArray("contents").size());
        String prompt = payload.getAsJsonArray("contents").get(0).getAsJsonObject()
                .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(prompt.contains("short confirmation"));
        assertTrue(prompt.contains("Do not suggest or return any actions"));
        assertTrue(payload.getAsJsonObject("systemInstruction").getAsJsonArray("parts").get(0)
                .getAsJsonObject().get("text").getAsString().endsWith("COMMAND, SETTINGS, AND LITEMATICA CATALOG:\n"));
    }

    @Test
    public void testConnectionPropagatesHttpErrors() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        http.enqueue(503, "{\"error\":{\"message\":\"temporary outage\"}}");

        try {
            client(http).testConnection("models/gemini-test", "English").get(5, TimeUnit.SECONDS);
            fail("Expected test-connection HTTP failure");
        } catch (ExecutionException failure) {
            assertTrue(failure.getCause() instanceof GeminiClient.GeminiApiException);
            assertEquals(503, ((GeminiClient.GeminiApiException) failure.getCause()).statusCode());
        }
        assertEquals(1, http.requests.size());
    }

    @Test
    public void cancellingTestConnectionPropagatesToInFlightHttpRequest() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        CompletableFuture<HttpResponse<InputStream>> pending = new CompletableFuture<>();
        http.enqueue(pending);

        CompletableFuture<GeminiClient.Reply> result = client(http).testConnection("models/gemini-test", "English");
        assertEquals(1, http.requests.size());
        assertTrue(result.cancel(true));
        assertTrue("The underlying sendAsync future should be cancelled", pending.isCancelled());
    }

    @Test
    public void cancellingListModelsPropagatesToInFlightHttpRequest() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        CompletableFuture<HttpResponse<InputStream>> pending = new CompletableFuture<>();
        http.enqueue(pending);

        CompletableFuture<List<GeminiClient.ModelInfo>> result = client(http).listModels();
        assertEquals(1, http.requests.size());
        assertTrue(result.cancel(true));
        assertTrue("The underlying sendAsync future should be cancelled", pending.isCancelled());
    }

    private static GeminiClient client(FakeHttpClient fake) throws Exception {
        GeminiClient client = new GeminiClient(TEST_KEY);
        Field field = GeminiClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(client, fake);
        return client;
    }

    private static JsonObject payload(HttpRequest request) throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }
            @Override public void onNext(java.nio.ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                body.updateAndGet(previous -> (previous == null ? "" : previous)
                        + new String(bytes, StandardCharsets.UTF_8));
            }
            @Override public void onError(Throwable throwable) { throw new AssertionError(throwable); }
            @Override public void onComplete() { }
        });
        return JsonParser.parseString(body.get()).getAsJsonObject();
    }

    private static String generationResponse(String plan) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + new com.google.gson.Gson().toJson(plan) + "}]}}]}";
    }

    private static HttpResponse<InputStream> response(int status, String body, URI uri) {
        InputStream stream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return (HttpResponse<InputStream>) Proxy.newProxyInstance(HttpResponse.class.getClassLoader(),
                new Class<?>[]{HttpResponse.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "statusCode" -> status;
                    case "body" -> stream;
                    case "headers" -> HttpHeaders.of(Map.of(), (name, value) -> true);
                    case "uri" -> uri;
                    case "request" -> HttpRequest.newBuilder(uri).build();
                    case "previousResponse", "sslSession" -> Optional.empty();
                    case "version" -> HttpClient.Version.HTTP_1_1;
                    default -> null;
                });
    }

    private static final class FakeHttpClient extends HttpClient {
        private final Queue<CompletableFuture<HttpResponse<InputStream>>> responses = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();

        private void enqueue(int status, String body) {
            responses.add(CompletableFuture.completedFuture(response(status, body,
                    URI.create("https://generativelanguage.googleapis.com/v1beta/test"))));
        }

        private void enqueue(CompletableFuture<HttpResponse<InputStream>> response) {
            responses.add(response);
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public WebSocket.Builder newWebSocketBuilder() { throw new UnsupportedOperationException(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException("Tests use sendAsync");
        }
        @SuppressWarnings("unchecked")
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler) {
            requests.add(request);
            if (responses.isEmpty()) throw new AssertionError("No fake response queued for " + request.uri());
            return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) responses.remove();
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, handler);
        }
    }
}
