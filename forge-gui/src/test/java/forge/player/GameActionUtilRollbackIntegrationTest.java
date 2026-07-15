package forge.player;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.cost.Cost;
import forge.game.cost.CostPayLife;
import forge.game.cost.CostPayment;
import forge.game.cost.PaymentDecision;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.AbilityActivated;
import forge.game.spellability.Spell;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameActionUtilRollbackIntegrationTest {
    private static IGuiBase previousGui;

    @BeforeAll
    static void initializeLocalizer() {
        previousGui = GuiBase.getInterface();
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
        final String assets = Paths.get("..", "forge-gui").toAbsolutePath().normalize()
                + File.separator;
        GuiBase.setInterface((IGuiBase) Proxy.newProxyInstance(
                IGuiBase.class.getClassLoader(),
                new Class<?>[] { IGuiBase.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getAssetsDir")) {
                        return assets;
                    }
                    if (method.getName().equals("isGuiThread")) {
                        return true;
                    }
                    return defaultValue(method.getReturnType());
                }));
    }

    @AfterAll
    static void restoreGui() {
        GuiBase.setInterface(previousGui);
    }

    @Test
    void rollbackRefundsTapPaidThroughCostPayment() {
        final Fixture fixture = fixture();
        final Card source = card(1, fixture, ZoneType.Battlefield);
        source.setSickness(false);
        final Cost cost = new Cost("T", true);
        final AbilityActivated ability = activatedAbility(source, cost);
        ability.setActivatingPlayer(fixture.player);
        final CostPayment payment = new CostPayment(cost, ability);

        assertTrue(payment.payCost(fixture.controller.getCostDecisionMaker(
                fixture.player, ability, false)));
        assertTrue(source.isTapped());

        GameActionUtil.rollbackAbility(ability, null, -1, payment, source);

        assertFalse(source.isTapped());
    }

    @Test
    void rollbackReturnsSpellToOriginalHandPositionAndRebindsHost() {
        final Fixture fixture = fixture();
        final Card first = card(1, fixture, ZoneType.Hand);
        final Card spellCard = card(2, fixture, ZoneType.Hand);
        final Card third = card(3, fixture, ZoneType.Hand);
        final Zone hand = fixture.player.getZone(ZoneType.Hand);
        final Spell spell = new Spell(spellCard, Cost.Zero) {
            private static final long serialVersionUID = 1L;

            @Override
            public void resolve() {
            }
        };
        spell.setActivatingPlayer(fixture.player);
        spell.setCardState(spellCard.getCurrentState());
        final int originalPosition = hand.getCards().indexOf(spellCard);
        final Card stackCard = fixture.game.getAction().moveToStack(spellCard, spell);
        spell.setHostCard(stackCard);

        GameActionUtil.rollbackAbility(spell, hand, originalPosition,
                new CostPayment(Cost.Zero, spell), spellCard);

        assertEquals(List.of(first.getId(), spellCard.getId(), third.getId()),
                hand.getCards().stream().map(Card::getId).toList());
        assertSame(hand.getCards().get(originalPosition), spell.getHostCard());
        assertFalse(fixture.game.getStackZone().contains(stackCard));
    }

    @Test
    void payingLifeMakesTheRemainingHumanCostDecisionMandatory() throws ReflectiveOperationException {
        final Fixture fixture = fixture();
        final Card source = card(1, fixture, ZoneType.Battlefield);
        final Cost cost = new Cost("PayLife<1>", true);
        final AbilityActivated ability = activatedAbility(source, cost);
        ability.setActivatingPlayer(fixture.player);
        fixture.game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;
        final HumanCostDecision decision = (HumanCostDecision) fixture.controller
                .getCostDecisionMaker(fixture.player, ability, false);
        final CostPayLife payLife = (CostPayLife) cost.getCostParts().stream()
                .filter(CostPayLife.class::isInstance)
                .findFirst()
                .orElseThrow();

        final PaymentDecision payment = decision.visit(payLife);

        assertNotNull(payment);
        final Field mandatory = HumanCostDecision.class.getDeclaredField("mandatory");
        mandatory.setAccessible(true);
        assertTrue(mandatory.getBoolean(decision),
                "life cannot be refunded, so later cost prompts must no longer be cancellable");
    }

    private static AbilityActivated activatedAbility(final Card source, final Cost cost) {
        return new AbilityActivated(source, cost, null) {
            private static final long serialVersionUID = 1L;

            @Override
            public void resolve() {
            }
        };
    }

    private static Fixture fixture() {
        final RegisteredPlayer registeredPlayer = new RegisteredPlayer(new Deck("Rollback integration"))
                .setPlayer(new LobbyPlayerHuman("Player"));
        final List<RegisteredPlayer> players = List.of(registeredPlayer);
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(players, rules, new Match(rules, players, "Rollback integration"));
        final Player player = game.getPlayers().get(0);
        player.setLife(20, null);
        final PlayerControllerHuman controller = (PlayerControllerHuman) player.getController();
        controller.setGui((IGuiGame) Proxy.newProxyInstance(
                IGuiGame.class.getClassLoader(),
                new Class<?>[] { IGuiGame.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("isLibgdxPort") || method.getName().equals("confirm")) {
                        return true;
                    }
                    return defaultValue(method.getReturnType());
                }));
        return new Fixture(game, player, controller);
    }

    private static Card card(final int id, final Fixture fixture, final ZoneType zone) {
        final String name = "Rollback Integration Card " + id;
        final CardRules rules = new CardRules.Reader().readCard(List.of(
                "Name:" + name,
                "ManaCost:no cost",
                "Types:Creature Bear",
                "PT:2/2",
                "Oracle:"
        ), name);
        final Card card = CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common), fixture.player, id, fixture.game);
        fixture.player.getZone(zone).add(card);
        return card;
    }

    private static Object defaultValue(final Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }

    private record Fixture(Game game, Player player, PlayerControllerHuman controller) {
    }
}
