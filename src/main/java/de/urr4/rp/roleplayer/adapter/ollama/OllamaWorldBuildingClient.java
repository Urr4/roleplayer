package de.urr4.rp.roleplayer.adapter.ollama;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.urr4.rp.roleplayer.domain.model.VaultNoteChange;
import de.urr4.rp.roleplayer.domain.port.out.WorldBuildingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OllamaWorldBuildingClient implements WorldBuildingClient {
    private static final Logger log = LoggerFactory.getLogger(OllamaWorldBuildingClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[.*]", Pattern.DOTALL);

    private final RestClient restClient;
    private final RestClient healthCheckRestClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OllamaWorldBuildingClient(@Value("${ollama.base-url}") String baseUrl,
                                     @Value("${ollama.worldbuilding-model:llama3.2}") String model,
                                     ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();

        SimpleClientHttpRequestFactory healthCheckRequestFactory = new SimpleClientHttpRequestFactory();
        healthCheckRequestFactory.setConnectTimeout(HEALTH_CHECK_TIMEOUT);
        healthCheckRequestFactory.setReadTimeout(HEALTH_CHECK_TIMEOUT);
        this.healthCheckRestClient = RestClient.builder().baseUrl(baseUrl).requestFactory(healthCheckRequestFactory).build();

        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public boolean isReachable() {
        try {
            healthCheckRestClient.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (ResourceAccessException e) {
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    @Override
    public List<VaultNoteChange> extractFacts(String worldName, String worldSlug, String chronicleName,
                                              String adventureName, String transcriptText,
                                              List<String> existingNoteSummaries) {
        OllamaGenerateResponse response = restClient.post()
                .uri("/api/generate")
                .body(Map.of(
                        "model", model,
                        "prompt", buildPrompt(worldName, worldSlug, chronicleName, adventureName, transcriptText, existingNoteSummaries),
                        "stream", false
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    throw new IllegalStateException("Ollama world extraction failed with HTTP " + clientResponse.getStatusCode().value());
                })
                .body(OllamaGenerateResponse.class);
        if (response == null || response.response() == null || response.response().isBlank()) {
            throw new IllegalStateException("Ollama returned no world-building response");
        }
        return parseNoteChanges(response.response());
    }

    List<VaultNoteChange> parseNoteChanges(String rawResponse) {
        Matcher matcher = JSON_ARRAY_PATTERN.matcher(rawResponse);
        if (!matcher.find()) {
            throw new IllegalStateException("No valid JSON array found in Ollama response");
        }
        String json = matcher.group();
        try {
            List<OllamaNoteChangeJson> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            return parsed.stream()
                    .filter(change -> isValidRelativePath(change.path()))
                    .map(change -> new VaultNoteChange(change.path(), change.title(), change.content(), "create".equalsIgnoreCase(change.action())))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Ollama world-building JSON: " + e.getMessage(), e);
        }
    }

    private boolean isValidRelativePath(String path) {
        boolean valid = path != null && !path.isBlank() && !path.startsWith("/") && !path.contains("..");
        if (!valid) {
            log.warn("Skipping invalid vault path from Ollama: {}", path);
        }
        return valid;
    }

    private String buildPrompt(String worldName, String worldSlug, String chronicleName, String adventureName,
                               String transcriptText, List<String> existingNoteSummaries) {
        String summaries = existingNoteSummaries == null || existingNoteSummaries.isEmpty()
                ? "Keine bestehenden Notizen."
                : String.join("\n---\n", existingNoteSummaries);
        return """
                Du bist ein Worldbuilding-Assistent für ein Pen-and-Paper-Rollenspiel.
                Analysiere das folgende Abenteuer-Transkript und extrahiere nur belastbare Weltfakten.
                Antworte AUSSCHLIESSLICH mit STRICT JSON, ohne Markdown-Fences, ohne Einleitung, ohne Kommentar.
                Format: ein JSON-Array von Objekten mit den Feldern
                {"path":"...","title":"...","action":"create"|"update","content":"..."}

                Regeln:
                - Verwende nur relative Pfade innerhalb dieser Welt, niemals führendes / und niemals ..
                - Die Welt heißt "%s" und hat den Slug "%s"
                - Nutze sinnvolle Unterordner wie Locations/, People/, Events/, Culture/
                - content muss gültiges Obsidian-Markdown sein
                - Wikilinks nur auf andere Notizen derselben Welt
                - Keine Spekulationen; nur Fakten aus dem Transkript
                - Wenn nichts Relevantes vorhanden ist, antworte mit []

                Chronik: %s
                Abenteuer: %s

                Bestehende Notiz-Zusammenfassungen:
                %s

                Transkript:
                %s
                """.formatted(worldName, worldSlug, chronicleName, adventureName, summaries, transcriptText);
    }

    private record OllamaGenerateResponse(String response) {
    }

    private record OllamaNoteChangeJson(String path, String title, String action, String content) {
    }
}
