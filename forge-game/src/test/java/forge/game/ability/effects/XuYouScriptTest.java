package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class XuYouScriptTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void realScriptParsesAllAbilitiesAndReplacementChains() throws Exception {
        final Path script = Paths.get("..", "custom", "cards", "blue", "许攸.txt")
                .toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "许攸");

        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Xu You script test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);

        final Card xuYou = CardFactory.getCard(
                new PaperCard(cardRules, "BT3K", CardRarity.MythicRare),
                controller, game);

        Assert.assertEquals(xuYou.getStaticAbilities().size(), 1);
        Assert.assertEquals(xuYou.getReplacementEffects().size(), 1);
        Assert.assertTrue(xuYou.getReplacementEffects().get(0)
                .hasParam("FirstCardInDrawStep"));
        Assert.assertEquals(xuYou.getReplacementEffects().get(0)
                .getOverridingAbility().getParam("FromLibrary"), "You");

        Assert.assertEquals(xuYou.getTriggers().size(), 2);
        Assert.assertTrue(xuYou.getTriggers().stream().allMatch(trigger ->
                trigger.hasParam("ActivatorThisTurnCastSharedCardType")));
        Assert.assertEquals(xuYou.getSpellAbilities().stream()
                .filter(ability -> ability.isActivatedAbility()).count(), 2L);
    }
}
