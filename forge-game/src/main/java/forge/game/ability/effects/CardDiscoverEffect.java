package forge.game.ability.effects;

import forge.StaticData;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardZoneTable;
import forge.game.player.Player;
import forge.game.player.PlayerCollection;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.MyRandom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

public class CardDiscoverEffect extends SpellAbilityEffect {
    static final int MAX_DYNAMIC_CANDIDATES = 128;

    @Override
    protected String getStackDescription(final SpellAbility sa) {
        final PlayerCollection players = getDefinedPlayersOrTargeted(sa);
        return players + " discover a card.";
    }

    @Override
    public void resolve(final SpellAbility sa) {
        final Card host = sa.getHostCard();
        final String source = sa.getParamOrDefault("Source", "CardDatabase");
        final String valid = sa.getParamOrDefault("ValidCards", "Card");
        final int optionCount = AbilityUtils.calculateAmount(host,
                sa.getParamOrDefault("OptionCount", "3"), sa);
        final ZoneType destination = ZoneType.smartValueOf(sa.getParamOrDefault("Destination", "Hand"));
        final String[] validRestrictions = valid.split(",");

        if (optionCount <= 0 || destination == null) {
            return;
        }

        for (final Player player : getDefinedPlayersOrTargeted(sa)) {
            if (player == null || !player.isInGame()) {
                continue;
            }

            final Random random = MyRandom.getRandom();
            final List<Card> options;
            if (source.equalsIgnoreCase("CardDatabase")) {
                final CardDiscoverCandidateFilter filter = CardDiscoverCandidateFilter.compile(valid, host, sa);
                options = selectDatabaseOptions(
                        StaticData.instance().getCommonCards().getUniqueCards(),
                        filter, optionCount, MAX_DYNAMIC_CANDIDATES, random,
                        paperCard -> Card.fromPaperCard(paperCard, player),
                        card -> card.isValid(validRestrictions, player, host, sa), true);
                if (!filter.isComplete() && options.size() < optionCount) {
                    System.err.println("CardDiscoverEffect exhausted its dynamic candidate budget for ValidCards$ "
                            + valid + " (" + filter.getCapability() + "); returning "
                            + options.size() + " option(s).");
                }
            } else {
                final CardCollection candidates = buildZoneCandidates(sa, source);
                final CardCollection validCandidates = CardLists.getValidCards(candidates, valid,
                        player, host, sa);
                options = selectUniqueOptions(validCandidates, optionCount, random);
            }
            if (options.isEmpty()) {
                continue;
            }

            final ZoneType origin = sourceZone(source);
            final Card chosen = player.getController().chooseSingleCardForZoneChange(destination,
                    Collections.singletonList(origin), sa, new CardCollection(options), null,
                    "Choose a card to discover", false, player);
            if (chosen == null) {
                continue;
            }

            final Game game = player.getGame();
            final Map<AbilityKey, Object> moveParams = AbilityKey.newMap();
            moveParams.put(AbilityKey.LastStateBattlefield, sa.getLastStateBattlefield());
            moveParams.put(AbilityKey.LastStateGraveyard, sa.getLastStateGraveyard());
            final Card moved = game.getAction().moveTo(player.getZone(destination), chosen, sa, moveParams);
            if (moved != null && moved.getZone() != null) {
                recordMovedCard(host, sa, destination, moved, sa.hasParam("RememberChosen"));
                final CardZoneTable table = new CardZoneTable();
                table.put(origin, moved.getZone().getZoneType(), moved);
                table.triggerChangesZoneAll(game, sa);
            }
        }
    }

    static void recordMovedCard(final Card host, final SpellAbility sa,
            final ZoneType destination, final Card moved, final boolean shouldRemember) {
        if (ZoneType.Exile.equals(destination)) {
            handleExiledWith(moved, sa);
        }
        rememberChosen(host, shouldRemember, moved);
    }

    static void rememberChosen(final Card host, final boolean shouldRemember, final Card chosen) {
        if (shouldRemember && chosen != null) {
            host.addRemembered(chosen);
        }
    }

    static CardCollection buildZoneCandidates(final SpellAbility sa,
            final String source) {
        final CardCollection candidates = new CardCollection();
        final ZoneType sourceZone = sourceZone(source);
        if (ZoneType.Library.equals(sourceZone) || ZoneType.Sideboard.equals(sourceZone)) {
            final List<Player> owners = AbilityUtils.getDefinedPlayers(sa.getHostCard(),
                    sa.getParamOrDefault("SourceController", "You"), sa);
            for (final Player owner : owners) {
                if (owner != null && owner.isInGame()) {
                    candidates.addAll(owner.getCardsIn(sourceZone));
                }
            }
        }
        return candidates;
    }

    static ZoneType sourceZone(final String source) {
        if ("Library".equalsIgnoreCase(source)) {
            return ZoneType.Library;
        }
        if ("Sideboard".equalsIgnoreCase(source)) {
            return ZoneType.Sideboard;
        }
        return ZoneType.None;
    }

    static List<Card> selectDatabaseOptions(final Iterable<PaperCard> paperCards,
            final CardDiscoverCandidateFilter filter, final int limit, final int dynamicBudget,
            final Random random, final Function<PaperCard, Card> cardFactory,
            final Predicate<Card> exactValidator) {
        return selectDatabaseOptions(paperCards, filter, limit, dynamicBudget, random,
                cardFactory, exactValidator, false);
    }

    private static List<Card> selectDatabaseOptions(final Iterable<PaperCard> paperCards,
            final CardDiscoverCandidateFilter filter, final int limit, final int dynamicBudget,
            final Random random, final Function<PaperCard, Card> cardFactory,
            final Predicate<Card> exactValidator, final boolean uniqueNamesGuaranteed) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        final int materializationLimit = filter.isComplete()
                ? limit : Math.max(0, dynamicBudget);
        if (materializationLimit == 0) {
            return Collections.emptyList();
        }

        final List<PaperCard> sampled = reservoirSampleUnique(
                paperCards, filter, materializationLimit, random, uniqueNamesGuaranteed);
        final List<Card> options = new ArrayList<>(Math.min(limit, sampled.size()));
        final Set<String> selectedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (final PaperCard paperCard : sampled) {
            final Card candidate = cardFactory.apply(paperCard);
            if (candidate == null || !exactValidator.test(candidate) || candidate.getName() == null) {
                continue;
            }
            if (selectedNames.add(candidate.getName())) {
                options.add(candidate);
                if (options.size() == limit) {
                    break;
                }
            }
        }
        return options;
    }

    private static List<PaperCard> reservoirSampleUnique(final Iterable<PaperCard> paperCards,
            final CardDiscoverCandidateFilter filter, final int limit, final Random random,
            final boolean uniqueNamesGuaranteed) {
        final List<PaperCard> sample = new ArrayList<>(limit);
        final Set<String> seenNames = uniqueNamesGuaranteed ? null
                : new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        int eligibleCount = 0;
        for (final PaperCard paperCard : paperCards) {
            if (!filter.matches(paperCard) || paperCard.getName() == null
                    || (seenNames != null && !seenNames.add(paperCard.getName()))) {
                continue;
            }

            eligibleCount++;
            if (sample.size() < limit) {
                sample.add(paperCard);
            } else {
                final int replacement = random.nextInt(eligibleCount);
                if (replacement < limit) {
                    sample.set(replacement, paperCard);
                }
            }
        }
        return sample;
    }

    static List<Card> selectUniqueOptions(final Iterable<Card> candidates, final int limit,
            final Random random) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        final Map<String, Card> byName = new LinkedHashMap<>();
        for (final Card card : candidates) {
            if (card != null && card.getName() != null) {
                byName.putIfAbsent(card.getName().toLowerCase(Locale.ROOT), card);
            }
        }
        final List<Card> options = new ArrayList<>(byName.values());
        Collections.shuffle(options, random);
        if (options.size() > limit) {
            return new ArrayList<>(options.subList(0, limit));
        }
        return options;
    }
}
