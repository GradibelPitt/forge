package forge.game.staticability;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

public class StaticAbilityCastWithFlash {

    public static boolean anyWithFlashNeedsInfo(final SpellAbility sa, final Card card, final Player activator) {
        return anyWithFlash(sa, card, activator, true);
    }

    public static boolean anyWithFlash(final SpellAbility sa, final Card card, final Player activator) {
        return anyWithFlash(sa, card, activator, false);
    }

    private static boolean anyWithFlash(final SpellAbility sa, final Card card,
            final Player activator, final boolean needsInfo) {
        final Game game = activator.getGame();
        final boolean[] result = {false};
        final boolean[] sawCard = {
                game.containsStaticAbilitySourceEquivalent(card)
        };
        game.visitStaticAbilityModeSources(StaticAbilityMode.CastWithFlash,
                ca -> {
            if (ca.equals(card)) {
                sawCard[0] = true;
            }
            for (final StaticAbility stAb : ca.getStaticAbilities()) {
                if (!stAb.checkConditions(StaticAbilityMode.CastWithFlash)) {
                    continue;
                }
                final boolean applies = needsInfo
                        ? applyWithFlashNeedsInfo(stAb, sa, card, activator)
                        : applyWithFlashAbility(stAb, sa, card, activator);
                if (applies) {
                    result[0] = true;
                    return false;
                }
            }
            return true;
        });
        if (!result[0] && !sawCard[0]) {
            for (final StaticAbility stAb : card.getStaticAbilities()) {
                if (!stAb.checkConditions(StaticAbilityMode.CastWithFlash)) {
                    continue;
                }
                if (needsInfo
                        ? applyWithFlashNeedsInfo(stAb, sa, card, activator)
                        : applyWithFlashAbility(stAb, sa, card, activator)) {
                    return true;
                }
            }
        }
        return result[0];
    }

    private static boolean commonParts(final StaticAbility stAb, final SpellAbility sa, final Card card, final Player activator, final boolean skipValidSA) {
        if (!stAb.matchesValidParam("ValidCard", card)) {
            return false;
        }

        if (!skipValidSA) {
            if (!stAb.matchesValidParam("ValidSA", sa)) {
                return false;
            }
        }

        if (!stAb.matchesValidParam("Caster", activator)) {
            return false;
        }
        return true;
    }

    public static boolean applyWithFlashNeedsInfo(final StaticAbility stAb, final SpellAbility sa, final Card card, final Player activator) {
        boolean info = false;
        String validSA = stAb.getParamOrDefault("ValidSA", "");
        if (validSA.contains("IsTargeting") || validSA.contains("XCost")) {
            info = true;
        }
        if (!commonParts(stAb, sa, card, activator, info)) {
            return false;
        }

        return info;
    }

    public static boolean applyWithFlashAbility(final StaticAbility stAb, final SpellAbility sa, final Card card, final Player activator) {
        if (!commonParts(stAb, sa, card, activator, false)) {
            return false;
        }

        return true;
    }
}
