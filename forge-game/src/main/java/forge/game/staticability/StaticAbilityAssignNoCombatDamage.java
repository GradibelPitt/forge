package forge.game.staticability;

import forge.game.card.Card;
import forge.game.card.CardCollection;

public class StaticAbilityAssignNoCombatDamage {

    public static boolean assignNoCombatDamage(final Card card) {
        CardCollection list = new CardCollection(card.getGame().getStaticAbilityModeSources(StaticAbilityMode.AssignNoCombatDamage));
        list.add(card);
        for (final Card ca : list) {
            for (final StaticAbility stAb : ca.getStaticAbilities()) {
                if (!stAb.checkConditions(StaticAbilityMode.AssignNoCombatDamage)) {
                    continue;
                }
                if (applyAssignNoCombatDamage(stAb, card)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean applyAssignNoCombatDamage(final StaticAbility stAb, final Card card) {
        if (!stAb.matchesValidParam("ValidCard", card)) {
            return false;
        }
        return true;
    }

}
