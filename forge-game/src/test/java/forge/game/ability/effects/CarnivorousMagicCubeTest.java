package forge.game.ability.effects;

import forge.CardStorageReader;
import forge.ImageKeys;
import forge.StaticData;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

public class CarnivorousMagicCubeTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        ImageKeys.initializeDirs("", Collections.emptyMap(), "", "", "", "", "", "", "");
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
    public void realScriptConjuresTwoCopiesOfTheCreatureItDestroyed() throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Carnivorous Magic Cube test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(controller);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "black", "食肉魔块.txt")
                .toAbsolutePath().normalize();
        final CardRules cubeRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "食肉魔块");
        Card cube = CardFactory.getCard(
                new PaperCard(cubeRules, "PH01", CardRarity.Rare), controller, game);
        cube = game.getAction().moveTo(ZoneType.Battlefield, cube, null, null);

        final CardRules victimRules = CardRules.fromScript(Arrays.asList(
                "Name:Carnivorous Magic Cube Victim",
                "ManaCost:1 G",
                "Types:Creature Bear",
                "PT:2/2",
                "Oracle:Test card."));
        final PaperCard victimPaper = new PaperCard(
                victimRules, "PH01", CardRarity.Common);
        StaticData.instance().getCommonCards().addCard(victimPaper);
        Card victim = CardFactory.getCard(victimPaper, controller, game);
        victim = game.getAction().moveTo(ZoneType.Battlefield, victim, null, null);

        final SpellAbility destroy = AbilityFactory.getAbility(cube.getSVar("TrigDestroy"), cube);
        Assert.assertEquals(destroy.getParam("ValidTgts"), "Creature.YouCtrl");
        Assert.assertTrue(destroy.hasParam("RememberDestroyed"));

        final Card victimLki = victim;
        victim = game.getAction().moveTo(ZoneType.Graveyard, victim, null, null);
        cube.addRemembered(victimLki);
        Assert.assertEquals(game.getZoneOf(victim).getZoneType(), ZoneType.Graveyard);
        Assert.assertEquals(cube.getRememberedCount(), 1,
                "the death-path fixture should carry the destroyed creature's LKI");

        cube = game.getAction().moveTo(ZoneType.Graveyard, cube, null, null);
        final SpellAbility conjure = AbilityFactory.getAbility(cube.getSVar("TrigConjure"), cube);
        conjure.setActivatingPlayer(controller);
        AbilityUtils.resolve(conjure);

        final long conjured = controller.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> "Carnivorous Magic Cube Victim".equals(card.getName()))
                .count();
        Assert.assertEquals(conjured, 2L,
                "the death ability should conjure two copies onto the battlefield");
    }
}
