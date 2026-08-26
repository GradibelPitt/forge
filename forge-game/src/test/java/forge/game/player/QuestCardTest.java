package forge.game.player;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class QuestCardTest {
    private static PaperCard paper(final String name, final String types, final String... keywords) {
        final java.util.ArrayList<String> script = new java.util.ArrayList<>(List.of(
                "Name:" + name,
                "ManaCost:R",
                "Types:" + types,
                "Oracle:Test card."
        ));
        for (final String keyword : keywords) {
            script.add("K:" + keyword);
        }
        return new PaperCard(CardRules.fromScript(script), "TST", CardRarity.Common);
    }

    @Test
    public void questRestrictionRequiresEveryListedCardCategoryInTheStartingDeck() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(List.of(), rules,
                new Match(rules, List.of(), "Quest restriction test"));
        final Player player = new Player("Quest player", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);

        final Card quest = Card.fromPaperCard(paper("Restricted Quest", "Enchantment Quest",
                "Quest:Pirate;Equipment;Card.Historic:Contains all three categories."), player);
        final Card pirate = Card.fromPaperCard(paper("Test Pirate", "Creature Pirate"), player);
        final Card equipment = Card.fromPaperCard(paper("Test Equipment", "Artifact Equipment"), player);
        player.getZone(ZoneType.Library).add(pirate);

        Assert.assertFalse(player.isQuestEligible(quest));

        player.getZone(ZoneType.Library).add(equipment);
        Assert.assertTrue(player.isQuestEligible(quest),
                "the Equipment is also historic, so it may satisfy both listed categories");
    }

    @Test
    public void movingAQuestToCommandActivatesItsDefaultStaticAndActivatedAbilitiesThere() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(List.of(), rules,
                new Match(rules, List.of(), "Quest command-zone test"));
        final Player player = new Player("Quest player", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);
        game.getPhaseHandler().setPlayerTurn(player);
        game.setAge(GameStage.Play);

        final PaperCard paper = new PaperCard(CardRules.fromScript(List.of(
                "Name:Test Quest",
                "ManaCost:R",
                "Types:Enchantment Quest",
                "S:Mode$ Continuous | Affected$ Player.You | SetMaxHandSize$ 9 | Description$ Test static ability.",
                "A:AB$ Draw | Cost$ 0 | NumCards$ 1 | SpellDescription$ Draw a card.",
                "Oracle:Test Quest."
        )), "TST", CardRarity.Common);
        final Card quest = Card.fromPaperCard(paper, player);
        player.getZone(ZoneType.Sideboard).add(quest);

        final Card moved = player.moveQuestToCommand(game, quest);

        Assert.assertNotNull(moved);
        Assert.assertTrue(player.getCardsIn(ZoneType.Command).contains(moved));
        Assert.assertEquals(game.getZoneOf(moved).getZoneType(), ZoneType.Command);

        final SpellAbility spell = moved.getSpellAbilities().stream()
                .filter(SpellAbility::isSpell)
                .findFirst()
                .orElseThrow();
        final SpellAbility activated = moved.getSpellAbilities().stream()
                .filter(sa -> !sa.isSpell())
                .findFirst()
                .orElseThrow();
        final StaticAbility staticAbility = moved.getStaticAbilities().getFirst();

        Assert.assertEquals(spell.getRestrictions().getZone(), ZoneType.Hand,
                "starting a Quest in command must not make its spell castable there");
        Assert.assertEquals(activated.getRestrictions().getZone(), ZoneType.Command);
        Assert.assertTrue(staticAbility.getActiveZone().contains(ZoneType.Command));
    }
}
