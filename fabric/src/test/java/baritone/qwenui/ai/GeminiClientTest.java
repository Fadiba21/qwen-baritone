package baritone.qwenui.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.Test;

/** Offline tests for GeminiClient's response validation and request-input guards. */
public class GeminiClientTest {
    private static final String TEST_KEY = "unit-test-key-never-a-real-credential";

    @Test
    public void parsesAValidCommandPlan() throws Exception {
        GeminiClient.Reply reply = parseReply(response("{\"message\":\"Ready\",\"actions\":["
                + "{\"type\":\"command\",\"name\":\"goto\",\"value\":\"1 2 3\"}]}"));
        assertEquals("Ready", reply.message());
        assertEquals(1, reply.actions().size());
        assertEquals("command", reply.actions().get(0).type());
        assertEquals("goto", reply.actions().get(0).name());
        assertEquals("1 2 3", reply.actions().get(0).value());
    }

    @Test
    public void parsesSettingAndMacroActions() throws Exception {
        GeminiClient.Reply reply = parseReply(response("{\"message\":\"Plan\",\"actions\":["
                + "{\"type\":\"setting\",\"name\":\"allowSprint\",\"value\":\"true\"},"
                + "{\"type\":\"macro\",\"name\":\"route\",\"commands\":[\"goto 1 2 3\",\"stop\"]}]}"));
        assertEquals(2, reply.actions().size());
        assertEquals("setting", reply.actions().get(0).type());
        assertEquals("macro", reply.actions().get(1).type());
        assertEquals(List.of("goto 1 2 3", "stop"), reply.actions().get(1).commands());
    }

    @Test
    public void parsesLitematicaProposalAndSafeNavigationControls() throws Exception {
        GeminiClient.Reply reply = parseReply(response("{\"message\":\"Use a reviewed plan\",\"actions\":["
                + "{\"type\":\"litematica\",\"name\":\"placement.load\"}],\"controls\":["
                + "{\"type\":\"page\",\"name\":\"litematica\",\"label\":\"Open Litematica\"},"
                + "{\"type\":\"command\",\"name\":\"goto\",\"label\":\"Go to coordinates\"}]}"));
        assertEquals("litematica", reply.actions().get(0).type());
        assertEquals("placement.load", reply.actions().get(0).name());
        assertEquals(2, reply.controls().size());
        assertEquals(new GeminiClient.Control("page", "litematica", "Open Litematica"), reply.controls().get(0));
        assertEquals(new GeminiClient.Control("command", "goto", "Go to coordinates"), reply.controls().get(1));
    }

    @Test
    public void malformedControlsAreRejectedIndependentlyWithoutLosingOtherSuggestions() throws Exception {
        GeminiClient.Reply reply = parseReply(response("{\"message\":\"ok\",\"actions\":[],\"controls\":["
                + "null,{\"type\":\"page\",\"name\":\"shell\",\"label\":\"Unsafe page\"},"
                + "{\"type\":\"command\",\"name\":\"goto;stop\",\"label\":\"Injection\"},"
                + "{\"type\":\"setting\",\"name\":7,\"label\":\"Bad type\"},"
                + "{\"type\":\"page\",\"name\":\"commands\",\"label\":\"Browse commands\"}]}"));
        assertEquals(1, reply.controls().size());
        assertEquals("commands", reply.controls().get(0).name());
    }

    @Test
    public void controlsAreBoundedToTwentyFour() throws Exception {
        StringBuilder controls = new StringBuilder("{\"message\":\"ok\",\"controls\":[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) controls.append(',');
            controls.append("{\"type\":\"page\",\"name\":\"home\",\"label\":\"Home ").append(i).append("\"}");
        }
        controls.append("]}");
        assertEquals(24, parseReply(response(controls.toString())).controls().size());
    }

    @Test
    public void twoArgumentReplyConstructorRemainsCompatible() {
        GeminiClient.Reply reply = new GeminiClient.Reply("ok", List.of());
        assertTrue(reply.controls().isEmpty());
    }

    @Test
    public void systemPromptAllowsGeneralConversationAndRetainsHistoryContext() throws Exception {
        String system = systemContext("Indonesian", "registered commands and live settings");
        assertTrue(system.contains("general-purpose conversational assistant"));
        assertTrue(system.contains("topics unrelated to Minecraft"));
        assertTrue(system.contains("supplied conversation history"));
        assertTrue(system.contains("For purely conversational answers or questions needing no operation, return actions [] and controls []."));
        assertTrue(system.endsWith("COMMAND, SETTINGS, AND LITEMATICA CATALOG:\nregistered commands and live settings"));
    }

    @Test
    public void systemPromptRequestsImmediateActionsWithoutFalseCompletionClaimsOrInventedControls() throws Exception {
        String system = systemContext("English", "live catalog");
        assertTrue(system.contains("execute immediately after this response"));
        assertTrue(system.contains("there is no user review/approval step"));
        assertTrue(system.contains("Never say or imply an action has already executed"));
        assertTrue(system.contains("Only request actions supported by the live catalog"));
        assertTrue(system.contains("Do not request shell, file-system, network, code execution, API-key access"));
        assertTrue(system.contains("Never invent a UI control or feature"));
        assertTrue(system.contains("available Litematica feature IDs from the live catalog"));
    }

    @Test
    public void generationRequestMergesConsecutiveAssistantMessagesWithoutDroppingExecutionStatus() throws Exception {
        Method method = GeminiClient.class.getDeclaredMethod("buildGenerationRequest", String.class, String.class,
                List.class, String.class);
        method.setAccessible(true);
        JsonObject request = (JsonObject) method.invoke(null, "English", "catalog", List.of(
                new GeminiClient.ChatMessage("user", "What happened?"),
                new GeminiClient.ChatMessage("assistant", "The action was requested."),
                new GeminiClient.ChatMessage("assistant", "Execution status: started.")), "next question");
        var contents = request.getAsJsonArray("contents");
        assertEquals(3, contents.size());
        assertEquals("user", contents.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("model", contents.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("The action was requested.\n\nExecution status: started.",
                contents.get(1).getAsJsonObject().getAsJsonArray("parts").get(0).getAsJsonObject()
                        .get("text").getAsString());
        assertEquals("user", contents.get(2).getAsJsonObject().get("role").getAsString());
        assertEquals("next question", contents.get(2).getAsJsonObject().getAsJsonArray("parts")
                .get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    public void truncatedHistoryStartsWithUserWhilePreservingEarlierModelContext() throws Exception {
        Method method = GeminiClient.class.getDeclaredMethod("buildGenerationRequest", String.class, String.class,
                List.class, String.class);
        method.setAccessible(true);
        JsonObject request = (JsonObject) method.invoke(null, "English", "catalog", List.of(
                new GeminiClient.ChatMessage("assistant", "Execution status: applied.")), "What changed?");
        var contents = request.getAsJsonArray("contents");
        assertEquals(3, contents.size());
        assertEquals("user", contents.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("model", contents.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("Execution status: applied.", contents.get(1).getAsJsonObject().getAsJsonArray("parts")
                .get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("user", contents.get(2).getAsJsonObject().get("role").getAsString());
    }

    @Test
    public void parsesJsonInsideMarkdownFence() throws Exception {
        GeminiClient.Reply reply = parseReply(response("```json\n{\"message\":\"ok\",\"actions\":[]}\n```"));
        assertEquals("ok", reply.message());
        assertTrue(reply.actions().isEmpty());
    }

    @Test
    public void extractsJsonFromSurroundingModelText() throws Exception {
        GeminiClient.Reply reply = parseReply(response("Here is the plan: {\"message\":\"ok\",\"actions\":[]} done"));
        assertEquals("ok", reply.message());
    }

    @Test
    public void acceptsMissingActionsAsAnEmptyPlan() throws Exception {
        assertTrue(parseReply(response("{\"message\":\"No action needed\"}")).actions().isEmpty());
    }

    @Test
    public void rejectsMissingCandidates() throws Exception {
        expectGeminiFailure(JsonParser.parseString("{}").getAsJsonObject(), "no response candidate");
    }

    @Test
    public void rejectsEmptyCandidateList() throws Exception {
        expectGeminiFailure(JsonParser.parseString("{\"candidates\":[]}").getAsJsonObject(), "no response candidate");
    }

    @Test
    public void rejectsNonArrayCandidates() throws Exception {
        expectGeminiFailure(JsonParser.parseString("{\"candidates\":{}}").getAsJsonObject(), "no response candidate");
    }

    @Test
    public void rejectsEmptyCandidateText() throws Exception {
        expectGeminiFailure(response("  \n "), "empty response");
    }

    @Test
    public void rejectsNonJsonPlan() throws Exception {
        expectGeminiFailure(response("not json"), "not valid JSON");
    }

    @Test
    public void rejectsNonObjectPlan() throws Exception {
        expectGeminiFailure(response("[1,2,3]"), "not valid JSON");
    }

    @Test
    public void rejectsActionsWhenNotAnArray() throws Exception {
        expectGeminiFailure(response("{\"message\":\"x\",\"actions\":{}}"), "invalid actions list");
    }

    @Test
    public void rejectsNonObjectAction() throws Exception {
        expectGeminiFailure(response("{\"actions\":[\"goto\"]}"), "invalid action");
    }

    @Test
    public void rejectsUnknownActionType() throws Exception {
        expectGeminiFailure(response("{\"actions\":[{\"type\":\"shell\",\"name\":\"x\"}]}"), "unsupported action type");
    }

    @Test
    public void rejectsBlankActionName() throws Exception {
        expectGeminiFailure(response("{\"actions\":[{\"type\":\"command\",\"name\":\" \"}]}"), "invalid or oversized action");
    }

    @Test
    public void rejectsActionNameOverLimit() throws Exception {
        String name = "n".repeat(257);
        expectGeminiFailure(response("{\"actions\":[{\"type\":\"setting\",\"name\":" + quote(name) + "}]}"), "invalid or oversized action");
    }

    @Test
    public void rejectsActionValueOverLimit() throws Exception {
        String value = "v".repeat(4001);
        expectGeminiFailure(response("{\"actions\":[{\"type\":\"setting\",\"name\":\"x\",\"value\":" + quote(value) + "}]}"), "invalid or oversized action");
    }

    @Test
    public void rejectsTooManyActions() throws Exception {
        StringBuilder actions = new StringBuilder("{\"actions\":[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) actions.append(',');
            actions.append("{\"type\":\"command\",\"name\":\"walk\"}");
        }
        actions.append("]}");
        expectGeminiFailure(response(actions.toString()), "too many actions");
    }

    @Test
    public void rejectsTooManyMacroCommands() throws Exception {
        StringBuilder commands = new StringBuilder("[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) commands.append(',');
            commands.append("\"stop\"");
        }
        commands.append(']');
        expectGeminiFailure(response("{\"actions\":[{\"type\":\"macro\",\"name\":\"route\",\"commands\":" + commands + "}]}") , "too many macro commands");
    }

    @Test
    public void rejectsOversizedMacroCommand() throws Exception {
        String command = "c".repeat(2049);
        expectGeminiFailure(response("{\"actions\":[{\"type\":\"macro\",\"name\":\"route\",\"commands\":[" + quote(command) + "]}]}"), "oversized macro command");
    }

    @Test
    public void rejectsModelPathTraversalAndUrlInjectionBeforeNetwork() throws Exception {
        GeminiClient client = new GeminiClient(TEST_KEY);
        for (String model : List.of("models/gemini/evil", "models/gemini?x=1", "https://attacker.invalid/models/x", "models/gemini#fragment", "models/../evil")) {
            try {
                client.ask(model, "English", "", List.of(), "hello").join();
                fail("Expected model path to be rejected: " + model);
            } catch (CompletionException failure) {
                assertTrue("Unexpected failure for " + model, failure.getCause() instanceof IllegalArgumentException);
                assertTrue(failure.getCause().getMessage().contains("Invalid Gemini model name"));
            }
        }
    }

    @Test
    public void acceptsOnlyModelResourcePathCharacters() throws Exception {
        java.lang.reflect.Field modelPattern = GeminiClient.class.getDeclaredField("MODEL_PATH");
        modelPattern.setAccessible(true);
        java.util.regex.Pattern pattern = (java.util.regex.Pattern) modelPattern.get(null);
        for (String model : List.of("models/gemini-2.0_flash", "models/model.v1", "models/a-b_2")) {
            assertTrue("Expected valid model resource path: " + model, pattern.matcher(model).matches());
        }
    }

    @Test
    public void rejectsApiKeysContainingHeaderLineBreaks() {
        for (String key : List.of("bad\nkey", "bad\rkey", " \t ")) {
            try {
                new GeminiClient(key);
                fail("Expected invalid API key rejection");
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.getMessage().contains(key));
            }
        }
    }

    @Test
    public void sanitizesApiErrorBodyAndDoesNotEchoApiKey() throws Exception {
        String body = "secret=" + TEST_KEY + " https://generativelanguage.googleapis.com/?key=" + TEST_KEY;
        HttpResponse<java.io.InputStream> response = mockResponse(403, body);
        Method read = GeminiClient.class.getDeclaredMethod("readJsonResponse", HttpResponse.class);
        read.setAccessible(true);
        try {
            read.invoke(new GeminiClient(TEST_KEY), response);
            fail("Expected provider error");
        } catch (InvocationTargetException wrapped) {
            Throwable failure = wrapped.getCause();
            assertTrue(failure instanceof CompletionException);
            Throwable apiError = failure.getCause();
            assertTrue(apiError instanceof GeminiClient.GeminiApiException);
            assertEquals(403, ((GeminiClient.GeminiApiException) apiError).statusCode());
            assertFalse(apiError.getMessage().contains(TEST_KEY));
            assertFalse(apiError.getMessage().contains("generativelanguage.googleapis.com"));
            assertFalse(apiError.getMessage().contains("secret="));
        }
    }

    @Test
    public void detectsUnsupportedJsonModeErrors() throws Exception {
        assertTrue(isUnsupported(400, "responseMimeType is unsupported"));
        assertTrue(isUnsupported(400, "response mime type not supported"));
        assertTrue(isUnsupported(400, "response_mime_type is invalid"));
        assertTrue(isUnsupported(422, "Unknown responseMimeType parameter"));
    }

    @Test
    public void doesNotTreatUnrelatedOrSuccessfulErrorsAsJsonModeFallback() throws Exception {
        assertFalse(isUnsupported(400, "invalid API key"));
        assertFalse(isUnsupported(400, "responseMimeType accepted"));
        assertFalse(isUnsupported(500, "responseMimeType unsupported"));
        assertFalse(isUnsupported(401, "responseMimeType unsupported"));
        assertFalse(isUnsupported(429, "responseMimeType unsupported"));
        assertFalse(isUnsupported(399, "responseMimeType unsupported"));
    }

    private static GeminiClient.Reply parseReply(JsonObject json) throws Exception {
        Method parse = GeminiClient.class.getDeclaredMethod("parseReply", JsonObject.class);
        parse.setAccessible(true);
        try {
            return (GeminiClient.Reply) parse.invoke(null, json);
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof Exception exception) throw exception;
            throw wrapped;
        }
    }

    private static String systemContext(String language, String catalog) throws Exception {
        Method method = GeminiClient.class.getDeclaredMethod("buildSystemContext", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, language, catalog);
    }

    private static void expectGeminiFailure(JsonObject json, String messagePart) throws Exception {
        try {
            parseReply(json);
            fail("Expected GeminiException containing: " + messagePart);
        } catch (GeminiClient.GeminiException expected) {
            assertTrue("Unexpected failure message: " + expected.getMessage(), expected.getMessage().contains(messagePart));
        }
    }

    private static JsonObject response(String text) {
        return JsonParser.parseString("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quote(text) + "}]}}]}").getAsJsonObject();
    }

    private static String quote(String text) {
        return new com.google.gson.Gson().toJson(text);
    }

    private static boolean isUnsupported(int status, String body) throws Exception {
        Method detect = GeminiClient.class.getDeclaredMethod("isUnsupportedJsonMode", int.class, String.class);
        detect.setAccessible(true);
        return (boolean) detect.invoke(null, status, body);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<java.io.InputStream> mockResponse(int status, String body) {
        java.io.InputStream stream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return (HttpResponse<java.io.InputStream>) Proxy.newProxyInstance(
                HttpResponse.class.getClassLoader(), new Class<?>[]{HttpResponse.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "statusCode" -> status;
                    case "body" -> stream;
                    case "headers" -> HttpHeaders.of(Map.of(), (name, value) -> true);
                    case "uri" -> URI.create("https://generativelanguage.googleapis.com/v1beta/models/test");
                    default -> null;
                });
    }
}
