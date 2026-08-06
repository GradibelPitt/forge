package forge.game.mulligan;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamesimulationtests.util.LobbyPlayerForTests;
import forge.gamesimulationtests.util.PlayerControllerForTests;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LondonMulliganTest {
    @BeforeClass
    public void initializeLocalizer() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void redrawsSevenUntilKeepThenBottomsCardsInSelectionOrder() {
        final Fixture fixture = new Fixture();
        final Game game = fixture.game;
        final Player player = fixture.player;
        final TrackingController controller = fixture.controller;
        player.setMaxHandSize(10);

        for (int i = 0; i < 21; i++) {
            player.getZone(ZoneType.Library).add(card("Card " + i, player, game));
        }
        player.drawCards(7);

        final LondonMulligan mulligan = new LondonMulligan(player, false);
        mulligan.mulligan();

        Assert.assertEquals(player.getZone(ZoneType.Hand).size(), 7,
                "the first London mulligan must redraw a full hand");
        Assert.assertEquals(controller.getTuckCalls(), 0,
                "cards must not be put on the bottom while the player is still mulliganing");

        mulligan.mulligan();

        Assert.assertEquals(player.getZone(ZoneType.Hand).size(), 7,
                "every London mulligan must redraw a full hand");
        Assert.assertEquals(controller.getTuckCalls(), 0,
                "bottoming cards must be deferred until the hand is kept");

        mulligan.keep();

        Assert.assertTrue(mulligan.hasKept());
        Assert.assertEquals(controller.getTuckCalls(), 1);
        Assert.assertEquals(controller.getLastCardsToReturn(), 2);
        Assert.assertEquals(player.getZone(ZoneType.Hand).size(), 5);

        final CardCollectionView library = player.getCardsIn(ZoneType.Library);
        final List<String> selectedOrder = controller.getSelectedOrder();
        Assert.assertEquals(library.get(library.size() - 2).getName(), selectedOrder.get(0));
        Assert.assertEquals(library.get(library.size() - 1).getName(), selectedOrder.get(1));
    }

    @Test
    public void keepingTheOpeningHandDoesNotOpenABottomingPrompt() {
        final Fixture fixture = new Fixture();
        final LondonMulligan mulligan = new LondonMulligan(fixture.player, false);

        mulligan.keep();

        Assert.assertTrue(mulligan.hasKept());
        Assert.assertEquals(fixture.controller.getTuckCalls(), 0);
    }

    @Test
    public void cannotMulliganPastTheStartingHandSize() {
        final Fixture fixture = new Fixture();
        fixture.player.setMaxHandSize(10);
        final LondonMulligan mulligan = new LondonMulligan(fixture.player, false);
        mulligan.timesMulliganed = fixture.player.getStartingHandSize();

        Assert.assertFalse(mulligan.canMulligan(),
                "another mulligan would require bottoming more cards than were drawn");
    }

    private static Card card(final String name, final Player player, final Game game) {
        final PaperCard paperCard = new PaperCard(CardRules.fromScript(List.of(
                "Name:" + name,
                "ManaCost:1",
                "Types:Artifact",
                "Oracle:Test card.")), "TST", CardRarity.Common);
        return CardFactory.getCard(paperCard, player, game);
    }

    private static final class Fixture {
        private final Game game;
        private final Player player;
        private final TrackingController controller;

        private Fixture() {
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(Collections.emptyList(), rules,
                    new Match(rules, Collections.emptyList(), "London mulligan test"));
            player = new Player("Player", game, 1);
            final LobbyPlayerForTests lobby = new LobbyPlayerForTests("Player", null);
            controller = new TrackingController(game, player, lobby);
            player.setFirstController(controller);
            player.setTeam(1);
            game.getPlayers().add(player);
        }
    }

    private static final class TrackingController extends PlayerControllerForTests {
        private int tuckCalls;
        private int lastCardsToReturn;
        private List<String> selectedOrder = Collections.emptyList();

        private TrackingController(final Game game, final Player player,
                final LobbyPlayerForTests lobbyPlayer) {
            super(game, player, lobbyPlayer);
        }

        @Override
        public CardCollectionView tuckCardsViaMulligan(final CardCollectionView hand,
                final int cardsToReturn) {
            tuckCalls++;
            lastCardsToReturn = cardsToReturn;

            final CardCollection selected = new CardCollection();
            if (cardsToReturn > 0) {
                selected.add(hand.get(2));
            }
            if (cardsToReturn > 1) {
                selected.add(hand.get(0));
            }
            for (final Card card : hand) {
                if (selected.size() == cardsToReturn) {
                    break;
                }
                if (!selected.contains(card)) {
                    selected.add(card);
                }
            }

            selectedOrder = new ArrayList<>();
            for (final Card card : selected) {
                selectedOrder.add(card.getName());
            }
            return selected;
        }

        private int getTuckCalls() {
            return tuckCalls;
        }

        private int getLastCardsToReturn() {
            return lastCardsToReturn;
        }

        private List<String> getSelectedOrder() {
            return selectedOrder;
        }
    }
}
