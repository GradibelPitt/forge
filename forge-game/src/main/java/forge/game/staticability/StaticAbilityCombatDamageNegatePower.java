package forge.game.staticability;

import forge.game.card.Card;

public class StaticAbilityCombatDamageNegatePower {
    public static boolean combatDamageNegatePower(final Card card) {
        final boolean[] applies = {false};
        card.getGame().visitStaticAbilityModeSources(StaticAbilityMode.CombatDamageNegatePower, source -> {
            for (final StaticAbility ability : source.getStaticAbilities()) {
                if (ability.checkConditions(StaticAbilityMode.CombatDamageNegatePower)
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
