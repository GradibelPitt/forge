package forge.deck.construction;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * UI-independent, optimistic transaction service for construction-time card contributions.
 * It never enumerates CardDb: every missing card name is resolved through the injected indexed resolver once.
 * Plans are thread-confined: the deck, its CardPools, the context, and every write that can affect them must remain
 * on the exact planning Thread, without concurrent or bypass mutation, until commit returns.
 */
public final class DeckConstructionService {
    public static final int MAX_ACTIVATED_RULES = 10_000;
    public static final int MAX_DEPENDENCY_EDGES = 50_000;
    public static final int MAX_DEPENDENCY_DEPTH = 256;
    public static final int MAX_TOTAL_DESIRED_MANAGED = 100_000;
    public static final int MAX_EDITS = 100_000;
    public static final int MAX_PRINTINGS_PER_NAME = 10_000;
    public static final int MAX_TOTAL_RESOLVED_PRINTINGS = 100_000;
    public static final int MAX_RESOLVER_RAW_ROWS = 20_000;
    public static final int MAX_SIGNATURE_ITEMS = 10_000;
    public static final long MAX_SIGNATURE_UTF8_BYTES = 16L * 1_024L * 1_024L;

    public DeckConstructionPlan plan(final Deck deck, final DeckConstructionContext context,
            final List<DeckConstructionEdit> edits) {
        if (deck == null || context == null || edits == null) {
            return blocked(DeckConstructionPlan.Status.BLOCKED,
                    issue(DeckConstructionIssue.Code.INVALID_EDIT, "deck, context, and edits are required"));
        }
        if (context.getValidationFailure() != null) {
            return blocked(DeckConstructionPlan.Status.BLOCKED,
                    issue(DeckConstructionIssue.Code.RESOURCE_LIMIT, context.getValidationFailure()));
        }
        if (edits.size() > MAX_EDITS) {
            return blocked(DeckConstructionPlan.Status.BLOCKED,
                    issue(DeckConstructionIssue.Code.RESOURCE_LIMIT, "edit count exceeds the limit"));
        }
        if (deck.hasDeferredSections()) {
            return blocked(DeckConstructionPlan.Status.UNRESOLVED,
                    issue(DeckConstructionIssue.Code.DEFERRED_DECK_NOT_LOADED,
                            "deck sections must be explicitly materialized before construction planning"));
        }
        try {
            final DeckConstructionLedger ledger = deck.getConstructionLedger();
            if (ledger.getStatus() != DeckConstructionLedger.Status.TRACKED) {
                return blocked(DeckConstructionPlan.Status.UNRESOLVED,
                        issue(DeckConstructionIssue.Code.LEDGER_NOT_TRACKED,
                                "construction ledger is " + ledger.getStatus()));
            }

            final Scan scan = scan(deck);
            final String baseFingerprint = baseFingerprint(scan, ledger, context.getCatalogGeneration(),
                    context.getLegalityGeneration(), context.hasFinalPoolValidator());
            final DeckConstructionIssue drift = verifyLedger(scan, ledger);
            if (drift != null) {
                return blocked(DeckConstructionPlan.Status.UNRESOLVED, drift);
            }

            final Planner planner = new Planner(context, scan, ledger);
            final DeckConstructionIssue editFailure = planner.applyEdits(edits);
            if (editFailure != null) {
                return blocked(DeckConstructionPlan.Status.BLOCKED, editFailure);
            }
            planner.buildClosure();
            planner.verifyMigrations();
            planner.verifyDependencyGraph();

            final FinalState finalState = planner.finalState();
            final List<DeckConstructionPlan.FinalPoolEntry> preview = preview(finalState.pools);
            final DeckConstructionPolicy policy = new DeckConstructionPolicy(planner.policyGrants);
            final DeckConstructionIssue validationIssue = validateFinalPool(context, preview, policy);
            if (validationIssue != null) {
                return blocked(DeckConstructionPlan.Status.BLOCKED, validationIssue);
            }
            final String sourceFingerprint = sourceFingerprint(baseFingerprint, finalState.exactManifest);
            final boolean changed = !samePools(scan.entries, finalState.slotCounts)
                    || !ledger.semanticallyEquals(finalState.ledger);
            return new DeckConstructionPlan(changed ? DeckConstructionPlan.Status.READY
                    : DeckConstructionPlan.Status.NO_CHANGE, List.of(), sourceFingerprint, baseFingerprint,
                    finalState.pools, finalState.ledger, policy,
                    preview, finalState.exactManifest, Thread.currentThread());
        } catch (final PlanFailure failure) {
            return blocked(failure.status, failure.issue);
        } catch (final RuntimeException exception) {
            return blocked(DeckConstructionPlan.Status.BLOCKED,
                    issue(DeckConstructionIssue.Code.INTERNAL_ERROR, safeMessage(exception)));
        }
    }

    public DeckConstructionCommitResult commit(final Deck deck, final DeckConstructionContext context,
            final DeckConstructionPlan plan) {
        if (deck == null || context == null || plan == null || !plan.canCommit()) {
            return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.BLOCKED,
                    List.of(issue(DeckConstructionIssue.Code.INVALID_EDIT, "a committable plan is required")));
        }
        if (context.getValidationFailure() != null) {
            return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.BLOCKED,
                    List.of(issue(DeckConstructionIssue.Code.RESOURCE_LIMIT,
                            context.getValidationFailure())));
        }
        if (plan.ownerThread() != Thread.currentThread()) {
            return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.BLOCKED,
                    List.of(issue(DeckConstructionIssue.Code.THREAD_CONFINEMENT,
                            "construction plans must be committed on their planning thread")));
        }
        if (deck.hasDeferredSections()) {
            return stale();
        }

        try {
            final Scan current = scan(deck);
            final String currentBase = baseFingerprint(current, deck.getConstructionLedger(),
                    context.getCatalogGeneration(), context.getLegalityGeneration(),
                    context.hasFinalPoolValidator());
            if (!currentBase.equals(plan.baseFingerprint())
                    || !sourceFingerprint(currentBase, plan.getExactManifest())
                            .equals(plan.getSourceFingerprint())
                    || !exactManifestStillMatches(current, context, plan.getExactManifest())) {
                return stale();
            }
            preflightFinalState(plan);
            final DeckConstructionIssue validationIssue = validateFinalPool(
                    context, plan.getFinalPool(), plan.getPolicy());
            if (validationIssue != null) {
                return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.STALE_PLAN,
                        List.of(validationIssue));
            }
            if (plan.getStatus() == DeckConstructionPlan.Status.NO_CHANGE) {
                if (!baseStillMatchesWithoutCallbacks(deck, context, plan)) {
                    return stale();
                }
                return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.NO_CHANGE, List.of());
            }
        } catch (final RuntimeException exception) {
            return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.STALE_PLAN,
                    List.of(issue(DeckConstructionIssue.Code.STALE_PLAN, safeMessage(exception))));
        }

        final Map<DeckSection, SectionBackup> backups = new EnumMap<>(DeckSection.class);
        final DeckConstructionLedger oldLedger;
        final List<DeckSection> changedSections;
        try {
            oldLedger = deck.getConstructionLedger().copy();
            changedSections = changedSections(deck, plan.finalPools());
        } catch (final RuntimeException exception) {
            return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.BLOCKED,
                    List.of(issue(DeckConstructionIssue.Code.COMMIT_FAILED, safeMessage(exception))));
        }
        int mutationIndex = 0;
        try {
            if (!baseStillMatchesWithoutCallbacks(deck, context, plan)) {
                return stale();
            }
        } catch (final RuntimeException exception) {
            return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.STALE_PLAN,
                    List.of(issue(DeckConstructionIssue.Code.STALE_PLAN, safeMessage(exception))));
        }
        try {
            for (final DeckSection section : changedSections) {
                final CardPool existing = deck.getLoadedSectionWithoutMaterializing(section);
                backups.put(section, new SectionBackup(existing, existing == null ? null : new CardPool(existing)));
                final CardPool target = existing == null ? new CardPool() : existing;
                target.clear();
                for (final DeckConstructionPlan.PoolEntry entry
                        : plan.finalPools().getOrDefault(section, List.of())) {
                    target.add(entry.card(), entry.count());
                }
                if (existing == null) {
                    deck.putSection(section, target);
                }
                final DeckConstructionContext.CommitFaultInjector fault = context.getCommitFaultInjector();
                if (fault != null) {
                    fault.afterMutation(section, ++mutationIndex);
                }
            }
            deck.setConstructionLedger(plan.finalLedger());
            final DeckConstructionContext.CommitFaultInjector fault = context.getCommitFaultInjector();
            if (fault != null) {
                fault.afterMutation(null, ++mutationIndex);
            }
            return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.COMMITTED, List.of());
        } catch (final RuntimeException exception) {
            try {
                rollback(deck, backups, oldLedger, context);
            } catch (final RuntimeException rollbackFailure) {
                return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.ROLLBACK_FAILED,
                        List.of(issue(DeckConstructionIssue.Code.ROLLBACK_FAILED_STATE_UNKNOWN,
                                "rollback failed; deck state is unknown: " + safeMessage(rollbackFailure))));
            }
            return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.ROLLED_BACK,
                    List.of(issue(DeckConstructionIssue.Code.COMMIT_FAILED, safeMessage(exception))));
        }
    }

    private static DeckConstructionPlan blocked(final DeckConstructionPlan.Status status,
            final DeckConstructionIssue issue) {
        return new DeckConstructionPlan(status, List.of(issue), "", "", Map.of(), null,
                DeckConstructionPolicy.empty(), List.of(), Map.of(), Thread.currentThread());
    }

    private static DeckConstructionCommitResult stale() {
        return new DeckConstructionCommitResult(DeckConstructionCommitResult.Status.STALE_PLAN,
                List.of(issue(DeckConstructionIssue.Code.STALE_PLAN, "deck, ledger, rules, or catalog changed")));
    }

    private static DeckConstructionIssue issue(final DeckConstructionIssue.Code code, final String message) {
        return new DeckConstructionIssue(code, message);
    }

    private static Scan scan(final Deck deck) {
        final SortedMap<DeckConstructionLedger.SlotKey, ActualSlot> entries = new TreeMap<>();
        long total = 0;
        for (final DeckSection section : DeckSection.values()) {
            final CardPool pool = deck.getLoadedSectionWithoutMaterializing(section);
            if (pool == null) {
                continue;
            }
            for (final Map.Entry<PaperCard, Integer> entry : pool) {
                final PaperCard card = entry.getKey();
                final Integer count = entry.getValue();
                if (card == null || count == null || count <= 0) {
                    throw new IllegalArgumentException("deck contains an invalid card pool entry");
                }
                final DeckPrintingKey printing = DeckPrintingKey.from(card);
                final DeckConstructionLedger.SlotKey key = new DeckConstructionLedger.SlotKey(section, printing);
                if (entries.put(key, new ActualSlot(card, count, printing)) != null) {
                    throw new IllegalArgumentException("deck contains duplicate exact printing slots");
                }
                total = checkedAdd(total, count, Integer.MAX_VALUE, "deck card count overflow");
                if (entries.size() > DeckConstructionLedger.MAX_ENTRIES) {
                    throw new IllegalArgumentException("deck distinct entry limit exceeded");
                }
            }
        }
        return new Scan(entries);
    }

    private static DeckConstructionIssue verifyLedger(final Scan scan, final DeckConstructionLedger ledger) {
        final Map<DeckConstructionLedger.SlotKey, ActualSlot> actualBySlot = new HashMap<>(scan.entries);
        final Map<DeckConstructionLedger.SlotKey, DeckConstructionLedger.Entry> ownershipBySlot = new HashMap<>();
        for (final DeckConstructionLedger.Entry entry : ledger.getEntries()) {
            final DeckConstructionLedger.SlotKey slot = new DeckConstructionLedger.SlotKey(
                    entry.getSection(), entry.getPrintingKey());
            ownershipBySlot.put(slot, entry);
            final ActualSlot actual = actualBySlot.get(slot);
            if (actual == null || actual.count != entry.getTotalCount()
                    || !entry.getPrintingKey().matches(actual.card)) {
                return issue(DeckConstructionIssue.Code.LEDGER_DRIFT,
                        "deck pool no longer equals manual + managed ledger ownership");
            }
        }
        for (final Map.Entry<DeckConstructionLedger.SlotKey, ActualSlot> actual : scan.entries.entrySet()) {
            final DeckConstructionLedger.Entry ownership = ownershipBySlot.get(actual.getKey());
            if (ownership == null || ownership.getTotalCount() != actual.getValue().count
                    || !ownership.getPrintingKey().matches(actual.getValue().card)) {
                return issue(DeckConstructionIssue.Code.LEDGER_DRIFT,
                        "tracked deck contains a card pool slot without exact ownership");
            }
        }
        return null;
    }

    private static String baseFingerprint(final Scan scan, final DeckConstructionLedger ledger,
            final String catalogGeneration, final String legalityGeneration,
            final boolean finalPoolValidatorPresent) {
        final CanonicalDigest digest = new CanonicalDigest();
        digest.add("catalog", catalogGeneration == null ? "" : catalogGeneration);
        digest.add("legality", legalityGeneration == null ? "" : legalityGeneration);
        digest.add("legality-validator-present", Boolean.toString(finalPoolValidatorPresent));
        for (final Map.Entry<DeckConstructionLedger.SlotKey, ActualSlot> entry : scan.entries.entrySet()) {
            digest.add("section", entry.getKey().getSection().name());
            digest.add("printing", poolIdentityEncoding(entry.getKey().getPrintingKey()));
            digest.add("count", Integer.toString(entry.getValue().count));
        }
        for (final String line : ledger.toLines()) {
            digest.add("ledger", line);
        }
        return digest.finish();
    }

    private static String sourceFingerprint(final String base,
            final Map<DeckPrintingKey, String> exactManifest) {
        final CanonicalDigest digest = new CanonicalDigest();
        digest.add("base", base);
        exactManifest.forEach((printing, signature) -> {
            digest.add("manifest-printing", poolIdentityEncoding(printing));
            digest.add("manifest-signature", signature);
        });
        return digest.finish();
    }

    private static boolean exactManifestStillMatches(final Scan current,
            final DeckConstructionContext context, final Map<DeckPrintingKey, String> expected) {
        final ResourceBudget budget = new ResourceBudget();
        final SignatureComputer signatures = new SignatureComputer(budget);
        final Map<String, NavigableMap<DeckPrintingKey, ResolvedPrinting>> cache = new HashMap<>();
        final Map<DeckPrintingKey, String> currentExact = new TreeMap<>();
        for (final ActualSlot slot : current.entries.values()) {
            if (!expected.containsKey(slot.printing)) {
                continue;
            }
            final String signature = signatures.signature(slot.card);
            final String previous = currentExact.putIfAbsent(slot.printing, signature);
            if (previous != null && !previous.equals(signature)) {
                return false;
            }
        }
        final Map<String, List<Map.Entry<DeckPrintingKey, String>>> byName = new TreeMap<>();
        for (final Map.Entry<DeckPrintingKey, String> entry : expected.entrySet()) {
            final String existing = currentExact.get(entry.getKey());
            if (existing != null) {
                if (!existing.equals(entry.getValue())) {
                    return false;
                }
            } else {
                byName.computeIfAbsent(canonical(entry.getKey().getName()), ignored -> new ArrayList<>()).add(entry);
            }
        }
        for (final Map.Entry<String, List<Map.Entry<DeckPrintingKey, String>>> name : byName.entrySet()) {
            final NavigableMap<DeckPrintingKey, ResolvedPrinting> exact = resolve(
                    context, name.getKey(), cache, budget, signatures);
            for (final Map.Entry<DeckPrintingKey, String> expectedPrinting : name.getValue()) {
                final ResolvedPrinting actual = exact.get(expectedPrinting.getKey());
                if (actual == null || !actual.signature.equals(expectedPrinting.getValue())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Final optimistic check after every resolver/validator callback; deliberately invokes no external callback. */
    private static boolean baseStillMatchesWithoutCallbacks(final Deck deck,
            final DeckConstructionContext context, final DeckConstructionPlan plan) {
        final Scan latest = scan(deck);
        final String latestBase = baseFingerprint(latest, deck.getConstructionLedger(),
                context.getCatalogGeneration(), context.getLegalityGeneration(),
                context.hasFinalPoolValidator());
        return latestBase.equals(plan.baseFingerprint())
                && finalManifestStillMatchesWithoutCallbacks(latest, plan);
    }

    private static boolean finalManifestStillMatchesWithoutCallbacks(final Scan latest,
            final DeckConstructionPlan plan) {
        final Map<DeckPrintingKey, String> expected = plan.getExactManifest();
        final SignatureComputer currentSignatures = new SignatureComputer(new ResourceBudget());
        for (final ActualSlot slot : latest.entries.values()) {
            final String signature = expected.get(slot.printing);
            if (signature != null && !signature.equals(currentSignatures.signature(slot.card))) {
                return false;
            }
        }
        final Set<DeckPrintingKey> found = new HashSet<>();
        final SignatureComputer finalSignatures = new SignatureComputer(new ResourceBudget());
        for (final List<DeckConstructionPlan.PoolEntry> section : plan.finalPools().values()) {
            for (final DeckConstructionPlan.PoolEntry entry : section) {
                final DeckPrintingKey printing = DeckPrintingKey.from(entry.card());
                final String signature = expected.get(printing);
                if (signature == null || !signature.equals(finalSignatures.signature(entry.card()))) {
                    return false;
                }
                found.add(printing);
            }
        }
        return found.containsAll(expected.keySet());
    }

    private static List<DeckConstructionPlan.FinalPoolEntry> preview(
            final Map<DeckSection, List<DeckConstructionPlan.PoolEntry>> pools) {
        final List<DeckConstructionPlan.FinalPoolEntry> result = new ArrayList<>();
        for (final Map.Entry<DeckSection, List<DeckConstructionPlan.PoolEntry>> section : pools.entrySet()) {
            for (final DeckConstructionPlan.PoolEntry entry : section.getValue()) {
                result.add(new DeckConstructionPlan.FinalPoolEntry(
                        section.getKey(), entry.card(), entry.count()));
            }
        }
        return List.copyOf(result);
    }

    private static DeckConstructionIssue validateFinalPool(final DeckConstructionContext context,
            final List<DeckConstructionPlan.FinalPoolEntry> preview,
            final DeckConstructionPolicy policy) {
        if (context.getFinalPoolValidator() == null) {
            return null;
        }
        final String rejection;
        try {
            rejection = context.getFinalPoolValidator().validate(preview, policy);
        } catch (final RuntimeException exception) {
            return issue(DeckConstructionIssue.Code.LEGALITY_REJECTED,
                    "final-pool validator failed: " + safeMessage(exception));
        }
        if (rejection != null && !rejection.isBlank()) {
            return issue(DeckConstructionIssue.Code.LEGALITY_REJECTED, rejection);
        }
        return null;
    }

    private static void preflightFinalState(final DeckConstructionPlan plan) {
        final Map<DeckConstructionLedger.SlotKey, Integer> poolCounts = new TreeMap<>();
        final Set<DeckPrintingKey> poolPrintings = new TreeSet<>();
        for (final Map.Entry<DeckSection, List<DeckConstructionPlan.PoolEntry>> section
                : plan.finalPools().entrySet()) {
            for (final DeckConstructionPlan.PoolEntry entry : section.getValue()) {
                if (entry.card() == null || entry.count() <= 0) {
                    throw new IllegalArgumentException("plan contains invalid final pool entry");
                }
                if (!section.getKey().validate(entry.card())
                        && !plan.getPolicy().allows(DeckConstructionRule.Constraint.SECTION,
                                entry.card().getName(), section.getKey())) {
                    throw new IllegalArgumentException("plan card is no longer valid in its target section");
                }
                final DeckPrintingKey printing = DeckPrintingKey.from(entry.card());
                poolPrintings.add(printing);
                final DeckConstructionLedger.SlotKey slot = new DeckConstructionLedger.SlotKey(
                        section.getKey(), printing);
                if (poolCounts.put(slot, entry.count()) != null) {
                    throw new IllegalArgumentException("plan contains duplicate final pool entry");
                }
            }
        }
        for (final DeckConstructionLedger.Entry entry : plan.finalLedger().getEntries()) {
            final Integer count = poolCounts.remove(new DeckConstructionLedger.SlotKey(
                    entry.getSection(), entry.getPrintingKey()));
            if (count == null || count != entry.getTotalCount()) {
                throw new IllegalArgumentException("plan ledger does not match final card pools");
            }
        }
        if (!poolCounts.isEmpty()) {
            throw new IllegalArgumentException("plan card pool is missing ledger ownership");
        }
        if (!poolPrintings.equals(plan.getExactManifest().keySet())) {
            throw new IllegalArgumentException("plan exact manifest does not match final card pools");
        }
    }

    private static List<DeckSection> changedSections(final Deck deck,
            final Map<DeckSection, List<DeckConstructionPlan.PoolEntry>> finalPools) {
        final List<DeckSection> changed = new ArrayList<>();
        for (final DeckSection section : DeckSection.values()) {
            final CardPool current = deck.getLoadedSectionWithoutMaterializing(section);
            final List<DeckConstructionPlan.PoolEntry> wanted = finalPools.getOrDefault(section, List.of());
            if (!samePool(current, wanted)) {
                changed.add(section);
            }
        }
        return changed;
    }

    private static boolean samePool(final CardPool current,
            final List<DeckConstructionPlan.PoolEntry> wanted) {
        if ((current == null || current.isEmpty()) && wanted.isEmpty()) {
            return true;
        }
        if (current == null || current.countDistinct() != wanted.size()) {
            return false;
        }
        for (final DeckConstructionPlan.PoolEntry entry : wanted) {
            if (current.count(entry.card()) != entry.count()) {
                return false;
            }
        }
        return true;
    }

    private static void rollback(final Deck deck, final Map<DeckSection, SectionBackup> backups,
            final DeckConstructionLedger oldLedger, final DeckConstructionContext context) {
        int mutationIndex = 0;
        for (final Map.Entry<DeckSection, SectionBackup> entry : backups.entrySet()) {
            final DeckSection section = entry.getKey();
            final SectionBackup backup = entry.getValue();
            final DeckConstructionContext.RollbackFaultInjector fault = context.getRollbackFaultInjector();
            if (fault != null) {
                fault.beforeRollbackMutation(section, ++mutationIndex);
            }
            if (backup.original == null) {
                removeSection(deck, section);
            } else {
                backup.original.clear();
                backup.original.addAll(backup.contents);
                deck.putSection(section, backup.original);
            }
        }
        final DeckConstructionContext.RollbackFaultInjector fault = context.getRollbackFaultInjector();
        if (fault != null) {
            fault.beforeRollbackMutation(null, ++mutationIndex);
        }
        deck.setConstructionLedger(oldLedger);
    }

    private static void removeSection(final Deck deck, final DeckSection section) {
        final Iterator<Map.Entry<DeckSection, CardPool>> iterator = deck.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getKey() == section) {
                iterator.remove();
                return;
            }
        }
    }

    private static boolean samePools(final SortedMap<DeckConstructionLedger.SlotKey, ActualSlot> actual,
            final SortedMap<DeckConstructionLedger.SlotKey, Integer> wanted) {
        if (actual.size() != wanted.size()) {
            return false;
        }
        for (final Map.Entry<DeckConstructionLedger.SlotKey, ActualSlot> entry : actual.entrySet()) {
            if (!Objects.equals(wanted.get(entry.getKey()), entry.getValue().count)) {
                return false;
            }
        }
        return true;
    }

    private static NavigableMap<DeckPrintingKey, ResolvedPrinting> resolve(final DeckConstructionContext context,
            final String canonicalName,
            final Map<String, NavigableMap<DeckPrintingKey, ResolvedPrinting>> cache,
            final ResourceBudget budget, final SignatureComputer signatures) {
        if (cache.containsKey(canonicalName)) {
            return cache.get(canonicalName);
        }
        final List<PaperCard> raw;
        try {
            raw = context.getResolver().findByCanonicalName(canonicalName);
        } catch (final RuntimeException exception) {
            throw new PlanFailure(DeckConstructionPlan.Status.BLOCKED,
                    issue(DeckConstructionIssue.Code.RESOLVER_FAILURE, safeMessage(exception)));
        }
        final NavigableMap<DeckPrintingKey, ResolvedPrinting> exact = new TreeMap<>();
        if (raw != null) {
            if (raw.size() > MAX_PRINTINGS_PER_NAME) {
                throw new PlanFailure(DeckConstructionPlan.Status.BLOCKED,
                        issue(DeckConstructionIssue.Code.RESOURCE_LIMIT,
                                "indexed printing result exceeds the per-name limit"));
            }
            budget.preflightResolverRows(raw.size());
            int rowsForName = 0;
            for (final PaperCard card : raw) {
                rowsForName++;
                budget.addResolverRawRow();
                if (rowsForName > MAX_PRINTINGS_PER_NAME) {
                    throw new PlanFailure(DeckConstructionPlan.Status.BLOCKED,
                            issue(DeckConstructionIssue.Code.RESOURCE_LIMIT,
                                    "indexed printing result exceeds the per-name limit"));
                }
                if (card == null || !canonical(card.getName()).equals(canonicalName)) {
                    continue;
                }
                final DeckPrintingKey key = DeckPrintingKey.from(card);
                final ResolvedPrinting row = new ResolvedPrinting(key, card, signatures.signature(card));
                final ResolvedPrinting previous = exact.get(key);
                if (previous == null) {
                    exact.put(key, row);
                } else {
                    if (!previous.signature.equals(row.signature)) {
                        throw new PlanFailure(DeckConstructionPlan.Status.BLOCKED,
                                issue(DeckConstructionIssue.Code.CONFLICTING_RULE,
                                        "catalog returned conflicting rules for one exact printing"));
                    }
                    if (row.printing.getFunctionalVariant().compareTo(
                            previous.printing.getFunctionalVariant()) < 0) {
                        exact.remove(previous.printing);
                        exact.put(row.printing, row);
                    }
                }
            }
        }
        budget.addResolvedPrintings(exact.size());
        final NavigableMap<DeckPrintingKey, ResolvedPrinting> result =
                Collections.unmodifiableNavigableMap(exact);
        cache.put(canonicalName, result);
        return result;
    }

    private static String canonical(final String name) {
        return DeckConstructionRule.canonicalCardNameKey(name);
    }

    private static String poolIdentityEncoding(final DeckPrintingKey printing) {
        return new DeckPrintingKey(printing.getName(), printing.getEdition(), printing.getArtIndex(),
                printing.getCollectorNumber(), printing.isFoil(), printing.getFlags(), "").encode();
    }

    private static long checkedAdd(final long left, final long right, final long maximum, final String message) {
        if (right < 0 || left > maximum - right) {
            throw new PlanFailure(DeckConstructionPlan.Status.BLOCKED,
                    issue(DeckConstructionIssue.Code.RESOURCE_LIMIT, message));
        }
        return left + right;
    }

    private static String safeMessage(final RuntimeException exception) {
        final String message = exception.getMessage();
        final String safe = message == null || message.isBlank()
                ? exception.getClass().getSimpleName() : message;
        return safe.length() <= 1_024 ? safe : safe.substring(0, 1_024);
    }

    private static final class Planner {
        private final DeckConstructionContext context;
        private final DeckConstructionLedger ledger;
        private final SortedMap<DeckConstructionLedger.SlotKey, MutableSlot> slots = new TreeMap<>();
        private final Map<String, SortedMap<DeckPrintingKey, PaperCard>> activeCards = new TreeMap<>();
        private final Map<String, NavigableMap<DeckPrintingKey, ResolvedPrinting>> resolverCache = new HashMap<>();
        private final Map<DeckPrintingKey, PaperCard> initialRepresentatives = new TreeMap<>();
        private final Map<DeckPrintingKey, PaperCard> certifiedManualAdds = new TreeMap<>();
        private final ResourceBudget budget = new ResourceBudget();
        private final SignatureComputer signatures = new SignatureComputer(budget);
        private final Map<DeckConstructionLedger.ContributionKey, List<OldContribution>> oldContributions
                = new TreeMap<>();
        private final Map<DeckConstructionRule.RuleKey, List<DeckConstructionLedger.ContributionKey>>
                oldKeysByLogical = new HashMap<>();
        private final Map<String, Set<DeckConstructionRule.RuleKey>> oldLogicalKeysBySource = new HashMap<>();
        private final Map<DeckConstructionRule.RuleKey, DeckConstructionRule> activeByLogicalKey = new TreeMap<>();
        private final Map<RuleNodeKey, DeckConstructionRule> activeByNodeKey = new TreeMap<>();
        private final Map<RuleNodeKey, String> targetByNodeKey = new TreeMap<>();
        private final Set<DeckConstructionPolicy.Key> policyGrants = new HashSet<>();
        private long desiredManaged;

        private Planner(final DeckConstructionContext context, final Scan scan,
                final DeckConstructionLedger ledger) {
            this.context = context;
            this.ledger = ledger;
            final Map<DeckConstructionLedger.SlotKey, DeckConstructionLedger.Entry> ownershipBySlot
                    = new HashMap<>();
            for (final DeckConstructionLedger.Entry entry : ledger.getEntries()) {
                ownershipBySlot.put(new DeckConstructionLedger.SlotKey(
                        entry.getSection(), entry.getPrintingKey()), entry);
            }
            for (final Map.Entry<DeckConstructionLedger.SlotKey, ActualSlot> entry : scan.entries.entrySet()) {
                final DeckConstructionLedger.Entry ownership = ownershipBySlot.get(entry.getKey());
                final int manual = ownership == null ? entry.getValue().count : ownership.getManualCount();
                slots.put(entry.getKey(), new MutableSlot(entry.getValue().card, manual));
                final PaperCard previous = initialRepresentatives.putIfAbsent(
                        entry.getKey().getPrintingKey(), entry.getValue().card);
                if (previous != null) {
                    ensureSameSignature(previous, entry.getValue().card,
                            "deck scan found conflicting representatives for one exact printing");
                }
            }
            for (final DeckConstructionLedger.Entry entry : ledger.getEntries()) {
                final ActualSlot actual = scan.entries.get(new DeckConstructionLedger.SlotKey(
                        entry.getSection(), entry.getPrintingKey()));
                for (final Map.Entry<DeckConstructionLedger.ContributionKey, Integer> managed
                        : entry.getManagedCounts().entrySet()) {
                    oldContributions.computeIfAbsent(managed.getKey(), ignored -> new ArrayList<>())
                            .add(new OldContribution(entry.getSection(), entry.getPrintingKey(), actual.card,
                                    managed.getValue()));
                    oldKeysByLogical.computeIfAbsent(DeckConstructionRule.RuleKey.of(
                                    managed.getKey().getSourceCanonicalName(), managed.getKey().getRuleId()),
                            ignored -> new ArrayList<>()).add(managed.getKey());
                    oldLogicalKeysBySource.computeIfAbsent(managed.getKey().getSourceCanonicalName(),
                            ignored -> new HashSet<>()).add(DeckConstructionRule.RuleKey.of(
                                    managed.getKey().getSourceCanonicalName(), managed.getKey().getRuleId()));
                }
            }
        }

        private DeckConstructionIssue applyEdits(final List<DeckConstructionEdit> edits) {
            for (final DeckConstructionEdit edit : edits) {
                if (edit == null || edit.getType() == null || edit.getCard() == null
                        || edit.getAmount() <= 0) {
                    return issue(DeckConstructionIssue.Code.INVALID_EDIT, "invalid construction edit");
                }
                final DeckPrintingKey printing;
                try {
                    printing = DeckPrintingKey.from(edit.getCard());
                } catch (final RuntimeException exception) {
                    return issue(DeckConstructionIssue.Code.INVALID_EDIT, safeMessage(exception));
                }
                switch (edit.getType()) {
                    case ADD -> {
                        if (edit.getToSection() == null) {
                            return issue(DeckConstructionIssue.Code.INVALID_EDIT, "ADD requires a target section");
                        }
                        final PaperCard certified = certifyManualAdd(edit.getCard(), printing);
                        final DeckPrintingKey actualPrinting = DeckPrintingKey.from(certified);
                        final MutableSlot target = slot(edit.getToSection(), actualPrinting, certified);
                        if (!addManual(target, edit.getAmount())) {
                            return issue(DeckConstructionIssue.Code.RESOURCE_LIMIT, "manual card count overflow");
                        }
                    }
                    case REMOVE -> {
                        if (edit.getFromSection() == null) {
                            return issue(DeckConstructionIssue.Code.INVALID_EDIT, "REMOVE requires a source section");
                        }
                        final MutableSlot source = slots.get(new DeckConstructionLedger.SlotKey(
                                edit.getFromSection(), printing));
                        if (source == null || source.manual < edit.getAmount()) {
                            return issue(DeckConstructionIssue.Code.MANAGED_CARD_EDIT,
                                    "REMOVE may only consume manual ownership");
                        }
                        source.manual -= edit.getAmount();
                    }
                    case MOVE -> {
                        if (edit.getFromSection() == null || edit.getToSection() == null) {
                            return issue(DeckConstructionIssue.Code.INVALID_EDIT,
                                    "MOVE requires source and target sections");
                        }
                        final MutableSlot source = slots.get(new DeckConstructionLedger.SlotKey(
                                edit.getFromSection(), printing));
                        if (source == null || source.manual < edit.getAmount()) {
                            return issue(DeckConstructionIssue.Code.MANAGED_CARD_EDIT,
                                    "MOVE may only consume manual ownership");
                        }
                        if (edit.getFromSection() == edit.getToSection()) {
                            continue;
                        }
                        final DeckPrintingKey actualPrinting = DeckPrintingKey.from(source.card);
                        final MutableSlot target = slot(edit.getToSection(), actualPrinting, source.card);
                        if (!addManual(target, edit.getAmount())) {
                            return issue(DeckConstructionIssue.Code.RESOURCE_LIMIT, "manual card count overflow");
                        }
                        source.manual -= edit.getAmount();
                    }
                }
            }
            return null;
        }

        private boolean addManual(final MutableSlot slot, final int amount) {
            if (slot.manual > DeckConstructionLedger.MAX_TOTAL_MANUAL_COUNT - amount) {
                return false;
            }
            slot.manual += amount;
            return true;
        }

        private PaperCard certifyManualAdd(final PaperCard requested, final DeckPrintingKey printing) {
            final PaperCard prior = certifiedManualAdds.get(printing);
            if (prior != null) {
                ensureSameSignature(prior, requested,
                        "manual edits disagree about one exact printing");
                return prior;
            }
            final PaperCard existing = initialRepresentatives.get(printing);
            if (existing != null) {
                ensureSameSignature(existing, requested,
                        "manual edit rules disagree with the existing exact printing");
                certifiedManualAdds.put(DeckPrintingKey.from(existing), existing);
                return existing;
            }
            final ResolvedPrinting candidate = resolveName(canonical(printing.getName())).get(printing);
            if (candidate != null) {
                if (!candidate.signature.equals(signatures.signature(requested))) {
                    fail(DeckConstructionIssue.Code.CONFLICTING_RULE,
                            "manual edit rules disagree with the indexed exact printing");
                }
                certifiedManualAdds.put(candidate.printing, candidate.card);
                return candidate.card;
            }
            fail(DeckConstructionIssue.Code.MISSING_CARD,
                    "manual exact printing is absent from the indexed catalog");
            throw new IllegalStateException("unreachable");
        }

        private void ensureSameSignature(final PaperCard first, final PaperCard second, final String message) {
            if (!signatures.signature(first).equals(signatures.signature(second))) {
                fail(DeckConstructionIssue.Code.CONFLICTING_RULE, message);
            }
        }

        private void buildClosure() {
            rebuildActiveCards();
            final Deque<NameDepth> queue = new ArrayDeque<>();
            final Set<String> queued = new HashSet<>();
            final Map<String, Integer> processedPrintingCounts = new HashMap<>();
            for (final String name : activeCards.keySet()) {
                if (queued.add(name)) {
                    queue.addLast(new NameDepth(name, 0));
                }
            }
            while (!queue.isEmpty()) {
                final NameDepth source = queue.removeFirst();
                queued.remove(source.name);
                if (source.depth > MAX_DEPENDENCY_DEPTH) {
                    fail(DeckConstructionIssue.Code.RESOURCE_LIMIT, "dependency depth exceeds limit");
                }
                final SortedMap<DeckPrintingKey, PaperCard> cards = activeCards.get(source.name);
                if (cards == null || cards.isEmpty()) {
                    fail(DeckConstructionIssue.Code.MISSING_CARD, "active source printing is unavailable");
                }
                if (processedPrintingCounts.getOrDefault(source.name, 0) >= cards.size()) {
                    continue;
                }
                final List<DeckConstructionRule> sourceRules = collectRules(cards.values(), source.name);
                inspectDiagnostics(cards.values(), sourceRules, source.name);
                processedPrintingCounts.put(source.name, cards.size());
                for (final DeckConstructionRule rule : sourceRules) {
                    final DeckConstructionRule.RuleKey logical = rule.getRuleKey();
                    final DeckConstructionRule previous = activeByLogicalKey.putIfAbsent(logical, rule);
                    if (previous != null && (!previous.getContentFingerprint().equals(rule.getContentFingerprint())
                            || previous.getSchemaVersion() != rule.getSchemaVersion())) {
                        fail(DeckConstructionIssue.Code.CONFLICTING_RULE,
                                "same source/rule id has conflicting definitions");
                    }
                    verifyExistingVersion(rule);
                    final RuleNodeKey node = nodeKey(rule);
                    if (activeByNodeKey.putIfAbsent(node, rule) != null) {
                        continue;
                    }
                    if (activeByNodeKey.size() > MAX_ACTIVATED_RULES) {
                        fail(DeckConstructionIssue.Code.RESOURCE_LIMIT, "activated rule limit exceeded");
                    }
                    if (rule.getMode() == DeckConstructionRule.Mode.ALLOW) {
                        policyGrants.add(DeckConstructionPolicy.keyFor(rule));
                        continue;
                    }
                    final Target target = selectTarget(rule);
                    targetByNodeKey.put(node, target.canonicalName);
                    addManaged(rule, target);
                    final int processed = processedPrintingCounts.getOrDefault(target.canonicalName, 0);
                    final int known = activeCards.getOrDefault(target.canonicalName, Collections.emptySortedMap())
                            .size();
                    if (processed < known && queued.add(target.canonicalName)) {
                        queue.addLast(new NameDepth(target.canonicalName, source.depth + 1));
                    }
                }
            }
        }

        private List<DeckConstructionRule> collectRules(final Collection<PaperCard> cards,
                final String expectedSource) {
            final Map<RuleNodeKey, DeckConstructionRule> rules = new TreeMap<>();
            for (final PaperCard card : cards) {
                for (final DeckConstructionRule rule : card.getRules().getDeckConstructionRules()) {
                    if (!expectedSource.equals(rule.getSourceCanonicalName())) {
                        fail(DeckConstructionIssue.Code.CONFLICTING_RULE,
                                "DeckRule source does not match its PaperCard");
                    }
                    final DeckConstructionRule old = rules.putIfAbsent(nodeKey(rule), rule);
                    if (old != null && !old.equals(rule)) {
                        fail(DeckConstructionIssue.Code.CONFLICTING_RULE, "conflicting exact rule instances");
                    }
                }
            }
            return List.copyOf(rules.values());
        }

        private void inspectDiagnostics(final Collection<PaperCard> cards,
                final List<DeckConstructionRule> validRules, final String sourceCanonicalName) {
            final Set<DeckConstructionRule.RuleKey> validKeys = new HashSet<>();
            for (final DeckConstructionRule rule : validRules) {
                validKeys.add(rule.getRuleKey());
            }
            final Set<DeckConstructionRule.RuleKey> invalidKeys = new HashSet<>();
            boolean hasAmbiguousDiagnostic = false;
            for (final PaperCard card : cards) {
                for (final DeckConstructionDiagnostic diagnostic
                        : card.getRules().getDeckConstructionDiagnostics()) {
                    final DeckConstructionRule.RuleKey diagnosticKey = diagnosticRuleKey(diagnostic, card.getName());
                    if (diagnosticKey == null
                            || diagnostic.getCode() == DeckConstructionDiagnostic.Code.RESOURCE_LIMIT
                            || diagnostic.getCode() == DeckConstructionDiagnostic.Code.INTERNAL_ERROR) {
                        hasAmbiguousDiagnostic = true;
                    } else {
                        invalidKeys.add(diagnosticKey);
                    }
                }
            }
            for (final DeckConstructionRule.RuleKey old : oldLogicalKeysBySource.getOrDefault(
                    sourceCanonicalName, Set.of())) {
                if (!validKeys.contains(old) && (invalidKeys.contains(old) || hasAmbiguousDiagnostic)) {
                    throw new PlanFailure(DeckConstructionPlan.Status.UNRESOLVED,
                            new DeckConstructionIssue(DeckConstructionIssue.Code.MIGRATION_REQUIRED,
                                    "a recorded rule disappeared while its diagnostics are ambiguous",
                                    old.getSourceCanonicalName(), old.getRuleId()));
                }
            }
        }

        private static DeckConstructionRule.RuleKey diagnosticRuleKey(
                final DeckConstructionDiagnostic diagnostic, final String fallbackSource) {
            final String source = diagnostic.getSourceCardName() == null
                    ? fallbackSource : diagnostic.getSourceCardName();
            if (diagnostic.getRuleId() == null || source == null) {
                return null;
            }
            try {
                return DeckConstructionRule.RuleKey.of(
                        source, diagnostic.getRuleId());
            } catch (final IllegalArgumentException exception) {
                return null;
            }
        }

        private Target selectTarget(final DeckConstructionRule rule) {
            final DeckConstructionLedger.ContributionKey contribution = contributionKey(rule);
            final List<OldContribution> old = oldContributions.getOrDefault(contribution, List.of());
            if (!old.isEmpty()) {
                if (old.size() != 1 || old.get(0).count != rule.getAmount()
                        || old.get(0).section != rule.getTarget()) {
                    fail(DeckConstructionIssue.Code.LEDGER_DRIFT,
                            "managed contribution shape does not match its unchanged rule");
                }
                final String oldName = canonical(old.get(0).printing.getName());
                if (rule.getMode() == DeckConstructionRule.Mode.ADD_FIXED
                        && !oldName.equals(canonical(rule.getCardName()))) {
                    fail(DeckConstructionIssue.Code.LEDGER_DRIFT,
                            "fixed contribution target does not match its unchanged rule");
                }
                if (rule.getMode() == DeckConstructionRule.Mode.CHOOSE_ONE
                        && rule.getCandidates().stream().map(DeckConstructionService::canonical)
                                .noneMatch(oldName::equals)) {
                    fail(DeckConstructionIssue.Code.LEDGER_DRIFT,
                            "choice contribution is no longer among unchanged candidates");
                }
                return new Target(oldName, old.get(0).printing, old.get(0).card);
            }

            if (rule.getMode() == DeckConstructionRule.Mode.ADD_FIXED) {
                final String name = canonical(rule.getCardName());
                return deterministicTarget(name);
            }
            final DeckPrintingKey choice = context.getChoice(rule.getSourceCanonicalName(), rule.getId());
            if (choice == null) {
                throw new PlanFailure(DeckConstructionPlan.Status.CHOICE_REQUIRED,
                        new DeckConstructionIssue(DeckConstructionIssue.Code.CHOICE_REQUIRED,
                                "a new CHOOSE_ONE rule requires an exact printing choice",
                                rule.getSourceCanonicalName(), rule.getId()));
            }
            final String choiceName = canonical(choice.getName());
            if (rule.getCandidates().stream().map(DeckConstructionService::canonical).noneMatch(choiceName::equals)) {
                fail(DeckConstructionIssue.Code.INVALID_CHOICE, "choice is not one of the rule candidates");
            }
            final PaperCard card = exactCard(choiceName, choice);
            if (card == null) {
                fail(DeckConstructionIssue.Code.INVALID_CHOICE, "chosen exact printing is unavailable");
            }
            return new Target(choiceName, DeckPrintingKey.from(card), card);
        }

        private Target deterministicTarget(final String canonicalName) {
            SortedMap<DeckPrintingKey, PaperCard> cards = activeCards.get(canonicalName);
            if (cards == null || cards.isEmpty()) {
                final NavigableMap<DeckPrintingKey, ResolvedPrinting> resolved = resolveName(canonicalName);
                if (resolved.isEmpty()) {
                    fail(DeckConstructionIssue.Code.MISSING_CARD,
                            "no exact printing exists for " + canonicalName);
                }
                final ResolvedPrinting selected = resolved.firstEntry().getValue();
                return new Target(canonicalName, selected.printing, selected.card);
            }
            final Map.Entry<DeckPrintingKey, PaperCard> first = cards.entrySet().iterator().next();
            return new Target(canonicalName, first.getKey(), first.getValue());
        }

        private PaperCard exactCard(final String canonicalName, final DeckPrintingKey printing) {
            final SortedMap<DeckPrintingKey, PaperCard> active = activeCards.get(canonicalName);
            if (active != null) {
                final PaperCard card = active.get(printing);
                if (printing.matches(card)) {
                    return card;
                }
            }
            final ResolvedPrinting resolved = resolveName(canonicalName).get(printing);
            return resolved == null ? null : resolved.card;
        }

        private NavigableMap<DeckPrintingKey, ResolvedPrinting> resolveName(final String canonicalName) {
            return resolve(context, canonicalName, resolverCache, budget, signatures);
        }

        private void addManaged(final DeckConstructionRule rule, final Target target) {
            desiredManaged = checkedAdd(desiredManaged, rule.getAmount(), MAX_TOTAL_DESIRED_MANAGED,
                    "desired managed card count exceeds limit");
            final MutableSlot slot = slot(rule.getTarget(), target.printing, target.card);
            final DeckConstructionLedger.ContributionKey key = contributionKey(rule);
            if (slot.managed.put(key, rule.getAmount()) != null) {
                fail(DeckConstructionIssue.Code.CONFLICTING_RULE, "duplicate managed contribution key");
            }
            activate(DeckPrintingKey.from(slot.card), slot.card);
        }

        private void verifyMigrations() {
            for (final DeckConstructionLedger.ContributionKey old : oldContributions.keySet()) {
                final DeckConstructionRule current = activeByLogicalKey.get(DeckConstructionRule.RuleKey.of(
                        old.getSourceCanonicalName(), old.getRuleId()));
                if (current == null) {
                    continue;
                }
                if (old.getContributionRevision() != 0
                        || old.getRuleSchemaVersion() != current.getSchemaVersion()
                        || !old.getRuleFingerprint().equals(current.getContentFingerprint())) {
                    throw new PlanFailure(DeckConstructionPlan.Status.UNRESOLVED,
                            new DeckConstructionIssue(DeckConstructionIssue.Code.MIGRATION_REQUIRED,
                                    "active rule content differs from its recorded contribution",
                                    current.getSourceCanonicalName(), current.getId()));
                }
            }
        }

        private void verifyExistingVersion(final DeckConstructionRule current) {
            for (final DeckConstructionLedger.ContributionKey old
                    : oldKeysByLogical.getOrDefault(current.getRuleKey(), List.of())) {
                if (old.getContributionRevision() != 0
                        || old.getRuleSchemaVersion() != current.getSchemaVersion()
                        || !old.getRuleFingerprint().equals(current.getContentFingerprint())) {
                    throw new PlanFailure(DeckConstructionPlan.Status.UNRESOLVED,
                            new DeckConstructionIssue(DeckConstructionIssue.Code.MIGRATION_REQUIRED,
                                    "active rule content differs from its recorded contribution",
                                    current.getSourceCanonicalName(), current.getId()));
                }
            }
        }

        private void verifyDependencyGraph() {
            final Map<RuleNodeKey, Set<RuleNodeKey>> graph = new TreeMap<>();
            final Map<String, List<RuleNodeKey>> nodesBySource = new TreeMap<>();
            for (final Map.Entry<RuleNodeKey, DeckConstructionRule> entry : activeByNodeKey.entrySet()) {
                graph.put(entry.getKey(), new TreeSet<>());
                nodesBySource.computeIfAbsent(entry.getValue().getSourceCanonicalName(), ignored -> new ArrayList<>())
                        .add(entry.getKey());
            }
            long edges = 0;
            for (final Map.Entry<RuleNodeKey, String> target : targetByNodeKey.entrySet()) {
                for (final RuleNodeKey targetNode : nodesBySource.getOrDefault(target.getValue(), List.of())) {
                    if (graph.get(target.getKey()).add(targetNode)) {
                        edges = checkedAdd(edges, 1, MAX_DEPENDENCY_EDGES,
                                "dependency edge limit exceeded");
                    }
                }
            }
            final Map<RuleNodeKey, Integer> colors = new HashMap<>();
            for (final RuleNodeKey node : graph.keySet()) {
                visit(node, graph, colors, 0);
            }
        }

        private void visit(final RuleNodeKey node, final Map<RuleNodeKey, Set<RuleNodeKey>> graph,
                final Map<RuleNodeKey, Integer> colors, final int depth) {
            if (depth > MAX_DEPENDENCY_DEPTH) {
                fail(DeckConstructionIssue.Code.RESOURCE_LIMIT, "dependency depth exceeds limit");
            }
            final int color = colors.getOrDefault(node, 0);
            if (color == 1) {
                throw new PlanFailure(DeckConstructionPlan.Status.UNRESOLVED,
                        issue(DeckConstructionIssue.Code.CYCLIC_DEPENDENCY,
                                "cyclic construction dependency detected"));
            }
            if (color == 2) {
                return;
            }
            colors.put(node, 1);
            for (final RuleNodeKey next : graph.getOrDefault(node, Set.of())) {
                visit(next, graph, colors, depth + 1);
            }
            colors.put(node, 2);
        }

        private FinalState finalState() {
            final Map<DeckSection, List<DeckConstructionPlan.PoolEntry>> pools = new EnumMap<>(DeckSection.class);
            final SortedMap<DeckConstructionLedger.SlotKey, Integer> counts = new TreeMap<>();
            final SortedMap<DeckPrintingKey, String> exactManifest = new TreeMap<>();
            final List<DeckConstructionLedger.Entry> ownership = new ArrayList<>();
            long totalManual = 0;
            long totalManaged = 0;
            for (final Map.Entry<DeckConstructionLedger.SlotKey, MutableSlot> entry : slots.entrySet()) {
                final MutableSlot value = entry.getValue();
                long managed = 0;
                for (final int count : value.managed.values()) {
                    managed = checkedAdd(managed, count, Integer.MAX_VALUE, "slot managed count overflow");
                }
                final long total = checkedAdd(value.manual, managed, Integer.MAX_VALUE,
                        "slot total count overflow");
                if (total == 0) {
                    continue;
                }
                if (!entry.getKey().getSection().validate(value.card)
                        && !policyGrants.contains(new DeckConstructionPolicy.Key(
                                DeckConstructionRule.Constraint.SECTION,
                                canonical(value.card.getName()), entry.getKey().getSection()))) {
                    fail(DeckConstructionIssue.Code.INVALID_EDIT,
                            "card is not valid in the target deck section");
                }
                totalManual = checkedAdd(totalManual, value.manual,
                        DeckConstructionLedger.MAX_TOTAL_MANUAL_COUNT, "total manual card limit exceeded");
                totalManaged = checkedAdd(totalManaged, managed,
                        DeckConstructionLedger.MAX_TOTAL_MANAGED_COUNT, "total managed card limit exceeded");
                final int count = Math.toIntExact(total);
                final DeckPrintingKey actualPrinting = DeckPrintingKey.from(value.card);
                if (!actualPrinting.equals(entry.getKey().getPrintingKey())) {
                    fail(DeckConstructionIssue.Code.INTERNAL_ERROR,
                            "shadow slot identity disagrees with its actual PaperCard");
                }
                final String signature = signatures.signature(value.card);
                final String previousSignature = exactManifest.putIfAbsent(actualPrinting, signature);
                if (previousSignature != null && !previousSignature.equals(signature)) {
                    fail(DeckConstructionIssue.Code.CONFLICTING_RULE,
                            "final pool contains conflicting representatives for one exact printing");
                }
                pools.computeIfAbsent(entry.getKey().getSection(), ignored -> new ArrayList<>())
                        .add(new DeckConstructionPlan.PoolEntry(value.card, count));
                final DeckConstructionLedger.SlotKey actualSlot = new DeckConstructionLedger.SlotKey(
                        entry.getKey().getSection(), actualPrinting);
                counts.put(actualSlot, count);
                ownership.add(new DeckConstructionLedger.Entry(entry.getKey().getSection(),
                        actualPrinting, value.manual, value.managed));
            }
            final DeckConstructionLedger finalLedger;
            try {
                finalLedger = DeckConstructionLedger.tracked(ledger.getLedgerId(), ownership);
            } catch (final IllegalArgumentException exception) {
                throw new PlanFailure(DeckConstructionPlan.Status.BLOCKED,
                        issue(DeckConstructionIssue.Code.RESOURCE_LIMIT,
                                "final construction ledger exceeds its bounded format: "
                                        + safeMessage(exception)));
            }
            return new FinalState(pools, counts, finalLedger, exactManifest);
        }

        private MutableSlot slot(final DeckSection section, final DeckPrintingKey printing, final PaperCard card) {
            final DeckConstructionLedger.SlotKey key = new DeckConstructionLedger.SlotKey(section, printing);
            final MutableSlot existing = slots.get(key);
            if (existing != null) {
                ensureSameSignature(existing.card, card,
                        "shadow slot and selected exact printing have conflicting rules");
                return existing;
            }
            final MutableSlot created = new MutableSlot(card, 0);
            slots.put(key, created);
            return created;
        }

        private void rebuildActiveCards() {
            activeCards.clear();
            for (final Map.Entry<DeckConstructionLedger.SlotKey, MutableSlot> entry : slots.entrySet()) {
                if (entry.getValue().manual > 0 || !entry.getValue().managed.isEmpty()) {
                    activate(entry.getKey().getPrintingKey(), entry.getValue().card);
                }
            }
        }

        private void activate(final DeckPrintingKey printing, final PaperCard card) {
            final String signature = signatures.signature(card);
            final SortedMap<DeckPrintingKey, PaperCard> cards = activeCards.computeIfAbsent(
                    canonical(printing.getName()), ignored -> new TreeMap<>());
            final PaperCard previous = cards.putIfAbsent(printing, card);
            if (previous != null && !signatures.signature(previous).equals(signature)) {
                fail(DeckConstructionIssue.Code.CONFLICTING_RULE,
                        "active deck slots disagree about one exact printing");
            }
        }

        private static RuleNodeKey nodeKey(final DeckConstructionRule rule) {
            return new RuleNodeKey(rule.getRuleKey(), rule.getSchemaVersion(), rule.getContentFingerprint());
        }

        private static DeckConstructionLedger.ContributionKey contributionKey(final DeckConstructionRule rule) {
            return new DeckConstructionLedger.ContributionKey(rule.getSourceCanonicalName(), rule.getId(), 0,
                    rule.getSchemaVersion(), rule.getContentFingerprint());
        }

        private static void fail(final DeckConstructionIssue.Code code, final String message) {
            final DeckConstructionPlan.Status status = code == DeckConstructionIssue.Code.CYCLIC_DEPENDENCY
                    || code == DeckConstructionIssue.Code.MIGRATION_REQUIRED
                    || code == DeckConstructionIssue.Code.LEDGER_DRIFT
                    ? DeckConstructionPlan.Status.UNRESOLVED : DeckConstructionPlan.Status.BLOCKED;
            throw new PlanFailure(status, issue(code, message));
        }
    }

    private record Scan(SortedMap<DeckConstructionLedger.SlotKey, ActualSlot> entries) { }

    private record ActualSlot(PaperCard card, int count, DeckPrintingKey printing) { }

    private static final class MutableSlot {
        private final PaperCard card;
        private int manual;
        private final SortedMap<DeckConstructionLedger.ContributionKey, Integer> managed = new TreeMap<>();

        private MutableSlot(final PaperCard card, final int manual) {
            this.card = card;
            this.manual = manual;
        }
    }

    private record OldContribution(DeckSection section, DeckPrintingKey printing, PaperCard card, int count) { }

    private record Target(String canonicalName, DeckPrintingKey printing, PaperCard card) { }

    private record NameDepth(String name, int depth) { }

    private record RuleNodeKey(DeckConstructionRule.RuleKey ruleKey, int schemaVersion,
                               String fingerprint) implements Comparable<RuleNodeKey> {
        @Override
        public int compareTo(final RuleNodeKey other) {
            int result = ruleKey.compareTo(other.ruleKey);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(schemaVersion, other.schemaVersion);
            return result != 0 ? result : fingerprint.compareTo(other.fingerprint);
        }
    }

    private record FinalState(Map<DeckSection, List<DeckConstructionPlan.PoolEntry>> pools,
                              SortedMap<DeckConstructionLedger.SlotKey, Integer> slotCounts,
                              DeckConstructionLedger ledger,
                              SortedMap<DeckPrintingKey, String> exactManifest) { }

    private record ResolvedPrinting(DeckPrintingKey printing, PaperCard card, String signature) { }

    private record SectionBackup(CardPool original, CardPool contents) { }

    private static final class PlanFailure extends RuntimeException {
        private final DeckConstructionPlan.Status status;
        private final DeckConstructionIssue issue;

        private PlanFailure(final DeckConstructionPlan.Status status, final DeckConstructionIssue issue) {
            super(issue.toString());
            this.status = status;
            this.issue = issue;
        }
    }

    private static final class ResourceBudget {
        private long resolverRawRows;
        private long resolvedPrintings;
        private long signatureItems;
        private long signatureUtf8Bytes;

        private void addResolverRawRow() {
            resolverRawRows = checkedAdd(resolverRawRows, 1, MAX_RESOLVER_RAW_ROWS,
                    "resolver raw-row limit exceeded");
        }

        private void preflightResolverRows(final int declaredRows) {
            if (declaredRows < 0 || resolverRawRows > MAX_RESOLVER_RAW_ROWS - declaredRows) {
                throw new PlanFailure(DeckConstructionPlan.Status.BLOCKED,
                        issue(DeckConstructionIssue.Code.RESOURCE_LIMIT,
                                "resolver raw-row limit exceeded"));
            }
        }

        private void addResolvedPrintings(final int count) {
            resolvedPrintings = checkedAdd(resolvedPrintings, count, MAX_TOTAL_RESOLVED_PRINTINGS,
                    "resolved printing count exceeds limit");
        }

        private void addSignatureItem(final String... fields) {
            signatureItems = checkedAdd(signatureItems, 1, MAX_SIGNATURE_ITEMS,
                    "rule/diagnostic signature item limit exceeded");
            for (final String field : fields) {
                final String value = field == null ? "" : field;
                if (value.length() > CanonicalDigest.MAX_FIELD_CHARACTERS) {
                    throw new PlanFailure(DeckConstructionPlan.Status.BLOCKED,
                            issue(DeckConstructionIssue.Code.RESOURCE_LIMIT,
                                    "rule/diagnostic field exceeds the character limit"));
                }
                if (value.length() > MAX_SIGNATURE_UTF8_BYTES - signatureUtf8Bytes) {
                    throw new PlanFailure(DeckConstructionPlan.Status.BLOCKED,
                            issue(DeckConstructionIssue.Code.RESOURCE_LIMIT,
                                    "rule/diagnostic UTF-8 budget exceeded"));
                }
                final int bytes = value.getBytes(StandardCharsets.UTF_8).length;
                signatureUtf8Bytes = checkedAdd(signatureUtf8Bytes, bytes,
                        MAX_SIGNATURE_UTF8_BYTES, "rule/diagnostic UTF-8 budget exceeded");
            }
        }
    }

    private static final class SignatureComputer {
        private final ResourceBudget budget;
        private final Map<PaperCard, String> cache = new IdentityHashMap<>();

        private SignatureComputer(final ResourceBudget budget) {
            this.budget = budget;
        }

        private String signature(final PaperCard card) {
            final String cached = cache.get(card);
            if (cached != null) {
                return cached;
            }
            final List<String> itemHashes = new ArrayList<>();
            for (final DeckConstructionRule rule : card.getRules().getDeckConstructionRules()) {
                final String source = rule.getRuleKey().getSourceCanonicalName();
                final String id = rule.getRuleKey().getRuleId();
                final String schema = Integer.toString(rule.getSchemaVersion());
                final String fingerprint = rule.getContentFingerprint();
                budget.addSignatureItem("rule", source, id, schema, fingerprint);
                final CanonicalDigest item = new CanonicalDigest();
                item.add("kind", "rule");
                item.add("source", source);
                item.add("id", id);
                item.add("schema", schema);
                item.add("fingerprint", fingerprint);
                itemHashes.add(item.finish());
            }
            for (final DeckConstructionDiagnostic diagnostic
                    : card.getRules().getDeckConstructionDiagnostics()) {
                final String source = diagnostic.getSourceCardName();
                final String id = diagnostic.getRuleId();
                final String index = Integer.toString(diagnostic.getRuleIndex());
                final String raw = diagnostic.getRawRule();
                final String message = diagnostic.getMessage();
                budget.addSignatureItem("diagnostic", diagnostic.getCode().name(), source,
                        id, index, raw, message);
                final CanonicalDigest item = new CanonicalDigest();
                item.add("kind", "diagnostic");
                item.add("code", diagnostic.getCode().name());
                item.add("source", source);
                item.add("id", id);
                item.add("index", index);
                item.add("raw", raw);
                item.add("message", message);
                itemHashes.add(item.finish());
            }
            itemHashes.sort(String::compareTo);
            final CanonicalDigest result = new CanonicalDigest();
            for (final String item : itemHashes) {
                result.add("item", item);
            }
            final String signature = result.finish();
            cache.put(card, signature);
            return signature;
        }
    }

    private static final class CanonicalDigest {
        private static final int MAX_FIELD_CHARACTERS = DeckConstructionLedger.MAX_LINE_LENGTH;

        private final MessageDigest digest;

        private CanonicalDigest() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (final NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        private void add(final String key, final String value) {
            update(key == null ? "" : key);
            if (value == null) {
                digest.update((byte) 0);
            } else {
                digest.update((byte) 1);
                update(value);
            }
        }

        private void update(final String value) {
            if (value.length() > MAX_FIELD_CHARACTERS) {
                throw new PlanFailure(DeckConstructionPlan.Status.BLOCKED,
                        issue(DeckConstructionIssue.Code.RESOURCE_LIMIT,
                                "fingerprint field exceeds the character limit"));
            }
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) (bytes.length >>> 24));
            digest.update((byte) (bytes.length >>> 16));
            digest.update((byte) (bytes.length >>> 8));
            digest.update((byte) bytes.length);
            digest.update(bytes);
        }

        private String finish() {
            final StringBuilder output = new StringBuilder(64);
            for (final byte value : digest.digest()) {
                output.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                output.append(Character.forDigit(value & 0x0f, 16));
            }
            return output.toString();
        }
    }
}
