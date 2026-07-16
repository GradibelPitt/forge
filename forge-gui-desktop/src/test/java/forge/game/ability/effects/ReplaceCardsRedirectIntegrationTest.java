package forge.game.ability.effects;

import forge.ai.AITest;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.player.LobbyPlayerHuman;
import forge.player.PlayerControllerHuman;
import forge.util.MyRandom;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ReplaceCardsRedirectIntegrationTest extends AITest {
    @Test
    public void orderedHumanHandRejectsAReenteredObjectWithTheSameId() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(List.of(), rules,
                new Match(rules, List.of(), "ReplaceCards ordered hand identity"));
        final Player player = new Player("Human", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);
        final LobbyPlayerHuman lobbyPlayer = new LobbyPlayerHuman("Human");
        final PlayerControllerHuman controller = new PlayerControllerHuman(
                game, player, lobbyPlayer) {
            @Override
            public boolean isOrderedZone() {
                return true;
            }
        };
        player.dangerouslySetController(controller);
        final Card host = card(paper("Ordered Hand Source", "B"), player);
        final SpellAbility replace = replaceAbility(
                host, player, "Hand", "Card.Black", false);
        final Card original = card(paper("Planned Hand Black One", "B"), player);
        final Zone hand = player.getZone(ZoneType.Hand);
        hand.add(original);
        final ReplaceCardsEffect.ZoneReplacementPlan plan = plan(
                player, ZoneType.Hand, Card::isBlack);
        game.getAction().ceaseToExist(original, true);
        final Card sameId = CardFactory.getCard(
                paper("Same Id Hand Impostor", "B"), player,
                original.getId(), game);
        sameId.setController(player, game.getNextTimestamp());
        hand.add(sameId);
        final Set<String> names = new LinkedHashSet<>();
        final CountingRandom random = new CountingRandom();

        Assert.assertFalse(player.getController().isAI());
        Assert.assertTrue(player.getController().isOrderedZone());
        ReplaceCardsEffect.executePlan(game, replace, plan,
                Map.of(1, List.of(paper("Blue One", "U"))), names, random);

        Assert.assertEquals(random.calls, 0);
        Assert.assertEquals(hand.size(), 1);
        Assert.assertSame(hand.get(0), sameId);
        Assert.assertTrue(names.isEmpty());
        Assert.assertTrue(player.getZone(ZoneType.None).isEmpty());
    }

    @Test
    public void realMovedRedirectDoesNotCommitNameAndCorrectsLaterPosition() {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card host = card(paper("Redirect Source", "B"), player);
        host.addNamedCard("Existing Name");
        final SpellAbility replace = replaceAbility(
                host, player, "Library", "Card.Black", false);
        final Card redirect = card(redirectPaper(), player);
        player.getZone(ZoneType.Battlefield).add(redirect);

        final Card redirectedOriginal = addToLibrary(
                paper("Redirected Black One", "B"), player);
        final Card stableMiddle = addToLibrary(
                paper("Stable White Two", "1 W"), player);
        final Card successfulOriginal = addToLibrary(
                paper("Successful Black Three", "2 B"), player);
        final Card stableTail = addToLibrary(
                paper("Stable Green Four", "3 G"), player);
        final Map<Integer, List<PaperCard>> candidates = new LinkedHashMap<>();
        candidates.put(1, List.of(paper("Redirected White One", "W")));
        candidates.put(3, List.of(paper("Successful Blue Three", "2 U")));
        final Zone library = player.getZone(ZoneType.Library);
        final ReplaceCardsEffect.ZoneReplacementPlan plan = plan(
                player, ZoneType.Library, Card::isBlack);
        final Set<String> successfulNames = new LinkedHashSet<>();
        final CountingRandom random = new CountingRandom();

        Assert.assertTrue(player.getController().isAI());
        Assert.assertFalse(player.getController().isOrderedZone());
        ReplaceCardsEffect.executePlan(game, replace, plan, candidates,
                successfulNames, random);

        Assert.assertEquals(random.calls, 2);
        Assert.assertEquals(library.size(), 3);
        Assert.assertSame(library.get(0), stableMiddle);
        Assert.assertEquals(library.get(1).getName(), "Successful Blue Three");
        Assert.assertSame(library.get(2), stableTail);
        Assert.assertTrue(redirectedOriginal.isInZone(ZoneType.None));
        Assert.assertTrue(successfulOriginal.isInZone(ZoneType.None));
        Assert.assertEquals(player.getZone(ZoneType.Exile).getCards().stream()
                .map(Card::getName).toList(), List.of("Redirected White One"));
        Assert.assertTrue(player.getZone(ZoneType.None).isEmpty());
        Assert.assertEquals(successfulNames,
                Set.of("Successful Blue Three"));
        ReplaceCardsEffect.mergeReplacementNames(host, successfulNames);
        Assert.assertEquals(host.getNamedCards(),
                List.of("Existing Name", "Successful Blue Three"));
        Assert.assertFalse(host.hasNamedCardName("Redirected White One"));
    }

    @Test
    public void duplicateZonesAreDeduplicatedBeforeAFullResolve() {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card host = card(paper("Duplicate Zone Source", "B"), player);
        final Card original = addToLibrary(
                paper("Duplicate Zone Black One", "B"), player);
        final SpellAbility replace = replaceAbility(
                host, player, "Library,Library", "Card", true);
        final Random previousRandom = MyRandom.getRandom();
        final CountingRandom random = new CountingRandom();

        Assert.assertEquals(ReplaceCardsEffect.parseZones(replace),
                List.of(ZoneType.Library));
        MyRandom.setRandom(random);
        try {
            AbilityUtils.resolve(replace);
        } finally {
            MyRandom.setRandom(previousRandom);
        }

        Assert.assertEquals(random.calls, 1);
        Assert.assertEquals(player.getZone(ZoneType.Library).size(), 1);
        final Card replacement = player.getZone(ZoneType.Library).get(0);
        Assert.assertNotSame(replacement, original);
        Assert.assertFalse(replacement.isBlack());
        Assert.assertFalse(replacement.isColorless());
        Assert.assertTrue(original.isInZone(ZoneType.None));
        Assert.assertEquals(host.getNamedCards(), List.of(replacement.getName()));
    }

    private static PaperCard paper(final String name, final String manaCost) {
        return new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:" + manaCost,
                "Types:Sorcery",
                "A:SP$ Draw | NumCards$ 1 | SpellDescription$ Test.",
                "Oracle:Test card."
        )), "TST", CardRarity.Common);
    }

    private static PaperCard redirectPaper() {
        final List<String> script = new ArrayList<>();
        script.add("Name:Library Redirect");
        script.add("ManaCost:1");
        script.add("Types:Artifact");
        script.add("R:Event$ Moved | ActiveZones$ Battlefield "
                + "| Origin$ None | Destination$ Library "
                + "| ValidCard$ Card.White | ReplaceWith$ RedirectToExile "
                + "| Description$ White cards go to exile instead.");
        script.add("SVar:RedirectToExile:DB$ ChangeZone | Hidden$ True "
                + "| Origin$ None | Destination$ Exile "
                + "| Defined$ ReplacedCard");
        script.add("Oracle:White cards go to exile instead of libraries.");
        return new PaperCard(CardRules.fromScript(script),
                "TST", CardRarity.Common);
    }

    private static Card card(final PaperCard paperCard, final Player player) {
        final Card card = Card.fromPaperCard(paperCard, player);
        card.setGameTimestamp(player.getGame().getNextTimestamp());
        card.setController(player, player.getGame().getNextTimestamp());
        return card;
    }

    private static Card addToLibrary(final PaperCard paperCard,
            final Player player) {
        final Card card = card(paperCard, player);
        player.getZone(ZoneType.Library).add(card);
        return card;
    }

    private static SpellAbility replaceAbility(final Card host,
            final Player player, final String zones, final String validCards,
            final boolean rememberNames) {
        final SpellAbility replace = AbilityFactory.getAbility(
                "DB$ ReplaceCards | Defined$ You | Zones$ " + zones + " "
                        + "| ValidCards$ " + validCards + " "
                        + "| ReplacementValid$ Card.nonBlack+nonColorless "
                        + "| MatchManaValue$ True"
                        + (rememberNames ? " | RememberNames$ True" : ""),
                host);
        replace.setActivatingPlayer(player);
        return replace;
    }

    private static ReplaceCardsEffect.ZoneReplacementPlan plan(
            final Player player, final ZoneType zone,
            final java.util.function.Predicate<Card> predicate) {
        return new ReplaceCardsEffect.ZoneReplacementPlan(
                player, zone, ReplaceCardsEffect.collectIndexedMatches(
                player.getCardsIn(zone), predicate));
    }

    private static final class CountingRandom extends Random {
        private static final long serialVersionUID = -1050764492267062506L;
        private int calls;

        @Override
        public int nextInt(final int bound) {
            calls++;
            return 0;
        }
    }
}
