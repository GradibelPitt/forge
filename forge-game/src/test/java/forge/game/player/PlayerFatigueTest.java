package forge.game.player;

import java.util.Collections;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import forge.CardStorageReader;
import forge.StaticData;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.util.Lang;
import forge.util.Localizer;

public class PlayerFatigueTest {
    @BeforeClass
    public void initializeLocalizer() {
        Lang.createInstance("en-US");
        final Path languages = Path.of("..", "forge-gui", "res", "languages").toAbsolutePath();
        final Path editions = Path.of("..", "forge-gui", "res", "editions").toAbsolutePath();
        Localizer.getInstance().initialize("en-US", languages.toString());
        try {
            final Path testData = Files.createTempDirectory("player-fatigue-test");
            final Path cards = Files.createDirectories(testData.resolve("cards"));
            final Path blocks = Files.createDirectories(testData.resolve("blocks"));
            new StaticData(new CardStorageReader(cards.toString(), null, false), null, editions.toString(),
                    editions.toString(), blocks.toString(), "", true, true);
        } catch (final Exception exception) {
            throw new RuntimeException("Unable to initialize player fatigue test data", exception);
        }
    }

    @Test
    public void emptyLibraryDrawsTakeIncreasingFatiguePerAttempt() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Fatigue test"));
        final Player player = new Player("Player", game, 1);
        game.setAge(GameStage.Play);
        player.addChangedKeywords(Collections.singletonList(Player.FATIGUE_ON_EMPTY_DRAW_KEYWORD),
                Collections.emptyList(), 1L, 1L);

        player.drawCards(3);
        Assert.assertEquals(player.getFatigueCount(), 3);
        Assert.assertEquals(player.getLife(), 14);

        player.drawCards(1);
        Assert.assertEquals(player.getFatigueCount(), 4);
        Assert.assertEquals(player.getLife(), 10);
        Assert.assertFalse(player.checkLoseCondition(), "fatigue replaces the empty-library loss condition");
    }

    @Test
    public void fatigueCountsAreIndependentForEachPlayer() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Fatigue test"));
        final Player first = new Player("First", game, 1);
        final Player second = new Player("Second", game, 2);

        first.takeFatigue();
        first.takeFatigue();
        second.takeFatigue();

        Assert.assertEquals(first.getFatigueCount(), 2);
        Assert.assertEquals(first.getLife(), 17);
        Assert.assertEquals(second.getFatigueCount(), 1);
        Assert.assertEquals(second.getLife(), 19);
    }
}
