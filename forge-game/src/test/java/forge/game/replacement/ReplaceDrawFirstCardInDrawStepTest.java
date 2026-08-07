package forge.game.replacement;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class ReplaceDrawFirstCardInDrawStepTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void appliesOnlyBeforeTheTurnPlayersFirstDrawOfTheDrawStep() {
        final Fixture fixture = fixture();
        final Card host = card(fixture, "Host");
        final ReplaceDraw replacement = (ReplaceDraw) ReplacementHandler.parseReplacement(
                "Event$ Draw | ValidPlayer$ Opponent"
                        + " | FirstCardInDrawStep$ True",
                host, true);
        final Map<AbilityKey, Object> runParams =
                AbilityKey.mapFromAffected(fixture.opponent);
        runParams.put(AbilityKey.ExtraDraws, 0);

        Assert.assertTrue(replacement.canReplace(runParams));

        fixture.opponent.getZone(ZoneType.Library).add(card(fixture, "Own draw"));
        fixture.opponent.drawCards(1);

        Assert.assertEquals(fixture.opponent.numDrawnThisDrawStep(), 1);
        Assert.assertFalse(replacement.canReplace(runParams),
                "later draws in the same draw step must not be replaceable");
    }

    private static Fixture fixture() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(),
                        "First draw in draw step replacement test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().devModeSet(PhaseType.DRAW, opponent);
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
