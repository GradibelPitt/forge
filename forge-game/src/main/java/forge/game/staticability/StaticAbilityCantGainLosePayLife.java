package forge.game.staticability;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

public class StaticAbilityCantGainLosePayLife {

    public static boolean anyCantGainLife(final Player player) {
        final Game game = player.getGame();
        for (final Card ca : game.getStaticAbilityModeSources(
                StaticAbilityMode.CantGainLife,
                StaticAbilityMode.CantChangeLife)) {
            for (final StaticAbility stAb : ca.getStaticAbilities()) {
                if (!(stAb.checkMode(StaticAbilityMode.CantGainLife) || stAb.checkMode(StaticAbilityMode.CantChangeLife))) {
                    continue;
                }

                if (!stAb.checkConditions()) {
                    continue;
                }

                if (applyCommonAbility(stAb, player)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean anyCantLoseLife(final Player player)  {
        final Game game = player.getGame();
        for (final Card ca : game.getStaticAbilityModeSources(
                StaticAbilityMode.CantLoseLife,
                StaticAbilityMode.CantChangeLife)) {
            for (final StaticAbility stAb : ca.getStaticAbilities()) {
                if (!(stAb.checkMode(StaticAbilityMode.CantLoseLife) || stAb.checkMode(StaticAbilityMode.CantChangeLife))) {
                    continue;
                }

                if (!stAb.checkConditions()) {
                    continue;
                }

                if (applyCommonAbility(stAb, player)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean anyCantPayLife(final Player player, final boolean effect, final SpellAbility cause)  {
        final Game game = player.getGame();
        for (final Card ca : game.getStaticAbilityModeSources(
                StaticAbilityMode.CantPayLife,
                StaticAbilityMode.CantLoseLife,
                StaticAbilityMode.CantChangeLife)) {
            for (final StaticAbility stAb : ca.getStaticAbilities()) {
                if (!(stAb.checkMode(StaticAbilityMode.CantPayLife) || stAb.checkMode(StaticAbilityMode.CantLoseLife) || stAb.checkMode(StaticAbilityMode.CantChangeLife))) {
                    continue;
                }

                if (!stAb.checkConditions()) {
                    continue;
                }

                if (stAb.hasParam("ForCost")) {
                    if ("True".equalsIgnoreCase(stAb.getParam("ForCost")) == effect) {
                        continue;
                    }
                }

                if (!stAb.matchesValidParam("ValidCause", cause)) {
                    continue;
                }

                if (applyCommonAbility(stAb, player)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean applyCommonAbility(final StaticAbility stAb, final Player player) {
        if (!stAb.matchesValidParam("ValidPlayer", player)) {
            return false;
        }
        return true;
    }

}
