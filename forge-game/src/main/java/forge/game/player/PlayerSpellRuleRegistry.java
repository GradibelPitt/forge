package forge.game.player;

import forge.game.ability.RecoverableEffectException;
import forge.game.card.Card;
import forge.game.mana.ManaConversionMatrix;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Stores source-independent spell rules for one player for the current game. */
public final class PlayerSpellRuleRegistry {
    private static final Set<ZoneType> HARMONY_EAGER_VIEW_ZONES =
            Collections.unmodifiableSet(EnumSet.of(
                    ZoneType.Hand, ZoneType.Graveyard, ZoneType.Battlefield,
                    ZoneType.Exile, ZoneType.Command, ZoneType.Stack,
                    ZoneType.Ante, ZoneType.Merged, ZoneType.Junkyard));
    private final Player owner;
    private final Map<String, PlayerSpellRule> rules = new LinkedHashMap<>();
    private long stackingSequence;
    private long harmonyEpoch;
    private int lastHarmonyViewRefreshCount;
    private int lastHarmonyViewRefreshFailureCount;
    private long harmonyViewScannedCardCount;
    private boolean harmonyViewRefreshPending;

    PlayerSpellRuleRegistry(final Player owner) {
        this.owner = owner;
    }

    /**
     * Registers a rule by stable key. Repeating the exact registration is a
     * no-op; reusing a key for different rule data is rejected.
     */
    public PlayerSpellRule register(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        return register(stableKey, validCard, validSpellAbility,
                genericReduction, manaConversion, false, 0);
    }

    public PlayerSpellRule register(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction) {
        final PlayerSpellRule candidate = new PlayerSpellRule(stableKey,
                validCard, validSpellAbility, genericReduction, manaConversion,
                harmony, harmonyReduction);
        return registerCandidate(candidate, true);
    }

    /**
     * Commits a registration already preflighted by a transactional effect.
     * Card views are deliberately refreshed only after every target registry
     * has committed, so view work cannot turn a multi-player grant into a
     * partial registry update.
     */
    public PlayerSpellRule registerAfterPreflight(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction) {
        final PlayerSpellRule candidate = new PlayerSpellRule(stableKey,
                validCard, validSpellAbility, genericReduction, manaConversion,
                harmony, harmonyReduction);
        return registerCandidate(candidate, false);
    }

    public PlayerSpellRule registerAfterPreflight(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction,
            final Set<String> namedCards) {
        return registerCandidate(new PlayerSpellRule(stableKey, validCard,
                validSpellAbility, genericReduction, manaConversion, harmony,
                harmonyReduction, namedCards), false);
    }

    /** Validates a stable registration without mutating this registry. */
    public void validateRegistration(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        validateRegistration(stableKey, validCard, validSpellAbility,
                genericReduction, manaConversion, false, 0);
    }

    public void validateRegistration(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction) {
        wouldAddRegistration(stableKey, validCard, validSpellAbility,
                genericReduction, manaConversion, harmony, harmonyReduction);
    }

    /**
     * Validates a stable registration and reports whether it would add a new
     * rule, without changing registry contents or the stacking sequence.
     */
    public boolean wouldAddRegistration(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        return wouldAddRegistration(stableKey, validCard, validSpellAbility,
                genericReduction, manaConversion, false, 0);
    }

    public boolean wouldAddRegistration(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction) {
        final PlayerSpellRule candidate = new PlayerSpellRule(stableKey,
                validCard, validSpellAbility, genericReduction, manaConversion,
                harmony, harmonyReduction);
        validateCandidate(candidate);
        return !rules.containsKey(candidate.getKey());
    }

    public boolean wouldAddRegistration(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction,
            final Set<String> namedCards) {
        final PlayerSpellRule candidate = new PlayerSpellRule(stableKey,
                validCard, validSpellAbility, genericReduction, manaConversion,
                harmony, harmonyReduction, namedCards);
        validateCandidate(candidate);
        return !rules.containsKey(candidate.getKey());
    }

    /**
     * Reports whether existing rules semantically cover the requested payment
     * conversion and its safe card/spell scope. Rule keys and generic
     * reductions are ignored. The query validates input and never mutates.
     */
    public boolean hasManaConversionCoverage(final String validCard,
            final String validSpellAbility, final String manaConversion) {
        if (validCard == null || validSpellAbility == null
                || manaConversion == null) {
            throw new IllegalArgumentException(
                    "Mana conversion coverage inputs must not be null");
        }
        final PlayerSpellRule probe = new PlayerSpellRule(
                "mana-conversion-coverage-probe", validCard,
                validSpellAbility, 0, manaConversion);
        for (final String requestedCardRestriction
                : probe.getValidCardRestrictionsForCoverage()) {
            boolean covered = false;
            for (final PlayerSpellRule rule : rules.values()) {
                if (!rule.isHarmony() && rule.coversPaymentScope(probe,
                        requestedCardRestriction)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reports whether an existing visible Harmony grant covers the requested
     * safe card and spell scope. A plain mana-conversion rule is deliberately
     * not treated as Harmony because it does not grant the visible keyword.
     */
    public boolean hasHarmonyCoverage(final String validCard,
            final String validSpellAbility) {
        return hasHarmonyCoverage(validCard, validSpellAbility, Set.of());
    }

    public boolean hasHarmonyCoverage(final String validCard,
            final String validSpellAbility, final Set<String> namedCards) {
        final PlayerSpellRule probe = new PlayerSpellRule(
                "harmony-coverage-probe", validCard, validSpellAbility,
                0, "", true, 0, namedCards);
        for (final String requestedCardRestriction
                : probe.getValidCardRestrictionsForCoverage()) {
            final Set<String> requestedNames = probe.getNamedCards();
            if (requestedNames.isEmpty()) {
                boolean covered = false;
                for (final PlayerSpellRule rule : rules.values()) {
                    if (rule.isHarmony() && rule.coversPaymentScope(probe,
                            requestedCardRestriction)
                            && rule.coversNamedCards(requestedNames)) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    return false;
                }
            } else {
                for (final String requestedName : requestedNames) {
                    boolean nameCovered = false;
                    for (final PlayerSpellRule rule : rules.values()) {
                        if (rule.isHarmony() && rule.coversPaymentScope(probe,
                                requestedCardRestriction)
                                && rule.coversNamedCards(
                                        Set.of(requestedName))) {
                            nameCovered = true;
                            break;
                        }
                    }
                    if (!nameCovered) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Validates rule data even when an effect currently has no players. */
    public static void validateRuleDefinition(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        validateRuleDefinition(stableKey, validCard, validSpellAbility,
                genericReduction, manaConversion, false, 0);
    }

    public static void validateRuleDefinition(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction) {
        new PlayerSpellRule(stableKey, validCard, validSpellAbility,
                genericReduction, manaConversion, harmony, harmonyReduction);
    }

    public static void validateRuleDefinition(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction,
            final Set<String> namedCards) {
        new PlayerSpellRule(stableKey, validCard, validSpellAbility,
                genericReduction, manaConversion, harmony, harmonyReduction,
                namedCards);
    }

    private PlayerSpellRule registerCandidate(final PlayerSpellRule candidate,
            final boolean refreshViews) {
        validateCandidate(candidate);
        final PlayerSpellRule existing = rules.get(candidate.getKey());
        if (existing == null) {
            final boolean expandsHarmonyVisibility = candidate.isHarmony()
                    && !hasHarmonyCoverage(
                            candidate.getValidCardRestriction(),
                            candidate.getValidSpellAbilityRestriction(),
                            candidate.getNamedCards());
            rules.put(candidate.getKey(), candidate);
            if (expandsHarmonyVisibility) {
                advanceHarmonyEpoch();
            }
            if (candidate.isHarmony() && refreshViews
                    && (expandsHarmonyVisibility
                            || harmonyViewRefreshPending)) {
                refreshCardViews();
            }
            return candidate;
        }
        if (candidate.isHarmony() && refreshViews
                && harmonyViewRefreshPending) {
            refreshCardViews();
        }
        return existing;
    }

    private void validateCandidate(final PlayerSpellRule candidate) {
        final PlayerSpellRule existing = rules.get(candidate.getKey());
        if (existing != null && !existing.equals(candidate)) {
            throw new RecoverableEffectException(
                    "Spell rule key already has different data: "
                    + candidate.getKey());
        }
    }

    /** Registers another independently stacking instance of a rule. */
    public PlayerSpellRule registerStacking(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        return registerStacking(baseKey, validCard, validSpellAbility,
                genericReduction, manaConversion, false, 0);
    }

    public PlayerSpellRule registerStacking(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction) {
        return registerStacking(baseKey, validCard, validSpellAbility,
                genericReduction, manaConversion, harmony, harmonyReduction,
                Set.of());
    }

    public PlayerSpellRule registerStacking(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction,
            final Set<String> namedCards) {
        final PlayerSpellRule candidate = prepareStackingRegistration(baseKey,
                validCard, validSpellAbility, genericReduction, manaConversion,
                harmony, harmonyReduction, namedCards);
        final boolean expandsHarmonyVisibility = candidate.isHarmony()
                && !hasHarmonyCoverage(validCard, validSpellAbility,
                        candidate.getNamedCards());
        rules.put(candidate.getKey(), candidate);
        stackingSequence++;
        if (expandsHarmonyVisibility) {
            advanceHarmonyEpoch();
        }
        if (candidate.isHarmony() && (expandsHarmonyVisibility
                || harmonyViewRefreshPending)) {
            refreshCardViews();
        }
        return candidate;
    }

    /** See {@link #registerAfterPreflight}. */
    public PlayerSpellRule registerStackingAfterPreflight(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction) {
        return registerStackingAfterPreflight(baseKey, validCard,
                validSpellAbility, genericReduction, manaConversion, harmony,
                harmonyReduction, Set.of());
    }

    public PlayerSpellRule registerStackingAfterPreflight(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction,
            final Set<String> namedCards) {
        final PlayerSpellRule candidate = prepareStackingRegistration(baseKey,
                validCard, validSpellAbility, genericReduction, manaConversion,
                harmony, harmonyReduction, namedCards);
        final boolean expandsHarmonyVisibility = candidate.isHarmony()
                && !hasHarmonyCoverage(validCard, validSpellAbility,
                        candidate.getNamedCards());
        rules.put(candidate.getKey(), candidate);
        stackingSequence++;
        if (expandsHarmonyVisibility) {
            advanceHarmonyEpoch();
        }
        return candidate;
    }

    /** Validates the next stacking registration without changing its sequence. */
    public void validateStackingRegistration(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        validateStackingRegistration(baseKey, validCard, validSpellAbility,
                genericReduction, manaConversion, false, 0);
    }

    public void validateStackingRegistration(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction) {
        validateStackingRegistration(baseKey, validCard, validSpellAbility,
                genericReduction, manaConversion, harmony, harmonyReduction,
                Set.of());
    }

    public void validateStackingRegistration(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction,
            final Set<String> namedCards) {
        prepareStackingRegistration(baseKey, validCard, validSpellAbility,
                genericReduction, manaConversion, harmony, harmonyReduction,
                namedCards);
    }

    private PlayerSpellRule prepareStackingRegistration(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion,
            final boolean harmony, final int harmonyReduction,
            final Set<String> namedCards) {
        if (baseKey == null || baseKey.trim().isEmpty()) {
            throw new IllegalArgumentException("baseKey must not be blank");
        }
        if (stackingSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Player spell rule stacking sequence exhausted");
        }
        final long nextSequence = stackingSequence + 1;
        final String uniqueKey = baseKey.trim() + "#" + nextSequence;
        final PlayerSpellRule candidate = new PlayerSpellRule(uniqueKey,
                validCard, validSpellAbility, genericReduction, manaConversion,
                harmony, harmonyReduction, namedCards);
        if (rules.containsKey(uniqueKey)) {
            throw new IllegalStateException(
                    "Player spell rule stacking key already exists: " + uniqueKey);
        }
        return candidate;
    }

    public int getGenericReduction(final Card card, final SpellAbility sa) {
        long total = 0;
        for (final PlayerSpellRule rule : rules.values()) {
            if (rule.getGenericReduction() > 0 && rule.matches(owner, card, sa)) {
                total += rule.getGenericReduction();
                if (total >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) total;
    }

    public int getHarmonyReduction(final Card card, final SpellAbility sa) {
        long total = 0;
        for (final PlayerSpellRule rule : rules.values()) {
            if (rule.getHarmonyReduction() > 0
                    && rule.matchesOwnedHarmony(owner, card, sa)) {
                total += rule.getHarmonyReduction();
                if (total >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) total;
    }

    /** Returns whether the card currently receives the visible Harmony keyword. */
    public boolean grantsHarmony(final Card card) {
        for (final PlayerSpellRule rule : rules.values()) {
            if (rule.grantsHarmony(owner, card)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasHarmonyRules() {
        for (final PlayerSpellRule rule : rules.values()) {
            if (rule.isHarmony()) {
                return true;
            }
        }
        return false;
    }

    public long getHarmonyEpoch() {
        return harmonyEpoch;
    }

    /** Returns whether a prior visible Harmony projection needs retrying. */
    public boolean hasPendingHarmonyViewRefresh() {
        return harmonyViewRefreshPending;
    }

    public boolean applyManaConversion(final ManaConversionMatrix matrix,
            final Card card, final SpellAbility sa) {
        boolean changed = false;
        for (final PlayerSpellRule rule : rules.values()) {
            changed |= rule.applyManaConversion(matrix, owner, card, sa);
        }
        return changed;
    }

    public boolean applyOwnedHarmonyManaConversion(
            final ManaConversionMatrix matrix, final Card card,
            final SpellAbility sa) {
        boolean changed = false;
        for (final PlayerSpellRule rule : rules.values()) {
            changed |= rule.applyOwnedHarmonyManaConversion(matrix, owner,
                    card, sa);
        }
        return changed;
    }

    public int size() {
        return rules.size();
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public void clear() {
        final boolean refresh = hasHarmonyRules()
                || harmonyViewRefreshPending;
        rules.clear();
        stackingSequence = 0;
        if (refresh) {
            advanceHarmonyEpoch();
            refreshCardViews();
        }
    }

    public void copyFrom(final PlayerSpellRuleRegistry source) {
        if (source == null) {
            throw new IllegalArgumentException("source registry must not be null");
        }
        if (source == this) {
            if (harmonyViewRefreshPending) {
                refreshCardViews();
            }
            return;
        }
        final boolean refresh = hasHarmonyRules()
                || source.hasHarmonyRules() || harmonyViewRefreshPending;
        rules.clear();
        rules.putAll(source.rules);
        stackingSequence = source.stackingSequence;
        if (refresh) {
            advanceHarmonyEpoch();
            refreshCardViews();
        }
    }

    /** Encodes the registry for developer snapshots and puzzle game states. */
    public String toStateString() {
        if (rules.isEmpty()) {
            return "";
        }
        final StringBuilder result = new StringBuilder()
                .append(stackingSequence);
        for (final PlayerSpellRule rule : rules.values()) {
            result.append(';')
                    .append(encode(rule.getKey())).append(',')
                    .append(encode(rule.getValidCardRestriction())).append(',')
                    .append(encode(rule.getValidSpellAbilityRestriction())).append(',')
                    .append(rule.getGenericReduction()).append(',')
                    .append(encode(rule.getManaConversion())).append(',')
                    .append(rule.isHarmony()).append(',')
                    .append(rule.getHarmonyReduction()).append(',')
                    .append(encode(String.join("\u001F", rule.getNamedCards())));
        }
        return result.toString();
    }

    /** Replaces the registry from a string produced by {@link #toStateString()}. */
    public void restoreFromStateString(final String state) {
        if (state == null || state.isEmpty()) {
            clear();
            return;
        }
        final String[] entries = state.split(";", -1);
        final long restoredSequence;
        try {
            restoredSequence = Long.parseLong(entries[0]);
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid player spell rule sequence", ex);
        }
        if (restoredSequence < 0) {
            throw new IllegalArgumentException("Player spell rule sequence must not be negative");
        }

        final Map<String, PlayerSpellRule> restored = new LinkedHashMap<>();
        for (int i = 1; i < entries.length; i++) {
            final String[] fields = entries[i].split(",", -1);
            if (fields.length != 5 && fields.length != 7 && fields.length != 8) {
                throw new IllegalArgumentException("Invalid player spell rule state entry");
            }
            final int reduction;
            try {
                reduction = Integer.parseInt(fields[3]);
            } catch (final NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid player spell rule reduction", ex);
            }
            final boolean harmony;
            final int harmonyReduction;
            if (fields.length == 5) {
                harmony = false;
                harmonyReduction = 0;
            } else {
                if (!"true".equals(fields[5]) && !"false".equals(fields[5])) {
                    throw new IllegalArgumentException(
                            "Invalid player spell rule Harmony flag");
                }
                harmony = Boolean.parseBoolean(fields[5]);
                try {
                    harmonyReduction = Integer.parseInt(fields[6]);
                } catch (final NumberFormatException ex) {
                    throw new IllegalArgumentException(
                            "Invalid player spell rule Harmony reduction", ex);
                }
            }
            final Set<String> namedCards = fields.length == 8
                    ? Set.of(decode(fields[7]).split("\u001F", -1)) : Set.of();
            final PlayerSpellRule rule = new PlayerSpellRule(
                    decode(fields[0]), decode(fields[1]), decode(fields[2]),
                    reduction, decode(fields[4]), harmony, harmonyReduction,
                    namedCards);
            if (restored.putIfAbsent(rule.getKey(), rule) != null) {
                throw new IllegalArgumentException("Duplicate player spell rule key: "
                        + rule.getKey());
            }
        }
        final boolean refresh = hasHarmonyRules()
                || restored.values().stream().anyMatch(PlayerSpellRule::isHarmony)
                || harmonyViewRefreshPending;
        rules.clear();
        rules.putAll(restored);
        stackingSequence = restoredSequence;
        if (refresh) {
            advanceHarmonyEpoch();
            refreshCardViews();
        }
    }

    /**
     * Rebuilds only cards that can currently need an eager visible update.
     * Hidden libraries and sideboards are intentionally left lazy; live
     * keyword queries use the Harmony epoch and remain correct without view
     * materialization. This never accesses the card database.
     */
    public void refreshCardViews() {
        lastHarmonyViewRefreshCount = 0;
        lastHarmonyViewRefreshFailureCount = 0;
        if (owner == null || owner.getGame() == null) {
            harmonyViewRefreshPending = false;
            return;
        }
        final Set<Card> cards = Collections.newSetFromMap(
                new IdentityHashMap<>());
        try {
            cards.addAll(owner.getGame().getCardsInOwnedBy(
                    HARMONY_EAGER_VIEW_ZONES, owner));
            for (final Player player : owner.getGame().getPlayers()) {
                if (player.getExtraZones() == null) {
                    continue;
                }
                for (final forge.game.zone.PlayerZone zone
                        : player.getExtraZones()) {
                    if (zone.is(ZoneType.ExtraHand)) {
                        for (final Card card : zone) {
                            if (card.getOwner() == owner) {
                                cards.add(card);
                            }
                        }
                    }
                }
            }
        } catch (final RuntimeException ex) {
            recordHarmonyViewRefreshFailure(null, ex);
            harmonyViewRefreshPending = true;
            return;
        }
        for (final Card card : cards) {
            harmonyViewScannedCardCount++;
            try {
                if (card.refreshHarmonyKeywordView()) {
                    lastHarmonyViewRefreshCount++;
                }
            } catch (final RuntimeException ex) {
                recordHarmonyViewRefreshFailure(card, ex);
            }
        }
        harmonyViewRefreshPending = lastHarmonyViewRefreshFailureCount > 0;
    }

    int getLastHarmonyViewRefreshCount() {
        return lastHarmonyViewRefreshCount;
    }

    int getLastHarmonyViewRefreshFailureCount() {
        return lastHarmonyViewRefreshFailureCount;
    }

    long getHarmonyViewScannedCardCount() {
        return harmonyViewScannedCardCount;
    }

    /** Records a non-fatal view failure for a later idempotent retry. */
    public void recordHarmonyViewRefreshFailure(final Card card,
            final RuntimeException ex) {
        lastHarmonyViewRefreshFailureCount++;
        harmonyViewRefreshPending = true;
        System.err.println("Failed to refresh Harmony view"
                + (card == null ? "" : " for card " + card.getId())
                + ": " + ex.getMessage());
    }

    private void advanceHarmonyEpoch() {
        harmonyEpoch++;
    }

    private static String encode(final String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(final String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid player spell rule text", ex);
        }
    }
}
