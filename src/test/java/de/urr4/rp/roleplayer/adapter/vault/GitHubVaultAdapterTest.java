package de.urr4.rp.roleplayer.adapter.vault;

import com.sun.net.httpserver.HttpServer;
import de.urr4.rp.roleplayer.domain.model.VaultFileSummary;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubVaultAdapterTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void keepsOwnerAndRepoNameAsSeparatePathSegmentsInsteadOfPercentEncodingTheSlash() throws IOException, InterruptedException {
        BlockingQueue<String> capturedPaths = new ArrayBlockingQueue<>(1);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            // "%2F" would be the buggy, percent-encoded slash - assert the
            // raw request path GitHub actually receives never contains it.
            capturedPaths.offer(exchange.getRequestURI().getRawPath());
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        GitHubVaultAdapter adapter = new GitHubVaultAdapter("test-token", "Urr4/roleplaying-worlds", "main",
                "http://localhost:" + server.getAddress().getPort());

        List<VaultFileSummary> notes = adapter.listNotes("content/worlds/test-welt");

        String requestPath = capturedPaths.poll(5, TimeUnit.SECONDS);
        assertTrue(notes.isEmpty());
        assertEquals("/repos/Urr4/roleplaying-worlds/contents/content/worlds/test-welt", requestPath);
        assertTrue(!requestPath.contains("%2F") && !requestPath.contains("%2f"));
    }
}
