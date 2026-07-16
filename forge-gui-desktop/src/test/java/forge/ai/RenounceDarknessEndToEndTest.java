package forge.ai;

import forge.StaticData;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.mana.ManaAtom;
import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.mana.Mana;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.PlaySpellAbility;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RenounceDarknessEndToEndTest extends AITest {
    @Test
    public void parsedChainReplacesHandAndLibraryThenGrantsAnIdempotentRule() {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final SpellAbility root = parsedRenounceDarkness(player);

        final Card handOriginal = addCardToZone("Duress", player, ZoneType.Hand);
        final Card libraryBefore = addCardToZone("Opt", player, ZoneType.Library);
        final Card libraryOriginal = addCardToZone("Murder", player, ZoneType.Library);
        final Card libraryAfter = addCardToZone(
                "Lightning Bolt", player, ZoneType.Library);
        final int handSize = player.getZone(ZoneType.Hand).size();
        final int librarySize = player.getZone(ZoneType.Library).size();
        final int handManaValue = handOriginal.getCMC();
        final int libraryManaValue = libraryOriginal.getCMC();
        final int libraryPosition = player.getZone(ZoneType.Library)
                .getCards().indexOf(libraryOriginal);

        Assert.assertTrue(handOriginal.isBlack());
        Assert.assertTrue(libraryOriginal.isBlack());
        Assert.assertEquals(libraryPosition, 1);

        AbilityUtils.resolve(root);

        final CardCollectionView handAfterFirstResolve = player
                .getZone(ZoneType.Hand).getCards();
        final CardCollectionView libraryAfterFirstResolve = player
                .getZone(ZoneType.Library).getCards();
        final Card handReplacement = handAfterFirstResolve.get(0);
        final Card libraryReplacement = libraryAfterFirstResolve.get(libraryPosition);

        Assert.assertEquals(handAfterFirstResolve.size(), handSize);
        Assert.assertEquals(libraryAfterFirstResolve.size(), librarySize);
        assertCeasedToExist(handOriginal, player, ZoneType.Hand);
        assertCeasedToExist(libraryOriginal, player, ZoneType.Library);
        assertEquivalentReplacement(handReplacement, handOriginal, handManaValue);
        assertEquivalentReplacement(
                libraryReplacement, libraryOriginal, libraryManaValue);
        Assert.assertSame(libraryAfterFirstResolve.get(0), libraryBefore);
        Assert.assertSame(libraryAfterFirstResolve.get(2), libraryAfter);
        Assert.assertEquals(player.getSpellRuleRegistry().size(), 1);

        AbilityUtils.resolve(root);

        Assert.assertEquals(player.getZone(ZoneType.Hand).size(), handSize);
        Assert.assertEquals(player.getZone(ZoneType.Library).size(), librarySize);
        Assert.assertSame(player.getZone(ZoneType.Hand).getCards().get(0),
                handReplacement);
        Assert.assertSame(player.getZone(ZoneType.Library).getCards().get(0),
                libraryBefore);
        Assert.assertSame(player.getZone(ZoneType.Library).getCards()
                .get(libraryPosition), libraryReplacement);
        Assert.assertSame(player.getZone(ZoneType.Library).getCards().get(2),
                libraryAfter);
        Assert.assertEquals(player.getSpellRuleRegistry().size(), 1);

        final Card ponder = addCardToZone("Ponder", player, ZoneType.Hand);
        final SpellAbility ponderSpell = ponder.getSpellAbilities().getFirst();
        ponderSpell.setActivatingPlayer(player);
        addFloatingMana(player, ManaAtom.RED, "Mountain");

        Assert.assertTrue(PlaySpellAbility.playSpellAbility(
                player.getController(), player, ponderSpell));
        Assert.assertFalse(game.getStack().isEmpty());
        Assert.assertEquals(game.getStack().peekAbility()
                .getHostCard().getName(), ponder.getName());
        Assert.assertEquals(player.getManaPool().totalMana(), 0);
        Assert.assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        Assert.assertEquals(game.getStack().peekAbility().getPayingMana()
                .get(0).getColor(), (byte) ManaAtom.RED);
    }

    @Test
    public void parsedChainReducesAnActualThreeManaSpellByTwo() {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final SpellAbility root = parsedRenounceDarkness(player);

        AbilityUtils.resolve(root);

        Assert.assertEquals(player.getSpellRuleRegistry().size(), 1);
        final Card divination = addCardToZone(
                "Divination", player, ZoneType.Hand);
        final SpellAbility divinationSpell = divination
                .getSpellAbilities().getFirst();
        divinationSpell.setActivatingPlayer(player);
        addFloatingMana(player, ManaAtom.BLUE, "Island");
        final ManaCostBeingPaid estimated = ComputerUtilMana.calculateManaCost(
                divinationSpell.getPayCosts(), divinationSpell,
                player, true, 0, false);

        Assert.assertEquals(estimated.toString(), "{U}");
        Assert.assertTrue(PlaySpellAbility.playSpellAbility(
                player.getController(), player, divinationSpell));
        Assert.assertFalse(game.getStack().isEmpty());
        Assert.assertEquals(game.getStack().peekAbility()
                .getHostCard().getName(), divination.getName());
        Assert.assertEquals(player.getManaPool().totalMana(), 0);
        Assert.assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        Assert.assertEquals(game.getStack().peekAbility().getPayingMana()
                .get(0).getColor(), (byte) ManaAtom.BLUE);
    }

    @Test
    public void missingManaValueCandidateLeavesTheOriginalCardInPlace() {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final SpellAbility root = parsedRenounceDarkness(player);
        final int missingManaValue = firstMissingReplacementManaValue();
        final Card original = Card.fromPaperCard(paper(
                "No Replacement Bucket Black Card",
                (missingManaValue - 1) + " B",
                "A:SP$ Draw | NumCards$ 1 | SpellDescription$ Test."), player);
        original.setGameTimestamp(game.getNextTimestamp());
        player.getZone(ZoneType.Hand).add(original);

        Assert.assertTrue(original.isBlack());
        Assert.assertEquals(original.getCMC(), missingManaValue);
        Assert.assertFalse(hasReplacementCandidateAt(missingManaValue));

        AbilityUtils.resolve(root);

        Assert.assertEquals(player.getZone(ZoneType.Hand).size(), 1);
        Assert.assertSame(player.getZone(ZoneType.Hand).getCards().get(0), original);
        Assert.assertTrue(original.isInZone(ZoneType.Hand));
        Assert.assertEquals(player.getSpellRuleRegistry().size(), 1);
    }

    private static SpellAbility parsedRenounceDarkness(final Player player) {
        final Path script = locateRenounceDarknessScript();
        final CardRules rules;
        try {
            rules = new CardRules.Reader().readCard(
                    Files.readAllLines(script, StandardCharsets.UTF_8), "弃暗投明");
        } catch (final IOException ex) {
            throw new AssertionError("Unable to read the real 弃暗投明 script", ex);
        }
        final Card source = Card.fromPaperCard(
                new PaperCard(rules, "PH01", CardRarity.Rare), player);
        source.setController(player, player.getGame().getNextTimestamp());
        final SpellAbility root = source.getSpellAbilities().getFirst();
        root.setActivatingPlayer(player);

        Assert.assertEquals(source.getName(), "弃暗投明");
        Assert.assertEquals(root.getApi(), ApiType.ReplaceCards);
        Assert.assertNotNull(root.getSubAbility());
        Assert.assertEquals(root.getSubAbility().getApi(), ApiType.GrantSpellRule);
        Assert.assertSame(root.getSubAbility().getParent(), root);
        return root;
    }

    private static Path locateRenounceDarknessScript() {
        final Path rootWorkingDirectory = Paths.get(
                "custom", "cards", "black", "弃暗投明.txt")
                .toAbsolutePath().normalize();
        final Path moduleWorkingDirectory = Paths.get(
                "..", "custom", "cards", "black", "弃暗投明.txt")
                .toAbsolutePath().normalize();
        if (Files.isRegularFile(rootWorkingDirectory)) {
            return rootWorkingDirectory;
        }
        if (Files.isRegularFile(moduleWorkingDirectory)) {
            return moduleWorkingDirectory;
        }
        throw new AssertionError("Unable to locate the real 弃暗投明 script; checked "
                + rootWorkingDirectory + " and " + moduleWorkingDirectory);
    }

    private static PaperCard paper(final String name, final String manaCost,
                                   final String... scriptLines) {
        final String[] lines = new String[scriptLines.length + 4];
        lines[0] = "Name:" + name;
        lines[1] = "ManaCost:" + manaCost;
        lines[2] = "Types:Sorcery";
        System.arraycopy(scriptLines, 0, lines, 3, scriptLines.length);
        lines[lines.length - 1] = "Oracle:Test card.";
        return new PaperCard(CardRules.fromScript(Arrays.asList(lines)),
                "TST", CardRarity.Common);
    }

    private static void assertCeasedToExist(final Card original,
                                            final Player player,
                                            final ZoneType formerZone) {
        Assert.assertFalse(player.getZone(formerZone).contains(original));
        Assert.assertTrue(original.isInZone(ZoneType.None));
    }

    private static void assertEquivalentReplacement(final Card replacement,
                                                    final Card original,
                                                    final int manaValue) {
        Assert.assertNotSame(replacement, original);
        Assert.assertEquals(replacement.getCMC(), manaValue);
        Assert.assertFalse(replacement.isBlack());
        Assert.assertFalse(replacement.isColorless());
    }

    private void addFloatingMana(final Player player, final int color,
                                 final String sourceName) {
        player.getManaPool().addMana(new Mana(
                (byte) color, createCard(sourceName, player), null, player));
    }

    private static int firstMissingReplacementManaValue() {
        final Set<Integer> occupied = new HashSet<>();
        for (final PaperCard candidate : StaticData.instance()
                .getCommonCards().getUniqueCards()) {
            if (isReplacementCandidate(candidate)) {
                occupied.add(candidate.getRules().getManaCost().getCMC());
            }
        }
        int manaValue = 1;
        while (occupied.contains(manaValue)) {
            manaValue++;
        }
        return manaValue;
    }

    private static boolean hasReplacementCandidateAt(final int manaValue) {
        for (final PaperCard candidate : StaticData.instance()
                .getCommonCards().getUniqueCards()) {
            if (isReplacementCandidate(candidate)
                    && candidate.getRules().getManaCost().getCMC() == manaValue) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReplacementCandidate(final PaperCard candidate) {
        return !candidate.getRules().getColor().hasBlack()
                && !candidate.getRules().getColor().isColorless();
    }
}
