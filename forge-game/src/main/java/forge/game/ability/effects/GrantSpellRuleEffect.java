package forge.game.ability.effects;

import forge.game.ability.AbilityUtils;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.PlayerSpellRuleRegistry;
import forge.game.spellability.SpellAbility;

import java.util.LinkedHashSet;
import java.util.Set;

/** Grants a source-independent spell cost and mana-conversion rule. */
public class GrantSpellRuleEffect extends SpellAbilityEffect {
    @Override
    protected String getStackDescription(final SpellAbility sa) {
        return sa.getDescription();
    }

    @Override
    public void resolve(final SpellAbility sa) {
        final Card host = sa.getHostCard();
        if (!sa.hasParam("Duration")) {
            throw new IllegalArgumentException(
                    "GrantSpellRule requires Duration$ Permanent");
        }
        if (!"Permanent".equalsIgnoreCase(sa.getParam("Duration"))) {
            throw new IllegalArgumentException(
                    "GrantSpellRule currently supports only Permanent duration");
        }

        final String ruleKey = sa.getParam("RuleKey");
        if (ruleKey == null || ruleKey.trim().isEmpty()) {
            throw new IllegalArgumentException("GrantSpellRule requires RuleKey");
        }
        final String validCards = sa.getParamOrDefault("ValidCards", "Card");
        final String validSpellAbilities = sa.getParamOrDefault("ValidSA", "Spell");
        final int genericReduction = sa.hasParam("ReduceGeneric")
                ? AbilityUtils.calculateAmount(host, sa.getParam("ReduceGeneric"), sa) : 0;
        final String manaConversion = sa.getParamOrDefault("ManaConversion", "");
        final String stackingValue = sa.getParamOrDefault("Stacking", "False");
        if (!"true".equalsIgnoreCase(stackingValue)
                && !"false".equalsIgnoreCase(stackingValue)) {
            throw new IllegalArgumentException(
                    "GrantSpellRule Stacking must be True or False");
        }
        final boolean stacking = Boolean.parseBoolean(stackingValue);

        PlayerSpellRuleRegistry.validateRuleDefinition(ruleKey, validCards,
                validSpellAbilities, genericReduction, manaConversion);
        final Set<Player> players = new LinkedHashSet<>();
        for (final Player player : getDefinedPlayersOrTargeted(sa)) {
            if (player == null || !player.isInGame()) {
                continue;
            }
            players.add(player);
        }

        // Forge resolves effects on one game thread. Preflight every target
        // before the first write so a conflict cannot leave a partial grant.
        for (final Player player : players) {
            if (stacking) {
                player.getSpellRuleRegistry().validateStackingRegistration(
                        ruleKey, validCards, validSpellAbilities,
                        genericReduction, manaConversion);
            } else {
                player.getSpellRuleRegistry().validateRegistration(ruleKey,
                        validCards, validSpellAbilities, genericReduction,
                        manaConversion);
            }
        }

        for (final Player player : players) {
            if (stacking) {
                player.getSpellRuleRegistry().registerStacking(ruleKey,
                        validCards, validSpellAbilities, genericReduction,
                        manaConversion);
            } else {
                player.getSpellRuleRegistry().register(ruleKey,
                        validCards, validSpellAbilities, genericReduction,
                        manaConversion);
            }
        }
    }
}
