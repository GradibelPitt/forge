/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package forge.game;

import forge.card.CardStateName;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardState;
import forge.game.player.Player;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.PlayerZoneBattlefield;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.util.Visitor;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Game-scoped index of cards which can contribute a continuous static ability
 * (or one of the legacy static commands) to
 * {@link GameAction#checkStaticAbilities()}.
 *
 * <p>The old static pass walked every card in every library on every zone
 * change. Most cards can never contribute to that pass. This index pays for
 * one bootstrap walk, then follows real game-zone membership and dynamic
 * static-ability creation. Candidate removal is performed only at the start of
 * a static pass, after old layer effects have been cleared. That timing can
 * distinguish a truly removed ability from one temporarily suppressed by
 * another continuous effect, without permanently retaining temporary grants.</p>
 *
 * <p>Candidate locations are maintained on zone mutations. A snapshot sorts
 * only the candidate set into the exact order of the former full scan; it
 * never walks a library to recover ordering. This matters for equal-timestamp
 * continuous effects and for legacy static commands, which are not otherwise
 * sorted.</p>
 *
 * <p>This class deliberately does not cache the result of a static pass. A
 * zone/controller/state change can alter continuous effects even when the set
 * of source cards is unchanged.</p>
 */
final class ContinuousStaticAbilitySourceIndex {
    private static final long MODE_ORDER_GAP = 1L << 32;
    static final int MAX_PENDING_MODE_RECHECKS = 65_536;
    static final int MAX_PENDING_MODE_RETRIES_PER_QUERY = 256;
    static final int MAX_PENDING_MODE_BACKOFF_SHIFT = 16;
    private static final int OVERFLOW_PROMOTION_INTERVAL = 64;
    private static final Comparator<ModeLocation> MODE_LOCATION_ORDER =
            Comparator.comparingInt((ModeLocation location) ->
                            modeZoneOrder(location.container.rank))
                    .thenComparingInt(location -> location.playerOrder)
                    .thenComparingLong(location -> location.order)
                    .thenComparingLong(location -> location.sequence);
    private static final Comparator<PendingModeRecheck> PENDING_MODE_ORDER =
            Comparator.comparingLong((PendingModeRecheck pending) ->
                            pending.nextRetryEpoch)
                    .thenComparingLong(pending -> pending.sequence);
    private static final int GRAVEYARD = 0;
    private static final int HAND = 1;
    private static final int LIBRARY = 2;
    private static final int BATTLEFIELD = 3;
    private static final int MELDED = 4;
    private static final int EXILE = 5;
    private static final int COMMAND = 6;
    private static final int SCHEME_DECK = 7;
    private static final int PLANAR_DECK = 8;
    private static final int ATTRACTION_DECK = 9;
    private static final int JUNKYARD = 10;
    private static final int CONTRAPTION_DECK = 11;
    private static final int SIDEBOARD = 12;
    private static final int INBOUND = 13;
    private static final int STACK = 14;
    private static final int[] STATIC_MODE_RANKS = {
            GRAVEYARD, BATTLEFIELD, EXILE, COMMAND, STACK
    };
    private static final ModeLocation GRAVEYARD_MODE_LOWER =
            modeLowerBound(GRAVEYARD);
    private static final ModeLocation BATTLEFIELD_MODE_LOWER =
            modeLowerBound(BATTLEFIELD);
    private static final ModeLocation EXILE_MODE_LOWER =
            modeLowerBound(EXILE);
    private static final ModeLocation COMMAND_MODE_LOWER =
            modeLowerBound(COMMAND);
    private static final ModeLocation STACK_MODE_LOWER =
            modeLowerBound(STACK);

    private final Game game;
    private final int maxPendingModeRechecks;
    private final int maxPendingModeRetriesPerQuery;
    private final int maxPendingModeBackoffShift;
    private Map<IdentityKey, LiveEntry> liveCards = new LinkedHashMap<>();
    private Map<Integer, List<LiveEntry>> liveCardsById =
            new LinkedHashMap<>();
    private Map<IdentityKey, CandidateEntry> candidates =
            new LinkedHashMap<>();
    private final Map<IdentityKey, Card> pendingRecheck =
            new LinkedHashMap<>();
    private Map<IdentityKey, ModeLocation> modeLocations =
            new LinkedHashMap<>();
    private Map<StaticAbilityMode, NavigableSet<ModeLocation>> modeCandidates =
            new EnumMap<>(StaticAbilityMode.class);
    private Map<ContainerKey, NavigableSet<ModeLocation>>
            modeLocationsByContainer = new LinkedHashMap<>();
    private final Map<StaticAbilityMode, Map<Integer, ModeRankSnapshot>>
            modeSnapshots = new EnumMap<>(StaticAbilityMode.class);
    private final Map<StaticAbilityMode, Integer> dirtyModeSnapshotRanks =
            new EnumMap<>(StaticAbilityMode.class);
    private final Map<IdentityKey, PendingModeRecheck> pendingModeRechecks =
            new LinkedHashMap<>();
    private final NavigableSet<PendingModeRecheck> pendingModeSchedule =
            new TreeSet<>(PENDING_MODE_ORDER);
    private final Map<IdentityKey, PendingModeRecheck>
            overflowModeRechecks = new LinkedHashMap<>();
    private final Map<Integer, Integer> stablePlayerOrderById =
            new LinkedHashMap<>();
    private final PotentialSourceDetector potentialSourceDetector;

    private boolean initialized;
    private long nextSequence;
    private long nextModeSequence;
    private long nextPendingModeSequence;
    private long modeQueryEpoch;
    private int nextStablePlayerOrder;

    // Package-private diagnostics used by regression/performance tests. They
    // are counters only and do not participate in engine decisions.
    private long bootstrapCardVisits;
    private long candidateCardVisits;
    private long checkInvocations;
    private long bootstrapPotentialChecks;
    private long afterClearPotentialChecks;
    private long preListPotentialChecks;
    private long currentPotentialChecks;
    private long modeDiscoveryCardVisits;
    private long modeSourceVisits;
    private long modeCandidateExaminations;
    private long modeOrderRelabelCardVisits;
    private long modeAppendInsertions;
    private long modeClassificationFailures;
    private long modeTailRecomputeCardVisits;
    private long modeSnapshotLocationCopies;
    private long modeSnapshotRebuilds;
    private long pendingModeRetryAttempts;
    private long pendingModeOverflowDrops;
    private long pendingModeOverflowEvictions;
    private long modeQueryDegradedSkips;

    ContinuousStaticAbilitySourceIndex(final Game game) {
        this(game, ContinuousStaticAbilitySourceIndex::hasPotentialSource,
                MAX_PENDING_MODE_RECHECKS,
                MAX_PENDING_MODE_RETRIES_PER_QUERY,
                MAX_PENDING_MODE_BACKOFF_SHIFT);
    }

    ContinuousStaticAbilitySourceIndex(final Game game,
            final PotentialSourceDetector potentialSourceDetector) {
        this(game, potentialSourceDetector, MAX_PENDING_MODE_RECHECKS,
                MAX_PENDING_MODE_RETRIES_PER_QUERY,
                MAX_PENDING_MODE_BACKOFF_SHIFT);
    }

    ContinuousStaticAbilitySourceIndex(final Game game,
            final PotentialSourceDetector potentialSourceDetector,
            final int maxPendingModeRechecks,
            final int maxPendingModeRetriesPerQuery,
            final int maxPendingModeBackoffShift) {
        this.game = game;
        this.potentialSourceDetector = potentialSourceDetector;
        this.maxPendingModeRechecks = Math.max(1, maxPendingModeRechecks);
        this.maxPendingModeRetriesPerQuery = Math.max(1,
                maxPendingModeRetriesPerQuery);
        this.maxPendingModeBackoffShift = Math.max(0, Math.min(30,
                maxPendingModeBackoffShift));
    }

    synchronized void cardEntered(final Card card, final Zone zone,
            final int position) {
        if (!initialized || card == null) {
            return;
        }
        final ContainerKey container = containerFor(zone);
        if (container == null || !isCurrentPlayer(container.player)) {
            return;
        }
        shiftForInsertion(container, position);
        addLiveCard(card, container, position);
    }

    synchronized void cardLeft(final Card card, final Zone zone,
            final int position) {
        if (!initialized || card == null) {
            return;
        }
        final ContainerKey container = containerFor(zone);
        if (container == null) {
            return;
        }
        final IdentityKey key = new IdentityKey(card);
        removeModeLocation(key);
        final LiveEntry removed = removeLiveCard(key);
        if (removed == null) {
            return;
        }
        candidates.remove(key);
        pendingRecheck.remove(key);
        shiftForRemoval(container, position);
    }

    synchronized void zoneContentsCleared(final Zone zone,
            final Iterable<Card> cards) {
        final ContainerKey container = containerFor(zone);
        if (!initialized || container == null) {
            return;
        }
        for (final Card card : cards) {
            final IdentityKey key = new IdentityKey(card);
            removeModeLocation(key);
            removeLiveCard(key);
            candidates.remove(key);
            pendingRecheck.remove(key);
        }
        modeLocationsByContainer.remove(container);
    }

    synchronized void zoneContentsSet(final Zone zone,
            final Iterable<Card> cards) {
        if (!initialized) {
            return;
        }
        final ContainerKey container = containerFor(zone);
        if (container == null || !isCurrentPlayer(container.player)) {
            return;
        }
        int position = 0;
        for (final Card card : cards) {
            addLiveCard(card, container, position++, false);
        }
        if (isStaticModeContainer(container)) {
            try {
                rebuildModeContainer(container, cards);
            } catch (final RuntimeException ignored) {
                // The authoritative Zone mutation has already succeeded.
                // Keep the live-card entry and an empty/previously consistent
                // mode bucket; a later corrected trait advertises itself via
                // markPotentialStaticAbilityModes.
                modeClassificationFailures++;
            }
        }
    }

    synchronized void zoneOrderChanged(final Zone zone,
            final Iterable<Card> cards) {
        if (!initialized) {
            return;
        }
        final ContainerKey container = containerFor(zone);
        if (container == null) {
            return;
        }
        refreshContainerPositions(container, cards);
        if (isStaticModeContainer(container)) {
            relabelModeContainer(container, cards);
        }
    }

    synchronized void meldedCardEntered(final Player player, final Card card,
            final int position) {
        specialCardEntered(player, MELDED, card, position);
    }

    synchronized void meldedCardLeft(final Player player, final Card card,
            final int position) {
        specialCardLeft(player, MELDED, card, position);
    }

    synchronized void inboundCardEntered(final Player player, final Card card,
            final int position) {
        specialCardEntered(player, INBOUND, card, position);
    }

    synchronized void inboundCardLeft(final Player player, final Card card,
            final int position) {
        specialCardLeft(player, INBOUND, card, position);
    }

    synchronized void markPotentialSource(final Card card) {
        if (!initialized || card == null) {
            return;
        }
        final IdentityKey key = new IdentityKey(card);
        if (liveCards.containsKey(key)) {
            pendingRecheck.put(key, card);
        }
    }

    synchronized void recheckPotentialSource(final Card card) {
        if (!initialized || card == null) {
            return;
        }
        final IdentityKey key = new IdentityKey(card);
        if (liveCards.containsKey(key)) {
            pendingRecheck.put(key, card);
        }
    }

    synchronized void markPotentialStaticAbilityModes(final Card card,
            final Set<StaticAbilityMode> modes) {
        if (!initialized || card == null || modes == null) {
            return;
        }
        final IdentityKey key = new IdentityKey(card);
        final LiveEntry live = liveCards.get(key);
        if (live == null || !isStaticModeContainer(live.container)) {
            return;
        }
        final EnumSet<StaticAbilityMode> indexedModes;
        try {
            indexedModes = copyIndexedModes(modes);
        } catch (final RuntimeException ignored) {
            modeClassificationFailures++;
            queuePendingModeRecheck(card, live,
                    modeLocations.containsKey(key) ? 0
                            : findCurrentPosition(card, live.container),
                    false);
            return;
        }
        if (indexedModes.isEmpty()) {
            return;
        }
        ModeLocation location = modeLocations.get(key);
        if (location == null) {
            location = ensureModeLocation(card, live,
                    findCurrentPosition(card, live.container));
        }
        for (final StaticAbilityMode mode : indexedModes) {
            addModeCandidate(location, mode);
        }
        resetPendingModeRecheck(key);
    }

    synchronized void reconcileIntrinsicStaticAbilityModes(final Card card,
            final Set<StaticAbilityMode> previousModes,
            final Set<StaticAbilityMode> currentModes) {
        if (!initialized || card == null || previousModes == null
                || currentModes == null) {
            return;
        }
        final IdentityKey key = new IdentityKey(card);
        final LiveEntry live = liveCards.get(key);
        if (live == null || !isStaticModeContainer(live.container)) {
            return;
        }
        final EnumSet<StaticAbilityMode> previous = copyIndexedModes(
                previousModes);
        final EnumSet<StaticAbilityMode> current = copyIndexedModes(
                currentModes);
        if (previous.equals(current)) {
            return;
        }

        ModeLocation location = modeLocations.get(key);
        if (location != null) {
            for (final StaticAbilityMode removed : previous) {
                if (!current.contains(removed)
                        && location.structuralModes.remove(removed)) {
                    markModeSnapshotDirty(removed,
                            location.container.rank);
                }
            }
        }
        if (!current.isEmpty()) {
            if (location == null) {
                location = ensureModeLocation(card, live,
                        findCurrentPosition(card, live.container));
            }
            for (final StaticAbilityMode added : current) {
                addModeCandidate(location, added);
            }
        }
        if (location == null) {
            return;
        }

        final EnumSet<StaticAbilityMode> allCurrentModes;
        try {
            allCurrentModes = collectCurrentModes(card);
        } catch (final RuntimeException ignored) {
            modeClassificationFailures++;
            queuePendingModeRecheck(card, live, 0, false);
            return;
        }
        for (final StaticAbilityMode indexed
                : EnumSet.copyOf(location.modes)) {
            if (!allCurrentModes.contains(indexed)) {
                removeModeCandidate(location, indexed);
            }
        }
        for (final StaticAbilityMode indexed : allCurrentModes) {
            addModeCandidate(location, indexed);
        }
        resetPendingModeRecheck(key);
    }

    synchronized void playerRemoved(final Player player) {
        if (!initialized || player == null) {
            return;
        }
        final var iterator = liveCards.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<IdentityKey, LiveEntry> item = iterator.next();
            final LiveEntry live = item.getValue();
            if (live.container.player != player) {
                continue;
            }
            removeModeLocation(item.getKey());
            iterator.remove();
            candidates.remove(item.getKey());
            pendingRecheck.remove(item.getKey());
            removeLiveCardById(live);
        }
        modeLocationsByContainer.keySet().removeIf(
                container -> container.player == player);
    }

    synchronized CardCollection snapshotAfterStaticEffectsCleared(
            final CardCollectionView preList) {
        ensureInitialized();

        // GameAction clears the previous layer effects immediately before this
        // call. Revalidate only at this safe point: intrinsic abilities hidden
        // by RemoveAllAbilities are visible again, while temporary grants from
        // the prior pass are gone.
        // Detection can inspect dynamically authored traits and may fail. Do
        // every fallible read against a proposed map first, so one malformed
        // card cannot prune earlier candidates or consume their ordering
        // sequence before the caller can repair and retry.
        final Map<IdentityKey, CandidateEntry> proposedCandidates =
                new LinkedHashMap<>();
        long proposedNextSequence = nextSequence;
        for (final Map.Entry<IdentityKey, CandidateEntry> item
                : new ArrayList<>(candidates.entrySet())) {
            if (liveCards.containsKey(item.getKey())
                    && detectAfterClear(item.getValue().card)) {
                proposedCandidates.put(item.getKey(), item.getValue());
            }
        }
        final List<Map.Entry<IdentityKey, Card>> pendingSnapshot =
                new ArrayList<>(pendingRecheck.entrySet());
        for (final Map.Entry<IdentityKey, Card> item : pendingSnapshot) {
            final LiveEntry live = liveCards.get(item.getKey());
            if (live != null && detectAfterClear(item.getValue())) {
                if (!proposedCandidates.containsKey(item.getKey())) {
                    proposedCandidates.put(item.getKey(),
                            new CandidateEntry(item.getValue(), live.container,
                                    findCurrentPosition(item.getValue(),
                                            live.container),
                                    proposedNextSequence++));
                }
            }
        }

        final IdentityHashMap<Player, Integer> playerOrder =
                new IdentityHashMap<>();
        int playerIndex = 0;
        for (final Player player : game.getPlayers()) {
            playerOrder.put(player, playerIndex++);
        }

        final List<CandidateEntry> ordered = new ArrayList<>();
        for (final CandidateEntry candidate : proposedCandidates.values()) {
            if (candidate.container.player == null
                    || playerOrder.containsKey(candidate.container.player)) {
                ordered.add(candidate);
            }
        }

        // A pre-check replaces a live object with its equal-ID LKI. The live
        // object may no longer be a normal candidate, so merge a temporary
        // entry at that live object's exact old-scan location. Do not append
        // detached LKI which the former full game traversal could not see.
        if (preList != null) {
            for (final Card card : preList) {
                if (detectPreList(card)) {
                    final LiveEntry live = latestLiveCardById(card.getId());
                    if (live != null && (live.container.player == null
                            || playerOrder.containsKey(live.container.player))
                            && !proposedCandidates.containsKey(
                            new IdentityKey(live.card))) {
                        ordered.add(new CandidateEntry(live.card,
                                live.container,
                                findCurrentPosition(live.card, live.container),
                                proposedNextSequence++));
                    }
                }
            }
        }

        ordered.sort((left, right) -> compareLocations(left, right,
                playerOrder));

        final CardCollection result = new CardCollection();
        for (final CandidateEntry candidate : ordered) {
            if (liveCards.containsKey(new IdentityKey(candidate.card))
                    && (candidate.container.player == null
                    || playerOrder.containsKey(candidate.container.player))) {
                result.add(candidate.card);
            }
        }

        candidates = proposedCandidates;
        nextSequence = proposedNextSequence;
        for (final Map.Entry<IdentityKey, Card> item : pendingSnapshot) {
            pendingRecheck.remove(item.getKey(), item.getValue());
        }
        candidateCardVisits += result.size();
        return result;
    }

    synchronized void refresh(final Iterable<Card> scannedSources,
            final Iterable<Card> abilityLayerAffectedCards) {
        collectIdentity(pendingRecheck, scannedSources);
        collectIdentity(pendingRecheck, abilityLayerAffectedCards);
        final Map<IdentityKey, Card> modeRefresh = new LinkedHashMap<>();
        collectIdentity(modeRefresh, scannedSources);
        collectIdentity(modeRefresh, abilityLayerAffectedCards);
        for (final Map.Entry<IdentityKey, Card> item
                : modeRefresh.entrySet()) {
            final LiveEntry live = liveCards.get(item.getKey());
            if (live != null && isStaticModeContainer(live.container)) {
                try {
                    indexCurrentStaticAbilityModes(item.getValue(), live);
                } catch (final RuntimeException ignored) {
                    modeClassificationFailures++;
                    queuePendingModeRecheck(item.getValue(), live,
                            modeLocations.containsKey(item.getKey()) ? 0
                                    : findCurrentPosition(item.getValue(),
                                    live.container),
                            false);
                }
            }
        }
    }

    synchronized void recordCheckInvocation() {
        checkInvocations++;
    }

    private boolean detectBootstrap(final BootstrapState bootstrap,
            final Card card) {
        bootstrap.bootstrapPotentialChecks++;
        return potentialSourceDetector.test(card);
    }

    private boolean detectAfterClear(final Card card) {
        afterClearPotentialChecks++;
        return potentialSourceDetector.test(card);
    }

    private boolean detectPreList(final Card card) {
        preListPotentialChecks++;
        return potentialSourceDetector.test(card);
    }

    private boolean detectCurrent(final Card card) {
        currentPotentialChecks++;
        return potentialSourceDetector.test(card);
    }

    synchronized void resetDiagnostics() {
        bootstrapCardVisits = 0;
        candidateCardVisits = 0;
        checkInvocations = 0;
        bootstrapPotentialChecks = 0;
        afterClearPotentialChecks = 0;
        preListPotentialChecks = 0;
        currentPotentialChecks = 0;
        modeDiscoveryCardVisits = 0;
        modeSourceVisits = 0;
        modeCandidateExaminations = 0;
        modeOrderRelabelCardVisits = 0;
        modeAppendInsertions = 0;
        modeClassificationFailures = 0;
        modeTailRecomputeCardVisits = 0;
        modeSnapshotLocationCopies = 0;
        modeSnapshotRebuilds = 0;
        pendingModeRetryAttempts = 0;
        pendingModeOverflowDrops = 0;
        pendingModeOverflowEvictions = 0;
        modeQueryDegradedSkips = 0;
    }

    synchronized Diagnostics diagnostics() {
        return new Diagnostics(bootstrapCardVisits, candidateCardVisits,
                checkInvocations, liveCards.size(), candidates.size(),
                bootstrapPotentialChecks, afterClearPotentialChecks,
                preListPotentialChecks, currentPotentialChecks,
                modeDiscoveryCardVisits, modeSourceVisits,
                modeCandidateExaminations, modeOrderRelabelCardVisits,
                modeAppendInsertions,
                modeClassificationFailures, modeTailRecomputeCardVisits,
                modeSnapshotLocationCopies, modeSnapshotRebuilds,
                pendingModeRechecks.size() + overflowModeRechecks.size(),
                pendingModeRetryAttempts, pendingModeOverflowDrops,
                overflowModeRechecks.size(), pendingModeOverflowEvictions,
                modeQueryDegradedSkips);
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        // Build entirely into local state. Card/trait inspection can execute
        // user-authored code and can fail; publishing partially-built maps
        // would create ghost candidates and callbacks are intentionally ignored
        // while initialized is false. A retry therefore rescans the then-current
        // zones and atomically replaces all three maps only after success.
        final BootstrapState bootstrap = new BootstrapState();
        for (final Player player : game.getPlayers()) {
            bootstrapZone(bootstrap, player.getZone(ZoneType.Graveyard));
            bootstrapZone(bootstrap, player.getZone(ZoneType.Hand));
            bootstrapZone(bootstrap, player.getZone(ZoneType.Library));
            bootstrapZone(bootstrap, player.getZone(ZoneType.Battlefield));
            bootstrapSpecial(bootstrap, player,
                    ((PlayerZoneBattlefield) player.getZone(
                            ZoneType.Battlefield)).getMeldedCards(), MELDED);
            bootstrapZone(bootstrap, player.getZone(ZoneType.Exile));
            for (final ZoneType zone : ZoneType.PART_OF_COMMAND_ZONE) {
                bootstrapZone(bootstrap, player.getZone(zone));
            }
            bootstrapZone(bootstrap, player.getZone(ZoneType.Sideboard));
            bootstrapSpecial(bootstrap, player, player.getInboundTokens(),
                    INBOUND);
        }
        bootstrapZone(bootstrap, game.getStackZone());

        liveCards = bootstrap.liveCards;
        liveCardsById = bootstrap.liveCardsById;
        candidates = bootstrap.candidates;
        modeLocations = bootstrap.modeLocations;
        modeCandidates = bootstrap.modeCandidates;
        modeLocationsByContainer = bootstrap.modeLocationsByContainer;
        initializeModeSnapshots();
        pendingModeRechecks.clear();
        pendingModeSchedule.clear();
        overflowModeRechecks.clear();
        pendingRecheck.clear();
        nextSequence = bootstrap.nextSequence;
        nextModeSequence = bootstrap.nextModeSequence;
        bootstrapCardVisits += bootstrap.cardVisits;
        bootstrapPotentialChecks += bootstrap.bootstrapPotentialChecks;
        modeDiscoveryCardVisits += bootstrap.modeDiscoveryCardVisits;
        initialized = true;
    }

    private void initializeModeSnapshots() {
        modeSnapshots.clear();
        dirtyModeSnapshotRanks.clear();
        for (final Map.Entry<StaticAbilityMode,
                NavigableSet<ModeLocation>> item
                : modeCandidates.entrySet()) {
            final Map<Integer, List<ModeLocation>> mutable =
                    new LinkedHashMap<>();
            for (final int rank : STATIC_MODE_RANKS) {
                mutable.put(rank, new ArrayList<>());
            }
            for (final ModeLocation location : item.getValue()) {
                mutable.computeIfAbsent(location.container.rank,
                        ignored -> new ArrayList<>()).add(location);
            }
            final Map<Integer, ModeRankSnapshot> immutable =
                    new LinkedHashMap<>();
            for (final Map.Entry<Integer, List<ModeLocation>> rank
                    : mutable.entrySet()) {
                final List<ModeLocation> copied = List.copyOf(rank.getValue());
                immutable.put(rank.getKey(), buildModeRankSnapshot(
                        item.getKey(), copied));
                modeSnapshotLocationCopies += copied.size();
            }
            modeSnapshots.put(item.getKey(), immutable);
        }
    }

    private void bootstrapZone(final BootstrapState bootstrap,
            final Zone zone) {
        final ContainerKey container = containerFor(zone);
        if (container == null) {
            return;
        }
        int position = 0;
        for (final Card card : zone.getCards(false)) {
            bootstrap.cardVisits++;
            addBootstrapCard(bootstrap, card, container, position++);
        }
    }

    private void bootstrapSpecial(final BootstrapState bootstrap,
            final Player player,
            final Iterable<Card> cards, final int containerRank) {
        final ContainerKey container = new ContainerKey(player, containerRank);
        int position = 0;
        for (final Card card : cards) {
            bootstrap.cardVisits++;
            addBootstrapCard(bootstrap, card, container, position++);
        }
    }

    private void addBootstrapCard(final BootstrapState bootstrap,
            final Card card, final ContainerKey container,
            final int position) {
        final IdentityKey key = new IdentityKey(card);
        final LiveEntry live = new LiveEntry(card, container);
        if (bootstrap.liveCards.putIfAbsent(key, live) != null) {
            return;
        }
        bootstrap.liveCardsById.computeIfAbsent(card.getId(), ignored ->
                new ArrayList<>()).add(live);
        if (detectBootstrap(bootstrap, card)) {
            bootstrap.candidates.put(key, new CandidateEntry(card, container,
                    position, bootstrap.nextSequence++));
        }
        if (isStaticModeContainer(container)) {
            addBootstrapModeCard(bootstrap, card, container, position);
        }
    }

    private void addBootstrapModeCard(final BootstrapState bootstrap,
            final Card card, final ContainerKey container,
            final int position) {
        final EnumSet<StaticAbilityMode> modes = collectBootstrapModes(
                bootstrap, card);
        if (modes.isEmpty()) {
            return;
        }
        final ModeLocation location = new ModeLocation(card, container,
                playerOrder(container.player),
                (position + 1L) * MODE_ORDER_GAP,
                bootstrap.nextModeSequence++);
        bootstrap.modeLocations.put(new IdentityKey(card), location);
        bootstrap.modeLocationsByContainer.computeIfAbsent(container,
                ignored -> newModeSet()).add(location);
        for (final StaticAbilityMode mode : modes) {
            if (mode == StaticAbilityMode.Continuous
                    || !location.modes.add(mode)) {
                continue;
            }
            if (card.getIntrinsicStaticAbilityModes().contains(mode)) {
                location.structuralModes.add(mode);
            }
            bootstrap.modeCandidates.computeIfAbsent(mode,
                    ignored -> newModeSet()).add(location);
        }
    }

    private void addLiveCard(final Card card, final ContainerKey container,
            final int position) {
        addLiveCard(card, container, position, true);
    }

    private void addLiveCard(final Card card, final ContainerKey container,
            final int position, final boolean indexModes) {
        final IdentityKey key = new IdentityKey(card);
        final LiveEntry live = new LiveEntry(card, container);
        if (liveCards.putIfAbsent(key, live) != null) {
            return;
        }
        liveCardsById.computeIfAbsent(card.getId(), ignored ->
                new ArrayList<>()).add(live);
        pendingRecheck.put(key, card);
        if (indexModes && isStaticModeContainer(container)) {
            try {
                indexCurrentStaticAbilityModes(card, live, position);
            } catch (final RuntimeException ignored) {
                // A malformed/dynamically authored ability must not make the
                // already-committed zone entry throw or publish a partial mode
                // bucket. Retain an ordered placeholder and retry from the
                // bounded pending queue on a later mode query.
                modeClassificationFailures++;
                queuePendingModeRecheck(card, live, position, false);
            }
        }
    }

    synchronized boolean visitStaticAbilityModeSources(
            final StaticAbilityMode mode, final Visitor<Card> visitor) {
        return visitStaticAbilityModeSources(mode, STATIC_MODE_RANKS,
                visitor);
    }

    synchronized boolean visitCurrentContinuousSources(
            final Visitor<Card> visitor) {
        ensureInitialized();
        for (final Map.Entry<IdentityKey, Card> item
                : pendingRecheck.entrySet()) {
            final LiveEntry live = liveCards.get(item.getKey());
            if (live != null && detectCurrent(item.getValue())) {
                candidates.computeIfAbsent(item.getKey(), ignored ->
                        new CandidateEntry(item.getValue(), live.container,
                                findCurrentPosition(item.getValue(),
                                        live.container), nextSequence++));
            }
        }
        for (final CandidateEntry candidate : candidates.values()) {
            if (isStaticModeContainer(candidate.container)
                    && (candidate.container.player == null
                    || isCurrentPlayer(candidate.container.player))
                    && !visitor.visit(candidate.card)) {
                return false;
            }
        }
        return true;
    }

    synchronized boolean visitStaticAbilityModeSources(
            final StaticAbilityMode mode, final Iterable<ZoneType> zones,
            final Visitor<Card> visitor) {
        ensureInitialized();
        final int[] ranks = orderedUniqueModeRanks(zones);
        return visitStaticAbilityModeSources(mode, ranks, visitor);
    }

    synchronized boolean containsStaticAbilitySourceEquivalent(
            final Card card, final Iterable<ZoneType> zones) {
        ensureInitialized();
        return card != null && containsStaticAbilitySourceEquivalent(
                card.getId(), orderedUniqueModeRanks(zones));
    }

    synchronized boolean containsStaticAbilitySourceEquivalent(
            final Card card) {
        ensureInitialized();
        return card != null && containsStaticAbilitySourceEquivalent(
                card.getId(), STATIC_MODE_RANKS);
    }

    private boolean visitStaticAbilityModeSources(
            final StaticAbilityMode mode, final int[] ranks,
            final Visitor<Card> visitor) {
        ensureInitialized();
        int classificationFailures = retryPendingModeRechecks();
        final NavigableSet<ModeLocation> sources = modeCandidates.get(mode);
        if (sources == null || sources.isEmpty() || ranks.length == 0) {
            return true;
        }

        // Match CardCollection's old bounded-query semantics without copying
        // a large bucket on every early-exit query. Each mode/rank cache is an
        // immutable identity/order list rebuilt only after that exact bucket
        // changes. Capturing the rank-list references here gives this query a
        // bounded snapshot; visitor mutations dirty a future version while a
        // reentrant query gets new immutable lists.
        final List<ModeRankSnapshot> snapshot = new ArrayList<>(
                ranks.length);
        for (final int rank : ranks) {
            snapshot.add(modeSnapshot(mode, rank, sources));
        }

        if (classificationFailures >= maxPendingModeRetriesPerQuery) {
            modeQueryDegradedSkips++;
            return visitRemainingStructuralModeSources(mode, ranks, snapshot,
                    0, 0, visitor);
        }

        for (int rankIndex = 0; rankIndex < snapshot.size(); rankIndex++) {
            final ModeRankSnapshot rankSnapshot = snapshot.get(rankIndex);
            int sourceIndex = rankSnapshot.nextActiveIndex(0);
            while (sourceIndex < rankSnapshot.size()) {
                final ModeLocation source = rankSnapshot.get(sourceIndex);
                modeCandidateExaminations++;
                if (isModeCandidateQuarantined(source, mode)) {
                    rankSnapshot.setQuarantined(source, true);
                    sourceIndex = rankSnapshot.nextActiveIndex(
                            sourceIndex + 1);
                    continue;
                }
                final boolean active;
                try {
                    active = isActiveModeLocation(source, mode);
                } catch (final RuntimeException ignored) {
                    // A query-time script/classification failure is transient
                    // from the index's perspective. Skip this invocation but
                    // retain the membership and the clean snapshot so a
                    // corrected or one-shot-failing trait retries without an
                    // unbounded rebuild loop.
                    modeClassificationFailures++;
                    quarantineModeCandidate(source, mode);
                    rankSnapshot.setQuarantined(source, true);
                    classificationFailures++;
                    if (classificationFailures
                            >= maxPendingModeRetriesPerQuery) {
                        modeQueryDegradedSkips++;
                        return visitRemainingStructuralModeSources(mode,
                                ranks, snapshot, rankIndex,
                                sourceIndex + 1, visitor);
                    }
                    sourceIndex = rankSnapshot.nextActiveIndex(
                            sourceIndex + 1);
                    continue;
                }
                if (!active) {
                    removeModeCandidate(source, mode);
                    sourceIndex = rankSnapshot.nextActiveIndex(
                            sourceIndex + 1);
                    continue;
                }
                if (isModeQueryVisible(source)
                        && isFirstLegacyStaticCardForId(source, ranks)) {
                    modeSourceVisits++;
                    if (!visitor.visit(source.card)) {
                        return false;
                    }
                }
                sourceIndex = rankSnapshot.nextActiveIndex(sourceIndex + 1);
            }
        }
        return true;
    }

    private boolean visitRemainingStructuralModeSources(
            final StaticAbilityMode mode, final int[] ranks,
            final List<ModeRankSnapshot> snapshots, final int firstRank,
            final int firstIndex, final Visitor<Card> visitor) {
        for (int rankIndex = firstRank; rankIndex < snapshots.size();
                rankIndex++) {
            final ModeRankSnapshot snapshot = snapshots.get(rankIndex);
            int sourceIndex = snapshot.nextStructuralActiveIndex(
                    rankIndex == firstRank ? firstIndex : 0);
            while (sourceIndex < snapshot.size()) {
                final ModeLocation source = snapshot.get(sourceIndex);
                modeCandidateExaminations++;
                if (isModeCandidateQuarantined(source, mode)) {
                    snapshot.setQuarantined(source, true);
                } else if (!isSafeStructuralModeLocation(source, mode)) {
                    snapshot.setStructural(source, false);
                    source.structuralModes.remove(mode);
                } else if (isModeQueryVisible(source)
                        && isFirstLegacyStaticCardForId(source, ranks)) {
                    modeSourceVisits++;
                    if (!visitor.visit(source.card)) {
                        return false;
                    }
                }
                sourceIndex = snapshot.nextStructuralActiveIndex(
                        sourceIndex + 1);
            }
        }
        return true;
    }

    private boolean isSafeStructuralModeLocation(
            final ModeLocation location, final StaticAbilityMode mode) {
        final IdentityKey key = new IdentityKey(location.card);
        final LiveEntry live = liveCards.get(key);
        return modeLocations.get(key) == location
                && location.modes.contains(mode)
                && location.structuralModes.contains(mode)
                && live != null && live.container.sameAs(location.container)
                && (location.container.player == null
                || isCurrentPlayer(location.container.player))
                && location.card.getIntrinsicStaticAbilityModes()
                .contains(mode);
    }

    private ModeRankSnapshot modeSnapshot(final StaticAbilityMode mode,
            final int rank, final NavigableSet<ModeLocation> sources) {
        final Map<Integer, ModeRankSnapshot> cachedRanks =
                modeSnapshots.computeIfAbsent(mode,
                        ignored -> emptyModeRankSnapshots());
        final int rankBit = 1 << rank;
        final int dirtyRanks = dirtyModeSnapshotRanks.getOrDefault(mode, 0);
        final ModeRankSnapshot cached = cachedRanks.get(rank);
        if (cached != null && (dirtyRanks & rankBit) == 0) {
            return cached;
        }

        final List<ModeLocation> rebuilt = new ArrayList<>();
        ModeLocation source = sources.ceiling(modeLowerBoundFor(rank));
        while (source != null && source.container.rank == rank) {
            rebuilt.add(source);
            source = sources.higher(source);
        }
        final List<ModeLocation> immutable = List.copyOf(rebuilt);
        final ModeRankSnapshot rebuiltSnapshot = buildModeRankSnapshot(mode,
                immutable);
        cachedRanks.put(rank, rebuiltSnapshot);
        final int remainingDirty = dirtyRanks & ~rankBit;
        if (remainingDirty == 0) {
            dirtyModeSnapshotRanks.remove(mode);
        } else {
            dirtyModeSnapshotRanks.put(mode, remainingDirty);
        }
        modeSnapshotRebuilds++;
        modeSnapshotLocationCopies += immutable.size();
        return rebuiltSnapshot;
    }

    private void markModeSnapshotDirty(final StaticAbilityMode mode,
            final int rank) {
        final Map<Integer, ModeRankSnapshot> cachedRanks =
                modeSnapshots.computeIfAbsent(mode,
                        ignored -> emptyModeRankSnapshots());
        // Do not let a dirty cache retain cards which have left the game. An
        // in-flight query already holds its immutable list reference; removing
        // the map entry only affects future queries, which rebuild this rank.
        cachedRanks.remove(rank);
        dirtyModeSnapshotRanks.merge(mode, 1 << rank,
                (left, right) -> left | right);
    }

    private static Map<Integer, ModeRankSnapshot>
            emptyModeRankSnapshots() {
        final Map<Integer, ModeRankSnapshot> empty = new LinkedHashMap<>();
        for (final int rank : STATIC_MODE_RANKS) {
            empty.put(rank, ModeRankSnapshot.empty());
        }
        return empty;
    }

    private ModeRankSnapshot buildModeRankSnapshot(
            final StaticAbilityMode mode,
            final List<ModeLocation> locations) {
        final BitSet structural = new BitSet(locations.size());
        final BitSet quarantined = new BitSet(locations.size());
        for (int i = 0; i < locations.size(); i++) {
            final ModeLocation location = locations.get(i);
            if (location.structuralModes.contains(mode)) {
                structural.set(i);
            }
            if (isModeCandidateQuarantined(location, mode)) {
                quarantined.set(i);
            }
        }
        return new ModeRankSnapshot(locations, structural, quarantined);
    }

    private void queuePendingModeRecheck(final Card card,
            final LiveEntry live, final int position,
            final boolean fixedOrder) {
        final IdentityKey key = new IdentityKey(card);
        final ModeLocation location = fixedOrder
                ? ensureFixedModeLocation(card, live, position)
                : ensureModeLocation(card, live, position);
        final PendingModeRecheck previous = detachPendingModeRecheck(key);
        final PendingModeRecheck pending = new PendingModeRecheck(key, card,
                live.container, location, nextPendingModeSequence(), 1,
                saturatedAdd(modeQueryEpoch, 1L), true);
        if (previous != null && previous.location == location) {
            pending.quarantinedModes.addAll(previous.quarantinedModes);
        }
        admitPendingModeRecheck(pending);
    }

    private void resetPendingModeRecheck(final IdentityKey key) {
        final PendingModeRecheck pending = detachPendingModeRecheck(key);
        if (pending == null) {
            return;
        }
        pending.failures = 1;
        pending.nextRetryEpoch = saturatedAdd(modeQueryEpoch, 1L);
        admitPendingModeRecheck(pending);
    }

    private void removePendingModeRecheck(final IdentityKey key) {
        detachPendingModeRecheck(key);
    }

    private PendingModeRecheck detachPendingModeRecheck(
            final IdentityKey key) {
        final PendingModeRecheck pending = pendingModeRechecks.remove(key);
        if (pending != null) {
            pendingModeSchedule.remove(pending);
            return pending;
        }
        return overflowModeRechecks.remove(key);
    }

    private boolean hasPendingModeRecheck(final IdentityKey key) {
        return pendingModeRechecks.containsKey(key)
                || overflowModeRechecks.containsKey(key);
    }

    private boolean isModeCandidateQuarantined(
            final ModeLocation location, final StaticAbilityMode mode) {
        final IdentityKey key = new IdentityKey(location.card);
        PendingModeRecheck pending = pendingModeRechecks.get(key);
        if (pending == null) {
            pending = overflowModeRechecks.get(key);
        }
        return pending != null && pending.location == location
                && pending.quarantinedModes.contains(mode);
    }

    private void setModeSnapshotQuarantined(
            final ModeLocation location, final StaticAbilityMode mode,
            final boolean quarantined) {
        final Map<Integer, ModeRankSnapshot> ranks = modeSnapshots.get(mode);
        if (ranks == null) {
            return;
        }
        final ModeRankSnapshot snapshot = ranks.get(location.container.rank);
        if (snapshot != null) {
            snapshot.setQuarantined(location, quarantined);
        }
    }

    private void admitPendingModeRecheck(
            final PendingModeRecheck pending) {
        if (pendingModeRechecks.size() >= maxPendingModeRechecks) {
            final PendingModeRecheck victim = pendingModeSchedule.isEmpty()
                    ? pendingModeRechecks.values().iterator().next()
                    : pendingModeSchedule.last();
            pendingModeRechecks.remove(victim.key);
            pendingModeSchedule.remove(victim);
            placeOverflowModeRecheck(victim);
        }
        pendingModeRechecks.put(pending.key, pending);
        pendingModeSchedule.add(pending);
    }

    private void placeOverflowModeRecheck(
            final PendingModeRecheck pending) {
        pendingModeOverflowEvictions++;
        if (overflowModeRechecks.size() >= maxPendingModeRechecks) {
            final var iterator = overflowModeRechecks.entrySet().iterator();
            final PendingModeRecheck dropped = iterator.next().getValue();
            iterator.remove();
            pendingModeOverflowDrops++;
            releaseDroppedModeRecheck(dropped);
        }
        overflowModeRechecks.put(pending.key, pending);
    }

    private void releaseDroppedModeRecheck(
            final PendingModeRecheck dropped) {
        for (final StaticAbilityMode mode : dropped.quarantinedModes) {
            reactivateModeCandidate(dropped.location, mode);
        }
        if (dropped.discoveryRequired && dropped.location.modes.isEmpty()) {
            removeModeLocation(dropped.key);
        }
    }

    private void promoteOverflowModeRecheck() {
        if (overflowModeRechecks.isEmpty()
                || modeQueryEpoch % OVERFLOW_PROMOTION_INTERVAL != 0L) {
            return;
        }
        final var iterator = overflowModeRechecks.entrySet().iterator();
        final PendingModeRecheck promoted = iterator.next().getValue();
        iterator.remove();
        promoted.nextRetryEpoch = saturatedAdd(modeQueryEpoch, 1L);
        admitPendingModeRecheck(promoted);
    }

    private int retryPendingModeRechecks() {
        advanceModeQueryEpoch();
        promoteOverflowModeRecheck();
        int attempts = 0;
        int failures = 0;
        while (attempts < maxPendingModeRetriesPerQuery
                && !pendingModeSchedule.isEmpty()) {
            final PendingModeRecheck pending = pendingModeSchedule.first();
            if (pending.nextRetryEpoch > modeQueryEpoch) {
                break;
            }
            pendingModeSchedule.pollFirst();
            if (pendingModeRechecks.get(pending.key) != pending) {
                continue;
            }
            final LiveEntry live = liveCards.get(pending.key);
            if (live == null || !live.container.sameAs(pending.container)
                    || modeLocations.get(pending.key) != pending.location) {
                pendingModeRechecks.remove(pending.key);
                continue;
            }

            attempts++;
            pendingModeRetryAttempts++;
            try {
                if (pending.discoveryRequired) {
                    final EnumSet<StaticAbilityMode> modes =
                            collectCurrentModes(pending.card);
                    for (final StaticAbilityMode discovered : modes) {
                        addModeCandidate(pending.location, discovered);
                    }
                    pending.discoveryRequired = false;
                }
                if (!pending.quarantinedModes.isEmpty()) {
                    final StaticAbilityMode quarantined =
                            pending.quarantinedModes.iterator().next();
                    final boolean active = isActiveModeLocation(
                            pending.location, quarantined);
                    pending.quarantinedModes.remove(quarantined);
                    if (active) {
                        reactivateModeCandidate(pending.location, quarantined);
                    } else {
                        removeModeCandidate(pending.location, quarantined);
                    }
                }
            } catch (final RuntimeException ignored) {
                modeClassificationFailures++;
                failures++;
                if (pending.failures < Integer.MAX_VALUE) {
                    pending.failures++;
                }
                pending.nextRetryEpoch = saturatedAdd(modeQueryEpoch,
                        pendingRetryDelay(pending.failures));
                pendingModeSchedule.add(pending);
                continue;
            }

            if (pending.discoveryRequired
                    || !pending.quarantinedModes.isEmpty()) {
                pending.failures = 1;
                pending.nextRetryEpoch = saturatedAdd(modeQueryEpoch, 1L);
                pendingModeSchedule.add(pending);
            } else {
                pendingModeRechecks.remove(pending.key);
                dropEmptyModeLocation(pending.location);
            }
        }
        return failures;
    }

    private void quarantineModeCandidate(final ModeLocation location,
            final StaticAbilityMode mode) {
        final IdentityKey key = new IdentityKey(location.card);
        final PendingModeRecheck previous = detachPendingModeRecheck(key);
        final PendingModeRecheck pending = new PendingModeRecheck(key,
                location.card, location.container, location,
                nextPendingModeSequence(), 1,
                saturatedAdd(modeQueryEpoch, 1L),
                previous != null && previous.discoveryRequired);
        if (previous != null && previous.location == location) {
            pending.quarantinedModes.addAll(previous.quarantinedModes);
        }
        pending.quarantinedModes.add(mode);
        admitPendingModeRecheck(pending);
        setModeSnapshotQuarantined(location, mode, true);
    }

    private void reactivateModeCandidate(final ModeLocation location,
            final StaticAbilityMode mode) {
        setModeSnapshotQuarantined(location, mode, false);
        final IdentityKey key = new IdentityKey(location.card);
        final LiveEntry live = liveCards.get(key);
        if (modeLocations.get(key) != location
                || live == null || !live.container.sameAs(location.container)
                || !location.modes.contains(mode)) {
            return;
        }
        if (modeCandidates.computeIfAbsent(mode,
                ignored -> newModeSet()).add(location)) {
            markModeSnapshotDirty(mode, location.container.rank);
        }
    }

    private long pendingRetryDelay(final int failures) {
        final int shift = Math.min(maxPendingModeBackoffShift,
                Math.max(0, failures - 1));
        return 1L << shift;
    }

    private void advanceModeQueryEpoch() {
        if (modeQueryEpoch == Long.MAX_VALUE) {
            pendingModeSchedule.clear();
            modeQueryEpoch = 0L;
            for (final PendingModeRecheck pending
                    : pendingModeRechecks.values()) {
                pending.nextRetryEpoch = pendingRetryDelay(pending.failures);
                pendingModeSchedule.add(pending);
            }
        }
        modeQueryEpoch++;
    }

    private long nextPendingModeSequence() {
        if (nextPendingModeSequence == Long.MAX_VALUE) {
            final List<PendingModeRecheck> pending = new ArrayList<>(
                    pendingModeSchedule);
            pendingModeSchedule.clear();
            long sequence = 0L;
            for (final PendingModeRecheck item : pending) {
                item.sequence = sequence++;
                pendingModeSchedule.add(item);
            }
            for (final PendingModeRecheck item
                    : overflowModeRechecks.values()) {
                item.sequence = sequence++;
            }
            nextPendingModeSequence = sequence;
        }
        return nextPendingModeSequence++;
    }

    private static long saturatedAdd(final long left, final long right) {
        return right > 0L && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }

    private boolean isActiveModeLocation(final ModeLocation location,
            final StaticAbilityMode mode) {
        final IdentityKey key = new IdentityKey(location.card);
        final LiveEntry live = liveCards.get(key);
        return modeLocations.get(key) == location
                && location.modes.contains(mode)
                && live != null && live.container.sameAs(location.container)
                && (location.container.player == null
                || isCurrentPlayer(location.container.player))
                && (location.card.getIntrinsicStaticAbilityModes().contains(mode)
                || currentlyHasStaticAbilityMode(location.card, mode));
    }

    private void removeModeCandidate(final ModeLocation location,
            final StaticAbilityMode mode) {
        final NavigableSet<ModeLocation> sources = modeCandidates.get(mode);
        if (sources != null) {
            sources.remove(location);
            if (sources.isEmpty()) {
                modeCandidates.remove(mode);
            }
        }
        location.structuralModes.remove(mode);
        if (location.modes.remove(mode)) {
            markModeSnapshotDirty(mode, location.container.rank);
        }
        dropEmptyModeLocation(location);
    }

    private boolean isModeQueryVisible(final ModeLocation location) {
        return location.container.rank != BATTLEFIELD
                || !location.card.isPhasedOut();
    }

    private boolean containsStaticAbilitySourceEquivalent(final int cardId,
            final int[] ranks) {
        final List<LiveEntry> sameId = liveCardsById.get(cardId);
        if (sameId == null) {
            return false;
        }
        for (final LiveEntry live : sameId) {
            if (isLegacyStaticLiveEntry(live, ranks)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFirstLegacyStaticCardForId(
            final ModeLocation location, final int[] ranks) {
        final List<LiveEntry> sameId = liveCardsById.get(
                location.card.getId());
        if (sameId == null || sameId.size() < 2) {
            return true;
        }
        final LiveEntry locationLive = liveCards.get(
                new IdentityKey(location.card));
        if (locationLive == null) {
            return false;
        }
        for (final LiveEntry other : sameId) {
            if (other == locationLive || !isLegacyStaticLiveEntry(other,
                    ranks)) {
                continue;
            }
            if (compareLegacyStaticLocations(other, locationLive, ranks) < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isLegacyStaticLiveEntry(final LiveEntry live,
            final int[] ranks) {
        return live != null && rankIndex(live.container.rank, ranks) >= 0
                && (live.container.player == null
                || isCurrentPlayer(live.container.player))
                && (live.container.rank != BATTLEFIELD
                || !live.card.isPhasedOut());
    }

    private int compareLegacyStaticLocations(final LiveEntry left,
            final LiveEntry right, final int[] ranks) {
        int compared = Integer.compare(rankIndex(left.container.rank, ranks),
                rankIndex(right.container.rank, ranks));
        if (compared == 0) {
            compared = Integer.compare(playerOrder(left.container.player),
                    playerOrder(right.container.player));
        }
        if (compared == 0) {
            compared = Integer.compare(findCurrentPosition(left.card,
                            left.container),
                    findCurrentPosition(right.card, right.container));
        }
        return compared;
    }

    private static int rankIndex(final int rank, final int[] ranks) {
        for (int i = 0; i < ranks.length; i++) {
            if (ranks[i] == rank) {
                return i;
            }
        }
        return -1;
    }

    private static int[] orderedUniqueModeRanks(
            final Iterable<ZoneType> zones) {
        if (zones == null) {
            return new int[0];
        }
        final int[] buffer = new int[STATIC_MODE_RANKS.length];
        int count = 0;
        for (final ZoneType zone : zones) {
            final int rank = rankForZone(zone);
            if (!isStaticModeRank(rank)
                    || rankIndex(rank, buffer, count) >= 0) {
                continue;
            }
            buffer[count++] = rank;
        }
        return java.util.Arrays.copyOf(buffer, count);
    }

    private static int rankIndex(final int rank, final int[] ranks,
            final int length) {
        for (int i = 0; i < length; i++) {
            if (ranks[i] == rank) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isStaticModeRank(final int rank) {
        return rank == GRAVEYARD || rank == BATTLEFIELD || rank == EXILE
                || rank == COMMAND || rank == STACK;
    }

    private void dropEmptyModeLocation(final ModeLocation location) {
        if (!location.modes.isEmpty()) {
            return;
        }
        final IdentityKey key = new IdentityKey(location.card);
        if (hasPendingModeRecheck(key)) {
            return;
        }
        if (modeLocations.get(key) == location) {
            modeLocations.remove(key);
        }
        final NavigableSet<ModeLocation> containerLocations =
                modeLocationsByContainer.get(location.container);
        if (containerLocations != null) {
            containerLocations.remove(location);
            if (containerLocations.isEmpty()) {
                modeLocationsByContainer.remove(location.container);
            }
        }
    }

    private static boolean currentlyHasStaticAbilityMode(final Card card,
            final StaticAbilityMode mode) {
        for (final StaticAbility ability : card.getStaticAbilities()) {
            if (ability.getMode() != null && ability.getMode().contains(mode)) {
                return true;
            }
        }
        for (final StaticAbility ability : card.getHiddenStaticAbilities()) {
            if (ability.getMode() != null && ability.getMode().contains(mode)) {
                return true;
            }
        }
        return false;
    }

    private void indexCurrentStaticAbilityModes(final Card card,
            final LiveEntry live, final int position) {
        final EnumSet<StaticAbilityMode> modes = collectCurrentModes(card);
        if (modes.isEmpty()) {
            return;
        }
        indexStaticAbilityModes(card, live, position, modes);
    }

    private void indexCurrentStaticAbilityModes(final Card card,
            final LiveEntry live) {
        final EnumSet<StaticAbilityMode> modes = collectCurrentModes(card);
        if (modes.isEmpty()) {
            return;
        }
        indexStaticAbilityModes(card, live,
                findCurrentPosition(card, live.container), modes);
    }

    private void indexStaticAbilityModes(final Card card,
            final LiveEntry live, final int position,
            final EnumSet<StaticAbilityMode> modes) {
        final ModeLocation location = ensureModeLocation(card, live, position);
        for (final StaticAbilityMode mode : modes) {
            addModeCandidate(location, mode);
        }
    }

    private EnumSet<StaticAbilityMode> collectCurrentModes(
            final Card card) {
        modeDiscoveryCardVisits++;
        return collectModes(card);
    }

    private static EnumSet<StaticAbilityMode> collectBootstrapModes(
            final BootstrapState bootstrap, final Card card) {
        bootstrap.modeDiscoveryCardVisits++;
        return collectModes(card);
    }

    private static EnumSet<StaticAbilityMode> collectModes(final Card card) {
        final EnumSet<StaticAbilityMode> modes =
                EnumSet.noneOf(StaticAbilityMode.class);
        modes.addAll(card.getIntrinsicStaticAbilityModes());
        for (final StaticAbility ability : card.getStaticAbilities()) {
            if (ability.getMode() != null) {
                modes.addAll(ability.getMode());
            }
        }
        for (final StaticAbility ability : card.getHiddenStaticAbilities()) {
            if (ability.getMode() != null) {
                modes.addAll(ability.getMode());
            }
        }
        modes.remove(StaticAbilityMode.Continuous);
        return modes;
    }

    private static EnumSet<StaticAbilityMode> copyIndexedModes(
            final Iterable<StaticAbilityMode> modes) {
        final EnumSet<StaticAbilityMode> result =
                EnumSet.noneOf(StaticAbilityMode.class);
        for (final StaticAbilityMode mode : modes) {
            if (mode != null && mode != StaticAbilityMode.Continuous) {
                result.add(mode);
            }
        }
        return result;
    }

    private ModeLocation ensureModeLocation(final Card card,
            final LiveEntry live, final int position) {
        final IdentityKey key = new IdentityKey(card);
        final ModeLocation existing = modeLocations.get(key);
        if (existing != null) {
            return existing;
        }
        final ContainerKey container = live.container;
        final CardCollectionView cards = cardsForView(container);
        final boolean append = cards == null || position >= cards.size() - 1;
        final NavigableSet<ModeLocation> containerLocations =
                modeLocationsByContainer.get(container);
        final ModeLocation tail = containerLocations == null
                || containerLocations.isEmpty()
                ? null : containerLocations.last();
        long order = MODE_ORDER_GAP;
        if (append && tail != null
                && tail.order <= Long.MAX_VALUE - MODE_ORDER_GAP) {
            order = tail.order + MODE_ORDER_GAP;
        }
        final ModeLocation location = new ModeLocation(card, container,
                playerOrder(container.player), order, nextModeSequence++);
        modeLocations.put(key, location);
        modeLocationsByContainer.computeIfAbsent(container,
                ignored -> newModeSet()).add(location);
        if (append) {
            modeAppendInsertions++;
        } else {
            relabelModeContainer(container, cards);
        }
        return location;
    }

    private void addModeCandidate(final ModeLocation location,
            final StaticAbilityMode mode) {
        if (mode == null || mode == StaticAbilityMode.Continuous) {
            return;
        }
        final boolean membershipAdded = location.modes.add(mode);
        final boolean wasStructural = location.structuralModes.contains(mode);
        final boolean isStructural = location.card
                .getIntrinsicStaticAbilityModes().contains(mode);
        if (isStructural) {
            location.structuralModes.add(mode);
        } else {
            location.structuralModes.remove(mode);
        }
        if (!membershipAdded) {
            if (wasStructural != isStructural) {
                markModeSnapshotDirty(mode, location.container.rank);
            }
            return;
        }
        modeCandidates.computeIfAbsent(mode, ignored -> newModeSet())
                .add(location);
        markModeSnapshotDirty(mode, location.container.rank);
    }

    private void removeModeLocation(final IdentityKey key) {
        removePendingModeRecheck(key);
        final ModeLocation location = modeLocations.remove(key);
        if (location == null) {
            return;
        }
        for (final StaticAbilityMode mode : location.modes) {
            markModeSnapshotDirty(mode, location.container.rank);
            final NavigableSet<ModeLocation> sources = modeCandidates.get(mode);
            if (sources != null) {
                sources.remove(location);
                if (sources.isEmpty()) {
                    modeCandidates.remove(mode);
                }
            }
        }
        final NavigableSet<ModeLocation> containerLocations =
                modeLocationsByContainer.get(location.container);
        if (containerLocations != null) {
            containerLocations.remove(location);
            if (containerLocations.isEmpty()) {
                modeLocationsByContainer.remove(location.container);
            }
        }
    }

    private void rebuildModeContainer(final ContainerKey container,
            final Iterable<Card> cards) {
        final List<IdentityKey> toRemove = new ArrayList<>();
        for (final Map.Entry<IdentityKey, ModeLocation> item
                : modeLocations.entrySet()) {
            if (item.getValue().container.sameAs(container)) {
                toRemove.add(item.getKey());
            }
        }
        for (final IdentityKey key : toRemove) {
            removeModeLocation(key);
        }

        // Zone#setCards has already committed its authoritative contents.
        // Classify each card as an independent transaction: a malformed card
        // remains live but inactive, while valid siblings before and after it
        // are still indexed. Correcting that card's mode advertises it again.
        int position = 0;
        for (final Card card : cards) {
            final LiveEntry live = liveCards.get(new IdentityKey(card));
            if (live != null) {
                try {
                    final EnumSet<StaticAbilityMode> modes =
                            collectCurrentModes(card);
                    if (!modes.isEmpty()) {
                        indexCurrentStaticAbilityModesAtFixedOrder(card, live,
                                position, modes);
                    }
                } catch (final RuntimeException ignored) {
                    modeClassificationFailures++;
                    queuePendingModeRecheck(card, live, position, true);
                }
            }
            position++;
        }
    }

    private void indexCurrentStaticAbilityModesAtFixedOrder(final Card card,
            final LiveEntry live, final int position,
            final EnumSet<StaticAbilityMode> modes) {
        final ModeLocation location = ensureFixedModeLocation(card, live,
                position);
        for (final StaticAbilityMode mode : modes) {
            addModeCandidate(location, mode);
        }
    }

    private ModeLocation ensureFixedModeLocation(final Card card,
            final LiveEntry live, final int position) {
        final IdentityKey key = new IdentityKey(card);
        final ModeLocation existing = modeLocations.get(key);
        if (existing != null) {
            return existing;
        }
        final ModeLocation location = new ModeLocation(card, live.container,
                playerOrder(live.container.player),
                (position + 1L) * MODE_ORDER_GAP, nextModeSequence++);
        modeLocations.put(key, location);
        modeLocationsByContainer.computeIfAbsent(live.container,
                ignored -> newModeSet()).add(location);
        return location;
    }

    private void relabelModeContainer(final ContainerKey container,
            final Iterable<Card> cards) {
        final NavigableSet<ModeLocation> containerLocations =
                modeLocationsByContainer.get(container);
        if (containerLocations == null || containerLocations.isEmpty()) {
            return;
        }
        final List<ModeLocation> locations = new ArrayList<>(
                containerLocations);
        for (final ModeLocation location : locations) {
            for (final StaticAbilityMode mode : location.modes) {
                markModeSnapshotDirty(mode, container.rank);
                final NavigableSet<ModeLocation> sources =
                        modeCandidates.get(mode);
                if (sources != null) {
                    sources.remove(location);
                }
            }
        }
        containerLocations.clear();
        long order = MODE_ORDER_GAP;
        for (final Card card : cards) {
            modeOrderRelabelCardVisits++;
            final ModeLocation location = modeLocations.get(
                    new IdentityKey(card));
            if (location != null && location.container.sameAs(container)) {
                location.order = order;
                order += MODE_ORDER_GAP;
            }
        }
        for (final ModeLocation location : locations) {
            containerLocations.add(location);
            for (final StaticAbilityMode mode : location.modes) {
                modeCandidates.computeIfAbsent(mode,
                        ignored -> newModeSet()).add(location);
            }
        }
    }

    private CardCollectionView cardsForView(final ContainerKey container) {
        if (container.rank == STACK) {
            return game.getStackZone().getCards();
        }
        final ZoneType zone = zoneForRank(container.rank);
        return container.player == null || zone == null ? null
                : container.player.getZone(zone).getCards(false);
    }

    private int playerOrder(final Player player) {
        if (player == null) {
            return 0;
        }
        seedStablePlayerOrder(game.getRegisteredPlayers());
        seedStablePlayerOrder(game.getPlayers());
        return stablePlayerOrderById.computeIfAbsent(player.getId(),
                ignored -> nextStablePlayerOrder++);
    }

    private void seedStablePlayerOrder(final Iterable<Player> players) {
        for (final Player player : players) {
            stablePlayerOrderById.computeIfAbsent(player.getId(),
                    ignored -> nextStablePlayerOrder++);
        }
    }

    private static NavigableSet<ModeLocation> newModeSet() {
        return new TreeSet<>(MODE_LOCATION_ORDER);
    }

    private void specialCardEntered(final Player player,
            final int containerRank, final Card card, final int position) {
        if (!initialized || player == null || card == null
                || !isCurrentPlayer(player)) {
            return;
        }
        final ContainerKey container = new ContainerKey(player, containerRank);
        shiftForInsertion(container, position);
        addLiveCard(card, container, position);
    }

    private void specialCardLeft(final Player player, final int containerRank,
            final Card card, final int position) {
        if (!initialized || player == null || card == null) {
            return;
        }
        final IdentityKey key = new IdentityKey(card);
        if (removeLiveCard(key) == null) {
            return;
        }
        candidates.remove(key);
        pendingRecheck.remove(key);
        shiftForRemoval(new ContainerKey(player, containerRank), position);
    }

    private void shiftForInsertion(final ContainerKey container,
            final int position) {
        for (final CandidateEntry candidate : candidates.values()) {
            if (candidate.container.sameAs(container)
                    && candidate.position >= position) {
                candidate.position++;
            }
        }
    }

    private LiveEntry removeLiveCard(final IdentityKey key) {
        final LiveEntry removed = liveCards.remove(key);
        if (removed != null) {
            removeLiveCardById(removed);
        }
        return removed;
    }

    private void removeLiveCardById(final LiveEntry removed) {
        final List<LiveEntry> sameId = liveCardsById.get(removed.card.getId());
        if (sameId == null) {
            return;
        }
        sameId.removeIf(live -> live == removed);
        if (sameId.isEmpty()) {
            liveCardsById.remove(removed.card.getId());
        }
    }

    private LiveEntry latestLiveCardById(final int id) {
        final List<LiveEntry> sameId = liveCardsById.get(id);
        return sameId == null || sameId.isEmpty()
                ? null : sameId.get(sameId.size() - 1);
    }

    private void shiftForRemoval(final ContainerKey container,
            final int position) {
        for (final CandidateEntry candidate : candidates.values()) {
            if (candidate.container.sameAs(container)
                    && candidate.position > position) {
                candidate.position--;
            }
        }
    }

    private void refreshContainerPositions(final ContainerKey container,
            final Iterable<Card> cards) {
        final Map<IdentityKey, CandidateEntry> pending = new LinkedHashMap<>();
        for (final Map.Entry<IdentityKey, CandidateEntry> item
                : candidates.entrySet()) {
            if (item.getValue().container.sameAs(container)) {
                pending.put(item.getKey(), item.getValue());
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        int position = 0;
        for (final Card card : cards) {
            final CandidateEntry candidate = pending.remove(
                    new IdentityKey(card));
            if (candidate != null) {
                candidate.position = position;
            }
            position++;
        }
    }

    private int findCurrentPosition(final Card card,
            final ContainerKey container) {
        final Iterable<Card> cards = cardsFor(container);
        if (cards == null) {
            return Integer.MAX_VALUE;
        }
        int position = 0;
        for (final Card current : cards) {
            if (current == card) {
                return position;
            }
            position++;
        }
        return Integer.MAX_VALUE;
    }

    private Iterable<Card> cardsFor(final ContainerKey container) {
        if (container.rank == STACK) {
            return game.getStackZone().getCards();
        }
        final Player player = container.player;
        if (player == null) {
            return null;
        }
        if (container.rank == MELDED) {
            return ((PlayerZoneBattlefield) player.getZone(
                    ZoneType.Battlefield)).getMeldedCards();
        }
        if (container.rank == INBOUND) {
            return player.getInboundTokens();
        }
        final ZoneType zone = zoneForRank(container.rank);
        return zone == null ? null : player.getZone(zone).getCards(false);
    }

    private boolean isCurrentPlayer(final Player player) {
        if (player == null) {
            return true;
        }
        for (final Player current : game.getPlayers()) {
            if (current == player) {
                return true;
            }
        }
        return false;
    }

    private static int compareLocations(final CandidateEntry left,
            final CandidateEntry right,
            final IdentityHashMap<Player, Integer> playerOrder) {
        final int leftPlayer = left.container.player == null
                ? Integer.MAX_VALUE
                : playerOrder.getOrDefault(left.container.player,
                        Integer.MAX_VALUE - 1);
        final int rightPlayer = right.container.player == null
                ? Integer.MAX_VALUE
                : playerOrder.getOrDefault(right.container.player,
                        Integer.MAX_VALUE - 1);
        int compared = Integer.compare(leftPlayer, rightPlayer);
        if (compared == 0) {
            compared = Integer.compare(left.container.rank,
                    right.container.rank);
        }
        if (compared == 0) {
            compared = Integer.compare(left.position, right.position);
        }
        if (compared == 0) {
            compared = Long.compare(left.sequence, right.sequence);
        }
        return compared;
    }

    private static void collectIdentity(final Map<IdentityKey, Card> result,
            final Iterable<Card> cards) {
        if (cards == null) {
            return;
        }
        for (final Card card : cards) {
            if (card != null) {
                result.putIfAbsent(new IdentityKey(card), card);
            }
        }
    }

    private static boolean hasPotentialSource(final Card card) {
        if (card == null) {
            return false;
        }
        if (!card.getStaticCommandList().isEmpty()) {
            return true;
        }

        final Set<CardState> visitedStates = java.util.Collections
                .newSetFromMap(new IdentityHashMap<>());
        final CardState currentState = card.getCurrentState();
        if (currentState != null) {
            visitedStates.add(currentState);
            if (hasContinuousAbility(card.getStaticAbilities())) {
                return true;
            }
        }

        // Inspect every face that this object may become. This keeps transform,
        // face-down, specialize and copied/clone state transitions out of the
        // invalidation hot path.
        for (final CardStateName stateName : new ArrayList<>(card.getStates())) {
            final CardState state = card.getState(stateName);
            if (state != null && visitedStates.add(state)
                    && hasContinuousAbility(state.getStaticAbilities())) {
                return true;
            }
        }

        return hasContinuousAbility(card.getHiddenStaticAbilities());
    }

    private static boolean hasContinuousAbility(
            final Iterable<StaticAbility> abilities) {
        for (final StaticAbility ability : abilities) {
            if (ability != null && ability.getMode() != null
                    && ability.getMode().contains(StaticAbilityMode.Continuous)) {
                return true;
            }
        }
        return false;
    }

    private static ContainerKey containerFor(final Zone zone) {
        if (zone == null) {
            return null;
        }
        final int rank = rankForZone(zone.getZoneType());
        return rank < 0 ? null : new ContainerKey(zone.getPlayer(), rank);
    }

    private static int rankForZone(final ZoneType zone) {
        if (zone == null) {
            return -1;
        }
        return switch (zone) {
            case Graveyard -> GRAVEYARD;
            case Hand -> HAND;
            case Library -> LIBRARY;
            case Battlefield -> BATTLEFIELD;
            case Exile -> EXILE;
            case Command -> COMMAND;
            case SchemeDeck -> SCHEME_DECK;
            case PlanarDeck -> PLANAR_DECK;
            case AttractionDeck -> ATTRACTION_DECK;
            case Junkyard -> JUNKYARD;
            case ContraptionDeck -> CONTRAPTION_DECK;
            case Sideboard -> SIDEBOARD;
            case Stack -> STACK;
            default -> -1;
        };
    }

    private static boolean isStaticModeContainer(
            final ContainerKey container) {
        return container != null && switch (container.rank) {
            case GRAVEYARD, BATTLEFIELD, EXILE, COMMAND, STACK -> true;
            default -> false;
        };
    }

    private static int modeZoneOrder(final int rank) {
        return switch (rank) {
            case GRAVEYARD -> 0;
            case BATTLEFIELD -> 1;
            case EXILE -> 2;
            case COMMAND -> 3;
            case STACK -> 4;
            default -> Integer.MAX_VALUE;
        };
    }

    private static ModeLocation modeLowerBound(final int rank) {
        return new ModeLocation(null, new ContainerKey(null, rank),
                Integer.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
    }

    private static ModeLocation modeLowerBoundFor(final int rank) {
        return switch (rank) {
            case GRAVEYARD -> GRAVEYARD_MODE_LOWER;
            case BATTLEFIELD -> BATTLEFIELD_MODE_LOWER;
            case EXILE -> EXILE_MODE_LOWER;
            case COMMAND -> COMMAND_MODE_LOWER;
            case STACK -> STACK_MODE_LOWER;
            default -> throw new IllegalArgumentException(
                    "Not a static-ability source rank: " + rank);
        };
    }

    private static ZoneType zoneForRank(final int rank) {
        return switch (rank) {
            case GRAVEYARD -> ZoneType.Graveyard;
            case HAND -> ZoneType.Hand;
            case LIBRARY -> ZoneType.Library;
            case BATTLEFIELD -> ZoneType.Battlefield;
            case EXILE -> ZoneType.Exile;
            case COMMAND -> ZoneType.Command;
            case SCHEME_DECK -> ZoneType.SchemeDeck;
            case PLANAR_DECK -> ZoneType.PlanarDeck;
            case ATTRACTION_DECK -> ZoneType.AttractionDeck;
            case JUNKYARD -> ZoneType.Junkyard;
            case CONTRAPTION_DECK -> ZoneType.ContraptionDeck;
            case SIDEBOARD -> ZoneType.Sideboard;
            default -> null;
        };
    }

    record Diagnostics(long bootstrapCardVisits, long candidateCardVisits,
            long checkInvocations, int liveCards, int candidateCards,
            long bootstrapPotentialChecks, long afterClearPotentialChecks,
            long preListPotentialChecks, long currentPotentialChecks,
            long modeDiscoveryCardVisits, long modeSourceVisits,
            long modeCandidateExaminations,
            long modeOrderRelabelCardVisits, long modeAppendInsertions,
            long modeClassificationFailures,
            long modeTailRecomputeCardVisits,
            long modeSnapshotLocationCopies, long modeSnapshotRebuilds,
            int pendingModeRechecks, long pendingModeRetryAttempts,
            long pendingModeOverflowDrops,
            int pendingModeOverflowQuarantined,
            long pendingModeOverflowEvictions,
            long modeQueryDegradedSkips) {
    }

    @FunctionalInterface
    interface PotentialSourceDetector {
        boolean test(Card card);
    }

    private static final class BootstrapState {
        private final Map<IdentityKey, LiveEntry> liveCards =
                new LinkedHashMap<>();
        private final Map<Integer, List<LiveEntry>> liveCardsById =
                new LinkedHashMap<>();
        private final Map<IdentityKey, CandidateEntry> candidates =
                new LinkedHashMap<>();
        private final Map<IdentityKey, ModeLocation> modeLocations =
                new LinkedHashMap<>();
        private final Map<StaticAbilityMode, NavigableSet<ModeLocation>>
                modeCandidates = new EnumMap<>(StaticAbilityMode.class);
        private final Map<ContainerKey, NavigableSet<ModeLocation>>
                modeLocationsByContainer = new LinkedHashMap<>();
        private long nextSequence;
        private long nextModeSequence;
        private long cardVisits;
        private long bootstrapPotentialChecks;
        private long modeDiscoveryCardVisits;
    }

    private static final class LiveEntry {
        private final Card card;
        private final ContainerKey container;

        private LiveEntry(final Card card, final ContainerKey container) {
            this.card = card;
            this.container = container;
        }
    }

    private static final class CandidateEntry {
        private final Card card;
        private final ContainerKey container;
        private int position;
        private final long sequence;

        private CandidateEntry(final Card card, final ContainerKey container,
                final int position, final long sequence) {
            this.card = card;
            this.container = container;
            this.position = position;
            this.sequence = sequence;
        }
    }

    private static final class ModeLocation {
        private final Card card;
        private final ContainerKey container;
        private final int playerOrder;
        private final long sequence;
        private long order;
        private final EnumSet<StaticAbilityMode> modes =
                EnumSet.noneOf(StaticAbilityMode.class);
        private final EnumSet<StaticAbilityMode> structuralModes =
                EnumSet.noneOf(StaticAbilityMode.class);

        private ModeLocation(final Card card, final ContainerKey container,
                final int playerOrder, final long order,
                final long sequence) {
            this.card = card;
            this.container = container;
            this.playerOrder = playerOrder;
            this.order = order;
            this.sequence = sequence;
        }
    }

    private static final class ModeRankSnapshot {
        private final List<ModeLocation> locations;
        private final Map<ModeLocation, Integer> positions =
                new IdentityHashMap<>();
        private final BitSet structural;
        private final BitSet quarantined;

        private ModeRankSnapshot(final List<ModeLocation> locations,
                final BitSet structural, final BitSet quarantined) {
            this.locations = locations;
            this.structural = structural;
            this.quarantined = quarantined;
            for (int i = 0; i < locations.size(); i++) {
                positions.put(locations.get(i), i);
            }
        }

        private static ModeRankSnapshot empty() {
            return new ModeRankSnapshot(List.of(), new BitSet(),
                    new BitSet());
        }

        private int size() {
            return locations.size();
        }

        private ModeLocation get(final int index) {
            return locations.get(index);
        }

        private int nextActiveIndex(final int fromIndex) {
            final int next = quarantined.nextClearBit(
                    Math.max(0, fromIndex));
            return Math.min(next, locations.size());
        }

        private int nextStructuralActiveIndex(final int fromIndex) {
            int next = structural.nextSetBit(Math.max(0, fromIndex));
            while (next >= 0 && quarantined.get(next)) {
                next = structural.nextSetBit(next + 1);
            }
            return next < 0 ? locations.size() : next;
        }

        private void setQuarantined(final ModeLocation location,
                final boolean value) {
            final Integer position = positions.get(location);
            if (position != null) {
                quarantined.set(position, value);
            }
        }

        private void setStructural(final ModeLocation location,
                final boolean value) {
            final Integer position = positions.get(location);
            if (position != null) {
                structural.set(position, value);
            }
        }
    }

    private static final class PendingModeRecheck {
        private final IdentityKey key;
        private final Card card;
        private final ContainerKey container;
        private final ModeLocation location;
        private final EnumSet<StaticAbilityMode> quarantinedModes =
                EnumSet.noneOf(StaticAbilityMode.class);
        private boolean discoveryRequired;
        private long sequence;
        private int failures;
        private long nextRetryEpoch;

        private PendingModeRecheck(final IdentityKey key, final Card card,
                final ContainerKey container,
                final ModeLocation location, final long sequence,
                final int failures, final long nextRetryEpoch,
                final boolean discoveryRequired) {
            this.key = key;
            this.card = card;
            this.container = container;
            this.location = location;
            this.sequence = sequence;
            this.failures = failures;
            this.nextRetryEpoch = nextRetryEpoch;
            this.discoveryRequired = discoveryRequired;
        }
    }

    private static final class ContainerKey {
        private final Player player;
        private final int rank;

        private ContainerKey(final Player player, final int rank) {
            this.player = player;
            this.rank = rank;
        }

        private boolean sameAs(final ContainerKey other) {
            return other != null && player == other.player && rank == other.rank;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof ContainerKey key && sameAs(key);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(player) + rank;
        }
    }

    private static final class IdentityKey {
        private final Card card;

        private IdentityKey(final Card card) {
            this.card = card;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(card);
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof IdentityKey key && key.card == card;
        }
    }
}
