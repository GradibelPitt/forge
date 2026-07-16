package forge.game.player;

import forge.game.card.Card;
import forge.game.mana.ManaConversionMatrix;
import forge.game.spellability.SpellAbility;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stores source-independent spell rules for one player for the current game. */
public final class PlayerSpellRuleRegistry {
    private final Player owner;
    private final Map<String, PlayerSpellRule> rules = new LinkedHashMap<>();
    private long stackingSequence;

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
        final PlayerSpellRule candidate = new PlayerSpellRule(stableKey,
                validCard, validSpellAbility, genericReduction, manaConversion);
        return registerCandidate(candidate);
    }

    /** Validates a stable registration without mutating this registry. */
    public void validateRegistration(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        wouldAddRegistration(stableKey, validCard, validSpellAbility,
                genericReduction, manaConversion);
    }

    /**
     * Validates a stable registration and reports whether it would add a new
     * rule, without changing registry contents or the stacking sequence.
     */
    public boolean wouldAddRegistration(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        final PlayerSpellRule candidate = new PlayerSpellRule(stableKey,
                validCard, validSpellAbility, genericReduction, manaConversion);
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
                if (rule.coversPaymentScope(probe,
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

    /** Validates rule data even when an effect currently has no players. */
    public static void validateRuleDefinition(final String stableKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        new PlayerSpellRule(stableKey, validCard, validSpellAbility,
                genericReduction, manaConversion);
    }

    private PlayerSpellRule registerCandidate(final PlayerSpellRule candidate) {
        validateCandidate(candidate);
        final PlayerSpellRule existing = rules.get(candidate.getKey());
        if (existing == null) {
            rules.put(candidate.getKey(), candidate);
            return candidate;
        }
        return existing;
    }

    private void validateCandidate(final PlayerSpellRule candidate) {
        final PlayerSpellRule existing = rules.get(candidate.getKey());
        if (existing != null && !existing.equals(candidate)) {
            throw new IllegalArgumentException("Spell rule key already has different data: "
                    + candidate.getKey());
        }
    }

    /** Registers another independently stacking instance of a rule. */
    public PlayerSpellRule registerStacking(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        final PlayerSpellRule candidate = prepareStackingRegistration(baseKey,
                validCard, validSpellAbility, genericReduction, manaConversion);
        rules.put(candidate.getKey(), candidate);
        stackingSequence++;
        return candidate;
    }

    /** Validates the next stacking registration without changing its sequence. */
    public void validateStackingRegistration(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        prepareStackingRegistration(baseKey, validCard, validSpellAbility,
                genericReduction, manaConversion);
    }

    private PlayerSpellRule prepareStackingRegistration(final String baseKey,
            final String validCard, final String validSpellAbility,
            final int genericReduction, final String manaConversion) {
        if (baseKey == null || baseKey.trim().isEmpty()) {
            throw new IllegalArgumentException("baseKey must not be blank");
        }
        if (stackingSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Player spell rule stacking sequence exhausted");
        }
        final long nextSequence = stackingSequence + 1;
        final String uniqueKey = baseKey.trim() + "#" + nextSequence;
        final PlayerSpellRule candidate = new PlayerSpellRule(uniqueKey,
                validCard, validSpellAbility, genericReduction, manaConversion);
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

    public boolean applyManaConversion(final ManaConversionMatrix matrix,
            final Card card, final SpellAbility sa) {
        boolean changed = false;
        for (final PlayerSpellRule rule : rules.values()) {
            changed |= rule.applyManaConversion(matrix, owner, card, sa);
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
        rules.clear();
        stackingSequence = 0;
    }

    public void copyFrom(final PlayerSpellRuleRegistry source) {
        if (source == null) {
            throw new IllegalArgumentException("source registry must not be null");
        }
        if (source == this) {
            return;
        }
        clear();
        rules.putAll(source.rules);
        stackingSequence = source.stackingSequence;
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
                    .append(encode(rule.getManaConversion()));
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
            if (fields.length != 5) {
                throw new IllegalArgumentException("Invalid player spell rule state entry");
            }
            final int reduction;
            try {
                reduction = Integer.parseInt(fields[3]);
            } catch (final NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid player spell rule reduction", ex);
            }
            final PlayerSpellRule rule = new PlayerSpellRule(
                    decode(fields[0]), decode(fields[1]), decode(fields[2]),
                    reduction, decode(fields[4]));
            if (restored.putIfAbsent(rule.getKey(), rule) != null) {
                throw new IllegalArgumentException("Duplicate player spell rule key: "
                        + rule.getKey());
            }
        }
        rules.clear();
        rules.putAll(restored);
        stackingSequence = restoredSequence;
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
