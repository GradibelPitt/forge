package forge.game.ability.effects;

import forge.StaticData;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.MyRandom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Replaces matching cards in hidden zones with random database cards.
 * Database candidates are indexed by mana value and cached whenever the
 * lightweight CardDiscover filter proves that the restriction is static.
 */
public class ReplaceCardsEffect extends SpellAbilityEffect {
    private static final ManaValuePoolCache REPLACEMENT_POOLS = new ManaValuePoolCache();

    @Override
    protected String getStackDescription(final SpellAbility sa) {
        return sa.getDescription();
    }

    @Override
    public void resolve(final SpellAbility sa) {
        final Card host = sa.getHostCard();
        final Game game = host.getGame();
        final String replacementValid = sa.getParamOrDefault(
                "ReplacementValid", "Card");
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                replacementValid, host, sa);
        if (!filter.isComplete()) {
            throw new IllegalArgumentException("ReplaceCards requires a statically exact "
                    + "ReplacementValid filter: " + replacementValid);
        }

        final Collection<PaperCard> database =
                StaticData.instance().getCommonCards().getUniqueCards();
        final Map<Integer, List<PaperCard>> candidatesByManaValue = REPLACEMENT_POOLS.get(
                StaticData.instance().getCommonCards(), database.size(), replacementValid,
                database, filter);
        final String validCards = sa.getParamOrDefault("ValidCards", "Card");
        final List<ZoneType> zones = ZoneType.listValueOf(
                sa.getParamOrDefault("Zones", "Hand,Library"));
        final boolean matchManaValue = sa.hasParam("MatchManaValue");
        final boolean rememberNames = sa.hasParam("RememberNames");
        final Random random = MyRandom.getRandom();

        for (final Player player : getDefinedPlayersOrTargeted(sa)) {
            if (player == null || !player.isInGame()) {
                continue;
            }
            for (final ZoneType zone : zones) {
                final CardCollection originals = CardLists.getValidCards(
                        new CardCollection(player.getCardsIn(zone)), validCards,
                        host.getController(), host, sa);
                for (final Card original : originals) {
                    final int manaValue = matchManaValue ? original.getCMC() : 0;
                    final List<PaperCard> candidates = candidatesByManaValue.get(manaValue);
                    if (candidates == null || candidates.isEmpty()
                            || !original.isInZone(zone)) {
                        continue;
                    }

                    final int libraryPosition = ZoneType.Library.equals(zone)
                            ? player.getZone(zone).getCards().indexOf(original) : 0;
                    final PaperCard selected = candidates.get(random.nextInt(candidates.size()));
                    final Card replacement = Card.fromPaperCard(selected, player);
                    game.getAction().ceaseToExist(original, true);
                    final Card moved = game.getAction().moveTo(zone, replacement,
                            Math.max(0, libraryPosition), sa, AbilityKey.newMap());
                    if (moved != null && rememberNames) {
                        rememberReplacementName(host, moved.getName());
                    }
                }
            }
        }
    }

    static void rememberReplacementName(final Card host, final String name) {
        if (name != null && !name.isEmpty() && !host.hasNamedCardName(name)) {
            host.addNamedCard(name);
        }
    }

    static final class ManaValuePoolCache {
        private Object databaseIdentity;
        private int databaseSize = -1;
        private final Map<String, Map<Integer, List<PaperCard>>> caches =
                new LinkedHashMap<>();

        synchronized Map<Integer, List<PaperCard>> get(final Object identity,
                final int size, final String restriction,
                final Iterable<PaperCard> database,
                final CardDiscoverCandidateFilter filter) {
            if (!filter.isContextIndependent()) {
                return build(database, filter);
            }
            if (databaseIdentity != identity || databaseSize != size) {
                databaseIdentity = identity;
                databaseSize = size;
                caches.clear();
            }
            return caches.computeIfAbsent(restriction,
                    ignored -> build(database, filter));
        }

        private static Map<Integer, List<PaperCard>> build(
                final Iterable<PaperCard> database,
                final CardDiscoverCandidateFilter filter) {
            final Map<Integer, List<PaperCard>> mutable = new LinkedHashMap<>();
            for (final PaperCard paperCard : database) {
                if (filter.matches(paperCard)) {
                    mutable.computeIfAbsent(
                            paperCard.getRules().getManaCost().getCMC(),
                            ignored -> new ArrayList<>()).add(paperCard);
                }
            }
            final Map<Integer, List<PaperCard>> result = new LinkedHashMap<>();
            for (final Map.Entry<Integer, List<PaperCard>> entry : mutable.entrySet()) {
                result.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        }
    }
}
