package forge.gamemodes.match;

import forge.game.phase.PhaseType;

/** A player's explicit priority yield, scoped to the turn in which it was requested. */
public final class PriorityPass {
    public enum Mode { OFF, UNTIL_RESPONSE, WHOLE_TURN, UNTIL_END_STEP }

    private Mode mode = Mode.OFF;
    private int turn;
    private boolean stopped;

    public synchronized void start(final Mode next, final int currentTurn) {
        mode = next;
        turn = currentTurn;
        stopped = false;
    }

    public synchronized void clear() {
        start(Mode.OFF, 0);
    }

    public synchronized void response() {
        if (mode == Mode.UNTIL_RESPONSE) stopped = true;
    }

    public synchronized boolean shouldYield(final int currentTurn, final PhaseType phase) {
        update(currentTurn, phase);
        return mode != Mode.OFF && !stopped;
    }

    public synchronized boolean mustStop(final int currentTurn, final PhaseType phase) {
        update(currentTurn, phase);
        return mode != Mode.OFF && stopped;
    }

    public synchronized Mode mode() { return mode; }

    public synchronized boolean isYielding() { return mode != Mode.OFF && !stopped; }

    private void update(final int currentTurn, final PhaseType phase) {
        if (mode == Mode.OFF) return;
        if (turn != currentTurn) {
            clear();
        } else if (mode == Mode.UNTIL_END_STEP && phase != null
                && (phase == PhaseType.END_OF_TURN || phase.isAfter(PhaseType.END_OF_TURN))) {
            stopped = true;
        }
    }
}
