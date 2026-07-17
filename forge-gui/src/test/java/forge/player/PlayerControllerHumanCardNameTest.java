package forge.player;

import forge.card.CardRules;
import forge.card.ICardFace;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.Match;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.card.CardFaceView;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.util.Lang;
import forge.util.Localizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerControllerHumanCardNameTest {
    @BeforeAll
    static void initializeLocalizer() {
        if (GuiBase.getInterface() == null) {
            GuiBase.setInterface((IGuiBase) Proxy.newProxyInstance(
                    IGuiBase.class.getClassLoader(), new Class<?>[] { IGuiBase.class },
                    (proxy, method, args) -> {
                        if ("getAssetsDir".equals(method.getName())) {
                            return Paths.get("").toAbsolutePath().normalize()
                                    + java.io.File.separator;
                        }
                        if (method.getName().startsWith("invokeInEdt")) {
                            ((Runnable) args[0]).run();
                            return null;
                        }
                        if ("isGuiThread".equals(method.getName())) {
                            return true;
                        }
                        return defaultValue(method.getReturnType());
                    }));
        }
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    void predicatePathShowsOnlyLegalCandidatesAndReturnsCanonicalName() {
        final ICardFace allowed = face("Allowed Canonical");
        final ICardFace hidden = face("Hidden Canonical");
        final AtomicReference<List<String>> shown = new AtomicReference<>();
        final PlayerControllerHuman controller = controller((method, choices) -> {
            shown.set(canonicalNames(choices));
            return choices.get(0);
        });

        final String chosen = controller.chooseCardNameFromCandidates(null,
                List.of(hidden, allowed).stream(), face -> face.getName().startsWith("Allowed"), "Card", "Choose");

        assertEquals("Allowed Canonical", chosen);
        assertEquals(List.of("Allowed Canonical"), shown.get());
    }

    @Test
    void listPathReturnsCanonicalNameAndCancellationReturnsEmpty() {
        final ICardFace canonical = face("English Canonical");
        final PlayerControllerHuman choosing = controller((method, choices) -> choices.get(0));
        final PlayerControllerHuman cancelling = controller((method, choices) -> null);

        assertEquals("English Canonical", choosing.chooseCardName(null, List.of(canonical), "Choose"));
        assertEquals("", cancelling.chooseCardName(null, List.of(canonical, face("Other")), "Choose"));
        assertEquals("", cancelling.chooseCardNameFromCandidates(null, List.of(canonical).stream(),
                face -> true, "Card", "Choose"));
    }

    @Test
    void ventureGenericCardFacePathRemainsMandatoryAndDoesNotUseOptionalCancellationPath() {
        final ICardFace first = face("Mandatory First");
        final ICardFace second = face("Mandatory Second");
        final AtomicReference<String> invoked = new AtomicReference<>();
        final PlayerControllerHuman controller = controller((method, choices) -> {
            invoked.set(method);
            return "one".equals(method) ? choices.get(0) : null;
        });

        final ICardFace chosen = controller.chooseSingleCardFace(null, List.of(first, second), "Choose");

        assertEquals("one", invoked.get());
        assertEquals("Mandatory First", chosen.getName());
    }

    private static PlayerControllerHuman controller(final Choice choice) {
        final GameRules rules = new GameRules(GameType.Constructed);
        final RegisteredPlayer registered = new RegisteredPlayer(new Deck("card-name chooser test"))
                .setPlayer(new LobbyPlayerHuman("Human"));
        final List<RegisteredPlayer> players = List.of(registered);
        final Game game = new Game(players, rules, new Match(rules, players, "card-name chooser test"));
        final Player player = game.getPlayers().get(0);
        final PlayerControllerHuman controller = new PlayerControllerHuman(
                game, player, player.getOriginalLobbyPlayer());
        player.dangerouslySetController(controller);
        controller.setGui((IGuiGame) Proxy.newProxyInstance(IGuiGame.class.getClassLoader(),
                new Class<?>[] { IGuiGame.class }, (proxy, method, args) -> {
                    if ("oneOrNone".equals(method.getName()) || "one".equals(method.getName())) {
                        @SuppressWarnings("unchecked") final List<CardFaceView> choices =
                                (List<CardFaceView>) args[1];
                        return choice.choose(method.getName(), choices);
                    }
                    return defaultValue(method.getReturnType());
                }));
        return controller;
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static ICardFace face(final String name) {
        return CardRules.fromScript(List.of(
                "Name:" + name,
                "ManaCost:1",
                "Types:Sorcery",
                "Oracle:Test."
        )).getMainPart();
    }

    private static List<String> canonicalNames(final List<CardFaceView> choices) {
        final List<String> names = new ArrayList<>();
        for (final CardFaceView choice : choices) {
            names.add(choice.getName());
        }
        return names;
    }

    @FunctionalInterface
    private interface Choice {
        CardFaceView choose(String method, List<CardFaceView> choices);
    }
}
