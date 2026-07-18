package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.StaticData;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.GameSnapshot;
import forge.game.GameState;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.event.GameEventSubgameEnd;
import forge.game.event.GameEventSubgameStart;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PlayerSpellRuleSubgameIntegrationTest extends AITest {
    private static final String RULE_KEY = "test:main-game-only";
    private static final String CHILD_RULE_KEY = "test:child-game-only";

    @Test
    public void snapshotBuildsAnIndependentContinuousSourceIndex() {
        final Game mainGame = initAndCreateGame();
        final Player player = mainGame.getPlayers().get(1);
        final Card target = createCard("Memnite", player);
        final Card source = Card.fromPaperCard(continuousPaperCard(
                "Snapshot Continuous Source", "All"), player);
        final Card modeSource = modeSource(mainGame, player,
                "Snapshot Mode Source");
        player.getZone(ZoneType.Battlefield).add(target);
        player.getZone(ZoneType.Battlefield).add(modeSource);
        player.getZone(ZoneType.Library).add(source);
        mainGame.getAction().checkStaticAbilities(false);
        Assert.assertEquals(target.getNetPower(), 2);

        final GameSnapshot snapshot = new GameSnapshot(mainGame);
        final Game copiedGame = snapshot.makeCopy();
        final Card copiedTarget = copiedGame.findById(target.getId());
        final Card copiedSource = copiedGame.findById(source.getId());
        final Card copiedModeSource = copiedGame.findById(modeSource.getId());
        Assert.assertNotNull(copiedTarget);
        Assert.assertNotNull(copiedSource);
        Assert.assertNotNull(copiedModeSource);
        Assert.assertNotSame(copiedTarget, target);
        Assert.assertNotSame(copiedSource, source);
        Assert.assertNotSame(copiedModeSource, modeSource);
        Assert.assertTrue(modeSourceNames(copiedGame)
                .contains("Snapshot Mode Source"));
        copiedGame.getAction().checkStaticAbilities(false);
        Assert.assertEquals(copiedTarget.getNetPower(), 2);

        player.getZone(ZoneType.Library).remove(source);
        player.getZone(ZoneType.Battlefield).remove(modeSource);
        mainGame.getAction().checkStaticAbilities(false);
        Assert.assertEquals(target.getNetPower(), 1);
        copiedGame.getAction().checkStaticAbilities(false);
        Assert.assertEquals(copiedTarget.getNetPower(), 2,
                "snapshot and main game indexes cannot share Card identity");
        Assert.assertFalse(modeSourceNames(mainGame)
                .contains("Snapshot Mode Source"));
        Assert.assertTrue(modeSourceNames(copiedGame)
                .contains("Snapshot Mode Source"),
                "snapshot mode buckets must retain only copied identities");

        snapshot.restoreGameState(mainGame);
        final Card restoredModeSource = mainGame.findById(modeSource.getId());
        Assert.assertNotNull(restoredModeSource);
        Assert.assertNotSame(restoredModeSource, modeSource);
        Assert.assertTrue(modeSourceNames(mainGame)
                .contains("Snapshot Mode Source"),
                "restoring into the existing Game must replace stale mode "
                        + "identities with restored ones");
    }

    @Test
    public void restartReindexesThePostMoveCardIdentity() {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card oldSource = Card.fromPaperCard(continuousPaperCard(
                "Restart Continuous Source", "All"), player);
        final Card oldModeSource = modeSource(game, player,
                "Restart Mode Source");
        final int sourceId = oldSource.getId();
        final int modeSourceId = oldModeSource.getId();
        final Card initialTarget = createCard("Memnite", player);
        final Card initialPartner = createCard("Memnite", player);
        final int partnerId = initialPartner.getId();
        player.getZone(ZoneType.Battlefield).add(oldSource);
        player.getZone(ZoneType.Battlefield).add(oldModeSource);
        player.getZone(ZoneType.Battlefield).add(initialTarget);
        player.getZone(ZoneType.Battlefield).add(initialPartner);
        initialTarget.setPairedWith(initialPartner);
        initialPartner.setPairedWith(initialTarget);
        game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(initialTarget.getNetPower(), 2);

        final Card restartHost = createCard("Karn Liberated", player);
        player.getZone(ZoneType.Exile).add(restartHost);
        final SpellAbility restart = AbilityFactory.getAbility(
                "DB$ RestartGame | RestrictFromZone$ Exile "
                        + "| RestrictFromValid$ Card", restartHost);
        restart.setActivatingPlayer(player);
        AbilityUtils.resolve(restart);

        final Card liveSource = game.findById(sourceId);
        final Card liveModeSource = game.findById(modeSourceId);
        Assert.assertNotNull(liveSource);
        Assert.assertNotNull(liveModeSource);
        Assert.assertNotSame(liveSource, oldSource);
        Assert.assertNotSame(liveModeSource, oldModeSource);
        Assert.assertTrue(liveSource.isInZone(ZoneType.Library));
        Assert.assertTrue(liveModeSource.isInZone(ZoneType.Library));
        Assert.assertFalse(modeSourceNames(game)
                .contains("Restart Mode Source"),
                "restart cannot leave the pre-restart Battlefield source in "
                        + "a stale mode bucket");
        player.getZone(ZoneType.Library).remove(liveModeSource);
        player.getZone(ZoneType.Battlefield).add(liveModeSource);
        Assert.assertTrue(modeSourceNames(game)
                .contains("Restart Mode Source"),
                "the post-restart identity must be indexed when it re-enters");
        final Card livePartner = game.findById(partnerId);
        Assert.assertNotNull(livePartner);
        Assert.assertNull(livePartner.getPairedWith(),
                "restart cannot retain a pair to a replaced battlefield identity");
        final Card postRestartTarget = createCard("Memnite", player);
        final Card postRestartPartner = createCard("Memnite", player);
        player.getZone(ZoneType.Battlefield).add(postRestartTarget);
        player.getZone(ZoneType.Battlefield).add(postRestartPartner);
        postRestartTarget.setPairedWith(postRestartPartner);
        postRestartPartner.setPairedWith(postRestartTarget);
        postRestartPartner.getCurrentState().removeCardTypes(true);
        game.getAction().checkStaticAbilities(false);
        Assert.assertNull(postRestartTarget.getPairedWith());
        Assert.assertNull(postRestartPartner.getPairedWith(),
                "post-restart dirty pair cleanup must use rebuilt identities");
        Assert.assertEquals(postRestartTarget.getNetPower(), 2,
                "the post-restart library identity must remain indexed");

        player.getZone(ZoneType.Library).remove(liveSource);
        player.getZone(ZoneType.Battlefield).remove(liveModeSource);
        game.getAction().checkStaticAbilities(false);
        Assert.assertEquals(postRestartTarget.getNetPower(), 1,
                "removing the post-restart identity must remove the source");
        Assert.assertFalse(modeSourceNames(game)
                .contains("Restart Mode Source"));
    }

    @Test
    public void gameStateRoundTripRestoresOnlyNewReciprocalPairIdentities()
            throws InterruptedException {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card originalFirst = createCard("Memnite", player);
        final Card originalSecond = createCard("Ornithopter", player);
        player.getZone(ZoneType.Battlefield).add(originalFirst);
        player.getZone(ZoneType.Battlefield).add(originalSecond);
        originalFirst.setPairedWith(originalSecond);
        originalSecond.setPairedWith(originalFirst);
        game.getAction().checkStaticAbilities(false);

        final GameState saved = new GameState();
        saved.initFromGame(game);
        Assert.assertTrue(saved.toString().contains("|PairedWith:"),
                "the portable state must encode pair endpoint IDs");
        applyGameStateAndWait(saved, game);

        final Card restoredFirst = findCardNamed(game, "Memnite");
        final Card restoredSecond = findCardNamed(game, "Ornithopter");
        Assert.assertNotNull(restoredFirst);
        Assert.assertNotNull(restoredSecond);
        Assert.assertNotSame(restoredFirst, originalFirst);
        Assert.assertNotSame(restoredSecond, originalSecond);
        Assert.assertSame(restoredFirst.getPairedWith(), restoredSecond);
        Assert.assertSame(restoredSecond.getPairedWith(), restoredFirst);
        game.getAction().checkStaticAbilities(false);
        Assert.assertSame(restoredFirst.getPairedWith(), restoredSecond,
                "the rebuilt tracker must accept the restored reciprocal pair");

        restoredSecond.getCurrentState().removeCardTypes(true);
        game.getAction().checkStaticAbilities(false);
        Assert.assertNull(restoredFirst.getPairedWith());
        Assert.assertNull(restoredSecond.getPairedWith(),
                "post-restore dirty cleanup must use only new identities");
    }

    @Test
    public void gameStateMalformedPairsFailClosedWithoutLeakingApplyFlags()
            throws InterruptedException {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card first = createCard("Memnite", player);
        final Card second = createCard("Ornithopter", player);
        player.getZone(ZoneType.Battlefield).add(first);
        player.getZone(ZoneType.Battlefield).add(second);
        first.setPairedWith(second);
        second.setPairedWith(first);

        final GameState saved = new GameState();
        saved.initFromGame(game);
        final String valid = saved.toString();
        final String firstToSecond = "|PairedWith:" + second.getId();
        final String secondToFirst = "|PairedWith:" + first.getId();
        final Map<String, String> malformedStates = new LinkedHashMap<>();
        malformedStates.put("not-int", valid.replace(firstToSecond,
                "|PairedWith:not-an-int"));
        malformedStates.put("int-overflow", valid.replace(firstToSecond,
                "|PairedWith:999999999999999999999"));
        malformedStates.put("duplicate-different-target",
                valid.replace(firstToSecond, firstToSecond
                        + "|PairedWith:" + first.getId()));
        malformedStates.put("conflicting-self-target",
                valid.replace(secondToFirst,
                        "|PairedWith:" + second.getId()));
        malformedStates.put("missing-target", valid.replace(firstToSecond,
                "|PairedWith:2147483000"));
        malformedStates.put("one-sided", valid.replace(secondToFirst, ""));

        for (final Map.Entry<String, String> malformed
                : malformedStates.entrySet()) {
            final GameState parsed = new GameState();
            parsed.parse(Arrays.asList(malformed.getValue().split("\\R")));
            applyGameStateAndWait(parsed, game);

            final Card restoredFirst = findCardNamed(game, "Memnite");
            final Card restoredSecond = findCardNamed(game, "Ornithopter");
            Assert.assertNotNull(restoredFirst, malformed.getKey());
            Assert.assertNotNull(restoredSecond, malformed.getKey());
            Assert.assertNull(restoredFirst.getPairedWith(),
                    malformed.getKey() + " must not half-restore a pair");
            Assert.assertNull(restoredSecond.getPairedWith(),
                    malformed.getKey());
            Assert.assertFalse(game.getStack().isResolving(),
                    malformed.getKey() + " leaked stack resolving state");
            Assert.assertFalse(game.getTriggerHandler().isTriggerSuppressed(
                    TriggerType.ChangesZone), malformed.getKey()
                            + " leaked trigger suppression");
        }
    }

    @Test
    public void gameStateCrossZoneDuplicateEndpointIdsFailClosed()
            throws InterruptedException {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card first = createCard("Memnite", player);
        final Card second = createCard("Ornithopter", player);
        player.getZone(ZoneType.Battlefield).add(first);
        player.getZone(ZoneType.Battlefield).add(second);
        player.getZone(ZoneType.Hand).add(createCard("Divination", player));
        player.getZone(ZoneType.Library).add(
                createCard("Divination", player));
        first.setPairedWith(second);
        second.setPairedWith(first);

        final GameState saved = new GameState();
        saved.initFromGame(game);
        final int playerIndex = game.getPlayers().indexOf(player);
        final Map<String, String> duplicates = new LinkedHashMap<>();
        duplicates.put("hand-battlefield",
                appendFieldToFirstCardInZone(saved.toString(), playerIndex,
                        "hand", "|Id:" + first.getId()));
        duplicates.put("library-battlefield",
                appendFieldToFirstCardInZone(saved.toString(), playerIndex,
                        "library", "|Id:" + second.getId()));

        for (final Map.Entry<String, String> duplicate
                : duplicates.entrySet()) {
            final GameState parsed = new GameState();
            parsed.parse(Arrays.asList(duplicate.getValue().split("\\R")));
            applyGameStateAndWait(parsed, game);
            final Card restoredFirst = findCardNamed(game, "Memnite");
            final Card restoredSecond = findCardNamed(game, "Ornithopter");
            Assert.assertNotNull(restoredFirst, duplicate.getKey());
            Assert.assertNotNull(restoredSecond, duplicate.getKey());
            Assert.assertNull(restoredFirst.getPairedWith(),
                    duplicate.getKey() + " must invalidate the whole pair");
            Assert.assertNull(restoredSecond.getPairedWith(),
                    duplicate.getKey());
        }
    }

    @Test
    public void gameStateLegalMultiplePairsIgnoreUnrelatedDuplicateIds()
            throws InterruptedException {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card firstA = createCard("Memnite", player);
        final Card firstB = createCard("Ornithopter", player);
        final Card secondA = createCard("Memnite", player);
        final Card secondB = createCard("Ornithopter", player);
        player.getZone(ZoneType.Battlefield).add(firstA);
        player.getZone(ZoneType.Battlefield).add(firstB);
        player.getZone(ZoneType.Battlefield).add(secondA);
        player.getZone(ZoneType.Battlefield).add(secondB);
        player.getZone(ZoneType.Hand).add(createCard("Divination", player));
        player.getZone(ZoneType.Library).add(
                createCard("Divination", player));
        firstA.setPairedWith(firstB);
        firstB.setPairedWith(firstA);
        secondA.setPairedWith(secondB);
        secondB.setPairedWith(secondA);

        final GameState saved = new GameState();
        saved.initFromGame(game);
        final int playerIndex = game.getPlayers().indexOf(player);
        final int unrelatedId = 2_147_482_000;
        String state = appendFieldToFirstCardInZone(saved.toString(),
                playerIndex, "hand", "|Id:" + unrelatedId
                        + "|PairedWith:" + firstA.getId());
        state = appendFieldToFirstCardInZone(state, playerIndex, "library",
                "|Id:" + unrelatedId);
        final GameState parsed = new GameState();
        parsed.parse(Arrays.asList(state.split("\\R")));
        applyGameStateAndWait(parsed, game);

        final Set<Card> restoredEndpoints = Collections.newSetFromMap(
                new IdentityHashMap<>());
        for (final Card card : game.getCardsIncludePhasingIn(
                ZoneType.Battlefield)) {
            if ("Memnite".equals(card.getName())
                    || "Ornithopter".equals(card.getName())) {
                restoredEndpoints.add(card);
            }
        }
        Assert.assertEquals(restoredEndpoints.size(), 4);
        for (final Card endpoint : restoredEndpoints) {
            Assert.assertNotNull(endpoint.getPairedWith());
            Assert.assertTrue(restoredEndpoints.contains(
                    endpoint.getPairedWith()));
            Assert.assertSame(endpoint.getPairedWith().getPairedWith(),
                    endpoint);
        }
    }

    @Test(timeOut = 15_000)
    public void realSubgameRoundTripKeepsRulesScopedToMainGame() {
        final Game mainGame = initAndCreateGame();
        final Player activatingPlayer = mainGame.getPlayers().get(1);
        activatingPlayer.getSpellRuleRegistry().register(
                RULE_KEY,
                "Card.nonColorless",
                "Spell",
                2,
                "AnyType->AnyColor");
        final List<String> mainStatesBefore = ruleStates(mainGame);

        final Card mainTarget = creature(mainGame, activatingPlayer,
                "Main target", 1, 1);
        final Card mainSource = continuousSource(mainGame, activatingPlayer,
                "Main sideboard source");
        final Card mainModeSource = modeSource(mainGame, activatingPlayer,
                "Main mode source");
        activatingPlayer.getZone(ZoneType.Battlefield).add(mainTarget);
        activatingPlayer.getZone(ZoneType.Battlefield).add(mainModeSource);
        activatingPlayer.getZone(ZoneType.Sideboard).add(mainSource);
        final Card mainPairA = creature(mainGame, activatingPlayer,
                "Main pair A", 1, 1);
        final Card mainPairB = creature(mainGame, activatingPlayer,
                "Main pair B", 1, 1);
        activatingPlayer.getZone(ZoneType.Battlefield).add(mainPairA);
        activatingPlayer.getZone(ZoneType.Battlefield).add(mainPairB);
        mainPairA.setPairedWith(mainPairB);
        mainPairB.setPairedWith(mainPairA);
        mainGame.getAction().checkStaticAbilities(false);
        Assert.assertEquals(mainTarget.getNetPower(), 2);

        final SubgameProbe probe = new SubgameProbe();
        mainGame.getMatch().subscribeToEvents(probe);
        final Card source = addCard("Karn Liberated", activatingPlayer);
        final SpellAbility subgame = AbilityFactory.getAbility(
                "DB$ Subgame | StartingLife$ 5", source);
        subgame.setActivatingPlayer(activatingPlayer);

        AbilityUtils.resolve(subgame);

        Assert.assertEquals(probe.startCount, 1);
        Assert.assertEquals(probe.endCount, 1);
        Assert.assertNotNull(probe.subgame);
        Assert.assertSame(probe.subgame.getMaingame(), mainGame);
        Assert.assertSame(probe.endedMainGame, mainGame);
        Assert.assertTrue(probe.subgame.isGameOver());
        Assert.assertTrue(probe.subgame.getOutcome().isDraw());
        Assert.assertEquals(probe.initialChildRuleStates,
                emptyRuleStates(mainGame.getPlayers().size()));
        Assert.assertEquals(ruleStates(probe.subgame),
                probe.childRuleStatesAfterRegistration);
        Assert.assertEquals(probe.childRuleStatesAfterRegistration.stream()
                .filter(state -> !state.isEmpty()).count(), 1L);
        Assert.assertEquals(ruleStates(mainGame), mainStatesBefore);
        Assert.assertTrue(probe.childIndexEffectApplied);
        Assert.assertTrue(probe.childIndexEffectRemoved);
        Assert.assertTrue(probe.childDerivedPairCleaned);
        Assert.assertTrue(probe.childModeSourceApplied);
        Assert.assertTrue(probe.childModeSourceRemoved);
        Assert.assertFalse(probe.childSawMainModeSource);
        Assert.assertTrue(modeSourceNames(mainGame)
                .contains("Main mode source"));
        Assert.assertFalse(modeSourceNames(mainGame)
                .contains("Child mode source"),
                "child mode-bucket writes cannot pollute the main game");
        Assert.assertSame(mainPairA.getPairedWith(), mainPairB,
                "child derived-state cleanup cannot mutate a main-game pair");
        Assert.assertSame(mainPairB.getPairedWith(), mainPairA);
        mainGame.getAction().checkStaticAbilities(false);
        Assert.assertEquals(mainTarget.getNetPower(), 2,
                "child index mutations cannot leak into the main game index");
    }

    private static List<String> ruleStates(final Game game) {
        final List<String> states = new ArrayList<>();
        for (final Player player : game.getPlayers()) {
            states.add(player.getSpellRuleRegistry().toStateString());
        }
        return states;
    }

    private static Card findCardNamed(final Game game, final String name) {
        for (final Card card : game.getCardsIncludePhasingIn(
                ZoneType.Battlefield)) {
            if (name.equals(card.getName())) {
                return card;
            }
        }
        return null;
    }

    private static String appendFieldToFirstCardInZone(final String state,
            final int playerIndex, final String zone, final String field) {
        final String marker = "p" + playerIndex + zone + "=";
        final int cardStart = state.indexOf(marker) + marker.length();
        Assert.assertTrue(cardStart >= marker.length(),
                "missing game-state zone " + marker);
        final int lineEnd = state.indexOf('\n', cardStart);
        final int firstField = state.indexOf('|', cardStart);
        final int insertion = firstField >= 0
                && (lineEnd < 0 || firstField < lineEnd)
                ? firstField : lineEnd < 0 ? state.length() : lineEnd;
        return state.substring(0, insertion) + field
                + state.substring(insertion);
    }

    private static void applyGameStateAndWait(final GameState state,
            final Game game) throws InterruptedException {
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        game.getAction().invoke(() -> {
            try {
                // This second call observes the game thread and therefore
                // exercises the public production entry synchronously.
                state.applyToGame(game);
            } catch (final Throwable throwable) {
                failure.set(throwable);
            } finally {
                finished.countDown();
            }
        });
        Assert.assertTrue(finished.await(10, TimeUnit.SECONDS),
                "timed out waiting for GameState restore");
        if (failure.get() != null) {
            Assert.fail("GameState restore failed", failure.get());
        }
    }

    private static List<String> emptyRuleStates(final int playerCount) {
        final List<String> states = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            states.add("");
        }
        return states;
    }

    private static Card card(final Game game, final Player owner,
            final String name) {
        final PaperCard paper = StaticData.instance().getCommonCards()
                .getCard("Memnite");
        if (paper == null) {
            throw new AssertionError("Memnite must be loaded for subgame test");
        }
        final Card card = Card.fromPaperCard(paper, owner);
        card.setName(name);
        return card;
    }

    private static PaperCard continuousPaperCard(final String name,
            final String effectZone) {
        return new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:0",
                "Types:Artifact",
                "S:Mode$ Continuous | EffectZone$ " + effectZone
                        + " | Affected$ Creature.YouCtrl | AddPower$ 1",
                "Oracle:Test continuous source."
        )), "TST", CardRarity.Common);
    }

    private static Card creature(final Game game, final Player owner,
            final String name, final int power, final int toughness) {
        final Card card = card(game, owner, name);
        card.getCurrentState().addType("Creature");
        card.getCurrentState().setBasePower(power);
        card.getCurrentState().setBaseToughness(toughness);
        return card;
    }

    private static Card continuousSource(final Game game, final Player owner,
            final String name) {
        final Card card = card(game, owner, name);
        card.addStaticAbility("Mode$ Continuous | EffectZone$ Sideboard "
                + "| Affected$ Creature | AddPower$ 1");
        return card;
    }

    private static Card modeSource(final Game game, final Player owner,
            final String name) {
        return Card.fromPaperCard(new PaperCard(CardRules.fromScript(
                Arrays.asList(
                        "Name:" + name,
                        "ManaCost:0",
                        "Types:Artifact",
                        "S:Mode$ CantAttach | EffectZone$ All "
                                + "| ValidCard$ Card | Target$ Player",
                        "Oracle:Test mode source.")),
                "TST", CardRarity.Common), owner);
    }

    private static List<String> modeSourceNames(final Game game) {
        final List<String> names = new ArrayList<>();
        game.visitStaticAbilityModeSources(StaticAbilityMode.CantAttach,
                source -> {
                    names.add(source.getName());
                    return true;
                });
        return names;
    }

    private static final class SubgameProbe {
        private Game subgame;
        private Game endedMainGame;
        private List<String> initialChildRuleStates = List.of();
        private List<String> childRuleStatesAfterRegistration = List.of();
        private int startCount;
        private int endCount;
        private boolean childIndexEffectApplied;
        private boolean childIndexEffectRemoved;
        private boolean childDerivedPairCleaned;
        private boolean childModeSourceApplied;
        private boolean childModeSourceRemoved;
        private boolean childSawMainModeSource;

        @Subscribe
        public void onSubgameStart(final GameEventSubgameStart event) {
            startCount++;
            subgame = event.subgame();
            initialChildRuleStates = ruleStates(subgame);
            subgame.getPlayers().get(0).getSpellRuleRegistry().register(
                    CHILD_RULE_KEY,
                    "Card.nonColorless",
                    "Spell",
                    1,
                    "AnyType->AnyColor");
            childRuleStatesAfterRegistration = ruleStates(subgame);
            final Player childPlayer = subgame.getPlayers().get(0);
            final Card childTarget = creature(subgame, childPlayer,
                    "Child target", 1, 1);
            final Card childSource = continuousSource(subgame, childPlayer,
                    "Child sideboard source");
            final Card childModeSource = modeSource(subgame, childPlayer,
                    "Child mode source");
            final Card childPair = creature(subgame, childPlayer,
                    "Child pair", 1, 1);
            final Card invalidPartner = card(subgame, childPlayer,
                    "Child invalid partner");
            invalidPartner.getCurrentState().removeCardTypes(true);
            invalidPartner.getCurrentState().addType("Artifact");
            childPlayer.getZone(ZoneType.Battlefield).add(childTarget);
            childPlayer.getZone(ZoneType.Battlefield).add(childModeSource);
            childPlayer.getZone(ZoneType.Battlefield).add(childPair);
            childPlayer.getZone(ZoneType.Battlefield).add(invalidPartner);
            childPlayer.getZone(ZoneType.Sideboard).add(childSource);
            childPair.setPairedWith(invalidPartner);
            invalidPartner.setPairedWith(childPair);
            subgame.getAction().checkStaticAbilities(false);
            childIndexEffectApplied = childTarget.getNetPower() == 2;
            childModeSourceApplied = modeSourceNames(subgame)
                    .contains("Child mode source");
            childSawMainModeSource = modeSourceNames(subgame)
                    .contains("Main mode source");
            childDerivedPairCleaned = childPair.getPairedWith() == null
                    && invalidPartner.getPairedWith() == null;
            childPlayer.getZone(ZoneType.Sideboard).remove(childSource);
            childPlayer.getZone(ZoneType.Battlefield)
                    .remove(childModeSource);
            subgame.getAction().checkStaticAbilities(false);
            childIndexEffectRemoved = childTarget.getNetPower() == 1;
            childModeSourceRemoved = !modeSourceNames(subgame)
                    .contains("Child mode source");
            for (final Player player : subgame.getPlayers()) {
                player.intentionalDraw();
            }
            subgame.setGameOver(GameEndReason.Draw);
        }

        @Subscribe
        public void onSubgameEnd(final GameEventSubgameEnd event) {
            endCount++;
            endedMainGame = event.maingame();
        }
    }
}
