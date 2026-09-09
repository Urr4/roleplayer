package de.urr4.rp.roleplayer.adapter.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.urr4.rp.roleplayer.domain.model.VaultFileSummary;
import de.urr4.rp.roleplayer.domain.model.VaultFileWrite;
import de.urr4.rp.roleplayer.domain.port.out.VaultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GitHubVaultAdapter implements VaultRepository {
    private final RestClient restClient;
    private final String owner;
    private final String repoName;
    private final String branch;

    @Autowired
    public GitHubVaultAdapter(@Value("${github.vault.token:}") String token,
                              @Value("${github.vault.repo:Urr4/roleplaying-worlds}") String repo,
                              @Value("${github.vault.branch:main}") String branch,
                              ObjectMapper objectMapper) {
        this(token, repo, branch, "https://api.github.com");
    }

    // Package-private constructor allowing tests to point at a local HTTP
    // server instead of the real GitHub API.
    GitHubVaultAdapter(String token, String repo, String branch, String apiBaseUrl) {
        // "owner/repo-name" must stay two separate path segments in the
        // GitHub API URLs below - if used as a single {repo} path variable,
        // Spring's URI template encoder percent-encodes the "/" inside it
        // (-> ".../repos/owner%2Frepo-name/...") which GitHub rejects with a
        // 404, since it can no longer tell the owner and repo name apart.
        String[] ownerAndName = repo.split("/", 2);
        this.owner = ownerAndName[0];
        this.repoName = ownerAndName.length > 1 ? ownerAndName[1] : "";
        this.branch = branch;
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Override
    public List<VaultFileSummary> listNotes(String worldFolderPath) {
        try {
            // worldFolderPath itself contains "/" (e.g. "content/worlds/x")
            // representing nested directories, so it must be concatenated
            // into the URI template string rather than passed as a {path}
            // value - a template *value* gets its "/" percent-encoded
            // (breaking the nested lookup with a 404), while a template
            // *string* correctly keeps "/" as a literal path separator.
            JsonNode response = restClient.get().uri("/repos/{owner}/{name}/contents/" + worldFolderPath, owner, repoName)
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.isArray()) return List.of();
            List<VaultFileSummary> notes = new ArrayList<>();
            for (JsonNode node : response) {
                if ("file".equals(node.path("type").asText()) && node.path("name").asText().endsWith(".md")) {
                    String path = node.path("path").asText();
                    String excerpt = getFileContent(path).map(content -> content.substring(0, Math.min(200, content.length()))).orElse("");
                    notes.add(new VaultFileSummary(path, excerpt));
                }
            }
            return notes;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return List.of();
            throw githubException("Failed to list vault notes", e);
        }
    }

    @Override
    public Optional<String> getFileContent(String path) {
        try {
            // Same "/" caveat as listNotes above.
            JsonNode response = restClient.get().uri("/repos/{owner}/{name}/contents/" + path, owner, repoName)
                    .retrieve().body(JsonNode.class);
            if (response == null || response.path("content").isMissingNode()) return Optional.empty();
            return Optional.of(new String(Base64.getMimeDecoder().decode(response.path("content").asText()), StandardCharsets.UTF_8));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            throw githubException("Failed to read vault file", e);
        }
    }

    @Override
    public void commitChanges(String commitMessage, List<VaultFileWrite> writes) {
        if (writes == null || writes.isEmpty()) return;
        try {
            String baseCommitSha = restClient.get().uri("/repos/{owner}/{name}/git/ref/heads/{branch}", owner, repoName, branch)
                    .retrieve().body(JsonNode.class).path("object").path("sha").asText();
            String baseTreeSha = restClient.get().uri("/repos/{owner}/{name}/git/commits/{sha}", owner, repoName, baseCommitSha)
                    .retrieve().body(JsonNode.class).path("tree").path("sha").asText();
            List<Map<String, String>> tree = new ArrayList<>();
            for (VaultFileWrite write : writes) {
                String blobSha = restClient.post().uri("/repos/{owner}/{name}/git/blobs", owner, repoName)
                        .body(Map.of("content", Base64.getEncoder().encodeToString(write.content().getBytes(StandardCharsets.UTF_8)), "encoding", "base64"))
                        .retrieve().body(JsonNode.class).path("sha").asText();
                tree.add(Map.of("path", write.path(), "mode", "100644", "type", "blob", "sha", blobSha));
            }
            String treeSha = restClient.post().uri("/repos/{owner}/{name}/git/trees", owner, repoName)
                    .body(Map.of("base_tree", baseTreeSha, "tree", tree))
                    .retrieve().body(JsonNode.class).path("sha").asText();
            String commitSha = restClient.post().uri("/repos/{owner}/{name}/git/commits", owner, repoName)
                    .body(Map.of("message", commitMessage, "tree", treeSha, "parents", List.of(baseCommitSha)))
                    .retrieve().body(JsonNode.class).path("sha").asText();
            restClient.patch().uri("/repos/{owner}/{name}/git/refs/heads/{branch}", owner, repoName, branch)
                    .body(Map.of("sha", commitSha))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        throw new IllegalStateException("Failed to update GitHub vault ref with HTTP " + clientResponse.getStatusCode().value());
                    }).toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw githubException("Failed to commit vault changes", e);
        }
    }

    private RuntimeException githubException(String message, RestClientResponseException e) {
        return new IllegalStateException(message + ": " + e.getStatusCode().value() + " " + e.getResponseBodyAsString(), e);
    }
}
