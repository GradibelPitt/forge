package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.item.PaperCard;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplaceCardsEffectTest {
    private static PaperCard paper(final String name, final String manaCost) {
        return new PaperCard(CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:" + manaCost,
                "Types:Sorcery",
                "Oracle:Test card."
        )), "TST", CardRarity.Common);
    }

    @Test
    public void cachedReplacementPoolScansDatabaseOnlyOnceAndBucketsByManaValue() {
        final Collection<PaperCard> database = Arrays.asList(
                paper("White One", "W"),
                paper("Blue Three", "2 U"),
                paper("Black One", "B"),
                paper("Colorless Three", "3")
        );
        final Card source = new Card(1, null);
        source.setName("Source");
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                "Card.nonBlack+nonColorless", source, null);
        final AtomicInteger visits = new AtomicInteger();
        final Iterable<PaperCard> countedDatabase = () -> database.stream()
                .peek(ignored -> visits.incrementAndGet()).iterator();
        final ReplaceCardsEffect.ManaValuePoolCache cache =
                new ReplaceCardsEffect.ManaValuePoolCache();
        final Object databaseIdentity = new Object();

        final Map<Integer, List<PaperCard>> first = cache.get(
                databaseIdentity, database.size(), "Card.nonBlack+nonColorless",
                countedDatabase, filter);
        final Map<Integer, List<PaperCard>> second = cache.get(
                databaseIdentity, database.size(), "Card.nonBlack+nonColorless",
                countedDatabase, filter);

        Assert.assertSame(first, second);
        Assert.assertEquals(visits.get(), database.size());
        Assert.assertEquals(first.get(1).stream().map(PaperCard::getName).toList(),
                List.of("White One"));
        Assert.assertEquals(first.get(3).stream().map(PaperCard::getName).toList(),
                List.of("Blue Three"));
    }

    @Test
    public void replacementNamesAreDeduplicatedForConstantTimeEmblemLookup() {
        final Card host = new Card(1, null);
        host.setName("Source");

        ReplaceCardsEffect.rememberReplacementName(host, "White One");
        ReplaceCardsEffect.rememberReplacementName(host, "White One");
        ReplaceCardsEffect.rememberReplacementName(host, "Blue Three");

        Assert.assertEquals(host.getNamedCards(), List.of("White One", "Blue Three"));
        Assert.assertTrue(host.hasNamedCardName("White One"));
        Assert.assertTrue(host.hasNamedCardName("Blue Three"));
        Assert.assertFalse(host.hasNamedCardName("Black One"));
    }

    @Test
    public void namedCardsValidityMatchesFutureCardsByName() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(List.of(), rules,
                new Match(rules, List.of(), "ReplaceCards named lookup test"));
        final Player player = new Player("Controller", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);

        final Card emblem = new Card(game.nextCardId(), game);
        emblem.setName("Emblem");
        emblem.setOwner(player);
        emblem.setController(player, game.getNextTimestamp());
        emblem.addNamedCard("White One");

        final Card futureCopy = new Card(game.nextCardId(), game);
        futureCopy.setName("White One");
        futureCopy.setOwner(player);
        futureCopy.setController(player, game.getNextTimestamp());
        futureCopy.setZone(player.getZone(forge.game.zone.ZoneType.Hand));

        Assert.assertTrue(futureCopy.isValid("Card.sharesNameWith NamedCards",
                player, emblem, null));
    }
}
