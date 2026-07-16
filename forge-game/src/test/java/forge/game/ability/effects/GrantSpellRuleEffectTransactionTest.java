package forge.game.ability.effects;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.List;

public class GrantSpellRuleEffectTransactionTest {
    private static final String RULE_KEY = "test:grant-rule:transaction";

    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void conflictingSecondPlayerRollsBackEveryPlayerAtomically() {
        final Fixture fixture = new Fixture("multi-player transaction failure");
        fixture.first.getSpellRuleRegistry().registerStacking(
                "first-existing", "Card.nonColorless", "Spell", 1, "");
        fixture.second.getSpellRuleRegistry().register(
                RULE_KEY, "Card.nonColorless", "Spell", 1, "");
        final RegistryState firstBefore = RegistryState.capture(fixture.first);
        final RegistryState secondBefore = RegistryState.capture(fixture.second);
        final SpellAbility grant = grant(fixture,
                "Defined$ AllPlayers | RuleKey$ " + RULE_KEY + " "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ReduceGeneric$ 2 "
                        + "| ManaConversion$ AnyType->AnyColor "
                        + "| Duration$ Permanent");

        final IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class, grant::resolve);

        Assert.assertTrue(error.getMessage().contains(
                "already has different data"));
        firstBefore.assertUnchanged(fixture.first);
        secondBefore.assertUnchanged(fixture.second);
    }

    @Test
    public void stackingKeyConflictOnSecondPlayerLeavesEveryRegistryUnchanged() {
        final Fixture fixture = new Fixture("multi-player stacking key conflict");
        fixture.second.getSpellRuleRegistry().register(
                RULE_KEY + "#1", "Card.nonColorless", "Spell", 1, "");
        final RegistryState firstBefore = RegistryState.capture(fixture.first);
        final RegistryState secondBefore = RegistryState.capture(fixture.second);
        final SpellAbility grant = grant(fixture,
                "Defined$ AllPlayers | RuleKey$ " + RULE_KEY + " "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ReduceGeneric$ 2 | Stacking$ True "
                        + "| Duration$ Permanent");

        final IllegalStateException error = Assert.expectThrows(
                IllegalStateException.class, grant::resolve);

        Assert.assertTrue(error.getMessage().contains(
                "stacking key already exists"));
        firstBefore.assertUnchanged(fixture.first);
        secondBefore.assertUnchanged(fixture.second);
    }

    @Test
    public void exhaustedSecondPlayerStackingSequenceLeavesEveryRegistryUnchanged() {
        final Fixture fixture = new Fixture("multi-player stacking exhaustion");
        fixture.first.getSpellRuleRegistry().register(
                "first-anchor", "Card.nonColorless", "Spell", 1, "");
        fixture.second.getSpellRuleRegistry().register(
                "second-anchor", "Card.nonColorless", "Spell", 1, "");
        setStackingSequence(fixture.second, Long.MAX_VALUE);
        final RegistryState firstBefore = RegistryState.capture(fixture.first);
        final RegistryState secondBefore = RegistryState.capture(fixture.second);
        final SpellAbility grant = grant(fixture,
                "Defined$ AllPlayers | RuleKey$ " + RULE_KEY + " "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ReduceGeneric$ 2 | Stacking$ True "
                        + "| Duration$ Permanent");

        final IllegalStateException error = Assert.expectThrows(
                IllegalStateException.class, grant::resolve);

        Assert.assertTrue(error.getMessage().contains("sequence exhausted"));
        firstBefore.assertUnchanged(fixture.first);
        secondBefore.assertUnchanged(fixture.second);
    }

    @Test
    public void successfulMultiPlayerGrantDeduplicatesRepeatedPlayers() {
        final Fixture fixture = new Fixture("multi-player duplicate success");
        final SpellAbility grant = grant(fixture,
                "Defined$ AllPlayers & AllPlayers | RuleKey$ " + RULE_KEY + " "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ReduceGeneric$ 2 | Stacking$ True "
                        + "| Duration$ Permanent");

        grant.resolve();

        Assert.assertEquals(fixture.first.getSpellRuleRegistry().size(), 1);
        Assert.assertEquals(fixture.second.getSpellRuleRegistry().size(), 1);
        Assert.assertEquals(stackingSequence(fixture.first), 1L);
        Assert.assertEquals(stackingSequence(fixture.second), 1L);
    }

    @Test
    public void missingDurationFailsBeforeMutatingTheRegistry() {
        final Fixture fixture = new Fixture("missing duration failure");
        fixture.first.getSpellRuleRegistry().registerStacking(
                "existing", "Card.nonColorless", "Spell", 1, "");
        final RegistryState before = RegistryState.capture(fixture.first);
        final SpellAbility grant = grant(fixture,
                "Defined$ You | RuleKey$ " + RULE_KEY + " "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ReduceGeneric$ 2");
        IllegalArgumentException error = null;

        try {
            grant.resolve();
        } catch (final IllegalArgumentException ex) {
            error = ex;
        }

        before.assertUnchanged(fixture.first);
        Assert.assertNotNull(error, "missing Duration must be rejected");
    }

    @Test
    public void nonPermanentDurationRemainsRejectedWithoutMutation() {
        final Fixture fixture = new Fixture("non-permanent duration failure");
        final RegistryState before = RegistryState.capture(fixture.first);
        final SpellAbility grant = grant(fixture,
                "Defined$ You | RuleKey$ " + RULE_KEY + " "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ReduceGeneric$ 2 | Duration$ UntilEndOfTurn");

        final IllegalArgumentException error = Assert.expectThrows(
                IllegalArgumentException.class, grant::resolve);

        Assert.assertTrue(error.getMessage().contains("only Permanent"));
        before.assertUnchanged(fixture.first);
    }

    private static SpellAbility grant(final Fixture fixture,
                                      final String parameters) {
        final Card source = new Card(fixture.game.nextCardId(), fixture.game);
        source.setName("Grant Spell Rule Transaction Source");
        source.setOwner(fixture.first);
        source.setController(fixture.first, fixture.game.getNextTimestamp());
        final SpellAbility grant = AbilityFactory.getAbility(
                "DB$ GrantSpellRule | " + parameters, source);
        grant.setActivatingPlayer(fixture.first);
        return grant;
    }

    private static long stackingSequence(final Player player) {
        final String state = player.getSpellRuleRegistry().toStateString();
        if (state.isEmpty()) {
            return 0L;
        }
        final int separator = state.indexOf(';');
        return Long.parseLong(separator < 0 ? state : state.substring(0, separator));
    }

    private static void setStackingSequence(final Player player,
                                            final long sequence) {
        final String state = player.getSpellRuleRegistry().toStateString();
        final int separator = state.indexOf(';');
        Assert.assertTrue(separator >= 0, "a rule is required to retain the sequence");
        player.getSpellRuleRegistry().restoreFromStateString(
                sequence + state.substring(separator));
    }

    private record RegistryState(String serialized, int size, long sequence) {
        private static RegistryState capture(final Player player) {
            return new RegistryState(
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

    private static final class Fixture {
        private final Game game;
        private final Player first;
        private final Player second;

        private Fixture(final String description) {
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(List.of(), rules,
                    new Match(rules, List.of(), description));
            first = new Player("First", game, 1);
            second = new Player("Second", game, 2);
            game.getPlayers().add(first);
            game.getPlayers().add(second);
            first.setTeam(1);
            second.setTeam(2);
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, first);
        }
    }
}
