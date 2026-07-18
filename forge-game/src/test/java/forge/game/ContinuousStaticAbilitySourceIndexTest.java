package forge.game;

import com.google.common.eventbus.Subscribe;
import forge.CardStorageReader;
import forge.GameCommand;
import forge.StaticData;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardStateName;
import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCopyService;
import forge.game.card.CardState;
import forge.game.card.CounterType;
import forge.game.cost.Cost;
import forge.game.cost.CostAdjustment;
import forge.game.event.GameEventCardChangeZone;
import forge.game.event.GameEventZone;
import forge.game.keyword.Keyword;
import forge.game.mana.ManaConversionMatrix;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.spellability.OptionalCostValue;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityAlternativeCost;
import forge.game.staticability.StaticAbilityCantAttach;
import forge.game.staticability.StaticAbilityCantBeCast;
import forge.game.staticability.StaticAbilityCantDraw;
import forge.game.staticability.StaticAbilityCantGainLosePayLife;
import forge.game.staticability.StaticAbilityCantTarget;
import forge.game.staticability.StaticAbilityCastWithFlash;
import forge.game.staticability.StaticAbilityManaConvert;
import forge.game.staticability.StaticAbilityMode;
import forge.game.staticability.StaticAbilityMustTarget;
import forge.game.trigger.TriggerHandler;
import forge.game.zone.PlayerZoneBattlefield;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ContinuousStaticAbilitySourceIndexTest {
    private static final int LARGE_LIBRARY_SIZE = 20_000;
    private static final int LARGE_TEST3_BATTLEFIELD_SIZE = 2_000;
    private static final int EXTREME_TEST3_BATTLEFIELD_SIZE = 20_000;
    private static final int SPARSE_MODE_TAIL_SIZE = 20_000;
    private static final int SPARSE_MODE_CHURN = 2_000;

    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Path languages = Paths.get("forge-gui", "res", "languages")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(languages)) {
            languages = Paths.get("..", "forge-gui", "res", "languages")
                    .toAbsolutePath().normalize();
        }
        Localizer.getInstance().initialize("en-US", languages.toString());
        if (StaticData.instance() == null) {
            new StaticData(new CardStorageReader(
                    workspacePath("forge-gui", "res", "cardsfolder"), null,
                    true), null,
                    workspacePath("forge-gui", "res", "editions"),
                    workspacePath("custom", "editions"),
                    workspacePath("forge-gui", "res", "blockdata"),
                    "Latest", true, true);
        }
    }

    @Test
    public void hugeLibraryVisitsOnlyActualContinuousSourcesAfterBootstrap() {
        final Fixture fixture = new Fixture("continuous source hot path");
        final CardCollection libraryCards = new CardCollection();
        for (int i = 0; i < LARGE_LIBRARY_SIZE; i++) {
            final Card test3 = fixture.card("test3 " + i);
            test3.addStaticAbility("Mode$ IgnoreLegendRule | EffectZone$ All");
            libraryCards.add(test3);
        }
        fixture.player.getZone(ZoneType.Library).setCards(libraryCards);

        final Card source = fixture.card("Real continuous source");
        source.addStaticAbility("Mode$ Continuous | EffectZone$ All "
                + "| Affected$ Creature.YouCtrl | AddPower$ 1");
        fixture.player.getZone(ZoneType.Library).add(source);
        final Card target = fixture.creature("Affected creature", 1, 1);
        fixture.player.getZone(ZoneType.Battlefield).add(target);

        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(target.getNetPower(), 2,
                "the indexed source must still apply its real effect");

        final ContinuousStaticAbilitySourceIndex index = fixture.index();
        index.resetDiagnostics();
        fixture.game.getAction().checkStaticAbilities(false);
        final ContinuousStaticAbilitySourceIndex.Diagnostics diagnostics =
                index.diagnostics();

        Assert.assertEquals(diagnostics.bootstrapCardVisits(), 0L,
                "a warmed index must never rescan the library");
        Assert.assertEquals(diagnostics.candidateCardVisits(), 1L,
                "20,000 non-Continuous test3 cards must stay off the hot path");
        Assert.assertEquals(diagnostics.candidateCards(), 1);
        Assert.assertEquals(diagnostics.checkInvocations(), 1L);

        final int replacements = 8;
        final Card stableTail = fixture.player.getZone(ZoneType.Library)
                .get(LARGE_LIBRARY_SIZE - 1);
        index.resetDiagnostics();
        for (int i = 0; i < replacements; i++) {
            final Card original = fixture.player.getZone(ZoneType.Library).get(i);
            final Card staged = fixture.card("Replacement " + i);
            final Card prepared = fixture.game.getAction().moveTo(
                    fixture.player.getZone(ZoneType.None), staged, null);
            fixture.game.getAction().ceaseToExist(original, true);
            final Card moved = fixture.game.getAction().moveTo(
                    fixture.player.getZone(ZoneType.Library), prepared, i,
                    null, AbilityKey.newMap());
            Assert.assertSame(fixture.player.getZone(ZoneType.Library).get(i),
                    moved);
        }
        final ContinuousStaticAbilitySourceIndex.Diagnostics replacementPass =
                index.diagnostics();
        Assert.assertEquals(replacementPass.bootstrapCardVisits(), 0L);
        Assert.assertEquals(replacementPass.checkInvocations(),
                3L * replacements,
                "None staging and each real move must retain all three legacy "
                        + "recomputation points");
        Assert.assertEquals(replacementPass.candidateCardVisits(),
                3L * replacements,
                "ReplaceCards-style work must scale with k*candidates, not k*N");
        Assert.assertSame(fixture.player.getZone(ZoneType.Library)
                .get(LARGE_LIBRARY_SIZE - 1), stableTail,
                "unreplaced library identity and position must stay unchanged");
    }

    @Test
    public void realTest3BattlefieldUsesLinearModeIndexAndCastQueriesSkipIt()
            throws IOException {
        final Fixture fixture = new Fixture("real test3 cast hot path");
        fixture.addPlayer("Opponent", 2, 2);
        final PaperCard test3Paper = realTest3Paper();
        final List<Card> test3Cards = new ArrayList<>();

        // Warm the empty game first so every following card exercises the
        // incremental entry path rather than hiding work in bootstrap.
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.ManaConvert, ignored -> true);
        fixture.index().resetDiagnostics();
        for (int i = 0; i < LARGE_TEST3_BATTLEFIELD_SIZE; i++) {
            final Card test3 = Card.fromPaperCard(test3Paper, fixture.player);
            fixture.player.getZone(ZoneType.Battlefield).add(test3);
            test3Cards.add(test3);
        }

        final ContinuousStaticAbilitySourceIndex.Diagnostics inserted =
                fixture.index().diagnostics();
        Assert.assertEquals(inserted.modeDiscoveryCardVisits(),
                LARGE_TEST3_BATTLEFIELD_SIZE,
                "each entering test3 should be classified exactly once");
        Assert.assertEquals(inserted.modeAppendInsertions(),
                LARGE_TEST3_BATTLEFIELD_SIZE,
                "append-only setup must take the O(1) ordering path");
        Assert.assertEquals(inserted.modeOrderRelabelCardVisits(), 0L,
                "incremental test3 setup must not relabel the growing zone");

        final AtomicLong unrelatedModeReads = new AtomicLong();
        for (final Card test3 : test3Cards) {
            final StaticAbility ability = test3.getStaticAbilities().getFirst();
            ability.setMode(new CountingModeSet(ability.getMode(),
                    unrelatedModeReads));
        }
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> false);
        unrelatedModeReads.set(0L);
        fixture.index().resetDiagnostics();

        final SpellAbility cast = fixture.spell(
                "Mode-index cast probe", "3 U");
        final ManaConversionMatrix conversion = new ManaConversionMatrix();
        conversion.restoreColorReplacements();
        StaticAbilityManaConvert.manaConvert(conversion, fixture.player,
                cast.getHostCard(), cast);
        Assert.assertFalse(StaticAbilityCantBeCast.cantBeCastAbility(cast,
                cast.getHostCard(), fixture.player));
        Assert.assertNull(StaticAbilityCantTarget.cantTarget(
                cast.getHostCard(), cast));
        Assert.assertTrue(StaticAbilityMustTarget
                .meetsMustTargetRestriction(cast));
        Assert.assertTrue(StaticAbilityAlternativeCost.alternativeCosts(cast,
                cast.getHostCard(), fixture.player).isEmpty());
        Assert.assertTrue(GameActionUtil.getOptionalCostValues(cast).isEmpty());
        CostAdjustment.adjust(new Cost("3 U", false), cast, false);
        final ManaCostBeingPaid manaCost = new ManaCostBeingPaid(
                cast.getHostCard().getManaCost());
        Assert.assertTrue(CostAdjustment.adjust(manaCost, cast,
                fixture.player, null, false, false));
        Assert.assertFalse(StaticAbilityCastWithFlash.anyWithFlash(cast,
                cast.getHostCard(), fixture.player));

        Assert.assertEquals(unrelatedModeReads.get(), 0L,
                "cast/payment queries must not even inspect an unrelated "
                        + "test3 ability set");
        Assert.assertEquals(fixture.index().diagnostics().modeSourceVisits(),
                0L, "mode buckets must exclude all unrelated test3 sources");

        fixture.index().resetDiagnostics();
        unrelatedModeReads.set(0L);
        for (final Card test3 : test3Cards) {
            Assert.assertTrue(test3.ignoreLegendRule());
        }
        Assert.assertEquals(fixture.index().diagnostics().modeSourceVisits(),
                (long) LARGE_TEST3_BATTLEFIELD_SIZE,
                "each legend query must stop at its first applicable source");
        Assert.assertEquals(fixture.index().diagnostics()
                        .modeCandidateExaminations(),
                (long) LARGE_TEST3_BATTLEFIELD_SIZE,
                "rank filtering must seek directly to Battlefield instead of "
                        + "rescanning all N test3 sources in Graveyard first");
        Assert.assertEquals(unrelatedModeReads.get(),
                (long) LARGE_TEST3_BATTLEFIELD_SIZE,
                "IgnoreLegend should inspect one ability, not N abilities, "
                        + "for each of N test3 permanents");
        Assert.assertEquals(fixture.index().diagnostics()
                .modeSnapshotRebuilds(), 0L,
                "stable early-exit queries reuse the immutable mode snapshot");
        Assert.assertEquals(fixture.index().diagnostics()
                .modeSnapshotLocationCopies(), 0L,
                "2k stable queries cannot recopy a 2k bucket per query");
        Assert.assertEquals(fixture.player.getCardsIn(ZoneType.Battlefield)
                .size(), LARGE_TEST3_BATTLEFIELD_SIZE);

        fixture.index().resetDiagnostics();
        unrelatedModeReads.set(0L);
        fixture.game.getAction().checkStateEffects(false);
        final long stateBasedVisits = fixture.index().diagnostics()
                .modeSourceVisits();
        Assert.assertTrue(stateBasedVisits >= LARGE_TEST3_BATTLEFIELD_SIZE
                        && stateBasedVisits
                        <= 2L * LARGE_TEST3_BATTLEFIELD_SIZE,
                "the real state-based legend-rule entry must stay linear: "
                        + stateBasedVisits);
        Assert.assertTrue(fixture.index().diagnostics()
                        .modeCandidateExaminations()
                        <= 2L * LARGE_TEST3_BATTLEFIELD_SIZE,
                "the real state-based entry examined too many mode "
                        + "candidates: " + fixture.index().diagnostics()
                        .modeCandidateExaminations());
        Assert.assertTrue(unrelatedModeReads.get()
                        <= 8L * LARGE_TEST3_BATTLEFIELD_SIZE,
                "the real state-based entry inspected too many test3 "
                        + "ability sets: " + unrelatedModeReads.get());
        Assert.assertEquals(fixture.player.getCardsIn(ZoneType.Battlefield)
                .size(), LARGE_TEST3_BATTLEFIELD_SIZE,
                "all real test3 permanents must survive the legend rule");
    }

    @Test
    public void twentyThousandTest3LegendsExamineOneCandidatePerQuery() {
        final Fixture fixture = new Fixture("20k test3 mode pressure");
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> true);
        final List<Card> test3Cards = new ArrayList<>();
        for (int i = 0; i < EXTREME_TEST3_BATTLEFIELD_SIZE; i++) {
            final Card test3 = fixture.card("test3");
            test3.getCurrentState().addType("Legendary");
            test3.getCurrentState().addType("Artifact");
            test3.addStaticAbility("Mode$ IgnoreLegendRule "
                    + "| EffectZone$ All | ValidCard$ Permanent.YouCtrl");
            fixture.player.getZone(ZoneType.Battlefield).add(test3);
            test3Cards.add(test3);
        }
        final ContinuousStaticAbilitySourceIndex.Diagnostics inserted =
                fixture.index().diagnostics();
        Assert.assertEquals(inserted.modeAppendInsertions(),
                (long) EXTREME_TEST3_BATTLEFIELD_SIZE);
        Assert.assertEquals(inserted.modeOrderRelabelCardVisits(), 0L,
                "20k append setup cannot hide triangular relabel work");
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> false);
        fixture.index().resetDiagnostics();

        final SpellAbility cast = fixture.spell(
                "20k mode-index cast probe", "3 U");
        final ManaConversionMatrix conversion = new ManaConversionMatrix();
        conversion.restoreColorReplacements();
        StaticAbilityManaConvert.manaConvert(conversion, fixture.player,
                cast.getHostCard(), cast);
        Assert.assertFalse(StaticAbilityCantBeCast.cantBeCastAbility(cast,
                cast.getHostCard(), fixture.player));
        Assert.assertNull(StaticAbilityCantTarget.cantTarget(
                cast.getHostCard(), cast));
        Assert.assertTrue(StaticAbilityMustTarget
                .meetsMustTargetRestriction(cast));
        CostAdjustment.adjust(new Cost("3 U", false), cast, false);
        final ManaCostBeingPaid manaCost = new ManaCostBeingPaid(
                cast.getHostCard().getManaCost());
        Assert.assertTrue(CostAdjustment.adjust(manaCost, cast,
                fixture.player, null, false, false));
        Assert.assertEquals(fixture.index().diagnostics()
                .modeCandidateExaminations(), 0L,
                "20k IgnoreLegend sources must contribute zero candidate "
                        + "work to cast, target, cost, and payment preflight");
        Assert.assertEquals(fixture.index().diagnostics()
                .modeClassificationFailures(), 0L);
        Assert.assertEquals(fixture.index().diagnostics()
                .modeSnapshotLocationCopies(), 0L);
        fixture.index().resetDiagnostics();

        for (final Card test3 : test3Cards) {
            Assert.assertTrue(test3.ignoreLegendRule());
        }

        Assert.assertEquals(fixture.index().diagnostics()
                        .modeCandidateExaminations(),
                (long) EXTREME_TEST3_BATTLEFIELD_SIZE,
                "20k legend checks must examine exactly one indexed source "
                        + "each, never materialize or scan the 20k-card zone");
        Assert.assertEquals(fixture.index().diagnostics().modeSourceVisits(),
                (long) EXTREME_TEST3_BATTLEFIELD_SIZE);
        Assert.assertEquals(fixture.index().diagnostics()
                .modeSnapshotRebuilds(), 0L,
                "20k stable early-exit queries must not rebuild snapshots");
        Assert.assertEquals(fixture.index().diagnostics()
                .modeSnapshotLocationCopies(), 0L,
                "20k stable early-exit queries must not copy 400m entries");
    }

    @Test
    public void remainingCantDrawQueryVisitsOnlyItsIndexedSource() {
        final Fixture fixture = new Fixture(
                "remaining static query migration benchmark");
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.CantDraw, ignored -> true);
        for (int i = 0; i < LARGE_TEST3_BATTLEFIELD_SIZE; i++) {
            fixture.player.getZone(ZoneType.Battlefield)
                    .add(fixture.card("unrelated permanent " + i));
        }
        final Card drawLimit = fixture.modeSource("draw limit",
                StaticAbilityMode.CantDraw);
        fixture.player.getZone(ZoneType.Battlefield).add(drawLimit);
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.CantDraw, ignored -> true);
        fixture.index().resetDiagnostics();

        final int queryCount = 100;
        for (int i = 0; i < queryCount; i++) {
            Assert.assertEquals(StaticAbilityCantDraw.canDrawAmount(
                    fixture.player, 1), 0);
        }

        final ContinuousStaticAbilitySourceIndex.Diagnostics diagnostics =
                fixture.index().diagnostics();
        Assert.assertEquals(diagnostics.modeSourceVisits(),
                (long) queryCount,
                "the production CantDraw path must visit only its one "
                        + "indexed source, regardless of battlefield size");
        Assert.assertEquals(diagnostics.modeCandidateExaminations(),
                (long) queryCount,
                "100 stable queries may examine at most one candidate each");
        Assert.assertEquals(diagnostics.modeSnapshotLocationCopies(), 0L,
                "stable migrated queries must reuse the mode snapshot");
    }

    @Test
    public void multiModeSnapshotsPreserveLegacyOrderAndDeduplicateCards() {
        final Fixture fixture = new Fixture("multi-mode source snapshot");
        final Card battlefield = fixture.modeSource("battlefield gain",
                StaticAbilityMode.CantGainLife);
        final Card graveyard = fixture.modeSource("graveyard change",
                StaticAbilityMode.CantChangeLife);
        final Card exileDual = fixture.modeSource("exile dual",
                StaticAbilityMode.CantGainLife);
        exileDual.addStaticAbility("Mode$ CantChangeLife | EffectZone$ All");
        fixture.player.getZone(ZoneType.Battlefield).add(battlefield);
        fixture.player.getZone(ZoneType.Graveyard).add(graveyard);
        fixture.player.getZone(ZoneType.Exile).add(exileDual);

        final CardCollection sources =
                fixture.game.getStaticAbilityModeSources(
                        StaticAbilityMode.CantGainLife,
                        StaticAbilityMode.CantChangeLife);
        Assert.assertEquals(sources.size(), 3,
                "a card advertising both requested modes must appear once");
        Assert.assertSame(sources.get(0), graveyard);
        Assert.assertSame(sources.get(1), battlefield);
        Assert.assertSame(sources.get(2), exileDual);
        Assert.assertTrue(StaticAbilityCantGainLosePayLife
                .anyCantGainLife(fixture.player));
        Assert.assertTrue(StaticAbilityCantGainLosePayLife
                .anyCantLoseLife(fixture.player),
                "CantChangeLife must participate in both life queries");
    }

    @Test
    public void modeQueriesPreserveZoneOrderPhasingAndSpecialExclusions() {
        final Fixture fixture = new Fixture("mode source ordering");
        final Card graveyard = fixture.modeSource("graveyard",
                StaticAbilityMode.ManaConvert);
        final Card battlefield = fixture.modeSource("battlefield",
                StaticAbilityMode.ManaConvert);
        final Card exile = fixture.modeSource("exile",
                StaticAbilityMode.ManaConvert);
        final Card command = fixture.modeSource("command",
                StaticAbilityMode.ManaConvert);
        final Card stack = fixture.modeSource("stack",
                StaticAbilityMode.ManaConvert);
        final Player opponent = fixture.addPlayer("Opponent", 2, 2);
        final Card opponentBattlefield = fixture.card(opponent,
                "opponent battlefield");
        opponentBattlefield.addStaticAbility(
                "Mode$ ManaConvert | EffectZone$ All");
        fixture.player.getZone(ZoneType.Graveyard).add(graveyard);
        fixture.player.getZone(ZoneType.Battlefield).add(battlefield);
        opponent.getZone(ZoneType.Battlefield).add(opponentBattlefield);
        fixture.player.getZone(ZoneType.Exile).add(exile);
        fixture.player.getZone(ZoneType.Command).add(command);
        fixture.game.getStackZone().add(stack);

        final Card library = fixture.modeSource("library excluded",
                StaticAbilityMode.ManaConvert);
        final Card sideboard = fixture.modeSource("sideboard excluded",
                StaticAbilityMode.ManaConvert);
        final Card inbound = fixture.modeSource("inbound excluded",
                StaticAbilityMode.ManaConvert);
        final Card melded = fixture.modeSource("melded excluded",
                StaticAbilityMode.ManaConvert);
        fixture.player.getZone(ZoneType.Library).add(library);
        fixture.player.getZone(ZoneType.Sideboard).add(sideboard);
        fixture.player.addInboundToken(inbound);
        final PlayerZoneBattlefield battlefieldZone =
                (PlayerZoneBattlefield) fixture.player.getZone(
                        ZoneType.Battlefield);
        battlefieldZone.add(melded);
        battlefieldZone.addToMelded(melded);

        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.ManaConvert),
                List.of("graveyard", "battlefield", "opponent battlefield",
                        "exile", "command", "stack"));
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.ManaConvert,
                        List.of(ZoneType.Battlefield, ZoneType.Stack,
                                ZoneType.Command)),
                List.of("battlefield", "opponent battlefield", "stack",
                        "command"),
                "CostAdjustment's legacy Battlefield/Stack/Command order "
                        + "must not inherit EnumSet order");

        battlefield.setPhasedOut(fixture.player);
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.ManaConvert),
                List.of("graveyard", "opponent battlefield", "exile",
                        "command", "stack"),
                "legacy STATIC_ABILITIES_SOURCE_ZONES filters phased-out "
                        + "battlefield cards");
        battlefield.setPhasedOut(null);
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.ManaConvert),
                List.of("graveyard", "battlefield", "opponent battlefield",
                        "exile", "command", "stack"),
                "a phased source must remain indexed for phasing in");
    }

    @Test
    public void playerRemovalKeepsStableSeatOrderForOldAndNewModeSources() {
        final Fixture fixture = new Fixture("stable mode source seat order");
        final Player second = fixture.addPlayer("Second", 2, 2);
        final Player third = fixture.addPlayer("Third", 3, 3);
        Assert.assertTrue(modeSourceNames(fixture.game,
                StaticAbilityMode.ManaConvert).isEmpty());

        final Card departing = fixture.card("departing source");
        departing.addStaticAbility(
                "Mode$ ManaConvert | EffectZone$ All");
        final Card oldFirst = fixture.card(second, "old first");
        oldFirst.addStaticAbility("Mode$ ManaConvert | EffectZone$ All");
        final Card oldSecond = fixture.card(second, "old second");
        oldSecond.addStaticAbility("Mode$ ManaConvert | EffectZone$ All");
        final Card thirdExisting = fixture.card(third, "third existing");
        thirdExisting.addStaticAbility(
                "Mode$ ManaConvert | EffectZone$ All");
        fixture.player.getZone(ZoneType.Battlefield).add(departing);
        second.getZone(ZoneType.Battlefield).add(oldFirst);
        second.getZone(ZoneType.Battlefield).add(oldSecond);
        third.getZone(ZoneType.Battlefield).add(thirdExisting);

        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.ManaConvert),
                List.of("departing source", "old first", "old second",
                        "third existing"));
        fixture.game.onPlayerLost(fixture.player);
        final Card added = fixture.card(second, "new after removal");
        added.addStaticAbility("Mode$ ManaConvert | EffectZone$ All");
        second.getZone(ZoneType.Battlefield).add(added);

        final Card reentrant = fixture.card(second, "reentrant new");
        reentrant.addStaticAbility("Mode$ ManaConvert | EffectZone$ All");
        final List<String> outer = new ArrayList<>();
        final List<String> nested = new ArrayList<>();
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.ManaConvert, card -> {
                    outer.add(card.getName());
                    if (card == oldFirst) {
                        second.getZone(ZoneType.Battlefield).add(reentrant);
                        nested.addAll(modeSourceNames(fixture.game,
                                StaticAbilityMode.ManaConvert));
                    }
                    return true;
                });
        Assert.assertEquals(outer, List.of("old first", "old second",
                        "new after removal", "third existing"),
                "an in-flight snapshot keeps old seat/zone order and excludes "
                        + "a reentrant insertion");
        Assert.assertEquals(nested, List.of("old first", "old second",
                        "new after removal", "reentrant new",
                        "third existing"));
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.ManaConvert),
                legacyBattlefieldModeSourceNames(fixture.game,
                        StaticAbilityMode.ManaConvert),
                "indexed order must equal the surviving players' legacy "
                        + "player/zone scan order");
        Assert.assertFalse(fixture.index()
                .containsStaticAbilitySourceEquivalent(departing));
        Assert.assertEquals(fixture.index().diagnostics()
                .pendingModeRechecks(), 0);
    }

    @Test
    public void modeQueryUsesBoundedSnapshotAcrossReorderInsertAndReentry() {
        final Fixture fixture = new Fixture("bounded mode query snapshot");
        final Card first = fixture.modeSource("first",
                StaticAbilityMode.IgnoreLegendRule);
        final Card second = fixture.modeSource("second",
                StaticAbilityMode.IgnoreLegendRule);
        final Card third = fixture.modeSource("third",
                StaticAbilityMode.IgnoreLegendRule);
        fixture.player.getZone(ZoneType.Battlefield).add(first);
        fixture.player.getZone(ZoneType.Battlefield).add(second);
        fixture.player.getZone(ZoneType.Battlefield).add(third);

        final List<String> outer = new ArrayList<>();
        final List<String> nested = new ArrayList<>();
        final AtomicBoolean changed = new AtomicBoolean();
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, card -> {
                    outer.add(card.getName());
                    if (card == first && changed.compareAndSet(false, true)) {
                        fixture.player.getZone(ZoneType.Battlefield)
                                .reorder(second, 2);
                        final Card inserted = fixture.modeSource("inserted",
                                StaticAbilityMode.IgnoreLegendRule);
                        fixture.player.getZone(ZoneType.Battlefield)
                                .add(inserted, 1);
                        fixture.game.visitStaticAbilityModeSources(
                                StaticAbilityMode.IgnoreLegendRule,
                                nestedCard -> {
                                    nested.add(nestedCard.getName());
                                    return true;
                                });
                    }
                    return true;
                });

        Assert.assertEquals(outer, List.of("first", "second", "third"),
                "an outer query must use the source identity/order snapshot "
                        + "taken before visitor callbacks");
        Assert.assertEquals(nested,
                List.of("first", "inserted", "third", "second"),
                "a nested query gets its own current bounded snapshot");

        final Fixture reentered = new Fixture(
                "removed and reentered mode source identity");
        final Card reFirst = reentered.modeSource("first",
                StaticAbilityMode.IgnoreLegendRule);
        final Card reSecond = reentered.modeSource("second",
                StaticAbilityMode.IgnoreLegendRule);
        final Card reThird = reentered.modeSource("third",
                StaticAbilityMode.IgnoreLegendRule);
        reentered.player.getZone(ZoneType.Battlefield).add(reFirst);
        reentered.player.getZone(ZoneType.Battlefield).add(reSecond);
        reentered.player.getZone(ZoneType.Battlefield).add(reThird);
        final List<String> afterReentry = new ArrayList<>();
        reentered.game.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, card -> {
                    afterReentry.add(card.getName());
                    if (card == reFirst) {
                        reentered.player.getZone(ZoneType.Battlefield)
                                .remove(reSecond);
                        reentered.player.getZone(ZoneType.Battlefield)
                                .add(reSecond);
                    }
                    return true;
                });
        Assert.assertEquals(afterReentry, List.of("first", "third"),
                "a removed snapshot identity is stale; its newly inserted "
                        + "replacement is outside the bounded query");
    }

    @Test
    public void queryClassificationFailureRetainsMembershipForRetry()
            throws ReflectiveOperationException {
        final Fixture fixture = new Fixture(
                "mode query classification retry");
        final Card source = fixture.modeSource("recovering source",
                StaticAbilityMode.IgnoreLegendRule);
        fixture.player.getZone(ZoneType.Battlefield).add(source);
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule),
                List.of("recovering source"));

        final StaticAbility ability = source.getStaticAbilities().getFirst();
        final Field modes = StaticAbility.class.getDeclaredField("modes");
        modes.setAccessible(true);
        modes.set(ability, new FailOnceContainsModeSet(
                StaticAbilityMode.IgnoreLegendRule));
        final Field intrinsicModes = Card.class.getDeclaredField(
                "intrinsicStaticAbilityModes");
        intrinsicModes.setAccessible(true);
        ((Set<?>) intrinsicModes.get(source)).clear();
        fixture.index().resetDiagnostics();

        Assert.assertTrue(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule).isEmpty(),
                "a malformed query-time classification is skipped safely");
        Assert.assertEquals(fixture.index().diagnostics()
                .modeClassificationFailures(), 1L);
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule),
                List.of("recovering source"),
                "query failure cannot permanently evict a valid membership");
        Assert.assertTrue(fixture.index().diagnostics()
                        .modeSnapshotRebuilds() <= 1L,
                "fail-once recovery may publish one clean snapshot, never "
                        + "enter an infinite dirty rebuild loop");
    }

    @Test
    public void queryTimePermanentFailuresAreQuarantinedWithBoundedWork()
            throws ReflectiveOperationException {
        final Fixture fixture = new Fixture(
                "20k query-time quarantine pressure");
        Assert.assertTrue(modeSourceNames(fixture.game,
                StaticAbilityMode.IgnoreLegendRule).isEmpty());
        for (int i = 0; i < EXTREME_TEST3_BATTLEFIELD_SIZE; i++) {
            final Card source = fixture.modeSource("contains malformed " + i,
                    StaticAbilityMode.IgnoreLegendRule);
            setAbilityModes(source.getStaticAbilities().getFirst(),
                    new AlwaysThrowContainsModeSet(
                            StaticAbilityMode.IgnoreLegendRule));
            clearIntrinsicModes(source);
            fixture.player.getZone(ZoneType.Battlefield).add(source);
        }
        final Card healthy = fixture.card("healthy structural source");
        fixture.player.getZone(ZoneType.Battlefield).add(healthy);
        healthy.addStaticAbility("Mode$ IgnoreLegendRule | EffectZone$ All");
        fixture.index().resetDiagnostics();

        Assert.assertTrue(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule)
                .contains("healthy structural source"),
                "budget exhaustion must jump over 20k dynamic failures to "
                        + "the later healthy intrinsic source");
        fixture.index().resetDiagnostics();

        for (int query = 0; query < 80; query++) {
            final long failuresBefore = fixture.index().diagnostics()
                    .modeClassificationFailures();
            final long examinationsBefore = fixture.index().diagnostics()
                    .modeCandidateExaminations();
            Assert.assertTrue(modeSourceNames(fixture.game,
                            StaticAbilityMode.IgnoreLegendRule)
                    .contains("healthy structural source"),
                    "a saturated quarantine budget cannot suppress an "
                            + "already ordered healthy structural rule");
            Assert.assertTrue(fixture.index().diagnostics()
                            .modeClassificationFailures() - failuresBefore
                            <= ContinuousStaticAbilitySourceIndex
                            .MAX_PENDING_MODE_RETRIES_PER_QUERY,
                    "query-time contains failures share the strict exception "
                            + "budget with pending retries");
            Assert.assertTrue(fixture.index().diagnostics()
                            .modeCandidateExaminations() - examinationsBefore
                            <= ContinuousStaticAbilitySourceIndex
                            .MAX_PENDING_MODE_RETRIES_PER_QUERY + 1L,
                    "a degraded query must skip quarantined entries and jump "
                            + "directly to the one safe structural source");
        }
        Assert.assertTrue(fixture.index().diagnostics()
                .modeQueryDegradedSkips() > 0L);
        Assert.assertEquals(fixture.index().diagnostics()
                        .modeSnapshotLocationCopies(), 0L,
                "a stable quarantine overlay must not copy the 20k source "
                        + "snapshot again, including across retry backoff "
                        + "and overflow-promotion epochs");

        fixture.player.getZone(ZoneType.Battlefield)
                .removeAllCards(true);
        Assert.assertEquals(fixture.index().diagnostics()
                .pendingModeRechecks(), 0);
    }

    @Test
    public void liveIntrinsicModeChangeReconcilesStructuralFallback()
            throws ReflectiveOperationException {
        final Fixture fixture = new Fixture(
                "live intrinsic mode reconciliation");
        Assert.assertTrue(modeSourceNames(fixture.game,
                StaticAbilityMode.IgnoreLegendRule).isEmpty());
        for (int i = 0; i < ContinuousStaticAbilitySourceIndex
                .MAX_PENDING_MODE_RETRIES_PER_QUERY; i++) {
            final Card malformed = fixture.card("dual malformed " + i);
            final StaticAbility legend = malformed.addStaticAbility(
                    "Mode$ IgnoreLegendRule | EffectZone$ All");
            final StaticAbility mana = malformed.addStaticAbility(
                    "Mode$ ManaConvert | EffectZone$ All");
            setAbilityModes(legend, new AlwaysThrowContainsModeSet(
                    StaticAbilityMode.IgnoreLegendRule));
            setAbilityModes(mana, new AlwaysThrowContainsModeSet(
                    StaticAbilityMode.ManaConvert));
            clearIntrinsicModes(malformed);
            fixture.player.getZone(ZoneType.Battlefield).add(malformed);
        }

        final Card healthy = fixture.card("live healthy");
        fixture.player.getZone(ZoneType.Battlefield).add(healthy);
        final StaticAbility changing = healthy.addStaticAbility(
                "Mode$ IgnoreLegendRule | EffectZone$ All");
        Assert.assertTrue(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule)
                .contains("live healthy"),
                "post-entry addStaticAbility must publish its rebuilt "
                        + "intrinsic mode before degraded fallback");

        changing.setMode(EnumSet.of(StaticAbilityMode.ManaConvert));
        Assert.assertFalse(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule)
                .contains("live healthy"),
                "X->Y must clear the old structural bit and membership even "
                        + "when X exhausts the dynamic exception budget");
        Assert.assertTrue(modeSourceNames(fixture.game,
                        StaticAbilityMode.ManaConvert)
                .contains("live healthy"),
                "X->Y must publish Y to degraded structural fallback");

        fixture.index().resetDiagnostics();
        for (int query = 0; query < 10; query++) {
            Assert.assertFalse(modeSourceNames(fixture.game,
                            StaticAbilityMode.IgnoreLegendRule)
                    .contains("live healthy"));
            Assert.assertTrue(modeSourceNames(fixture.game,
                            StaticAbilityMode.ManaConvert)
                    .contains("live healthy"));
        }
        Assert.assertEquals(fixture.index().diagnostics()
                .modeSnapshotLocationCopies(), 0L,
                "stable X/Y quarantine queries cannot recopy their buckets");
    }

    @Test
    public void modeSnapshotInvalidationIsScopedToModeAndRank() {
        final Fixture fixture = new Fixture("scoped mode snapshot dirtiness");
        final Card mana = fixture.modeSource("mana battlefield",
                StaticAbilityMode.ManaConvert);
        final Card legendBattlefield = fixture.modeSource(
                "legend battlefield", StaticAbilityMode.IgnoreLegendRule);
        final Card legendGraveyard = fixture.modeSource("legend graveyard",
                StaticAbilityMode.IgnoreLegendRule);
        fixture.player.getZone(ZoneType.Battlefield).add(mana);
        fixture.player.getZone(ZoneType.Battlefield).add(legendBattlefield);
        fixture.player.getZone(ZoneType.Graveyard).add(legendGraveyard);

        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.ManaConvert),
                List.of("mana battlefield"));
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule),
                List.of("legend graveyard", "legend battlefield"));
        fixture.index().resetDiagnostics();

        final Card added = fixture.modeSource("added legend battlefield",
                StaticAbilityMode.IgnoreLegendRule);
        fixture.player.getZone(ZoneType.Battlefield).add(added);
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.ManaConvert),
                List.of("mana battlefield"));
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule,
                        List.of(ZoneType.Graveyard)),
                List.of("legend graveyard"));
        Assert.assertEquals(fixture.index().diagnostics()
                .modeSnapshotRebuilds(), 0L,
                "a Battlefield IgnoreLegend mutation cannot dirty another "
                        + "mode or the same mode's Graveyard rank");

        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule,
                        List.of(ZoneType.Battlefield)),
                List.of("legend battlefield",
                        "added legend battlefield"));
        Assert.assertEquals(fixture.index().diagnostics()
                .modeSnapshotRebuilds(), 1L);
        Assert.assertEquals(fixture.index().diagnostics()
                .modeSnapshotLocationCopies(), 2L,
                "only the two sources in the affected mode/rank are copied");
    }

    @Test
    public void malformedDynamicModeEntryIsAtomicNonThrowingAndRepairable()
            throws ReflectiveOperationException {
        final Fixture fixture = new Fixture("malformed dynamic mode entry");
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> true);
        final Card source = fixture.card("malformed mode source");
        final StaticAbility ability = source.addStaticAbility(
                "Mode$ IgnoreLegendRule | EffectZone$ All");
        final Field modes = StaticAbility.class.getDeclaredField("modes");
        modes.setAccessible(true);
        modes.set(ability, new ThrowingModeSet(
                StaticAbilityMode.IgnoreLegendRule));
        fixture.index().resetDiagnostics();

        fixture.player.getZone(ZoneType.Battlefield).add(source);

        Assert.assertEquals(fixture.index().diagnostics().liveCards(), 1,
                "the successful Zone add remains authoritative");
        Assert.assertEquals(fixture.index().diagnostics()
                .modeClassificationFailures(), 1L);
        Assert.assertTrue(modeSourceNames(fixture.game,
                StaticAbilityMode.IgnoreLegendRule).isEmpty(),
                "a failed classification cannot publish a partial bucket");

        ability.setMode(EnumSet.of(StaticAbilityMode.IgnoreLegendRule));
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule),
                List.of("malformed mode source"),
                "correcting the trait must advertise and index it without a "
                        + "zone round trip");
    }

    @Test
    public void enteredCardClassificationFailureRetriesWithoutExternalWrite()
            throws ReflectiveOperationException {
        final Fixture fixture = new Fixture(
                "entered mode classification retry");
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> true);
        final Card source = fixture.modeSource("fail-once entered source",
                StaticAbilityMode.IgnoreLegendRule);
        final StaticAbility ability = source.getStaticAbilities().getFirst();
        setAbilityModes(ability, new FailOnceIteratorModeSet(
                StaticAbilityMode.IgnoreLegendRule));

        fixture.player.getZone(ZoneType.Battlefield).add(source);

        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule),
                List.of("fail-once entered source"),
                "the next mode query must retry the small pending set without "
                        + "requiring another trait or zone write");
    }

    @Test
    public void setCardsClassificationFailureRetriesInRebuiltZoneOrder()
            throws ReflectiveOperationException {
        final Fixture fixture = new Fixture(
                "setCards mode classification retry");
        final Card first = fixture.modeSource("first",
                StaticAbilityMode.IgnoreLegendRule);
        final Card second = fixture.modeSource("second fail-once",
                StaticAbilityMode.IgnoreLegendRule);
        final Card third = fixture.modeSource("third",
                StaticAbilityMode.IgnoreLegendRule);
        fixture.player.getZone(ZoneType.Battlefield).add(first);
        fixture.player.getZone(ZoneType.Battlefield).add(second);
        fixture.player.getZone(ZoneType.Battlefield).add(third);
        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule),
                List.of("first", "second fail-once", "third"));
        setAbilityModes(second.getStaticAbilities().getFirst(),
                new FailOnceIteratorModeSet(
                        StaticAbilityMode.IgnoreLegendRule));

        fixture.player.getZone(ZoneType.Battlefield).setCards(
                List.of(third, second, first));

        Assert.assertEquals(modeSourceNames(fixture.game,
                        StaticAbilityMode.IgnoreLegendRule),
                List.of("third", "second fail-once", "first"),
                "a failed rebuilt sibling must recover at its exact order "
                        + "without dropping or duplicating successful siblings");
    }

    @Test
    public void permanentMalformedPendingRetriesAreCappedAndCleaned()
            throws ReflectiveOperationException {
        final Fixture fixture = new Fixture("20k malformed pending modes");
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> true);
        for (int i = 0; i < EXTREME_TEST3_BATTLEFIELD_SIZE; i++) {
            final Card malformed = fixture.modeSource("malformed " + i,
                    StaticAbilityMode.IgnoreLegendRule);
            setAbilityModes(malformed.getStaticAbilities().getFirst(),
                    new AlwaysThrowIteratorModeSet());
            fixture.player.getZone(ZoneType.Battlefield).add(malformed);
        }
        Assert.assertEquals(fixture.index().diagnostics()
                        .pendingModeRechecks(),
                EXTREME_TEST3_BATTLEFIELD_SIZE);
        fixture.index().resetDiagnostics();

        for (int query = 0; query < 10; query++) {
            final long before = fixture.index().diagnostics()
                    .pendingModeRetryAttempts();
            Assert.assertTrue(modeSourceNames(fixture.game,
                    StaticAbilityMode.ManaConvert).isEmpty());
            Assert.assertTrue(fixture.index().diagnostics()
                            .pendingModeRetryAttempts() - before
                            <= ContinuousStaticAbilitySourceIndex
                            .MAX_PENDING_MODE_RETRIES_PER_QUERY,
                    "one hover/query may inspect only the bounded number of "
                            + "due pending cards");
        }
        Assert.assertEquals(fixture.index().diagnostics()
                        .pendingModeRetryAttempts(),
                10L * ContinuousStaticAbilitySourceIndex
                        .MAX_PENDING_MODE_RETRIES_PER_QUERY,
                "20k permanent failures cannot be rescanned on every query");
        Assert.assertEquals(fixture.index().diagnostics()
                        .modeClassificationFailures(),
                fixture.index().diagnostics().pendingModeRetryAttempts());

        fixture.player.getZone(ZoneType.Battlefield)
                .removeAllCards(true);
        Assert.assertEquals(fixture.index().diagnostics()
                .pendingModeRechecks(), 0,
                "leaving the zone must release every pending card reference");
    }

    @Test
    public void pendingLimitBackoffAndCounterOverflowStayBounded()
            throws ReflectiveOperationException {
        Assert.assertEquals(ContinuousStaticAbilitySourceIndex
                .MAX_PENDING_MODE_RECHECKS, 65_536,
                "the production limit rejects item 65,537");
        final Fixture fixture = new Fixture("scaled pending boundary");
        final ContinuousStaticAbilitySourceIndex index =
                new ContinuousStaticAbilitySourceIndex(fixture.game,
                        card -> false, 2, 1, 2);
        index.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> true);
        final Card first = fixture.modeSource("first malformed",
                StaticAbilityMode.IgnoreLegendRule);
        final Card second = fixture.modeSource("second malformed",
                StaticAbilityMode.IgnoreLegendRule);
        final Card overflow = fixture.modeSource("overflow malformed",
                StaticAbilityMode.IgnoreLegendRule);
        for (final Card card : List.of(first, second)) {
            setAbilityModes(card.getStaticAbilities().getFirst(),
                    new AlwaysThrowIteratorModeSet());
        }
        setAbilityModes(overflow.getStaticAbilities().getFirst(),
                new FailOnceIteratorModeSet(
                        StaticAbilityMode.IgnoreLegendRule));
        index.cardEntered(first,
                fixture.player.getZone(ZoneType.Battlefield), 0);
        index.cardEntered(second,
                fixture.player.getZone(ZoneType.Battlefield), 1);
        index.cardEntered(overflow,
                fixture.player.getZone(ZoneType.Battlefield), 2);
        Assert.assertEquals(index.diagnostics().pendingModeRechecks(), 3);
        Assert.assertEquals(index.diagnostics().pendingModeOverflowDrops(),
                0L);
        Assert.assertEquals(index.diagnostics()
                .pendingModeOverflowQuarantined(), 1);
        Assert.assertEquals(index.diagnostics()
                .pendingModeOverflowEvictions(), 1L,
                "a far-backoff incumbent rotates to overflow so limit+1 gets "
                        + "a prompt retry opportunity");

        index.resetDiagnostics();
        index.visitStaticAbilityModeSources(
                StaticAbilityMode.ManaConvert, ignored -> true);
        Assert.assertEquals(index.diagnostics().pendingModeRetryAttempts(),
                1L);
        index.visitStaticAbilityModeSources(
                StaticAbilityMode.ManaConvert, ignored -> true);
        Assert.assertEquals(index.diagnostics().pendingModeRetryAttempts(),
                2L, "the second due card consumes the next bounded query");
        final List<String> recovered = new ArrayList<>();
        index.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, card -> {
                    recovered.add(card.getName());
                    return true;
                });
        Assert.assertTrue(recovered.contains("overflow malformed"),
                "a new fail-once source cannot be starved by permanent old "
                        + "entries at a saturated cap");

        final Field epoch = ContinuousStaticAbilitySourceIndex.class
                .getDeclaredField("modeQueryEpoch");
        epoch.setAccessible(true);
        epoch.setLong(index, Long.MAX_VALUE);
        final Field sequence = ContinuousStaticAbilitySourceIndex.class
                .getDeclaredField("nextPendingModeSequence");
        sequence.setAccessible(true);
        sequence.setLong(index, Long.MAX_VALUE);
        index.cardLeft(first,
                fixture.player.getZone(ZoneType.Battlefield), 0);
        index.cardEntered(first,
                fixture.player.getZone(ZoneType.Battlefield), 0);
        index.visitStaticAbilityModeSources(
                StaticAbilityMode.ManaConvert, ignored -> true);
        Assert.assertTrue((long) epoch.get(index) > 0L,
                "epoch overflow is renormalized instead of wrapping negative");
        Assert.assertTrue((long) sequence.get(index) >= 0L,
                "sequence overflow is compacted instead of wrapping negative");

        index.cardLeft(first,
                fixture.player.getZone(ZoneType.Battlefield), 0);
        index.cardLeft(second,
                fixture.player.getZone(ZoneType.Battlefield), 1);
        index.cardLeft(overflow,
                fixture.player.getZone(ZoneType.Battlefield), 2);
        Assert.assertEquals(index.diagnostics().pendingModeRechecks(), 0);
    }

    @Test
    public void pendingOverflowDropReleasesBothKindsAndDoesNotStarveNewWork()
            throws ReflectiveOperationException {
        final Fixture fixture = new Fixture("scaled pending true overflow");
        final ContinuousStaticAbilitySourceIndex index =
                new ContinuousStaticAbilitySourceIndex(fixture.game,
                        card -> false, 2, 1, 2);
        index.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> true);
        final Zone battlefield = fixture.player.getZone(
                ZoneType.Battlefield);

        final Card known = fixture.modeSource("known quarantined",
                StaticAbilityMode.IgnoreLegendRule);
        setAbilityModes(known.getStaticAbilities().getFirst(),
                new AlwaysThrowContainsModeSet(
                        StaticAbilityMode.IgnoreLegendRule));
        clearIntrinsicModes(known);
        index.cardEntered(known, battlefield, 0);
        index.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> true);
        index.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> true);

        final List<Card> discovery = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final Card malformed = fixture.modeSource("discovery " + i,
                    StaticAbilityMode.IgnoreLegendRule);
            setAbilityModes(malformed.getStaticAbilities().getFirst(),
                    new AlwaysThrowIteratorModeSet());
            discovery.add(malformed);
            index.cardEntered(malformed, battlefield, i + 1);
        }

        Assert.assertEquals(index.diagnostics().pendingModeRechecks(), 4,
                "primary plus overflow tiers remain strictly bounded");
        Assert.assertTrue(index.diagnostics().pendingModeOverflowDrops() >= 2L,
                "six pending items at cap two must exercise real >2*cap "
                        + "drops, not only primary-to-overflow rotation");
        Assert.assertTrue(hasModeLocation(index, known),
                "dropping a known quarantine must restore its indexed "
                        + "membership so it can be classified again");
        Assert.assertFalse(hasModeLocation(index, discovery.get(1)),
                "dropping an empty discovery placeholder must release its "
                        + "mode-location reference");

        final Card failOnce = fixture.modeSource("late fail-once",
                StaticAbilityMode.IgnoreLegendRule);
        setAbilityModes(failOnce.getStaticAbilities().getFirst(),
                new FailOnceIteratorModeSet(
                        StaticAbilityMode.IgnoreLegendRule));
        index.cardEntered(failOnce, battlefield, discovery.size() + 1);
        final List<String> recovered = new ArrayList<>();
        for (int query = 0; query < 4 && recovered.isEmpty(); query++) {
            index.visitStaticAbilityModeSources(
                    StaticAbilityMode.IgnoreLegendRule, card -> {
                        if (card == failOnce) {
                            recovered.add(card.getName());
                        }
                        return true;
                    });
        }
        Assert.assertEquals(recovered, List.of("late fail-once"),
                "a later recoverable source must get a bounded retry even "
                        + "while both pending tiers are saturated");

        for (int i = 0; i < discovery.size(); i++) {
            index.cardLeft(discovery.get(i), battlefield, i + 1);
        }
        index.cardLeft(failOnce, battlefield, discovery.size() + 1);
        Assert.assertEquals(index.diagnostics().pendingModeRechecks(), 1,
                "a dropped known quarantine must safely fail and isolate "
                        + "again while later work is being serviced");
        index.cardLeft(known, battlefield, 0);
        Assert.assertEquals(index.diagnostics().pendingModeRechecks(), 0);
        Assert.assertFalse(hasModeLocation(index, known));
        Assert.assertFalse(hasModeLocation(index, failOnce),
                "leaving the zone must release every pending/location card "
                        + "reference after overflow pressure");
    }

    @Test
    public void dynamicDetectorFailureRollsBackCandidatesAndRetriesInOrder() {
        final Fixture fixture = new Fixture("dynamic detector transaction");
        final Card first = fixture.card("first dynamic candidate");
        final Card second = fixture.card("second dynamic candidate");
        final AtomicBoolean failOnce = new AtomicBoolean(true);
        final ContinuousStaticAbilitySourceIndex index =
                new ContinuousStaticAbilitySourceIndex(fixture.game, card -> {
                    if (card == second && failOnce.compareAndSet(true, false)) {
                        throw new IllegalStateException(
                                "injected dynamic detector failure");
                    }
                    return true;
                });
        Assert.assertTrue(index.snapshotAfterStaticEffectsCleared(
                CardCollection.EMPTY).isEmpty());
        index.cardEntered(first,
                fixture.player.getZone(ZoneType.Battlefield), 0);
        index.cardEntered(second,
                fixture.player.getZone(ZoneType.Battlefield), 1);

        Assert.expectThrows(IllegalStateException.class,
                () -> index.snapshotAfterStaticEffectsCleared(
                        CardCollection.EMPTY));
        Assert.assertEquals(index.diagnostics().liveCards(), 2);
        Assert.assertEquals(index.diagnostics().candidateCards(), 0,
                "failure on the second card cannot partly commit the first");
        Assert.assertEquals(index.snapshotAfterStaticEffectsCleared(
                        CardCollection.EMPTY).stream().toList(),
                List.of(first, second),
                "retry must retain the unconsumed pending set and sequence");
    }

    @Test
    public void repeatedSparseModeTailRemovalDoesNotRescanTheZone() {
        final Fixture fixture = new Fixture("lazy mode tail cleanup");
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> true);
        final List<Card> modeSources = new ArrayList<>();
        for (int i = 0; i < LARGE_TEST3_BATTLEFIELD_SIZE; i++) {
            final Card source = fixture.modeSource("mode source " + i,
                    StaticAbilityMode.IgnoreLegendRule);
            fixture.player.getZone(ZoneType.Battlefield).add(source);
            modeSources.add(source);
        }
        for (int i = 0; i < LARGE_TEST3_BATTLEFIELD_SIZE; i++) {
            fixture.player.getZone(ZoneType.Battlefield).add(
                    fixture.card("non-mode tail " + i));
        }
        fixture.index().resetDiagnostics();

        for (int i = modeSources.size() - 1; i >= 0; i--) {
            fixture.player.getZone(ZoneType.Battlefield)
                    .remove(modeSources.get(i));
        }
        Assert.assertEquals(fixture.index().diagnostics()
                .modeTailRecomputeCardVisits(), 0L,
                "tail removal must only mark one lazy dirty container");

        fixture.player.getZone(ZoneType.Battlefield).add(fixture.modeSource(
                "replacement mode tail", StaticAbilityMode.IgnoreLegendRule));
        Assert.assertTrue(fixture.index().diagnostics()
                        .modeTailRecomputeCardVisits()
                        <= LARGE_TEST3_BATTLEFIELD_SIZE + 1L,
                "the next append may rebuild the tail once, never once per "
                        + "removed source");
    }

    @Test
    public void sparseModeTailRemoveReaddChurnIsConstantPerMutation() {
        final Fixture fixture = new Fixture("sparse mode tail churn");
        fixture.game.visitStaticAbilityModeSources(
                StaticAbilityMode.IgnoreLegendRule, ignored -> true);
        final Card source = fixture.modeSource("churning mode source",
                StaticAbilityMode.IgnoreLegendRule);
        fixture.player.getZone(ZoneType.Battlefield).add(source);
        for (int i = 0; i < SPARSE_MODE_TAIL_SIZE; i++) {
            fixture.player.getZone(ZoneType.Battlefield).add(
                    fixture.card("sparse non-mode " + i));
        }
        fixture.index().resetDiagnostics();

        for (int i = 0; i < SPARSE_MODE_CHURN; i++) {
            fixture.player.getZone(ZoneType.Battlefield).remove(source);
            fixture.player.getZone(ZoneType.Battlefield).add(source);
            Assert.assertEquals(modeSourceNames(fixture.game,
                            StaticAbilityMode.IgnoreLegendRule),
                    List.of("churning mode source"));
        }

        Assert.assertEquals(fixture.index().diagnostics()
                        .modeTailRecomputeCardVisits(), 0L,
                "remove/re-add must retrieve the previous indexed tail "
                        + "without scanning a 20k non-mode zone tail");
        Assert.assertEquals(fixture.index().diagnostics()
                        .modeOrderRelabelCardVisits(), 0L,
                "append churn cannot relabel the containing zone");
        Assert.assertEquals(fixture.index().diagnostics()
                        .modeSnapshotRebuilds(),
                (long) SPARSE_MODE_CHURN,
                "one queried mutation rebuilds one affected mode/rank");
        Assert.assertEquals(fixture.index().diagnostics()
                        .modeSnapshotLocationCopies(),
                (long) SPARSE_MODE_CHURN,
                "snapshot work scales with the one mode source, never the "
                        + "20k unrelated zone tail");
    }

    @Test
    public void cantAttachAndHexproofShroudQueriesSkipUnrelatedTest3()
            throws IOException {
        final Fixture fixture = new Fixture(
                "cant attach and protection mode hot paths");
        final Player opponent = fixture.addPlayer("Opponent", 2, 2);

        final Card aura = fixture.card("Test Aura");
        aura.getCurrentState().addType("Enchantment");
        aura.getCurrentState().addType("Aura");
        final Card equipment = fixture.card("Test Equipment");
        equipment.getCurrentState().addType("Artifact");
        equipment.getCurrentState().addType("Equipment");
        final Card creatureTarget = fixture.creature(
                "Creature attachment target", 2, 2);

        final Card auraRule = fixture.card("Aura restriction");
        auraRule.addStaticAbility("Mode$ CantAttach | EffectZone$ All "
                + "| ValidCard$ Aura | Target$ Creature");
        final Card equipmentRule = fixture.card("Equipment restriction");
        equipmentRule.addStaticAbility("Mode$ CantAttach | EffectZone$ All "
                + "| ValidCard$ Equipment | Target$ Player");

        final Card nonmatchingHexproof = fixture.card(
                "Nonmatching hexproof exception");
        nonmatchingHexproof.addStaticAbility(
                "Mode$ IgnoreHexproof | EffectZone$ All "
                        + "| Activator$ Opponent | ValidEntity$ Card");
        final Card matchingHexproof = fixture.card(
                "Matching hexproof exception");
        matchingHexproof.addStaticAbility(
                "Mode$ IgnoreHexproof | EffectZone$ All "
                        + "| Activator$ You | ValidEntity$ Card");
        final Card matchingShroud = fixture.card(
                "Matching shroud exception");
        matchingShroud.addStaticAbility(
                "Mode$ IgnoreShroud | EffectZone$ All "
                        + "| Activator$ You | ValidEntity$ Card");

        final Card hexproofTarget = fixture.creature(opponent,
                "Hexproof target", 2, 2);
        hexproofTarget.addIntrinsicKeyword("Hexproof");
        final Card shroudTarget = fixture.creature(opponent,
                "Shroud target", 2, 2);
        shroudTarget.addIntrinsicKeyword("Shroud");
        fixture.player.getZone(ZoneType.Battlefield).add(creatureTarget);
        opponent.getZone(ZoneType.Battlefield).add(hexproofTarget);
        opponent.getZone(ZoneType.Battlefield).add(shroudTarget);

        final PaperCard test3Paper = realTest3Paper();
        final List<Card> test3Cards = new ArrayList<>();
        for (int i = 0; i < LARGE_TEST3_BATTLEFIELD_SIZE; i++) {
            final Card test3 = Card.fromPaperCard(test3Paper, fixture.player);
            fixture.player.getZone(ZoneType.Graveyard).add(test3);
            test3Cards.add(test3);
        }
        fixture.player.getZone(ZoneType.Battlefield).add(auraRule);
        fixture.player.getZone(ZoneType.Battlefield).add(equipmentRule);
        fixture.player.getZone(ZoneType.Battlefield)
                .add(nonmatchingHexproof);
        fixture.player.getZone(ZoneType.Battlefield).add(matchingHexproof);
        fixture.player.getZone(ZoneType.Battlefield).add(matchingShroud);

        final StaticAbility auraRestriction = auraRule.getStaticAbilities()
                .getFirst();
        Assert.assertTrue(aura.isAura());
        Assert.assertTrue(creatureTarget.isCreature());
        Assert.assertTrue(auraRestriction.checkConditions(
                StaticAbilityMode.CantAttach));
        Assert.assertTrue(auraRestriction.matchesValidParam("ValidCard", aura));
        Assert.assertTrue(auraRestriction.matchesValidParam(
                "Target", creatureTarget));
        Assert.assertSame(legacyCantAttach(creatureTarget, aura, false),
                auraRestriction);
        Assert.assertSame(StaticAbilityCantAttach.cantAttach(
                creatureTarget, aura, false), auraRestriction);
        Assert.assertNull(legacyCantAttach(fixture.player, aura, false));
        Assert.assertNull(StaticAbilityCantAttach.cantAttach(
                fixture.player, aura, false));
        final StaticAbility equipmentRestriction = equipmentRule
                .getStaticAbilities().getFirst();
        Assert.assertSame(legacyCantAttach(fixture.player, equipment, false),
                equipmentRestriction);
        Assert.assertSame(StaticAbilityCantAttach.cantAttach(
                fixture.player, equipment, false), equipmentRestriction);
        Assert.assertNull(legacyCantAttach(creatureTarget, equipment, false));
        Assert.assertNull(StaticAbilityCantAttach.cantAttach(
                creatureTarget, equipment, false));

        final SpellAbility cast = fixture.spell(
                "Protection targeting probe", "1 U");
        final StaticAbility hexproof = keywordAbility(hexproofTarget,
                Keyword.HEXPROOF);
        final StaticAbility shroud = keywordAbility(shroudTarget,
                Keyword.SHROUD);
        Assert.assertTrue(legacyIgnoreProtection(hexproofTarget, cast,
                hexproof));
        Assert.assertTrue(legacyIgnoreProtection(shroudTarget, cast, shroud));
        Assert.assertNull(StaticAbilityCantTarget.cantTarget(
                hexproofTarget, cast),
                "the matching second exception in the chain ignores hexproof");
        Assert.assertNull(StaticAbilityCantTarget.cantTarget(
                shroudTarget, cast));

        final AtomicLong unrelatedModeReads = new AtomicLong();
        for (final Card test3 : test3Cards) {
            final StaticAbility ability = test3.getStaticAbilities().getFirst();
            ability.setMode(new CountingModeSet(ability.getMode(),
                    unrelatedModeReads));
        }
        unrelatedModeReads.set(0L);
        fixture.index().resetDiagnostics();

        Assert.assertSame(StaticAbilityCantAttach.cantAttach(
                fixture.player, equipment, false), equipmentRestriction);
        Assert.assertNull(StaticAbilityCantTarget.cantTarget(
                hexproofTarget, cast));
        Assert.assertNull(StaticAbilityCantTarget.cantTarget(
                shroudTarget, cast));
        Assert.assertEquals(unrelatedModeReads.get(), 0L,
                "CantAttach and protection exceptions must seek their exact "
                        + "mode buckets without touching 2k test3 abilities");
    }

    @Test
    public void migratedModeEnumsTrackDynamicAddRemoveAndReadd() {
        for (final StaticAbilityMode mode : List.of(
                StaticAbilityMode.CantAttach,
                StaticAbilityMode.IgnoreHexproof,
                StaticAbilityMode.IgnoreShroud)) {
            final Fixture fixture = new Fixture(
                    "dynamic migrated mode " + mode);
            final Card source = fixture.card("dynamic " + mode);
            fixture.player.getZone(ZoneType.Battlefield).add(source);
            fixture.game.visitStaticAbilityModeSources(mode,
                    ignored -> true);

            final StaticAbility first = source.addStaticAbility(
                    "Mode$ " + mode + " | EffectZone$ All");
            Assert.assertEquals(modeSourceNames(fixture.game, mode),
                    List.of("dynamic " + mode));
            source.getCurrentState().removeStaticAbility(first);
            Assert.assertTrue(modeSourceNames(fixture.game, mode).isEmpty());

            source.addStaticAbility(
                    "Mode$ " + mode + " | EffectZone$ All");
            Assert.assertEquals(modeSourceNames(fixture.game, mode),
                    List.of("dynamic " + mode));
        }
    }

    @Test
    public void manaConversionKeepsStrictLegacySourceOrder() {
        final Fixture fixture = new Fixture("ordered mana conversion");
        final Card widen = fixture.card("graveyard widen");
        widen.addStaticAbility("Mode$ ManaConvert | EffectZone$ All "
                + "| ManaConversion$ AnyType->AnyColor");
        final Card restrict = fixture.card("battlefield restrict");
        restrict.addStaticAbility("Mode$ ManaConvert | EffectZone$ All "
                + "| ManaConversion$ AnyType<-Blue");
        fixture.player.getZone(ZoneType.Graveyard).add(widen);
        fixture.player.getZone(ZoneType.Battlefield).add(restrict);
        final SpellAbility cast = fixture.spell("Conversion target", "1 U");
        final ManaConversionMatrix conversion = new ManaConversionMatrix();
        conversion.restoreColorReplacements();

        Assert.assertTrue(StaticAbilityManaConvert.manaConvert(conversion,
                fixture.player, cast.getHostCard(), cast));
        for (final byte manaType : MagicColor.WUBRGC) {
            Assert.assertEquals(conversion.getPossibleColorUses(manaType),
                    (byte) ManaAtom.BLUE,
                    "widen-then-restrict ordering changed for mana type "
                            + manaType);
        }
    }

    @Test
    public void sameIdDedupKeepsSourceFirstAndGlobalFirstDirections() {
        final Fixture sourceFirst = new Fixture("source-first same ID");
        final SpellAbility sourceCast = sourceFirst.spell(
                "source-first spell", "3 U");
        final Card source = sourceCast.getHostCard();
        source.addStaticAbility("Mode$ AlternativeCost | EffectZone$ All "
                + "| ValidCard$ Card.Self | Cost$ 1 "
                + "| Description$ source alternative");
        source.addStaticAbility("Mode$ OptionalCost | EffectZone$ All "
                + "| ValidCard$ Card.Self | Cost$ 1");
        final Card sameIdGlobal = sourceFirst.card(source.getId(),
                "same-ID global alternative");
        sameIdGlobal.addStaticAbility(
                "Mode$ AlternativeCost | EffectZone$ All "
                        + "| ValidCard$ Card | Cost$ 9 "
                        + "| Description$ global alternative");
        sameIdGlobal.addStaticAbility("Mode$ OptionalCost | EffectZone$ All "
                + "| ValidCard$ Card | Cost$ 9");
        sourceFirst.player.getZone(ZoneType.Battlefield).add(sameIdGlobal);

        final List<SpellAbility> alternatives =
                StaticAbilityAlternativeCost.alternativeCosts(sourceCast,
                        source, sourceFirst.player);
        Assert.assertEquals(alternatives.size(), 1,
                "AlternativeCost must seed its source before equal-ID globals");
        Assert.assertEquals(alternatives.get(0).getPayCosts().getTotalMana()
                .getCMC(), 1);
        final List<OptionalCostValue> optional =
                GameActionUtil.getOptionalCostValues(sourceCast);
        Assert.assertEquals(optional.size(), 1,
                "OptionalCost must seed its source before equal-ID globals");
        Assert.assertEquals(optional.get(0).getCost().getTotalMana().getCMC(),
                1);

        final Fixture globalFirst = new Fixture("global-first same ID");
        final SpellAbility hostCast = globalFirst.spell(
                "global-first spell", "3 U");
        final Card host = hostCast.getHostCard();
        host.addStaticAbility("Mode$ CantBeCast | EffectZone$ All "
                + "| ValidCard$ Card.Self");
        host.addStaticAbility("Mode$ CastWithFlash | EffectZone$ All "
                + "| ValidCard$ Card.Self");
        host.addStaticAbility("Mode$ RaiseCost | EffectZone$ All "
                + "| ValidCard$ Card.Self | Type$ Spell | Amount$ 1");
        host.addStaticAbility("Mode$ ReduceCost | EffectZone$ All "
                + "| ValidCard$ Card.Self | Type$ Spell | Amount$ 2");
        host.addStaticAbility("Mode$ SetCost | EffectZone$ All "
                + "| ValidCard$ Card.Self | Type$ Spell | Amount$ 10 "
                + "| RaiseTo$ True");
        final Card sameIdBlocker = globalFirst.card(host.getId(),
                "same-ID global blocker");
        globalFirst.player.getZone(ZoneType.Battlefield).add(sameIdBlocker);

        Assert.assertFalse(StaticAbilityCantBeCast.cantBeCastAbility(hostCast,
                host, globalFirst.player),
                "global-first CantBeCast must let an equal-ID global suppress "
                        + "the appended host even when that global has no mode");
        Assert.assertFalse(StaticAbilityCastWithFlash.anyWithFlash(hostCast,
                host, globalFirst.player),
                "global-first CastWithFlash must preserve equal-ID dedup");
        final Cost raised = CostAdjustment.adjust(new Cost("3 U", false),
                hostCast, false);
        Assert.assertEquals(raised.getTotalMana().getCMC(), 4,
                "global-first RaiseCost must not append an equal-ID host");
        final ManaCostBeingPaid adjusted = new ManaCostBeingPaid(
                host.getManaCost());
        Assert.assertTrue(CostAdjustment.adjust(adjusted, hostCast,
                globalFirst.player, null, false, false));
        Assert.assertEquals(adjusted.toString(), "{3}{U}",
                "global-first ReduceCost/SetCost must not append an equal-ID "
                        + "host");
    }

    @Test
    public void nullOriginToNonePreservesFullStaticCheckSemantics() {
        final Fixture fixture = new Fixture("None staging semantics");
        final Card paired = fixture.creature("Paired creature", 1, 1);
        final Card invalidPartner = fixture.card("Invalid noncreature partner");
        fixture.player.getZone(ZoneType.Battlefield).add(paired);
        fixture.player.getZone(ZoneType.Battlefield).add(invalidPartner);
        final Card stateTriggerHost = fixture.card("Always trigger host");
        fixture.player.getZone(ZoneType.Battlefield).add(stateTriggerHost);
        fixture.game.getAction().checkStaticAbilities(false);
        paired.setPairedWith(invalidPartner);
        invalidPartner.setPairedWith(paired);
        final List<String> commandResult = new ArrayList<>();
        addOrderCommand(paired, "checked", commandResult);
        stateTriggerHost.setSVar("TrigDraw", "DB$ Draw | NumCards$ 1");
        stateTriggerHost.addTrigger(TriggerHandler.parseTrigger(
                "Mode$ Always | TriggerZones$ Battlefield "
                        + "| Execute$ TrigDraw", stateTriggerHost, true));
        fixture.game.getTriggerHandler().registerActiveTrigger(
                stateTriggerHost, false);
        Assert.assertFalse(fixture.game.getStack()
                .hasSimultaneousStackEntries());
        fixture.index().resetDiagnostics();

        final ZoneEventCounter events = new ZoneEventCounter();
        fixture.game.subscribeToEvents(events);
        final Card staged = fixture.card("Staged replacement");

        final Card moved = fixture.game.getAction().moveTo(
                fixture.player.getZone(ZoneType.None), staged, null);

        Assert.assertSame(moved, staged);
        Assert.assertSame(fixture.player.getZone(ZoneType.None).get(0), staged);
        Assert.assertTrue(staged.isInZone(ZoneType.None));
        Assert.assertEquals(events.zoneEvents, 1,
                "the existing Zone Added event must be preserved");
        Assert.assertEquals(events.changeZoneEvents, 1,
                "the existing card-change-zone event must be preserved");
        Assert.assertEquals(fixture.index().diagnostics().checkInvocations(), 1L,
                "None staging must retain the generic static recomputation");
        Assert.assertEquals(commandResult, List.of("checked"),
                "legacy immediate static commands must still run");
        Assert.assertNull(paired.getPairedWith(),
                "the generic pass must retain invalid soulbond cleanup");
        Assert.assertNull(invalidPartner.getPairedWith());
        Assert.assertTrue(fixture.game.getStack().hasSimultaneousStackEntries(),
                "a real TriggerType.Always state trigger must be queued");
    }

    @Test
    public void dynamicAddAndRemovalCannotLeaveAFalseNegative() {
        final Fixture fixture = new Fixture("dynamic source invalidation");
        final Card source = fixture.creature("Dynamic source", 1, 1);
        fixture.player.getZone(ZoneType.Battlefield).add(source);
        fixture.game.getAction().checkStaticAbilities(false);

        final StaticAbility ability = source.addStaticAbility(
                "Mode$ Continuous | Affected$ Card.Self | AddPower$ 1");
        fixture.index().resetDiagnostics();
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                1L, "creating a live Continuous ability must index its host");
        Assert.assertEquals(source.getNetPower(), 2);

        final ContinuousStaticAbilitySourceIndex.Diagnostics beforeLkiCopy =
                fixture.index().diagnostics();
        fixture.game.copyLastState();
        final ContinuousStaticAbilitySourceIndex.Diagnostics afterLkiCopy =
                fixture.index().diagnostics();
        Assert.assertEquals(afterLkiCopy.liveCards(), beforeLkiCopy.liveCards());
        Assert.assertEquals(afterLkiCopy.candidateCards(),
                beforeLkiCopy.candidateCards(),
                "detached last-state zones must not enter the live index");

        source.getCurrentState().removeStaticAbility(ability);
        fixture.index().resetDiagnostics();
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                0L, "a truly removed source is pruned after layer clear");
        Assert.assertEquals(source.getNetPower(), 1);

        fixture.index().resetDiagnostics();
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                0L, "removed sources must not accumulate on the hot path");

        final List<String> commandResult = new ArrayList<>();
        addOrderCommand(source, "ran", commandResult);
        fixture.index().resetDiagnostics();
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(commandResult, List.of("ran"));
        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                1L, "the generic static-command write API must index its host");
    }

    @Test
    public void alternateFaceDownAndCopiedAbilitiesRemainDiscoverable() {
        final Fixture fixture = new Fixture("state and copy invalidation");
        final Card transforming = Card.fromPaperCard(
                paper("Transforming source", "1"), fixture.player);
        transforming.getCurrentState().addType("Creature");
        transforming.getCurrentState().setBasePower(1);
        transforming.getCurrentState().setBaseToughness(1);
        transforming.addAlternateState(CardStateName.Backside, false);
        final CardState back = transforming.getState(CardStateName.Backside);
        back.addType("Creature");
        back.setBasePower(1);
        back.setBaseToughness(1);
        back.addStaticAbility(new StaticAbility(
                "Mode$ Continuous | Affected$ Card.Self | AddPower$ 1",
                transforming, back));
        fixture.player.getZone(ZoneType.Battlefield).add(transforming);

        fixture.game.getAction().checkStaticAbilities(false);
        fixture.index().resetDiagnostics();
        transforming.setState(CardStateName.Backside, false);
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                1L, "an alternate face must be indexed before it transforms");
        Assert.assertEquals(transforming.getNetPower(), 2);

        transforming.setState(CardStateName.FaceDown, false);
        fixture.index().resetDiagnostics();
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                1L, "face-down state must not evict a source's other faces");

        final Card copiedHost = fixture.creature("Copied source", 1, 1);
        fixture.player.getZone(ZoneType.Battlefield).add(copiedHost);
        final StaticAbility copied = back.getStaticAbilities().getFirst()
                .copy(copiedHost, false);
        copiedHost.addStaticAbility(copied);
        fixture.index().resetDiagnostics();
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertTrue(fixture.index().diagnostics().candidateCardVisits() >= 2,
                "copying a Continuous ability onto a live host must index it");
        Assert.assertEquals(copiedHost.getNetPower(), 2);
    }

    @Test
    public void sideboardAndLkiSourcesKeepLegacyCoverage() {
        final Fixture fixture = new Fixture("sideboard and LKI coverage");
        final Card target = fixture.creature("Target", 1, 1);
        fixture.player.getZone(ZoneType.Battlefield).add(target);
        final Card sideboardSource = fixture.card("Sideboard source");
        sideboardSource.addStaticAbility(
                "Mode$ Continuous | EffectZone$ Sideboard "
                        + "| Affected$ Creature.YouCtrl | AddPower$ 1");
        fixture.player.getZone(ZoneType.Sideboard).add(sideboardSource);

        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(target.getNetPower(), 2,
                "the previous withSideboard scan semantics must be retained");

        sideboardSource.getCurrentState().removeStaticAbility(
                sideboardSource.getStaticAbilities().getFirst());
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(target.getNetPower(), 1);

        final Card lki = CardCopyService.getLKICopy(sideboardSource);
        lki.addStaticAbility("Mode$ Continuous | EffectZone$ Sideboard "
                + "| Affected$ Creature.YouCtrl | AddPower$ 1");
        final CardCollection preList = new CardCollection(lki);
        fixture.index().resetDiagnostics();
        fixture.game.getAction().checkStaticAbilities(false, new HashSet<>(),
                preList);

        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                1L, "a preList LKI source must be merged without a game scan");
        Assert.assertEquals(target.getNetPower(), 2);
    }

    @Test
    public void sameIdInboundRemovalRestoresOriginalForLkiMerge() {
        final Fixture fixture = new Fixture("same-id inbound restoration");
        final Card target = fixture.creature("LKI target", 1, 1);
        fixture.player.getZone(ZoneType.Battlefield).add(target);
        final Card original = fixture.card("Original identity");
        fixture.player.getZone(ZoneType.Sideboard).add(original);
        fixture.game.getAction().checkStaticAbilities(false);

        final Card inbound = CardCopyService.getLKICopy(original);
        fixture.player.addInboundToken(inbound);
        fixture.player.removeInboundToken(inbound);

        final Card lki = CardCopyService.getLKICopy(original);
        lki.addStaticAbility("Mode$ Continuous | EffectZone$ Sideboard "
                + "| Affected$ Creature.YouCtrl | AddPower$ 1");
        fixture.index().resetDiagnostics();
        fixture.game.getAction().checkStaticAbilities(false, new HashSet<>(),
                new CardCollection(lki));

        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                1L, "removing a same-ID inbound copy must reveal original live identity");
        Assert.assertEquals(target.getNetPower(), 2);
    }

    @Test
    public void commandAndDynamicallyCreatedHiddenSourcesAreIndexed() {
        final Fixture fixture = new Fixture("command and hidden sources");
        final Card target = fixture.creature("Command target", 1, 1);
        fixture.player.getZone(ZoneType.Battlefield).add(target);
        final Card commandSource = fixture.card("Command source");
        commandSource.addStaticAbility(
                "Mode$ Continuous | EffectZone$ Command "
                        + "| Affected$ Creature.YouCtrl | AddPower$ 1");
        fixture.player.getZone(ZoneType.Command).add(commandSource);
        final Card hiddenSource = fixture.creature("Hidden source", 1, 1);
        fixture.player.getZone(ZoneType.Battlefield).add(hiddenSource);

        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(target.getNetPower(), 2,
                "Command must retain its old source-zone coverage");

        hiddenSource.addCounterInternal(CounterType.getType("Flying"), 1,
                fixture.player, false, null, null);
        fixture.index().resetDiagnostics();
        fixture.game.getAction().checkStaticAbilities(false);

        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                2L, "a live hidden counter static must join the command source");
        Assert.assertTrue(hiddenSource.hasKeyword(Keyword.FLYING),
                "the dynamically created hidden Continuous ability must apply");

        hiddenSource.subtractCounter(CounterType.getType("Flying"), 1,
                fixture.player);
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertFalse(hiddenSource.hasKeyword(Keyword.FLYING));
        fixture.player.getZone(ZoneType.Battlefield).remove(hiddenSource);
        fixture.player.getZone(ZoneType.Battlefield).add(hiddenSource);
        Assert.assertEquals(fixture.index().diagnostics().candidateCards(), 1,
                "zero-counter re-entry must remove the old hidden candidate");
        fixture.index().resetDiagnostics();
        hiddenSource.addCounterInternal(CounterType.getType("Flying"), 1,
                fixture.player, false, null, null);
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                2L, "a cached counter static must remain discoverable on re-add");
        Assert.assertTrue(hiddenSource.hasKeyword(Keyword.FLYING));
    }

    @Test
    public void cachedManabondStaticRemainsDiscoverableOnReAdd() {
        final Fixture fixture = new Fixture("cached manabond source");
        final Card source = fixture.creature("Manabond target", 1, 1);
        fixture.player.getZone(ZoneType.Battlefield).add(source);
        fixture.game.getAction().checkStaticAbilities(false);
        final CounterType manabond = CounterType.getType("MANABOND");

        source.addCounterInternal(manabond, 1, fixture.player, false, null,
                null);
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertTrue(source.isLand());
        source.subtractCounter(manabond, 1, fixture.player);
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertFalse(source.isLand());
        fixture.player.getZone(ZoneType.Battlefield).remove(source);
        fixture.player.getZone(ZoneType.Battlefield).add(source);
        Assert.assertEquals(fixture.index().diagnostics().candidateCards(), 0,
                "zero-counter re-entry must remove the old Manabond candidate");

        fixture.index().resetDiagnostics();
        source.addCounterInternal(manabond, 1, fixture.player, false, null,
                null);
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                1L, "the cached Manabond Continuous ability must be revisited");
        Assert.assertTrue(source.isLand(),
                "re-adding Manabond must restore its land type effect");
        Assert.assertFalse(source.getManaAbilities().isEmpty(),
                "re-adding Manabond must restore its reflected mana ability");
    }

    @Test
    public void cachedChangedKeywordStaticIsRecheckedAfterZoneReentry() {
        final Fixture fixture = new Fixture("cached changed keyword source");
        final Card target = fixture.creature("Changeling target", 1, 1);
        fixture.player.getZone(ZoneType.Battlefield).add(target);
        fixture.game.getAction().checkStaticAbilities(false);
        final Card grantHost = fixture.card("Keyword grant host");
        final StaticAbility grant = StaticAbility.create(
                "Mode$ Continuous | Affected$ Card.Self | AddPower$ 0",
                grantHost, grantHost.getCurrentState(), true);
        final long timestamp = 1234L;

        target.addChangedCardKeywords(List.of("Changeling"), List.of(), false,
                timestamp, grant, true);
        Assert.assertEquals(fixture.index().snapshotAfterStaticEffectsCleared(
                CardCollection.EMPTY).size(), 1,
                "a dynamically granted Continuous keyword must be indexed");
        target.removeChangedCardKeywords(timestamp, grant.getId(), true);
        Assert.assertTrue(fixture.index().snapshotAfterStaticEffectsCleared(
                CardCollection.EMPTY).isEmpty());
        fixture.player.getZone(ZoneType.Battlefield).remove(target);
        fixture.player.getZone(ZoneType.Battlefield).add(target);
        Assert.assertEquals(fixture.index().diagnostics().candidateCards(), 0);

        target.addChangedCardKeywords(List.of("Changeling"), List.of(), false,
                timestamp, grant, true);
        fixture.index().resetDiagnostics();
        Assert.assertEquals(fixture.index().snapshotAfterStaticEffectsCleared(
                CardCollection.EMPTY).size(), 1,
                "reusing storedKeywords must re-index its Continuous trait");
        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                1L);
    }

    @Test
    public void suppressedSourceReturnsAfterSuppressorLeaves() {
        final Fixture fixture = new Fixture("suppressed source lifetime");
        final Card source = fixture.creature("Suppressed source", 1, 1);
        source.addStaticAbility("Mode$ Continuous | EffectZone$ Battlefield "
                + "| Affected$ Card.Self | AddPower$ 1");
        final Card suppressor = fixture.card("Ability suppressor");
        suppressor.addStaticAbility("Mode$ Continuous "
                + "| EffectZone$ Battlefield | Affected$ Creature.YouCtrl "
                + "| RemoveAllAbilities$ True");
        fixture.player.getZone(ZoneType.Battlefield).add(source);
        fixture.player.getZone(ZoneType.Battlefield).add(suppressor);

        fixture.game.getAction().checkStaticAbilities(false);
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(source.getNetPower(), 1,
                "the suppressor must remove the source ability");

        fixture.player.getZone(ZoneType.Battlefield).remove(source);
        fixture.player.getZone(ZoneType.Battlefield).add(source);
        fixture.player.getZone(ZoneType.Battlefield).remove(suppressor);
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(source.getNetPower(), 2,
                "a suppressed source moved between indexed zones must return");
    }

    @Test
    public void bootstrapFailurePublishesNothingAndRetryUsesCurrentZones() {
        final Fixture fixture = new Fixture("transactional bootstrap");
        final Card original = fixture.continuousSource("Original source");
        fixture.player.getZone(ZoneType.Library).add(original);
        final Card addedDuringFailure = fixture.continuousSource(
                "Added during failure");
        final AtomicBoolean failOnce = new AtomicBoolean(true);
        final ContinuousStaticAbilitySourceIndex index =
                new ContinuousStaticAbilitySourceIndex(fixture.game, card -> {
                    if (card == original && failOnce.compareAndSet(true, false)) {
                        fixture.player.getZone(ZoneType.Graveyard)
                                .add(addedDuringFailure);
                        throw new IllegalStateException("injected bootstrap failure");
                    }
                    return card == original || card == addedDuringFailure;
                });

        Assert.expectThrows(IllegalStateException.class,
                () -> index.snapshotAfterStaticEffectsCleared(
                        CardCollection.EMPTY));
        Assert.assertEquals(index.diagnostics().liveCards(), 0,
                "a failed bootstrap cannot publish partial live entries");
        Assert.assertEquals(index.diagnostics().candidateCards(), 0,
                "a failed bootstrap cannot publish ghost candidates");
        Assert.assertEquals(index.diagnostics().bootstrapCardVisits(), 0L,
                "failed local visits are not committed to diagnostics");

        Assert.assertEquals(index.snapshotAfterStaticEffectsCleared(
                        CardCollection.EMPTY).stream().toList(),
                List.of(addedDuringFailure, original),
                "retry must rebuild from the zones as they exist after failure");
        Assert.assertEquals(index.diagnostics().liveCards(), 2);
        Assert.assertEquals(index.diagnostics().candidateCards(), 2);
    }

    @Test
    public void removedTemporarySourcesDoNotRemainInLargeLibraryHotPath() {
        final Fixture fixture = new Fixture("temporary source cleanup");
        final CardCollection cards = new CardCollection();
        final List<StaticAbility> temporary = new ArrayList<>();
        for (int i = 0; i < LARGE_LIBRARY_SIZE; i++) {
            cards.add(fixture.card("Temporary source " + i));
        }
        fixture.player.getZone(ZoneType.Library).setCards(cards);
        fixture.game.getAction().checkStaticAbilities(false);

        for (final Card card : cards) {
            temporary.add(card.addStaticAbility(
                    "Mode$ Continuous | EffectZone$ All "
                            + "| Affected$ Card.Self | AddPower$ 0"));
        }
        Assert.assertEquals(fixture.index()
                .snapshotAfterStaticEffectsCleared(CardCollection.EMPTY).size(),
                LARGE_LIBRARY_SIZE);
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).getCurrentState().removeStaticAbility(temporary.get(i));
        }
        Assert.assertTrue(fixture.index()
                .snapshotAfterStaticEffectsCleared(CardCollection.EMPTY)
                .isEmpty());
        Assert.assertEquals(fixture.index().diagnostics().candidateCards(), 0,
                "temporary Continuous grants must be pruned after clear");

        fixture.index().resetDiagnostics();
        Assert.assertTrue(fixture.index()
                .snapshotAfterStaticEffectsCleared(CardCollection.EMPTY)
                .isEmpty());
        Assert.assertEquals(fixture.index().diagnostics().candidateCardVisits(),
                0L, "subsequent hot passes must not revisit removed grants");
    }

    @Test
    public void playerLossRemovesSideboardSourcesAndAllPlayerReferences() {
        final Fixture fixture = new Fixture("player loss cleanup");
        final Player survivor = fixture.addPlayer("Survivor", 2, 2);
        final Card target = fixture.creature(survivor, "Surviving target", 1, 1);
        survivor.getZone(ZoneType.Battlefield).add(target);
        final Card sideboardSource = fixture.card("Departing sideboard source");
        sideboardSource.addStaticAbility(
                "Mode$ Continuous | EffectZone$ Sideboard "
                        + "| Affected$ Creature | AddPower$ 1");
        fixture.player.getZone(ZoneType.Sideboard).add(sideboardSource);

        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(target.getNetPower(), 2);
        fixture.game.onPlayerLost(fixture.player);
        fixture.game.getAction().checkStaticAbilities(false);

        Assert.assertEquals(target.getNetPower(), 1,
                "a departed player's unvisited sideboard cannot keep applying");
        Assert.assertEquals(fixture.index().diagnostics().candidateCards(), 0);
        Assert.assertEquals(fixture.index().diagnostics().liveCards(), 1,
                "the index must retain only the surviving battlefield card");
        Assert.assertTrue(fixture.player.getZone(ZoneType.Sideboard)
                .contains(sideboardSource),
                "the regression specifically requires physical sideboard "
                        + "contents to remain outside normal loss cleanup");
    }

    @Test
    public void separateGamesOwnIndependentIndexes() {
        final Fixture main = new Fixture("main game index");
        final Fixture child = new Fixture("child game index");
        final Card mainSource = main.continuousSource("Main source");
        final Card childSource = child.continuousSource("Child source");
        main.player.getZone(ZoneType.Library).add(mainSource);
        child.player.getZone(ZoneType.Library).add(childSource);

        Assert.assertEquals(main.index().snapshotAfterStaticEffectsCleared(
                        CardCollection.EMPTY)
                .stream().toList(), List.of(mainSource));
        Assert.assertEquals(child.index().snapshotAfterStaticEffectsCleared(
                        CardCollection.EMPTY)
                .stream().toList(), List.of(childSource));
        main.player.getZone(ZoneType.Library).remove(mainSource);
        Assert.assertTrue(main.index().snapshotAfterStaticEffectsCleared(
                CardCollection.EMPTY).isEmpty());
        Assert.assertEquals(child.index().snapshotAfterStaticEffectsCleared(
                        CardCollection.EMPTY)
                .stream().toList(), List.of(childSource),
                "snapshot/copy/restart/subgame Game instances cannot share index state");
    }

    @Test
    public void candidateAndLegacyCommandOrderMatchesFormerFullScanAfterReentry() {
        final Fixture fixture = new Fixture("candidate ordering");
        final Card graveyardSource = fixture.creature("Graveyard source", 1, 1);
        final Card librarySource = fixture.creature("Library source", 1, 1);
        for (final Card source : List.of(graveyardSource, librarySource)) {
            source.setLayerTimestamp(77L);
            source.addStaticAbility("Mode$ Continuous | EffectZone$ All "
                    + "| Affected$ Card.Self | AddPower$ 0");
        }
        final List<String> commandOrder = new ArrayList<>();
        addOrderCommand(graveyardSource, "graveyard", commandOrder);
        addOrderCommand(librarySource, "library", commandOrder);
        fixture.player.getZone(ZoneType.Graveyard).add(graveyardSource);
        fixture.player.getZone(ZoneType.Library).add(librarySource);

        final CardCollection initialSources = fixture.index()
                .snapshotAfterStaticEffectsCleared(CardCollection.EMPTY);
        Assert.assertEquals(initialSources.stream().toList(),
                List.of(graveyardSource, librarySource),
                "equal-timestamp Continuous sources must retain the old "
                        + "Graveyard-before-Library scan order");
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(commandOrder, List.of("graveyard", "library"));

        commandOrder.clear();
        addOrderCommand(graveyardSource, "library", commandOrder);
        addOrderCommand(librarySource, "graveyard", commandOrder);
        fixture.player.getZone(ZoneType.Graveyard).remove(graveyardSource);
        fixture.player.getZone(ZoneType.Library).add(graveyardSource);
        fixture.player.getZone(ZoneType.Library).remove(librarySource);
        fixture.player.getZone(ZoneType.Graveyard).add(librarySource);

        final CardCollection afterReentry = fixture.index()
                .snapshotAfterStaticEffectsCleared(CardCollection.EMPTY);
        Assert.assertEquals(afterReentry.stream().toList(),
                List.of(librarySource, graveyardSource),
                "re-entry time must not replace the fixed cross-zone order");
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(commandOrder, List.of("graveyard", "library"),
                "legacy static commands must execute in the former full-scan "
                        + "order after cross-zone re-entry");
    }

    @Test
    public void meldedInboundAndStackKeepTheirFormerContainerPositions() {
        final Fixture fixture = new Fixture("special container ordering");
        final Card battlefield = fixture.continuousSource("battlefield");
        final Card melded = fixture.continuousSource("melded");
        final Card exile = fixture.continuousSource("exile");
        final Card sideboard = fixture.continuousSource("sideboard");
        final Card inbound = fixture.continuousSource("inbound");
        final Card stack = fixture.continuousSource("stack");
        final Card stackSecond = fixture.continuousSource("stack second");

        final PlayerZoneBattlefield battlefieldZone =
                (PlayerZoneBattlefield) fixture.player.getZone(
                        ZoneType.Battlefield);
        battlefieldZone.add(battlefield);
        battlefieldZone.add(melded);
        battlefieldZone.addToMelded(melded);
        fixture.player.getZone(ZoneType.Exile).add(exile);
        fixture.player.getZone(ZoneType.Sideboard).add(sideboard);
        fixture.player.addInboundToken(inbound);
        fixture.game.getStackZone().add(stack);
        fixture.game.getStackZone().add(stackSecond);

        Assert.assertEquals(fixture.index().snapshotAfterStaticEffectsCleared(
                        CardCollection.EMPTY)
                        .stream().toList(),
                List.of(battlefield, melded, exile, sideboard, inbound, stack,
                        stackSecond),
                "special containers must retain Battlefield/Melded/Exile/"
                        + "Sideboard/Inbound/Stack scan order");

        fixture.game.getStackZone().reorder(stackSecond, 0);
        Assert.assertEquals(fixture.index().snapshotAfterStaticEffectsCleared(
                        CardCollection.EMPTY)
                        .stream().toList(),
                List.of(battlefield, melded, exile, sideboard, inbound,
                        stackSecond, stack),
                "candidate order must follow explicit in-zone reordering");
    }

    private static void addOrderCommand(final Card source,
            final String value, final List<String> output) {
        final GameCommand command = () -> output.add(value);
        source.addStaticCommandList(new Object[]{"1", "EQ1", source, command});
    }

    private static PaperCard realTest3Paper() throws IOException {
        Path script = Paths.get("custom", "cards", "colorless", "test3.txt")
                .toAbsolutePath().normalize();
        if (!Files.exists(script)) {
            script = Paths.get("..", "custom", "cards", "colorless",
                    "test3.txt").toAbsolutePath().normalize();
        }
        Assert.assertTrue(Files.isRegularFile(script),
                "real custom/cards/colorless/test3.txt must be loadable");
        final CardRules rules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "test3");
        return new PaperCard(rules, "TST", CardRarity.Common);
    }

    private static String workspacePath(final String first,
            final String... more) {
        Path result = Paths.get(first);
        for (final String element : more) {
            result = result.resolve(element);
        }
        if (!Files.exists(result)) {
            result = Paths.get("..").resolve(result);
        }
        return result.toAbsolutePath().normalize().toString();
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

    private static List<String> modeSourceNames(final Game game,
            final StaticAbilityMode mode) {
        final List<String> result = new ArrayList<>();
        game.visitStaticAbilityModeSources(mode, card -> {
            result.add(card.getName());
            return true;
        });
        return result;
    }

    private static List<String> modeSourceNames(final Game game,
            final StaticAbilityMode mode, final Iterable<ZoneType> zones) {
        final List<String> result = new ArrayList<>();
        game.visitStaticAbilityModeSources(mode, zones, card -> {
            result.add(card.getName());
            return true;
        });
        return result;
    }

    private static List<String> legacyBattlefieldModeSourceNames(
            final Game game, final StaticAbilityMode mode) {
        final List<String> result = new ArrayList<>();
        for (final Player player : game.getPlayers()) {
            for (final Card card : player.getCardsIn(ZoneType.Battlefield)) {
                for (final StaticAbility ability : card.getStaticAbilities()) {
                    if (ability.getMode() != null
                            && ability.getMode().contains(mode)) {
                        result.add(card.getName());
                        break;
                    }
                }
            }
        }
        return result;
    }

    private static StaticAbility legacyCantAttach(final GameEntity target,
            final Card attachment, final boolean checkSba) {
        for (final Card source : target.getGame().getCardsIn(
                ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            for (final StaticAbility ability : source.getStaticAbilities()) {
                if (ability.checkConditions(StaticAbilityMode.CantAttach)
                        && StaticAbilityCantAttach.applyCantAttachAbility(
                        ability, attachment, target, checkSba)) {
                    return ability;
                }
            }
        }
        return null;
    }

    private static boolean legacyIgnoreProtection(final GameEntity entity,
            final SpellAbility spellAbility, final StaticAbility keyword) {
        for (final Card source : entity.getGame().getCardsIn(
                ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            for (final StaticAbility ability : source.getStaticAbilities()) {
                if (keyword.isKeyword(Keyword.HEXPROOF)
                        && !ability.checkConditions(
                        StaticAbilityMode.IgnoreHexproof)) {
                    continue;
                }
                if (keyword.isKeyword(Keyword.SHROUD)
                        && !ability.checkConditions(
                        StaticAbilityMode.IgnoreShroud)) {
                    continue;
                }
                if (ability.matchesValidParam("Activator",
                        spellAbility.getActivatingPlayer())
                        && ability.matchesValidParam("ValidEntity", entity)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static StaticAbility keywordAbility(final Card card,
            final Keyword keyword) {
        for (final StaticAbility ability : card.getStaticAbilities()) {
            if (ability.isKeyword(keyword)) {
                return ability;
            }
        }
        throw new AssertionError("missing keyword ability " + keyword);
    }

    private static void setAbilityModes(final StaticAbility ability,
            final Set<StaticAbilityMode> modes)
            throws ReflectiveOperationException {
        final Field field = StaticAbility.class.getDeclaredField("modes");
        field.setAccessible(true);
        field.set(ability, modes);
    }

    private static void clearIntrinsicModes(final Card card)
            throws ReflectiveOperationException {
        final Field field = Card.class.getDeclaredField(
                "intrinsicStaticAbilityModes");
        field.setAccessible(true);
        ((Set<?>) field.get(card)).clear();
    }

    private static boolean hasModeLocation(
            final ContinuousStaticAbilitySourceIndex index,
            final Card expected) throws ReflectiveOperationException {
        final Field locationsField = ContinuousStaticAbilitySourceIndex.class
                .getDeclaredField("modeLocations");
        locationsField.setAccessible(true);
        final Map<?, ?> locations = (Map<?, ?>) locationsField.get(index);
        for (final Object location : locations.values()) {
            final Field cardField = location.getClass().getDeclaredField(
                    "card");
            cardField.setAccessible(true);
            if (cardField.get(location) == expected) {
                return true;
            }
        }
        return false;
    }

    private static final class Fixture {
        private final Game game;
        private final Player player;

        private Fixture(final String description) {
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(List.of(), rules,
                    new Match(rules, List.of(), description));
            player = new Player("Controller", game, 1);
            game.getPlayers().add(player);
            player.setTeam(1);
            game.getPhaseHandler().setPlayerTurn(player);
            game.setAge(GameStage.Play);
        }

        private Card card(final String name) {
            final Card card = new Card(game.nextCardId(), game);
            card.setName(name);
            card.setOwner(player);
            card.setController(player, game.getNextTimestamp());
            return card;
        }

        private Card card(final int id, final String name) {
            final Card card = new Card(id, game);
            card.setName(name);
            card.setOwner(player);
            card.setController(player, game.getNextTimestamp());
            return card;
        }

        private Card modeSource(final String name,
                final StaticAbilityMode mode) {
            final Card card = card(name);
            card.addStaticAbility("Mode$ " + mode + " | EffectZone$ All");
            return card;
        }

        private SpellAbility spell(final String name, final String manaCost) {
            final Card card = Card.fromPaperCard(paper(name, manaCost), player);
            // These focused engine fixtures intentionally have no UI
            // controller. Setting the logical zone is sufficient for casting
            // helpers and avoids invoking hand-order UI policy.
            card.setZone(player.getZone(ZoneType.Hand));
            final SpellAbility spell = card.getSpellAbilities().getFirst();
            spell.setActivatingPlayer(player);
            return spell;
        }

        private Card creature(final String name, final int power,
                final int toughness) {
            return creature(player, name, power, toughness);
        }

        private Card creature(final Player owner, final String name,
                final int power, final int toughness) {
            final Card card = card(owner, name);
            card.getCurrentState().addType("Creature");
            card.getCurrentState().setBasePower(power);
            card.getCurrentState().setBaseToughness(toughness);
            return card;
        }

        private Card card(final Player owner, final String name) {
            final Card card = new Card(game.nextCardId(), game);
            card.setName(name);
            card.setOwner(owner);
            card.setController(owner, game.getNextTimestamp());
            return card;
        }

        private Player addPlayer(final String name, final int id,
                final int team) {
            final Player added = new Player(name, game, id);
            game.getPlayers().add(added);
            added.setTeam(team);
            return added;
        }

        private Card continuousSource(final String name) {
            final Card card = card(name);
            card.addStaticAbility("Mode$ Continuous | EffectZone$ All "
                    + "| Affected$ Card.Self | AddPower$ 0");
            return card;
        }

        private ContinuousStaticAbilitySourceIndex index() {
            return game.getContinuousStaticAbilitySourceIndex();
        }
    }

    private static final class ZoneEventCounter {
        private int zoneEvents;
        private int changeZoneEvents;

        @Subscribe
        public void onZone(final GameEventZone event) {
            zoneEvents++;
        }

        @Subscribe
        public void onChangeZone(final GameEventCardChangeZone event) {
            changeZoneEvents++;
        }
    }

    private static final class CountingModeSet
            extends AbstractSet<StaticAbilityMode> {
        private final Set<StaticAbilityMode> delegate;
        private final AtomicLong reads;

        private CountingModeSet(final Set<StaticAbilityMode> delegate,
                final AtomicLong reads) {
            this.delegate = EnumSet.copyOf(delegate);
            this.reads = reads;
        }

        @Override
        public Iterator<StaticAbilityMode> iterator() {
            reads.incrementAndGet();
            return delegate.iterator();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean contains(final Object value) {
            reads.incrementAndGet();
            return delegate.contains(value);
        }
    }

    private static final class ThrowingModeSet
            extends AbstractSet<StaticAbilityMode> {
        private final StaticAbilityMode value;

        private ThrowingModeSet(final StaticAbilityMode value) {
            this.value = value;
        }

        @Override
        public Iterator<StaticAbilityMode> iterator() {
            throw new IllegalStateException("injected malformed mode set");
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean contains(final Object candidate) {
            return value == candidate;
        }
    }

    private static final class FailOnceContainsModeSet
            extends AbstractSet<StaticAbilityMode> {
        private final StaticAbilityMode value;
        private final AtomicBoolean fail = new AtomicBoolean(true);

        private FailOnceContainsModeSet(final StaticAbilityMode value) {
            this.value = value;
        }

        @Override
        public Iterator<StaticAbilityMode> iterator() {
            return Set.of(value).iterator();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean contains(final Object candidate) {
            if (fail.compareAndSet(true, false)) {
                throw new IllegalStateException(
                        "injected one-shot query classification failure");
            }
            return value == candidate;
        }
    }

    private static final class FailOnceIteratorModeSet
            extends AbstractSet<StaticAbilityMode> {
        private final StaticAbilityMode value;
        private final AtomicBoolean fail = new AtomicBoolean(true);

        private FailOnceIteratorModeSet(final StaticAbilityMode value) {
            this.value = value;
        }

        @Override
        public Iterator<StaticAbilityMode> iterator() {
            if (fail.compareAndSet(true, false)) {
                throw new IllegalStateException(
                        "injected one-shot mode discovery failure");
            }
            return Set.of(value).iterator();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean contains(final Object candidate) {
            return value == candidate;
        }
    }

    private static final class AlwaysThrowIteratorModeSet
            extends AbstractSet<StaticAbilityMode> {
        @Override
        public Iterator<StaticAbilityMode> iterator() {
            throw new IllegalStateException(
                    "injected permanent mode discovery failure");
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean contains(final Object candidate) {
            return false;
        }
    }

    private static final class AlwaysThrowContainsModeSet
            extends AbstractSet<StaticAbilityMode> {
        private final StaticAbilityMode value;

        private AlwaysThrowContainsModeSet(final StaticAbilityMode value) {
            this.value = value;
        }

        @Override
        public Iterator<StaticAbilityMode> iterator() {
            return Set.of(value).iterator();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean contains(final Object candidate) {
            throw new IllegalStateException(
                    "injected permanent mode contains failure");
        }
    }
}
