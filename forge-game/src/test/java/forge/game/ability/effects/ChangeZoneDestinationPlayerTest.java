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

public class ChangeZoneDestinationPlayerTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void destinationPlayerPutsOwnedCardsOnAnotherPlayersLibrary() {
        final Fixture fixture = fixture();
        final Card source = card(fixture, "Source");
        final Card firstMoved = card(fixture, "First moved spell");
        final Card secondMoved = card(fixture, "Second moved spell");
        final Card sentinel = card(fixture, "Existing top card");

        fixture.controller.getZone(ZoneType.Battlefield).add(source);
        fixture.controller.getZone(ZoneType.Exile).add(firstMoved);
        fixture.controller.getZone(ZoneType.Exile).add(secondMoved);
        fixture.target.getZone(ZoneType.Library).add(sentinel);
        source.addRemembered(firstMoved);
        source.addRemembered(secondMoved);
        source.addRemembered(fixture.target);

        final SpellAbility move = AbilityFactory.getAbility(
                "DB$ ChangeZone | Defined$ Remembered | Origin$ Exile"
                        + " | Destination$ Library"
                        + " | DestinationPlayer$ Player.IsRemembered"
                        + " | RandomOrder$ True"
                        + " | LibraryPosition$ 0",
                source);
        move.setActivatingPlayer(fixture.controller);
        AbilityUtils.resolve(move);

        Assert.assertEquals(fixture.target.getCardsIn(ZoneType.Library).size(), 3);
        Assert.assertEquals(fixture.target.getCardsIn(ZoneType.Library).get(2).getId(),
                sentinel.getId());
        Assert.assertTrue(fixture.target.getCardsIn(ZoneType.Library).stream()
                .limit(2).allMatch(c -> c.getId() == firstMoved.getId()
                        || c.getId() == secondMoved.getId()));
        Assert.assertTrue(fixture.target.getCardsIn(ZoneType.Library).stream()
                .limit(2).allMatch(c -> c.getOwner().equals(fixture.controller)),
                "moving to another player's library must not change ownership");
    }

    private static Fixture fixture() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(),
                        "ChangeZone destination player test"));
        final Player controller = new Player("Controller", game, 1);
        final Player target = new Player("Target", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(target);
        controller.setTeam(1);
        target.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(controller);
        game.setAge(GameStage.Play);
        return new Fixture(game, controller, target);
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

    private record Fixture(Game game, Player controller, Player target) {
    }
}
