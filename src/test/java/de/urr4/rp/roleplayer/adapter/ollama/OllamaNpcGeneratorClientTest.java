package de.urr4.rp.roleplayer.adapter.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import de.urr4.rp.roleplayer.domain.model.NpcGeneration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaNpcGeneratorClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesAValidNpcGenerationJsonResponse() {
        OllamaNpcGeneratorClient client = new OllamaNpcGeneratorClient("http://localhost:11434", "llama3.2:1b", new ObjectMapper());
        String response = """
                {"firstImpression":"Freundliches Lächeln, warme Stimme","goal":"Will seinen Laden vor der Pleite retten",
                "attitude":"Freundlich","rulesAndTaboos":"Verkauft niemals gestohlene Ware","quirks":"Poliert ständig seine Brille"}
                """;

        NpcGeneration generation = client.parseNpcGeneration(response);

        assertEquals("Freundliches Lächeln, warme Stimme", generation.firstImpression());
        assertEquals("Will seinen Laden vor der Pleite retten", generation.goal());
        assertEquals("Freundlich", generation.attitude());
        assertEquals("Verkauft niemals gestohlene Ware", generation.rulesAndTaboos());
        assertEquals("Poliert ständig seine Brille", generation.quirks());
    }

    @Test
    void throwsAClearErrorWhenResponseIsBlank() {
        OllamaNpcGeneratorClient client = new OllamaNpcGeneratorClient("http://localhost:11434", "llama3.2:1b", new ObjectMapper());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> client.parseNpcGeneration(""));

        assertTrue(exception.getMessage().contains("no NPC generation response"));
    }

    @Test
    void throwsAClearErrorWhenResponseIsNotValidJson() {
        OllamaNpcGeneratorClient client = new OllamaNpcGeneratorClient("http://localhost:11434", "llama3.2:1b", new ObjectMapper());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.parseNpcGeneration("Das ist kein JSON."));

        assertTrue(exception.getMessage().contains("not valid JSON"));
    }

    @Test
    void promptMentionsAllFiveNpcSheetFieldsAndTheShortDescription() {
        OllamaNpcGeneratorClient client = new OllamaNpcGeneratorClient("http://localhost:11434", "llama3.2:1b", new ObjectMapper());

        String prompt = client.buildPrompt("freundlicher Händler");

        assertTrue(prompt.contains("freundlicher Händler"));
        assertTrue(prompt.contains("firstImpression"));
        assertTrue(prompt.contains("goal"));
        assertTrue(prompt.contains("attitude"));
        assertTrue(prompt.contains("rulesAndTaboos"));
        assertTrue(prompt.contains("quirks"));
    }

    @Test
    void generateSendsAJsonSchemaFormatConstrainingTheFiveRequiredFields() throws IOException, InterruptedException {
        BlockingQueue<String> capturedBodies = new ArrayBlockingQueue<>(1);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/generate", exchange -> {
            capturedBodies.offer(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = "{\"response\":\"{\\\"firstImpression\\\":\\\"a\\\",\\\"goal\\\":\\\"b\\\","
                    + "\\\"attitude\\\":\\\"c\\\",\\\"rulesAndTaboos\\\":\\\"d\\\",\\\"quirks\\\":\\\"e\\\"}\"}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        OllamaNpcGeneratorClient client = new OllamaNpcGeneratorClient(
                "http://localhost:" + server.getAddress().getPort(), "llama3.2:1b", new ObjectMapper());

        NpcGeneration generation = client.generate("freundlicher Händler");

        assertEquals("a", generation.firstImpression());
        String requestBody = capturedBodies.poll(5, TimeUnit.SECONDS);
        assertTrue(requestBody.contains("\"format\""));
        assertTrue(requestBody.contains("\"llama3.2:1b\""));
        assertTrue(requestBody.contains(
                "\"required\":[\"firstImpression\",\"goal\",\"attitude\",\"rulesAndTaboos\",\"quirks\"]"));
    }
}
