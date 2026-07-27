package de.urr4.rp.roleplayer.domain.model;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Curated word lists used to roll random NPC attributes. Fixed for v1 — not
 * editable via the API.
 */
public final class NpcAttributePools {

    public static final List<String> MOTIVES = List.of(
            "Revenge", "Greed", "Protection", "Curiosity", "Duty", "Survival",
            "Ambition", "Fear", "Loyalty", "Boredom", "Love", "Redemption",
            "Power", "Freedom", "Faith"
    );

    public static final List<String> MOODS = List.of(
            "Hostile", "Friendly", "Nervous", "Calm", "Suspicious", "Desperate",
            "Arrogant", "Cheerful", "Exhausted", "Cunning", "Bored", "Anxious",
            "Jovial", "Grim", "Flirtatious"
    );

    private NpcAttributePools() {
    }

    public static String randomMotive() {
        return pick(MOTIVES);
    }

    public static String randomMood() {
        return pick(MOODS);
    }

    public static NpcStatus randomStatus() {
        NpcStatus[] values = NpcStatus.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private static String pick(List<String> pool) {
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
