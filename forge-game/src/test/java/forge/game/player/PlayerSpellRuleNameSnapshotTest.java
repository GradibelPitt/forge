package forge.game.player;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.spellability.Spell;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class PlayerSpellRuleNameSnapshotTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void namedHarmonyRuleOnlyProjectsToTheCapturedNamesAndSurvivesStateRestore() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(List.of(), rules,
                new Match(rules, List.of(), "named harmony rule"));
        final Player player = new Player("Player", game, 1);
        game.getPlayers().add(player);

        player.getSpellRuleRegistry().registerAfterPreflight("named-harmony",
                "Card", "Spell", 0, "", true, 3,
                Set.of("Shared Name"));
        final Card matching = card(game, player, "Shared Name");
        final Card other = card(game, player, "Other Name");

        Assert.assertTrue(player.getSpellRuleRegistry().grantsHarmony(matching));
        Assert.assertFalse(player.getSpellRuleRegistry().grantsHarmony(other));

        final String snapshot = player.getSpellRuleRegistry().toStateString();
        player.getSpellRuleRegistry().clear();
        player.getSpellRuleRegistry().restoreFromStateString(snapshot);

        Assert.assertTrue(player.getSpellRuleRegistry().grantsHarmony(matching));
        Assert.assertFalse(player.getSpellRuleRegistry().grantsHarmony(other));
    }

    @Test
    public void opponentCardsSnapshotCapturesOpponentNamesForHarmony() {
        final Fixture fixture = new Fixture("opponent names snapshot");
        final Card opponentCard = card(fixture.game, fixture.second,
                "Shared Name");
        fixture.second.getZone(ZoneType.Library).add(opponentCard);
        final Card source = card(fixture.game, fixture.first, "Source");
        final SpellAbility grant = AbilityFactory.getAbility(
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ opponent-names "
                        + "| ValidCards$ Card | ValidSA$ Spell "
                        + "| NameSnapshot$ OpponentCards | Harmony$ True "
                        + "| HarmonyReduction$ 3 | Duration$ Permanent", source);
        grant.setActivatingPlayer(fixture.first);

        grant.resolve();

        Assert.assertTrue(fixture.first.getSpellRuleRegistry().grantsHarmony(
                card(fixture.game, fixture.first, "Shared Name")));
        Assert.assertFalse(fixture.first.getSpellRuleRegistry().grantsHarmony(
                card(fixture.game, fixture.first, "Other Name")));
    }

    @Test
    public void emptyOpponentCardsSnapshotDoesNotBecomeAnUnrestrictedRule() {
        final Fixture fixture = new Fixture("empty opponent names snapshot");
        final Card source = card(fixture.game, fixture.first, "Source");
        final SpellAbility grant = AbilityFactory.getAbility(
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ empty-opponent-names "
                        + "| ValidCards$ Card | ValidSA$ Spell "
                        + "| NameSnapshot$ OpponentCards | Harmony$ True "
                        + "| HarmonyReduction$ 3 | Duration$ Permanent", source);
        grant.setActivatingPlayer(fixture.first);

        grant.resolve();

        Assert.assertFalse(fixture.first.getSpellRuleRegistry().grantsHarmony(
                card(fixture.game, fixture.first, "Any Name")));
    }

    @Test
    public void stackingOpponentSnapshotsRemainScopedAndAccumulateForOverlappingNames() {
        final Fixture fixture = new Fixture("stacking opponent names snapshots");
        fixture.second.getZone(ZoneType.Library).add(
                card(fixture.game, fixture.second, "Shared Name"));
        final Card source = card(fixture.game, fixture.first, "Source");
        final SpellAbility grant = AbilityFactory.getAbility(
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ opponent-names "
                        + "| ValidCards$ Card | ValidSA$ Spell "
                        + "| NameSnapshot$ OpponentCards | Harmony$ True "
                        + "| HarmonyReduction$ 3 | Stacking$ True "
                        + "| Duration$ Permanent", source);
        grant.setActivatingPlayer(fixture.first);

        grant.resolve();
        fixture.second.getZone(ZoneType.Library).add(
                card(fixture.game, fixture.second, "Second Snapshot Name"));
        grant.resolve();

        final SpellAbility shared = spell(fixture, "Shared Name");
        final SpellAbility secondOnly = spell(fixture, "Second Snapshot Name");
        final SpellAbility unrelated = spell(fixture, "Unrelated Name");
        Assert.assertEquals(fixture.first.getSpellRuleRegistry().size(), 2);
        Assert.assertEquals(fixture.first.getSpellRuleRegistry()
                .getHarmonyReduction(shared.getHostCard(), shared), 6);
        Assert.assertEquals(fixture.first.getSpellRuleRegistry()
                .getHarmonyReduction(secondOnly.getHostCard(), secondOnly), 3);
        Assert.assertEquals(fixture.first.getSpellRuleRegistry()
                .getHarmonyReduction(unrelated.getHostCard(), unrelated), 0);
        Assert.assertFalse(fixture.first.getSpellRuleRegistry()
                .grantsHarmony(unrelated.getHostCard()));
    }

    @Test
    public void conflictingPreflightIsSkippedWithoutEscapingAbilityResolution() {
        final Fixture fixture = new Fixture("recoverable opponent names conflict");
        fixture.second.getZone(ZoneType.Library).add(
                card(fixture.game, fixture.second, "First Snapshot Name"));
        final Card source = card(fixture.game, fixture.first, "Source");
        final SpellAbility grant = AbilityFactory.getAbility(
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ opponent-names "
                        + "| ValidCards$ Card | ValidSA$ Spell "
                        + "| NameSnapshot$ OpponentCards | Harmony$ True "
                        + "| HarmonyReduction$ 3 | Duration$ Permanent", source);
        grant.setActivatingPlayer(fixture.first);
        grant.resolve();
        final String beforeConflict = fixture.first.getSpellRuleRegistry()
                .toStateString();
        fixture.second.getZone(ZoneType.Library).add(
                card(fixture.game, fixture.second, "Changed Snapshot Name"));

        AbilityUtils.resolve(grant);

        Assert.assertEquals(fixture.first.getSpellRuleRegistry()
                .toStateString(), beforeConflict);
        Assert.assertTrue(fixture.game.getGameLog().getAllEntries().stream()
                .anyMatch(entry -> entry.message().contains("recoverable error")));
    }

    private static Card card(final Game game, final Player owner,
            final String name) {
        final Card card = new Card(game.nextCardId(), game);
        card.setName(name);
        card.setOwner(owner);
        return card;
    }

    private static SpellAbility spell(final Fixture fixture, final String name) {
        final Card card = card(fixture.game, fixture.first, name);
        card.setZone(fixture.first.getZone(ZoneType.Hand));
        final Spell spell = new Spell(card, Cost.Zero) {
            private static final long serialVersionUID = 1L;

            @Override
            public void resolve() {
            }
        };
        spell.setActivatingPlayer(fixture.first);
        return spell;
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
        }
    }
}
