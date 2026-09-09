package de.urr4.rp.roleplayer.adapter.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaWorldBuildingClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }
    @Test
    void extractsFirstJsonArrayAndSkipsInvalidPaths() {
        OllamaWorldBuildingClient client = new OllamaWorldBuildingClient("http://localhost:11434", "llama3.2", new ObjectMapper());
        String response = "Vorspann [{\"path\":\"Locations/Dorf.md\",\"title\":\"Dorf\",\"action\":\"create\",\"content\":\"# Dorf\"},{\"path\":\"../bad.md\",\"title\":\"Bad\",\"action\":\"update\",\"content\":\"x\"}] Nachspann";

        var changes = client.parseNoteChanges(response);

        assertEquals(1, changes.size());
        assertEquals("Locations/Dorf.md", changes.getFirst().relativePath());
    }

    @Test
    void throwsAClearErrorWhenModelReturnsAFlatArrayOfStringsInsteadOfObjects() {
        OllamaWorldBuildingClient client = new OllamaWorldBuildingClient("http://localhost:11434", "llama3.2", new ObjectMapper());
        String response = "[\"path\",\"title\",\"action\",\"content\"]";

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> client.parseNoteChanges(response));

        assertTrue(exception.getMessage().contains("not an object"));
        assertTrue(exception.getMessage().contains("more capable OLLAMA_WORLDBUILDING_MODEL"));
    }

    @Test
    void repairsMultipleSquareBracketPseudoObjectsIntoRealNoteChanges() {
        OllamaWorldBuildingClient client = new OllamaWorldBuildingClient("http://localhost:11434", "llama3.2", new ObjectMapper());
        // Reproduces a real llama3.2:3b response: each note written as its
        // own top-level "[ ... ]" block using square brackets instead of
        // curly braces, with no comma/array wrapper between blocks, and a
        // markdown link (containing literal brackets) inside "content".
        String response = """
                [ "path": "test-welt/Geografie", "title": "Geografie", "action": "create", "content": "|- [Paspaturia](test-welt/Locations/Paspaturia)" ]
                [ "path": "test-welt/Locations/Paspaturia", "title": "Paspaturia", "action": "create", "content": "Die Testwelt ist eine fiktive Welt." ]
                """;

        var changes = client.parseNoteChanges(response);

        assertEquals(2, changes.size());
        assertEquals("test-welt/Geografie", changes.get(0).relativePath());
        assertEquals("test-welt/Locations/Paspaturia", changes.get(1).relativePath());
        assertTrue(changes.get(0).markdownContent().contains("[Paspaturia](test-welt/Locations/Paspaturia)"));
    }

    @Test
    void mergePromptMandatesObsidianWikilinkSyntaxForEveryMentionOfLinkableEntities() {
        OllamaWorldBuildingClient client = new OllamaWorldBuildingClient("http://localhost:11434", "llama3.2", new ObjectMapper());

        String prompt = client.buildMergePrompt("Testwelt", "test-welt", "Chronik", "Abenteuer", "Fakten...", java.util.List.of());

        // Must require the real Obsidian [[Title]] wikilink syntax (not
        // markdown [text](path) links) for every occurrence of a linkable
        // entity, not just the first mention - otherwise the vault stops
        // being a proper Obsidian graph of cross-referenced notes.
        assertTrue(prompt.contains("[[Notiztitel]]"));
        assertTrue(prompt.contains("JEDER Erwähnung"));
        assertTrue(prompt.contains("NIEMALS die"));
    }

    @Test
    void mergePromptForbidsRewordingTheReviewedFactsTextAndOnlyAllowsAddingObsidianStructure() {
        OllamaWorldBuildingClient client = new OllamaWorldBuildingClient("http://localhost:11434", "llama3.2", new ObjectMapper());

        String prompt = client.buildMergePrompt("Testwelt", "test-welt", "Chronik", "Abenteuer", "Fakten...", java.util.List.of());

        // The draft facts text was already reviewed/edited by the user - the
        // merge step must only add Obsidian structure (splitting into notes,
        // wikilinks, headings) on top of it, never rewrite/summarize/shorten
        // the wording itself, otherwise reviewed facts get silently altered.
        assertTrue(prompt.contains("Verändere ihn NICHT"));
        assertTrue(prompt.contains("wortwörtlich"));
        assertTrue(prompt.contains("Umformulieren"));
    }

    @Test
    void mergeFactsIntoVaultDoesNotConstrainTheFirstAttemptWithAJsonSchema() throws IOException, InterruptedException {
        BlockingQueue<String> capturedBodies = new ArrayBlockingQueue<>(1);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/generate", exchange -> {
            capturedBodies.offer(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"response\":\"[]\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        OllamaWorldBuildingClient client = new OllamaWorldBuildingClient(
                "http://localhost:" + server.getAddress().getPort(), "llama3.2", new ObjectMapper());

        client.mergeFactsIntoVault("Testwelt", "test-welt", "Chronik", "Abenteuer", "Fakten...", List.of());

        String requestBody = capturedBodies.poll(5, TimeUnit.SECONDS);
        // The first attempt must stay unconstrained ("format" absent) -
        // small models produce measurably richer, more detailed note
        // content without a JSON Schema grammar constraint; the schema is
        // only applied as a last-resort retry if parsing fails entirely
        // (see mergeFactsIntoVaultRetriesWithAJsonSchemaOnlyWhenTheFirstAttemptCannotBeParsed).
        assertTrue(!requestBody.contains("\"format\""));
    }

    @Test
    void mergeFactsIntoVaultRetriesWithAJsonSchemaOnlyWhenTheFirstAttemptCannotBeParsed() throws IOException, InterruptedException {
        BlockingQueue<String> capturedBodies = new ArrayBlockingQueue<>(2);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/generate", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            capturedBodies.offer(requestBody);
            // First call (no "format") returns completely unparseable prose
            // (no bracket structure at all); second call (with "format")
            // returns a valid, schema-constrained array.
            boolean isRetry = requestBody.contains("\"format\"");
            String response = isRetry
                    ? "{\"response\":\"[{\\\"path\\\":\\\"Locations/Dorf.md\\\",\\\"title\\\":\\\"Dorf\\\",\\\"action\\\":\\\"create\\\",\\\"content\\\":\\\"# Dorf\\\"}]\"}"
                    : "{\"response\":\"Es tut mir leid, ich kann das nicht als JSON formatieren.\"}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        OllamaWorldBuildingClient client = new OllamaWorldBuildingClient(
                "http://localhost:" + server.getAddress().getPort(), "llama3.2", new ObjectMapper());

        var changes = client.mergeFactsIntoVault("Testwelt", "test-welt", "Chronik", "Abenteuer", "Fakten...", List.of());

        assertEquals(1, changes.size());
        assertEquals("Locations/Dorf.md", changes.getFirst().relativePath());
        String firstRequestBody = capturedBodies.poll(5, TimeUnit.SECONDS);
        String secondRequestBody = capturedBodies.poll(5, TimeUnit.SECONDS);
        assertTrue(!firstRequestBody.contains("\"format\""));
        // "format" must be a JSON Schema object (not just the string "json")
        // so Ollama grammar-constrains its output to a real array-of-objects
        // shape, eliminating malformed/prose-wrapped JSON regardless of the
        // model's own instruction-following ability.
        assertTrue(secondRequestBody.contains("\"format\""));
        assertTrue(secondRequestBody.contains("\"type\":\"array\""));
        assertTrue(secondRequestBody.contains("\"required\":[\"path\",\"title\",\"action\",\"content\"]"));
    }
}