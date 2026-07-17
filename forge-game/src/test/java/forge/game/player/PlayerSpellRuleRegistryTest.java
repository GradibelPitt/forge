package forge.game.player;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardStateName;
import forge.card.CardType;
import forge.card.ColorSet;
import forge.card.GamePieceType;
import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.card.CardState;
import forge.game.cost.Cost;
import forge.game.cost.CostAdjustment;
import forge.game.mana.ManaConversionMatrix;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.mana.ManaPool;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordView;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityManaConvert;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

public class PlayerSpellRuleRegistryTest {
    private static final String RULE_KEY = "renounce-darkness:colored-spells";

    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void stableKeyRegistrationIsIdempotentAndDoesNotStack() {
        final Fixture fixture = new Fixture("stable key test");

        registerColoredSpellRule(fixture.grantedPlayer);
        registerColoredSpellRule(fixture.grantedPlayer);

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry().size(), 1);
        final ManaCostBeingPaid adjusted = adjustedCost(
                spell(fixture.grantedPlayer, "Idempotent Colored Spell", "5 W U"));
        assertCost(adjusted, 3, 1, 1);
    }

    @Test
    public void stableWouldAddQueryIsValidatedAndReadOnly() {
        final Fixture fixture = new Fixture("stable would-add query test");
        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "existing-stack", "Card.nonColorless", "Spell", 1, "");
        String before = fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();

        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry()
                .wouldAddRegistration("new-stable", "Card.nonColorless",
                        "Spell", 2, "AnyType->AnyColor"));
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);

        fixture.grantedPlayer.getSpellRuleRegistry().register(
                "new-stable", "Card.nonColorless", "Spell", 2,
                "AnyType->AnyColor");
        before = fixture.grantedPlayer.getSpellRuleRegistry().toStateString();
        Assert.assertFalse(fixture.grantedPlayer.getSpellRuleRegistry()
                .wouldAddRegistration("new-stable", "Card.nonColorless",
                        "Spell", 2, "AnyType->AnyColor"));
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);

        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry()
                        .wouldAddRegistration("new-stable",
                                "Card.nonColorless", "Spell", 3,
                                "AnyType->AnyColor"));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry()
                        .wouldAddRegistration("invalid", "Card.Self",
                                "Spell", 2, ""));
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);
    }

    @Test
    public void manaConversionCoverageUsesMatricesAndScopeImplication() {
        final Fixture fixture = new Fixture("mana conversion coverage query");
        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "broad-existing", "Card.nonColorless", "Spell", 3,
                "AnyType->AnyColor W->U");
        final String before = fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();
        final int beforeSize = fixture.grantedPlayer.getSpellRuleRegistry()
                .size();

        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasManaConversionCoverage(
                        "Card.Blue", "Instant", "AnyType->AnyColor"));
        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasManaConversionCoverage(
                        "Card.nonColorless", "Spell",
                        "AnyType->AnyColor"));

        final Fixture restrictive = new Fixture("restrictive conversion query");
        restrictive.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "restricted-existing", "Card", "Spell", 0,
                "AnyType->AnyColor W<-U");
        Assert.assertFalse(restrictive.grantedPlayer.getSpellRuleRegistry()
                .hasManaConversionCoverage(
                        "Card.Blue", "Instant", "AnyType->AnyColor"));

        final Fixture reverse = new Fixture("conversion reverse query");
        reverse.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "partial-existing", "Card", "Spell", 0, "W->U");
        Assert.assertFalse(reverse.grantedPlayer.getSpellRuleRegistry()
                .hasManaConversionCoverage(
                        "Card", "Spell", "W->U B->R"));

        final Fixture harmony = new Fixture("Harmony is not caster coverage");
        registerHarmonyRule(harmony.grantedPlayer);
        Assert.assertFalse(harmony.grantedPlayer.getSpellRuleRegistry()
                .hasManaConversionCoverage(
                        "Card.nonColorless", "Spell",
                        "AnyType->AnyColor"));

        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry()
                        .hasManaConversionCoverage(
                                "Card.Self", "Spell",
                                "AnyType->AnyColor"));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry()
                        .hasManaConversionCoverage(
                                "Card.nonColorless", "Spell", "Bogus"));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry()
                        .hasManaConversionCoverage(
                                null, "Spell", "AnyType->AnyColor"));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry()
                        .hasManaConversionCoverage(
                                "Card.White+nonWhite", "Spell",
                                "AnyType->AnyColor"));

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry().size(),
                beforeSize);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);
    }

    @Test
    public void manaConversionCoverageNormalizesRestrictionsAndAllowsUnion() {
        final Fixture fixture = new Fixture("normalized scope union query");
        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "blue", "Card.Blue+nonColorless+Blue", "Instant,Instant",
                0, "W->U W->U");
        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "red", "Instant.Red+Red", "Instant", 0, "W->U");
        final String before = fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();

        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasManaConversionCoverage(
                        "Instant.Red,Card.nonColorless+Blue,Instant.Red",
                        "Instant", "W->U"));
        Assert.assertFalse(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasManaConversionCoverage(
                        "Card.nonColorless", "Spell", "W->U"));
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);
    }

    @Test
    public void coloredSpellsUseThePlayerRuleWithoutACommandOrBattlefieldCard() {
        final Fixture fixture = new Fixture("card independent rule test");
        registerColoredSpellRule(fixture.grantedPlayer);
        final SpellAbility spell = spell(fixture.grantedPlayer, "Colored Spell", "5 W U");

        Assert.assertTrue(fixture.game.getCardsIn(ZoneType.Command).isEmpty());
        Assert.assertTrue(fixture.game.getCardsIn(ZoneType.Battlefield).isEmpty());

        final ManaCostBeingPaid adjusted = adjustedCost(spell);
        assertCost(adjusted, 3, 1, 1);

        final ManaConversionMatrix conversion = new ManaConversionMatrix();
        conversion.restoreColorReplacements();
        Assert.assertTrue(StaticAbilityManaConvert.manaConvert(
                conversion, fixture.grantedPlayer, spell.getHostCard(), spell));
        for (final byte manaType : MagicColor.WUBRGC) {
            final byte possibleUses = conversion.getPossibleColorUses(manaType);
            Assert.assertTrue((possibleUses & MagicColor.WHITE) != 0,
                    "mana type " + manaType + " should be usable as white");
            Assert.assertTrue((possibleUses & MagicColor.BLUE) != 0,
                    "mana type " + manaType + " should be usable as blue");
        }
    }

    @Test
    public void harmonyIsAVisibleDynamicKeywordAndClearRemovesIt() {
        final Fixture fixture = new Fixture("visible Harmony keyword test");
        final Card existing = Card.fromPaperCard(
                paper("Existing Harmony Spell", "1 U U"),
                fixture.grantedPlayer);
        fixture.grantedPlayer.getZone(ZoneType.Hand).add(existing);
        existing.setZone(fixture.grantedPlayer.getZone(ZoneType.Hand));
        existing.updateStateForView();
        Assert.assertFalse(existing.hasKeyword(Keyword.HARMONY));

        registerHarmonyRule(fixture.grantedPlayer);

        Assert.assertTrue(existing.hasKeyword(Keyword.HARMONY));
        Assert.assertTrue(existing.getView().getCurrentState().hasKeyword(
                Keyword.HARMONY));
        final KeywordView view = existing.getView().getCurrentState()
                .getKeywords().getValues(Keyword.HARMONY).iterator().next();
        Assert.assertEquals(view.title(), "调和");
        Assert.assertEquals(view.reminderText(),
                "你可以用任意颜色的法术力支付此咒语的法术力费用");
        Assert.assertTrue(existing.getView().getCurrentState().getAbilityText()
                .contains("调和（你可以用任意颜色的法术力支付此咒语的法术力费用）"));

        final Card createdAfterRule = Card.fromPaperCard(
                paper("Later Harmony Spell", "2 R"), fixture.grantedPlayer);
        Assert.assertTrue(createdAfterRule.hasKeyword(Keyword.HARMONY));
        final Card colorless = Card.fromPaperCard(
                paper("Colorless Non-Harmony Spell", "3"),
                fixture.grantedPlayer);
        Assert.assertFalse(colorless.hasKeyword(Keyword.HARMONY));

        fixture.grantedPlayer.getSpellRuleRegistry().clear();
        Assert.assertFalse(existing.hasKeyword(Keyword.HARMONY));
        Assert.assertFalse(existing.getView().getCurrentState().hasKeyword(
                Keyword.HARMONY));
        Assert.assertFalse(existing.getView().getCurrentState().getAbilityText()
                .contains("调和（你可以用任意颜色的法术力支付此咒语的法术力费用）"));
    }

    @Test
    public void intrinsicHarmonyStillUsesNormalCardKeywordSemantics() {
        final Fixture fixture = new Fixture("intrinsic Harmony compatibility");
        final PaperCard paper = new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:Intrinsic Harmony Spell",
                "ManaCost:U",
                "Types:Sorcery",
                "K:Harmony",
                "A:SP$ Draw | NumCards$ 1 | SpellDescription$ Test.",
                "Oracle:Test."
        )), "TST", CardRarity.Common);
        final Card card = Card.fromPaperCard(paper, fixture.grantedPlayer);
        fixture.grantedPlayer.getZone(ZoneType.Hand).add(card);
        card.setZone(fixture.grantedPlayer.getZone(ZoneType.Hand));
        card.updateStateForView();

        assertHarmonyProjection(card, true);
        final SpellAbility spell = card.getSpellAbilities().getFirst();
        spell.setActivatingPlayer(fixture.grantedPlayer);
        assertConversionApplies(fixture.grantedPlayer, spell);
        card.addCantHaveKeyword(Keyword.HARMONY,
                fixture.game.getNextTimestamp());
        card.updateKeywords();
        assertHarmonyProjection(card, false);
        final ManaConversionMatrix suppressed = new ManaConversionMatrix();
        suppressed.restoreColorReplacements();
        Assert.assertFalse(StaticAbilityManaConvert.manaConvert(suppressed,
                fixture.grantedPlayer, card, spell));
    }

    @Test
    public void harmonyViewTracksTypeColorAndKeywordSuppressionDifferentially() {
        final Fixture fixture = new Fixture("Harmony characteristic changes");
        final Card card = Card.fromPaperCard(
                paper("Changing Harmony Spell", "1 U"),
                fixture.grantedPlayer);
        fixture.grantedPlayer.getZone(ZoneType.Hand).add(card);
        card.setZone(fixture.grantedPlayer.getZone(ZoneType.Hand));
        card.updateStateForView();

        registerHarmonyRule(fixture.grantedPlayer);

        assertHarmonyProjection(card, true);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshCount(), 1);

        card.setType(CardType.parse("Land", false));
        assertHarmonyProjection(card, false);
        card.setType(CardType.parse("Instant", false));
        assertHarmonyProjection(card, true);

        card.setColor(ColorSet.fromMask(0));
        assertHarmonyProjection(card, false);
        card.setColor(ColorSet.fromNames("U"));
        assertHarmonyProjection(card, true);

        // Harmony is a player-rule projection, so card-layer "can't have"
        // suppression cannot make the live query and visible label diverge.
        card.addCantHaveKeyword(Keyword.HARMONY,
                fixture.game.getNextTimestamp());
        card.updateKeywords();
        assertHarmonyProjection(card, true);

        final long scansBeforeEquivalentRegistration = fixture.grantedPlayer
                .getSpellRuleRegistry().getHarmonyViewScannedCardCount();
        final int refreshesBeforeEquivalentRegistration = fixture.grantedPlayer
                .getSpellRuleRegistry().getLastHarmonyViewRefreshCount();
        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "equivalent-visible-harmony", "Card.nonColorless", "Spell",
                0, "", true, 2);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getHarmonyViewScannedCardCount(),
                scansBeforeEquivalentRegistration);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshCount(),
                refreshesBeforeEquivalentRegistration);
        assertHarmonyProjection(card, true);
    }

    @Test
    public void harmonyCacheTracksDoubleFacesAndFaceDownRoundTrips() {
        final Fixture fixture = new Fixture("Harmony face-state cache");
        final PaperCard paper = new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:Colored Harmony Face",
                "ManaCost:1 U",
                "Types:Sorcery",
                "A:SP$ Draw | NumCards$ 1 | SpellDescription$ Test.",
                "AlternateMode:DoubleFaced",
                "Oracle:Test.",
                "ALTERNATE",
                "Name:Colorless Harmony Face",
                "ManaCost:no cost",
                "Colors:colorless",
                "Types:Sorcery",
                "A:SP$ Draw | NumCards$ 1 | SpellDescription$ Test.",
                "Oracle:Test."
        )), "TST", CardRarity.Common);
        final Card card = Card.fromPaperCard(paper, fixture.grantedPlayer);
        fixture.grantedPlayer.getZone(ZoneType.Hand).add(card);
        card.setZone(fixture.grantedPlayer.getZone(ZoneType.Hand));
        card.updateStateForView();
        registerHarmonyRule(fixture.grantedPlayer);

        assertHarmonyProjection(card, true);
        Assert.assertTrue(card.setState(CardStateName.Backside, true, true));
        assertHarmonyProjection(card, false);
        Assert.assertTrue(card.setState(CardStateName.Original, true, true));
        assertHarmonyProjection(card, true);

        Assert.assertTrue(card.setState(CardStateName.FaceDown, true, true));
        assertHarmonyProjection(card, false);
        Assert.assertTrue(card.setState(CardStateName.Original, true, true));
        assertHarmonyProjection(card, true);
    }

    @Test
    public void failedHarmonyViewRefreshDoesNotBreakGrantAndCanRetry() {
        final Fixture fixture = new Fixture("Harmony view retry");
        final FailingAbilityTextCard card = new FailingAbilityTextCard(
                fixture.game.nextCardId(), fixture.game);
        card.setName("Retryable Harmony View");
        card.setOwner(fixture.grantedPlayer);
        card.setType(CardType.parse("Sorcery", false));
        card.setColor("U");
        fixture.grantedPlayer.getZone(ZoneType.Hand).add(card);
        card.setZone(fixture.grantedPlayer.getZone(ZoneType.Hand));

        registerHarmonyRule(fixture.grantedPlayer);

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .size(), 1);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshCount(), 0);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshFailureCount(), 1);
        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        Assert.assertTrue(card.hasKeyword(Keyword.HARMONY));

        registerHarmonyRule(fixture.grantedPlayer);

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshFailureCount(), 0);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshCount(), 1);
        Assert.assertFalse(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        assertHarmonyProjection(card, true);
        Assert.assertTrue(card.getView().getCurrentState().getAbilityText()
                .contains("调和"));
    }

    @Test
    public void directZoneAndColorRefreshFailuresAreNonFatalAndRetryable() {
        final Fixture fixture = new Fixture("direct Harmony view retry");
        final FailingAbilityTextCard card = new FailingAbilityTextCard(
                fixture.game.nextCardId(), fixture.game, true);
        card.setName("Direct Retry Harmony Spell");
        card.setOwner(fixture.grantedPlayer);
        card.setType(CardType.parse("Sorcery", false));
        card.setColor("U");
        registerHarmonyRule(fixture.grantedPlayer);
        fixture.grantedPlayer.getZone(ZoneType.Hand).add(card);

        card.setZone(fixture.grantedPlayer.getZone(ZoneType.Hand));

        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        registerHarmonyRule(fixture.grantedPlayer);
        Assert.assertFalse(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        assertHarmonyProjection(card, true);

        card.failNextAbilityText();
        card.setColor(ColorSet.fromMask(0));

        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        registerHarmonyRule(fixture.grantedPlayer);
        Assert.assertFalse(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        assertHarmonyProjection(card, false);
    }

    @Test
    public void idempotentGrantRetriesAPendingHarmonyViewRefresh() {
        final Fixture fixture = new Fixture("Harmony Grant retry");
        final FailingAbilityTextCard card = new FailingAbilityTextCard(
                fixture.game.nextCardId(), fixture.game, true);
        prepareVisibleColoredSpell(fixture, card, "Grant Retry Spell");
        final SpellAbility grant = grantAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You "
                        + "| RuleKey$ " + RULE_KEY + " "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| Harmony$ True | HarmonyReduction$ 2 "
                        + "| Duration$ Permanent");

        grant.resolve();
        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());

        grant.resolve();

        Assert.assertFalse(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshCount(), 1);
        assertHarmonyProjection(card, true);
    }

    @Test
    public void repeatedClearRetriesAPendingHarmonyViewRefresh() {
        final Fixture fixture = new Fixture("Harmony clear retry");
        final FailingAbilityTextCard card = new FailingAbilityTextCard(
                fixture.game.nextCardId(), fixture.game, false);
        prepareVisibleColoredSpell(fixture, card, "Clear Retry Spell");
        registerHarmonyRule(fixture.grantedPlayer);
        assertHarmonyProjection(card, true);
        card.failNextAbilityText();

        fixture.grantedPlayer.getSpellRuleRegistry().clear();

        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry()
                .isEmpty());
        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());

        fixture.grantedPlayer.getSpellRuleRegistry().clear();

        Assert.assertFalse(fixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshCount(), 1);
        assertHarmonyProjection(card, false);
    }

    @Test
    public void repeatedCopyAndRestoreRetryPendingHarmonyViews() {
        final Fixture copyFixture = new Fixture("Harmony copy retry");
        final FailingAbilityTextCard copiedCard = new FailingAbilityTextCard(
                copyFixture.game.nextCardId(), copyFixture.game, false);
        prepareVisibleColoredSpell(copyFixture, copiedCard, "Copy Retry Spell");
        registerHarmonyRule(copyFixture.grantedPlayer);
        copiedCard.failNextAbilityText();

        copyFixture.grantedPlayer.getSpellRuleRegistry().copyFrom(
                copyFixture.otherPlayer.getSpellRuleRegistry());
        Assert.assertTrue(copyFixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        copyFixture.grantedPlayer.getSpellRuleRegistry().copyFrom(
                copyFixture.otherPlayer.getSpellRuleRegistry());

        Assert.assertFalse(copyFixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        assertHarmonyProjection(copiedCard, false);

        final Fixture restoreFixture = new Fixture("Harmony restore retry");
        final FailingAbilityTextCard restoredCard = new FailingAbilityTextCard(
                restoreFixture.game.nextCardId(), restoreFixture.game, false);
        prepareVisibleColoredSpell(restoreFixture, restoredCard,
                "Restore Retry Spell");
        registerHarmonyRule(restoreFixture.grantedPlayer);
        restoreFixture.otherPlayer.getSpellRuleRegistry().register(
                "restore-target", "Card.nonColorless", "Spell", 1, "");
        final String state = restoreFixture.otherPlayer
                .getSpellRuleRegistry().toStateString();
        restoredCard.failNextAbilityText();

        restoreFixture.grantedPlayer.getSpellRuleRegistry()
                .restoreFromStateString(state);
        Assert.assertTrue(restoreFixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        restoreFixture.grantedPlayer.getSpellRuleRegistry()
                .restoreFromStateString(state);

        Assert.assertFalse(restoreFixture.grantedPlayer.getSpellRuleRegistry()
                .hasPendingHarmonyViewRefresh());
        assertHarmonyProjection(restoredCard, false);
    }

    @Test
    public void harmonyFollowsCardOwnershipWhileLegacyRulesFollowCaster() {
        final Fixture fixture = new Fixture("Harmony ownership semantics");
        registerHarmonyRule(fixture.grantedPlayer);
        final SpellAbility ownedByGranted = spellOwnedBy(
                fixture.grantedPlayer, fixture.otherPlayer,
                "Borrowed Owner-Harmony Spell", "1 U U");

        Assert.assertTrue(ownedByGranted.getHostCard().hasKeyword(
                Keyword.HARMONY));
        Assert.assertTrue(ownedByGranted.getHostCard().getView()
                .getCurrentState().hasKeyword(Keyword.HARMONY));
        Assert.assertEquals(adjustedCost(ownedByGranted).toString(), "{1}");
        assertConversionApplies(fixture.otherPlayer, ownedByGranted);

        final Fixture inverse = new Fixture("caster must not lend Harmony");
        registerHarmonyRule(inverse.otherPlayer);
        final SpellAbility ownedByOther = spellOwnedBy(
                inverse.grantedPlayer, inverse.otherPlayer,
                "Borrowed Non-Harmony Spell", "1 U U");
        Assert.assertFalse(ownedByOther.getHostCard().hasKeyword(
                Keyword.HARMONY));
        Assert.assertFalse(ownedByOther.getHostCard().getView()
                .getCurrentState().hasKeyword(Keyword.HARMONY));
        Assert.assertEquals(adjustedCost(ownedByOther).toString(),
                "{1}{U}{U}");
        final ManaConversionMatrix noHarmony = new ManaConversionMatrix();
        noHarmony.restoreColorReplacements();
        Assert.assertFalse(StaticAbilityManaConvert.manaConvert(noHarmony,
                inverse.otherPlayer, ownedByOther.getHostCard(), ownedByOther));

        inverse.otherPlayer.getSpellRuleRegistry().register(
                RULE_KEY + ":legacy-caster-rule",
                "Card.nonColorless", "Spell", 2, "AnyType->AnyColor");
        Assert.assertEquals(adjustedCost(ownedByOther).toString(), "{U}{U}");
        assertConversionApplies(inverse.otherPlayer, ownedByOther);
    }

    @Test
    public void hiddenLibraryHarmonyUsesEpochWithoutEagerAbilityTextRebuilds() {
        final Fixture fixture = new Fixture("Harmony hidden-library epoch");
        Card first = null;
        Card last = null;
        for (int i = 0; i < 20_000; i++) {
            final Card card = new Card(fixture.game.nextCardId(), fixture.game);
            card.setName("Hidden Harmony Card " + i);
            card.setOwner(fixture.grantedPlayer);
            card.setType(CardType.parse("Sorcery", false));
            card.setColor("U");
            fixture.grantedPlayer.getZone(ZoneType.Library).add(card);
            card.setZone(fixture.grantedPlayer.getZone(ZoneType.Library));
            if (first == null) {
                first = card;
            }
            last = card;
        }
        Assert.assertNotNull(first);
        Assert.assertNotNull(last);
        final String firstText = first.getView().getCurrentState()
                .getAbilityText();
        final String lastText = last.getView().getCurrentState()
                .getAbilityText();

        registerHarmonyRule(fixture.grantedPlayer);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshCount(), 0);
        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "hidden-library-stack", "Card.nonColorless", "Spell",
                0, "", true, 2);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshCount(), 0);

        Assert.assertTrue(first.hasKeyword(Keyword.HARMONY));
        Assert.assertTrue(last.hasKeyword(Keyword.HARMONY));
        Assert.assertEquals(first.getView().getCurrentState().getAbilityText(),
                firstText);
        Assert.assertEquals(last.getView().getCurrentState().getAbilityText(),
                lastText);

        first.setType(CardType.parse("Land", false));
        first.setColor("R");
        Assert.assertFalse(first.hasKeyword(Keyword.HARMONY));
        Assert.assertEquals(first.getView().getCurrentState().getAbilityText(),
                firstText);

        fixture.grantedPlayer.getSpellRuleRegistry().clear();
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshCount(), 0);
        Assert.assertFalse(last.hasKeyword(Keyword.HARMONY));
        Assert.assertEquals(last.getView().getCurrentState().getAbilityText(),
                lastText);
    }

    @Test
    public void coveredStackingHarmonySkipsTwentyThousandPublicViewScans() {
        final Fixture fixture = new Fixture("covered Harmony public scan");
        for (int i = 0; i < 20_000; i++) {
            final Card card = new Card(fixture.game.nextCardId(), fixture.game);
            card.setName("Public Nonspell " + i);
            card.setOwner(fixture.grantedPlayer);
            card.setType(CardType.parse("Land", false));
            card.setColor("U");
            fixture.grantedPlayer.getZone(ZoneType.Battlefield).add(card);
            card.setZone(fixture.grantedPlayer.getZone(ZoneType.Battlefield));
        }
        registerHarmonyRule(fixture.grantedPlayer);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getLastHarmonyViewRefreshCount(), 0);
        final long scansBeforeStacking = fixture.grantedPlayer
                .getSpellRuleRegistry().getHarmonyViewScannedCardCount();
        final long epochBeforeStacking = fixture.grantedPlayer
                .getSpellRuleRegistry().getHarmonyEpoch();

        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "covered-public-stack", "Card.nonColorless", "Spell",
                0, "", true, 2);

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getHarmonyViewScannedCardCount(), scansBeforeStacking);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getHarmonyEpoch(), epochBeforeStacking);
        Assert.assertEquals(adjustedCost(spell(fixture.grantedPlayer,
                "Stacked Harmony Reduction", "5 U")).toString(), "{2}");
    }

    @Test
    public void harmonyReductionRemovesColoredPipsButLegacyReductionDoesNot() {
        final Fixture harmony = new Fixture("Harmony reduction semantics");
        registerHarmonyRule(harmony.grantedPlayer);
        final SpellAbility harmonySpell = spell(harmony.grantedPlayer,
                "Harmony Double Blue", "1 U U");

        Assert.assertEquals(adjustedCost(harmonySpell).toString(), "{1}");
        assertConversionApplies(harmony.grantedPlayer, harmonySpell);

        final Fixture legacy = new Fixture("legacy generic reduction semantics");
        registerColoredSpellRule(legacy.grantedPlayer);
        final SpellAbility legacySpell = spell(legacy.grantedPlayer,
                "Legacy Double Blue", "1 U U");

        Assert.assertEquals(adjustedCost(legacySpell).toString(), "{U}{U}");
        Assert.assertFalse(legacySpell.getHostCard().hasKeyword(
                Keyword.HARMONY));
    }

    @Test
    public void harmonyStateRoundTripsAndLegacyFiveFieldStateStillRestores() {
        final Fixture source = new Fixture("Harmony state source");
        registerHarmonyRule(source.grantedPlayer);
        final String harmonyState = source.grantedPlayer
                .getSpellRuleRegistry().toStateString();
        final Fixture restored = new Fixture("Harmony state restored");

        restored.grantedPlayer.getSpellRuleRegistry()
                .restoreFromStateString(harmonyState);

        Assert.assertEquals(restored.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), harmonyState);
        final SpellAbility restoredSpell = spell(restored.grantedPlayer,
                "Restored Harmony Spell", "1 U U");
        Assert.assertTrue(restoredSpell.getHostCard().hasKeyword(
                Keyword.HARMONY));
        Assert.assertEquals(adjustedCost(restoredSpell).toString(), "{1}");

        final String legacyState = "0;"
                + encodeStateField("legacy") + ","
                + encodeStateField("Card.nonColorless") + ","
                + encodeStateField("Spell") + ",2,"
                + encodeStateField("AnyType->AnyColor");
        restored.grantedPlayer.getSpellRuleRegistry()
                .restoreFromStateString(legacyState);
        Assert.assertEquals(adjustedCost(spell(restored.grantedPlayer,
                "Legacy Restored Spell", "1 U U")).toString(), "{U}{U}");
    }

    @Test
    public void harmonyRejectsLegacyReductionInRegistrationAndStateRestore() {
        final Fixture fixture = new Fixture("ambiguous Harmony definition");
        registerColoredSpellRule(fixture.grantedPlayer);
        final RegistrySnapshot before = RegistrySnapshot.capture(
                fixture.grantedPlayer);

        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().register(
                        "ambiguous-harmony", "Card.nonColorless", "Spell",
                        2, "", true, 2));
        before.assertUnchanged(fixture.grantedPlayer);

        final String ambiguousState = "0;"
                + encodeStateField("ambiguous-harmony") + ","
                + encodeStateField("Card.nonColorless") + ","
                + encodeStateField("Spell") + ",2,,true,2";
        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry()
                        .restoreFromStateString(ambiguousState));
        before.assertUnchanged(fixture.grantedPlayer);
    }

    @Test
    public void colorlessSpellsDoNotMatchThePlayerRule() {
        final Fixture fixture = new Fixture("colorless exclusion test");
        registerColoredSpellRule(fixture.grantedPlayer);
        final SpellAbility spell = spell(fixture.grantedPlayer, "Colorless Spell", "7");

        final ManaCostBeingPaid adjusted = adjustedCost(spell);
        Assert.assertEquals(adjusted.getUnpaidShards(ManaCostShard.GENERIC), 7);

        final ManaConversionMatrix conversion = new ManaConversionMatrix();
        conversion.restoreColorReplacements();
        Assert.assertFalse(StaticAbilityManaConvert.manaConvert(
                conversion, fixture.grantedPlayer, spell.getHostCard(), spell));
    }

    @Test
    public void rulesAreIsolatedPerPlayer() {
        final Fixture fixture = new Fixture("player isolation test");
        registerColoredSpellRule(fixture.grantedPlayer);

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry().size(), 1);
        Assert.assertTrue(fixture.otherPlayer.getSpellRuleRegistry().isEmpty());

        final SpellAbility grantedSpell = spell(fixture.grantedPlayer, "Granted Spell", "4 R");
        final SpellAbility otherSpell = spell(fixture.otherPlayer, "Other Spell", "4 R");
        Assert.assertEquals(adjustedCost(grantedSpell).toString(), "{2}{R}");
        Assert.assertEquals(adjustedCost(otherSpell).toString(), "{4}{R}");

        final ManaConversionMatrix grantedConversion = new ManaConversionMatrix();
        grantedConversion.restoreColorReplacements();
        Assert.assertTrue(StaticAbilityManaConvert.manaConvert(
                grantedConversion, fixture.grantedPlayer, grantedSpell.getHostCard(), grantedSpell));

        final ManaConversionMatrix otherConversion = new ManaConversionMatrix();
        otherConversion.restoreColorReplacements();
        Assert.assertFalse(StaticAbilityManaConvert.manaConvert(
                otherConversion, fixture.otherPlayer, otherSpell.getHostCard(), otherSpell));
    }

    @Test
    public void rulesSurviveTurnCleanupCanBeClearedAndFreshGamesStartEmpty() {
        final Fixture fixture = new Fixture("registry lifecycle test");
        registerColoredSpellRule(fixture.grantedPlayer);

        fixture.grantedPlayer.onCleanupPhase();
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry().size(), 1);

        fixture.grantedPlayer.getSpellRuleRegistry().clear();
        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry().isEmpty());

        final Fixture freshFixture = new Fixture("fresh game lifecycle test");
        Assert.assertTrue(freshFixture.grantedPlayer.getSpellRuleRegistry().isEmpty());
        Assert.assertTrue(freshFixture.otherPlayer.getSpellRuleRegistry().isEmpty());
    }

    @Test
    public void grantSpellRuleAbilityIsIdempotentWithoutAnEmblem() {
        final Fixture fixture = new Fixture("GrantSpellRule integration test");
        final Card source = Card.fromPaperCard(
                paper("Renounce Darkness", "B"), fixture.grantedPlayer);
        source.setController(fixture.grantedPlayer,
                fixture.game.getNextTimestamp());
        final SpellAbility grant = AbilityFactory.getAbility(
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ RenounceDarkness.ColoredSpells "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| Harmony$ True | HarmonyReduction$ 2 "
                        + "| Duration$ Permanent",
                source);
        grant.setActivatingPlayer(fixture.grantedPlayer);

        grant.resolve();
        grant.resolve();

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry().size(), 1);
        final SpellAbility spell = spell(fixture.grantedPlayer,
                "Idempotent Colored Spell", "5 R");
        Assert.assertEquals(adjustedCost(spell).toString(), "{4}");
        Assert.assertTrue(spell.getHostCard().hasKeyword(Keyword.HARMONY));
        Assert.assertTrue(fixture.game.getCardsIn(ZoneType.Command).isEmpty());
    }

    @Test
    public void sourceDependentRestrictionsAreRejectedAtRegistration() {
        final Fixture fixture = new Fixture("source-independent validation test");

        final IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().register(
                        RULE_KEY, "Card.IsRemembered", "Spell", 2,
                        "AnyType->AnyColor"));

        Assert.assertTrue(error.getMessage().contains("source-independent"));
    }

    @Test
    public void genericReductionSaturatesInsteadOfOverflowing() {
        final Fixture fixture = new Fixture("generic reduction overflow test");
        fixture.grantedPlayer.getSpellRuleRegistry().register(
                "large-one", "Card.nonColorless", "Spell",
                Integer.MAX_VALUE, "");
        fixture.grantedPlayer.getSpellRuleRegistry().register(
                "large-two", "Card.nonColorless", "Spell",
                Integer.MAX_VALUE, "");

        final SpellAbility overflowSpell = spell(
                fixture.grantedPlayer, "Overflow Safe Spell", "5 U");
        overflowSpell.getMapParams().put("ReduceCost", "1");
        final ManaCostBeingPaid adjusted = adjustedCost(overflowSpell);

        Assert.assertEquals(adjusted.toString(), "{U}");
    }

    @Test
    public void stateEncodingRoundTripsAndMalformedStateIsAtomic() {
        final Fixture sourceFixture = new Fixture("state encoding source");
        sourceFixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                RULE_KEY, "Card.nonColorless", "Spell", 2,
                "AnyType->AnyColor");
        final String state = sourceFixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();
        final Fixture destinationFixture = new Fixture("state encoding destination");

        destinationFixture.grantedPlayer.getSpellRuleRegistry()
                .restoreFromStateString(state);
        Assert.assertEquals(destinationFixture.grantedPlayer
                .getSpellRuleRegistry().toStateString(), state);
        final SpellAbility restoredSpell = spell(
                destinationFixture.grantedPlayer,
                "Restored Conversion Spell", "U");
        final ManaConversionMatrix restoredConversion = new ManaConversionMatrix();
        restoredConversion.restoreColorReplacements();
        Assert.assertTrue(StaticAbilityManaConvert.manaConvert(
                restoredConversion, destinationFixture.grantedPlayer,
                restoredSpell.getHostCard(), restoredSpell));
        for (final byte manaType : ManaAtom.MANATYPES) {
            final byte colorUses = restoredConversion.getPossibleColorUses(manaType);
            Assert.assertEquals((byte) (colorUses & ManaAtom.ALL_MANA_COLORS),
                    ManaAtom.ALL_MANA_COLORS);
        }

        Assert.expectThrows(IllegalArgumentException.class,
                () -> destinationFixture.grantedPlayer.getSpellRuleRegistry()
                        .restoreFromStateString("not-a-sequence;broken"));
        Assert.assertEquals(destinationFixture.grantedPlayer
                .getSpellRuleRegistry().toStateString(), state);

        final String invalidConversion = stateWithManaConversion(state, "Bogus");
        Assert.expectThrows(IllegalArgumentException.class,
                () -> destinationFixture.grantedPlayer.getSpellRuleRegistry()
                        .restoreFromStateString(invalidConversion));
        Assert.assertEquals(destinationFixture.grantedPlayer
                .getSpellRuleRegistry().toStateString(), state);
    }

    @Test
    public void zeroMaskConversionsFailStateRestoreAtomically() {
        final Fixture fixture = new Fixture("zero mask state restore test");
        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "preserved", "Card.nonColorless", "Spell", 1,
                "AnyType->AnyColor");
        final RegistrySnapshot before = RegistrySnapshot.capture(
                fixture.grantedPlayer);
        final String zeroSource = stateWithManaConversion(
                before.serialized(), "nonWUBRGC->AnyColor");
        final String zeroDestination = stateWithManaConversion(
                before.serialized(), "AnyType->nonWUBRGC");
        final String zeroRestrictedDestination = stateWithManaConversion(
                before.serialized(), "AnyType<-nonWUBRGC");

        final IllegalArgumentException sourceError = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry()
                        .restoreFromStateString(zeroSource));
        Assert.assertEquals(sourceError.getMessage(),
                "Invalid mana conversion source selects no mana types: "
                        + "nonWUBRGC->AnyColor");
        before.assertUnchanged(fixture.grantedPlayer);

        final IllegalArgumentException destinationError = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry()
                        .restoreFromStateString(zeroDestination));
        Assert.assertEquals(destinationError.getMessage(),
                "Invalid mana conversion destination selects no mana types: "
                        + "AnyType->nonWUBRGC");
        before.assertUnchanged(fixture.grantedPlayer);

        final IllegalArgumentException restrictedDestinationError = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry()
                        .restoreFromStateString(zeroRestrictedDestination));
        Assert.assertEquals(restrictedDestinationError.getMessage(),
                "Invalid mana conversion destination selects no mana types: "
                        + "AnyType<-nonWUBRGC");
        before.assertUnchanged(fixture.grantedPlayer);
    }

    @Test
    public void validMultipleManaConversionsRoundTripAndApply() {
        final Fixture sourceFixture = new Fixture("multi-pair conversion source");
        sourceFixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "multi-pair", "Card.nonColorless", "Spell", 0,
                "W->U U<-WU");
        final String state = sourceFixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();
        final Fixture restoredFixture = new Fixture("multi-pair conversion restored");

        restoredFixture.grantedPlayer.getSpellRuleRegistry()
                .restoreFromStateString(state);

        Assert.assertEquals(restoredFixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), state);
        final SpellAbility cast = spell(restoredFixture.grantedPlayer,
                "Multi-Pair Conversion Spell", "U");
        final ManaConversionMatrix conversion = new ManaConversionMatrix();
        conversion.restoreColorReplacements();
        Assert.assertTrue(StaticAbilityManaConvert.manaConvert(
                conversion, restoredFixture.grantedPlayer,
                cast.getHostCard(), cast));
        Assert.assertEquals(conversion.getPossibleColorUses(MagicColor.WHITE),
                (byte) (MagicColor.WHITE | MagicColor.BLUE));
        Assert.assertEquals(conversion.getPossibleColorUses(MagicColor.BLUE),
                MagicColor.BLUE);
    }

    @Test
    public void malformedManaConversionFailsAtRegistration() {
        final Fixture fixture = new Fixture("mana conversion validation test");

        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().register(
                        RULE_KEY, "Card.nonColorless", "Spell", 2, "Bogus"));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().register(
                        RULE_KEY, "Card.nonColorless", "Spell", 2,
                        "AnyType->"));
        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry().isEmpty());
    }

    @Test
    public void emptyManaConversionSourceFailsWithoutRegistryPollution() {
        final Fixture fixture = new Fixture("empty mana conversion source test");
        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "preserved", "Card.nonColorless", "Spell", 1, "");
        final RegistrySnapshot before = RegistrySnapshot.capture(
                fixture.grantedPlayer);

        final IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().register(
                        RULE_KEY, "Card.nonColorless", "Spell", 2,
                        "nonWUBRGC->AnyColor"));

        Assert.assertEquals(error.getMessage(),
                "Invalid mana conversion source selects no mana types: "
                        + "nonWUBRGC->AnyColor");
        before.assertUnchanged(fixture.grantedPlayer);
    }

    @Test
    public void emptyManaConversionDestinationFailsWithoutRegistryPollution() {
        final Fixture fixture = new Fixture("empty mana conversion destination test");
        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "preserved", "Card.nonColorless", "Spell", 1, "");
        final RegistrySnapshot before = RegistrySnapshot.capture(
                fixture.grantedPlayer);

        final IllegalArgumentException additiveError = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().register(
                        RULE_KEY, "Card.nonColorless", "Spell", 2,
                        "AnyType->nonWUBRGC"));
        Assert.assertEquals(additiveError.getMessage(),
                "Invalid mana conversion destination selects no mana types: "
                        + "AnyType->nonWUBRGC");
        before.assertUnchanged(fixture.grantedPlayer);

        final IllegalArgumentException restrictiveError = Assert.expectThrows(
                IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().register(
                        RULE_KEY, "Card.nonColorless", "Spell", 2,
                        "AnyType<-nonWUBRGC"));
        Assert.assertEquals(restrictiveError.getMessage(),
                "Invalid mana conversion destination selects no mana types: "
                        + "AnyType<-nonWUBRGC");
        before.assertUnchanged(fixture.grantedPlayer);
    }

    @Test
    public void registryCopyPreservesRulesAndTheStackingSequence() {
        final Fixture sourceFixture = new Fixture("registry copy source");
        sourceFixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                RULE_KEY, "Card.nonColorless", "Spell", 2,
                "AnyType->AnyColor");
        final Fixture destinationFixture = new Fixture("registry copy destination");

        destinationFixture.grantedPlayer.getSpellRuleRegistry().copyFrom(
                sourceFixture.grantedPlayer.getSpellRuleRegistry());
        destinationFixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                RULE_KEY, "Card.nonColorless", "Spell", 2,
                "AnyType->AnyColor");

        Assert.assertEquals(
                destinationFixture.grantedPlayer.getSpellRuleRegistry().size(), 2);
        Assert.assertEquals(adjustedCost(spell(destinationFixture.grantedPlayer,
                "Copied Rule Spell", "5 G")).toString(), "{1}{G}");
    }

    @Test
    public void nonSpellAndEffectPaymentEntryPointsDoNotMatch() {
        final Fixture fixture = new Fixture("non-spell hard gate test");
        registerColoredSpellRule(fixture.grantedPlayer);
        final SpellAbility cast = spell(fixture.grantedPlayer,
                "Effect Payment Spell", "3 U");

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getGenericReduction(cast.getHostCard(), null), 0);
        final ManaConversionMatrix nullAbilityConversion = new ManaConversionMatrix();
        nullAbilityConversion.restoreColorReplacements();
        Assert.assertFalse(StaticAbilityManaConvert.manaConvert(
                nullAbilityConversion, fixture.grantedPlayer,
                cast.getHostCard(), null));

        final ManaCostBeingPaid wardCost = new ManaCostBeingPaid(
                new ManaCost("3 U"));
        Assert.assertTrue(CostAdjustment.adjust(wardCost, cast,
                fixture.grantedPlayer, null, false, true));
        Assert.assertEquals(wardCost.toString(), "{3}{U}");

        final Card abilityHost = Card.fromPaperCard(
                paper("Activated Ability Host", "U"), fixture.grantedPlayer);
        final SpellAbility activated = AbilityFactory.getAbility(
                "AB$ Draw | Cost$ 2 U | NumCards$ 1 | SpellDescription$ Test.",
                abilityHost);
        activated.setActivatingPlayer(fixture.grantedPlayer);
        Assert.assertTrue(activated.isActivatedAbility());
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getGenericReduction(abilityHost, activated), 0);
        final ManaConversionMatrix activatedConversion = new ManaConversionMatrix();
        activatedConversion.restoreColorReplacements();
        Assert.assertFalse(StaticAbilityManaConvert.manaConvert(
                activatedConversion, fixture.grantedPlayer, abilityHost, activated));
    }

    @Test
    public void trueCopiedSpellsDoNotMatch() {
        final Fixture fixture = new Fixture("copied spell hard gate test");
        registerColoredSpellRule(fixture.grantedPlayer);
        final SpellAbility copied = spell(fixture.grantedPlayer,
                "Copied Stack Spell", "4 U");
        copied.getHostCard().setGamePieceType(GamePieceType.COPIED_SPELL);
        copied.setCopied(true);

        Assert.assertTrue(copied.getHostCard().isCopiedSpell());
        Assert.assertTrue(copied.isCopied());
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .getGenericReduction(copied.getHostCard(), copied), 0);
        final ManaConversionMatrix conversion = new ManaConversionMatrix();
        conversion.restoreColorReplacements();
        Assert.assertFalse(StaticAbilityManaConvert.manaConvert(
                conversion, fixture.grantedPlayer, copied.getHostCard(), copied));
    }

    @Test
    public void playEffectAndDefinedAlternativeCostsStillMatchDuringPaymentInitialization() {
        final Fixture fixture = new Fixture("paid play effect hard gate test");
        registerColoredSpellRule(fixture.grantedPlayer);

        final SpellAbility playEffect = spell(fixture.grantedPlayer,
                "Paid Play Effect Spell", "7 U");
        playEffect.setCastFromPlayEffect(true);
        playEffect.setPayCosts(new Cost("4 U", false));
        Assert.assertEquals(adjustedPayCost(playEffect).toString(), "{2}{U}");
        assertConversionApplies(fixture.grantedPlayer, playEffect);

        final SpellAbility alternative = spell(fixture.grantedPlayer,
                "Alternative Cost Spell", "7 U")
                .copyWithDefinedCost(new Cost("5 U", false));
        alternative.setActivatingPlayer(fixture.grantedPlayer);
        Assert.assertTrue(alternative.isSpell());
        Assert.assertEquals(adjustedPayCost(alternative).toString(), "{3}{U}");
        assertConversionApplies(fixture.grantedPlayer, alternative);

        final Fixture harmony = new Fixture("Harmony alternative cost path");
        registerHarmonyRule(harmony.grantedPlayer);
        final SpellAbility harmonyAlternative = spell(harmony.grantedPlayer,
                "Harmony Alternative Cost Spell", "7 U")
                .copyWithDefinedCost(new Cost("1 U U", false));
        harmonyAlternative.setActivatingPlayer(harmony.grantedPlayer);
        Assert.assertEquals(adjustedPayCost(harmonyAlternative).toString(),
                "{1}");
        assertConversionApplies(harmony.grantedPlayer, harmonyAlternative);
    }

    @Test
    public void genericReductionPreservesNonGenericShardsAndHandlesExpandedX() {
        final Fixture fixture = new Fixture("mana shape hard gate test");
        registerColoredSpellRule(fixture.grantedPlayer);
        final SpellAbility shaped = spell(fixture.grantedPlayer,
                "Mixed Mana Shape Spell", "4 W/U S C");

        final ManaCostBeingPaid adjusted = adjustedCost(shaped);
        Assert.assertEquals(adjusted.getUnpaidShards(ManaCostShard.GENERIC), 2);
        Assert.assertEquals(adjusted.getUnpaidShards(ManaCostShard.WU), 1);
        Assert.assertEquals(adjusted.getUnpaidShards(ManaCostShard.S), 1);
        Assert.assertEquals(adjusted.getUnpaidShards(ManaCostShard.COLORLESS), 1);

        final SpellAbility xSpell = spell(fixture.grantedPlayer,
                "X Mana Shape Spell", "X U");
        final ManaCostBeingPaid unresolvedX = new ManaCostBeingPaid(
                xSpell.getHostCard().getManaCost());
        Assert.assertTrue(CostAdjustment.adjust(unresolvedX, xSpell,
                fixture.grantedPlayer, null, false, false));
        Assert.assertEquals(unresolvedX.getXcounter(), 1);
        Assert.assertEquals(unresolvedX.getUnpaidShards(ManaCostShard.BLUE), 1);

        final ManaCostBeingPaid expandedX = new ManaCostBeingPaid(
                xSpell.getHostCard().getManaCost());
        expandedX.setXManaCostPaid(3, "");
        Assert.assertTrue(CostAdjustment.adjust(expandedX, xSpell,
                fixture.grantedPlayer, null, false, false));
        Assert.assertEquals(expandedX.getXcounter(), 0);
        Assert.assertEquals(expandedX.getUnpaidShards(ManaCostShard.GENERIC), 1);
        Assert.assertEquals(expandedX.getUnpaidShards(ManaCostShard.BLUE), 1);
    }

    @Test
    public void twoGenericHybridUsesDecreaseGenericManaSemantics() {
        final Fixture reductionOne = new Fixture("two hybrid reduction one test");
        reductionOne.grantedPlayer.getSpellRuleRegistry().register(
                "reduce-one", "Card.nonColorless", "Spell", 1, "");
        final ManaCostBeingPaid oneAdjusted = adjustedCost(spell(
                reductionOne.grantedPlayer, "One Reduction Hybrid", "2/W"));
        Assert.assertEquals(oneAdjusted.getUnpaidShards(ManaCostShard.W2), 1);

        final Fixture reductionTwo = new Fixture("two hybrid reduction two test");
        reductionTwo.grantedPlayer.getSpellRuleRegistry().register(
                "reduce-two", "Card.nonColorless", "Spell", 2, "");
        final ManaCostBeingPaid twoAdjusted = adjustedCost(spell(
                reductionTwo.grantedPlayer, "Two Reduction Hybrid", "2/W"));
        Assert.assertTrue(twoAdjusted.isPaid());
    }

    @Test
    public void anyTypeToAnyColorCannotPayColorlessOrEnableSnowForColor() {
        final Fixture fixture = new Fixture("conversion boundary test");
        registerColoredSpellRule(fixture.grantedPlayer);
        final SpellAbility cast = spell(fixture.grantedPlayer,
                "Conversion Boundary Spell", "2 U");
        final ManaPool pool = fixture.grantedPlayer.getManaPool();
        pool.restoreColorReplacements();

        Assert.assertTrue(StaticAbilityManaConvert.manaConvert(
                pool, fixture.grantedPlayer, cast.getHostCard(), cast));
        Assert.assertFalse(pool.canPayForShardWithColor(
                ManaCostShard.COLORLESS, (byte) ManaAtom.WHITE));
        Assert.assertTrue(pool.canPayForShardWithColor(
                ManaCostShard.COLORLESS, (byte) ManaAtom.COLORLESS));
        Assert.assertFalse(pool.isSnowForColor());
    }

    @Test
    public void registryAndStaticManaConversionComposeWithStaticRestriction() {
        final Fixture fixture = new Fixture("conversion composition test");
        registerColoredSpellRule(fixture.grantedPlayer);
        addStaticAbilitySource(fixture, ZoneType.Battlefield,
                "Mode$ ManaConvert | ValidPlayer$ You | ValidCard$ Card.nonColorless "
                        + "| ValidSA$ Spell | ManaConversion$ AnyType<-Blue");
        final SpellAbility cast = spell(fixture.grantedPlayer,
                "Conversion Composition Spell", "2 U");
        final ManaConversionMatrix conversion = new ManaConversionMatrix();
        conversion.restoreColorReplacements();

        Assert.assertTrue(StaticAbilityManaConvert.manaConvert(
                conversion, fixture.grantedPlayer, cast.getHostCard(), cast));
        for (final byte manaType : MagicColor.WUBRGC) {
            Assert.assertEquals(conversion.getPossibleColorUses(manaType),
                    MagicColor.BLUE, "unexpected final uses for mana type " + manaType);
        }
    }

    @Test
    public void trinisphereSetCostRunsAfterRegistryReduction() {
        final Fixture fixture = new Fixture("registry and Trinisphere order test");
        registerColoredSpellRule(fixture.grantedPlayer);
        addStaticAbilitySource(fixture, ZoneType.Battlefield,
                "Mode$ SetCost | ValidCard$ Card | Type$ Spell "
                        + "| Amount$ 3 | RaiseTo$ True");

        final ManaCostBeingPaid adjusted = adjustedCost(spell(
                fixture.grantedPlayer, "Trinisphere Ordered Spell", "2 U"));

        Assert.assertEquals(adjusted.toString(), "{2}{U}");
        Assert.assertEquals(adjusted.getConvertedManaCost(), 3);

        final Fixture harmony = new Fixture("Harmony and Trinisphere order");
        registerHarmonyRule(harmony.grantedPlayer);
        addStaticAbilitySource(harmony, ZoneType.Battlefield,
                "Mode$ SetCost | ValidCard$ Card | Type$ Spell "
                        + "| Amount$ 3 | RaiseTo$ True");
        final ManaCostBeingPaid harmonyAdjusted = adjustedCost(spell(
                harmony.grantedPlayer, "Harmony Trinisphere Spell", "1 U U"));
        Assert.assertEquals(harmonyAdjusted.toString(), "{3}");
        Assert.assertEquals(harmonyAdjusted.getConvertedManaCost(), 3);
    }

    @Test
    public void inertRulesAreRejectedWithoutRegistryPollution() {
        final Fixture fixture = new Fixture("inert rule rejection test");
        final String before = fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();

        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().register(
                        "inert", "Card", "Spell", 0, ""));

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);
        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry().isEmpty());
    }

    @Test
    public void grantRejectsInvalidStackingValueAtomically() {
        final Fixture fixture = new Fixture("strict stacking parser test");
        registerColoredSpellRule(fixture.grantedPlayer);
        final String before = fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();
        final SpellAbility malformed = grantAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ typo-stacking "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ReduceGeneric$ 2 | Stacking$ Tru "
                        + "| Duration$ Permanent");

        final IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class, malformed::resolve);
        Assert.assertTrue(error.getMessage().contains(
                "Stacking must be True or False"));
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);
    }

    @Test
    public void malformedStackingGrantDoesNotAdvanceSequenceOrChangeRules() {
        final Fixture fixture = new Fixture("malformed stacking grant atomicity test");
        fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                "existing", "Card.nonColorless", "Spell", 1, "");
        final String before = fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();
        final SpellAbility malformed = grantAbility(fixture,
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ malformed "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ReduceGeneric$ 2 | ManaConversion$ Bogus "
                        + "| Stacking$ True | Duration$ Permanent");

        final IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class, malformed::resolve);
        Assert.assertTrue(error.getMessage().contains("Invalid mana conversion"));
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);
    }

    @Test
    public void copyFromSelfIsANoOp() {
        final Fixture fixture = new Fixture("self copy registry test");
        registerColoredSpellRule(fixture.grantedPlayer);
        final String before = fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();

        fixture.grantedPlayer.getSpellRuleRegistry().copyFrom(
                fixture.grantedPlayer.getSpellRuleRegistry());

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry().size(), 1);
    }

    @Test
    public void stackingRegistrationRejectsNullAndBlankBaseKeysAtomically() {
        final Fixture fixture = new Fixture("stacking base key validation test");
        final String before = fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();

        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                        null, "Card.nonColorless", "Spell", 1, ""));
        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().registerStacking(
                        "  ", "Card.nonColorless", "Spell", 1, ""));

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);
        Assert.assertTrue(fixture.grantedPlayer.getSpellRuleRegistry().isEmpty());
    }

    @Test
    public void conflictingStableKeyRegistrationIsAtomic() {
        final Fixture fixture = new Fixture("stable key conflict atomicity test");
        registerColoredSpellRule(fixture.grantedPlayer);
        final String before = fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString();

        Assert.expectThrows(IllegalArgumentException.class,
                () -> fixture.grantedPlayer.getSpellRuleRegistry().register(
                        RULE_KEY, "Card.nonColorless", "Spell", 3,
                        "AnyType->AnyColor"));

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry()
                .toStateString(), before);
        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry().size(), 1);
        Assert.assertEquals(adjustedCost(spell(fixture.grantedPlayer,
                "Stable Conflict Spell", "5 U")).toString(), "{3}{U}");
    }

    @Test(timeOut = 5000)
    public void twentyThousandStableKeyRegistrationsRemainOneEntry() {
        final Fixture fixture = new Fixture("stable key performance guard test");

        for (int i = 0; i < 20_000; i++) {
            registerColoredSpellRule(fixture.grantedPlayer);
        }

        Assert.assertEquals(fixture.grantedPlayer.getSpellRuleRegistry().size(), 1);
        Assert.assertTrue(fixture.game.getCardsIn(ZoneType.Battlefield).isEmpty());
        Assert.assertTrue(fixture.game.getCardsIn(ZoneType.Command).isEmpty());
    }

    private static String stateWithManaConversion(
            final String state, final String conversion) {
        final int entryStart = state.indexOf(';') + 1;
        final String[] fields = state.substring(entryStart).split(",", -1);
        Assert.assertTrue(fields.length == 5 || fields.length == 7);
        fields[4] = encodeStateField(conversion);
        return state.substring(0, entryStart) + String.join(",", fields);
    }

    private static long stackingSequence(final Player player) {
        final String state = player.getSpellRuleRegistry().toStateString();
        if (state.isEmpty()) {
            return 0L;
        }
        final int separator = state.indexOf(';');
        return Long.parseLong(separator < 0 ? state : state.substring(0, separator));
    }

    private static void registerColoredSpellRule(final Player player) {
        player.getSpellRuleRegistry().register(
                RULE_KEY,
                "Card.nonColorless",
                "Spell",
                2,
                "AnyType->AnyColor");
    }

    private static void registerHarmonyRule(final Player player) {
        player.getSpellRuleRegistry().register(
                RULE_KEY,
                "Card.nonColorless",
                "Spell",
                0,
                "",
                true,
                2);
    }

    private static String encodeStateField(final String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static ManaCostBeingPaid adjustedCost(final SpellAbility spell) {
        final ManaCostBeingPaid cost = new ManaCostBeingPaid(spell.getHostCard().getManaCost());
        Assert.assertTrue(CostAdjustment.adjust(
                cost, spell, spell.getActivatingPlayer(), null, false, false));
        return cost;
    }

    private static ManaCostBeingPaid adjustedPayCost(final SpellAbility spell) {
        final Cost adjustedBase = CostAdjustment.adjust(
                spell.getPayCosts(), spell, false);
        final ManaCostBeingPaid cost = new ManaCostBeingPaid(
                adjustedBase.getCostMana().getManaCostFor(spell));
        Assert.assertTrue(CostAdjustment.adjust(
                cost, spell, spell.getActivatingPlayer(), null, false, false));
        return cost;
    }

    private static void assertConversionApplies(final Player player,
                                                final SpellAbility spell) {
        final ManaConversionMatrix conversion = new ManaConversionMatrix();
        conversion.restoreColorReplacements();
        Assert.assertTrue(StaticAbilityManaConvert.manaConvert(
                conversion, player, spell.getHostCard(), spell));
    }

    private static SpellAbility grantAbility(final Fixture fixture,
                                             final String definition) {
        final Card source = Card.fromPaperCard(
                paper("Grant Rule Test Source", "B"), fixture.grantedPlayer);
        source.setController(fixture.grantedPlayer,
                fixture.game.getNextTimestamp());
        final SpellAbility grant = AbilityFactory.getAbility(definition, source);
        grant.setActivatingPlayer(fixture.grantedPlayer);
        return grant;
    }

    private static StaticAbility addStaticAbilitySource(final Fixture fixture,
                                                        final ZoneType zone,
                                                        final String definition) {
        final Card source = new Card(fixture.game.nextCardId(), fixture.game);
        source.setName("Static Rule Test Source");
        source.setOwner(fixture.grantedPlayer);
        source.setController(fixture.grantedPlayer,
                fixture.game.getNextTimestamp());
        final StaticAbility ability = source.addStaticAbility(definition);
        ability.setActiveZone(EnumSet.of(zone));
        fixture.grantedPlayer.getZone(zone).add(source);
        return ability;
    }

    private static void assertCost(final ManaCostBeingPaid cost,
                                   final int generic,
                                   final int white,
                                   final int blue) {
        Assert.assertEquals(cost.getUnpaidShards(ManaCostShard.GENERIC), generic);
        Assert.assertEquals(cost.getUnpaidShards(ManaCostShard.WHITE), white);
        Assert.assertEquals(cost.getUnpaidShards(ManaCostShard.BLUE), blue);
    }

    private static SpellAbility spell(final Player player, final String name, final String manaCost) {
        final Card card = Card.fromPaperCard(paper(name, manaCost), player);
        card.setZone(player.getZone(ZoneType.Hand));
        final SpellAbility spell = card.getSpellAbilities().getFirst();
        spell.setActivatingPlayer(player);
        return spell;
    }

    private static SpellAbility spellOwnedBy(final Player owner,
                                             final Player activator,
                                             final String name,
                                             final String manaCost) {
        final Card card = Card.fromPaperCard(paper(name, manaCost), owner);
        card.setController(activator, activator.getGame().getNextTimestamp());
        card.setZone(activator.getZone(ZoneType.Hand));
        final SpellAbility spell = card.getSpellAbilities().getFirst();
        spell.setActivatingPlayer(activator);
        return spell;
    }

    private static void assertHarmonyProjection(final Card card,
                                                final boolean expected) {
        Assert.assertEquals(card.hasKeyword(Keyword.HARMONY), expected);
        Assert.assertEquals(card.getView().getCurrentState().hasKeyword(
                Keyword.HARMONY), expected);
    }

    private static void prepareVisibleColoredSpell(final Fixture fixture,
                                                   final Card card,
                                                   final String name) {
        card.setName(name);
        card.setOwner(fixture.grantedPlayer);
        card.setType(CardType.parse("Sorcery", false));
        card.setColor("U");
        fixture.grantedPlayer.getZone(ZoneType.Hand).add(card);
        card.setZone(fixture.grantedPlayer.getZone(ZoneType.Hand));
    }

    private static PaperCard paper(final String name, final String manaCost) {
        return new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:" + manaCost,
                "Types:Sorcery",
                "A:SP$ Draw | NumCards$ 1 | SpellDescription$ Test.",
                "Oracle:Test card."
        )), "TST", CardRarity.Common);
    }

    private record RegistrySnapshot(String serialized, int size, long sequence) {
        private static RegistrySnapshot capture(final Player player) {
            return new RegistrySnapshot(
                    player.getSpellRuleRegistry().toStateString(),
                    player.getSpellRuleRegistry().size(),
                    stackingSequence(player));
        }

        private void assertUnchanged(final Player player) {
            Assert.assertEquals(player.getSpellRuleRegistry().toStateString(),
                    serialized);
            Assert.assertEquals(player.getSpellRuleRegistry().size(), size);
            Assert.assertEquals(stackingSequence(player), sequence);
        }
    }

    private static final class FailingAbilityTextCard extends Card {
        private boolean failNextAbilityText;

        private FailingAbilityTextCard(final int id, final Game game) {
            this(id, game, true);
        }

        private FailingAbilityTextCard(final int id, final Game game,
                                       final boolean failInitially) {
            super(id, game);
            failNextAbilityText = failInitially;
        }

        private void failNextAbilityText() {
            failNextAbilityText = true;
        }

        @Override
        public String getAbilityText(final CardState state) {
            if (failNextAbilityText) {
                failNextAbilityText = false;
                throw new IllegalStateException("injected view failure");
            }
            return super.getAbilityText(state);
        }
    }

    private static final class Fixture {
        private final Game game;
        private final Player grantedPlayer;
        private final Player otherPlayer;

        private Fixture(final String description) {
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(List.of(), rules, new Match(rules, List.of(), description));
            grantedPlayer = new Player("Granted player", game, 1);
            otherPlayer = new Player("Other player", game, 2);
            game.getPlayers().add(grantedPlayer);
            game.getPlayers().add(otherPlayer);
            grantedPlayer.setTeam(1);
            otherPlayer.setTeam(2);
        }
    }
}
