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
        final String harmonyValue = sa.getParamOrDefault("Harmony", "False");
        if (!"true".equalsIgnoreCase(harmonyValue)
                && !"false".equalsIgnoreCase(harmonyValue)) {
            throw new IllegalArgumentException(
                    "GrantSpellRule Harmony must be True or False");
        }
        final boolean harmony = Boolean.parseBoolean(harmonyValue);
        if (harmony && sa.hasParam("ManaConversion")) {
            throw new IllegalArgumentException(
                    "GrantSpellRule Harmony supplies its own mana conversion");
        }
        if (harmony && sa.hasParam("ReduceGeneric")) {
            throw new IllegalArgumentException(
                    "GrantSpellRule Harmony uses HarmonyReduction, not ReduceGeneric");
        }
        if (!harmony && sa.hasParam("HarmonyReduction")) {
            throw new IllegalArgumentException(
                    "GrantSpellRule HarmonyReduction requires Harmony$ True");
        }
        final int harmonyReduction = sa.hasParam("HarmonyReduction")
                ? AbilityUtils.calculateAmount(host,
                        sa.getParam("HarmonyReduction"), sa) : 0;
        final String stackingValue = sa.getParamOrDefault("Stacking", "False");
        if (!"true".equalsIgnoreCase(stackingValue)
                && !"false".equalsIgnoreCase(stackingValue)) {
            throw new IllegalArgumentException(
                    "GrantSpellRule Stacking must be True or False");
        }
        final boolean stacking = Boolean.parseBoolean(stackingValue);
        final String nameSnapshot = sa.getParamOrDefault("NameSnapshot", "");
        if (!nameSnapshot.isEmpty() && !"OpponentCards".equals(nameSnapshot)) {
            throw new IllegalArgumentException(
                    "GrantSpellRule NameSnapshot must be OpponentCards");
        }

        final Set<Player> players = new LinkedHashSet<>();
        for (final Player player : getDefinedPlayersOrTargeted(sa)) {
            if (player == null || !player.isInGame()) {
                continue;
            }
            players.add(player);
        }

        final java.util.Map<Player, Set<String>> namedCards =
                new java.util.LinkedHashMap<>();
        for (final Player player : players) {
            final Set<String> names = new LinkedHashSet<>();
            if ("OpponentCards".equals(nameSnapshot)) {
                for (final Player opponent : player.getOpponents()) {
                    for (final Card card : opponent.getAllCards()) {
                        if (card != null && card.getName() != null
                                && !card.getName().isEmpty()) {
                            names.add(card.getName());
                        }
                    }
                }
                // An empty snapshot must match no cards, not become the
                // unfiltered form used by legacy player spell rules.
                if (names.isEmpty()) {
                    names.add("\uE000");
                }
            }
            namedCards.put(player, names);
            PlayerSpellRuleRegistry.validateRuleDefinition(ruleKey, validCards,
                    validSpellAbilities, genericReduction, manaConversion,
                    harmony, harmonyReduction, names);
        }

        // Forge resolves effects on one game thread. Preflight every target
        // before the first write so a conflict cannot leave a partial grant.
        final Set<Player> refreshPlayers = new LinkedHashSet<>();
        for (final Player player : players) {
            if (stacking) {
                player.getSpellRuleRegistry().validateStackingRegistration(
                        ruleKey, validCards, validSpellAbilities,
                        genericReduction, manaConversion, harmony,
                        harmonyReduction, namedCards.get(player));
                if (harmony && (!player.getSpellRuleRegistry()
                        .hasHarmonyCoverage(validCards, validSpellAbilities,
                                namedCards.get(player))
                        || player.getSpellRuleRegistry()
                                .hasPendingHarmonyViewRefresh())) {
                    refreshPlayers.add(player);
                }
            } else {
                final boolean addsRule = player.getSpellRuleRegistry()
                        .wouldAddRegistration(ruleKey, validCards,
                                validSpellAbilities, genericReduction,
                                manaConversion, harmony, harmonyReduction,
                                namedCards.get(player));
                if (harmony && ((addsRule && !player.getSpellRuleRegistry()
                        .hasHarmonyCoverage(validCards, validSpellAbilities))
                        || player.getSpellRuleRegistry()
                                .hasPendingHarmonyViewRefresh())) {
                    refreshPlayers.add(player);
                }
            }
        }

        for (final Player player : players) {
            if (stacking) {
                player.getSpellRuleRegistry().registerStackingAfterPreflight(
                        ruleKey,
                        validCards, validSpellAbilities, genericReduction,
                        manaConversion, harmony, harmonyReduction,
                        namedCards.get(player));
            } else {
                player.getSpellRuleRegistry().registerAfterPreflight(ruleKey,
                        validCards, validSpellAbilities, genericReduction,
                        manaConversion, harmony, harmonyReduction,
                        namedCards.get(player));
            }
        }
        for (final Player player : refreshPlayers) {
            player.getSpellRuleRegistry().refreshCardViews();
        }
    }
}
