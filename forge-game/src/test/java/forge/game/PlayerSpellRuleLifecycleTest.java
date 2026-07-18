package forge.game;

import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.game.phase.PhaseType;
import forge.game.player.IGameEntitiesFactory;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.player.RegisteredPlayer;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.List;

public class PlayerSpellRuleLifecycleTest {
    private static final String RULE_KEY = "lifecycle-rule";

    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void gameSnapshotAndDeveloperGameStateDumpPreserveRules() {
        final Fixture fixture = new Fixture("snapshot and game state test");
        register(fixture.player);

        final Game copiedGame = new GameSnapshot(fixture.game).makeCopy();
        Assert.assertEquals(copiedGame.getPlayers().get(0)
                .getSpellRuleRegistry().size(), 1);

        final GameState dumped = new GameState();
        dumped.initFromGame(fixture.game);
        final String text = dumped.toString();
        Assert.assertTrue(text.contains("p0spellrules="));

    }

    @Test
    public void subgameStartsEmptyWithoutChangingMainGameRules() {
        final Fixture fixture = new Fixture("subgame lifecycle test");
        register(fixture.player);

        final Game subgame = new Game(fixture.registeredPlayers,
                fixture.game.getRules(), fixture.game.getMatch(), fixture.game, 20);

        Assert.assertTrue(subgame.getPlayers().get(0)
                .getSpellRuleRegistry().isEmpty());
        Assert.assertEquals(fixture.player.getSpellRuleRegistry().size(), 1);
    }

    private static void register(final Player player) {
        player.getSpellRuleRegistry().register(RULE_KEY,
                "Card.nonColorless", "Spell", 2, "AnyType->AnyColor");
    }

    private static final class Fixture {
        private final List<RegisteredPlayer> registeredPlayers;
        private final Game game;
        private final Player player;

        private Fixture(final String description) {
            final RegisteredPlayer registeredPlayer = new RegisteredPlayer(
                    new Deck(description)).setPlayer(new TestLobbyPlayer("Player"));
            registeredPlayers = List.of(registeredPlayer);
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(registeredPlayers, rules,
                    new Match(rules, registeredPlayers, description));
            player = game.getPlayers().get(0);
            player.setTeam(1);
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, player);
        }

    }

    private static final class TestLobbyPlayer extends LobbyPlayer
            implements IGameEntitiesFactory {
        private TestLobbyPlayer(final String name) {
            super(name);
        }

        @Override
        public Player createIngamePlayer(final Game game, final int id) {
            return new Player(getName(), game, id);
        }

        @Override
        public PlayerController createMindSlaveController(
                final Player master, final Player slave) {
            return null;
        }

        @Override
        public void hear(final LobbyPlayer player, final String message) {
        }
    }
}
