package forge.ai.ability;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.SpellAbilityAi;
import forge.game.ability.ApiType;
import forge.game.player.Player;
import forge.game.player.PlayerSpellRuleRegistry;
import forge.game.spellability.SpellAbility;

/** AI policy for source-independent player spell rules. */
public class GrantSpellRuleAi extends SpellAbilityAi {
    private static final String HARMONY_CONVERSION = "AnyType->AnyColor";

    @Override
    protected AiAbilityDecision checkApiLogic(final Player ai,
            final SpellAbility sa) {
        return decision(ai, sa);
    }

    @Override
    public AiAbilityDecision chkDrawback(final Player ai,
            final SpellAbility sa) {
        return decision(ai, sa);
    }

    @Override
    protected boolean allowAiLogicBypass(final Player ai,
            final SpellAbility sa) {
        return false;
    }

    static boolean isSafeForAi(final Player ai, final SpellAbility sa) {
        if (ai == null || sa == null || sa.getApi() != ApiType.GrantSpellRule
                || sa.getActivatingPlayer() != ai || sa.usesTargeting()
                || !"You".equals(sa.getParam("Defined"))
                || !"Permanent".equalsIgnoreCase(sa.getParam("Duration"))) {
            return false;
        }

        final String ruleKey = sa.getParam("RuleKey");
        if (ruleKey == null || ruleKey.trim().isEmpty()) {
            return false;
        }

        final Integer genericReduction = parseNonNegativeInteger(
                sa.getParamOrDefault("ReduceGeneric", "0"));
        if (genericReduction == null) {
            return false;
        }
        final String harmonyValue = sa.getParamOrDefault("Harmony", "False");
        if (!"true".equalsIgnoreCase(harmonyValue)
                && !"false".equalsIgnoreCase(harmonyValue)) {
            return false;
        }
        final boolean harmony = Boolean.parseBoolean(harmonyValue);
        if ((harmony && (sa.hasParam("ManaConversion")
                || sa.hasParam("ReduceGeneric")))
                || (!harmony && sa.hasParam("HarmonyReduction"))) {
            return false;
        }
        final Integer harmonyReduction = parseNonNegativeInteger(
                sa.getParamOrDefault("HarmonyReduction", "0"));
        if (harmonyReduction == null) {
            return false;
        }
        final String manaConversion = sa.getParamOrDefault(
                "ManaConversion", "").trim();
        if (!manaConversion.isEmpty()
                && !HARMONY_CONVERSION.equals(manaConversion)) {
            return false;
        }
        if (genericReduction == 0 && manaConversion.isEmpty() && !harmony) {
            return false;
        }

        final String stackingValue = sa.getParamOrDefault("Stacking", "False");
        if (!"true".equalsIgnoreCase(stackingValue)
                && !"false".equalsIgnoreCase(stackingValue)) {
            return false;
        }
        final boolean stacking = Boolean.parseBoolean(stackingValue);
        final String validCards = sa.getParamOrDefault("ValidCards", "Card");
        final String validSa = sa.getParamOrDefault("ValidSA", "Spell");

        try {
            PlayerSpellRuleRegistry.validateRuleDefinition(ruleKey, validCards,
                    validSa, genericReduction, manaConversion, harmony,
                    harmonyReduction);
            if (stacking) {
                if (genericReduction == 0 && harmonyReduction == 0
                        && ((harmony && ai.getSpellRuleRegistry()
                                .hasHarmonyCoverage(validCards, validSa)
                        || !harmony && ai.getSpellRuleRegistry()
                                .hasManaConversionCoverage(validCards, validSa,
                                        manaConversion)))) {
                    return false;
                }
                ai.getSpellRuleRegistry().validateStackingRegistration(ruleKey,
                        validCards, validSa, genericReduction, manaConversion,
                        harmony, harmonyReduction);
            } else if (!ai.getSpellRuleRegistry().wouldAddRegistration(ruleKey,
                    validCards, validSa, genericReduction, manaConversion,
                    harmony, harmonyReduction)) {
                return false;
            }
        } catch (final RuntimeException ex) {
            return false;
        }
        return true;
    }

    private static AiAbilityDecision decision(final Player ai,
            final SpellAbility sa) {
        return isSafeForAi(ai, sa)
                ? new AiAbilityDecision(100, AiPlayDecision.WillPlay)
                : new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    private static Integer parseNonNegativeInteger(final String value) {
        if (value == null || !value.matches("[0-9]+")) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException ex) {
            return null;
        }
    }
}
