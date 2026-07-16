package forge.game.keyword;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementType;
import forge.game.trigger.TriggerType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

public class DurabilityTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void parsesDurabilityAmountAndCreatesItsRules() {
        final KeywordInterface keyword = Keyword.getInstance("Durability:2");
        Assert.assertEquals(keyword.getKeyword(), Keyword.DURABILITY);
        Assert.assertEquals(keyword.getAmount(), 2);

        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Durability test"));
        final Player controller = new Player("Controller", game, 1);
        game.getPlayers().add(controller);
        final CardRules cardRules = new CardRules.Reader().readCard(List.of(
                "Name:Durability Fixture",
                "ManaCost:1",
                "Types:Artifact",
                "K:Durability:2",
                "Oracle:Durability 2"
        ), "Durability Fixture");
        final Card card = CardFactory.getCard(
                new PaperCard(cardRules, "TST", CardRarity.Common), controller, game);

        Assert.assertEquals(CounterEnumType.getType("DURABILITY"), CounterEnumType.DURABILITY);
        Assert.assertEquals(CounterEnumType.getType("MITHRIL"), CounterEnumType.MITHRIL);
        Assert.assertTrue(card.getReplacementEffects().stream()
                .anyMatch(replacement -> replacement.getMode() == ReplacementType.Moved
                        && "DURABILITY".equals(replacement.getOverridingAbility()
                                .getParam("CounterType"))
                        && "2".equals(replacement.getOverridingAbility()
                                .getParam("CounterNum"))));
        Assert.assertTrue(card.getTriggers().stream()
                .anyMatch(trigger -> trigger.getMode() == TriggerType.CounterRemoved
                        && "DURABILITY".equals(trigger.getParam("CounterType"))
                        && "0".equals(trigger.getParam("NewCounterAmount"))));
    }
}
