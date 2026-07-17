package forge.player;

import forge.CardStorageReader;
import forge.StaticData;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.util.Lang;
import forge.util.Localizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayableManaAbilityUtilTest {
    private static final int STRESS_CARD_COUNT = 20_000;
    private static IGuiBase previousGuiBase;
    private static boolean installedGuiBase;

    @BeforeAll
    static void initializeStaticData() {
        previousGuiBase = GuiBase.getInterface();
        if (previousGuiBase == null) {
            installedGuiBase = true;
            GuiBase.setInterface((IGuiBase) Proxy.newProxyInstance(
                    IGuiBase.class.getClassLoader(),
                    new Class<?>[] { IGuiBase.class },
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("getAssetsDir")) {
                            return Paths.get("").toAbsolutePath().normalize()
                                    + java.io.File.separator;
                        }
                        if (method.getName().startsWith("invokeInEdt")) {
                            ((Runnable) arguments[0]).run();
                            return null;
                        }
                        if (method.getName().equals("isGuiThread")) {
                            return true;
                        }
                        return defaultValue(method.getReturnType());
                    }));
        }
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US",
                workspacePath("forge-gui", "res", "languages"));
        synchronized (StaticData.class) {
            if (StaticData.instance() != null) {
                return;
            }
            new StaticData(new CardStorageReader(
                    workspacePath("forge-gui", "res", "cardsfolder"),
                    null, true), null,
                    workspacePath("forge-gui", "res", "editions"),
                    workspacePath("custom", "editions"),
                    workspacePath("forge-gui", "res", "blockdata"),
                    "Latest", true, true);
        }
    }

    @AfterAll
    static void restoreGuiBase() {
        if (installedGuiBase) {
            GuiBase.setInterface(previousGuiBase);
        }
    }

    private static String workspacePath(final String first,
            final String... more) {
        Path path = Paths.get(first, more).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            path = Paths.get("..").resolve(Paths.get(first, more))
                    .toAbsolutePath().normalize();
        }
        return path.toString();
    }

    @Test
    void paymentHighlightNeverEnumeratesAllAbilitiesForTwentyThousandTest3Cards() {
        final Fixture fixture = fixture();
        final AtomicInteger broadAbilityQueries = new AtomicInteger();
        final AtomicInteger weakSelectionWrites = new AtomicInteger();
        fixture.controller.setGui(gui(weakSelectionWrites));
        fixture.controller.getYieldController().setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, "true");
        fixture.controller.getYieldController().setPref(FPref.UI_SHOW_AUTOTAP_PREVIEW, "false");

        for (int i = 0; i < STRESS_CARD_COUNT; i++) {
            final Card test3 = new CountingNonManaCard(i + 1, fixture.game, broadAbilityQueries);
            test3.setOwner(fixture.player);
            test3.setController(fixture.player, 0L);
            test3.addStaticAbility("Mode$ IgnoreLegendRule | EffectZone$ All");
            fixture.player.getZone(ZoneType.Battlefield).add(test3);
        }
        broadAbilityQueries.set(0);

        final long started = System.nanoTime();
        fixture.controller.pushActionableCards(true);
        final long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS
                .toMillis(System.nanoTime() - started);
        System.out.println("PAYMENT_TEST3_20K elapsedMs=" + elapsedMillis
                + " cardsVisited=" + STRESS_CARD_COUNT
                + " broadAbilityQueries=" + broadAbilityQueries.get());

        assertEquals(0, broadAbilityQueries.get(),
                "payment highlighting must never call Card#getAllPossibleAbilities");
        assertTrue(elapsedMillis < 5_000,
                "20k non-mana test3 cards must stay within the linear scan gate");
        assertEquals(1, weakSelectionWrites.get());
        assertFalse(PlayableManaAbilityUtil.hasPlayableManaAbility(null, fixture.player));
        assertFalse(PlayableManaAbilityUtil.hasPlayableManaAbility(
                fixture.player.getZone(ZoneType.Battlefield).get(0), null));
    }

    @Test
    void realManaAbilityIsHighlightedAndTappedAbilityIsFiltered() {
        final Fixture fixture = fixture();
        final Card playable = manaCard(1, fixture);
        final Card tapped = manaCard(2, fixture);
        tapped.setTapped(true);

        final List<SpellAbility> playableAbilities =
                PlayableManaAbilityUtil.getPlayableManaAbilities(playable, fixture.player);
        assertEquals(1, playableAbilities.size());
        assertTrue(playableAbilities.get(0).isManaAbility());
        assertTrue(PlayableManaAbilityUtil.hasPlayableManaAbility(playable, fixture.player));
        assertFalse(PlayableManaAbilityUtil.hasPlayableManaAbility(tapped, fixture.player));
        final List<SpellAbility> filtered =
                PlayableManaAbilityUtil.getPlayableManaAbilities(tapped, fixture.player);
        assertTrue(filtered.isEmpty());
        filtered.add(null);
        assertEquals(1, filtered.size(),
                "legacy callers receive a mutable list even when no mana ability is playable");

        final Set<CardView> highlighted = fixture.controller.collectPaymentActionableCards();
        assertTrue(highlighted.contains(playable.getView()));
        assertFalse(highlighted.contains(tapped.getView()));
    }

    @Test
    void staticAlternativeCostForManaAbilityRemainsAvailable() {
        final Fixture fixture = fixture();
        final Card source = manaCard(1, fixture);
        source.addStaticAbility("Mode$ AlternativeCost | ValidSA$ Activated "
                + "| ValidPlayer$ You | Cost$ 0 | EffectZone$ All");
        source.setTapped(true);

        final List<SpellAbility> playable =
                PlayableManaAbilityUtil.getPlayableManaAbilities(source, fixture.player);

        assertEquals(1, playable.size(),
                "the tapped base ability is illegal, but its zero-cost alternative survives");
        assertTrue(playable.get(0).isManaAbility());
        assertTrue(PlayableManaAbilityUtil.hasPlayableManaAbility(source, fixture.player));
    }

    private static Card manaCard(final int id, final Fixture fixture) {
        final Card card = new Card(id, fixture.game);
        card.setName("Mana Ability Test " + id);
        card.setOwner(fixture.player);
        card.setController(fixture.player, 0L);
        card.setSickness(false);
        card.addSpellAbility(AbilityFactory.getAbility(
                "AB$ Mana | Cost$ T | Produced$ G | Amount$ 1", card));
        fixture.player.getZone(ZoneType.Battlefield).add(card);
        return card;
    }

    private static Fixture fixture() {
        final RegisteredPlayer registered = new RegisteredPlayer(new Deck("Mana highlight stress"))
                .setPlayer(new LobbyPlayerHuman("Human"));
        final List<RegisteredPlayer> players = List.of(registered);
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(players, rules, new Match(rules, players, "Mana highlight stress"));
        final Player player = game.getPlayers().get(0);
        return new Fixture(game, player, (PlayerControllerHuman) player.getController());
    }

    private static IGuiGame gui(final AtomicInteger weakSelectionWrites) {
        return (IGuiGame) Proxy.newProxyInstance(
                IGuiGame.class.getClassLoader(), new Class<?>[] { IGuiGame.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("setWeaklySelectable")) {
                        weakSelectionWrites.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });
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

    private static final class CountingNonManaCard extends Card {
        private final AtomicInteger broadAbilityQueries;

        private CountingNonManaCard(final int id, final Game game,
                                    final AtomicInteger broadAbilityQueries) {
            super(id, game);
            this.broadAbilityQueries = broadAbilityQueries;
        }

        @Override
        public List<SpellAbility> getAllPossibleAbilities(final Player player,
                                                           final boolean removeUnplayable) {
            broadAbilityQueries.incrementAndGet();
            return super.getAllPossibleAbilities(player, removeUnplayable);
        }
    }

    private record Fixture(Game game, Player player, PlayerControllerHuman controller) {
    }
}
