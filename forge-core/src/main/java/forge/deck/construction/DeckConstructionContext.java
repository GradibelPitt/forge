package forge.deck.construction;

import forge.deck.DeckSection;
import forge.item.PaperCard;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Injected, indexed catalog access and user choices used by the UI-independent planner.
 * The context and everything it can reach are confined to the planning thread through commit completion.
 */
public final class DeckConstructionContext {
    public static final int MAX_CHOICES = 10_000;
    public static final int MAX_CATALOG_GENERATION_UTF8_BYTES = 4_096;
    public static final int MAX_LEGALITY_GENERATION_UTF8_BYTES = 4_096;
    public static final long MAX_CHOICE_ENCODING_UTF8_BYTES = 1_048_576L;

    @FunctionalInterface
    public interface PrintingResolver {
        /**
         * Resolve only this canonical card name. Implementations must be read-only, must not mutate the deck,
         * CardPools, ledger, or context, and must not enumerate the complete CardDb.
         */
        List<PaperCard> findByCanonicalName(String canonicalCardName);
    }

    @FunctionalInterface
    public interface CommitFaultInjector {
        void afterMutation(DeckSection section, int mutationIndex);
    }

    @FunctionalInterface
    public interface RollbackFaultInjector {
        void beforeRollbackMutation(DeckSection section, int mutationIndex);
    }

    @FunctionalInterface
    public interface FinalPoolValidator {
        /**
         * Return null/empty when valid, otherwise a bounded user-facing rejection reason. Implementations must be
         * read-only and must not mutate the deck, CardPools, ledger, or context. Preview entries contain only
         * immutable value objects and never expose shared PaperCard/CardRules instances.
         */
        String validate(List<DeckConstructionPlan.FinalPoolEntry> finalPool,
                DeckConstructionPolicy constructionPolicy);
    }

    private final PrintingResolver resolver;
    private final String catalogGeneration;
    private final Map<DeckConstructionRule.RuleKey, DeckPrintingKey> choices;
    private final CommitFaultInjector commitFaultInjector;
    private final RollbackFaultInjector rollbackFaultInjector;
    private final FinalPoolValidator finalPoolValidator;
    private final String legalityGeneration;
    private final String validationFailure;

    private DeckConstructionContext(final Builder builder) {
        resolver = builder.resolver;
        catalogGeneration = builder.catalogGeneration == null ? "" : builder.catalogGeneration;
        choices = Map.copyOf(builder.choices);
        commitFaultInjector = builder.commitFaultInjector;
        rollbackFaultInjector = builder.rollbackFaultInjector;
        finalPoolValidator = builder.finalPoolValidator;
        legalityGeneration = builder.legalityGeneration == null ? "" : builder.legalityGeneration;
        String failure = builder.validationFailure;
        if (failure == null && resolver == null) {
            failure = "printing resolver is required";
        }
        if (failure == null && (catalogGeneration.length() > MAX_CATALOG_GENERATION_UTF8_BYTES
                || catalogGeneration.getBytes(StandardCharsets.UTF_8).length
                        > MAX_CATALOG_GENERATION_UTF8_BYTES)) {
            failure = "catalog generation exceeds the UTF-8 byte limit";
        }
        if (failure == null && (legalityGeneration.length() > MAX_LEGALITY_GENERATION_UTF8_BYTES
                || legalityGeneration.getBytes(StandardCharsets.UTF_8).length
                        > MAX_LEGALITY_GENERATION_UTF8_BYTES)) {
            failure = "legality generation exceeds the UTF-8 byte limit";
        }
        if (failure == null && finalPoolValidator != null && legalityGeneration.isBlank()) {
            failure = "a final-pool validator requires a nonblank legality generation";
        }
        validationFailure = failure;
    }

    public static Builder builder(final PrintingResolver resolver) {
        return new Builder(resolver);
    }

    public PrintingResolver getResolver() {
        return resolver;
    }

    public String getCatalogGeneration() {
        return catalogGeneration;
    }

    public String getLegalityGeneration() {
        return legalityGeneration;
    }

    public DeckPrintingKey getChoice(final String sourceCanonicalName, final String ruleId) {
        return choices.get(DeckConstructionRule.RuleKey.of(sourceCanonicalName, ruleId));
    }

    CommitFaultInjector getCommitFaultInjector() {
        return commitFaultInjector;
    }

    RollbackFaultInjector getRollbackFaultInjector() {
        return rollbackFaultInjector;
    }

    FinalPoolValidator getFinalPoolValidator() {
        return finalPoolValidator;
    }

    boolean hasFinalPoolValidator() {
        return finalPoolValidator != null;
    }

    String getValidationFailure() {
        return validationFailure;
    }

    public static final class Builder {
        private final PrintingResolver resolver;
        private String catalogGeneration = "";
        private final Map<DeckConstructionRule.RuleKey, DeckPrintingKey> choices = new HashMap<>();
        private final Map<DeckConstructionRule.RuleKey, Long> choiceEncodingSizes = new HashMap<>();
        private long totalChoiceEncodingBytes;
        private CommitFaultInjector commitFaultInjector;
        private RollbackFaultInjector rollbackFaultInjector;
        private FinalPoolValidator finalPoolValidator;
        private String legalityGeneration = "";
        private String validationFailure;

        private Builder(final PrintingResolver resolver) {
            this.resolver = resolver;
        }

        public Builder catalogGeneration(final String generation) {
            catalogGeneration = generation;
            return this;
        }

        public Builder choose(final String sourceCardName, final String ruleId, final DeckPrintingKey printing) {
            if (sourceCardName == null || ruleId == null || printing == null) {
                validationFailure = "choice source, rule id, and exact printing are required";
            } else {
                try {
                    final DeckConstructionRule.RuleKey key = DeckConstructionRule.RuleKey.of(sourceCardName, ruleId);
                    if (choices.size() >= MAX_CHOICES && !choices.containsKey(key)) {
                        validationFailure = "choice count exceeds the limit";
                    } else {
                        final long encodedSize = choiceEncodingSize(key, printing);
                        final long oldSize = choiceEncodingSizes.getOrDefault(key, 0L);
                        final long nextTotal;
                        try {
                            nextTotal = Math.addExact(
                                    Math.subtractExact(totalChoiceEncodingBytes, oldSize), encodedSize);
                        } catch (final ArithmeticException exception) {
                            validationFailure = "choice encoding byte count overflow";
                            return this;
                        }
                        if (nextTotal > MAX_CHOICE_ENCODING_UTF8_BYTES) {
                            validationFailure = "choice encoding exceeds the cumulative byte limit";
                        } else {
                            choices.put(key, printing.copy());
                            choiceEncodingSizes.put(key, encodedSize);
                            totalChoiceEncodingBytes = nextTotal;
                        }
                    }
                } catch (final IllegalArgumentException | ArithmeticException exception) {
                    validationFailure = "choice source or rule id is invalid";
                }
            }
            return this;
        }

        /** Test/diagnostic seam; production callers normally leave it unset. */
        public Builder commitFaultInjector(final CommitFaultInjector injector) {
            commitFaultInjector = injector;
            return this;
        }

        /** Test/diagnostic seam for proving explicit state-unknown rollback handling. */
        public Builder rollbackFaultInjector(final RollbackFaultInjector injector) {
            rollbackFaultInjector = injector;
            return this;
        }

        /**
         * Installs a read-only validator. A non-null validator requires a nonblank generation token, and callers
         * must change that token whenever the validator implementation or any legality input changes.
         */
        public Builder finalPoolValidator(final FinalPoolValidator validator, final String generation) {
            finalPoolValidator = validator;
            legalityGeneration = generation;
            return this;
        }

        public DeckConstructionContext build() {
            return new DeckConstructionContext(this);
        }

        private static long choiceEncodingSize(final DeckConstructionRule.RuleKey key,
                final DeckPrintingKey printing) {
            final long sourceBytes = key.getSourceCanonicalName().getBytes(StandardCharsets.UTF_8).length;
            final long idBytes = key.getRuleId().getBytes(StandardCharsets.UTF_8).length;
            return Math.addExact(Math.addExact(sourceBytes, idBytes), printing.encodedLength());
        }
    }
}
