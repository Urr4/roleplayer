package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.VaultFileSummary;
import de.urr4.rp.roleplayer.domain.model.VaultFileWrite;

import java.util.List;
import java.util.Optional;

public interface VaultRepository {
    List<VaultFileSummary> listNotes(String worldFolderPath);

    Optional<String> getFileContent(String path);

    void commitChanges(String commitMessage, List<VaultFileWrite> writes);
}
