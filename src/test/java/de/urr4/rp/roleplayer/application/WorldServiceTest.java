package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.adapter.memory.InMemoryWorldRepository;
import de.urr4.rp.roleplayer.domain.model.World;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class WorldServiceTest {

    @Test
    void createListGetAndSlugifyWorlds() {
        WorldService service = new WorldService(new InMemoryWorldRepository());

        World world = service.createWorld("Königreich Äon Süd");

        assertEquals("koenigreich-aeon-sued", world.slug());
        assertEquals(1, service.listWorlds().size());
        assertEquals(world, service.getWorld(world.id()));
    }

    @Test
    void getMissingWorldThrows() {
        WorldService service = new WorldService(new InMemoryWorldRepository());
        assertThrows(NoSuchElementException.class, () -> service.getWorld("missing"));
    }
}
