package forge.gui;

import forge.util.IHasName;
import forge.util.ITranslatable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Immutable, candidate-scoped text index used by choice dialogs. It never
 * consults a card database: every result is one of the objects supplied to the
 * constructor, in deterministic input order within the same match rank.
 */
public final class CardNameSearchIndex<T> {
    private final List<Entry<T>> entries;

    public CardNameSearchIndex(final Collection<T> candidates, final Function<T, String> display) {
        this(candidates, display, () -> false);
    }

    public CardNameSearchIndex(final Collection<T> candidates, final Function<T, String> display,
                               final BooleanSupplier cancelled) {
        final List<Entry<T>> indexed = new ArrayList<>(candidates.size());
        int order = 0;
        for (final T candidate : candidates) {
            if (cancelled.getAsBoolean()) {
                break;
            }
            final Set<String> aliases = new LinkedHashSet<>();
            addAlias(aliases, display == null ? null : display.apply(candidate));
            if (candidate instanceof ITranslatable translatable) {
                addAlias(aliases, translatable.getTranslatedName());
                addAlias(aliases, translatable.getUntranslatedName());
            }
            if (candidate instanceof IHasName named) {
                addAlias(aliases, named.getName());
            }
            addAlias(aliases, candidate == null ? null : candidate.toString());
            indexed.add(new Entry<>(candidate, order++, List.copyOf(aliases)));
        }
        entries = List.copyOf(indexed);
    }

    public List<T> search(final String query) {
        return search(query, () -> false, true);
    }

    public List<T> search(final String query, final BooleanSupplier cancelled) {
        return search(query, cancelled, true);
    }

    /** Lightweight UI-thread path for mobile: exact/prefix/substring only. */
    public List<T> searchWithoutFuzzy(final String query) {
        final String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return entries.stream().map(Entry::item).toList();
        }
        final List<T> exact = new ArrayList<>();
        final List<T> prefix = new ArrayList<>();
        final List<T> substring = new ArrayList<>();
        for (final Entry<T> entry : entries) {
            int best = Integer.MAX_VALUE;
            for (final String alias : entry.aliases()) {
                if (alias.equals(normalizedQuery)) {
                    best = 0;
                    break;
                }
                if (alias.startsWith(normalizedQuery)) {
                    best = Math.min(best, 1);
                } else if (alias.contains(normalizedQuery)) {
                    best = Math.min(best, 2);
                }
            }
            if (best == 0) {
                exact.add(entry.item());
            } else if (best == 1) {
                prefix.add(entry.item());
            } else if (best == 2) {
                substring.add(entry.item());
            }
        }
        final List<T> result = new ArrayList<>(exact.size() + prefix.size() + substring.size());
        result.addAll(exact);
        result.addAll(prefix);
        result.addAll(substring);
        return result;
    }

    private List<T> search(final String query, final BooleanSupplier cancelled, final boolean allowFuzzy) {
        final String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return entries.stream().map(Entry::item).toList();
        }

        final List<Match<T>> matches = new ArrayList<>();
        for (final Entry<T> entry : entries) {
            if (cancelled.getAsBoolean()) {
                return List.of();
            }
            int best = Integer.MAX_VALUE;
            for (final String alias : entry.aliases()) {
                best = Math.min(best, rank(alias, normalizedQuery, allowFuzzy));
                if (best == 0) {
                    break;
                }
            }
            if (best != Integer.MAX_VALUE) {
                matches.add(new Match<>(entry.item(), entry.order(), best));
            }
        }
        matches.sort(Comparator.comparingInt(Match<T>::rank).thenComparingInt(Match<T>::order));
        return matches.stream().map(Match::item).toList();
    }

    public static String normalize(final String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        final String folded = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        final String decomposed = Normalizer.normalize(folded, Normalizer.Form.NFD);
        final StringBuilder result = new StringBuilder(decomposed.length());
        decomposed.codePoints()
                .filter(cp -> Character.getType(cp) != Character.NON_SPACING_MARK)
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    public static String displayLabel(final Object value, final Function<Object, String> display) {
        final String translated = safe(display == null ? null : display.apply(value));
        final String canonical = value instanceof IHasName named ? safe(named.getName()) : "";
        if (!translated.isEmpty() && !canonical.isEmpty()
                && !normalize(translated).equals(normalize(canonical))) {
            return translated + " / " + canonical;
        }
        return !translated.isEmpty() ? translated : canonical;
    }

    private static void addAlias(final Set<String> aliases, final String value) {
        final String normalized = normalize(value);
        if (!normalized.isEmpty()) {
            aliases.add(normalized);
        }
    }

    private static int rank(final String alias, final String query, final boolean allowFuzzy) {
        if (alias.equals(query)) {
            return 0;
        }
        if (alias.startsWith(query)) {
            return 1;
        }
        if (alias.contains(query)) {
            return 2;
        }
        if (!allowFuzzy) {
            return Integer.MAX_VALUE;
        }
        final int threshold = query.length() <= 4 ? 1 : query.length() <= 10 ? 2 : 3;
        return boundedLevenshtein(alias, query, threshold) <= threshold ? 3 : Integer.MAX_VALUE;
    }

    private static int boundedLevenshtein(final String left, final String right, final int limit) {
        if (Math.abs(left.length() - right.length()) > limit) {
            return limit + 1;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= right.length(); j++) {
                final int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > limit) {
                return limit + 1;
            }
            final int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }

    private record Entry<T>(T item, int order, List<String> aliases) { }
    private record Match<T>(T item, int order, int rank) { }
}
