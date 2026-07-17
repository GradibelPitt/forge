package forge.gui;

import forge.util.ITranslatable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CardNameSearchIndexTest {
    @Test
    void chineseQueryDoesNotNormalizeToEmptyAndReturnsCanonicalCandidate() {
        final Candidate garden = new Candidate("Garden of Hope", "希望花园");
        final Candidate other = new Candidate("Island", "海岛");
        final CardNameSearchIndex<Candidate> index = new CardNameSearchIndex<>(List.of(other, garden), null);

        assertEquals("园", CardNameSearchIndex.normalize("园"));
        final List<Candidate> result = index.search("园");
        assertEquals(1, result.size());
        assertSame(garden, result.get(0));
        assertEquals("Garden of Hope", result.get(0).getName());
    }

    @Test
    void indexesCanonicalEnglishAlongsideTranslatedDisplay() {
        final Candidate garden = new Candidate("Garden of Hope", "希望花园");
        final CardNameSearchIndex<Candidate> index = new CardNameSearchIndex<>(List.of(garden), null);

        assertEquals(List.of(garden), index.search("garden"));
        assertEquals("希望花园 / Garden of Hope", CardNameSearchIndex.displayLabel(garden,
                value -> ((Candidate) value).getTranslatedName()));
    }

    @Test
    void normalizesUnicodeWidthCaseDiacriticsWhitespaceAndPunctuation() {
        assertEquals("cafe花园42", CardNameSearchIndex.normalize(" ＣＡＦÉ—花 园！４２ "));
    }

    @Test
    void ranksExactThenPrefixThenSubstringThenFuzzyAndPreservesInputOrder() {
        final Candidate substring = new Candidate("The Garden Room", "The Garden Room");
        final Candidate fuzzy = new Candidate("Gardin", "Gardin");
        final Candidate exactFirst = new Candidate("Garden", "Garden");
        final Candidate prefixFirst = new Candidate("Gardener", "Gardener");
        final Candidate exactSecond = new Candidate("GARDEN", "GARDEN");
        final CardNameSearchIndex<Candidate> index = new CardNameSearchIndex<>(
                List.of(substring, fuzzy, exactFirst, prefixFirst, exactSecond), null);

        assertEquals(List.of(exactFirst, exactSecond, prefixFirst, substring, fuzzy), index.search("garden"));
        assertEquals(List.of(exactFirst, exactSecond, prefixFirst, substring), index.searchWithoutFuzzy("garden"));
    }

    @Test
    void emptyQueryRestoresOnlyOriginalCandidatesAndAbsentCandidateNeverLeaks() {
        final Candidate allowed = new Candidate("Allowed", "允许");
        final Candidate hidden = new Candidate("Hidden", "隐藏");
        final CardNameSearchIndex<Candidate> index = new CardNameSearchIndex<>(List.of(allowed), null);

        assertEquals(List.of(allowed), index.search(""));
        assertEquals(List.of(), index.search(hidden.getTranslatedName()));
    }

    @Test
    void tenThousandCandidatesAreIndexedOnceAndQueriesDoNotRevisitDisplayProvider() {
        final AtomicInteger displayVisits = new AtomicInteger();
        final List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            candidates.add(new Candidate("Candidate " + i, "候选 " + i));
        }
        final CardNameSearchIndex<Candidate> index = new CardNameSearchIndex<>(candidates, candidate -> {
            displayVisits.incrementAndGet();
            return candidate.getTranslatedName();
        });
        assertEquals(10_000, displayVisits.get());

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertSame(candidates.get(9_999), index.search("候选9999").get(0));
            assertSame(candidates.get(9_999), index.search("candidate9999").get(0));
        });
        assertEquals(10_000, displayVisits.get());

        assertTimeoutPreemptively(Duration.ofMillis(500), () -> {
            assertEquals(1, index.searchWithoutFuzzy("候选9999").size());
            assertEquals(1, index.searchWithoutFuzzy("candidate9999").size());
        });
        assertEquals(10_000, displayVisits.get(), "mobile search must reuse the one-time index");
    }

    @Test
    void staleBackgroundGenerationCannotReplaceLatestResultsOrClosedDialog() {
        final LatestSearchGeneration generation = new LatestSearchGeneration();
        final long oldQuery = generation.begin();
        final long currentQuery = generation.begin();

        assertFalse(generation.isCurrent(oldQuery));
        assertTrue(generation.isCurrent(currentQuery));
        generation.invalidate();
        assertFalse(generation.isCurrent(currentQuery));
    }

    private record Candidate(String name, String translatedName) implements ITranslatable {
        @Override public String getName() { return name; }
        @Override public String getTranslatedName() { return translatedName; }
    }
}
