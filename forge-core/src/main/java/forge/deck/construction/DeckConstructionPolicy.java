package forge.deck.construction;

import forge.deck.DeckSection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable O(1) lookup of active, narrowly scoped ALLOW rules. */
public final class DeckConstructionPolicy {
    private final Set<Key> grants;

    DeckConstructionPolicy(final Set<Key> grants) {
        this.grants = Collections.unmodifiableSet(new HashSet<>(grants));
    }

    public static DeckConstructionPolicy empty() {
        return new DeckConstructionPolicy(Set.of());
    }

    public boolean allows(final DeckConstructionRule.Constraint constraint, final String cardName,
            final DeckSection section) {
        if (constraint == null || cardName == null) {
            return false;
        }
        final DeckSection scopedSection = constraint == DeckConstructionRule.Constraint.SECTION ? section : null;
        return grants.contains(new Key(constraint,
                DeckConstructionRule.canonicalCardNameKey(cardName), scopedSection));
    }

    public int copyLimit(final String cardName, final DeckSection section, final int defaultLimit) {
        return allows(DeckConstructionRule.Constraint.COPY_LIMIT, cardName, section)
                ? Integer.MAX_VALUE : defaultLimit;
    }

    static Key keyFor(final DeckConstructionRule rule) {
        return new Key(rule.getConstraint(), DeckConstructionRule.canonicalCardNameKey(rule.getCardName()),
                rule.getConstraint() == DeckConstructionRule.Constraint.SECTION ? rule.getTarget() : null);
    }

    static final class Key {
        private final DeckConstructionRule.Constraint constraint;
        private final String targetCanonicalName;
        private final DeckSection section;

        Key(final DeckConstructionRule.Constraint constraint, final String targetCanonicalName,
                final DeckSection section) {
            this.constraint = Objects.requireNonNull(constraint, "constraint");
            this.targetCanonicalName = Objects.requireNonNull(targetCanonicalName, "targetCanonicalName");
            this.section = section;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Key other)) {
                return false;
            }
            return constraint == other.constraint
                    && targetCanonicalName.equals(other.targetCanonicalName)
                    && section == other.section;
        }

        @Override
        public int hashCode() {
            return Objects.hash(constraint, targetCanonicalName, section);
        }
    }
}
