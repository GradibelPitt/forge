package forge.game.cost;

import forge.CardStorageReader;
import forge.StaticData;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.staticability.StaticAbility;
import forge.game.zone.ZoneType;
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

public class MountainGiantCostTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
        if (StaticData.instance() == null) {
            final String cards = Paths.get("..", "forge-gui", "res", "cardsfolder")
                    .toAbsolutePath().normalize().toString();
            final String editions = Paths.get("..", "forge-gui", "res", "editions")
                    .toAbsolutePath().normalize().toString();
            final String customEditions = Paths.get("..", "custom", "editions")
                    .toAbsolutePath().normalize().toString();
            final String blockData = Paths.get("..", "forge-gui", "res", "blockdata")
                    .toAbsolutePath().normalize().toString();
            new StaticData(new CardStorageReader(cards, null, true), null, editions,
                    customEditions, blockData, "Latest", true, true);
        }
    }

    @Test
    public void realScriptCountsEveryOtherHandCardFromAnyCastZone() throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Mountain Giant cost test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(controller);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "colorless", "山岭巨人.txt")
                .toAbsolutePath().normalize();
        final CardRules giantRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "山岭巨人");
        Card giant = CardFactory.getCard(
                new PaperCard(giantRules, "PH01", CardRarity.Rare), controller, game);
        giant = game.getAction().moveTo(ZoneType.Hand, giant, null, null);

        for (int i = 1; i <= 4; i++) {
            final Card card = vanillaCard(game, controller, "Other hand card " + i);
            game.getAction().moveTo(ZoneType.Hand, card, null, null);
        }

        Assert.assertEquals(reductionAmount(giant), 4,
                "the source in hand must exclude only itself and count four other cards");

        giant = game.getAction().moveTo(ZoneType.Graveyard, giant, null, null);
        Assert.assertEquals(reductionAmount(giant), 4,
                "casting from another zone must count all four cards still in hand");
    }

    private static int reductionAmount(final Card card) {
        final StaticAbility reducer = card.getStaticAbilities().getFirst();
        Assert.assertEquals(reducer.getParam("Amount"), "X");
        return AbilityUtils.calculateAmount(
                card, reducer.getParam("Amount"), reducer);
    }

    private static Card vanillaCard(final Game game, final Player owner,
                                    final String name) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:1",
                "Types:Artifact",
                "Oracle:Test card."));
        return CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common), owner, game);
    }
}
