package forge.game.staticability;

import forge.game.HearthstoneMode;
import forge.game.card.Card;

public final class StaticAbilityCanBeAttacked {
    private StaticAbilityCanBeAttacked() {
    }

    public static boolean canBeAttacked(final Card defender) {
        return canBeAttackedBy(defender, null);
    }

    public static boolean canBeAttackedBy(final Card defender, final Card attacker) {
        // This only exposes creatures as direct attack targets. Evasion abilities
        // such as Flying and Horsemanship remain in the ordinary blocking rules.
        if (HearthstoneMode.isActive(defender.getGame()) && defender.isCreature()
                && (attacker == null
                || attacker.getController().isOpponentOf(defender.getController()))) {
            return true;
        }

        for (final Card source : defender.getGame().getStaticAbilityModeSources(
                StaticAbilityMode.CanBeAttacked)) {
            for (final StaticAbility stAb : source.getStaticAbilities()) {
                if (!stAb.checkConditions(StaticAbilityMode.CanBeAttacked)) {
                    continue;
                }
                if (!stAb.matchesValidParam("ValidDefender", defender)) {
                    continue;
                }
                if (attacker != null
                        && !stAb.matchesValidParam("ValidAttacker", attacker)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }
}
