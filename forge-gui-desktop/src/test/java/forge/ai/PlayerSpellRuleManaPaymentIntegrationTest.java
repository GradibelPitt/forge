package forge.ai;

import forge.card.mana.ManaAtom;
import forge.game.Game;
import forge.game.GameSnapshot;
import forge.game.GameState;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.mana.Mana;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.PlaySpellAbility;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.ThreadUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class PlayerSpellRuleManaPaymentIntegrationTest extends AITest {
    private static final String RULE_KEY = "test:off-color-floating-mana";

    @Test
    public void playerSpellRulePaysColoredSpellWithOffColorFloatingMana() {
        final CastFixture withoutRule = prepareBlueSpellWithRedMana();

        Assert.assertFalse(PlaySpellAbility.playSpellAbility(
                withoutRule.player.getController(), withoutRule.player,
                withoutRule.spell));
        Assert.assertTrue(withoutRule.game.getStack().isEmpty());
        Assert.assertTrue(withoutRule.player.getZone(ZoneType.Hand)
                .contains(withoutRule.card));
        Assert.assertEquals(withoutRule.player.getManaPool().totalMana(), 1);

        final CastFixture withRule = prepareBlueSpellWithRedMana();
        withRule.player.getSpellRuleRegistry().register(
                RULE_KEY,
                "Card.nonColorless",
                "Spell",
                0,
                "AnyType->AnyColor");

        Assert.assertTrue(PlaySpellAbility.playSpellAbility(
                withRule.player.getController(), withRule.player,
                withRule.spell));
        Assert.assertFalse(withRule.game.getStack().isEmpty());
        Assert.assertEquals(withRule.game.getStack().peekAbility()
                .getHostCard().getName(), "Ponder");
        Assert.assertEquals(withRule.player.getManaPool().totalMana(), 0);
        Assert.assertEquals(withRule.game.getStack().peekAbility()
                .getPayingMana().size(), 1);
        Assert.assertEquals(withRule.game.getStack().peekAbility()
                .getPayingMana().get(0).getColor(), (byte) ManaAtom.RED);
    }

    @Test
    public void aiEstimateAndActualPaymentUseRegistryGenericReduction() {
        final CastFixture withoutRule = prepareThreeManaBlueSpellWithBlueMana();

        Assert.assertFalse(ComputerUtilMana.canPayManaCost(
                withoutRule.spell, withoutRule.player, 0, false));

        final CastFixture withRule = prepareThreeManaBlueSpellWithBlueMana();
        withRule.player.getSpellRuleRegistry().register(
                RULE_KEY + ":generic-reduction",
                "Card.nonColorless",
                "Spell",
                2,
                "");
        final ManaCostBeingPaid estimated = ComputerUtilMana.calculateManaCost(
                withRule.spell.getPayCosts(), withRule.spell,
                withRule.player, true, 0, false);

        Assert.assertEquals(estimated.toString(), "{U}");
        Assert.assertTrue(ComputerUtilMana.canPayManaCost(
                withRule.spell, withRule.player, 0, false));
        Assert.assertTrue(PlaySpellAbility.playSpellAbility(
                withRule.player.getController(), withRule.player,
                withRule.spell));
        Assert.assertFalse(withRule.game.getStack().isEmpty());
        Assert.assertEquals(withRule.game.getStack().peekAbility()
                .getHostCard().getName(), "Divination");
        Assert.assertEquals(withRule.player.getManaPool().totalMana(), 0);
        Assert.assertEquals(withRule.game.getStack().peekAbility()
                .getPayingMana().size(), 1);
        Assert.assertEquals(withRule.game.getStack().peekAbility()
                .getPayingMana().get(0).getColor(), (byte) ManaAtom.BLUE);
    }

    @Test
    public void mindslaverAiControllerUsesTheControlledPlayersRegistry() {
        final CastFixture withoutRule = prepareControlledBlueSpellWithRedMana();

        Assert.assertFalse(PlaySpellAbility.playSpellAbility(
                withoutRule.player.getController(), withoutRule.player,
                withoutRule.spell));
        Assert.assertTrue(withoutRule.game.getStack().isEmpty());
        Assert.assertEquals(withoutRule.player.getManaPool().totalMana(), 1);

        final CastFixture withRule = prepareControlledBlueSpellWithRedMana();
        withRule.player.getSpellRuleRegistry().register(
                RULE_KEY + ":mindslaver",
                "Card.nonColorless",
                "Spell",
                0,
                "AnyType->AnyColor");

        Assert.assertNotNull(withRule.player.getControllingPlayer());
        Assert.assertTrue(withRule.player.getController()
                instanceof PlayerControllerAi);
        Assert.assertTrue(withRule.player.getControllingPlayer()
                .getSpellRuleRegistry().isEmpty());
        Assert.assertTrue(PlaySpellAbility.playSpellAbility(
                withRule.player.getController(), withRule.player,
                withRule.spell));
        Assert.assertFalse(withRule.game.getStack().isEmpty());
        Assert.assertEquals(withRule.game.getStack().peekAbility()
                .getHostCard().getName(), "Ponder");
        Assert.assertEquals(withRule.player.getManaPool().totalMana(), 0);
        Assert.assertEquals(withRule.game.getStack().peekAbility()
                .getPayingMana().get(0).getColor(), (byte) ManaAtom.RED);
    }

    @Test
    public void gameSnapshotPreservesRegistryWithAiControllers() {
        final Game game = initAndCreateGame();
        final Player player = registerRuleForAiPlayer(game);

        final Game copiedGame = new GameSnapshot(game).makeCopy();
        final Player copiedPlayer = copiedGame.getPlayers().get(1);

        Assert.assertTrue(copiedPlayer.getController()
                instanceof PlayerControllerAi);
        Assert.assertEquals(copiedPlayer.getSpellRuleRegistry().size(), 1);
        Assert.assertEquals(copiedPlayer.getSpellRuleRegistry().toStateString(),
                player.getSpellRuleRegistry().toStateString());
    }

    @Test
    public void gameStateRoundTripPreservesRegistryWithAiController()
            throws Exception {
        final Game game = initAndCreateGame();
        final Player player = registerRuleForAiPlayer(game);
        final GameState dumped = new GameState();
        dumped.initFromGame(game);
        final String text = dumped.toString();

        Assert.assertTrue(text.contains("spellrules="));
        player.getSpellRuleRegistry().clear();
        Assert.assertTrue(player.getSpellRuleRegistry().isEmpty());

        final GameState restored = new GameState();
        restored.parse(Arrays.asList(text.split("\\R")));
        applyGameStateAndWait(restored, game);

        Assert.assertTrue(player.getController() instanceof PlayerControllerAi);
        Assert.assertEquals(player.getSpellRuleRegistry().size(), 1);
    }

    @Test
    public void restartGameResolveClearsRegistryWithAiController() {
        final Game game = initAndCreateGame();
        final Player player = registerRuleForAiPlayer(game);
        final Card source = addCard("Karn Liberated", player);
        final SpellAbility restart = AbilityFactory.getAbility(
                "DB$ RestartGame | RestrictFromZone$ Exile "
                        + "| RestrictFromValid$ Card",
                source);
        restart.setActivatingPlayer(player);

        restart.resolve();

        Assert.assertTrue(player.getController() instanceof PlayerControllerAi);
        Assert.assertTrue(player.getSpellRuleRegistry().isEmpty());
    }

    @Test
    public void subgameStartsEmptyAndLeavesMainGameRegistryIntact() {
        final Game game = initAndCreateGame();
        final Player player = registerRuleForAiPlayer(game);

        final Game subgame = new Game(game.getMatch().getPlayers(),
                game.getRules(), game.getMatch(), game, 20);
        final Player subgamePlayer = subgame.getPlayers().get(1);

        Assert.assertTrue(subgamePlayer.getController()
                instanceof PlayerControllerAi);
        Assert.assertTrue(subgamePlayer.getSpellRuleRegistry().isEmpty());
        Assert.assertEquals(player.getSpellRuleRegistry().size(), 1);
    }

    @Test
    public void aiAcceptsReplaceCardsWithGrantSpellRuleSubAbility() {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card source = createCard("Ponder", player);
        final SpellAbility replace = AbilityFactory.getAbility(
                "DB$ ReplaceCards | Defined$ You | Zones$ Hand,Library "
                        + "| ValidCards$ Card.Black "
                        + "| ReplacementValid$ Card.nonBlack+nonColorless "
                        + "| MatchManaValue$ True",
                source);
        final AbilitySub grant = (AbilitySub) AbilityFactory.getAbility(
                "DB$ GrantSpellRule | Defined$ You | RuleKey$ ai-test "
                        + "| ValidCards$ Card.nonColorless | ValidSA$ Spell "
                        + "| ReduceGeneric$ 2 "
                        + "| ManaConversion$ AnyType->AnyColor "
                        + "| Duration$ Permanent",
                source);
        replace.setSubAbility(grant);
        replace.setActivatingPlayer(player);

        Assert.assertTrue(SpellApiToAi.Converter.get(replace)
                .canPlayWithSubs(player, replace).willingToPlay());
    }

    private CastFixture prepareBlueSpellWithRedMana() {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card card = addCardToZone("Ponder", player, ZoneType.Hand);
        final SpellAbility spell = card.getSpellAbilities().getFirst();
        final Card redManaSource = createCard("Mountain", player);
        player.getManaPool().addMana(new Mana(
                (byte) ManaAtom.RED, redManaSource, null, player));
        return new CastFixture(game, player, card, spell);
    }

    private CastFixture prepareThreeManaBlueSpellWithBlueMana() {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card card = addCardToZone("Divination", player, ZoneType.Hand);
        final SpellAbility spell = card.getSpellAbilities().getFirst();
        spell.setActivatingPlayer(player);
        final Card blueManaSource = createCard("Island", player);
        player.getManaPool().addMana(new Mana(
                (byte) ManaAtom.BLUE, blueManaSource, null, player));
        return new CastFixture(game, player, card, spell);
    }

    private CastFixture prepareControlledBlueSpellWithRedMana() {
        final Game game = initAndCreateGame();
        final Player master = game.getPlayers().get(0);
        final Player slave = game.getPlayers().get(1);
        slave.addController(game.getNextTimestamp(), master);
        final Card card = addCardToZone("Ponder", slave, ZoneType.Hand);
        final SpellAbility spell = card.getSpellAbilities().getFirst();
        spell.setActivatingPlayer(slave);
        final Card redManaSource = createCard("Mountain", slave);
        slave.getManaPool().addMana(new Mana(
                (byte) ManaAtom.RED, redManaSource, null, slave));
        return new CastFixture(game, slave, card, spell);
    }

    private Player registerRuleForAiPlayer(final Game game) {
        final Player player = game.getPlayers().get(1);
        Assert.assertTrue(player.getController() instanceof PlayerControllerAi);
        player.getSpellRuleRegistry().register(
                RULE_KEY,
                "Card.nonColorless",
                "Spell",
                0,
                "AnyType->AnyColor");
        return player;
    }

    private static void applyGameStateAndWait(final GameState state,
                                              final Game game)
            throws Exception {
        final FutureTask<Void> apply = new FutureTask<>(() -> {
            state.applyToGame(game);
            return null;
        });
        ThreadUtil.invokeInGameThread(apply);
        apply.get(10, TimeUnit.SECONDS);
    }

    private record CastFixture(Game game, Player player, Card card,
                               SpellAbility spell) {
    }
}
