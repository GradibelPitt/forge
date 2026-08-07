package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

public class DrawFromLibraryTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void playerDrawsFromTopOfAnotherPlayersLibrary() {
        final Fixture fixture = fixture();
        final Card source = card(fixture, "Source");
        final Card top = card(fixture, "Top card");
        final Card bottom = card(fixture, "Bottom card");

        fixture.controller.getZone(ZoneType.Battlefield).add(source);
        fixture.controller.getZone(ZoneType.Library).add(top);
        fixture.controller.getZone(ZoneType.Library).add(bottom);
        source.addRemembered(fixture.opponent);

        final SpellAbility draw = AbilityFactory.getAbility(
                "DB$ Draw | Defined$ Player.IsRemembered | NumCards$ 1"
                        + " | FromLibrary$ You",
                source);
        draw.setActivatingPlayer(fixture.controller);
        AbilityUtils.resolve(draw);

        final Card drawn = fixture.opponent.getCardsIn(ZoneType.Hand).get(0);
        Assert.assertEquals(drawn.getId(), top.getId());
        Assert.assertEquals(fixture.controller.getCardsIn(ZoneType.Library).get(0).getId(),
                bottom.getId());
        Assert.assertSame(drawn.getOwner(), fixture.controller,
                "drawing from another library must not change card ownership");
        Assert.assertEquals(fixture.opponent.getNumDrawnThisTurn(), 1,
                "the move must count as a draw by the receiving player");
        Assert.assertEquals(fixture.controller.getNumDrawnThisTurn(), 0);
    }

    private static Fixture fixture() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(),
                        "Draw from another player's library test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(opponent);
        game.setAge(GameStage.Play);
        return new Fixture(game, controller, opponent);
    }

    private static Card card(final Fixture fixture, final String name) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:0",
                "Types:Artifact",
                "Oracle:Test card."));
        return CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common),
                fixture.controller, fixture.game);
    }

    private record Fixture(Game game, Player controller, Player opponent) {
    }
}
