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
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityManaConvert;
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
import java.util.Arrays;
import java.util.Collections;

public class TonyKingOfPiracyScriptTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void realScriptParsesManaConversionDrawReplacementsAndEtbCondition()
            throws Exception {
        final Path script = Paths.get("..", "custom", "cards", "blue",
                "盗版之王托尼.txt").toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8),
                "盗版之王托尼");

        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(),
                        "Tony, King of Piracy script test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);

        final Card tony = CardFactory.getCard(
                new PaperCard(cardRules, "PH01", CardRarity.MythicRare),
                controller, game);

        Assert.assertEquals(tony.getStaticAbilities().size(), 1);
        Assert.assertFalse(tony.getStaticAbilities().get(0)
                .hasParam("ValidPlayer"));
        Assert.assertEquals(tony.getStaticAbilities().get(0)
                .getParam("ValidSA"), "Spell");
        Assert.assertEquals(tony.getStaticAbilities().get(0)
                .getParam("ManaConversion"), "AnyType->AnyColor");

        final Card testSpell = CardFactory.getCard(
                new PaperCard(CardRules.fromScript(Arrays.asList(
                        "Name:Test Spell",
                        "ManaCost:U",
                        "Types:Sorcery",
                        "A:SP$ Draw | Defined$ You | NumCards$ 1"
                                + " | SpellDescription$ Draw a card.",
                        "Oracle:Draw a card.")),
                        "TST", CardRarity.Common),
                opponent, game);
        final SpellAbility cast = testSpell.getSpellAbilities().get(0);
        cast.setActivatingPlayer(opponent);
        Assert.assertTrue(StaticAbilityManaConvert.checkManaConvert(
                tony.getStaticAbilities().get(0), controller, testSpell, cast));
        Assert.assertTrue(StaticAbilityManaConvert.checkManaConvert(
                tony.getStaticAbilities().get(0), opponent, testSpell, cast));

        Assert.assertEquals(tony.getReplacementEffects().size(), 2);
        final ReplacementEffect yourDraw = tony.getReplacementEffects().stream()
                .filter(replacement -> "You".equals(
                        replacement.getParam("ValidPlayer")))
                .findFirst().orElseThrow();
        Assert.assertEquals(yourDraw.getOverridingAbility().getApi(),
                ApiType.ChoosePlayer);
        Assert.assertEquals(yourDraw.getOverridingAbility()
                .getParam("Choices"), "Player.OpponentOf ReplacedPlayer");
        Assert.assertEquals(yourDraw.getOverridingAbility().getSubAbility()
                .getParam("FromLibrary"), "Player.Chosen");

        final ReplacementEffect opponentDraw = tony.getReplacementEffects().stream()
                .filter(replacement -> "Opponent".equals(
                        replacement.getParam("ValidPlayer")))
                .findFirst().orElseThrow();
        Assert.assertEquals(opponentDraw.getOverridingAbility().getApi(),
                ApiType.Draw);
        Assert.assertEquals(opponentDraw.getOverridingAbility()
                .getParam("Defined"), "ReplacedPlayer");
        Assert.assertEquals(opponentDraw.getOverridingAbility()
                .getParam("FromLibrary"), "You");

        Assert.assertEquals(tony.getTriggers().size(), 1);
        Assert.assertEquals(tony.getTriggers().get(0)
                .getParam("IsPresent"), "Land.untapped+YouCtrl");
        Assert.assertEquals(tony.getTriggers().get(0)
                .getParam("PresentCompare"), "EQ0");
    }
}
