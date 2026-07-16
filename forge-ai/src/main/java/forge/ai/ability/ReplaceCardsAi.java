package forge.ai.ability;

import forge.ai.AiAbilityDecision;
import forge.ai.AiPlayDecision;
import forge.ai.SpellAbilityAi;
import forge.game.ability.ApiType;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** AI policy for random hidden-zone card replacement. */
public class ReplaceCardsAi extends SpellAbilityAi {
    private static final Set<String> SAFE_ZONES = Set.of("Hand", "Library");

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

    private static boolean isSafeForAi(final Player ai, final SpellAbility sa) {
        if (ai == null || sa == null || sa.getApi() != ApiType.ReplaceCards
                || sa.getActivatingPlayer() != ai || sa.usesTargeting()
                || !"You".equals(sa.getParam("Defined"))
                || !"Card.Black".equals(sa.getParam("ValidCards"))
                || !"Card.nonBlack+nonColorless".equals(
                        sa.getParam("ReplacementValid"))
                || !"true".equalsIgnoreCase(sa.getParam("MatchManaValue"))
                || !hasOnlySafeZones(sa.getParam("Zones"))) {
            return false;
        }

        // Random replacement alone has no generally provable card advantage.
        // The known safe use gains a beneficial, self-only persistent rule.
        final AbilitySub subAbility = sa.getSubAbility();
        return subAbility != null && subAbility.getApi() == ApiType.GrantSpellRule
                && GrantSpellRuleAi.isSafeForAi(ai, subAbility);
    }

    private static boolean hasOnlySafeZones(final String value) {
        if (value == null) {
            return false;
        }
        final String[] zones = Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .toArray(String[]::new);
        return zones.length == SAFE_ZONES.size()
                && new HashSet<>(Arrays.asList(zones)).equals(SAFE_ZONES);
    }

    private static AiAbilityDecision decision(final Player ai,
            final SpellAbility sa) {
        return isSafeForAi(ai, sa)
                ? new AiAbilityDecision(100, AiPlayDecision.WillPlay)
                : new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }
}
