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

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Tracks only the battlefield objects whose derived controller-zone or
 * soulbond state may need cleanup after a continuous-static pass.
 *
 * <p>Card equality is intentionally never used here. LKI objects share ids
 * with live cards, while this tracker is allowed to mutate only the exact
 * identity currently registered in a live battlefield container.</p>
 */
final class BattlefieldDerivedStateTracker {
    private static final int MAX_FIXED_POINT_PASSES = 64;

    private final Game game;
    private final IdentityHashMap<Card, BattlefieldSlot> liveBattlefield =
            new IdentityHashMap<>();
    private final IdentityHashMap<Player, List<Card>> battlefieldOrder =
            new IdentityHashMap<>();
    private final Set<Card> controllerDirty = identitySet();
    private final IdentityHashMap<Card, Card> pairedEndpoints =
            new IdentityHashMap<>();
    private final Set<Card> pairDirty = identitySet();

    private long nextSequence;
    private long controllerCandidateVisits;
    private long pairCandidateVisits;
    private long affectedFallbackVisits;
    private long fullBattlefieldFallbackVisits;
    private long lifecycleRebuildVisits;
    private long fixedPointDeferrals;
    private long bulkContainerScanVisits;
    private long bulkIdentityRemovalVisits;
    private long bulkIdentitySetVisits;
    private long bulkPartnerCandidateVisits;

    BattlefieldDerivedStateTracker(final Game game) {
        this.game = game;
    }

    synchronized void cardEntered(final Card card, final Zone zone,
            final int requestedPosition) {
        if (!isBattlefield(zone) || card == null) {
            return;
        }
        final Player containerPlayer = zone.getPlayer();
        unregisterLiveIdentity(card, false);
        final List<Card> order = battlefieldOrder.computeIfAbsent(
                containerPlayer, ignored -> new ArrayList<>());
        final int position = Math.max(0,
                Math.min(requestedPosition, order.size()));
        order.add(position, card);
        final BattlefieldSlot slot = new BattlefieldSlot(containerPlayer,
                position, allocateSequence());
        liveBattlefield.put(card, slot);
        refreshPositions(order, position + 1);
        refreshPairEndpoint(card);
        if (!card.isPhasedOut() && card.getController() != containerPlayer) {
            controllerDirty.add(card);
        }
        markPairDirty(card);
    }

    synchronized void cardLeft(final Card card, final Zone zone,
            final int position) {
        if (!isBattlefield(zone) || card == null) {
            return;
        }
        final BattlefieldSlot slot = liveBattlefield.get(card);
        if (slot == null || slot.container != zone.getPlayer()) {
            return;
        }
        unregisterLiveIdentity(card, true);
    }

    synchronized void zoneContentsCleared(final Zone zone,
            final Iterable<Card> cards) {
        if (!isBattlefield(zone)) {
            return;
        }
        bulkUnregisterContainer(zone.getPlayer(), cards, true);
    }

    synchronized void zoneContentsSet(final Zone zone,
            final Iterable<Card> cards) {
        if (!isBattlefield(zone)) {
            return;
        }
        final Player player = zone.getPlayer();
        final List<Card> incoming = new ArrayList<>();
        final Set<Card> seenIncoming = identitySet();
        for (final Card card : cards) {
            if (card != null && seenIncoming.add(card)) {
                incoming.add(card);
            }
        }
        final List<Card> order = new ArrayList<>();
        final IdentityHashMap<Player, Set<Card>> removalsByContainer =
                new IdentityHashMap<>();
        for (final Card card : incoming) {
            final BattlefieldSlot oldSlot = liveBattlefield.get(card);
            if (oldSlot != null) {
                removalsByContainer.computeIfAbsent(oldSlot.container,
                        ignored -> identitySet()).add(card);
            }
        }
        for (final java.util.Map.Entry<Player, Set<Card>> removal
                : removalsByContainer.entrySet()) {
            bulkUnregisterIdentities(removal.getKey(), removal.getValue(),
                    true);
        }
        int position = 0;
        for (final Card card : incoming) {
            bulkIdentitySetVisits++;
            order.add(card);
            final BattlefieldSlot slot = new BattlefieldSlot(player,
                    position++, allocateSequence());
            liveBattlefield.put(card, slot);
            refreshPairEndpoint(card);
            if (!card.isPhasedOut() && card.getController() != player) {
                controllerDirty.add(card);
            }
            markPairDirty(card);
        }
        battlefieldOrder.put(player, order);
    }

    synchronized void zoneOrderChanged(final Zone zone,
            final Iterable<Card> cards) {
        if (!isBattlefield(zone)) {
            return;
        }
        final List<Card> order = new ArrayList<>();
        int position = 0;
        for (final Card card : cards) {
            order.add(card);
            final BattlefieldSlot slot = liveBattlefield.get(card);
            if (slot != null && slot.container == zone.getPlayer()) {
                slot.position = position;
            }
            position++;
        }
        battlefieldOrder.put(zone.getPlayer(), order);
    }

    synchronized void controllerMayHaveChanged(final Card card) {
        if (card == null || !liveBattlefield.containsKey(card)) {
            return;
        }
        controllerDirty.add(card);
        markPairDirty(card);
    }

    synchronized void cardTypeMayHaveChanged(final Card card) {
        if (card != null && liveBattlefield.containsKey(card)
                && (pairedEndpoints.containsKey(card)
                || card.isPaired())) {
            markPairDirty(card);
        }
    }

    synchronized void phasingMayHaveChanged(final Card card) {
        if (card == null || !liveBattlefield.containsKey(card)) {
            return;
        }
        if (!card.isPhasedOut()) {
            controllerDirty.add(card);
        }
        markPairDirty(card);
    }

    synchronized void pairingChanged(final Card card, final Card oldPartner,
            final Card newPartner) {
        if (card == null) {
            return;
        }
        refreshPairEndpoint(card);
        refreshPairEndpoint(oldPartner);
        refreshPairEndpoint(newPartner);
        queuePairIfLive(card);
        queuePairIfLive(oldPartner);
        queuePairIfLive(newPartner);
    }

    synchronized void markAffected(final Iterable<Card> affectedCards) {
        if (affectedCards == null) {
            return;
        }
        for (final Card card : affectedCards) {
            affectedFallbackVisits++;
            final BattlefieldSlot slot = liveBattlefield.get(card);
            if (slot == null) {
                continue;
            }
            if (!card.isPhasedOut()
                    && card.getController() != slot.container) {
                controllerDirty.add(card);
            }
            if (pairedEndpoints.containsKey(card) || card.isPaired()) {
                pairDirty.add(card);
            }
        }
    }

    synchronized void playerRemoved(final Player player) {
        if (player == null) {
            return;
        }
        final List<Card> cards = battlefieldOrder.remove(player);
        if (cards != null) {
            bulkUnregisterRemovedOrder(cards, player, true);
        }
        controllerDirty.removeIf(card -> card.getController() == player);
    }

    void drain(final Set<Card> affectedCards, final GameAction action) {
        drain(affectedCards, action::controllerChangeZoneCorrection);
    }

    void drain(final Set<Card> affectedCards,
            final ControllerCorrector corrector) {
        int pass = 0;
        while (pass++ < MAX_FIXED_POINT_PASSES) {
            final List<Card> controllers = takeOrderedControllerWork();
            final List<Card> pairs = takeOrderedPairWork();
            if (controllers.isEmpty() && pairs.isEmpty()) {
                return;
            }

            for (int i = 0; i < controllers.size(); i++) {
                final Card card = controllers.get(i);
                incrementControllerVisits();
                if (!needsControllerCorrection(card)) {
                    continue;
                }
                try {
                    corrector.correct(card);
                    if (affectedCards != null) {
                        affectedCards.add(card);
                    }
                } catch (final RuntimeException | Error ex) {
                    restoreControllerWork(controllers, i);
                    restorePairWork(pairs, 0);
                    throw ex;
                }
            }

            for (int i = 0; i < pairs.size(); i++) {
                final Card card = pairs.get(i);
                incrementPairVisits();
                try {
                    cleanupPairIfInvalid(card, affectedCards);
                } catch (final RuntimeException | Error ex) {
                    restorePairWork(pairs, i);
                    throw ex;
                }
            }
        }
        synchronized (this) {
            fixedPointDeferrals++;
        }
    }

    synchronized void rebuildFromCurrentBattlefields() {
        liveBattlefield.clear();
        battlefieldOrder.clear();
        controllerDirty.clear();
        pairedEndpoints.clear();
        pairDirty.clear();
        for (final Player player : game.getPlayers()) {
            final List<Card> order = new ArrayList<>();
            int position = 0;
            for (final Card card : player.getCardsIn(
                    ZoneType.Battlefield, false)) {
                lifecycleRebuildVisits++;
                order.add(card);
                liveBattlefield.put(card, new BattlefieldSlot(player,
                        position++, allocateSequence()));
            }
            battlefieldOrder.put(player, order);
        }
        for (final Card card : liveBattlefield.keySet()) {
            refreshPairEndpoint(card);
            final BattlefieldSlot slot = liveBattlefield.get(card);
            if (!card.isPhasedOut()
                    && card.getController() != slot.container) {
                controllerDirty.add(card);
            }
            markPairDirty(card);
        }
    }

    synchronized void resetDiagnostics() {
        controllerCandidateVisits = 0;
        pairCandidateVisits = 0;
        affectedFallbackVisits = 0;
        fullBattlefieldFallbackVisits = 0;
        lifecycleRebuildVisits = 0;
        fixedPointDeferrals = 0;
        bulkContainerScanVisits = 0;
        bulkIdentityRemovalVisits = 0;
        bulkIdentitySetVisits = 0;
        bulkPartnerCandidateVisits = 0;
    }

    synchronized Diagnostics diagnostics() {
        return new Diagnostics(controllerCandidateVisits,
                pairCandidateVisits, affectedFallbackVisits,
                fullBattlefieldFallbackVisits, lifecycleRebuildVisits,
                fixedPointDeferrals, bulkContainerScanVisits,
                bulkIdentityRemovalVisits, bulkIdentitySetVisits,
                bulkPartnerCandidateVisits, liveBattlefield.size(),
                pairedEndpoints.size(), controllerDirty.size(), pairDirty.size());
    }

    private synchronized List<Card> takeOrderedControllerWork() {
        return takeOrderedWork(controllerDirty, true);
    }

    private synchronized List<Card> takeOrderedPairWork() {
        return takeOrderedWork(pairDirty, false);
    }

    private List<Card> takeOrderedWork(final Set<Card> dirty,
            final boolean controllers) {
        if (dirty.isEmpty()) {
            return List.of();
        }
        final List<Card> result = new ArrayList<>();
        for (final Card card : dirty) {
            if (!liveBattlefield.containsKey(card)) {
                continue;
            }
            if (!controllers && !card.isPaired()
                    && !pairedEndpoints.containsKey(card)) {
                continue;
            }
            result.add(card);
        }
        dirty.clear();
        final IdentityHashMap<Player, Integer> players = new IdentityHashMap<>();
        int playerOrder = 0;
        for (final Player player : game.getPlayers()) {
            players.put(player, playerOrder++);
        }
        result.sort(Comparator
                .comparingInt((Card card) -> players.getOrDefault(
                        liveBattlefield.get(card).container,
                        Integer.MAX_VALUE))
                .thenComparingInt(card -> liveBattlefield.get(card).position)
                .thenComparingLong(card -> liveBattlefield.get(card).sequence));
        return result;
    }

    private synchronized boolean needsControllerCorrection(final Card card) {
        final BattlefieldSlot slot = liveBattlefield.get(card);
        return slot != null && !card.isPhasedOut()
                && card.getController() != null
                && card.getController() != slot.container;
    }

    private void cleanupPairIfInvalid(final Card card,
            final Set<Card> affectedCards) {
        final Card partner = card.getPairedWith();
        if (partner == null) {
            synchronized (this) {
                pairedEndpoints.remove(card);
            }
            return;
        }
        if (isValidPair(card, partner)) {
            return;
        }

        if (card.getPairedWith() == partner) {
            card.setPairedWith(null);
        }
        if (partner.getPairedWith() == card) {
            partner.setPairedWith(null);
        }
        if (affectedCards != null) {
            affectedCards.add(card);
            if (isLiveBattlefieldIdentity(partner)) {
                affectedCards.add(partner);
            }
        }
    }

    private synchronized boolean isLiveBattlefieldIdentity(final Card card) {
        return liveBattlefield.containsKey(card);
    }

    private synchronized boolean isValidPair(final Card card,
            final Card partner) {
        return liveBattlefield.containsKey(card)
                && liveBattlefield.containsKey(partner)
                && card.getPairedWith() == partner
                && partner.getPairedWith() == card
                && card.isCreature()
                && partner.isCreature()
                && card.getController() == partner.getController();
    }

    private synchronized void restoreControllerWork(final List<Card> work,
            final int start) {
        for (int i = start; i < work.size(); i++) {
            if (liveBattlefield.containsKey(work.get(i))) {
                controllerDirty.add(work.get(i));
            }
        }
    }

    private synchronized void restorePairWork(final List<Card> work,
            final int start) {
        for (int i = start; i < work.size(); i++) {
            if (liveBattlefield.containsKey(work.get(i))) {
                pairDirty.add(work.get(i));
            }
        }
    }

    private synchronized void incrementControllerVisits() {
        controllerCandidateVisits++;
    }

    private synchronized void incrementPairVisits() {
        pairCandidateVisits++;
    }

    private void unregisterLiveIdentity(final Card card,
            final boolean dirtyPartner) {
        final BattlefieldSlot removed = liveBattlefield.remove(card);
        if (removed == null) {
            return;
        }
        final List<Card> order = battlefieldOrder.get(removed.container);
        if (order != null) {
            int position = removed.position;
            if (position < 0 || position >= order.size()
                    || order.get(position) != card) {
                position = identityIndexOf(order, card);
            }
            if (position >= 0) {
                order.remove(position);
                refreshPositions(order, position);
            }
            if (order.isEmpty()) {
                battlefieldOrder.remove(removed.container);
            }
        }
        controllerDirty.remove(card);
        pairDirty.remove(card);
        pairedEndpoints.remove(card);
        if (dirtyPartner && card.getPairedWith() != null) {
            final Card partner = card.getPairedWith();
            if (liveBattlefield.containsKey(partner)) {
                pairDirty.add(partner);
            }
        }
    }

    /**
     * Removes an entire battlefield container without repeatedly shifting its
     * order list. The callback is issued before the owning Zone clears its
     * cards, so the tracked order is the authoritative fast path; the supplied
     * iterable is only a fail-closed recovery path for a missing order entry.
     */
    private void bulkUnregisterContainer(final Player container,
            final Iterable<Card> fallbackCards, final boolean dirtyPartners) {
        final List<Card> removed = battlefieldOrder.remove(container);
        if (removed != null) {
            bulkUnregisterRemovedOrder(removed, container, dirtyPartners);
            return;
        }

        final List<Card> fallback = new ArrayList<>();
        for (final Card card : fallbackCards) {
            final BattlefieldSlot slot = liveBattlefield.get(card);
            if (slot != null && slot.container == container) {
                fallback.add(card);
            }
        }
        bulkUnregisterRemovedOrder(fallback, container, dirtyPartners);
    }

    /**
     * Removes selected identities from one container in one order-list pass.
     * This handles defensive setCards calls whose incoming cards are still
     * registered in a different battlefield container.
     */
    private void bulkUnregisterIdentities(final Player container,
            final Set<Card> removals, final boolean dirtyPartners) {
        if (removals.isEmpty()) {
            return;
        }
        final List<Card> oldOrder = battlefieldOrder.get(container);
        if (oldOrder == null) {
            bulkUnregisterRemovedOrder(new ArrayList<>(removals), container,
                    dirtyPartners);
            return;
        }

        final List<Card> kept = new ArrayList<>(Math.max(0,
                oldOrder.size() - removals.size()));
        final List<Card> removed = new ArrayList<>(removals.size());
        for (final Card card : oldOrder) {
            bulkContainerScanVisits++;
            if (removals.contains(card)) {
                removed.add(card);
            } else {
                kept.add(card);
            }
        }
        if (kept.isEmpty()) {
            battlefieldOrder.remove(container);
        } else {
            battlefieldOrder.put(container, kept);
        }
        refreshPositions(kept, 0);
        bulkUnregisterRemovedOrder(removed, container, dirtyPartners, false);

        // A corrupt/stale order must not leave a requested live identity
        // registered. This loop is bounded by the incoming set, not by the
        // global battlefield, and normally removes nothing.
        final List<Card> missing = new ArrayList<>();
        for (final Card card : removals) {
            final BattlefieldSlot slot = liveBattlefield.get(card);
            if (slot != null && slot.container == container) {
                missing.add(card);
            }
        }
        bulkUnregisterRemovedOrder(missing, container, dirtyPartners, false);
    }

    private void bulkUnregisterRemovedOrder(final List<Card> removed,
            final Player container, final boolean dirtyPartners) {
        bulkUnregisterRemovedOrder(removed, container, dirtyPartners, true);
    }

    private void bulkUnregisterRemovedOrder(final List<Card> removed,
            final Player container, final boolean dirtyPartners,
            final boolean countContainerScan) {
        final List<Card> actuallyRemoved = new ArrayList<>(removed.size());
        for (final Card card : removed) {
            if (countContainerScan) {
                bulkContainerScanVisits++;
            }
            final BattlefieldSlot slot = liveBattlefield.get(card);
            if (slot == null || slot.container != container) {
                continue;
            }
            liveBattlefield.remove(card);
            controllerDirty.remove(card);
            pairDirty.remove(card);
            pairedEndpoints.remove(card);
            actuallyRemoved.add(card);
            bulkIdentityRemovalVisits++;
        }
        if (!dirtyPartners) {
            return;
        }
        // Remove every endpoint first. A partner that was removed by the same
        // bulk operation must not be re-queued as live work.
        for (final Card card : actuallyRemoved) {
            bulkPartnerCandidateVisits++;
            final Card partner = card.getPairedWith();
            if (partner != null && liveBattlefield.containsKey(partner)) {
                pairDirty.add(partner);
            }
        }
    }

    private void refreshPositions(final List<Card> order, final int start) {
        for (int i = Math.max(0, start); i < order.size(); i++) {
            final BattlefieldSlot slot = liveBattlefield.get(order.get(i));
            if (slot != null) {
                slot.position = i;
            }
        }
    }

    private long allocateSequence() {
        final long allocated = nextSequence;
        if (nextSequence != Long.MAX_VALUE) {
            nextSequence++;
        }
        return allocated;
    }

    private void refreshPairEndpoint(final Card card) {
        if (card == null) {
            return;
        }
        if (liveBattlefield.containsKey(card) && card.getPairedWith() != null) {
            pairedEndpoints.put(card, card.getPairedWith());
        } else {
            pairedEndpoints.remove(card);
        }
    }

    private void markPairDirty(final Card card) {
        if (card == null) {
            return;
        }
        if (liveBattlefield.containsKey(card)
                && (pairedEndpoints.containsKey(card) || card.isPaired())) {
            queuePairIfLive(card);
        }
        final Card partner = card.getPairedWith();
        queuePairIfLive(partner);
    }

    private void queuePairIfLive(final Card card) {
        if (card != null && liveBattlefield.containsKey(card)) {
            pairDirty.add(card);
        }
    }

    private static boolean isBattlefield(final Zone zone) {
        return zone != null && zone.is(ZoneType.Battlefield)
                && zone.getPlayer() != null;
    }

    private static int identityIndexOf(final List<Card> cards,
            final Card expected) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i) == expected) {
                return i;
            }
        }
        return -1;
    }

    private static Set<Card> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    @FunctionalInterface
    interface ControllerCorrector {
        void correct(Card card);
    }

    record Diagnostics(long controllerCandidateVisits,
            long pairCandidateVisits, long affectedFallbackVisits,
            long fullBattlefieldFallbackVisits, long lifecycleRebuildVisits,
            long fixedPointDeferrals, long bulkContainerScanVisits,
            long bulkIdentityRemovalVisits, long bulkIdentitySetVisits,
            long bulkPartnerCandidateVisits, int liveBattlefieldCards,
            int pairedEndpoints, int pendingControllerCards,
            int pendingPairCards) {
    }

    private static final class BattlefieldSlot {
        private final Player container;
        private int position;
        private final long sequence;

        private BattlefieldSlot(final Player container, final int position,
                final long sequence) {
            this.container = container;
            this.position = position;
            this.sequence = sequence;
        }
    }
}
