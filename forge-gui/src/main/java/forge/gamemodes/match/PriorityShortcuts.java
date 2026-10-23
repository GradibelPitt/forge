package forge.gamemodes.match;

import forge.game.player.PlayerView;
import forge.interfaces.IGameController;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

/** Shared settings and commands for every match UI; no frontend preferences are written. */
public final class PriorityShortcuts {
    private PriorityShortcuts() { }

    public static boolean isEnabled() {
        return FModel.getPreferences().getPrefBoolean(FPref.UI_PRIORITY_SHORTCUTS);
    }

    public static PriorityPass.Mode modeForDigit(final int digit) {
        return switch (digit) {
            case 1 -> PriorityPass.Mode.OFF;
            case 2 -> PriorityPass.Mode.UNTIL_RESPONSE;
            case 6 -> PriorityPass.Mode.WHOLE_TURN;
            case 0 -> PriorityPass.Mode.UNTIL_END_STEP;
            default -> null;
        };
    }

    public static int digitForMode(final PriorityPass.Mode mode) {
        return switch (mode) {
            case OFF -> 1;
            case UNTIL_RESPONSE -> 2;
            case WHOLE_TURN -> 6;
            case UNTIL_END_STEP -> 0;
        };
    }

    public static boolean perform(final IGameController controller, final IGameController input,
                                  final PlayerView player, final PriorityPass.Mode mode) {
        if (!isEnabled() || controller == null || input == null || player == null || mode == null) return false;
        controller.sendYieldUpdate(new YieldUpdate.PriorityShortcut(player, mode));
        if (mode == PriorityPass.Mode.OFF) input.passPriority();
        return true;
    }
}
