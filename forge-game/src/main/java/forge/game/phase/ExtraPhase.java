package forge.game.phase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import forge.game.trigger.Trigger;

public class ExtraPhase {
    private final PhaseType phase;
    private List<Trigger> delTrig = Collections.synchronizedList(new ArrayList<>());
    private boolean windfuryCombat;

    public ExtraPhase(PhaseType phase) {
        this.phase = phase;
    }

    public PhaseType getPhase() {
        return phase;
    }

    public void addTrigger(Trigger deltrigger) {
        this.delTrig.add(deltrigger);
    }

    public List<Trigger> getDelayedTriggers() {
        return delTrig;
    }

    public boolean isWindfuryCombat() {
        return windfuryCombat;
    }

    public void setWindfuryCombat(final boolean windfuryCombat0) {
        windfuryCombat = windfuryCombat0;
    }

}
