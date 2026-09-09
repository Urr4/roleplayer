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
    void mergeFactsIntoVaultSendsAJsonSchemaFormatToConstrainOllamasOutput() throws IOException, InterruptedException {
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
        // "format" must be a JSON Schema object (not just the string "json")
        // so Ollama grammar-constrains its output to a real array-of-objects
        // shape, eliminating malformed/prose-wrapped JSON regardless of the
        // model's own instruction-following ability.
        assertTrue(requestBody.contains("\"format\""));
        assertTrue(requestBody.contains("\"type\":\"array\""));
        assertTrue(requestBody.contains("\"required\":[\"path\",\"title\",\"action\",\"content\"]"));
    }
}
