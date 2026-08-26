package forge.game.staticability;

import forge.game.HearthstoneMode;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;

public final class StaticAbilityCanBeAttacked {
    private StaticAbilityCanBeAttacked() {
    }

    public static boolean canBeAttacked(final Card defender) {
        return canBeAttackedBy(defender, null);
    }

    public static boolean canBeAttackedBy(final Card defender, final Card attacker) {
        // A creature can be attacked when the attacker could block it under the
        // ordinary pairwise restrictions. This mirrors evasion without creating
        // a blocking assignment or importing the attacker's blocker readiness.
        if (HearthstoneMode.isActive(defender.getGame()) && defender.isCreature()
                && (attacker == null
                || attacker.getController().isOpponentOf(defender.getController()))) {
            return attacker == null || CombatUtil.canBlockByRestrictions(defender, attacker);
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
