package forge.game.player;

import java.util.HashSet;
import java.util.Set;

public final class FriendlyDamageTracker {
    private final Set<Integer> damagedEntityIds = new HashSet<>();

    public boolean record(final int entityId) {
        return damagedEntityIds.add(entityId);
    }

    public int size() {
        return damagedEntityIds.size();
    }

    public void clear() {
        damagedEntityIds.clear();
    }
}
