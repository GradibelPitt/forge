package forge.game.staticability;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

public class StaticAbilityIgnoreHexproofShroud {

    static public boolean ignore(GameEntity entity, final SpellAbility spellAbility, StaticAbility keyword) {
        final Game game = entity.getGame();
        final boolean hexproof = keyword.isKeyword(Keyword.HEXPROOF);
        final boolean shroud = keyword.isKeyword(Keyword.SHROUD);
        if (!hexproof && !shroud) {
            return legacyIgnore(game, entity, spellAbility, keyword);
        }
        final StaticAbilityMode indexedMode = hexproof
                ? StaticAbilityMode.IgnoreHexproof
                : StaticAbilityMode.IgnoreShroud;
        final boolean[] result = {false};
        game.visitStaticAbilityModeSources(indexedMode, ca -> {
            for (final StaticAbility stAb : ca.getStaticAbilities()) {
                if (hexproof && !stAb.checkConditions(
                        StaticAbilityMode.IgnoreHexproof)) {
                    continue;
                }
                if (shroud && !stAb.checkConditions(
                        StaticAbilityMode.IgnoreShroud)) {
                    continue;
                }
                if (commonAbility(stAb, entity, spellAbility)) {
                    result[0] = true;
                    return false;
                }
            }
            return true;
        });
        return result[0];
    }

    private static boolean legacyIgnore(final Game game,
            final GameEntity entity, final SpellAbility spellAbility,
            final StaticAbility keyword) {
        for (final Card ca : game.getCardsIn(
                ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            for (final StaticAbility stAb : ca.getStaticAbilities()) {
                if (keyword.isKeyword(Keyword.HEXPROOF)
                        && !stAb.checkConditions(
                        StaticAbilityMode.IgnoreHexproof)) {
                    continue;
                }
                if (keyword.isKeyword(Keyword.SHROUD)
                        && !stAb.checkConditions(
                        StaticAbilityMode.IgnoreShroud)) {
                    continue;
                }
                if (commonAbility(stAb, entity, spellAbility)) {
                    return true;
                }
            }
        }
        return false;
    }

    static protected boolean commonAbility(StaticAbility stAb, GameEntity entity, final SpellAbility spellAbility) {
        final Player activator = spellAbility.getActivatingPlayer();

        if (!stAb.matchesValidParam("Activator", activator)) {
            return false;
        }

        if (!stAb.matchesValidParam("ValidEntity", entity)) {
            return false;
        }

        return true;
    }
}
