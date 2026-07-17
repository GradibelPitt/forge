package forge.game.staticability;

import forge.game.GameEntity;
import forge.game.card.Card;

public class StaticAbilityCantAttach {

    public static StaticAbility cantAttach(final GameEntity target, final Card card, boolean checkSBA) {
        final StaticAbility[] result = {null};
        target.getGame().visitStaticAbilityModeSources(
                StaticAbilityMode.CantAttach, ca -> {
            for (final StaticAbility stAb : ca.getStaticAbilities()) {
                if (!stAb.checkConditions(StaticAbilityMode.CantAttach)) {
                    continue;
                }

                if (applyCantAttachAbility(stAb, card, target, checkSBA)) {
                    result[0] = stAb;
                    return false;
                }
            }
            return true;
        });
        return result[0];
    }

    public static boolean applyCantAttachAbility(final StaticAbility stAb, final Card card, final GameEntity target, boolean checkSBA) {
        if (!stAb.matchesValidParam("ValidCard", card)) {
            return false;
        }

        if (!stAb.matchesValidParam("Target", target)) {
            return false;
        }

        if (stAb.hasParam("ValidCardToTarget")) {
            if (!(target instanceof Card)) {
                return false;
            }
            Card tcard = (Card) target;

            if (!stAb.matchesValid(card, stAb.getParam("ValidCardToTarget").split(","), tcard)) {
                return false;
            }
        }

        if ((checkSBA || !stAb.hasParam("ExceptionSBA")) && stAb.hasParam("Exceptions") && stAb.matchesValidParam("Exceptions", card)) {
            return false;
        }

        return true;
    }
}
