package forge.deck.construction;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckConstructionServiceTest {
    private final DeckConstructionService service = new DeckConstructionService();

    @Test
    void fixedContributionIsOncePerDeckTransactionalAndIdempotent() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friends", DeckSection.Main, "Friend", 2));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, friend);
        final Deck deck = deck(source, 2);
        final DeckConstructionLedger original = deck.getConstructionLedger().copy();

        final DeckConstructionPlan first = service.plan(deck, catalog.context(), List.of());

        assertEquals(DeckConstructionPlan.Status.READY, first.getStatus());
        assertEquals(1, catalog.lookups("FRIEND"));
        assertEquals(0, deck.getMain().count(friend));
        assertTrue(original.semanticallyEquals(deck.getConstructionLedger()));
        assertEquals(DeckConstructionCommitResult.Status.COMMITTED,
                service.commit(deck, catalog.context(), first).getStatus());
        assertEquals(2, deck.getMain().count(friend));
        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(deck, catalog.context(), first).getStatus());

        final DeckConstructionPlan second = service.plan(deck, catalog.context(), List.of());
        assertEquals(DeckConstructionPlan.Status.NO_CHANGE, second.getStatus());
        assertEquals(2, deck.getMain().count(friend));
        assertEquals(DeckConstructionCommitResult.Status.NO_CHANGE,
                service.commit(deck, catalog.context(), second).getStatus());
    }

    @Test
    void emptyTrackedDeckAcceptsItsFirstManualCardOnlyThroughAnEdit() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, friend);
        final Deck deck = new Deck("empty");

        final DeckConstructionPlan plan = service.plan(deck, catalog.context(), List.of(
                DeckConstructionEdit.add(source, DeckSection.Main, 1)));
        commit(deck, catalog.context(), plan);

        assertEquals(1, deck.getMain().count(source));
        assertEquals(1, deck.getMain().count(friend));
    }

    @Test
    void newlyResolvedPrintingCanActivateItsOwnRuleAndCommitThroughExactManifest() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("target", DeckSection.Main, "Target", 1));
        final PaperCard target = card("Target", "GEN", 1, false, "2",
                fixed("leaf", DeckSection.Main, "Leaf", 1));
        final PaperCard leaf = card("Leaf", "GEN", 1, false, "3");
        final Catalog catalog = new Catalog(source, target, leaf);
        final Deck deck = deck(source, 1);

        final DeckConstructionPlan plan = service.plan(deck, catalog.context(), List.of());

        assertEquals(List.of("Leaf", "Source", "Target"), plan.getFinalPool().stream()
                .map(entry -> entry.getPrintingKey().getName()).sorted().toList());
        assertEquals(3, plan.getExactManifest().size());
        commit(deck, catalog.context(), plan);
        assertEquals(1, deck.getMain().count(target));
        assertEquals(1, deck.getMain().count(leaf));
    }

    @Test
    void removingRulePrintingKeepsPlainSameNamePrintingAndRetractsContribution() {
        final PaperCard rulePrinting = card("Source", "A", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard plainPrinting = card("Source", "B", 1, false, "2");
        final PaperCard friend = card("Friend", "GEN", 1, false, "3");
        final Catalog catalog = new Catalog(rulePrinting, plainPrinting, friend);
        final Deck deck = deck(rulePrinting, 1);
        deck.getMain().add(plainPrinting);
        trackCurrentManual(deck);
        commit(deck, catalog.context(), service.plan(deck, catalog.context(), List.of()));

        final DeckConstructionPlan removal = service.plan(deck, catalog.context(), List.of(
                DeckConstructionEdit.remove(rulePrinting, DeckSection.Main, 1)));
        commit(deck, catalog.context(), removal);

        assertEquals(0, deck.getMain().count(rulePrinting));
        assertEquals(1, deck.getMain().count(plainPrinting));
        assertEquals(0, deck.getMain().count(friend));
    }

    @Test
    void newManualIdentityMustResolveExactlyAndUsesActualRepresentativeMetadata() {
        final PaperCard requestVariant = cardVariant("Manual", "GEN", 1, false, "1",
                "variant-b");
        final PaperCard actualVariant = cardVariant("Manual", "GEN", 1, false, "1",
                "variant-a");
        final Deck missingDeck = new Deck("missing");
        final DeckConstructionPlan missing = service.plan(missingDeck,
                DeckConstructionContext.builder(name -> List.of()).build(), List.of(
                        DeckConstructionEdit.add(requestVariant, DeckSection.Main, 1)));
        assertIssue(missing, DeckConstructionPlan.Status.BLOCKED,
                DeckConstructionIssue.Code.MISSING_CARD);
        assertTrue(missingDeck.getMain().isEmpty());
        assertTrue(missingDeck.getConstructionLedger().getEntries().isEmpty());

        final Catalog catalog = new Catalog(requestVariant, actualVariant);
        final Deck deck = new Deck("actual-representative");
        final DeckConstructionPlan plan = service.plan(deck, catalog.context(), List.of(
                DeckConstructionEdit.add(requestVariant, DeckSection.Main, 1)));
        assertEquals("variant-a", plan.getFinalPool().get(0).getPrintingKey().getFunctionalVariant());
        commit(deck, catalog.context(), plan);
        final DeckConstructionLedger.Entry entry = deck.getConstructionLedger().getEntries().get(0);
        assertEquals("variant-a", entry.getPrintingKey().getFunctionalVariant());
        assertEquals("variant-a", deck.getMain().get(1).getFunctionalVariant());

        final DeckConstructionPlan move = service.plan(deck, catalog.context(), List.of(
                DeckConstructionEdit.move(requestVariant, DeckSection.Main, DeckSection.Sideboard, 1)));
        commit(deck, catalog.context(), move);
        assertEquals("variant-a", deck.get(DeckSection.Sideboard).get(1).getFunctionalVariant());
        assertEquals("variant-a", deck.getConstructionLedger().getEntries().get(0)
                .getPrintingKey().getFunctionalVariant());
    }

    @Test
    void samePoolIdentityWithDifferentRuleSignaturesIsRejected() {
        final PaperCard withRule = cardVariant("Manual", "GEN", 1, false, "1", "variant-a",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard withoutRule = cardVariant("Manual", "GEN", 1, false, "1", "variant-b");
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Deck deck = new Deck("conflict");
        final DeckConstructionContext context = new Catalog(withRule, withoutRule, friend).context();

        assertIssue(service.plan(deck, context, List.of(
                        DeckConstructionEdit.add(withoutRule, DeckSection.Main, 1))),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.CONFLICTING_RULE);
        assertTrue(deck.getMain().isEmpty());

        final Deck existingConflict = deck(withRule, 1);
        existingConflict.getOrCreate(DeckSection.Sideboard).add(withoutRule);
        trackCurrentManual(existingConflict);
        assertIssue(service.plan(existingConflict, context, List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.CONFLICTING_RULE);
    }

    @Test
    void unresolvedNewExactPrintingOrSameNameReplacementMakesPlanStale() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("target", DeckSection.Main, "Target", 1));
        final PaperCard target = card("Target", "A", 1, false, "2");
        final PaperCard replacement = card("Target", "B", 1, false, "3");
        final Catalog catalog = new Catalog(source, target);

        final Deck missingDeck = deck(source, 1);
        final DeckConstructionPlan missingPlan = service.plan(missingDeck, catalog.context(), List.of());
        final DeckConstructionContext missingAtCommit = DeckConstructionContext.builder(name -> List.of())
                .catalogGeneration("catalog-1").build();
        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(missingDeck, missingAtCommit, missingPlan).getStatus());
        assertEquals(0, missingDeck.getMain().count(target));

        final Deck replacementDeck = deck(source, 1);
        final DeckConstructionPlan replacementPlan = service.plan(
                replacementDeck, catalog.context(), List.of());
        final DeckConstructionContext replacedAtCommit = DeckConstructionContext.builder(
                name -> "TARGET".equals(name) ? List.of(replacement) : List.of())
                .catalogGeneration("catalog-1").build();
        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(replacementDeck, replacedAtCommit, replacementPlan).getStatus());
        assertEquals(0, replacementDeck.getMain().count(replacement));

        final PaperCard existing = card("Existing", "OLD", 1, false, "4");
        final Deck existingDeck = deck(existing, 1);
        final DeckConstructionContext noCatalogEntry = DeckConstructionContext.builder(name -> List.of())
                .catalogGeneration("catalog-1").build();
        final DeckConstructionPlan existingPlan = service.plan(existingDeck, noCatalogEntry, List.of());
        assertEquals(DeckConstructionCommitResult.Status.NO_CHANGE,
                service.commit(existingDeck, noCatalogEntry, existingPlan).getStatus());
    }

    @Test
    void sameNameExactResolverIsIndexedOnceForTenThousandManualAddsAndCommitChecks() {
        final CardRules rules = CardRules.fromScript(List.of(
                "Name:Mass", "ManaCost:0", "Types:Artifact", "Oracle:Test."));
        final List<PaperCard> printings = new ArrayList<>();
        final List<DeckConstructionEdit> edits = new ArrayList<>();
        for (int i = 1; i <= DeckConstructionService.MAX_PRINTINGS_PER_NAME; i++) {
            final PaperCard card = new PaperCard(rules, "GEN", CardRarity.Common,
                    i, false, Integer.toString(i), "Artist", "");
            printings.add(card);
            edits.add(DeckConstructionEdit.add(card, DeckSection.Main, 1));
        }
        final AtomicInteger resolverCalls = new AtomicInteger();
        final AtomicInteger rawRowsRead = new AtomicInteger();
        final List<PaperCard> probedRows = new AbstractList<>() {
            @Override
            public PaperCard get(final int index) {
                rawRowsRead.incrementAndGet();
                return printings.get(index);
            }

            @Override
            public int size() {
                return printings.size();
            }
        };
        final DeckConstructionContext context = DeckConstructionContext.builder(name -> {
            resolverCalls.incrementAndGet();
            return "MASS".equals(name) ? probedRows : List.of();
        }).catalogGeneration("mass-index-1").build();
        final Deck deck = new Deck("mass-index");

        final DeckConstructionPlan plan = service.plan(deck, context, edits);

        assertEquals(DeckConstructionPlan.Status.READY, plan.getStatus(), () -> plan.getIssues().toString());
        assertEquals(10_000, plan.getFinalPool().size());
        assertEquals(1, resolverCalls.get());
        assertEquals(10_000, rawRowsRead.get());
        assertEquals(DeckConstructionCommitResult.Status.COMMITTED,
                service.commit(deck, context, plan).getStatus());
        assertEquals(2, resolverCalls.get());
        assertEquals(20_000, rawRowsRead.get());
        assertEquals(10_000, deck.getMain().countDistinct());
    }

    @Test
    void twoRulesToSamePrintingKeepIndependentContributionOwnership() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("one", DeckSection.Main, "Friend", 1),
                fixed("two", DeckSection.Main, "Friend", 2));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, friend);
        final Deck deck = deck(source, 1);

        final DeckConstructionPlan plan = service.plan(deck, catalog.context(), List.of());
        assertEquals(1, catalog.lookups("FRIEND"));
        final int beforeCommitLookups = catalog.lookups("FRIEND");
        commit(deck, catalog.context(), plan);
        assertEquals(beforeCommitLookups + 1, catalog.lookups("FRIEND"));

        final DeckConstructionLedger.Entry entry = deck.getConstructionLedger()
                .getEntry(DeckSection.Main, DeckPrintingKey.from(friend)).orElseThrow();
        assertEquals(3, entry.getManagedCount());
        assertEquals(2, entry.getManagedCounts().size());
    }

    @Test
    void manualOwnershipOfSameAndOtherExactPrintingsIsNeverConsumed() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friends", DeckSection.Main, "Friend", 2));
        final PaperCard selected = card("Friend", "A", 1, false, "1");
        final PaperCard otherEdition = card("Friend", "B", 7, true, "9")
                .copyWithFlags(Map.of("markedColors", "WU"));
        final Catalog catalog = new Catalog(source, selected, otherEdition);
        final Deck deck = deck(source, 1);
        deck.getMain().add(selected, 1);
        deck.getMain().add(otherEdition, 3);
        trackCurrentManual(deck);

        commit(deck, catalog.context(), service.plan(deck, catalog.context(), List.of()));

        assertEquals(3, deck.getMain().count(selected));
        assertEquals(3, deck.getMain().count(otherEdition));
        assertEquals(1, deck.getConstructionLedger().getEntry(DeckSection.Main,
                DeckPrintingKey.from(selected)).orElseThrow().getManualCount());
        assertEquals(3, deck.getConstructionLedger().getEntry(DeckSection.Main,
                DeckPrintingKey.from(otherEdition)).orElseThrow().getManualCount());
    }

    @Test
    void removingManualSourceRetractsOnlyManagedCards() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friends", DeckSection.Main, "Friend", 2));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, friend);
        final Deck deck = deck(source, 1);
        deck.getMain().add(friend, 1);
        trackCurrentManual(deck);
        commit(deck, catalog.context(), service.plan(deck, catalog.context(), List.of()));

        final DeckConstructionPlan removal = service.plan(deck, catalog.context(), List.of(
                DeckConstructionEdit.remove(source, DeckSection.Main, 1)));
        commit(deck, catalog.context(), removal);

        assertEquals(0, deck.getMain().count(source));
        assertEquals(1, deck.getMain().count(friend));
        final DeckConstructionLedger.Entry survivor = deck.getConstructionLedger()
                .getEntry(DeckSection.Main, DeckPrintingKey.from(friend)).orElseThrow();
        assertEquals(1, survivor.getManualCount());
        assertEquals(0, survivor.getManagedCount());
    }

    @Test
    void chooseRequiresAnExactChoiceThenPersistsItWithoutAnotherPrompt() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                choose("pick", DeckSection.Sideboard, 2, "First", "Second"));
        final PaperCard first = card("First", "GEN", 1, false, "1");
        final PaperCard second = cardVariant("Second", "GEN", 2, true, "2", "variant-a");
        final PaperCard secondChoice = cardVariant("Second", "GEN", 2, true, "2", "variant-b");
        final Catalog catalog = new Catalog(source, first, second);
        final Deck deck = deck(source, 1);

        final DeckConstructionPlan missing = service.plan(deck, catalog.context(), List.of());
        assertIssue(missing, DeckConstructionPlan.Status.CHOICE_REQUIRED,
                DeckConstructionIssue.Code.CHOICE_REQUIRED);
        assertFalse(deck.has(DeckSection.Sideboard));

        final DeckConstructionContext selected = catalog.contextBuilder()
                .choose("Source", "pick", DeckPrintingKey.from(secondChoice)).build();
        final DeckConstructionPlan chosen = service.plan(deck, selected, List.of());
        commit(deck, selected, chosen);
        assertEquals(2, deck.get(DeckSection.Sideboard).count(second));
        assertEquals("variant-a", deck.getConstructionLedger().getEntry(
                DeckSection.Sideboard, DeckPrintingKey.from(second)).orElseThrow()
                .getPrintingKey().getFunctionalVariant());

        final DeckConstructionPlan persisted = service.plan(deck, catalog.context(), List.of());
        assertEquals(DeckConstructionPlan.Status.NO_CHANGE, persisted.getStatus());
    }

    @Test
    void selfTwoNodeAndThreeNodeCyclesFailClosedWithoutMutation() {
        assertCycle(Map.of("A", fixed("to-a", DeckSection.Main, "A", 1)));
        assertCycle(Map.of(
                "A", fixed("to-b", DeckSection.Main, "B", 1),
                "B", fixed("to-a", DeckSection.Main, "A", 1)));
        assertCycle(Map.of(
                "A", fixed("to-b", DeckSection.Main, "B", 1),
                "B", fixed("to-c", DeckSection.Main, "C", 1),
                "C", fixed("to-a", DeckSection.Main, "A", 1)));
    }

    @Test
    void missingTargetBlocksButUntrackedInvalidRuleIsIgnoredWithoutThrowing() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("missing", DeckSection.Main, "Absent", 1));
        final Deck missingDeck = deck(source, 1);
        assertIssue(service.plan(missingDeck, new Catalog(source).context(), List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.MISSING_CARD);
        assertEquals(1, missingDeck.getMain().count(source));

        final PaperCard invalid = card("Invalid", "SRC", 1, false, "1",
                "Id$ bad | Mode$ UNKNOWN | Card$ X");
        final PaperCard manual = card("Manual", "GEN", 1, false, "2");
        final Deck invalidDeck = deck(invalid, 1);
        assertEquals(DeckConstructionPlan.Status.NO_CHANGE,
                service.plan(invalidDeck, new Catalog(invalid, manual).context(), List.of()).getStatus());
        assertEquals(DeckConstructionPlan.Status.READY,
                service.plan(invalidDeck, new Catalog(invalid, manual).context(), List.of(
                        DeckConstructionEdit.add(manual, DeckSection.Main, 1))).getStatus());
    }

    @Test
    void unrelatedBadRuleDoesNotDisableValidRuleButInvalidatedRecordedIdRequiresMigration() {
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final PaperCard mixed = card("Source", "SRC", 1, false, "1",
                fixed("valid", DeckSection.Main, "Friend", 1),
                "Mode$ UNKNOWN | Card$ X");
        final Catalog mixedCatalog = new Catalog(mixed, friend);
        final Deck mixedDeck = deck(mixed, 1);
        final DeckConstructionPlan mixedPlan = service.plan(mixedDeck, mixedCatalog.context(), List.of());
        assertEquals(DeckConstructionPlan.Status.READY, mixedPlan.getStatus());
        commit(mixedDeck, mixedCatalog.context(), mixedPlan);
        assertEquals(1, mixedDeck.getMain().count(friend));

        final PaperCard original = card("Recorded", "SRC", 1, false, "1",
                fixed("recorded", DeckSection.Main, "Friend", 1));
        final Catalog catalog = new Catalog(original, friend);
        final Deck deck = deck(original, 1);
        commit(deck, catalog.context(), service.plan(deck, catalog.context(), List.of()));
        replaceSource(deck, original, card("Recorded", "SRC", 1, false, "1",
                "Id$ recorded | Mode$ UNKNOWN | Card$ Friend"));

        assertIssue(service.plan(deck, catalog.context(), List.of()),
                DeckConstructionPlan.Status.UNRESOLVED, DeckConstructionIssue.Code.MIGRATION_REQUIRED);
        assertEquals(1, deck.getMain().count(friend));
    }

    @Test
    void ambiguousDiagnosticOnlyBlocksWhenARecordedLogicalRuleDisappears() {
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final PaperCard original = card("Source", "SRC", 1, false, "1",
                fixed("recorded", DeckSection.Main, "Friend", 1));
        final Catalog catalog = new Catalog(original, friend);
        final Deck deck = deck(original, 1);
        commit(deck, catalog.context(), service.plan(deck, catalog.context(), List.of()));

        final PaperCard stillValid = card("Source", "SRC", 1, false, "1",
                fixed("recorded", DeckSection.Main, "Friend", 1),
                "Mode$ UNKNOWN | Card$ Unrelated");
        replaceSource(deck, original, stillValid);
        final DeckConstructionPlan stillValidPlan = service.plan(deck, catalog.context(), List.of());
        assertEquals(DeckConstructionPlan.Status.NO_CHANGE, stillValidPlan.getStatus());
        assertEquals(DeckConstructionCommitResult.Status.NO_CHANGE,
                service.commit(deck, catalog.context(), stillValidPlan).getStatus());

        final PaperCard ambiguousRemoval = card("Source", "SRC", 1, false, "1",
                "Mode$ UNKNOWN | Card$ Unrelated");
        replaceSource(deck, stillValid, ambiguousRemoval);
        assertIssue(service.plan(deck, catalog.context(), List.of()),
                DeckConstructionPlan.Status.UNRESOLVED, DeckConstructionIssue.Code.MIGRATION_REQUIRED);
        assertEquals(1, deck.getMain().count(friend));
    }

    @Test
    void ledgerDriftFromBypassMutationFailsClosed() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final PaperCard unrelated = card("Unrelated", "GEN", 1, false, "3");
        final Catalog catalog = new Catalog(source, friend, unrelated);
        final Deck deck = deck(source, 1);
        commit(deck, catalog.context(), service.plan(deck, catalog.context(), List.of()));
        deck.getMain().add(unrelated, 1);

        assertIssue(service.plan(deck, catalog.context(), List.of()),
                DeckConstructionPlan.Status.UNRESOLVED, DeckConstructionIssue.Code.LEDGER_DRIFT);

        final Deck untracked = new Deck("legacy");
        untracked.setConstructionLedger(DeckConstructionLedger.untracked());
        assertIssue(service.plan(untracked, catalog.context(), List.of()),
                DeckConstructionPlan.Status.UNRESOLVED, DeckConstructionIssue.Code.LEDGER_NOT_TRACKED);
    }

    @Test
    void changingAmountSectionCardOrCandidatesRequiresExplicitMigration() {
        assertFixedMigration(
                fixed("rule", DeckSection.Main, "Friend", 1),
                fixed("rule", DeckSection.Main, "Friend", 2));
        assertFixedMigration(
                fixed("rule", DeckSection.Main, "Friend", 1),
                fixed("rule", DeckSection.Sideboard, "Friend", 1));
        assertFixedMigration(
                fixed("rule", DeckSection.Main, "Friend", 1),
                fixed("rule", DeckSection.Main, "Other", 1));

        final PaperCard oldSource = card("Source", "SRC", 1, false, "1",
                choose("rule", DeckSection.Main, 1, "Friend", "Other"));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final PaperCard other = card("Other", "GEN", 1, false, "3");
        final Catalog catalog = new Catalog(oldSource, friend, other);
        final Deck deck = deck(oldSource, 1);
        final DeckConstructionContext choice = catalog.contextBuilder()
                .choose("Source", "rule", DeckPrintingKey.from(friend)).build();
        commit(deck, choice, service.plan(deck, choice, List.of()));
        replaceSource(deck, oldSource, card("Source", "SRC", 1, false, "1",
                choose("rule", DeckSection.Main, 1, "Friend", "Third")));

        assertIssue(service.plan(deck, catalog.context(), List.of()),
                DeckConstructionPlan.Status.UNRESOLVED, DeckConstructionIssue.Code.MIGRATION_REQUIRED);
        assertEquals(1, deck.getMain().count(friend));
    }

    @Test
    void cleanlyRemovedRuleUsesItsOldExactLedgerToRetractManagedCards() {
        final PaperCard original = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(original, friend);
        final Deck deck = deck(original, 1);
        commit(deck, catalog.context(), service.plan(deck, catalog.context(), List.of()));
        final PaperCard ruleless = card("Source", "SRC", 1, false, "1");
        replaceSource(deck, original, ruleless);
        catalog.replace(ruleless);

        final DeckConstructionPlan removal = service.plan(deck, catalog.context(), List.of());
        commit(deck, catalog.context(), removal);

        assertEquals(0, deck.getMain().count(friend));
        assertEquals(1, deck.getMain().countDistinct());
    }

    @Test
    void sourceLevelResourceDiagnosticCannotSilentlyRetractRecordedContributions() {
        final PaperCard original = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(original, friend);
        final Deck deck = deck(original, 1);
        commit(deck, catalog.context(), service.plan(deck, catalog.context(), List.of()));
        replaceSource(deck, original, card("Source", "SRC", 1, false, "1",
                "X".repeat(DeckConstructionRuleParser.MAX_RULE_LENGTH + 1)));

        assertIssue(service.plan(deck, catalog.context(), List.of()),
                DeckConstructionPlan.Status.UNRESOLVED, DeckConstructionIssue.Code.MIGRATION_REQUIRED);
        assertEquals(1, deck.getMain().count(friend));
    }

    @Test
    void manualAddToManagedSlotWorksButRemoveOrMoveCannotConsumeManagedOwnership() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 2));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, friend);
        final Deck deck = deck(source, 1);
        commit(deck, catalog.context(), service.plan(deck, catalog.context(), List.of()));

        assertIssue(service.plan(deck, catalog.context(), List.of(
                        DeckConstructionEdit.remove(friend, DeckSection.Main, 1))),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.MANAGED_CARD_EDIT);
        assertIssue(service.plan(deck, catalog.context(), List.of(
                        DeckConstructionEdit.move(friend, DeckSection.Main, DeckSection.Sideboard, 1))),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.MANAGED_CARD_EDIT);

        final DeckConstructionPlan add = service.plan(deck, catalog.context(), List.of(
                DeckConstructionEdit.add(friend, DeckSection.Main, 1)));
        commit(deck, catalog.context(), add);
        final DeckConstructionLedger.Entry entry = deck.getConstructionLedger()
                .getEntry(DeckSection.Main, DeckPrintingKey.from(friend)).orElseThrow();
        assertEquals(1, entry.getManualCount());
        assertEquals(2, entry.getManagedCount());
    }

    @Test
    void manualMoveTransfersOnlyExactManualOwnershipBetweenSections() {
        final PaperCard vanilla = card("Vanilla", "TST", 1, false, "1");
        final Deck deck = deck(vanilla, 2);
        final DeckConstructionContext context = new Catalog(vanilla).context();
        final DeckConstructionPlan move = service.plan(deck, context, List.of(
                DeckConstructionEdit.move(vanilla, DeckSection.Main, DeckSection.Sideboard, 1)));

        commit(deck, context, move);

        assertEquals(1, deck.getMain().count(vanilla));
        assertEquals(1, deck.get(DeckSection.Sideboard).count(vanilla));
        assertEquals(1, deck.getConstructionLedger().getEntry(
                DeckSection.Sideboard, DeckPrintingKey.from(vanilla)).orElseThrow().getManualCount());
    }

    @Test
    void stalePlanAndInjectedCommitFaultBothHaveZeroPartialEffects() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final PaperCard unrelated = card("Unrelated", "GEN", 1, false, "3");
        final Catalog catalog = new Catalog(source, friend, unrelated);
        final Deck staleDeck = deck(source, 1);
        final DeckConstructionPlan stalePlan = service.plan(staleDeck, catalog.context(), List.of());
        staleDeck.getMain().add(unrelated);
        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(staleDeck, catalog.context(), stalePlan).getStatus());
        assertEquals(0, staleDeck.getMain().count(friend));

        final Deck generationDeck = deck(source, 1);
        final DeckConstructionPlan generationPlan = service.plan(
                generationDeck, catalog.context(), List.of());
        final DeckConstructionContext changedGeneration = DeckConstructionContext.builder(
                name -> catalog.printings.getOrDefault(name, List.of()))
                .catalogGeneration("catalog-2").build();
        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(generationDeck, changedGeneration, generationPlan).getStatus());
        assertEquals(0, generationDeck.getMain().count(friend));

        final Deck rollbackDeck = deck(source, 1);
        final DeckConstructionLedger ledgerBefore = rollbackDeck.getConstructionLedger().copy();
        final DeckConstructionPlan rollbackPlan = service.plan(rollbackDeck, catalog.context(), List.of());
        final DeckConstructionContext fault = catalog.contextBuilder()
                .commitFaultInjector((section, index) -> {
                    if (index == 1) {
                        throw new IllegalStateException("injected");
                    }
                }).build();
        assertEquals(DeckConstructionCommitResult.Status.ROLLED_BACK,
                service.commit(rollbackDeck, fault, rollbackPlan).getStatus());
        assertEquals(1, rollbackDeck.getMain().count(source));
        assertEquals(0, rollbackDeck.getMain().count(friend));
        assertTrue(ledgerBefore.semanticallyEquals(rollbackDeck.getConstructionLedger()));
    }

    @Test
    void planCommitIsConfinedToItsOwnerThread() throws InterruptedException {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, friend);
        final Deck deck = deck(source, 1);
        final DeckConstructionPlan plan = service.plan(deck, catalog.context(), List.of());
        final AtomicReference<DeckConstructionCommitResult> result = new AtomicReference<>();

        final Thread other = new Thread(() -> result.set(
                service.commit(deck, catalog.context(), plan)));
        other.start();
        other.join();

        assertEquals(DeckConstructionCommitResult.Status.BLOCKED, result.get().getStatus());
        assertEquals(DeckConstructionIssue.Code.THREAD_CONFINEMENT,
                result.get().getIssues().get(0).getCode());
        assertEquals(1, deck.getMain().count(source));
        assertEquals(0, deck.getMain().count(friend));
    }

    @Test
    void rollbackRestoresSecondAndNewSectionsLedgerAndOriginalPoolReferences() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("main", DeckSection.Main, "Friend", 1),
                fixed("side", DeckSection.Sideboard, "Friend", 1));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, friend);

        for (final int faultIndex : List.of(2, 3)) {
            final Deck deck = deck(source, 1);
            final CardPool originalMain = deck.getMain();
            final CardPool originalSide = faultIndex == 2
                    ? deck.getOrCreate(DeckSection.Sideboard) : null;
            final DeckConstructionLedger originalLedger = deck.getConstructionLedger().copy();
            final DeckConstructionPlan plan = service.plan(deck, catalog.context(), List.of());
            final DeckConstructionContext fault = catalog.contextBuilder()
                    .commitFaultInjector((section, index) -> {
                        if (index == faultIndex) {
                            throw new IllegalStateException("forward failure " + faultIndex);
                        }
                    }).build();

            assertEquals(DeckConstructionCommitResult.Status.ROLLED_BACK,
                    service.commit(deck, fault, plan).getStatus());
            assertSame(originalMain, deck.getMain());
            assertEquals(1, originalMain.count(source));
            assertEquals(0, originalMain.count(friend));
            if (originalSide == null) {
                assertEquals(null, deck.get(DeckSection.Sideboard));
            } else {
                assertSame(originalSide, deck.get(DeckSection.Sideboard));
                assertTrue(originalSide.isEmpty());
            }
            assertTrue(originalLedger.semanticallyEquals(deck.getConstructionLedger()));
        }

        final Deck unknown = deck(source, 1);
        final DeckConstructionPlan plan = service.plan(unknown, catalog.context(), List.of());
        final DeckConstructionContext rollbackFailure = catalog.contextBuilder()
                .commitFaultInjector((section, index) -> {
                    if (index == 2) {
                        throw new IllegalStateException("forward");
                    }
                })
                .rollbackFaultInjector((section, index) -> {
                    throw new IllegalStateException("rollback");
                }).build();
        final DeckConstructionCommitResult failed = service.commit(unknown, rollbackFailure, plan);
        assertEquals(DeckConstructionCommitResult.Status.ROLLBACK_FAILED, failed.getStatus());
        assertEquals(DeckConstructionIssue.Code.ROLLBACK_FAILED_STATE_UNKNOWN,
                failed.getIssues().get(0).getCode());
    }

    @Test
    void allowPolicyIsExactByConstraintCardAndSection() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                allow("format", "FORMAT_CARD_POOL", "Guest", null),
                allow("copy", "COPY_LIMIT", "Guest", null),
                allow("section", "SECTION", "Guest", DeckSection.Sideboard));
        final Deck deck = deck(source, 1);
        final DeckConstructionPlan plan = service.plan(deck, new Catalog(source).context(), List.of());
        final DeckConstructionPolicy policy = plan.getPolicy();

        assertTrue(policy.allows(DeckConstructionRule.Constraint.FORMAT_CARD_POOL, "Guest", DeckSection.Main));
        assertFalse(policy.allows(DeckConstructionRule.Constraint.FORMAT_CARD_POOL, "Other", DeckSection.Main));
        assertFalse(policy.allows(DeckConstructionRule.Constraint.COMMANDER_COLOR_IDENTITY,
                "Guest", DeckSection.Main));
        assertTrue(policy.allows(DeckConstructionRule.Constraint.SECTION, "Guest", DeckSection.Sideboard));
        assertFalse(policy.allows(DeckConstructionRule.Constraint.SECTION, "Guest", DeckSection.Main));
        assertEquals(Integer.MAX_VALUE, policy.copyLimit("Guest", DeckSection.Main, 4));
        assertEquals(4, policy.copyLimit("Other", DeckSection.Main, 4));
    }

    @Test
    void sectionValidatorIsOnlyBypassedByAnExactActiveSectionGrant() {
        final PaperCard guest = card("Guest", "GEN", 1, false, "2");
        final PaperCard other = card("Other", "GEN", 1, false, "3");
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("guest", DeckSection.Commander, "Guest", 1),
                allow("guest-section", "SECTION", "Guest", DeckSection.Commander));
        final Catalog catalog = new Catalog(source, guest, other);
        final Deck allowed = deck(source, 1);
        final DeckConstructionPlan allowedPlan = service.plan(allowed, catalog.context(), List.of());
        assertEquals(DeckConstructionPlan.Status.READY, allowedPlan.getStatus());
        commit(allowed, catalog.context(), allowedPlan);
        assertEquals(1, allowed.get(DeckSection.Commander).count(guest));

        final PaperCard ungrantedSource = card("Ungranting", "SRC", 1, false, "4",
                fixed("other", DeckSection.Commander, "Other", 1),
                allow("wrong-card", "SECTION", "Guest", DeckSection.Commander));
        assertIssue(service.plan(deck(ungrantedSource, 1), catalog.context(), List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.INVALID_EDIT);
    }

    @Test
    void finalPoolPreviewValidatorIsReadOnlyAndGenerationParticipatesInStaleness() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, friend);
        final Deck rejectedDeck = deck(source, 1);
        final DeckConstructionContext rejected = catalog.contextBuilder()
                .finalPoolValidator((preview, policy) -> "format rejected", "format-1").build();
        assertIssue(service.plan(rejectedDeck, rejected, List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.LEGALITY_REJECTED);
        assertEquals(0, rejectedDeck.getMain().count(friend));

        final Deck deck = deck(source, 1);
        final DeckConstructionContext accepted = catalog.contextBuilder()
                .finalPoolValidator((preview, policy) -> null, "format-1").build();
        final DeckConstructionPlan plan = service.plan(deck, accepted, List.of());
        assertEquals(2, plan.getFinalPool().size());
        assertThrows(UnsupportedOperationException.class,
                () -> plan.getFinalPool().clear());
        assertFalse(java.util.Arrays.stream(DeckConstructionPlan.FinalPoolEntry.class.getDeclaredFields())
                .anyMatch(field -> PaperCard.class.isAssignableFrom(field.getType())));

        final DeckConstructionContext changedGeneration = catalog.contextBuilder()
                .finalPoolValidator((preview, policy) -> null, "format-2").build();
        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(deck, changedGeneration, plan).getStatus());
        assertEquals(0, deck.getMain().count(friend));

        final DeckConstructionContext changedDecision = catalog.contextBuilder()
                .finalPoolValidator((preview, policy) -> "changed decision", "format-1").build();
        final DeckConstructionCommitResult result = service.commit(deck, changedDecision, plan);
        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN, result.getStatus());
        assertEquals(DeckConstructionIssue.Code.LEGALITY_REJECTED, result.getIssues().get(0).getCode());
        assertEquals(0, deck.getMain().count(friend));

        final PaperCard vanilla = card("Vanilla", "TST", 1, false, "3");
        final Deck noChangeDeck = deck(vanilla, 1);
        final DeckConstructionContext noChangeAccepted = new Catalog(vanilla).contextBuilder()
                .finalPoolValidator((preview, policy) -> null, "format-1").build();
        final DeckConstructionPlan noChangePlan = service.plan(
                noChangeDeck, noChangeAccepted, List.of());
        assertEquals(DeckConstructionPlan.Status.NO_CHANGE, noChangePlan.getStatus());
        final DeckConstructionContext noChangeRejected = new Catalog(vanilla).contextBuilder()
                .finalPoolValidator((preview, policy) -> "now illegal", "format-1").build();
        final DeckConstructionCommitResult noChangeResult = service.commit(
                noChangeDeck, noChangeRejected, noChangePlan);
        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN, noChangeResult.getStatus());
        assertEquals(DeckConstructionIssue.Code.LEGALITY_REJECTED,
                noChangeResult.getIssues().get(0).getCode());
    }

    @Test
    void planningDeferredDeckFailsWithoutMaterializingItsCardLists() {
        final Deck deck = new Deck("deferred");
        deck.setDeferredSections(Map.of("Main", List.of("1 Deferred Card|TST|1")));
        deck.setConstructionLedger(DeckConstructionLedger.tracked(
                deck.getConstructionLedger().getLedgerId(), List.of()));

        final DeckConstructionPlan plan = service.plan(deck,
                DeckConstructionContext.builder(name -> List.of()).build(), List.of());

        assertIssue(plan, DeckConstructionPlan.Status.UNRESOLVED,
                DeckConstructionIssue.Code.DEFERRED_DECK_NOT_LOADED);
        assertTrue(deck.hasDeferredSections());
        assertTrue(deck.getLoadedSectionWithoutMaterializing(DeckSection.Main).isEmpty());
    }

    @Test
    void validatorPresenceAndNonblankGenerationArePartOfTheCapabilityFingerprint() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, friend);
        final Deck readyDeck = deck(source, 1);
        final DeckConstructionContext withValidator = catalog.contextBuilder()
                .finalPoolValidator((preview, policy) -> null, "format-1").build();
        final DeckConstructionPlan ready = service.plan(readyDeck, withValidator, List.of());
        final DeckConstructionContext missingValidator = catalog.contextBuilder()
                .finalPoolValidator(null, "format-1").build();

        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(readyDeck, missingValidator, ready).getStatus());
        assertEquals(0, readyDeck.getMain().count(friend));

        final PaperCard vanilla = card("Vanilla", "TST", 1, false, "3");
        final Catalog vanillaCatalog = new Catalog(vanilla);
        final Deck noChangeDeck = deck(vanilla, 1);
        final DeckConstructionPlan noChange = service.plan(noChangeDeck,
                vanillaCatalog.contextBuilder().finalPoolValidator(
                        (preview, policy) -> null, "format-1").build(), List.of());
        assertEquals(DeckConstructionPlan.Status.NO_CHANGE, noChange.getStatus());
        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(noChangeDeck, vanillaCatalog.contextBuilder()
                        .finalPoolValidator(null, "format-1").build(), noChange).getStatus());
        assertEquals(1, noChangeDeck.getMain().count(vanilla));

        final DeckConstructionContext blankGeneration = catalog.contextBuilder()
                .finalPoolValidator((preview, policy) -> null, " ").build();
        assertIssue(service.plan(deck(source, 1), blankGeneration, List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);
    }

    @Test
    void validatorReentryMutationMakesReadyAndNoChangePlansStaleWithoutServiceOverwrite() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final PaperCard intruder = card("Intruder", "GEN", 1, false, "3");
        final Catalog catalog = new Catalog(source, friend, intruder);
        final Deck readyDeck = deck(source, 1);
        final boolean[] mutateReady = {false};
        final DeckConstructionContext readyContext = catalog.contextBuilder()
                .finalPoolValidator((preview, policy) -> {
                    if (mutateReady[0]) {
                        readyDeck.getMain().add(intruder);
                    }
                    return null;
                }, "format-1").build();
        final DeckConstructionPlan ready = service.plan(readyDeck, readyContext, List.of());
        mutateReady[0] = true;

        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(readyDeck, readyContext, ready).getStatus());
        assertEquals(1, readyDeck.getMain().count(source));
        assertEquals(0, readyDeck.getMain().count(friend));
        assertEquals(1, readyDeck.getMain().count(intruder));

        final PaperCard vanilla = card("Vanilla", "TST", 1, false, "4");
        final Catalog vanillaCatalog = new Catalog(vanilla, intruder);
        final Deck noChangeDeck = deck(vanilla, 1);
        final boolean[] mutateNoChange = {false};
        final DeckConstructionContext noChangeContext = vanillaCatalog.contextBuilder()
                .finalPoolValidator((preview, policy) -> {
                    if (mutateNoChange[0]) {
                        noChangeDeck.getMain().add(intruder);
                    }
                    return null;
                }, "format-1").build();
        final DeckConstructionPlan noChange = service.plan(noChangeDeck, noChangeContext, List.of());
        mutateNoChange[0] = true;

        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(noChangeDeck, noChangeContext, noChange).getStatus());
        assertEquals(1, noChangeDeck.getMain().count(vanilla));
        assertEquals(1, noChangeDeck.getMain().count(intruder));
    }

    @Test
    void rulesOnlyValidatorReentryMakesThePlanStale() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("friend", DeckSection.Main, "Friend", 1));
        final PaperCard reloadedSource = card("Source", "SRC", 1, false, "1");
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, friend);
        final Deck deck = deck(source, 1);
        final boolean[] mutate = {false};
        final DeckConstructionContext context = catalog.contextBuilder()
                .finalPoolValidator((preview, policy) -> {
                    if (mutate[0]) {
                        replaceSource(deck, source, reloadedSource);
                    }
                    return null;
                }, "format-1").build();
        final DeckConstructionPlan plan = service.plan(deck, context, List.of());
        mutate[0] = true;

        assertEquals(DeckConstructionCommitResult.Status.STALE_PLAN,
                service.commit(deck, context, plan).getStatus());
        assertEquals(1, deck.getMain().count(reloadedSource));
        assertEquals(0, deck.getMain().count(friend));
        assertTrue(deck.getMain().get(1).getRules().getDeckConstructionRules().isEmpty());
    }

    @Test
    void invalidInputsAndManagedResourceOverflowAreStructuredFailures() {
        final PaperCard vanilla = card("Vanilla", "TST", 1, false, "1");
        final Deck deck = deck(vanilla, 1);
        assertIssue(service.plan(deck, new Catalog(vanilla).context(), List.of(
                        DeckConstructionEdit.add(null, null, 0))),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.INVALID_EDIT);
        assertIssue(service.plan(deck, DeckConstructionContext.builder(null).build(), List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);
        assertIssue(service.plan(deck, DeckConstructionContext.builder(name -> List.of())
                        .catalogGeneration("x".repeat(
                                DeckConstructionContext.MAX_CATALOG_GENERATION_UTF8_BYTES + 1)).build(), List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);
        assertIssue(service.plan(deck, DeckConstructionContext.builder(name -> List.of())
                        .finalPoolValidator(null, "x".repeat(
                                DeckConstructionContext.MAX_LEGALITY_GENERATION_UTF8_BYTES + 1)).build(), List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);
        final DeckConstructionContext.Builder tooManyChoices = DeckConstructionContext.builder(name -> List.of());
        for (int i = 0; i <= DeckConstructionContext.MAX_CHOICES; i++) {
            tooManyChoices.choose("Source", "r" + i, DeckPrintingKey.from(vanilla));
        }
        assertIssue(service.plan(deck, tooManyChoices.build(), List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);

        final List<String> rules = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rules.add(fixed("r" + i, DeckSection.Main, "Target", 1_000));
        }
        final PaperCard source = card("Source", "SRC", 1, false, "1", rules.toArray(String[]::new));
        final PaperCard target = card("Target", "GEN", 1, false, "2",
                fixed("overflow", DeckSection.Main, "Last", 1));
        final PaperCard last = card("Last", "GEN", 1, false, "3");
        assertIssue(service.plan(deck(source, 1), new Catalog(source, target, last).context(), List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);

        final PaperCard lookupSource = card("Lookup Source", "SRC", 1, false, "4",
                fixed("lookup", DeckSection.Main, "Target", 1));
        final List<PaperCard> tooManyPrintings = new ArrayList<>();
        for (int i = 0; i <= DeckConstructionService.MAX_PRINTINGS_PER_NAME; i++) {
            tooManyPrintings.add(target);
        }
        final DeckConstructionContext oversizedResolver = DeckConstructionContext.builder(
                name -> tooManyPrintings).build();
        assertIssue(service.plan(deck(lookupSource, 1), oversizedResolver, List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);
        final DeckConstructionPlan resolverFailure = service.plan(deck(lookupSource, 1),
                DeckConstructionContext.builder(name -> {
                    throw new IllegalStateException("x".repeat(10_000));
                }).build(), List.of());
        assertIssue(resolverFailure, DeckConstructionPlan.Status.BLOCKED,
                DeckConstructionIssue.Code.RESOLVER_FAILURE);
        assertTrue(resolverFailure.getIssues().get(0).getMessage().length() <= 1_024);
    }

    @Test
    void oversizedFinalLedgerIsAResourceFailureWithoutPoolOrLedgerMutation() {
        final CardRules rules = CardRules.fromScript(List.of(
                "Name:Long", "ManaCost:0", "Types:Artifact", "Oracle:Test."));
        final String longVariant = "v".repeat(DeckPrintingKey.MAX_FUNCTIONAL_VARIANT_LENGTH);
        final List<PaperCard> printings = new ArrayList<>();
        final List<DeckConstructionEdit> edits = new ArrayList<>();
        for (int i = 1; i <= 3_100; i++) {
            final PaperCard card = new PaperCard(rules, "GEN", CardRarity.Common,
                    i, false, Integer.toString(i), "Artist", longVariant);
            printings.add(card);
            edits.add(DeckConstructionEdit.add(card, DeckSection.Main, 1));
        }
        final Deck deck = new Deck("oversized-ledger");
        final DeckConstructionLedger original = deck.getConstructionLedger().copy();
        final DeckConstructionContext context = DeckConstructionContext.builder(
                name -> "LONG".equals(name) ? printings : List.of())
                .catalogGeneration("long-ledger-1").build();

        final DeckConstructionPlan plan = service.plan(deck, context, edits);

        assertIssue(plan, DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);
        assertTrue(deck.getMain().isEmpty());
        assertTrue(original.semanticallyEquals(deck.getConstructionLedger()));
    }

    @Test
    void twentyThousandDistinctPrintingsUseBatchLedgerAndEachTargetNameLookupAtMostOnce() {
        final CardRules vanillaRules = CardRules.fromScript(List.of(
                "Name:Vanilla", "ManaCost:0", "Types:Artifact", "Oracle:Test."));
        final Deck deck = new Deck("large");
        for (int i = 0; i < 20_000; i++) {
            deck.getMain().add(new PaperCard(vanillaRules, "E" + i, CardRarity.Common,
                    1, false, Integer.toString(i), "Artist", ""));
        }
        trackCurrentManual(deck);
        final DeckConstructionPlan large = service.plan(deck,
                DeckConstructionContext.builder(name -> List.of()).catalogGeneration("large").build(), List.of());
        assertEquals(DeckConstructionPlan.Status.NO_CHANGE, large.getStatus());
        assertEquals(20_000, deck.getMain().countDistinct());

        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("one", DeckSection.Main, "Target", 1),
                fixed("two", DeckSection.Sideboard, "Target", 1));
        final PaperCard target = card("Target", "GEN", 1, false, "2");
        final Catalog catalog = new Catalog(source, target);
        assertEquals(DeckConstructionPlan.Status.READY,
                service.plan(deck(source, 1), catalog.context(), List.of()).getStatus());
        assertEquals(1, catalog.lookups("TARGET"));
    }

    @Test
    void resolverRawRowsAndRuleDiagnosticSummariesHaveGlobalBudgets() {
        final PaperCard source = card("Source", "SRC", 1, false, "1",
                fixed("a", DeckSection.Main, "A", 1),
                fixed("b", DeckSection.Main, "B", 1),
                fixed("c", DeckSection.Main, "C", 1));
        final PaperCard a = card("A", "GEN", 1, false, "2");
        final PaperCard b = card("B", "GEN", 1, false, "3");
        final PaperCard c = card("C", "GEN", 1, false, "4");
        final PaperCard wrong = card("Wrong", "GEN", 1, false, "5");
        final Map<String, PaperCard> targets = Map.of("A", a, "B", b, "C", c);
        final DeckConstructionContext rawFlood = DeckConstructionContext.builder(name -> {
            final PaperCard target = targets.get(name);
            if (target == null) {
                return List.of();
            }
            final List<PaperCard> rows = new ArrayList<>();
            rows.addAll(Collections.nCopies(5_000, wrong));
            rows.addAll(Collections.nCopies(5_000, target));
            return rows;
        }).build();
        assertIssue(service.plan(deck(source, 1), rawFlood, List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);

        final List<String> floodScript = new ArrayList<>(List.of(
                "Name:Flood", "ManaCost:0", "Types:Artifact", "Oracle:Test."));
        for (int i = 0; i < 100; i++) {
            floodScript.add("DeckRule:Id$ bad" + i + " | Mode$ UNKNOWN | Card$ X");
        }
        final CardRules floodRules = CardRules.fromScript(floodScript);
        final Deck diagnosticFlood = new Deck("diagnostic-flood");
        for (int i = 0; i < 101; i++) {
            diagnosticFlood.getMain().add(new PaperCard(floodRules, "E" + i,
                    CardRarity.Common, 1, false, Integer.toString(i), "Artist", ""));
        }
        trackCurrentManual(diagnosticFlood);
        assertIssue(service.plan(diagnosticFlood,
                        DeckConstructionContext.builder(name -> List.of()).build(), List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);
    }

    @Test
    void deletingDiagnosticFloodDoesNotSelfStaleExpectedManifestCheck() {
        final List<String> floodScript = new ArrayList<>(List.of(
                "Name:Flood", "ManaCost:0", "Types:Artifact", "Oracle:Test."));
        for (int i = 0; i < 100; i++) {
            floodScript.add("DeckRule:Id$ bad" + i + " | Mode$ UNKNOWN | Card$ X");
        }
        final CardRules floodRules = CardRules.fromScript(floodScript);
        final Deck deck = new Deck("delete-diagnostic-flood");
        final List<DeckConstructionEdit> removals = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            final PaperCard card = new PaperCard(floodRules, "E" + i,
                    CardRarity.Common, 1, false, Integer.toString(i), "Artist", "");
            deck.getMain().add(card);
            removals.add(DeckConstructionEdit.remove(card, DeckSection.Main, 1));
        }
        trackCurrentManual(deck);
        final DeckConstructionContext context = DeckConstructionContext.builder(name -> List.of())
                .catalogGeneration("delete-flood-1").build();

        final DeckConstructionPlan plan = service.plan(deck, context, removals);

        assertEquals(DeckConstructionPlan.Status.READY, plan.getStatus(), () -> plan.getIssues().toString());
        assertTrue(plan.getExactManifest().isEmpty());
        assertEquals(DeckConstructionCommitResult.Status.COMMITTED,
                service.commit(deck, context, plan).getStatus());
        assertTrue(deck.getMain().isEmpty());
        assertTrue(deck.getConstructionLedger().getEntries().isEmpty());
    }

    @Test
    void choiceEncodingBudgetSubtractsOverwrittenChoiceAndRejectsActualOverflow() {
        final PaperCard vanilla = card("Vanilla", "TST", 1, false, "1");
        final DeckPrintingKey small = DeckPrintingKey.from(vanilla);
        final DeckPrintingKey large = new DeckPrintingKey("Choice", "TST", 1, "1", false,
                Map.of(), "v".repeat(DeckPrintingKey.MAX_FUNCTIONAL_VARIANT_LENGTH));
        final int sourceBytes = "SOURCE".getBytes(StandardCharsets.UTF_8).length;
        long used = sourceBytes + "replace".getBytes(StandardCharsets.UTF_8).length
                + small.encodedLength();
        int choicesThatFit = 0;
        while (true) {
            final int next = sourceBytes + ("r" + choicesThatFit).getBytes(StandardCharsets.UTF_8).length
                    + large.encodedLength();
            if (used + next > DeckConstructionContext.MAX_CHOICE_ENCODING_UTF8_BYTES) {
                break;
            }
            used += next;
            choicesThatFit++;
        }

        final DeckConstructionContext.Builder valid = DeckConstructionContext.builder(name -> List.of())
                .choose("Source", "replace", large)
                .choose("Source", "replace", small);
        for (int i = 0; i < choicesThatFit; i++) {
            valid.choose("Source", "r" + i, large);
        }
        assertEquals(DeckConstructionPlan.Status.NO_CHANGE,
                service.plan(deck(vanilla, 1), valid.build(), List.of()).getStatus());

        final DeckConstructionContext.Builder overflow = DeckConstructionContext.builder(name -> List.of())
                .choose("Source", "replace", large)
                .choose("Source", "replace", small);
        for (int i = 0; i <= choicesThatFit; i++) {
            overflow.choose("Source", "r" + i, large);
        }
        assertIssue(service.plan(deck(vanilla, 1), overflow.build(), List.of()),
                DeckConstructionPlan.Status.BLOCKED, DeckConstructionIssue.Code.RESOURCE_LIMIT);
    }

    private void assertCycle(final Map<String, String> scripts) {
        final List<PaperCard> cards = scripts.entrySet().stream()
                .map(entry -> card(entry.getKey(), "TST", 1, false, "1", entry.getValue()))
                .toList();
        final PaperCard source = cards.stream().filter(card -> card.getName().equals("A")).findFirst().orElseThrow();
        final Deck deck = deck(source, 1);
        final DeckConstructionLedger ledger = deck.getConstructionLedger().copy();
        final DeckConstructionPlan plan = service.plan(deck,
                new Catalog(cards.toArray(PaperCard[]::new)).context(), List.of());
        assertIssue(plan, DeckConstructionPlan.Status.UNRESOLVED,
                DeckConstructionIssue.Code.CYCLIC_DEPENDENCY);
        assertEquals(1, deck.getMain().countDistinct());
        assertTrue(ledger.semanticallyEquals(deck.getConstructionLedger()));
    }

    private void assertFixedMigration(final String oldRule, final String newRule) {
        final PaperCard oldSource = card("Source", "SRC", 1, false, "1", oldRule);
        final PaperCard friend = card("Friend", "GEN", 1, false, "2");
        final PaperCard other = card("Other", "GEN", 1, false, "3");
        final Catalog catalog = new Catalog(oldSource, friend, other);
        final Deck deck = deck(oldSource, 1);
        commit(deck, catalog.context(), service.plan(deck, catalog.context(), List.of()));
        replaceSource(deck, oldSource, card("Source", "SRC", 1, false, "1", newRule));

        assertIssue(service.plan(deck, catalog.context(), List.of()),
                DeckConstructionPlan.Status.UNRESOLVED, DeckConstructionIssue.Code.MIGRATION_REQUIRED);
        assertEquals(1, deck.getMain().count(friend));
    }

    private static void replaceSource(final Deck deck, final PaperCard oldSource, final PaperCard newSource) {
        deck.getMain().removeAll(oldSource);
        deck.getMain().add(newSource);
    }

    private static void assertIssue(final DeckConstructionPlan plan, final DeckConstructionPlan.Status status,
            final DeckConstructionIssue.Code code) {
        assertEquals(status, plan.getStatus());
        assertFalse(plan.getIssues().isEmpty());
        assertEquals(code, plan.getIssues().get(0).getCode());
    }

    private void commit(final Deck deck, final DeckConstructionContext context,
            final DeckConstructionPlan plan) {
        assertTrue(plan.canCommit(), () -> plan.getIssues().toString());
        assertEquals(DeckConstructionCommitResult.Status.COMMITTED,
                service.commit(deck, context, plan).getStatus());
    }

    private static Deck deck(final PaperCard card, final int amount) {
        final Deck deck = new Deck("test");
        deck.getMain().add(card, amount);
        trackCurrentManual(deck);
        return deck;
    }

    private static void trackCurrentManual(final Deck deck) {
        final List<DeckConstructionLedger.Entry> entries = new ArrayList<>();
        for (final DeckSection section : DeckSection.values()) {
            if (deck.get(section) == null) {
                continue;
            }
            for (final Map.Entry<PaperCard, Integer> card : deck.get(section)) {
                entries.add(new DeckConstructionLedger.Entry(section, DeckPrintingKey.from(card.getKey()),
                        card.getValue(), Map.of()));
            }
        }
        deck.setConstructionLedger(DeckConstructionLedger.tracked(
                deck.getConstructionLedger().getLedgerId(), entries));
    }

    private static PaperCard card(final String name, final String edition, final int artIndex,
            final boolean foil, final String collectorNumber, final String... deckRules) {
        return cardVariant(name, edition, artIndex, foil, collectorNumber, "", deckRules);
    }

    private static PaperCard cardVariant(final String name, final String edition, final int artIndex,
            final boolean foil, final String collectorNumber, final String functionalVariant,
            final String... deckRules) {
        final List<String> script = new ArrayList<>(List.of(
                "Name:" + name,
                "ManaCost:0",
                "Types:Artifact",
                "Oracle:Test card."));
        for (final String rule : deckRules) {
            script.add("DeckRule:" + rule);
        }
        return new PaperCard(CardRules.fromScript(script), edition, CardRarity.Common, artIndex, foil,
                collectorNumber, "Artist", functionalVariant);
    }

    private static String fixed(final String id, final DeckSection target, final String card, final int amount) {
        return "Id$ " + id + " | Mode$ ADD_FIXED | Target$ " + target.name()
                + " | Card$ " + card + " | Amount$ " + amount;
    }

    private static String choose(final String id, final DeckSection target, final int amount,
            final String... candidates) {
        return "Id$ " + id + " | Mode$ CHOOSE_ONE | Target$ " + target.name()
                + " | Candidates$ " + String.join(";", candidates) + " | Amount$ " + amount;
    }

    private static String allow(final String id, final String constraint, final String card,
            final DeckSection target) {
        return "Id$ " + id + " | Mode$ ALLOW | Constraint$ " + constraint + " | Card$ " + card
                + (target == null ? "" : " | Target$ " + target.name());
    }

    private static final class Catalog {
        private final Map<String, List<PaperCard>> printings = new HashMap<>();
        private final Map<String, AtomicInteger> lookups = new HashMap<>();

        private Catalog(final PaperCard... cards) {
            for (final PaperCard card : cards) {
                printings.computeIfAbsent(DeckConstructionRule.canonicalCardNameKey(card.getName()),
                        ignored -> new ArrayList<>()).add(card);
            }
        }

        private DeckConstructionContext context() {
            return contextBuilder().build();
        }

        private DeckConstructionContext.Builder contextBuilder() {
            return DeckConstructionContext.builder(name -> {
                lookups.computeIfAbsent(name, ignored -> new AtomicInteger()).incrementAndGet();
                return printings.getOrDefault(name, List.of());
            }).catalogGeneration("catalog-1");
        }

        private int lookups(final String canonicalName) {
            return lookups.getOrDefault(canonicalName, new AtomicInteger()).get();
        }

        private void replace(final PaperCard card) {
            printings.put(DeckConstructionRule.canonicalCardNameKey(card.getName()),
                    new ArrayList<>(List.of(card)));
        }
    }
}
