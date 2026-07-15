package forge.game;

import forge.LobbyPlayer;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.cost.Cost;
import forge.game.cost.CostPayment;
import forge.game.phase.PhaseType;
import forge.game.player.IGameEntitiesFactory;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.AbilityActivated;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class GameActionUtilRollbackTest {
    @BeforeClass
    public void initializeLocalizer() {
        Lang.createInstance("en-US");
        final String languages = path("forge-gui", "res", "languages");
        Localizer.getInstance().initialize("en-US", languages);
    }

    private static String path(final String first, final String... more) {
        java.nio.file.Path result = Paths.get("..", first);
        for (final String element : more) {
            result = result.resolve(element);
        }
        return result.toAbsolutePath().normalize().toString();
    }

    @Test
    public void rollbackDoesNotRestoreSnapshotWhenBattlefieldChangedDuringActivation() {
        final RegisteredPlayer registeredPlayer = new RegisteredPlayer(new Deck("Rollback test"))
                .setPlayer(new TestLobbyPlayer("Player"));
        final List<RegisteredPlayer> registeredPlayers = List.of(registeredPlayer);
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(registeredPlayers, rules,
                new Match(rules, registeredPlayers, "Rollback test"));
        final Player player = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, player);

        final Card source = cardOnBattlefield(1, game, player);
        final AbilityActivated ability = new AbilityActivated(source, Cost.Zero,
                new TargetRestrictions(Map.of("ValidTgts", "Any"))) {
            private static final long serialVersionUID = 1L;

            @Override
            public void resolve() {
            }
        };
        ability.setActivatingPlayer(player);
        final CostPayment payment = new CostPayment(Cost.Zero, ability);

        game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;
        game.stashGameState();

        final Card enteredAfterSnapshot = cardOnBattlefield(2, game, player);
        ability.getTargets().add(enteredAfterSnapshot);
        game.getStack().freezeStack(ability);

        GameActionUtil.rollbackAbility(ability, null, -1, payment, source);

        Assert.assertTrue(player.getZone(ZoneType.Battlefield).contains(enteredAfterSnapshot),
                "cancelling an uncommitted ability must not roll the whole game back");
        Assert.assertFalse(game.getStack().isFrozen(),
                "manual cancellation must clear the stack freeze used while paying costs");
        Assert.assertTrue(ability.getTargets().isEmpty(),
                "manual cancellation must clear choices made by the cancelled ability");
    }

    private static Card cardOnBattlefield(final int id, final Game game, final Player player) {
        final String name = "Rollback Test Card " + id;
        final CardRules rules = new CardRules.Reader().readCard(List.of(
                "Name:" + name,
                "ManaCost:no cost",
                "Types:Basic Land Forest",
                "Oracle:({T}: Add {G}.)"
        ), name);
        final Card card = CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common), player, id, game);
        player.getZone(ZoneType.Battlefield).add(card);
        return card;
    }

    private static final class TestLobbyPlayer extends LobbyPlayer implements IGameEntitiesFactory {
        private TestLobbyPlayer(final String name) {
            super(name);
        }

        @Override
        public Player createIngamePlayer(final Game game, final int id) {
            return new Player(getName(), game, id);
        }

        @Override
        public PlayerController createMindSlaveController(final Player master, final Player slave) {
            return null;
        }

        @Override
        public void hear(final LobbyPlayer player, final String message) {
        }
    }
}
