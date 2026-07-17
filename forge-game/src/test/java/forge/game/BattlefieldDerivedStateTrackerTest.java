package forge.game;

import forge.LobbyPlayer;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCopyService;
import forge.game.phase.PhaseType;
import forge.game.player.IGameEntitiesFactory;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.player.RegisteredPlayer;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BattlefieldDerivedStateTrackerTest {
    private static final int LARGE_BATTLEFIELD_SIZE = 20_000;

    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res",
                "languages").toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void replacementStyleChecksNeverTraverseAnUnchangedLargeBattlefield() {
        final Fixture fixture = new Fixture("derived cleanup hot path", true);
        final CardCollection battlefield = new CardCollection();
        for (int i = 0; i < LARGE_BATTLEFIELD_SIZE; i++) {
            final Card test3 = fixture.card("test3 " + i);
            test3.addStaticAbility(
                    "Mode$ IgnoreLegendRule | EffectZone$ All");
            battlefield.add(test3);
        }
        fixture.player.getZone(ZoneType.Battlefield).setCards(battlefield);

        final int replacements = 8;
        for (int i = 0; i < replacements; i++) {
            fixture.player.getZone(ZoneType.Library).add(
                    fixture.card("Original " + i));
        }
        fixture.game.getAction().checkStaticAbilities(false);
        final BattlefieldDerivedStateTracker tracker = fixture.tracker();
        tracker.resetDiagnostics();

        for (int i = 0; i < replacements; i++) {
            final Card original = fixture.player.getZone(ZoneType.Library)
                    .get(i);
            final Card staged = fixture.card("Replacement " + i);
            final Card prepared = fixture.game.getAction().moveTo(
                    fixture.player.getZone(ZoneType.None), staged, null);
            fixture.game.getAction().ceaseToExist(original, true);
            fixture.game.getAction().moveTo(
                    fixture.player.getZone(ZoneType.Library), prepared, i,
                    null, AbilityKey.newMap());
        }

        final BattlefieldDerivedStateTracker.Diagnostics diagnostics =
                tracker.diagnostics();
        Assert.assertEquals(diagnostics.liveBattlefieldCards(),
                LARGE_BATTLEFIELD_SIZE);
        Assert.assertEquals(diagnostics.controllerCandidateVisits(), 0L);
        Assert.assertEquals(diagnostics.pairCandidateVisits(), 0L);
        Assert.assertEquals(diagnostics.affectedFallbackVisits(), 0L);
        Assert.assertEquals(diagnostics.fullBattlefieldFallbackVisits(), 0L,
                "three static checks per replacement must not become 3*k*B");
    }

    @Test
    public void oneControllerChangeVisitsOnlyItsIdentityAndThenUnpairs() {
        final Fixture fixture = new Fixture("one dirty controller", true);
        final Card first = fixture.creature("First");
        final Card partner = fixture.creature("Partner");
        fixture.player.getZone(ZoneType.Battlefield).add(first);
        fixture.player.getZone(ZoneType.Battlefield).add(partner);
        first.setPairedWith(partner);
        partner.setPairedWith(first);
        fixture.game.getAction().checkStaticAbilities(false);

        final BattlefieldDerivedStateTracker tracker = fixture.tracker();
        tracker.resetDiagnostics();
        first.addTempController(fixture.opponent,
                fixture.game.getNextTimestamp());
        fixture.game.getAction().checkStaticAbilities(false);

        Assert.assertEquals(tracker.diagnostics().controllerCandidateVisits(),
                1L);
        Assert.assertTrue(containsIdentity(
                fixture.opponent.getCardsIn(ZoneType.Battlefield), first));
        Assert.assertNull(first.getPairedWith());
        Assert.assertNull(partner.getPairedWith());
    }

    @Test
    public void typeLossAndLeavingInvalidateButPhasingPreservesKnownPairs() {
        final Fixture fixture = new Fixture("pair invalidation", true);

        final Card bothLoseA = fixture.creature("Both lose A");
        final Card bothLoseB = fixture.creature("Both lose B");
        fixture.player.getZone(ZoneType.Battlefield).add(bothLoseA);
        fixture.player.getZone(ZoneType.Battlefield).add(bothLoseB);
        pair(bothLoseA, bothLoseB);
        bothLoseA.getCurrentState().removeCardTypes(true);
        bothLoseB.getCurrentState().removeCardTypes(true);
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertNull(bothLoseA.getPairedWith());
        Assert.assertNull(bothLoseB.getPairedWith());

        final Card leaving = fixture.creature("Leaving");
        final Card survivor = fixture.creature("Survivor");
        fixture.player.getZone(ZoneType.Battlefield).add(leaving);
        fixture.player.getZone(ZoneType.Battlefield).add(survivor);
        pair(leaving, survivor);
        fixture.game.getAction().ceaseToExist(leaving, true);
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertNull(leaving.getPairedWith());
        Assert.assertNull(survivor.getPairedWith());

        final Card phased = fixture.creature("Phased");
        final Card phasePartner = fixture.creature("Phase partner");
        fixture.player.getZone(ZoneType.Battlefield).add(phased);
        fixture.player.getZone(ZoneType.Battlefield).add(phasePartner);
        pair(phased, phasePartner);
        phased.phase(false);
        Assert.assertTrue(phased.isPhasedOut());
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertSame(phased.getPairedWith(), phasePartner,
                "Soulbond pairing survives the real phase-out path");
        Assert.assertSame(phasePartner.getPairedWith(), phased);

        phased.phase(false);
        Assert.assertFalse(phased.isPhasedOut());
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertSame(phased.getPairedWith(), phasePartner,
                "the preserved pair resumes after phasing in");
        Assert.assertSame(phasePartner.getPairedWith(), phased);
    }

    @Test
    public void faceDownOriginalStateMarksOnlyItsKnownPairDirty() {
        final Fixture fixture = new Fixture("face-down type invalidation", true);
        final Card first = fixture.creature("Face-down first");
        final Card partner = fixture.creature("Face-down partner");
        fixture.player.getZone(ZoneType.Battlefield).add(first);
        fixture.player.getZone(ZoneType.Battlefield).add(partner);
        pair(first, partner);
        fixture.game.getAction().checkStaticAbilities(false);
        fixture.tracker().resetDiagnostics();

        first.setOriginalStateAsFaceDown();
        fixture.game.getAction().checkStaticAbilities(false);

        final BattlefieldDerivedStateTracker.Diagnostics diagnostics =
                fixture.tracker().diagnostics();
        Assert.assertEquals(diagnostics.pairCandidateVisits(), 2L,
                "the bypass mutator must dirty only the reciprocal pair");
        Assert.assertSame(first.getPairedWith(), partner,
                "face-down creature characteristics keep a valid pair");
        Assert.assertSame(partner.getPairedWith(), first);

        final Card offBattlefield = fixture.card("Off-battlefield morph");
        fixture.tracker().resetDiagnostics();
        offBattlefield.setOriginalStateAsFaceDown();
        Assert.assertEquals(fixture.tracker().diagnostics()
                .pendingPairCards(), 0,
                "a bypass mutator outside the battlefield stays untracked");
    }

    @Test
    public void bulkBattlefieldLifecycleIsStrictlyLinearByVisitCount() {
        final Fixture fixture = new Fixture("bulk lifecycle", true);
        final CardCollection battlefield = new CardCollection();
        for (int i = 0; i < LARGE_BATTLEFIELD_SIZE; i++) {
            battlefield.add(fixture.card("Bulk card " + i));
        }
        fixture.player.getZone(ZoneType.Battlefield).setCards(battlefield);
        final BattlefieldDerivedStateTracker tracker = fixture.tracker();

        tracker.resetDiagnostics();
        fixture.player.getZone(ZoneType.Battlefield).removeAllCards(true);
        BattlefieldDerivedStateTracker.Diagnostics diagnostics =
                tracker.diagnostics();
        assertBulkRemovalVisits(diagnostics, LARGE_BATTLEFIELD_SIZE);
        Assert.assertEquals(diagnostics.bulkIdentitySetVisits(), 0L);
        Assert.assertEquals(diagnostics.liveBattlefieldCards(), 0);

        fixture.player.getZone(ZoneType.Battlefield).setCards(battlefield);
        tracker.resetDiagnostics();
        fixture.player.getZone(ZoneType.Battlefield).setCards(battlefield);
        diagnostics = tracker.diagnostics();
        assertBulkRemovalVisits(diagnostics, LARGE_BATTLEFIELD_SIZE);
        Assert.assertEquals(diagnostics.bulkIdentitySetVisits(),
                (long) LARGE_BATTLEFIELD_SIZE);
        Assert.assertEquals(diagnostics.liveBattlefieldCards(),
                LARGE_BATTLEFIELD_SIZE);

        tracker.resetDiagnostics();
        tracker.playerRemoved(fixture.player);
        diagnostics = tracker.diagnostics();
        assertBulkRemovalVisits(diagnostics, LARGE_BATTLEFIELD_SIZE);
        Assert.assertEquals(diagnostics.bulkIdentitySetVisits(), 0L);
        Assert.assertEquals(diagnostics.liveBattlefieldCards(), 0);
    }

    @Test
    public void staleOldContainerLeaveCannotDeleteMigratedIdentity() {
        final Fixture fixture = new Fixture("cross-container identity", true);
        final Card moved = fixture.creature("Migrated card");
        fixture.player.getZone(ZoneType.Battlefield).add(moved);
        moved.setController(fixture.opponent,
                fixture.game.getNextTimestamp());

        final BattlefieldDerivedStateTracker tracker = fixture.tracker();
        tracker.resetDiagnostics();
        fixture.opponent.getZone(ZoneType.Battlefield).setCards(
                new CardCollection(moved));
        Assert.assertEquals(tracker.diagnostics().liveBattlefieldCards(), 1);
        Assert.assertEquals(tracker.diagnostics().bulkIdentitySetVisits(), 1L);

        // The old physical container can emit its delayed remove after the
        // new container has already registered the same identity.
        fixture.player.getZone(ZoneType.Battlefield).remove(moved);
        Assert.assertEquals(tracker.diagnostics().liveBattlefieldCards(), 1,
                "an old-container callback cannot unregister the new slot");
        Assert.assertTrue(containsIdentity(fixture.opponent.getCardsIn(
                ZoneType.Battlefield), moved));

        tracker.resetDiagnostics();
        tracker.zoneContentsSet(
                fixture.opponent.getZone(ZoneType.Battlefield),
                List.of(moved, moved));
        final BattlefieldDerivedStateTracker.Diagnostics duplicate =
                tracker.diagnostics();
        Assert.assertEquals(duplicate.bulkContainerScanVisits(), 1L);
        Assert.assertEquals(duplicate.bulkIdentityRemovalVisits(), 1L);
        Assert.assertEquals(duplicate.bulkIdentitySetVisits(), 1L,
                "duplicate incoming identities deterministically collapse");
        Assert.assertEquals(duplicate.liveBattlefieldCards(), 1);

        fixture.opponent.getZone(ZoneType.Battlefield).remove(moved);
        Assert.assertEquals(tracker.diagnostics().liveBattlefieldCards(), 0);
    }

    @Test
    public void lkiWithTheSameIdCanNeverDirtyOrMoveTheLiveCard() {
        final Fixture fixture = new Fixture("LKI identity", true);
        final Card live = fixture.creature("Live identity");
        fixture.player.getZone(ZoneType.Battlefield).add(live);
        fixture.game.getAction().checkStaticAbilities(false);
        final BattlefieldDerivedStateTracker tracker = fixture.tracker();
        tracker.resetDiagnostics();

        final Card lki = CardCopyService.getLKICopy(live);
        Assert.assertEquals(lki.getId(), live.getId());
        Assert.assertNotSame(lki, live);
        lki.setController(fixture.opponent, fixture.game.getNextTimestamp());
        fixture.game.getAction().checkStaticAbilities(false);

        Assert.assertEquals(tracker.diagnostics().controllerCandidateVisits(),
                0L);
        Assert.assertTrue(containsIdentity(
                fixture.player.getCardsIn(ZoneType.Battlefield), live));
        Assert.assertFalse(containsIdentity(
                fixture.opponent.getCardsIn(ZoneType.Battlefield), live));
    }

    @Test
    public void preListDoesNotDrainPendingRealGameCleanup() {
        final Fixture fixture = new Fixture("preList isolation", true);
        final Card live = fixture.creature("Pending control");
        fixture.player.getZone(ZoneType.Battlefield).add(live);
        fixture.game.getAction().checkStaticAbilities(false);
        live.addTempController(fixture.opponent,
                fixture.game.getNextTimestamp());

        final Card lki = CardCopyService.getLKICopy(live);
        fixture.game.getAction().checkStaticAbilities(false, new HashSet<>(),
                new CardCollection(lki));
        Assert.assertTrue(containsIdentity(
                fixture.player.getCardsIn(ZoneType.Battlefield), live),
                "an LKI pre-check cannot mutate the real battlefield");
        Assert.assertEquals(fixture.tracker().diagnostics()
                .pendingControllerCards(), 1);

        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertTrue(containsIdentity(
                fixture.opponent.getCardsIn(ZoneType.Battlefield), live));
    }

    @Test
    public void failedCorrectionRemainsDirtyAndCanBeRetried() {
        final Fixture fixture = new Fixture("dirty retry", true);
        final Card live = fixture.creature("Retry controller");
        fixture.player.getZone(ZoneType.Battlefield).add(live);
        fixture.game.getAction().checkStaticAbilities(false);
        live.addTempController(fixture.opponent,
                fixture.game.getNextTimestamp());

        final Set<Card> affected = new HashSet<>();
        Assert.expectThrows(IllegalStateException.class,
                () -> fixture.tracker().drain(affected, card -> {
                    throw new IllegalStateException("injected correction failure");
                }));
        Assert.assertEquals(fixture.tracker().diagnostics()
                .pendingControllerCards(), 1,
                "a failed correction must remain retryable");

        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertTrue(containsIdentity(
                fixture.opponent.getCardsIn(ZoneType.Battlefield), live));
        Assert.assertEquals(fixture.tracker().diagnostics()
                .pendingControllerCards(), 0);
    }

    @Test
    public void fixedPointGuardRetainsAnOscillatingControllerForLaterRetry() {
        final Fixture fixture = new Fixture("fixed point guard", true);
        final Card live = fixture.creature("Oscillating controller");
        fixture.player.getZone(ZoneType.Battlefield).add(live);
        fixture.game.getAction().checkStaticAbilities(false);
        live.setController(fixture.opponent, fixture.game.getNextTimestamp());

        fixture.tracker().drain(new HashSet<>(), card -> {
            card.setController(fixture.player,
                    fixture.game.getNextTimestamp());
            card.setController(fixture.opponent,
                    fixture.game.getNextTimestamp());
        });

        final BattlefieldDerivedStateTracker.Diagnostics deferred =
                fixture.tracker().diagnostics();
        Assert.assertEquals(deferred.fixedPointDeferrals(), 1L);
        Assert.assertEquals(deferred.pendingControllerCards(), 1,
                "the guard must defer rather than discard oscillating work");
        fixture.game.getAction().checkStaticAbilities(false);
        Assert.assertTrue(containsIdentity(
                fixture.opponent.getCardsIn(ZoneType.Battlefield), live));
    }

    @Test
    public void staticCheckHoldIsNestedAndExceptionSafe() {
        final Fixture fixture = new Fixture("nested static hold", false);
        final GameAction action = fixture.game.getAction();
        Assert.assertFalse(action.isCheckingStaticAbilitiesOnHold());

        try (GameAction.StaticAbilityCheckScope outer =
                     action.holdStaticAbilityChecks()) {
            Assert.assertTrue(action.isCheckingStaticAbilitiesOnHold());
            try {
                try (GameAction.StaticAbilityCheckScope inner =
                             action.holdStaticAbilityChecks()) {
                    Assert.assertTrue(action.isCheckingStaticAbilitiesOnHold());
                    throw new IllegalStateException("injected SBA failure");
                }
            } catch (final IllegalStateException expected) {
                Assert.assertEquals(expected.getMessage(),
                        "injected SBA failure");
            }
            Assert.assertTrue(action.isCheckingStaticAbilitiesOnHold(),
                    "closing an inner scope cannot release the outer hold");
        }
        Assert.assertFalse(action.isCheckingStaticAbilitiesOnHold());
    }

    @Test
    public void controllerCorrectionPreservesOuterTriggerSuppression() {
        final Fixture fixture = new Fixture("suppression scope", true);
        final Card live = fixture.creature("Suppressed correction");
        fixture.player.getZone(ZoneType.Battlefield).add(live);
        live.setController(fixture.opponent, fixture.game.getNextTimestamp());

        fixture.game.getTriggerHandler().suppressMode(
                TriggerType.ChangesZone);
        try {
            fixture.game.getAction().controllerChangeZoneCorrection(live);
            Assert.assertTrue(fixture.game.getTriggerHandler()
                    .isTriggerSuppressed(TriggerType.ChangesZone));
        } finally {
            fixture.game.getTriggerHandler().clearSuppression(
                    TriggerType.ChangesZone);
        }
    }

    @Test
    public void snapshotRebuildsOnlyLiveCopiedIdentities() {
        final RegisteredFixture fixture = new RegisteredFixture(
                "derived tracker lifecycle");
        final Card first = fixture.creature("Snapshot first");
        final Card second = fixture.creature("Snapshot second");
        fixture.player.getZone(ZoneType.Battlefield).add(first);
        fixture.player.getZone(ZoneType.Battlefield).add(second);
        pair(first, second);
        fixture.game.getAction().checkStaticAbilities(false);

        final Game copy = new GameSnapshot(fixture.game).makeCopy();
        final Card copiedFirst = copy.findById(first.getId());
        final Card copiedSecond = copy.findById(second.getId());
        Assert.assertNotSame(copiedFirst, first);
        Assert.assertNotSame(copiedSecond, second);
        Assert.assertSame(copiedFirst.getPairedWith(), copiedSecond);
        Assert.assertEquals(copy.getBattlefieldDerivedStateTracker()
                .diagnostics().liveBattlefieldCards(), 2);
        copiedFirst.getCurrentState().removeCardTypes(true);
        copy.getAction().checkStaticAbilities(false);
        Assert.assertNull(copiedFirst.getPairedWith());
        Assert.assertSame(first.getPairedWith(), second,
                "snapshot cleanup cannot mutate main-game identities");
    }

    private static void pair(final Card first, final Card second) {
        first.setPairedWith(second);
        second.setPairedWith(first);
    }

    private static void assertBulkRemovalVisits(
            final BattlefieldDerivedStateTracker.Diagnostics diagnostics,
            final int expected) {
        Assert.assertEquals(diagnostics.bulkContainerScanVisits(),
                (long) expected,
                "each existing identity is scanned exactly once");
        Assert.assertEquals(diagnostics.bulkIdentityRemovalVisits(),
                (long) expected,
                "each existing identity is removed exactly once");
        Assert.assertEquals(diagnostics.bulkPartnerCandidateVisits(),
                (long) expected,
                "partner liveness is checked once per removed identity");
    }

    private static boolean containsIdentity(final Iterable<Card> cards,
            final Card expected) {
        for (final Card card : cards) {
            if (card == expected) {
                return true;
            }
        }
        return false;
    }

    private static class Fixture {
        protected final Game game;
        protected final Player player;
        protected final Player opponent;

        private Fixture(final String description, final boolean twoPlayers) {
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(List.of(), rules,
                    new Match(rules, List.of(), description));
            player = new Player("Player", game, 1);
            game.getPlayers().add(player);
            player.setTeam(1);
            if (twoPlayers) {
                opponent = new Player("Opponent", game, 2);
                game.getPlayers().add(opponent);
                opponent.setTeam(2);
            } else {
                opponent = null;
            }
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, player);
            game.setAge(GameStage.Play);
        }

        protected Card card(final String name) {
            final Card card = Card.fromPaperCard(paper(name), player);
            card.setOwner(player);
            card.setController(player, game.getNextTimestamp());
            return card;
        }

        protected Card creature(final String name) {
            final Card card = card(name);
            card.getCurrentState().addType("Creature");
            card.getCurrentState().setBasePower(1);
            card.getCurrentState().setBaseToughness(1);
            return card;
        }

        protected BattlefieldDerivedStateTracker tracker() {
            return game.getBattlefieldDerivedStateTracker();
        }
    }

    private static final class RegisteredFixture {
        private final Game game;
        private final Player player;

        private RegisteredFixture(final String description) {
            final RegisteredPlayer registered = new RegisteredPlayer(
                    new Deck(description)).setPlayer(
                    new TestLobbyPlayer("Player"));
            final List<RegisteredPlayer> players = List.of(registered);
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(players, rules,
                    new Match(rules, players, description));
            player = game.getPlayers().get(0);
            player.setTeam(1);
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, player);
            game.setAge(GameStage.Play);
        }

        private Card card(final String name) {
            final Card card = Card.fromPaperCard(paper(name), player);
            card.setOwner(player);
            card.setController(player, game.getNextTimestamp());
            return card;
        }

        private Card creature(final String name) {
            final Card card = card(name);
            card.getCurrentState().addType("Creature");
            card.getCurrentState().setBasePower(1);
            card.getCurrentState().setBaseToughness(1);
            return card;
        }
    }

    private static PaperCard paper(final String name) {
        return new PaperCard(CardRules.fromScript(List.of(
                "Name:" + name,
                "ManaCost:0",
                "Types:Artifact",
                "Oracle:")), "TST", CardRarity.Common);
    }

    private static final class TestLobbyPlayer extends LobbyPlayer
            implements IGameEntitiesFactory {
        private TestLobbyPlayer(final String name) {
            super(name);
        }

        @Override
        public Player createIngamePlayer(final Game game, final int id) {
            return new Player(getName(), game, id);
        }

        @Override
        public PlayerController createMindSlaveController(
                final Player master, final Player slave) {
            return null;
        }

        @Override
        public void hear(final LobbyPlayer player, final String message) {
        }
    }
}
