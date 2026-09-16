package de.urr4.rp.roleplayer.adapter.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.urr4.rp.roleplayer.domain.model.NpcGeneration;
import de.urr4.rp.roleplayer.domain.port.out.NpcGeneratorClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OllamaNpcGeneratorClient implements NpcGeneratorClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // NPC sheets are short and use a small, fast model, so a much shorter
    // read timeout than the world-building client is appropriate.
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    // JSON Schema passed as Ollama's "format" - constrains token sampling so
    // the model can only emit an object with exactly these 5 string fields
    // (see https://ollama.com/blog/structured-outputs). This matters more
    // here than for world-building: a small/fast model like llama3.2:1b is
    // less reliable at freeform instruction-following, so the guaranteed
    // structure is worth the (here negligible) cost in content richness.
    private static final Map<String, Object> NPC_JSON_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "firstImpression", Map.of("type", "string"),
                    "goal", Map.of("type", "string"),
                    "attitude", Map.of("type", "string"),
                    "rulesAndTaboos", Map.of("type", "string"),
                    "quirks", Map.of("type", "string")
            ),
            "required", List.of("firstImpression", "goal", "attitude", "rulesAndTaboos", "quirks")
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OllamaNpcGeneratorClient(@Value("${ollama.base-url}") String baseUrl,
                                    @Value("${ollama.npc-model:llama3.2:1b}") String model,
                                    ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public NpcGeneration generate(String shortDescription) {
        OllamaGenerateResponse response = restClient.post()
                .uri("/api/generate")
                .body(Map.of(
                        "model", model,
                        "prompt", buildPrompt(shortDescription),
                        "stream", false,
                        "format", NPC_JSON_SCHEMA
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    String errorBody = new String(clientResponse.getBody().readAllBytes());
                    throw new IllegalStateException("Ollama NPC generation failed with HTTP "
                            + clientResponse.getStatusCode().value() + ": " + errorBody);
                })
                .body(OllamaGenerateResponse.class);
        return parseNpcGeneration(response == null ? null : response.response());
    }

    NpcGeneration parseNpcGeneration(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("Ollama returned no NPC generation response");
        }
        try {
            return objectMapper.readValue(rawResponse, NpcGeneration.class);
        } catch (Exception e) {
            throw new IllegalStateException("Ollama NPC response is not valid JSON: " + e.getMessage()
                    + " (response: " + truncateForError(rawResponse) + ")", e);
        }
    }

    private static String truncateForError(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    String buildPrompt(String shortDescription) {
        return """
                Du bist ein Spielleiter-Assistent für ein Pen-and-Paper-Rollenspiel.
                Erstelle aus der folgenden Kurzbeschreibung ein knappes NPC-Blatt.
                Antworte AUSSCHLIESSLICH mit STRICT JSON, ohne Markdown-Fences, ohne
                Einleitung, ohne Kommentar. Format:
                {"firstImpression":"...","goal":"...","attitude":"...","rulesAndTaboos":"...","quirks":"..."}

                Feldbedeutung (jeweils 1-3 kurze, spielbare Sätze auf Deutsch):
                - firstImpression: Erster Eindruck (Optik/Stimme) - wie der NPC auf den ersten
                  Blick/Klang wirkt
                - goal: Ziel - was der NPC gerade will/erreichen möchte
                - attitude: Haltung - z.B. freundlich, neutral, unfreundlich, misstrauisch, o.ä.
                - rulesAndTaboos: Regeln und Tabus - was der NPC niemals tun würde oder worauf
                  er/sie besteht
                - quirks: Eigenheiten und Marotten - ein einprägsames Detail oder Tick

                Kurzbeschreibung: %s
                """.formatted(shortDescription);
    }

    private record OllamaGenerateResponse(String response) {
    }
}
