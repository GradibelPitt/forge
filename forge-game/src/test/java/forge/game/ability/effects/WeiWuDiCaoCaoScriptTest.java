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
import forge.game.replacement.ReplacementEffect;
import forge.game.staticability.StaticAbility;
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
import java.util.HashMap;
import java.util.List;

public class WeiWuDiCaoCaoScriptTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void realScriptParsesWardResolutionReplacementAndFreeCastPermission()
            throws Exception {
        final Path script = Paths.get("..", "custom", "cards", "multicolor",
                "魏武帝曹操.txt").toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8),
                "魏武帝曹操");

        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(),
                        "Wei Wudi Cao Cao script test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);

        final Card caoCao = CardFactory.getCard(
                new PaperCard(cardRules, "BT3K", CardRarity.MythicRare),
                controller, game);

        Assert.assertEquals(caoCao.getManaCost().getCMC(), 2);
        Assert.assertEquals(caoCao.getNetPower(), 2);
        Assert.assertEquals(caoCao.getNetToughness(), 3);
        Assert.assertTrue(caoCao.hasKeyword("Ward:Discard<2/Card>"));

        Assert.assertEquals(caoCao.getReplacementEffects().size(), 1);
        final ReplacementEffect replacement = caoCao.getReplacementEffects().get(0);
        Assert.assertEquals(replacement.getParam("Origin"), "Stack");
        Assert.assertEquals(replacement.getParam("Destination"), "Graveyard");
        Assert.assertEquals(replacement.getParam("Fizzle"), "False");
        Assert.assertEquals(replacement.getParam("ValidCard"),
                "Instant.YouDontCtrl+dealtDamageToYouThisTurn,"
                        + "Sorcery.YouDontCtrl+dealtDamageToYouThisTurn");
        Assert.assertEquals(replacement.getOverridingAbility().getParam("Defined"),
                "ReplacedCard");
        Assert.assertEquals(replacement.getOverridingAbility().getParam("Destination"),
                "Exile");
        Assert.assertTrue(replacement.getOverridingAbility()
                .hasParam("ExiledWithEffectSource"));

        final StaticAbility permission = caoCao.getStaticAbilities().stream()
                .filter(staticAbility -> staticAbility.hasParam("MayPlayWithoutManaCost"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing free-cast permission"));
        Assert.assertEquals(permission.getParam("Affected"),
                "Card.ExiledWithSource");
        Assert.assertEquals(permission.getParam("AffectedZone"), "Exile");
        Assert.assertTrue(permission.hasParam("MayPlay"));
    }

    @Test
    public void damagingOpponentSpellMatchesButFriendlyOrUndamagingSpellDoesNot()
            throws Exception {
        final Path script = Paths.get("..", "custom", "cards", "multicolor",
                "魏武帝曹操.txt").toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8),
                "魏武帝曹操");
        final CardRules burnRules = new CardRules.Reader().readCard(List.of(
                "Name:Test Burn",
                "ManaCost:R",
                "Types:Instant",
                "A:SP$ DealDamage | ValidTgts$ Any | NumDmg$ 1 | SpellDescription$ Test Burn deals 1 damage to any target.",
                "Oracle:Test Burn deals 1 damage to any target."
        ), "Test Burn");

        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(),
                        "Wei Wudi Cao Cao validity test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);

        final Card caoCao = CardFactory.getCard(
                new PaperCard(cardRules, "BT3K", CardRarity.MythicRare),
                controller, game);
        final ReplacementEffect replacement = caoCao.getReplacementEffects().get(0);
        final String validity = "Instant.YouDontCtrl+dealtDamageToYouThisTurn";

        final Card opponentBurn = CardFactory.getCard(
                new PaperCard(burnRules, "TST", CardRarity.Common),
                opponent, game);
        Assert.assertFalse(opponentBurn.isValid(validity, controller, caoCao,
                replacement));
        opponentBurn.getDamageHistory().registerDamage(1, false, opponentBurn,
                controller, new HashMap<>());
        Assert.assertTrue(opponentBurn.isValid(validity, controller, caoCao,
                replacement));

        final Card friendlyBurn = CardFactory.getCard(
                new PaperCard(burnRules, "TST", CardRarity.Common),
                controller, game);
        friendlyBurn.getDamageHistory().registerDamage(1, false, friendlyBurn,
                controller, new HashMap<>());
        Assert.assertFalse(friendlyBurn.isValid(validity, controller, caoCao,
                replacement));
    }
}
