package de.urr4.rp.roleplayer.adapter.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaWorldBuildingClientTest {
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
}
