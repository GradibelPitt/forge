package forge.deck.construction;

import forge.deck.DeckSection;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Parses non-fatal {@code DeckRule:} cardscript metadata. */
public final class DeckConstructionRuleParser {
    public static final int MAX_RULES = 100;
    public static final int MAX_RULE_LENGTH = 16_384;
    public static final int MAX_FIELD_LENGTH = 8_192;
    public static final int MAX_FIELDS = 16;
    public static final int MAX_RULE_ID_LENGTH = 1_024;
    public static final int MAX_CARD_NAME_LENGTH = 4_096;
    public static final int MAX_AMOUNT = 1_000;
    public static final int MAX_CANDIDATES = 1_000;

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "ID", "MODE", "TARGET", "CARD", "AMOUNT", "CANDIDATES", "CONSTRAINT", "CARDINALITY");

    private DeckConstructionRuleParser() {
    }

    /**
     * Production entry point. User-authored rule failures are returned as
     * inactive diagnostics and never escape as parsing exceptions.
     */
    public static Result parse(final String sourceCardName, final List<String> rawRules) {
        if (rawRules == null) {
            return Result.withDiagnostic(diagnostic(DeckConstructionDiagnostic.Code.EMPTY_RULE_SET,
                    sourceCardName, null, -1, null, "DeckRule collection is null"));
        }
        if (rawRules.isEmpty()) {
            return Result.empty();
        }
        if (rawRules.size() > MAX_RULES) {
            return Result.withDiagnostic(diagnostic(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                    sourceCardName, null, -1, null,
                    "A card may define at most " + MAX_RULES + " DeckRule entries"));
        }

        if (containsControlCharacter(sourceCardName)) {
            return Result.withDiagnostic(diagnostic(
                    DeckConstructionDiagnostic.Code.INVALID_CONTROL_CHARACTER,
                    sourceCardName, null, -1, null,
                    "DeckRule source card name contains a forbidden control character"));
        }
        final String source = normalizeDisplay(sourceCardName);
        if (source == null) {
            return Result.withDiagnostic(diagnostic(DeckConstructionDiagnostic.Code.MISSING_SOURCE_CARD,
                    sourceCardName, null, -1, null, "DeckRule source card name is empty"));
        }
        if (exceedsCardNameLimit(source)) {
            return Result.withDiagnostic(diagnostic(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                    source, null, -1, null,
                    "DeckRule source card name exceeds " + MAX_CARD_NAME_LENGTH + " UTF-8 bytes"));
        }

        try {
            return parseDefinitions(source, rawRules);
        } catch (final RuntimeException exception) {
            return Result.withDiagnostic(diagnostic(DeckConstructionDiagnostic.Code.INTERNAL_ERROR,
                    source, null, -1, null,
                    "DeckRule parser rejected unexpected input: " + exception.getClass().getSimpleName()));
        }
    }

    /**
     * Lint/test entry point. It uses the same parser as production, then turns
     * any inactive diagnostic into one controlled exception.
     */
    public static List<DeckConstructionRule> parseStrict(final String sourceCardName,
            final List<String> rawRules) {
        final Result result = parse(sourceCardName, rawRules);
        if (!result.getDiagnostics().isEmpty()) {
            throw new StrictParsingException(result.getDiagnostics());
        }
        return result.getRules();
    }

    private static Result parseDefinitions(final String source, final List<String> rawRules) {
        final List<Definition> definitions = new ArrayList<>(rawRules.size());
        final Map<String, Integer> idCounts = new HashMap<>();
        boolean resourceLimited = false;

        for (int index = 0; index < rawRules.size(); index++) {
            final Definition definition = parseFields(source, rawRules.get(index), index);
            definitions.add(definition);
            resourceLimited |= definition.resourceLimited;
            for (final String scannedId : definition.scannedIds) {
                idCounts.merge(scannedId, 1, Integer::sum);
            }
        }

        final List<DeckConstructionRule> rules = new ArrayList<>();
        final List<DeckConstructionDiagnostic> diagnostics = new ArrayList<>();
        for (final Definition definition : definitions) {
            if (definition.diagnostic != null) {
                diagnostics.add(definition.diagnostic);
                diagnostics.addAll(definition.associatedDiagnostics);
                continue;
            }
            final String id = normalizeDisplay(definition.fields.get("ID"));
            if (id != null && idCounts.getOrDefault(id, 0) > 1) {
                diagnostics.add(definition.diagnostic(DeckConstructionDiagnostic.Code.DUPLICATE_ID, id,
                        "DeckRule Id must be unique within its source card"));
                continue;
            }
            final RuleOrDiagnostic parsed = parseRule(source, definition, id);
            if (parsed.rule == null) {
                diagnostics.add(parsed.diagnostic);
                resourceLimited |= parsed.diagnostic.getCode()
                        == DeckConstructionDiagnostic.Code.RESOURCE_LIMIT;
            } else {
                rules.add(parsed.rule);
            }
        }
        return new Result(resourceLimited ? Collections.emptyList() : rules, diagnostics);
    }

    private static Definition parseFields(final String source, final String rawRule, final int index) {
        if (rawRule == null || rawRule.strip().isEmpty()) {
            return Definition.invalid(source, rawRule, index, DeckConstructionDiagnostic.Code.EMPTY_RULE,
                    "DeckRule value is empty", Collections.emptyList(), false);
        }
        if (exceedsUtf8Limit(rawRule, MAX_RULE_LENGTH)) {
            return Definition.invalid(source, rawRule, index, DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                    "DeckRule exceeds " + MAX_RULE_LENGTH + " UTF-8 bytes",
                    Collections.emptyList(), true);
        }

        final String[] pieces = rawRule.split("\\|", -1);
        if (pieces.length > MAX_FIELDS) {
            return Definition.invalid(source, rawRule, index, DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                    "DeckRule has too many fields", Collections.emptyList(), true);
        }
        for (final String piece : pieces) {
            if (exceedsUtf8Limit(piece, MAX_FIELD_LENGTH)) {
                return Definition.invalid(source, rawRule, index,
                        DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                        "DeckRule field exceeds " + MAX_FIELD_LENGTH + " UTF-8 bytes",
                        Collections.emptyList(), true);
            }
            final int delimiter = piece.indexOf('$');
            if (delimiter > 0) {
                final String value = piece.substring(delimiter + 1).strip();
                if (exceedsUtf8Limit(value, MAX_FIELD_LENGTH)) {
                    return Definition.invalid(source, rawRule, index,
                            DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                            "DeckRule field value exceeds " + MAX_FIELD_LENGTH + " UTF-8 bytes",
                            Collections.emptyList(), true);
                }
            }
        }

        final List<String> scannedIds = scanIds(pieces);
        for (final String piece : pieces) {
            final int delimiter = piece.indexOf('$');
            if (delimiter > 0 && "ID".equals(piece.substring(0, delimiter).strip()
                    .toUpperCase(Locale.ROOT))) {
                final String rawId = piece.substring(delimiter + 1);
                if (containsControlCharacter(rawId)) {
                    return Definition.invalid(source, rawRule, index,
                            DeckConstructionDiagnostic.Code.INVALID_CONTROL_CHARACTER,
                            "DeckRule Id contains a forbidden control character",
                            scannedIds, false);
                }
                final String id = normalizeDisplay(rawId);
                if (id != null && exceedsUtf8Limit(id, MAX_RULE_ID_LENGTH)) {
                    return Definition.invalid(source, rawRule, index,
                            DeckConstructionDiagnostic.Code.RESOURCE_LIMIT,
                            "DeckRule Id exceeds " + MAX_RULE_ID_LENGTH + " UTF-8 bytes",
                            Collections.emptyList(), true);
                }
            }
        }

        final Map<String, String> fields = new LinkedHashMap<>();
        for (final String piece : pieces) {
            final int delimiter = piece.indexOf('$');
            if (delimiter <= 0) {
                return Definition.invalid(source, rawRule, index,
                        DeckConstructionDiagnostic.Code.MALFORMED_FIELD,
                        "Every DeckRule field must use Key$ Value syntax", scannedIds, false);
            }
            final String scriptKey = piece.substring(0, delimiter).strip();
            final String key = scriptKey.toUpperCase(Locale.ROOT);
            final String value = piece.substring(delimiter + 1).strip();
            if (scriptKey.isEmpty()) {
                return Definition.invalid(source, rawRule, index,
                        DeckConstructionDiagnostic.Code.MALFORMED_FIELD,
                        "DeckRule field key is empty", scannedIds, false);
            }
            if (!KNOWN_FIELDS.contains(key)) {
                return Definition.invalid(source, rawRule, index,
                        DeckConstructionDiagnostic.Code.UNKNOWN_FIELD,
                        "Unknown DeckRule field: " + scriptKey, scannedIds, false);
            }
            if (fields.putIfAbsent(key, value) != null) {
                return Definition.invalid(source, rawRule, index,
                        DeckConstructionDiagnostic.Code.DUPLICATE_FIELD,
                        "DeckRule field is repeated: " + key, scannedIds, false);
            }
        }
        return Definition.valid(source, rawRule, index, fields, scannedIds);
    }

    private static List<String> scanIds(final String[] pieces) {
        final List<String> ids = new ArrayList<>();
        for (final String piece : pieces) {
            final int delimiter = piece.indexOf('$');
            if (delimiter <= 0 || !"ID".equals(piece.substring(0, delimiter).strip()
                    .toUpperCase(Locale.ROOT))) {
                continue;
            }
            final String id = normalizeDisplay(piece.substring(delimiter + 1));
            if (id != null) {
                ids.add(id);
            }
        }
        return Collections.unmodifiableList(ids);
    }

    private static RuleOrDiagnostic parseRule(final String source, final Definition definition, final String id) {
        if (id == null) {
            return definition.failure(DeckConstructionDiagnostic.Code.MISSING_FIELD, null,
                    "DeckRule requires a non-empty Id field");
        }
        if (containsControlCharacter(id)) {
            return definition.failure(DeckConstructionDiagnostic.Code.INVALID_CONTROL_CHARACTER, id,
                    "DeckRule Id contains a forbidden control character");
        }
        if (exceedsUtf8Limit(id, MAX_RULE_ID_LENGTH)) {
            return definition.failure(DeckConstructionDiagnostic.Code.RESOURCE_LIMIT, id,
                    "DeckRule Id exceeds " + MAX_RULE_ID_LENGTH + " UTF-8 bytes");
        }
        final String rawMode = normalizeDisplay(definition.fields.get("MODE"));
        if (rawMode == null) {
            return definition.failure(DeckConstructionDiagnostic.Code.MISSING_FIELD, id,
                    "DeckRule requires a Mode field");
        }

        final DeckConstructionRule.Mode mode;
        try {
            mode = DeckConstructionRule.Mode.valueOf(rawMode.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return definition.failure(DeckConstructionDiagnostic.Code.UNKNOWN_MODE, id,
                    "Unknown DeckRule Mode: " + rawMode);
        }

        final String rawCardinality = normalizeDisplay(definition.fields.get("CARDINALITY"));
        final DeckConstructionRule.Cardinality cardinality;
        if (rawCardinality == null && !definition.fields.containsKey("CARDINALITY")) {
            cardinality = DeckConstructionRule.Cardinality.ONCE_PER_DECK;
        } else if (DeckConstructionRule.Cardinality.ONCE_PER_DECK.name().equalsIgnoreCase(rawCardinality)) {
            cardinality = DeckConstructionRule.Cardinality.ONCE_PER_DECK;
        } else {
            return definition.failure(DeckConstructionDiagnostic.Code.UNSUPPORTED_CARDINALITY, id,
                    "Only Cardinality$ ONCE_PER_DECK is supported by schema version 1");
        }

        switch (mode) {
        case ADD_FIXED:
            return parseAddFixed(source, definition, id, cardinality);
        case CHOOSE_ONE:
            return parseChooseOne(source, definition, id, cardinality);
        case ALLOW:
            return parseAllow(source, definition, id, cardinality);
        default:
            return definition.failure(DeckConstructionDiagnostic.Code.UNKNOWN_MODE, id,
                    "Unsupported DeckRule Mode: " + mode);
        }
    }

    private static RuleOrDiagnostic parseAddFixed(final String source, final Definition definition,
            final String id, final DeckConstructionRule.Cardinality cardinality) {
        final RuleOrDiagnostic disallowed = rejectPresent(definition, id, "CANDIDATES", "CONSTRAINT");
        if (disallowed != null) {
            return disallowed;
        }
        final ValueOrDiagnostic<DeckSection> target = parseRequiredTarget(definition, id);
        if (target.value == null) {
            return RuleOrDiagnostic.failure(target.diagnostic);
        }
        final ValueOrDiagnostic<String> card = parseRequiredName(definition, id, "CARD");
        if (card.value == null) {
            return RuleOrDiagnostic.failure(card.diagnostic);
        }
        final ValueOrDiagnostic<Integer> amount = parseRequiredAmount(definition, id);
        if (amount.value == null) {
            return RuleOrDiagnostic.failure(amount.diagnostic);
        }
        return RuleOrDiagnostic.success(new DeckConstructionRule(source, id,
                DeckConstructionRule.Mode.ADD_FIXED, target.value, card.value, amount.value,
                Collections.emptyList(), null, cardinality));
    }

    private static RuleOrDiagnostic parseChooseOne(final String source, final Definition definition,
            final String id, final DeckConstructionRule.Cardinality cardinality) {
        final RuleOrDiagnostic disallowed = rejectPresent(definition, id, "CARD", "CONSTRAINT");
        if (disallowed != null) {
            return disallowed;
        }
        final ValueOrDiagnostic<DeckSection> target = parseRequiredTarget(definition, id);
        if (target.value == null) {
            return RuleOrDiagnostic.failure(target.diagnostic);
        }
        final ValueOrDiagnostic<List<String>> candidates = parseCandidates(definition, id);
        if (candidates.value == null) {
            return RuleOrDiagnostic.failure(candidates.diagnostic);
        }
        final ValueOrDiagnostic<Integer> amount = parseRequiredAmount(definition, id);
        if (amount.value == null) {
            return RuleOrDiagnostic.failure(amount.diagnostic);
        }
        return RuleOrDiagnostic.success(new DeckConstructionRule(source, id,
                DeckConstructionRule.Mode.CHOOSE_ONE, target.value, null, amount.value,
                candidates.value, null, cardinality));
    }

    private static RuleOrDiagnostic parseAllow(final String source, final Definition definition,
            final String id, final DeckConstructionRule.Cardinality cardinality) {
        final RuleOrDiagnostic disallowed = rejectPresent(definition, id, "AMOUNT", "CANDIDATES");
        if (disallowed != null) {
            return disallowed;
        }
        final ValueOrDiagnostic<String> card = parseRequiredName(definition, id, "CARD");
        if (card.value == null) {
            return RuleOrDiagnostic.failure(card.diagnostic);
        }
        final String rawConstraint = normalizeDisplay(definition.fields.get("CONSTRAINT"));
        if (rawConstraint == null) {
            return definition.failure(DeckConstructionDiagnostic.Code.MISSING_FIELD, id,
                    "ALLOW requires a Constraint field");
        }

        final DeckConstructionRule.Constraint constraint;
        try {
            constraint = DeckConstructionRule.Constraint.valueOf(rawConstraint.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return definition.failure(DeckConstructionDiagnostic.Code.UNKNOWN_CONSTRAINT, id,
                    "Unknown ALLOW Constraint: " + rawConstraint);
        }

        DeckSection target = null;
        if (constraint == DeckConstructionRule.Constraint.SECTION) {
            final ValueOrDiagnostic<DeckSection> parsedTarget = parseRequiredTarget(definition, id);
            if (parsedTarget.value == null) {
                return RuleOrDiagnostic.failure(parsedTarget.diagnostic);
            }
            target = parsedTarget.value;
        } else if (definition.fields.containsKey("TARGET")) {
            return definition.failure(DeckConstructionDiagnostic.Code.FIELD_NOT_ALLOWED, id,
                    "Target is only valid for ALLOW with Constraint$ SECTION");
        }

        return RuleOrDiagnostic.success(new DeckConstructionRule(source, id,
                DeckConstructionRule.Mode.ALLOW, target, card.value, 0,
                Collections.emptyList(), constraint, cardinality));
    }

    private static RuleOrDiagnostic rejectPresent(final Definition definition, final String id,
            final String... fieldNames) {
        for (final String fieldName : fieldNames) {
            if (definition.fields.containsKey(fieldName)) {
                return definition.failure(DeckConstructionDiagnostic.Code.FIELD_NOT_ALLOWED, id,
                        fieldName + " is not valid for this DeckRule Mode");
            }
        }
        return null;
    }

    private static ValueOrDiagnostic<DeckSection> parseRequiredTarget(final Definition definition,
            final String id) {
        final String rawTarget = normalizeDisplay(definition.fields.get("TARGET"));
        if (rawTarget == null) {
            return ValueOrDiagnostic.failure(definition.diagnostic(
                    DeckConstructionDiagnostic.Code.MISSING_FIELD, id,
                    "DeckRule requires a Target field"));
        }
        final DeckSection target = DeckSection.smartValueOf(rawTarget);
        if (target == null) {
            return ValueOrDiagnostic.failure(definition.diagnostic(
                    DeckConstructionDiagnostic.Code.INVALID_TARGET, id,
                    "Unknown DeckRule Target: " + rawTarget));
        }
        return ValueOrDiagnostic.success(target);
    }

    private static ValueOrDiagnostic<String> parseRequiredName(final Definition definition,
            final String id, final String fieldName) {
        final String name = normalizeDisplay(definition.fields.get(fieldName));
        if (name == null) {
            return ValueOrDiagnostic.failure(definition.diagnostic(
                    DeckConstructionDiagnostic.Code.MISSING_FIELD, id,
                    "DeckRule requires a non-empty " + fieldName + " field"));
        }
        if (exceedsCardNameLimit(name)) {
            return ValueOrDiagnostic.failure(definition.diagnostic(
                    DeckConstructionDiagnostic.Code.RESOURCE_LIMIT, id,
                    fieldName + " exceeds " + MAX_CARD_NAME_LENGTH + " UTF-8 bytes"));
        }
        return ValueOrDiagnostic.success(name);
    }

    private static ValueOrDiagnostic<Integer> parseRequiredAmount(final Definition definition,
            final String id) {
        final String rawAmount = normalizeDisplay(definition.fields.get("AMOUNT"));
        if (rawAmount == null) {
            return ValueOrDiagnostic.failure(definition.diagnostic(
                    DeckConstructionDiagnostic.Code.MISSING_FIELD, id,
                    "DeckRule requires an Amount field"));
        }
        if (!isAsciiSignedInteger(rawAmount)) {
            return ValueOrDiagnostic.failure(definition.diagnostic(
                    DeckConstructionDiagnostic.Code.INVALID_AMOUNT, id,
                    "Amount must use an optional ASCII sign followed by ASCII digits"));
        }
        try {
            final int amount = Integer.parseInt(rawAmount);
            if (amount < 1 || amount > MAX_AMOUNT) {
                return ValueOrDiagnostic.failure(definition.diagnostic(
                        DeckConstructionDiagnostic.Code.INVALID_AMOUNT, id,
                        "Amount must be between 1 and " + MAX_AMOUNT));
            }
            return ValueOrDiagnostic.success(amount);
        } catch (final NumberFormatException exception) {
            return ValueOrDiagnostic.failure(definition.diagnostic(
                    DeckConstructionDiagnostic.Code.INVALID_AMOUNT, id,
                    "Amount is not a valid integer"));
        }
    }

    private static boolean isAsciiSignedInteger(final String value) {
        int index = 0;
        if (value.charAt(0) == '+' || value.charAt(0) == '-') {
            index = 1;
        }
        if (index == value.length()) {
            return false;
        }
        for (; index < value.length(); index++) {
            final char digit = value.charAt(index);
            if (digit < '0' || digit > '9') {
                return false;
            }
        }
        return true;
    }

    private static ValueOrDiagnostic<List<String>> parseCandidates(final Definition definition,
            final String id) {
        if (!definition.fields.containsKey("CANDIDATES")) {
            return ValueOrDiagnostic.failure(definition.diagnostic(
                    DeckConstructionDiagnostic.Code.MISSING_FIELD, id,
                    "CHOOSE_ONE requires a Candidates field"));
        }
        final String rawCandidates = definition.fields.get("CANDIDATES");
        final String[] values = rawCandidates.split(";", -1);
        final List<String> candidates = new ArrayList<>(values.length);
        final Set<String> membership = new LinkedHashSet<>();
        for (final String value : values) {
            final String candidate = normalizeDisplay(value);
            if (candidate == null) {
                return ValueOrDiagnostic.failure(definition.diagnostic(
                        DeckConstructionDiagnostic.Code.EMPTY_CANDIDATE, id,
                        "Candidates cannot contain empty entries"));
            }
            if (exceedsCardNameLimit(candidate)) {
                return ValueOrDiagnostic.failure(definition.diagnostic(
                        DeckConstructionDiagnostic.Code.RESOURCE_LIMIT, id,
                        "Candidate name exceeds " + MAX_CARD_NAME_LENGTH + " UTF-8 bytes"));
            }
            // NFC makes canonically equivalent Unicode spellings equal. ROOT
            // casing is deterministic and independent of the user's locale.
            final String comparisonKey = DeckConstructionRule.canonicalCardNameKey(candidate);
            if (membership.add(comparisonKey)) {
                candidates.add(candidate);
                if (candidates.size() > MAX_CANDIDATES) {
                    return ValueOrDiagnostic.failure(definition.diagnostic(
                            DeckConstructionDiagnostic.Code.TOO_MANY_CANDIDATES, id,
                            "Candidates may contain at most " + MAX_CANDIDATES
                                    + " canonically distinct names"));
                }
            }
        }
        return ValueOrDiagnostic.success(candidates);
    }

    private static String normalizeDisplay(final String value) {
        if (value == null) {
            return null;
        }
        final String stripped = value.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        return Normalizer.normalize(stripped, Normalizer.Form.NFC);
    }

    private static boolean exceedsCardNameLimit(final String value) {
        return exceedsUtf8Limit(value, MAX_CARD_NAME_LENGTH)
                || exceedsUtf8Limit(DeckConstructionRule.canonicalCardNameKey(value),
                        MAX_CARD_NAME_LENGTH);
    }

    static boolean exceedsUtf8Limit(final String value, final int maximumBytes) {
        return value.length() > maximumBytes
                || value.getBytes(StandardCharsets.UTF_8).length > maximumBytes;
    }

    static boolean containsControlCharacter(final String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character <= 0x001f || character == 0x007f) {
                return true;
            }
        }
        return false;
    }

    private static DeckConstructionDiagnostic diagnostic(final DeckConstructionDiagnostic.Code code,
            final String source, final String id, final int index, final String rawRule, final String message) {
        return new DeckConstructionDiagnostic(code, source, id, index, rawRule, message);
    }

    public static final class Result {
        private final List<DeckConstructionRule> rules;
        private final List<DeckConstructionDiagnostic> diagnostics;

        private Result(final List<DeckConstructionRule> rules,
                final List<DeckConstructionDiagnostic> diagnostics) {
            this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
        }

        private static Result empty() {
            return new Result(Collections.emptyList(), Collections.emptyList());
        }

        private static Result withDiagnostic(final DeckConstructionDiagnostic diagnostic) {
            return new Result(Collections.emptyList(), Collections.singletonList(diagnostic));
        }

        public List<DeckConstructionRule> getRules() {
            return rules;
        }

        public List<DeckConstructionDiagnostic> getDiagnostics() {
            return diagnostics;
        }
    }

    public static final class StrictParsingException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final List<DeckConstructionDiagnostic> diagnostics;

        private StrictParsingException(final List<DeckConstructionDiagnostic> diagnostics) {
            super("Inactive DeckRule: " + diagnostics.get(0));
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
        }

        public List<DeckConstructionDiagnostic> getDiagnostics() {
            return diagnostics;
        }
    }

    private static final class Definition {
        private final String source;
        private final String rawRule;
        private final int index;
        private final Map<String, String> fields;
        private final DeckConstructionDiagnostic diagnostic;
        private final List<DeckConstructionDiagnostic> associatedDiagnostics;
        private final List<String> scannedIds;
        private final boolean resourceLimited;

        private Definition(final String source, final String rawRule, final int index,
                final Map<String, String> fields, final DeckConstructionDiagnostic diagnostic,
                final List<DeckConstructionDiagnostic> associatedDiagnostics,
                final List<String> scannedIds, final boolean resourceLimited) {
            this.source = source;
            this.rawRule = rawRule;
            this.index = index;
            this.fields = fields;
            this.diagnostic = diagnostic;
            this.associatedDiagnostics = associatedDiagnostics;
            this.scannedIds = scannedIds;
            this.resourceLimited = resourceLimited;
        }

        private static Definition valid(final String source, final String rawRule, final int index,
                final Map<String, String> fields, final List<String> scannedIds) {
            return new Definition(source, rawRule, index, fields, null,
                    Collections.emptyList(), scannedIds, false);
        }

        private static Definition invalid(final String source, final String rawRule, final int index,
                final DeckConstructionDiagnostic.Code code, final String message,
                final List<String> scannedIds, final boolean resourceLimited) {
            final List<String> distinctIds = new ArrayList<>(new LinkedHashSet<>(scannedIds));
            final String mainId = distinctIds.size() == 1 ? distinctIds.get(0) : null;
            final List<DeckConstructionDiagnostic> associations = new ArrayList<>();
            if (distinctIds.size() > 1) {
                for (final String id : distinctIds) {
                    associations.add(DeckConstructionRuleParser.diagnostic(
                            DeckConstructionDiagnostic.Code.MALFORMED_FIELD,
                            source, id, index, rawRule,
                            "Invalid DeckRule definition references Id: " + id));
                }
            }
            return new Definition(source, rawRule, index, Collections.emptyMap(),
                    DeckConstructionRuleParser.diagnostic(code, source, mainId, index, rawRule, message),
                    Collections.unmodifiableList(associations),
                    Collections.unmodifiableList(new ArrayList<>(scannedIds)), resourceLimited);
        }

        private DeckConstructionDiagnostic diagnostic(final DeckConstructionDiagnostic.Code code,
                final String id, final String message) {
            return DeckConstructionRuleParser.diagnostic(code, source, id, index, rawRule, message);
        }

        private RuleOrDiagnostic failure(final DeckConstructionDiagnostic.Code code,
                final String id, final String message) {
            return RuleOrDiagnostic.failure(diagnostic(code, id, message));
        }
    }

    private static final class RuleOrDiagnostic {
        private final DeckConstructionRule rule;
        private final DeckConstructionDiagnostic diagnostic;

        private RuleOrDiagnostic(final DeckConstructionRule rule,
                final DeckConstructionDiagnostic diagnostic) {
            this.rule = rule;
            this.diagnostic = diagnostic;
        }

        private static RuleOrDiagnostic success(final DeckConstructionRule rule) {
            return new RuleOrDiagnostic(rule, null);
        }

        private static RuleOrDiagnostic failure(final DeckConstructionDiagnostic diagnostic) {
            return new RuleOrDiagnostic(null, diagnostic);
        }
    }

    private static final class ValueOrDiagnostic<T> {
        private final T value;
        private final DeckConstructionDiagnostic diagnostic;

        private ValueOrDiagnostic(final T value, final DeckConstructionDiagnostic diagnostic) {
            this.value = value;
            this.diagnostic = diagnostic;
        }

        private static <T> ValueOrDiagnostic<T> success(final T value) {
            return new ValueOrDiagnostic<>(value, null);
        }

        private static <T> ValueOrDiagnostic<T> failure(final DeckConstructionDiagnostic diagnostic) {
            return new ValueOrDiagnostic<>(null, diagnostic);
        }
    }
}
