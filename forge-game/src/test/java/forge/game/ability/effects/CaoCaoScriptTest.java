package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
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
import java.util.List;

public class CaoCaoScriptTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void realScriptParsesDamageTaxAndOpponentChoiceChains()
            throws Exception {
        final Path script = Paths.get("..", "custom", "cards", "multicolor",
                "超世之杰曹操.txt").toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8),
                "超世之杰曹操");

        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(),
                        "Cao Cao script test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);

        final Card caoCao = CardFactory.getCard(
                new PaperCard(cardRules, "BT3K", CardRarity.MythicRare),
                controller, game);

        Assert.assertEquals(caoCao.getManaCost().getCMC(), 3);
        Assert.assertEquals(caoCao.getNetPower(), 3);
        Assert.assertEquals(caoCao.getNetToughness(), 4);

        Assert.assertEquals(caoCao.getReplacementEffects().size(), 1);
        final ReplacementEffect prevention = caoCao.getReplacementEffects().get(0);
        Assert.assertEquals(prevention.getParam("ValidSource"),
                "Card.OppCtrl,Emblem.OppCtrl");
        Assert.assertEquals(prevention.getParam("ValidTarget"), "You,Card.Self");
        Assert.assertTrue(prevention.hasParam("PreventionEffect"));
        final SpellAbility taxedPrevention = prevention.getOverridingAbility();
        Assert.assertEquals(taxedPrevention.getApi(), ApiType.ReplaceDamage);
        Assert.assertEquals(taxedPrevention.getParam("Amount"), "ShieldAmount");
        Assert.assertEquals(taxedPrevention.getParam("UnlessCost"), "1");
        Assert.assertEquals(taxedPrevention.getParam("UnlessPayer"),
                "ReplacedSourceController");

        Assert.assertEquals(caoCao.getTriggers().size(), 1);
        final SpellAbility choice = caoCao.getTriggers().get(0).ensureAbility();
        Assert.assertEquals(choice.getApi(), ApiType.GenericChoice);
        Assert.assertEquals(choice.getParam("Defined"),
                "TriggeredSourceController");
        final List<AbilitySub> modes = choice.getAdditionalAbilityList("Choices");
        Assert.assertEquals(modes.size(), 2);

        final SpellAbility givePermanent = modes.get(0);
        Assert.assertEquals(givePermanent.getApi(), ApiType.ChooseCard);
        Assert.assertEquals(givePermanent.getParam("Choices"),
                "Permanent.RememberedPlayerCtrl");
        Assert.assertEquals(givePermanent.getSubAbility().getApi(),
                ApiType.GainControl);
        Assert.assertEquals(givePermanent.getSubAbility().getParam("NewController"),
                "You");

        final SpellAbility exileHand = modes.get(1);
        Assert.assertEquals(exileHand.getApi(), ApiType.ChooseCard);
        Assert.assertEquals(exileHand.getParam("ChoiceZone"), "Hand");
        Assert.assertEquals(exileHand.getSubAbility().getApi(),
                ApiType.ChangeZone);
        Assert.assertEquals(exileHand.getSubAbility().getParam("Destination"),
                "Exile");
    }
}
