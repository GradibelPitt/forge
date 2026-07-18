package forge.ai.simulation;

import forge.ai.ComputerUtilMana;
import forge.card.MagicColor;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.mana.ManaConversionMatrix;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerSpellRuleRegistry;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityManaConvert;
import forge.game.zone.ZoneType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PlayerSpellRuleGameCopierTest extends SimulationTest {
    @Test
    public void gameCopierPreservesRuleStateEffectsAndIsolation() {
        final Game originalGame = initAndCreateGame();
        final Player originalOpponent = originalGame.getPlayers().get(0);
        final Player originalPlayer = originalGame.getPlayers().get(1);
        originalGame.getPhaseHandler().devModeSet(
                PhaseType.MAIN1, originalPlayer);
        final PlayerSpellRuleRegistry originalRegistry =
                originalPlayer.getSpellRuleRegistry();

        originalRegistry.register("stable-harmony", "Card.nonColorless",
                "Spell", 2, "AnyType->AnyColor");
        originalRegistry.registerStacking("stacking-reduction",
                "Card.nonColorless", "Spell", 1, "");
        originalOpponent.getSpellRuleRegistry().register("opponent-rule",
                "Card.nonColorless", "Spell", 1, "");
        final String originalState = originalRegistry.toStateString();
        final String opponentState = originalOpponent.getSpellRuleRegistry()
                .toStateString();
        Assert.assertTrue(originalState.startsWith("1;"));

        final Card originalCard = addCardToZone(
                "Divination", originalPlayer, ZoneType.Hand);
        final Card originalPairFirst = addCardToZone(
                "Memnite", originalPlayer, ZoneType.Battlefield);
        final Card originalPairSecond = addCardToZone(
                "Ornithopter", originalPlayer, ZoneType.Battlefield);
        originalPairFirst.setPairedWith(originalPairSecond);
        originalPairSecond.setPairedWith(originalPairFirst);
        originalGame.getAction().checkStaticAbilities(false);
        final GameCopier copier = new GameCopier(originalGame);
        final Game copiedGame = copier.makeCopy();
        final Player copiedPlayer = (Player) copier.find(originalPlayer);
        final Player copiedOpponent = (Player) copier.find(originalOpponent);
        final PlayerSpellRuleRegistry copiedRegistry =
                copiedPlayer.getSpellRuleRegistry();

        Assert.assertSame(copier.getCopiedGame(), copiedGame);
        Assert.assertNotSame(copiedRegistry, originalRegistry);
        Assert.assertEquals(copiedRegistry.size(), 2);
        Assert.assertEquals(copiedRegistry.toStateString(), originalState);
        Assert.assertEquals(copiedOpponent.getSpellRuleRegistry().size(), 1);
        Assert.assertEquals(copiedOpponent.getSpellRuleRegistry()
                .toStateString(), opponentState);

        final Card copiedCard = (Card) copier.find(originalCard);
        final Card copiedPairFirst = (Card) copier.find(originalPairFirst);
        final Card copiedPairSecond = (Card) copier.find(originalPairSecond);
        Assert.assertNotSame(copiedPairFirst, originalPairFirst);
        Assert.assertNotSame(copiedPairSecond, originalPairSecond);
        Assert.assertSame(copiedPairFirst.getPairedWith(), copiedPairSecond);
        Assert.assertSame(copiedPairSecond.getPairedWith(), copiedPairFirst);
        final SpellAbility copiedSpell = copiedCard.getFirstSpellAbility();
        copiedSpell.setActivatingPlayer(copiedPlayer);
        Assert.assertEquals(copiedRegistry.getGenericReduction(
                copiedCard, copiedSpell), 3);
        final ManaCostBeingPaid copiedCost = ComputerUtilMana.calculateManaCost(
                copiedSpell.getPayCosts(), copiedSpell, copiedPlayer,
                true, 0, false);
        Assert.assertEquals(copiedCost.toString(), "{U}");

        final ManaConversionMatrix conversion = new ManaConversionMatrix();
        conversion.restoreColorReplacements();
        Assert.assertTrue(StaticAbilityManaConvert.manaConvert(
                conversion, copiedPlayer, copiedCard, copiedSpell));
        Assert.assertNotEquals(
                conversion.getPossibleColorUses(MagicColor.RED)
                        & MagicColor.BLUE,
                0);

        Assert.assertEquals(copiedRegistry.registerStacking("copy-only",
                "Card.nonColorless", "Spell", 1, "").getKey(),
                "copy-only#2");
        Assert.assertEquals(originalRegistry.toStateString(), originalState);
        copiedRegistry.clear();
        Assert.assertEquals(originalRegistry.toStateString(), originalState);

        copiedRegistry.register("copy-after-clear", "Card.nonColorless",
                "Spell", 1, "");
        final String independentlyRebuiltCopyState =
                copiedRegistry.toStateString();
        originalRegistry.register("original-only", "Card.nonColorless",
                "Spell", 1, "");
        Assert.assertEquals(copiedRegistry.toStateString(),
                independentlyRebuiltCopyState);
        originalRegistry.clear();
        Assert.assertEquals(copiedRegistry.toStateString(),
                independentlyRebuiltCopyState);

        copiedPairSecond.getCurrentState().removeCardTypes(true);
        copiedGame.getAction().checkStaticAbilities(false);
        Assert.assertNull(copiedPairFirst.getPairedWith());
        Assert.assertNull(copiedPairSecond.getPairedWith());
        Assert.assertSame(originalPairFirst.getPairedWith(),
                originalPairSecond,
                "simulation cleanup cannot mutate original pair identities");
        Assert.assertSame(originalPairSecond.getPairedWith(),
                originalPairFirst);
    }
}
