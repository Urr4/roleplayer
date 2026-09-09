package de.urr4.rp.roleplayer.adapter.ollama;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

@Component
public class OllamaWorldBuildingClient implements WorldBuildingClient {
    private static final Logger log = LoggerFactory.getLogger(OllamaWorldBuildingClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);

    // JSON Schema passed as Ollama's "format" - constrains token sampling so
    // the model can only emit an array of objects matching this exact shape
    // (see https://ollama.com/blog/structured-outputs). Note: Ollama's
    // structured-output grammar doesn't support "action" as a plain string
    // enum well across all backends, so it's kept a plain string and still
    // validated/normalized ("create".equalsIgnoreCase(...)) after parsing.
    private static final Map<String, Object> NOTE_CHANGES_JSON_SCHEMA = Map.of(
            "type", "array",
            "items", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "path", Map.of("type", "string"),
                            "title", Map.of("type", "string"),
                            "action", Map.of("type", "string"),
                            "content", Map.of("type", "string")
                    ),
                    "required", List.of("path", "title", "action", "content")
            )
    );

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
    public String summarizeFacts(String worldName, String worldSlug, String chronicleName, String adventureName,
                                 String transcriptText) {
        OllamaGenerateResponse response = restClient.post()
                .uri("/api/generate")
                .body(Map.of(
                        "model", model,
                        "prompt", buildSummarizePrompt(worldName, chronicleName, adventureName, transcriptText),
                        "stream", false
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    String errorBody = new String(clientResponse.getBody().readAllBytes());
                    throw new IllegalStateException("Ollama world-fact summarization failed with HTTP "
                            + clientResponse.getStatusCode().value() + ": " + errorBody);
                })
                .body(OllamaGenerateResponse.class);
        if (response == null || response.response() == null || response.response().isBlank()) {
            throw new IllegalStateException("Ollama returned no world-fact summary");
        }
        return response.response().trim();
    }

    @Override
    public List<VaultNoteChange> mergeFactsIntoVault(String worldName, String worldSlug, String chronicleName, String adventureName,
                                                      String factsText, List<String> existingNoteSummaries) {
        String prompt = buildMergePrompt(worldName, worldSlug, chronicleName, adventureName, factsText, existingNoteSummaries);
        // Prefer an unconstrained call first: small models like llama3.2:3b
        // produce noticeably richer, more detailed note content (actual
        // facts, more notes) when not grammar-constrained by a JSON Schema -
        // the schema keeps them syntactically valid but measurably shrinks
        // and flattens the generated content (observed empty "content"
        // fields and far fewer notes in practice). The existing
        // block-scanning repair logic in parseNoteChanges() already fixes
        // the most common malformed-JSON patterns these models produce, so
        // this path succeeds in the large majority of cases.
        String rawResponse = generate(prompt, null);
        try {
            return parseNoteChanges(rawResponse);
        } catch (RuntimeException e) {
            // Only if the unconstrained response couldn't be parsed/repaired
            // at all, retry once with the JSON Schema "format" - this
            // grammar-constrains Ollama's token sampling to guarantee a
            // syntactically and structurally valid array of {path,title,
            // action,content} objects, trading content richness for
            // guaranteed-parseable structure as a last resort instead of
            // failing outright. Needs Ollama >= 0.5 (structured outputs).
            log.warn("Unconstrained Ollama vault-merge response could not be parsed ({}), retrying with a JSON Schema format", e.getMessage());
            String schemaConstrainedResponse = generate(prompt, NOTE_CHANGES_JSON_SCHEMA);
            return parseNoteChanges(schemaConstrainedResponse);
        }
    }

    private String generate(String prompt, Object format) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false
        ));
        if (format != null) {
            body.put("format", format);
        }
        OllamaGenerateResponse response = restClient.post()
                .uri("/api/generate")
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    String errorBody = new String(clientResponse.getBody().readAllBytes());
                    throw new IllegalStateException("Ollama vault merge failed with HTTP "
                            + clientResponse.getStatusCode().value() + ": " + errorBody);
                })
                .body(OllamaGenerateResponse.class);
        if (response == null || response.response() == null || response.response().isBlank()) {
            throw new IllegalStateException("Ollama returned no world-building response");
        }
        return response.response();
    }

    List<VaultNoteChange> parseNoteChanges(String rawResponse) {
        List<String> blocks = findTopLevelJsonBlocks(rawResponse);
        if (blocks.isEmpty()) {
            throw new IllegalStateException("No valid JSON array found in Ollama response: " + truncateForError(rawResponse));
        }

        List<JsonNode> elements = new ArrayList<>();
        for (String block : blocks) {
            // Small/weak models sometimes emit each note as its own
            // top-level "[ \"path\": ..., \"title\": ... ]" block using
            // square brackets instead of the requested {"path": ...} object
            // - and/or several such blocks back-to-back instead of one
            // JSON array. Detect and repair that specific, consistent
            // mistake instead of failing outright.
            String candidate = looksLikeObjectMisusingSquareBrackets(block)
                    ? "{" + block.substring(1, block.length() - 1) + "}"
                    : block;
            JsonNode node;
            try {
                node = objectMapper.readTree(candidate);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Ollama response is not valid JSON: " + e.getMessage() + " (response: " + truncateForError(candidate) + ")", e);
            }
            if (node.isArray()) {
                node.forEach(elements::add);
            } else {
                elements.add(node);
            }
        }

        List<VaultNoteChange> changes = new ArrayList<>();
        for (JsonNode element : elements) {
            if (!element.isObject()) {
                // Small/weak models sometimes ignore the requested object
                // shape and emit a flat array of strings (e.g. the field
                // names themselves) instead of {"path": ..., "title": ...}
                // objects. Fail with a message that points at the actual
                // model output instead of a cryptic Jackson stack trace.
                throw new IllegalStateException(
                        "Ollama returned a JSON array item that is not an object (" + element.getNodeType() + "): "
                                + truncateForError(element.toString())
                                + ". The model likely didn't follow the required {\"path\":...,\"title\":...} format - "
                                + "consider using a more capable OLLAMA_WORLDBUILDING_MODEL.");
            }
            OllamaNoteChangeJson change;
            try {
                change = objectMapper.treeToValue(element, OllamaNoteChangeJson.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse Ollama world-building JSON item: " + e.getMessage()
                        + " (item: " + truncateForError(element.toString()) + ")", e);
            }
            if (isValidRelativePath(change.path())) {
                changes.add(new VaultNoteChange(change.path(), change.title(), change.content(), "create".equalsIgnoreCase(change.action())));
            }
        }
        return changes;
    }

    /**
     * Scans the whole response for balanced top-level {@code [...]}/{@code
     * {...}} blocks, ignoring any prose text before/between/after them.
     * Handles nested brackets and quoted strings (including markdown link
     * syntax like {@code [text](url)} inside a "content" field) correctly by
     * tracking bracket depth with a single stack across both bracket types.
     */
    private static List<String> findTopLevelJsonBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '[' || c == '{') {
                int end = findMatchingBracket(text, i);
                if (end < 0) {
                    break;
                }
                blocks.add(text.substring(i, end + 1));
                i = end + 1;
            } else {
                i++;
            }
        }
        return blocks;
    }

    private static int findMatchingBracket(String text, int start) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                stack.push(c);
            } else if (c == ']' || c == '}') {
                if (stack.isEmpty()) {
                    return -1;
                }
                stack.pop();
                if (stack.isEmpty()) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * True if a top-level bracket block looks like {@code [ "key": value,
     * ... ]} - i.e. a single object's fields written directly inside square
     * brackets instead of curly braces - rather than a genuine array of
     * objects/values (which would start with {@code [{}, [, "..." (no
     * colon), a number, true/false/null, or be empty).
     */
    private static boolean looksLikeObjectMisusingSquareBrackets(String block) {
        if (!block.startsWith("[")) {
            return false;
        }
        int i = 1;
        while (i < block.length() && Character.isWhitespace(block.charAt(i))) {
            i++;
        }
        if (i >= block.length() || block.charAt(i) != '"') {
            return false;
        }
        i++;
        while (i < block.length()) {
            char c = block.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            i++;
        }
        if (i >= block.length()) {
            return false;
        }
        i++;
        while (i < block.length() && Character.isWhitespace(block.charAt(i))) {
            i++;
        }
        return i < block.length() && block.charAt(i) == ':';
    }

    private static String truncateForError(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    private boolean isValidRelativePath(String path) {
        boolean valid = path != null && !path.isBlank() && !path.startsWith("/") && !path.contains("..");
        if (!valid) {
            log.warn("Skipping invalid vault path from Ollama: {}", path);
        }
        return valid;
    }

    private String buildSummarizePrompt(String worldName, String chronicleName, String adventureName, String transcriptText) {
        return """
                Du bist ein Worldbuilding-Assistent für ein Pen-and-Paper-Rollenspiel.
                Lies das folgende Abenteuer-Transkript und fasse die erkannten Fakten über
                Charaktere, NPCs, die Welt, Kultur, Politik, Orte und Ereignisse in einem
                einzigen zusammenhängenden Fließtext zusammen (kein Markdown, keine
                Aufteilung in Dateien, keine JSON-Struktur).

                Regeln:
                - Schreibe in normalem Deutsch, in klaren Absätzen pro Thema (z.B. Charaktere,
                  NPCs, Orte, Kultur/Politik, Ereignisse).
                - Nur belastbare Fakten aus dem Transkript, keine Spekulationen.
                - Der Text wird anschließend von einem Menschen redigiert (z.B. um Namen zu
                  korrigieren), bevor er weiterverarbeitet wird - schreibe daher so, dass er
                  gut lesbar und leicht editierbar ist.
                - Wenn nichts Relevantes im Transkript steht, antworte mit einem kurzen Hinweis
                  darauf statt mit erfundenen Inhalten.

                Welt: %s
                Chronik: %s
                Abenteuer: %s

                Transkript:
                %s
                """.formatted(worldName, chronicleName, adventureName, transcriptText);
    }

    String buildMergePrompt(String worldName, String worldSlug, String chronicleName, String adventureName,
                                    String factsText, List<String> existingNoteSummaries) {
        String summaries = existingNoteSummaries == null || existingNoteSummaries.isEmpty()
                ? "Keine bestehenden Notizen."
                : String.join("\n---\n", existingNoteSummaries);
        return """
                Du bist ein Worldbuilding-Assistent für ein Pen-and-Paper-Rollenspiel.
                Der folgende Text enthält vom Spielleiter geprüfte und ggf. korrigierte
                Weltfakten (Charaktere, NPCs, Welt, Kultur, Politik, ...) aus einem Abenteuer.
                Wandle ihn in Obsidian-Markdown-Notizen um und merge ihn mit den bestehenden
                Notizen dieser Welt.
                Antworte AUSSCHLIESSLICH mit STRICT JSON, ohne Markdown-Fences, ohne Einleitung, ohne Kommentar.
                Format: ein JSON-Array von Objekten mit den Feldern
                {"path":"...","title":"...","action":"create"|"update","content":"..."}

                Regeln:
                - Verwende nur relative Pfade innerhalb dieser Welt, niemals führendes / und niemals ..
                - Die Welt heißt "%s" und hat den Slug "%s"
                - "path" darf NICHT mit dem Welt-Slug ("%s") oder "content/worlds/" beginnen -
                  der Ordner der Welt wird automatisch vorangestellt. Beginne "path" direkt mit
                  dem Unterordner, z.B. "Locations/Paspaturia", NICHT "%s/Locations/Paspaturia"
                - "path" MUSS auf ".md" enden, z.B. "Locations/Paspaturia.md"
                - Nutze sinnvolle Unterordner wie Locations/, People/, Events/, Culture/
                - content muss gültiges Obsidian-Markdown sein
                - Dies ist ein Obsidian-Vault: JEDE Erwähnung eines wichtigen Entities
                  (Person, Ort, Organisation, Gegenstand, Ereignis), das eine eigene Notiz
                  hat oder in dieser Antwort bekommt, MUSS als Obsidian-Wikilink verlinkt
                  werden - und zwar bei JEDER Erwähnung in JEDER Notiz, nicht nur beim
                  ersten Vorkommen
                - Verwende dafür AUSSCHLIESSLICH die Doppel-Klammer-Syntax [[Notiztitel]]
                  (optional mit Anzeigetext [[Notiztitel|Anzeigetext]]) - NIEMALS die
                  Markdown-Link-Syntax [Text](Pfad) für interne Verweise
                - Nutze für den Wikilink den Titel der Ziel-Notiz (z.B. [[Paspaturia]]),
                  nicht den vollen Pfad
                - Wikilinks ausschließlich auf andere Notizen DERSELBEN Welt, niemals auf
                  Notizen einer anderen Welt
                - Übernimm nur, was im Fakten-Text steht; keine Spekulationen
                - Wenn nichts Relevantes vorhanden ist, antworte mit []

                Chronik: %s
                Abenteuer: %s

                Bestehende Notiz-Zusammenfassungen:
                %s

                Geprüfter Fakten-Text:
                %s
                """.formatted(worldName, worldSlug, worldSlug, worldSlug, chronicleName, adventureName, summaries, factsText);
    }

    private record OllamaGenerateResponse(String response) {
    }

    private record OllamaNoteChangeJson(String path, String title, String action, String content) {
    }
}
