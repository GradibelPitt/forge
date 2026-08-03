package forge.game.ability.effects;

import forge.StaticData;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardPredicates;
import forge.game.card.GameRuleCard;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.MyRandom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

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
        validateAbility(sa);
        final Card host = sa.getHostCard();
        final Game game = host.getGame();
        final List<ZoneType> zones = parseZones(sa);
        final String replacementValid = sa.getParamOrDefault(
                "ReplacementValid", "Card");
        final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(
                replacementValid, host, sa);
        if (!filter.isComplete()) {
            throw new IllegalArgumentException("ReplaceCards requires a statically exact "
                    + "ReplacementValid filter: " + replacementValid);
        }

        final String validCards = sa.getParamOrDefault("ValidCards", "Card");
        final Predicate<Card> validPredicate = CardPredicates.restriction(
                validCards.split(","), host.getController(), host, sa);
        final List<ZoneReplacementPlan> plans = collectPlans(
                sa, zones, validPredicate);
        if (plans.stream().allMatch(plan -> plan.cards().isEmpty())) {
            return;
        }

        final Collection<PaperCard> database =
                StaticData.instance().getCommonCards().getUniqueCards();
        final Map<Integer, List<PaperCard>> candidatesByManaValue = REPLACEMENT_POOLS.get(
                StaticData.instance().getCommonCards(), database.size(), replacementValid,
                database, filter);
        final boolean rememberNames = "True".equalsIgnoreCase(
                sa.getParamOrDefault("RememberNames", "False"));
        final LinkedHashSet<String> successfulNames = new LinkedHashSet<>();
        final Random random = MyRandom.getRandom();

        for (final ZoneReplacementPlan plan : plans) {
            if (!plan.player().isInGame()) {
                continue;
            }
            executePlan(game, sa, plan, candidatesByManaValue,
                    rememberNames ? successfulNames : null, random);
        }
        if (rememberNames) {
            mergeReplacementNames(host, successfulNames);
        }
    }

    private static void validateAbility(final SpellAbility sa) {
        if (sa == null || sa.getHostCard() == null
                || sa.getHostCard().getGame() == null) {
            throw new IllegalArgumentException(
                    "ReplaceCards requires an in-game host card");
        }
        final String matchManaValue = sa.getParam("MatchManaValue");
        if (!sa.hasParam("MatchManaValue") || matchManaValue == null
                || !"True".equalsIgnoreCase(matchManaValue.trim())) {
            throw new IllegalArgumentException(
                    "ReplaceCards requires MatchManaValue$ True");
        }
    }

    static List<ZoneType> parseZones(final SpellAbility sa) {
        final Set<ZoneType> zones = new LinkedHashSet<>(ZoneType.listValueOf(
                sa.getParamOrDefault("Zones", "Hand,Library")));
        if (zones.isEmpty()) {
            throw new IllegalArgumentException(
                    "ReplaceCards requires at least one supported zone");
        }
        for (final ZoneType zone : zones) {
            if (zone != ZoneType.Hand && zone != ZoneType.Library) {
                throw new IllegalArgumentException(
                        "ReplaceCards only supports Hand and Library zones: " + zone);
            }
        }
        return new ArrayList<>(zones);
    }

    private List<ZoneReplacementPlan> collectPlans(final SpellAbility sa,
            final List<ZoneType> zones, final Predicate<Card> validPredicate) {
        final List<Player> players = collectEligiblePlayers(
                getDefinedPlayersOrTargeted(sa));

        final List<ZoneReplacementPlan> plans = new ArrayList<>();
        for (final Player player : players) {
            for (final ZoneType zone : zones) {
                plans.add(new ZoneReplacementPlan(player, zone,
                        collectIndexedMatches(player.getCardsIn(zone), validPredicate)));
            }
        }
        return plans;
    }

    static List<Player> collectEligiblePlayers(final Iterable<Player> values) {
        final Set<Player> players = new LinkedHashSet<>();
        for (final Player player : values) {
            if (player != null && player.isInGame()) {
                players.add(player);
            }
        }
        return new ArrayList<>(players);
    }

    static <T> List<IndexedValue<T>> collectIndexedMatches(
            final Iterable<T> values, final Predicate<? super T> predicate) {
        final List<IndexedValue<T>> result = new ArrayList<>();
        final Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int position = 0;
        for (final T value : values) {
            if (value != null && predicate.test(value) && seen.add(value)) {
                result.add(new IndexedValue<>(value, position));
            }
            position++;
        }
        return result;
    }

    static <T> boolean containsIdentity(final Iterable<T> values,
            final T expected) {
        for (final T value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    static void executePlan(final Game game,
            final SpellAbility sa, final ZoneReplacementPlan plan,
            final Map<Integer, List<PaperCard>> candidatesByManaValue,
            final Set<String> successfulNames, final Random random) {
        final Player player = plan.player();
        final boolean orderedHand = plan.zone() == ZoneType.Hand
                && player.getController() != null
                && player.getController().isOrderedZone();
        executePlan(game, sa, plan, candidatesByManaValue, successfulNames,
                random, orderedHand);
    }

    private static void executePlan(final Game game,
            final SpellAbility sa, final ZoneReplacementPlan plan,
            final Map<Integer, List<PaperCard>> candidatesByManaValue,
            final Set<String> successfulNames, final Random random,
            final boolean orderedHand) {
        final Player player = plan.player();
        final Zone zone = player.getZone(plan.zone());
        int positionAdjustment = 0;

        for (final IndexedValue<Card> indexed : plan.cards()) {
            final Card original = indexed.value();
            final int position = indexed.position() + positionAdjustment;
            if (orderedHand) {
                if (!containsIdentity(zone, original)) {
                    continue;
                }
            } else if (position < 0 || position >= zone.size()
                    || zone.get(position) != original) {
                continue;
            }

            final List<PaperCard> candidates = candidatesByManaValue.get(
                    original.getCMC());
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }

            final PaperCard selected = candidates.get(
                    random.nextInt(candidates.size()));
            final Card replacement = Card.fromPaperCard(selected, player);
            final Map<AbilityKey, Object> moveParams = AbilityKey.newMap();
            final Zone stagingZone = player.getZone(ZoneType.None);
            // A null-origin non-token uses GameAction's dev-mode fast path and
            // skips Moved replacements. Stage it in None just as MakeCard does.
            final Card prepared = game.getAction().moveTo(
                    stagingZone, replacement, sa, moveParams);
            if (prepared == null || !stagingZone.contains(prepared)) {
                continue;
            }

            final int sizeBefore = zone.size();
            game.getAction().ceaseToExist(original, true);
            final int destinationPosition = orderedHand
                    ? Math.min(Math.max(0, position), zone.size())
                    : Math.max(0, position);
            final Card moved = game.getAction().moveTo(zone, prepared,
                    destinationPosition, sa, moveParams);
            positionAdjustment += zone.size() - sizeBefore;

            boolean moveSucceeded = moved != null && moved.getZone() == zone;
            if (moveSucceeded) {
                moveSucceeded = orderedHand
                        ? containsIdentity(zone, moved)
                        : destinationPosition < zone.size()
                        && zone.get(destinationPosition) == moved;
            }
            if (!moveSucceeded && stagingZone.contains(prepared)) {
                game.getAction().ceaseToExist(prepared, true);
            }
            if (moveSucceeded && successfulNames != null
                    && moved.getName() != null && !moved.getName().isEmpty()) {
                successfulNames.add(moved.getName());
            }
        }
    }

    record IndexedValue<T>(T value, int position) {
    }

    record ZoneReplacementPlan(
            Player player,
            ZoneType zone,
            List<IndexedValue<Card>> cards) {
    }

    static void mergeReplacementNames(final Card host,
            final Iterable<String> successfulNames) {
        final ReplacementNameMerge merge = collectReplacementNameMerge(
                host.getNamedCards(), successfulNames);
        if (merge.hasAdditions()) {
            host.setNamedCards(new ArrayList<>(merge.names()));
        }
    }

    static ReplacementNameMerge collectReplacementNameMerge(
            final Iterable<String> currentNames,
            final Iterable<String> successfulNames) {
        final List<String> merged = new ArrayList<>();
        final Set<String> membership = new LinkedHashSet<>();
        for (final String name : currentNames) {
            merged.add(name);
            if (name != null && !name.isEmpty()) {
                membership.add(name);
            }
        }
        boolean hasAdditions = false;
        for (final String name : successfulNames) {
            if (name != null && !name.isEmpty() && membership.add(name)) {
                merged.add(name);
                hasAdditions = true;
            }
        }
        return new ReplacementNameMerge(
                Collections.unmodifiableList(merged), hasAdditions);
    }

    record ReplacementNameMerge(List<String> names, boolean hasAdditions) {
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
                if (GameRuleCard.canMaterialize(paperCard)
                        && filter.matches(paperCard)) {
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
