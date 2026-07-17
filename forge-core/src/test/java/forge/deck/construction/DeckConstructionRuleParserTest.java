package forge.deck.construction;

import forge.card.CardRules;
import forge.card.CardSplitType;
import forge.deck.DeckSection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckConstructionRuleParserTest {
    private static final String SOURCE = "Construction Source";

    @Test
    void parsesAllSupportedModesWithoutResolvingCards() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ fixed | Mode$ add_fixed | Target$ main | Card$ Generated One | Amount$ 2",
                "Id$ choice | Mode$ CHOOSE_ONE | Target$ Sideboard | Candidates$ First;Second | Amount$ 1",
                "Id$ allow | Mode$ ALLOW | Constraint$ commander_color_identity | Card$ Guest"
        ));

        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(3, result.getRules().size());

        final DeckConstructionRule fixed = result.getRules().get(0);
        assertEquals(SOURCE, fixed.getSourceCardName());
        assertTrue(fixed.getGlobalKey().startsWith("rk1."));
        assertEquals("CONSTRUCTION SOURCE", fixed.getRuleKey().getSourceCanonicalName());
        assertEquals("fixed", fixed.getRuleKey().getRuleId());
        assertEquals("CONSTRUCTION SOURCE", fixed.getSourceCardKey());
        assertEquals("CONSTRUCTION SOURCE", fixed.getSourceCanonicalName());
        assertEquals(DeckConstructionRule.CURRENT_SCHEMA_VERSION, fixed.getSchemaVersion());
        assertEquals(DeckConstructionRule.Cardinality.ONCE_PER_DECK, fixed.getCardinality());
        assertEquals(64, fixed.getContentFingerprint().length());
        assertEquals(DeckConstructionRule.Mode.ADD_FIXED, fixed.getMode());
        assertEquals(DeckSection.Main, fixed.getTarget());
        assertEquals("Generated One", fixed.getCardName());
        assertEquals(2, fixed.getAmount());
        assertTrue(fixed.getCandidates().isEmpty());
        assertEquals(null, fixed.getConstraint());

        final DeckConstructionRule choice = result.getRules().get(1);
        assertEquals(DeckConstructionRule.Mode.CHOOSE_ONE, choice.getMode());
        assertEquals(List.of("First", "Second"), choice.getCandidates());
        assertEquals(1, choice.getAmount());

        final DeckConstructionRule allow = result.getRules().get(2);
        assertEquals(DeckConstructionRule.Mode.ALLOW, allow.getMode());
        assertEquals(null, allow.getTarget());
        assertEquals(DeckConstructionRule.Constraint.COMMANDER_COLOR_IDENTITY, allow.getConstraint());
        assertEquals("Guest", allow.getCardName());
        assertEquals(0, allow.getAmount());
    }

    @Test
    void duplicateIdsMakeEveryDefinitionWithThatIdInactive() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                fixed("duplicate", "First", 1),
                fixed("unique", "Unique", 1),
                fixed("duplicate", "Second", 1)
        ));

        assertEquals(List.of("unique"), result.getRules().stream().map(DeckConstructionRule::getId).toList());
        assertEquals(2, result.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getCode() == DeckConstructionDiagnostic.Code.DUPLICATE_ID)
                .count());
        assertTrue(result.getDiagnostics().stream().allMatch(DeckConstructionDiagnostic::isInactive));
    }

    @Test
    void ruleIdsAreTrimmedNfcAndRemainCaseSensitive() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(" Straße ", List.of(
                fixed("Case", "First", 1),
                fixed("case", "Second", 1)
        ));

        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(List.of("Case", "case"),
                result.getRules().stream().map(DeckConstructionRule::getId).toList());
        assertTrue(result.getRules().get(0).getGlobalKey().startsWith("rk1."));
        assertEquals("Straße", result.getRules().get(0).getSourceCardName());
    }

    @Test
    void rejectsUnknownModeAndConstraintWithoutThrowing() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ mode | Mode$ INVENTED | Target$ Main | Card$ A | Amount$ 1",
                "Id$ constraint | Mode$ ALLOW | Target$ Main | Constraint$ INVENTED | Card$ A"
        ));

        assertTrue(result.getRules().isEmpty());
        assertEquals(List.of(
                        DeckConstructionDiagnostic.Code.UNKNOWN_MODE,
                        DeckConstructionDiagnostic.Code.UNKNOWN_CONSTRAINT),
                result.getDiagnostics().stream().map(DeckConstructionDiagnostic::getCode).toList());
    }

    @Test
    void rejectsMalformedDuplicateAndUnknownFields() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, Arrays.asList(
                null,
                "",
                "Id without delimiter",
                "Id$ one | Id$ two | Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ 1",
                "Id$ extra | Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ 1 | Amuont$ 2"
        ));

        assertTrue(result.getRules().isEmpty());
        assertEquals(7, result.getDiagnostics().size());
        assertEquals(DeckConstructionDiagnostic.Code.EMPTY_RULE, result.getDiagnostics().get(0).getCode());
        assertEquals(DeckConstructionDiagnostic.Code.EMPTY_RULE, result.getDiagnostics().get(1).getCode());
        assertEquals(DeckConstructionDiagnostic.Code.MALFORMED_FIELD, result.getDiagnostics().get(2).getCode());
        assertEquals(DeckConstructionDiagnostic.Code.DUPLICATE_FIELD, result.getDiagnostics().get(3).getCode());
        assertEquals(List.of("one", "two"), result.getDiagnostics().subList(4, 6).stream()
                .map(DeckConstructionDiagnostic::getRuleId).toList());
        assertEquals(DeckConstructionDiagnostic.Code.UNKNOWN_FIELD, result.getDiagnostics().get(6).getCode());
        assertEquals("extra", result.getDiagnostics().get(6).getRuleId());
    }

    @Test
    void malformedDefinitionsStillReserveEveryScannableId() {
        final List<String> malformed = List.of(
                fixed("shared", "Malformed", 1) + " | Bogus$ value",
                "Id$ shared | Id$ other | Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ 1",
                fixed("shared", "Malformed", 1) + " | trailing"
        );

        for (final String badRule : malformed) {
            final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                    fixed("shared", "Valid", 1), badRule));

            assertTrue(result.getRules().isEmpty(), badRule);
            assertTrue(result.getDiagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.getCode()
                            == DeckConstructionDiagnostic.Code.DUPLICATE_ID));
            assertTrue(result.getDiagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.getRuleIndex() == 1
                            && "shared".equals(diagnostic.getRuleId())));
        }
    }

    @Test
    void malformedRuleWithoutIdDoesNotDisableAnUnrelatedValidRule() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                fixed("valid", "Valid", 1),
                "Mode$ ADD_FIXED | Target$ Main | Card$ Bad | Amount$ 1 | trailing"
        ));

        assertEquals(List.of("valid"),
                result.getRules().stream().map(DeckConstructionRule::getId).toList());
        assertEquals(1, result.getDiagnostics().size());
        assertEquals(DeckConstructionDiagnostic.Code.MALFORMED_FIELD,
                result.getDiagnostics().get(0).getCode());
        assertEquals(null, result.getDiagnostics().get(0).getRuleId());
    }

    @Test
    void malformedRuleWithTwoIdsReservesBothIds() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                fixed("first", "First", 1),
                fixed("second", "Second", 1),
                "Id$ first | ID$ second | Mode$ ADD_FIXED | Target$ Main | Card$ Bad | Amount$ 1"
        ));

        assertTrue(result.getRules().isEmpty());
        assertEquals(2, result.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getCode() == DeckConstructionDiagnostic.Code.DUPLICATE_ID)
                .count());
        assertTrue(result.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getRuleIndex() == 2)
                .map(DeckConstructionDiagnostic::getRuleId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(List.of("first", "second")));
    }

    @Test
    void malformedIdPrescanUsesNfcButKeepsCaseSensitivity() {
        final DeckConstructionRuleParser.Result duplicate = DeckConstructionRuleParser.parse(SOURCE, List.of(
                fixed("café", "Valid", 1),
                fixed("cafe\u0301", "Bad", 1) + " | trailing"
        ));
        final DeckConstructionRuleParser.Result differentCase = DeckConstructionRuleParser.parse(SOURCE, List.of(
                fixed("Case", "Valid", 1),
                fixed("case", "Bad", 1) + " | trailing"
        ));

        assertTrue(duplicate.getRules().isEmpty());
        assertEquals(List.of("Case"),
                differentCase.getRules().stream().map(DeckConstructionRule::getId).toList());
    }

    @Test
    void acceptsLowercaseParameterNames() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "id$ lower | mode$ add_fixed | target$ main | card$ Generated | amount$ 2"
        ));

        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(1, result.getRules().size());
        assertEquals("lower", result.getRules().get(0).getId());
        assertEquals("Generated", result.getRules().get(0).getCardName());
    }

    @Test
    void parameterNamesAreCaseInsensitiveForDuplicateDetection() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ first | ID$ second | Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ 1"
        ));

        assertTrue(result.getRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.DUPLICATE_FIELD,
                result.getDiagnostics().get(0).getCode());
    }

    @Test
    void rejectsMissingModeSpecificFields() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ 1",
                "Id$ fixed-card | Mode$ ADD_FIXED | Target$ Main | Amount$ 1",
                "Id$ fixed-amount | Mode$ ADD_FIXED | Target$ Main | Card$ A",
                "Id$ choose | Mode$ CHOOSE_ONE | Target$ Main | Amount$ 1",
                "Id$ allow-constraint | Mode$ ALLOW | Target$ Main | Card$ A",
                "Id$ allow-card | Mode$ ALLOW | Target$ Main | Constraint$ COPY_LIMIT"
        ));

        assertTrue(result.getRules().isEmpty());
        assertEquals(6, result.getDiagnostics().size());
        assertTrue(result.getDiagnostics().stream()
                .allMatch(diagnostic -> diagnostic.getCode() == DeckConstructionDiagnostic.Code.MISSING_FIELD));
    }

    @Test
    void validatesTargetAmountAndCandidateBounds() {
        final List<String> tooManyCandidates = new ArrayList<>();
        for (int i = 0; i <= DeckConstructionRuleParser.MAX_CANDIDATES; i++) {
            tooManyCandidates.add("C" + i);
        }
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ target | Mode$ ADD_FIXED | Target$ Nowhere | Card$ A | Amount$ 1",
                fixed("zero", "A", 0),
                fixed("too-large", "A", DeckConstructionRuleParser.MAX_AMOUNT + 1),
                "Id$ overflow | Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ 999999999999999999999",
                "Id$ empty-candidate | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ A;;B | Amount$ 1",
                "Id$ too-many | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ "
                        + String.join(";", tooManyCandidates) + " | Amount$ 1"
        ));

        assertTrue(result.getRules().isEmpty());
        assertEquals(List.of(
                        DeckConstructionDiagnostic.Code.INVALID_TARGET,
                        DeckConstructionDiagnostic.Code.INVALID_AMOUNT,
                        DeckConstructionDiagnostic.Code.INVALID_AMOUNT,
                        DeckConstructionDiagnostic.Code.INVALID_AMOUNT,
                        DeckConstructionDiagnostic.Code.EMPTY_CANDIDATE,
                        DeckConstructionDiagnostic.Code.TOO_MANY_CANDIDATES),
                result.getDiagnostics().stream().map(DeckConstructionDiagnostic::getCode).toList());
    }

    @Test
    void acceptsAmountAndCandidateLimitsExactly() {
        final List<String> candidates = new ArrayList<>();
        for (int i = 0; i < DeckConstructionRuleParser.MAX_CANDIDATES; i++) {
            candidates.add("C" + i);
        }
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                fixed("minimum", "A", 1),
                fixed("maximum", "B", DeckConstructionRuleParser.MAX_AMOUNT),
                "Id$ candidates | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ "
                        + String.join(";", candidates) + " | Amount$ 1"
        ));

        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(3, result.getRules().size());
        assertEquals(DeckConstructionRuleParser.MAX_CANDIDATES, result.getRules().get(2).getCandidates().size());
    }

    @Test
    void amountSyntaxUsesOnlyAsciiDigitsAndAnOptionalAsciiSign() {
        final List<String> invalidAmounts = List.of(
                "\u0661",       // Arabic-Indic digit one
                "\uff11",       // fullwidth digit one
                "\uff0b1",      // fullwidth plus
                "\u22121",      // Unicode minus
                "+",
                "-",
                "++1"
        );
        for (int index = 0; index < invalidAmounts.size(); index++) {
            final String amount = invalidAmounts.get(index);
            final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                    "Id$ unicode-" + index + " | Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ "
                            + amount));

            assertTrue(result.getRules().isEmpty(), amount);
            assertEquals(DeckConstructionDiagnostic.Code.INVALID_AMOUNT,
                    result.getDiagnostics().get(0).getCode());
        }

        final DeckConstructionRuleParser.Result valid = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ plus | Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ +1",
                "Id$ minimum | Mode$ ADD_FIXED | Target$ Main | Card$ B | Amount$ 1",
                "Id$ maximum | Mode$ ADD_FIXED | Target$ Main | Card$ C | Amount$ 1000"
        ));
        final DeckConstructionRuleParser.Result outOfRange = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ negative | Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ -1",
                "Id$ zero | Mode$ ADD_FIXED | Target$ Main | Card$ B | Amount$ 0",
                "Id$ large | Mode$ ADD_FIXED | Target$ Main | Card$ C | Amount$ 1001"
        ));

        assertTrue(valid.getDiagnostics().isEmpty());
        assertEquals(List.of(1, 1, 1000),
                valid.getRules().stream().map(DeckConstructionRule::getAmount).toList());
        assertTrue(outOfRange.getRules().isEmpty());
        assertTrue(outOfRange.getDiagnostics().stream()
                .allMatch(diagnostic -> diagnostic.getCode()
                        == DeckConstructionDiagnostic.Code.INVALID_AMOUNT));
    }

    @Test
    void unicodeAmountDoesNotCrashCardRulesInitialization() {
        final CardRules rules = CardRules.fromScript(List.of(
                "Name:Unicode Amount Source",
                "ManaCost:0",
                "Types:Artifact",
                "DeckRule:Id$ unicode | Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ \u0661"
        ));

        assertTrue(rules.getDeckConstructionRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.INVALID_AMOUNT,
                rules.getDeckConstructionDiagnostics().get(0).getCode());
    }

    @Test
    void candidateDeduplicationIsUnicodeCanonicalAndLocaleIndependent() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ unicode | Mode$ CHOOSE_ONE | Target$ Main | "
                        + "Candidates$ Éclair;e\u0301clair;ÉCLAIR;Straße;STRASSE;Other | Amount$ 1"
        ));

        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(List.of("Éclair", "Straße", "Other"), result.getRules().get(0).getCandidates());
    }

    @Test
    void candidateLimitCountsCanonicalNamesAfterDeduplication() {
        final List<String> spellings = new ArrayList<>();
        for (int i = 0; i <= DeckConstructionRuleParser.MAX_CANDIDATES; i++) {
            spellings.add(i % 2 == 0 ? "Straße" : "STRASSE");
        }

        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ duplicates | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ "
                        + String.join(";", spellings) + " | Amount$ 1"
        ));

        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(List.of("Straße"), result.getRules().get(0).getCandidates());
    }

    @Test
    void fingerprintUsesCanonicalFieldOrderAndDefaultCardinality() {
        final DeckConstructionRule first = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ first | Mode$ ADD_FIXED | Target$ Main | Card$ Éclair | Amount$ 2"
        )).getRules().get(0);
        final DeckConstructionRule reordered = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Amount$ 2 | Card$ e\u0301CLAIR | Target$ main | Mode$ add_fixed | "
                        + "Cardinality$ once_per_deck | Id$ second"
        )).getRules().get(0);
        final DeckConstructionRule changed = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ third | Mode$ ADD_FIXED | Target$ Main | Card$ Éclair | Amount$ 3"
        )).getRules().get(0);

        assertEquals(first.getContentFingerprint(), reordered.getContentFingerprint());
        assertFalse(first.getContentFingerprint().equals(changed.getContentFingerprint()));
    }

    @Test
    void ruleKeyIsStructuredComparableAndGlobalEncodingIsUnambiguous() {
        final DeckConstructionRule first = DeckConstructionRuleParser.parse("AB",
                List.of(fixed("C", "One", 1))).getRules().get(0);
        final DeckConstructionRule second = DeckConstructionRuleParser.parse("A",
                List.of(fixed("BC", "Two", 1))).getRules().get(0);
        final DeckConstructionRule firstAgain = DeckConstructionRuleParser.parse("ab",
                List.of(fixed("C", "Three", 1))).getRules().get(0);
        final Map<DeckConstructionRule.RuleKey, String> rules = new HashMap<>();
        rules.put(first.getRuleKey(), "first");
        rules.put(second.getRuleKey(), "second");

        assertNotEquals(first.getRuleKey(), second.getRuleKey());
        assertNotEquals(first.getGlobalKey(), second.getGlobalKey());
        assertEquals(first.getRuleKey(), firstAgain.getRuleKey());
        assertEquals(0, first.getRuleKey().compareTo(firstAgain.getRuleKey()));
        assertEquals(2, rules.size());
        assertTrue(first.getGlobalKey().startsWith("rk1."));
        assertFalse(first.getGlobalKey().contains(first.getSourceCardName()));
    }

    @Test
    void ruleKeyFactoryCanonicalizesAndValidatesExternalValues() {
        final DeckConstructionRule.RuleKey first = DeckConstructionRule.RuleKey.of(
                " Straße ", " cafe\u0301 ");
        final DeckConstructionRule.RuleKey second = DeckConstructionRule.RuleKey.of(
                "STRASSE", " café ");

        assertEquals(first, second);
        assertEquals("STRASSE", first.getSourceCanonicalName());
        assertEquals("café", first.getRuleId());
        assertThrows(IllegalArgumentException.class,
                () -> DeckConstructionRule.RuleKey.of("Source\u001fName", "id"));
        assertThrows(IllegalArgumentException.class,
                () -> DeckConstructionRule.RuleKey.of("Source", "before\u0000after"));
        assertThrows(IllegalArgumentException.class,
                () -> DeckConstructionRule.RuleKey.of("Source", " "));
        assertThrows(IllegalArgumentException.class,
                () -> DeckConstructionRule.RuleKey.of("Source",
                        "I".repeat(DeckConstructionRuleParser.MAX_RULE_ID_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> DeckConstructionRule.RuleKey.of(
                        "S".repeat(DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH + 1), "id"));
    }

    @Test
    void controlCharactersInSourceOrIdAreRejectedWithoutKeyCollisions() {
        final List<String> controls = List.of("\u001f", "\n", "\u0000", "\u007f");
        for (final String control : controls) {
            final DeckConstructionRuleParser.Result source = DeckConstructionRuleParser.parse(
                    "Source" + control + "Name", List.of(fixed("id", "A", 1)));
            final DeckConstructionRuleParser.Result id = DeckConstructionRuleParser.parse(SOURCE,
                    List.of(fixed("before" + control + "after", "A", 1)));

            assertTrue(source.getRules().isEmpty());
            assertTrue(id.getRules().isEmpty());
            assertEquals(DeckConstructionDiagnostic.Code.INVALID_CONTROL_CHARACTER,
                    source.getDiagnostics().get(0).getCode());
            assertEquals(DeckConstructionDiagnostic.Code.INVALID_CONTROL_CHARACTER,
                    id.getDiagnostics().get(0).getCode());
        }

        final DeckConstructionRuleParser.Result oldLeft = DeckConstructionRuleParser.parse(
                "A\u001fB", List.of(fixed("C", "A", 1)));
        final DeckConstructionRuleParser.Result oldRight = DeckConstructionRuleParser.parse(
                "A", List.of(fixed("B\u001fC", "A", 1)));
        assertTrue(oldLeft.getRules().isEmpty());
        assertTrue(oldRight.getRules().isEmpty());
    }

    @Test
    void unsupportedCardinalityIsInactiveInsteadOfSilentlyDefaulting() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ per-copy | Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ 1 | Cardinality$ PER_COPY",
                "Id$ future | Mode$ ADD_FIXED | Target$ Main | Card$ B | Amount$ 1 | Cardinality$ SOMEDAY"
        ));

        assertTrue(result.getRules().isEmpty());
        assertEquals(List.of(
                        DeckConstructionDiagnostic.Code.UNSUPPORTED_CARDINALITY,
                        DeckConstructionDiagnostic.Code.UNSUPPORTED_CARDINALITY),
                result.getDiagnostics().stream().map(DeckConstructionDiagnostic::getCode).toList());
    }

    @Test
    void reservesCrossLayerDiagnosticCodes() {
        assertTrue(List.of(DeckConstructionDiagnostic.Code.values()).containsAll(List.of(
                DeckConstructionDiagnostic.Code.CYCLIC_DEPENDENCY,
                DeckConstructionDiagnostic.Code.MIGRATION_REQUIRED,
                DeckConstructionDiagnostic.Code.MISSING_CARD,
                DeckConstructionDiagnostic.Code.RESOURCE_LIMIT)));
    }

    @Test
    void diagnosticsBoundUnicodeFieldsWithoutSplittingSurrogatePairs() {
        final DeckConstructionDiagnostic diagnostic = new DeckConstructionDiagnostic(
                DeckConstructionDiagnostic.Code.INVALID_SOURCE_LAYOUT,
                "😀".repeat(2_000),
                "\u001f" + "😀".repeat(500),
                7,
                "😀".repeat(200),
                "😀".repeat(3_000));

        assertEquals(DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH,
                DeckConstructionDiagnostic.MAX_SOURCE_UTF8_BYTES);
        assertEquals(DeckConstructionRuleParser.MAX_RULE_ID_LENGTH,
                DeckConstructionDiagnostic.MAX_RULE_ID_UTF8_BYTES);
        assertTrue(diagnostic.getSourceCardName().getBytes(StandardCharsets.UTF_8).length
                <= DeckConstructionDiagnostic.MAX_SOURCE_UTF8_BYTES);
        assertTrue(diagnostic.getRuleId().getBytes(StandardCharsets.UTF_8).length
                <= DeckConstructionDiagnostic.MAX_RULE_ID_UTF8_BYTES);
        assertTrue(diagnostic.getRawRule().getBytes(StandardCharsets.UTF_8).length
                <= DeckConstructionDiagnostic.MAX_RAW_RULE_UTF8_BYTES);
        assertTrue(diagnostic.getMessage().getBytes(StandardCharsets.UTF_8).length
                <= DeckConstructionDiagnostic.MAX_MESSAGE_UTF8_BYTES);
        assertTrue(diagnostic.getRuleId().startsWith("\u001f"));
        for (final String value : List.of(diagnostic.getSourceCardName(), diagnostic.getRuleId(),
                diagnostic.getRawRule(), diagnostic.getMessage())) {
            assertFalse(Character.isHighSurrogate(value.charAt(value.length() - 1)));
        }
    }

    @Test
    void rejectsOversizedRuleFieldAndRuleSetWithoutThrowing() {
        final String oversizedField = "A".repeat(DeckConstructionRuleParser.MAX_FIELD_LENGTH + 1);
        final String oversizedRule = "X".repeat(DeckConstructionRuleParser.MAX_RULE_LENGTH + 1);
        final List<String> tooManyRules = Collections.nCopies(DeckConstructionRuleParser.MAX_RULES + 1,
                fixed("same", "A", 1));

        final DeckConstructionRuleParser.Result field = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ field | Mode$ ADD_FIXED | Target$ Main | Card$ " + oversizedField + " | Amount$ 1"));
        final DeckConstructionRuleParser.Result rule = DeckConstructionRuleParser.parse(SOURCE,
                List.of(oversizedRule));
        final DeckConstructionRuleParser.Result set = DeckConstructionRuleParser.parse(SOURCE, tooManyRules);

        assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT, field.getDiagnostics().get(0).getCode());
        assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT, rule.getDiagnostics().get(0).getCode());
        assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT, set.getDiagnostics().get(0).getCode());
        assertTrue(field.getRules().isEmpty());
        assertTrue(rule.getRules().isEmpty());
        assertTrue(set.getRules().isEmpty());
    }

    @Test
    void rejectsIdentifiersAndCardNamesThatDownstreamStorageCannotRepresent() {
        final String oversizedId = "I".repeat(DeckConstructionRuleParser.MAX_RULE_ID_LENGTH + 1);
        final String oversizedName = "C".repeat(DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH + 1);
        final String canonicallyOversizedName = "ß".repeat(
                DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH / 2 + 1);

        final DeckConstructionRuleParser.Result id = DeckConstructionRuleParser.parse(SOURCE,
                List.of(fixed(oversizedId, "A", 1)));
        final DeckConstructionRuleParser.Result source = DeckConstructionRuleParser.parse(oversizedName,
                List.of(fixed("source", "A", 1)));
        final DeckConstructionRuleParser.Result canonicalSource = DeckConstructionRuleParser.parse(
                canonicallyOversizedName, List.of(fixed("canonical-source", "A", 1)));
        final DeckConstructionRuleParser.Result card = DeckConstructionRuleParser.parse(SOURCE,
                List.of(fixed("card", oversizedName, 1)));
        final DeckConstructionRuleParser.Result candidate = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ candidate | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ "
                        + oversizedName + " | Amount$ 1"));

        for (final DeckConstructionRuleParser.Result result : List.of(
                id, source, canonicalSource, card, candidate)) {
            assertTrue(result.getRules().isEmpty());
            assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                    result.getDiagnostics().get(0).getCode());
        }
    }

    @Test
    void acceptsIdentifierAndCardNameStorageLimitsExactly() {
        final String maximumId = "I".repeat(DeckConstructionRuleParser.MAX_RULE_ID_LENGTH);
        final String maximumName = "C".repeat(DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH);

        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(maximumName,
                List.of(fixed(maximumId, maximumName, 1)));

        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(1, result.getRules().size());
    }

    @Test
    void resourceLimitedRuleFailsClosedForEveryRuleFromTheSameSource() {
        final String oversized = "X".repeat(DeckConstructionRuleParser.MAX_RULE_LENGTH + 1);

        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                fixed("valid", "Valid", 1), oversized));

        assertTrue(result.getRules().isEmpty());
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getCode()
                        == DeckConstructionDiagnostic.Code.RESOURCE_LIMIT));
    }

    @Test
    void utf8ByteLimitsHandleAstralCharactersAtAndPastBoundaries() {
        final String exactId = "😀".repeat(DeckConstructionRuleParser.MAX_RULE_ID_LENGTH / 4);
        final String oversizedId = exactId + "😀";
        final String exactName = "😀".repeat(DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH / 4);
        final String oversizedName = exactName + "😀";

        final DeckConstructionRuleParser.Result exact = DeckConstructionRuleParser.parse(exactName,
                List.of(fixed(exactId, exactName, 1)));
        final DeckConstructionRuleParser.Result id = DeckConstructionRuleParser.parse(SOURCE,
                List.of(fixed(oversizedId, "A", 1)));
        final DeckConstructionRuleParser.Result source = DeckConstructionRuleParser.parse(oversizedName,
                List.of(fixed("source", "A", 1)));
        final DeckConstructionRuleParser.Result card = DeckConstructionRuleParser.parse(SOURCE,
                List.of(fixed("card", oversizedName, 1)));
        final DeckConstructionRuleParser.Result candidate = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ candidate | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ "
                        + oversizedName + " | Amount$ 1"));

        assertEquals(DeckConstructionRuleParser.MAX_RULE_ID_LENGTH,
                exactId.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH,
                exactName.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(exact.getDiagnostics().isEmpty());
        assertEquals(1, exact.getRules().size());
        for (final DeckConstructionRuleParser.Result result : List.of(id, source, card, candidate)) {
            assertTrue(result.getRules().isEmpty());
            assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                    result.getDiagnostics().get(0).getCode());
        }
    }

    @Test
    void nameLimitsApplyAfterNfcAndUppercaseCanonicalization() {
        final String decomposedAtLimit = "e\u0301".repeat(
                DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH / 2);
        final String expandingUnit = "\u0149";
        final int inputBytes = expandingUnit.getBytes(StandardCharsets.UTF_8).length;
        final int canonicalBytes = DeckConstructionRule.canonicalCardNameKey(expandingUnit)
                .getBytes(StandardCharsets.UTF_8).length;
        final String uppercaseExpansion = expandingUnit.repeat(
                DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH / canonicalBytes + 1);

        final DeckConstructionRuleParser.Result normalized = DeckConstructionRuleParser.parse(
                decomposedAtLimit, List.of(fixed("nfc", decomposedAtLimit, 1)));
        final DeckConstructionRuleParser.Result expanded = DeckConstructionRuleParser.parse(
                uppercaseExpansion, List.of(fixed("expanded", "A", 1)));

        assertTrue(decomposedAtLimit.getBytes(StandardCharsets.UTF_8).length
                > DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH);
        assertEquals(DeckConstructionRuleParser.MAX_CARD_NAME_LENGTH,
                java.text.Normalizer.normalize(decomposedAtLimit, java.text.Normalizer.Form.NFC)
                        .getBytes(StandardCharsets.UTF_8).length);
        assertTrue(canonicalBytes > inputBytes);
        assertTrue(normalized.getDiagnostics().isEmpty());
        assertTrue(expanded.getRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                expanded.getDiagnostics().get(0).getCode());
    }

    @Test
    void rawSegmentLengthAndFieldCountAreHardLimited() {
        final String oversizedSegment = "Id$ value"
                + " ".repeat(DeckConstructionRuleParser.MAX_FIELD_LENGTH - "Id$ value".length() + 1);
        final List<String> tooManyFields = new ArrayList<>();
        for (int i = 0; i < DeckConstructionRuleParser.MAX_FIELDS; i++) {
            tooManyFields.add("Id$ value" + i);
        }
        tooManyFields.add("Mode$ ADD_FIXED");

        final DeckConstructionRuleParser.Result segment = DeckConstructionRuleParser.parse(SOURCE, List.of(
                oversizedSegment + "| Mode$ ADD_FIXED | Target$ Main | Card$ A | Amount$ 1"));
        final DeckConstructionRuleParser.Result fields = DeckConstructionRuleParser.parse(SOURCE,
                List.of(String.join(" | ", tooManyFields)));

        assertTrue(segment.getRules().isEmpty());
        assertTrue(fields.getRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                segment.getDiagnostics().get(0).getCode());
        assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                fields.getDiagnostics().get(0).getCode());
    }

    @Test
    void rawSegmentLimitCountsUtf8BytesRatherThanUtf16Characters() {
        final String exactSegment = utf8PaddedField("Bogus$ ",
                DeckConstructionRuleParser.MAX_FIELD_LENGTH);

        final DeckConstructionRuleParser.Result exact = DeckConstructionRuleParser.parse(SOURCE,
                List.of(exactSegment));
        final DeckConstructionRuleParser.Result oversized = DeckConstructionRuleParser.parse(SOURCE,
                List.of(exactSegment + "x"));

        assertEquals(DeckConstructionRuleParser.MAX_FIELD_LENGTH,
                exactSegment.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(DeckConstructionDiagnostic.Code.UNKNOWN_FIELD,
                exact.getDiagnostics().get(0).getCode());
        assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                oversized.getDiagnostics().get(0).getCode());
    }

    @Test
    void definitionLimitCountsUtf8BytesAtTheAstralBoundary() {
        final String first = utf8PaddedField("Bogus$ ",
                DeckConstructionRuleParser.MAX_FIELD_LENGTH);
        final String second = utf8PaddedField("Other$ ",
                DeckConstructionRuleParser.MAX_RULE_LENGTH - first.getBytes(StandardCharsets.UTF_8).length - 1);
        final String exactDefinition = first + '|' + second;
        final String oversizedDefinition = exactDefinition + 'x';

        final DeckConstructionRuleParser.Result exact = DeckConstructionRuleParser.parse(SOURCE,
                List.of(exactDefinition));
        final DeckConstructionRuleParser.Result oversized = DeckConstructionRuleParser.parse(SOURCE,
                List.of(oversizedDefinition));

        assertEquals(DeckConstructionRuleParser.MAX_RULE_LENGTH,
                exactDefinition.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(DeckConstructionDiagnostic.Code.UNKNOWN_FIELD,
                exact.getDiagnostics().get(0).getCode());
        assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                oversized.getDiagnostics().get(0).getCode());
    }

    @Test
    void readerAcceptsExactlyMaxTrimmedDefinitionCharactersAfterDeckRulePrefix() {
        final List<String> fields = new ArrayList<>(List.of(
                "Id$ boundary",
                " Mode$ ADD_FIXED",
                " Target$ Main",
                " Card$ Boundary Card",
                " Amount$ 1"
        ));
        int padding = DeckConstructionRuleParser.MAX_RULE_LENGTH
                - String.join("|", fields).length();
        for (int i = 0; i < fields.size() - 1; i++) {
            final int fieldPadding = padding / (fields.size() - 1 - i);
            fields.set(i, fields.get(i) + " ".repeat(fieldPadding));
            padding -= fieldPadding;
        }
        final String definition = String.join("|", fields);
        final int firstSeparator = definition.indexOf('|');
        final String oversizedDefinition = definition.substring(0, firstSeparator) + ' '
                + definition.substring(firstSeparator);

        final CardRules rules = CardRules.fromScript(List.of(
                "Name:Boundary Source",
                "ManaCost:0",
                "Types:Artifact",
                "DeckRule:" + definition
        ));
        final CardRules oversized = CardRules.fromScript(List.of(
                "Name:Oversized Boundary Source",
                "ManaCost:0",
                "Types:Artifact",
                "DeckRule:" + oversizedDefinition
        ));

        assertEquals(DeckConstructionRuleParser.MAX_RULE_LENGTH,
                definition.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(DeckConstructionRuleParser.MAX_RULE_LENGTH + 1,
                oversizedDefinition.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(rules.getDeckConstructionDiagnostics().isEmpty());
        assertEquals(1, rules.getDeckConstructionRules().size());
        assertTrue(oversized.getDeckConstructionRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                oversized.getDeckConstructionDiagnostics().get(0).getCode());
    }

    @Test
    void parsesEveryAllowConstraint() {
        final List<String> rules = new ArrayList<>();
        for (final DeckConstructionRule.Constraint constraint : DeckConstructionRule.Constraint.values()) {
            rules.add("Id$ " + constraint.name() + " | Mode$ ALLOW | Constraint$ "
                    + constraint.name().toLowerCase(java.util.Locale.ROOT) + " | Card$ Guest"
                    + (constraint == DeckConstructionRule.Constraint.SECTION ? " | Target$ Main" : ""));
        }

        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, rules);

        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(List.of(DeckConstructionRule.Constraint.values()),
                result.getRules().stream().map(DeckConstructionRule::getConstraint).toList());
    }

    @Test
    void sectionAllowRequiresTargetAndOtherAllowConstraintsRejectIt() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ section | Mode$ ALLOW | Constraint$ SECTION | Card$ Guest",
                "Id$ copy | Mode$ ALLOW | Constraint$ COPY_LIMIT | Card$ Guest | Target$ Main"
        ));

        assertTrue(result.getRules().isEmpty());
        assertEquals(List.of(
                        DeckConstructionDiagnostic.Code.MISSING_FIELD,
                        DeckConstructionDiagnostic.Code.FIELD_NOT_ALLOWED),
                result.getDiagnostics().stream().map(DeckConstructionDiagnostic::getCode).toList());
    }

    @Test
    void allowSectionTargetParticipatesInFingerprint() {
        final DeckConstructionRule main = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ main | Mode$ ALLOW | Constraint$ SECTION | Card$ Guest | Target$ Main"
        )).getRules().get(0);
        final DeckConstructionRule side = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ side | Mode$ ALLOW | Constraint$ SECTION | Card$ Guest | Target$ Sideboard"
        )).getRules().get(0);

        assertFalse(main.getContentFingerprint().equals(side.getContentFingerprint()));
    }

    @Test
    void strictEntryPointThrowsControlledExceptionWithDiagnostics() {
        final DeckConstructionRuleParser.StrictParsingException exception = assertThrows(
                DeckConstructionRuleParser.StrictParsingException.class,
                () -> DeckConstructionRuleParser.parseStrict(SOURCE, List.of(
                        fixed("valid", "A", 1),
                        "Id$ invalid | Mode$ UNKNOWN | Target$ Main | Card$ B | Amount$ 1"
                )));

        assertEquals(DeckConstructionDiagnostic.Code.UNKNOWN_MODE,
                exception.getDiagnostics().get(0).getCode());
        assertThrows(UnsupportedOperationException.class,
                () -> exception.getDiagnostics().add(exception.getDiagnostics().get(0)));
    }

    @Test
    void resultRuleAndCandidateCollectionsAreImmutable() {
        final DeckConstructionRuleParser.Result result = DeckConstructionRuleParser.parse(SOURCE, List.of(
                "Id$ choice | Mode$ CHOOSE_ONE | Target$ Main | Candidates$ A;B | Amount$ 1"
        ));

        assertThrows(UnsupportedOperationException.class, () -> result.getRules().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getDiagnostics().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.getRules().get(0).getCandidates().clear());
    }

    @Test
    void cardRulesReaderCarriesRulesAndDiagnosticsWithoutAffectingUnknownKeys() {
        final CardRules rules = CardRules.fromScript(List.of(
                "Name:Reader Source",
                "ManaCost:0",
                "Types:Artifact",
                "FutureUnrelatedKey:ignored",
                "DeckRule:" + fixed("fixed", "Generated", 2),
                "DeckRule:Id$ bad | Mode$ UNKNOWN | Target$ Main | Card$ A | Amount$ 1"
        ));

        assertEquals(1, rules.getDeckConstructionRules().size());
        assertTrue(rules.getDeckConstructionRules().get(0).getGlobalKey().startsWith("rk1."));
        assertEquals(1, rules.getDeckConstructionDiagnostics().size());
        assertEquals(DeckConstructionDiagnostic.Code.UNKNOWN_MODE,
                rules.getDeckConstructionDiagnostics().get(0).getCode());
        assertThrows(UnsupportedOperationException.class, rules.getDeckConstructionRules()::clear);
        assertThrows(UnsupportedOperationException.class, rules.getDeckConstructionDiagnostics()::clear);
    }

    @Test
    void readerResetPreventsRuleAndDiagnosticLeakage() {
        final CardRules.Reader reader = new CardRules.Reader();
        final CardRules first = reader.readCard(List.of(
                "Name:First",
                "ManaCost:0",
                "Types:Artifact",
                "DeckRule:" + fixed("first", "Generated", 1),
                "DeckRule:Id$ bad | Mode$ UNKNOWN | Target$ Main | Card$ A | Amount$ 1"
        ), "first");
        final CardRules second = reader.readCard(List.of(
                "Name:Second",
                "ManaCost:0",
                "Types:Artifact"
        ), "second");

        assertEquals(1, first.getDeckConstructionRules().size());
        assertEquals(1, first.getDeckConstructionDiagnostics().size());
        assertTrue(second.getDeckConstructionRules().isEmpty());
        assertTrue(second.getDeckConstructionDiagnostics().isEmpty());
    }

    @Test
    void readerBoundsRetainedDefinitionsAndOverflowFailsClosed() throws Exception {
        final CardRules.Reader reader = new CardRules.Reader();
        for (int i = 0; i < DeckConstructionRuleParser.MAX_RULES * 20; i++) {
            reader.parseLine("DeckRule:" + fixed("rule-" + i, "Generated", 1));
        }
        final Field definitionsField = CardRules.Reader.class
                .getDeclaredField("deckConstructionRuleDefinitions");
        definitionsField.setAccessible(true);
        final List<?> retained = (List<?>) definitionsField.get(reader);

        reader.parseLine("Name:Bounded Reader");
        reader.parseLine("ManaCost:0");
        reader.parseLine("Types:Artifact");
        final CardRules rules = reader.getCard();

        assertEquals(DeckConstructionRuleParser.MAX_RULES + 1, retained.size());
        assertTrue(rules.getDeckConstructionRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                rules.getDeckConstructionDiagnostics().get(0).getCode());

        reader.reset();
        assertTrue(((List<?>) definitionsField.get(reader)).isEmpty());
    }

    @Test
    void deckRuleBeforeNameUsesFinalSourceNameAtGetCardTime() {
        final CardRules rules = CardRules.fromScript(List.of(
                "DeckRule:" + fixed("early", "Generated", 1),
                "Name:Late Source",
                "ManaCost:0",
                "Types:Artifact"
        ));

        assertEquals(1, rules.getDeckConstructionRules().size());
        assertEquals("Late Source", rules.getDeckConstructionRules().get(0).getSourceCardName());
        assertTrue(rules.getDeckConstructionRules().get(0).getGlobalKey().startsWith("rk1."));
    }

    @Test
    void splitConstructionSourceCombinesFinalFrontAndBackNames() {
        final CardRules rules = CardRules.fromScript(List.of(
                "DeckRule:" + fixed("split", "Generated", 1),
                "Name:Front",
                "ManaCost:1 W",
                "Types:Instant",
                "Oracle:Front.",
                "AlternateMode:Split",
                "ALTERNATE",
                "Name:Back",
                "ManaCost:1 U",
                "Types:Sorcery",
                "Oracle:Back."
        ));

        assertEquals(CardSplitType.Split, rules.getSplitType());
        assertEquals("Front // Back", rules.getName());
        assertEquals("Front // Back", rules.getDeckConstructionRules().get(0).getSourceCardName());
    }

    @Test
    void transformAndModalConstructionSourcesUseOnlyTheFrontName() {
        for (final String mode : List.of("Transform", "Modal")) {
            final CardRules rules = CardRules.fromScript(List.of(
                    "Name:Front",
                    "ManaCost:1 W",
                    "Types:Creature",
                    "Oracle:Front.",
                    "AlternateMode:" + mode,
                    "ALTERNATE",
                    "Name:Back",
                    "ManaCost:1 U",
                    "Types:Creature",
                    "Oracle:Back.",
                    "DeckRule:" + fixed("front-" + mode, "Generated", 1)
            ));

            assertEquals("Front", rules.getName());
            assertEquals("Front", rules.getDeckConstructionRules().get(0).getSourceCardName());
        }
    }

    @Test
    void splitConstructionSourceSupportsTwoPlaceholderFaces() {
        final CardRules rules = CardRules.fromScript(List.of(
                "DeckRule:" + fixed("proxies", "Generated", 1),
                "CopyFaceFrom:ProxyA",
                "AlternateMode:Split",
                "ALTERNATE",
                "CopyFaceFrom:ProxyB"
        ));

        assertEquals(CardSplitType.Split, rules.getSplitType());
        assertEquals("ProxyA // ProxyB", rules.getPreInitName());
        assertEquals("ProxyA // ProxyB", rules.getDeckConstructionRules().get(0).getSourceCardName());
    }

    @Test
    void invalidPlaceholderLayoutCannotChangeIdentityWhenPlaceholdersAreSupplied() throws Exception {
        final CardRules rules = new CardRules.Reader().readCard(List.of(
                "CopyFaceFrom:ProxyA",
                "AlternateMode:Split",
                "DeckRule:" + fixed("missing-back", "Generated", 1)
        ), "broken_placeholder");
        final Method hasPlaceholders = CardRules.class.getDeclaredMethod("hasPlaceholderFaces");
        hasPlaceholders.setAccessible(true);
        final Method supply = CardRules.class.getDeclaredMethod("supplyPlaceholderFaces", Map.class);
        supply.setAccessible(true);
        final String nameBefore = rules.getName();
        final String preInitBefore = rules.getPreInitName();

        supply.invoke(rules, Collections.emptyMap());

        assertEquals(false, hasPlaceholders.invoke(rules));
        assertEquals(nameBefore, rules.getName());
        assertEquals(preInitBefore, rules.getPreInitName());
        assertEquals(nameBefore, preInitBefore);
        assertTrue(rules.isUnsupported());
        assertEquals("ProxyA", rules.getDeckConstructionDiagnostics().get(0).getSourceCardName());
    }

    @Test
    void actualBlankFaceDoesNotFallBackToAPlaceholderName() throws Exception {
        final CardRules.Reader reader = new CardRules.Reader();
        reader.parseLine("CopyFaceFrom:ProxyA");
        final Class<?> cardFaceClass = Class.forName("forge.card.CardFace");
        final java.lang.reflect.Constructor<?> constructor = cardFaceClass
                .getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        final Object actualFace = constructor.newInstance("Actual");
        final Field nameField = cardFaceClass.getDeclaredField("name");
        nameField.setAccessible(true);
        nameField.set(actualFace, " ");
        final Field facesField = CardRules.Reader.class.getDeclaredField("faces");
        facesField.setAccessible(true);
        java.lang.reflect.Array.set(facesField.get(reader), 0, actualFace);
        reader.parseLine("DeckRule:" + fixed("blank", "Generated", 1));

        final CardRules rules = reader.getCard();

        assertTrue(rules.getDeckConstructionRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.INVALID_SOURCE_LAYOUT,
                rules.getDeckConstructionDiagnostics().get(0).getCode());
        assertTrue(rules.getName().startsWith("Invalid construction source [anonymous#"));
        assertTrue(rules.isUnsupported());
    }

    @Test
    void incompleteSplitOnlyFallsBackWhenConstructionMetadataNeedsAName() {
        final List<String> base = List.of(
                "Name:Front",
                "ManaCost:1 W",
                "Types:Instant",
                "Oracle:Front.",
                "AlternateMode:Split"
        );
        final CardRules withoutRule = CardRules.fromScript(base);
        final List<String> withRuleScript = new ArrayList<>(base);
        withRuleScript.add("DeckRule:" + fixed("incomplete", "Generated", 1));
        final CardRules withRule = CardRules.fromScript(withRuleScript);

        assertEquals(CardSplitType.Split, withoutRule.getSplitType());
        assertEquals(CardSplitType.None, withRule.getSplitType());
        assertNotEquals("Front", withRule.getName());
        assertTrue(withRule.getName().startsWith("Invalid construction source [anonymous#"));
        assertTrue(withRule.isUnsupported());
        assertTrue(withRule.getDeckConstructionRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.INVALID_SOURCE_LAYOUT,
                withRule.getDeckConstructionDiagnostics().get(0).getCode());
        assertEquals("Front", withRule.getDeckConstructionDiagnostics().get(0).getSourceCardName());
        assertEquals(null, withRule.getDeckConstructionDiagnostics().get(0).getRuleId());
    }

    @Test
    void legalAndMalformedCardsWithTheSameFrontNameKeepDistinctIdentities() {
        final CardRules legal = CardRules.fromScript(List.of(
                "Name:Front",
                "ManaCost:0",
                "Types:Artifact",
                "Oracle:Legal."
        ));
        final CardRules malformed = CardRules.fromScript(List.of(
                "Name:Front",
                "ManaCost:0",
                "Types:Artifact",
                "Oracle:Malformed.",
                "AlternateMode:Split",
                "DeckRule:" + fixed("missing-back", "Generated", 1)
        ));
        final Map<String, CardRules> byName = new HashMap<>();
        byName.put(legal.getName(), legal);
        byName.put(malformed.getName(), malformed);

        assertEquals("Front", legal.getName());
        assertNotEquals(legal.getName(), malformed.getName());
        assertEquals(2, byName.size());
        assertTrue(malformed.isUnsupported());
    }

    @Test
    void unknownAlternateModeWithDeckRuleIsInactiveAndBounded() {
        final String unknownMode = "Unknown".repeat(2_048);
        final CardRules rules = CardRules.fromScript(List.of(
                "Name:Front",
                "ManaCost:0",
                "Types:Artifact",
                "Oracle:Front.",
                "Variant:Alt:Oracle:Variant.",
                "AlternateMode:" + unknownMode,
                "ALTERNATE",
                "Name:Back",
                "ManaCost:0",
                "Types:Artifact",
                "Oracle:Back.",
                "DeckRule:" + fixed("unknown-mode", "Generated", 1)
        ));

        assertEquals(CardSplitType.None, rules.getSplitType());
        assertNotEquals("Front", rules.getName());
        assertTrue(rules.getName().startsWith("Invalid construction source [anonymous#"));
        assertTrue(rules.isUnsupported());
        assertEquals(null, rules.getOtherPart());
        assertEquals(1, rules.getAllFaces().size());
        assertTrue(rules.getSpecializeParts().isEmpty());
        assertFalse(rules.hasFunctionalVariants());
        assertTrue(rules.getDeckConstructionRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.INVALID_SOURCE_LAYOUT,
                rules.getDeckConstructionDiagnostics().get(0).getCode());
        assertEquals("Front", rules.getDeckConstructionDiagnostics().get(0).getSourceCardName());
        assertTrue(rules.getDeckConstructionDiagnostics().get(0).getMessage().length() < 200);
    }

    @Test
    void unknownAlternateModeWithoutDeckRuleKeepsAnExplicitLoadFailure() {
        assertThrows(IllegalArgumentException.class, () -> CardRules.fromScript(List.of(
                "Name:Front",
                "ManaCost:0",
                "Types:Artifact",
                "Oracle:Front.",
                "AlternateMode:Unknown"
        )));
    }

    @Test
    void deckRuleOnlyMalformedLayoutReturnsSafeInactiveCardRules() {
        final CardRules rules = CardRules.fromScript(List.of(
                "DeckRule:" + fixed("missing", "Generated", 1)
        ));

        assertTrue(rules.getName().startsWith("Invalid construction source [anonymous#"));
        assertEquals(CardSplitType.None, rules.getSplitType());
        assertTrue(rules.isUnsupported());
        assertTrue(rules.getDeckConstructionRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.INVALID_SOURCE_LAYOUT,
                rules.getDeckConstructionDiagnostics().get(0).getCode());
        assertEquals(null, rules.getDeckConstructionDiagnostics().get(0).getRuleId());
    }

    @Test
    void malformedCardsLoadedFromDifferentFilesReceiveDistinctSafeNames() {
        final List<String> script = List.of("DeckRule:" + fixed("missing", "Generated", 1));
        final CardRules.Reader reader = new CardRules.Reader();
        final CardRules first = reader.readCard(script, "broken_source_one");
        final CardRules repeated = reader.getCard();
        final CardRules reread = new CardRules.Reader().readCard(script, "broken_source_one");
        final CardRules second = new CardRules.Reader().readCard(script, "broken_source_two");

        assertEquals(first.getName(), repeated.getName());
        assertEquals(first.getName(), reread.getName());
        assertNotEquals(first.getName(), second.getName());
        assertTrue(first.getName().startsWith("Invalid construction source [broken_source_one#"));
        assertTrue(second.getName().startsWith("Invalid construction source [broken_source_two#"));
        assertTrue(first.getName().matches(".*#[0-9a-f]{64}\\]"));
    }

    @Test
    void malformedCardsWithoutFilenamesUseStableLayoutAndRuleDigests() {
        final CardRules first = CardRules.fromScript(List.of(
                "DeckRule:" + fixed("first", "Generated", 1)
        ));
        final CardRules repeated = CardRules.fromScript(List.of(
                "DeckRule:" + fixed("first", "Generated", 1)
        ));
        final CardRules different = CardRules.fromScript(List.of(
                "DeckRule:" + fixed("second", "Generated", 1)
        ));
        final String sharedModePrefix = "X".repeat(128);
        final CardRules modeA = CardRules.fromScript(List.of(
                "Name:Front",
                "ManaCost:0",
                "Types:Artifact",
                "AlternateMode:" + sharedModePrefix + 'A',
                "DeckRule:" + fixed("mode", "Generated", 1)
        ));
        final CardRules modeB = CardRules.fromScript(List.of(
                "Name:Front",
                "ManaCost:0",
                "Types:Artifact",
                "AlternateMode:" + sharedModePrefix + 'B',
                "DeckRule:" + fixed("mode", "Generated", 1)
        ));

        assertEquals(first.getName(), repeated.getName());
        assertNotEquals(first.getName(), different.getName());
        assertNotEquals(modeA.getName(), modeB.getName());
        assertTrue(first.getName().startsWith("Invalid construction source [anonymous#"));
    }

    @Test
    void repeatedGetCardDoesNotShareMutableConstructionCollections() {
        final CardRules.Reader reader = new CardRules.Reader();
        reader.reset();
        reader.parseLine("Name:Repeated");
        reader.parseLine("ManaCost:0");
        reader.parseLine("Types:Artifact");
        reader.parseLine("DeckRule:" + fixed("same", "Generated", 1));

        final CardRules first = reader.getCard();
        final CardRules second = reader.getCard();

        assertNotSame(first.getDeckConstructionRules(), second.getDeckConstructionRules());
        assertEquals(first.getDeckConstructionRules(), second.getDeckConstructionRules());
    }

    @Test
    void reinitializeCopiesRulesAndDiagnosticsWithoutAliasing() throws Exception {
        final CardRules original = CardRules.fromScript(List.of(
                "Name:Reloadable",
                "ManaCost:0",
                "Types:Artifact"
        ));
        final CardRules replacement = CardRules.fromScript(List.of(
                "Name:Reloadable",
                "ManaCost:1",
                "Types:Artifact",
                "DeckRule:" + fixed("new", "Generated", 1),
                "DeckRule:Id$ bad | Mode$ UNKNOWN | Target$ Main | Card$ A | Amount$ 1"
        ));
        final Method reinitialize = CardRules.class.getDeclaredMethod("reinitializeFromRules", CardRules.class);
        reinitialize.setAccessible(true);

        reinitialize.invoke(original, replacement);

        assertEquals(replacement.getDeckConstructionRules(), original.getDeckConstructionRules());
        assertEquals(replacement.getDeckConstructionDiagnostics(), original.getDeckConstructionDiagnostics());
        assertNotSame(replacement.getDeckConstructionRules(), original.getDeckConstructionRules());
        assertNotSame(replacement.getDeckConstructionDiagnostics(), original.getDeckConstructionDiagnostics());
    }

    @Test
    void nullInputsAreInactiveAndNeverThrowInProductionParser() {
        final DeckConstructionRuleParser.Result nullSource = DeckConstructionRuleParser.parse(null,
                Collections.singletonList(fixed("fixed", "A", 1)));
        final DeckConstructionRuleParser.Result nullRules = DeckConstructionRuleParser.parse(SOURCE, null);

        assertTrue(nullSource.getRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.MISSING_SOURCE_CARD,
                nullSource.getDiagnostics().get(0).getCode());
        assertTrue(nullRules.getRules().isEmpty());
        assertEquals(DeckConstructionDiagnostic.Code.EMPTY_RULE_SET,
                nullRules.getDiagnostics().get(0).getCode());
    }

    @Test
    void cardRuleEqualityIsValueBased() {
        final DeckConstructionRule first = DeckConstructionRuleParser.parse(SOURCE,
                List.of(fixed("fixed", "A", 1))).getRules().get(0);
        final DeckConstructionRule second = DeckConstructionRuleParser.parse(SOURCE,
                List.of(fixed("fixed", "A", 1))).getRules().get(0);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertFalse(first.equals(null));
    }

    private static String fixed(final String id, final String cardName, final int amount) {
        return "Id$ " + id + " | Mode$ ADD_FIXED | Target$ Main | Card$ " + cardName + " | Amount$ " + amount;
    }

    private static String utf8PaddedField(final String prefix, final int totalBytes) {
        final int remaining = totalBytes - prefix.getBytes(StandardCharsets.UTF_8).length;
        final int emojiCount = remaining / 4;
        return prefix + "😀".repeat(emojiCount) + "x".repeat(remaining - emojiCount * 4);
    }
}
