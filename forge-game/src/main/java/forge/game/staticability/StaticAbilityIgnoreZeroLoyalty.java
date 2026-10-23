package forge.game.staticability;

import forge.game.card.Card;

public class StaticAbilityIgnoreZeroLoyalty {
    public static boolean ignorePlaneswalkerZeroLoyaltyRule(final Card card) {
        final boolean[] applies = {false};
        card.getGame().visitStaticAbilityModeSources(StaticAbilityMode.IgnorePlaneswalkerZeroLoyaltyRule, source -> {
            for (final StaticAbility ability : source.getStaticAbilities()) {
                if (ability.checkConditions(StaticAbilityMode.IgnorePlaneswalkerZeroLoyaltyRule)
                        && ability.matchesValidParam("ValidCard", card)) {
                    applies[0] = true;
                    return false;
                }
            }
            return true;
        });
        return applies[0];
    }
}
