package forge.ai;

import forge.ai.ability.GrantSpellRuleAi;
import forge.ai.ability.ReplaceCardsAi;
import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerAlways;
import forge.game.zone.ZoneType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;

public class PlayerSpellRuleAiSafetyTest extends AITest {
    private static final String REPLACE_BASE =
            "DB$ ReplaceCards | Defined$ %s | Zones$ Hand,Library "
                    + "| ValidCards$ Card.Black "
                    + "| ReplacementValid$ Card.nonBlack+nonColorless "
                    + "| MatchManaValue$ True";
    private static final String GRANT_BASE =
            "DB$ GrantSpellRule | Defined$ %s | RuleKey$ ai-safety "
                    + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                    + "| ReduceGeneric$ 2 "
                    + "| ManaConversion$ AnyType->AnyColor "
                    + "| Duration$ Permanent";

    @Test
    public void converterUsesDedicatedPolicies() {
        Assert.assertTrue(SpellApiToAi.Converter.get(ApiType.ReplaceCards)
                instanceof ReplaceCardsAi);
        Assert.assertTrue(SpellApiToAi.Converter.get(ApiType.GrantSpellRule)
                instanceof GrantSpellRuleAi);
    }

    @Test
    public void aiAcceptsTheKnownSelfBeneficialRenounceChain() {
        final Fixture fixture = createFixture();
        final SpellAbility replace = createRenounceChain(fixture, "You", "You");

        Assert.assertTrue(SpellApiToAi.Converter.get(replace)
                .canPlayWithSubs(fixture.ai, replace).willingToPlay());
    }

    @Test
    public void aiRejectsOpponentAndAllPlayerGrants() {
        final Fixture fixture = createFixture();

        final SpellAbility opponent = createAbility(fixture,
                String.format(GRANT_BASE, "Opponent"));
        final SpellAbility allPlayers = createAbility(fixture,
                String.format(GRANT_BASE, "AllPlayers"));

        Assert.assertFalse(canPlay(fixture.ai, opponent));
        Assert.assertFalse(canPlay(fixture.ai, allPlayers));
    }

    @Test
    public void aiRejectsReplacementOfAnotherPlayersCards() {
        final Fixture fixture = createFixture();
        final SpellAbility opponentReplace = createRenounceChain(
                fixture, "Opponent", "You");
        final SpellAbility opponentGrant = createRenounceChain(
                fixture, "You", "Opponent");

        Assert.assertFalse(canPlay(fixture.ai, opponentReplace));
        Assert.assertFalse(canPlay(fixture.ai, opponentGrant));
    }

    @Test
    public void playForSubCannotBypassAnUnsafeOpponentRoot() {
        final Fixture fixture = createFixture();
        final SpellAbility root = createGrantChain(fixture,
                "Opponent", "You", "PlayForSub");

        Assert.assertTrue(new RejectingLegacyAi().canPlayWithSubs(
                fixture.ai, root).willingToPlay());
        Assert.assertFalse(canPlay(fixture.ai, root));
    }

    @Test
    public void alwaysCannotBypassOptionalUnsafeTriggerButMandatoryStillRuns() {
        final Fixture fixture = createFixture();
        final SpellAbility root = createGrantChain(fixture,
                "Opponent", "You", "Always");
        root.setTrigger(new TriggerAlways(Collections.emptyMap(),
                fixture.source, true));
        root.setOptionalTrigger(true);

        Assert.assertTrue(new RejectingLegacyAi().doTriggerNoCostWithSubs(
                fixture.ai, root, false).willingToPlay());
        Assert.assertFalse(SpellApiToAi.Converter.get(root)
                .doTriggerNoCostWithSubs(fixture.ai, root, false)
                .willingToPlay());
        Assert.assertEquals(SpellApiToAi.Converter.get(root)
                .doTriggerNoCostWithSubs(fixture.ai, root, true).decision(),
                AiPlayDecision.WillPlay);
    }

    @Test
    public void aiRejectsUnprovenStandaloneReplacementWithoutCandidates() {
        final Fixture fixture = createFixture();
        final SpellAbility replace = createAbility(fixture,
                String.format(REPLACE_BASE, "You"));

        Assert.assertTrue(fixture.ai.getCardsIn(ZoneType.Hand).isEmpty());
        Assert.assertTrue(fixture.ai.getCardsIn(ZoneType.Library).isEmpty());
        Assert.assertFalse(canPlay(fixture.ai, replace));
    }

    @Test
    public void malformedOrUnsupportedScriptsAreRejectedWithoutMutation() {
        final Fixture fixture = createFixture();
        final String originalState = fixture.ai.getSpellRuleRegistry()
                .toStateString();
        final SpellAbility missingDuration = createAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ missing-duration "
                        + "| ReduceGeneric$ 2");
        final SpellAbility dynamicReduction = createAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ dynamic "
                        + "| ReduceGeneric$ Count$Valid Card | Duration$ Permanent");
        final SpellAbility invalidRestriction = createAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ invalid "
                        + "| ValidCards$ Card.Self | ReduceGeneric$ 2 "
                        + "| Duration$ Permanent");
        final SpellAbility falseManaValue = createAbility(fixture,
                "DB$ ReplaceCards | Defined$ You | Zones$ Hand,Library "
                        + "| ValidCards$ Card.Black "
                        + "| ReplacementValid$ Card.nonBlack+nonColorless "
                        + "| MatchManaValue$ False");
        final AbilitySub safeGrant = (AbilitySub) AbilityFactory.getAbility(
                String.format(GRANT_BASE, "You"), fixture.source);
        falseManaValue.setSubAbility(safeGrant);
        falseManaValue.setActivatingPlayer(fixture.ai);

        Assert.assertFalse(canPlay(fixture.ai, missingDuration));
        Assert.assertFalse(canPlay(fixture.ai, dynamicReduction));
        Assert.assertFalse(canPlay(fixture.ai, invalidRestriction));
        Assert.assertFalse(canPlay(fixture.ai, falseManaValue));
        Assert.assertEquals(fixture.ai.getSpellRuleRegistry().toStateString(),
                originalState);
    }

    @Test
    public void conflictingStableRuleIsRejectedDuringReadOnlyPreflight() {
        final Fixture fixture = createFixture();
        fixture.ai.getSpellRuleRegistry().register("ai-safety",
                "Card.nonColorless", "Spell", 1, "");
        final String originalState = fixture.ai.getSpellRuleRegistry()
                .toStateString();
        final SpellAbility grant = createAbility(fixture,
                String.format(GRANT_BASE, "You"));

        Assert.assertFalse(canPlay(fixture.ai, grant));
        Assert.assertEquals(fixture.ai.getSpellRuleRegistry().toStateString(),
                originalState);
    }

    @Test
    public void firstHarmonyOnlyStackingRuleIsAllowedWithoutPreflightWrites() {
        final Fixture fixture = createFixture();
        final String originalState = fixture.ai.getSpellRuleRegistry()
                .toStateString();
        final SpellAbility grant = createAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You "
                        + "| RuleKey$ harmony-only-stacking "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ManaConversion$ AnyType->AnyColor "
                        + "| Stacking$ True | Duration$ Permanent");

        Assert.assertTrue(canPlay(fixture.ai, grant));
        Assert.assertTrue(fixture.ai.getSpellRuleRegistry().isEmpty());
        Assert.assertEquals(fixture.ai.getSpellRuleRegistry().toStateString(),
                originalState);
    }

    @Test
    public void equivalentHarmonyOnlyStackingRuleIsRejectedWithoutGrowth() {
        final Fixture fixture = createFixture();
        fixture.ai.getSpellRuleRegistry().registerStacking("existing",
                "Card", "Spell", 1,
                "AnyType->AnyColor W->U");
        final String originalState = fixture.ai.getSpellRuleRegistry()
                .toStateString();
        final int originalSize = fixture.ai.getSpellRuleRegistry().size();
        final SpellAbility grant = createAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You "
                        + "| RuleKey$ duplicate-harmony-stacking "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ManaConversion$ AnyType->AnyColor "
                        + "| Stacking$ True | Duration$ Permanent");

        Assert.assertTrue(originalState.startsWith("1;"));
        Assert.assertFalse(canPlay(fixture.ai, grant));
        Assert.assertEquals(fixture.ai.getSpellRuleRegistry().size(),
                originalSize);
        Assert.assertEquals(fixture.ai.getSpellRuleRegistry().toStateString(),
                originalState);
    }

    @Test
    public void harmonyOnlyStackingWithDifferentRestrictionsIsAllowed() {
        final Fixture fixture = createFixture();
        fixture.ai.getSpellRuleRegistry().registerStacking("blue-only",
                "Card.Blue", "Instant", 0, "AnyType->AnyColor");
        final String originalState = fixture.ai.getSpellRuleRegistry()
                .toStateString();
        final SpellAbility grant = createAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You "
                        + "| RuleKey$ all-colored-harmony-stacking "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ManaConversion$ AnyType->AnyColor "
                        + "| Stacking$ True | Duration$ Permanent");

        Assert.assertTrue(canPlay(fixture.ai, grant));
        Assert.assertEquals(fixture.ai.getSpellRuleRegistry().toStateString(),
                originalState);
    }

    @Test
    public void restrictedExistingConversionDoesNotBlockFullHarmony() {
        final Fixture fixture = createFixture();
        fixture.ai.getSpellRuleRegistry().registerStacking(
                "restricted-harmony", "Card", "Spell", 0,
                "AnyType->AnyColor W<-U");
        final String originalState = fixture.ai.getSpellRuleRegistry()
                .toStateString();
        final SpellAbility grant = createAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You "
                        + "| RuleKey$ unrestricted-harmony-stacking "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ManaConversion$ AnyType->AnyColor "
                        + "| Stacking$ True | Duration$ Permanent");

        Assert.assertTrue(canPlay(fixture.ai, grant));
        Assert.assertEquals(fixture.ai.getSpellRuleRegistry().toStateString(),
                originalState);
    }

    @Test
    public void stackingRuleWithPositiveGenericReductionRemainsAllowed() {
        final Fixture fixture = createFixture();
        fixture.ai.getSpellRuleRegistry().registerStacking("existing-harmony",
                "Card.nonColorless", "Spell", 0,
                "AnyType->AnyColor");
        final String originalState = fixture.ai.getSpellRuleRegistry()
                .toStateString();
        final SpellAbility grant = createAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You "
                        + "| RuleKey$ useful-stacking "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ReduceGeneric$ 1 "
                        + "| ManaConversion$ AnyType->AnyColor "
                        + "| Stacking$ True | Duration$ Permanent");

        Assert.assertTrue(canPlay(fixture.ai, grant));
        Assert.assertEquals(fixture.ai.getSpellRuleRegistry().toStateString(),
                originalState);
    }

    @Test
    public void repeatedRenounceWithNoCardsAndNoNewRuleIsRejected() {
        final Fixture fixture = createFixture();
        fixture.ai.getSpellRuleRegistry().register("ai-safety",
                "Card.nonColorless", "Spell", 2,
                "AnyType->AnyColor");
        final String originalState = fixture.ai.getSpellRuleRegistry()
                .toStateString();
        final SpellAbility replace = createRenounceChain(fixture, "You", "You");

        Assert.assertTrue(fixture.ai.getCardsIn(ZoneType.Hand).isEmpty());
        Assert.assertTrue(fixture.ai.getCardsIn(ZoneType.Library).isEmpty());
        Assert.assertFalse(canPlay(fixture.ai, replace));
        Assert.assertEquals(fixture.ai.getSpellRuleRegistry().toStateString(),
                originalState);
    }

    private Fixture createFixture() {
        final Game game = initAndCreateGame();
        final Player ai = game.getPlayers().get(1);
        return new Fixture(ai, createCard("Ponder", ai));
    }

    private SpellAbility createRenounceChain(final Fixture fixture,
            final String replaceDefined, final String grantDefined) {
        final SpellAbility replace = createAbility(fixture,
                String.format(REPLACE_BASE, replaceDefined));
        final AbilitySub grant = (AbilitySub) AbilityFactory.getAbility(
                String.format(GRANT_BASE, grantDefined), fixture.source);
        replace.setSubAbility(grant);
        replace.setActivatingPlayer(fixture.ai);
        return replace;
    }

    private SpellAbility createGrantChain(final Fixture fixture,
            final String rootDefined, final String subDefined,
            final String aiLogic) {
        final SpellAbility root = createAbility(fixture,
                String.format(GRANT_BASE, rootDefined)
                        + " | AILogic$ " + aiLogic);
        final AbilitySub safeSub = (AbilitySub) AbilityFactory.getAbility(
                String.format(GRANT_BASE, subDefined), fixture.source);
        root.setSubAbility(safeSub);
        root.setActivatingPlayer(fixture.ai);
        return root;
    }

    private SpellAbility createAbility(final Fixture fixture,
            final String definition) {
        final SpellAbility result = AbilityFactory.getAbility(
                definition, fixture.source);
        result.setActivatingPlayer(fixture.ai);
        return result;
    }

    private static boolean canPlay(final Player ai, final SpellAbility sa) {
        return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa)
                .willingToPlay();
    }

    private static final class RejectingLegacyAi extends SpellAbilityAi {
        @Override
        protected AiAbilityDecision checkApiLogic(final Player ai,
                final SpellAbility sa) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
    }

    private record Fixture(Player ai, Card source) {
    }
}
