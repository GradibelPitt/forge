package forge.game.player;

import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.mana.ManaConversionMatrix;
import forge.game.spellability.SpellAbility;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An immutable, game-scoped rule that modifies spells cast by one player.
 *
 * <p>The rule deliberately has no source card. It therefore remains active
 * when the card that granted it changes zones and can be evaluated without
 * scanning battlefield or command-zone cards.</p>
 */
public final class PlayerSpellRule {
    public static final String HARMONY_MANA_CONVERSION = "AnyType->AnyColor";
    private static final Set<String> VALID_CARD_BASES = Set.of(
            "Card", "Spell", "Permanent", "Creature", "Artifact",
            "Enchantment", "Instant", "Sorcery", "Planeswalker", "Battle",
            "Land");
    private static final Set<String> VALID_CARD_PROPERTIES = Set.of(
            "White", "Blue", "Black", "Red", "Green", "Colorless",
            "nonWhite", "nonBlue", "nonBlack", "nonRed", "nonGreen",
            "nonColorless");
    private static final Set<String> VALID_SA_BASES = Set.of(
            "Spell", "Instant", "Sorcery");
    private static final Set<String> PERMANENT_CARD_BASES = Set.of(
            "Creature", "Artifact", "Enchantment", "Planeswalker",
            "Battle", "Land");
    private static final Pattern MANA_LETTERS = Pattern.compile("[WUBRGCwubrgc]+");
    private static final Set<String> MANA_NAMES = Set.of(
            "white", "blue", "black", "red", "green", "colorless");

    private final String key;
    private final String[] validCards;
    private final String[] validSpellAbilities;
    private final int genericReduction;
    private final String manaConversion;
    private final boolean harmony;
    private final int harmonyReduction;

    PlayerSpellRule(final String key, final String validCard,
            final String validSpellAbility, final int genericReduction,
            final String manaConversion) {
        this(key, validCard, validSpellAbility, genericReduction,
                manaConversion, false, 0);
    }

    PlayerSpellRule(final String key, final String validCard,
            final String validSpellAbility, final int genericReduction,
            final String manaConversion, final boolean harmony,
            final int harmonyReduction) {
        this.key = requireText(key, "key");
        validCards = splitRestrictions(validCard, "Card");
        validSpellAbilities = splitRestrictions(validSpellAbility, "Spell");
        validateCardRestrictions(validCards);
        validateSpellAbilityRestrictions(validSpellAbilities);
        if (genericReduction < 0) {
            throw new IllegalArgumentException("genericReduction must not be negative");
        }
        this.genericReduction = genericReduction;
        this.manaConversion = normalizeManaConversion(manaConversion);
        if (harmonyReduction < 0) {
            throw new IllegalArgumentException(
                    "harmonyReduction must not be negative");
        }
        if (!harmony && harmonyReduction != 0) {
            throw new IllegalArgumentException(
                    "harmonyReduction requires Harmony");
        }
        if (harmony && !this.manaConversion.isEmpty()) {
            throw new IllegalArgumentException(
                    "Harmony supplies its own mana conversion");
        }
        if (harmony && genericReduction != 0) {
            throw new IllegalArgumentException(
                    "Harmony uses HarmonyReduction, not ReduceGeneric");
        }
        this.harmony = harmony;
        this.harmonyReduction = harmonyReduction;
        if (genericReduction == 0 && this.manaConversion.isEmpty()
                && !harmony) {
            throw new IllegalArgumentException(
                    "A player spell rule must modify cost or mana payment");
        }
    }

    public String getKey() {
        return key;
    }

    public int getGenericReduction() {
        return genericReduction;
    }

    public String getValidCardRestriction() {
        return String.join(",", validCards);
    }

    public String getValidSpellAbilityRestriction() {
        return String.join(",", validSpellAbilities);
    }

    public String getManaConversion() {
        return manaConversion;
    }

    public boolean isHarmony() {
        return harmony;
    }

    public int getHarmonyReduction() {
        return harmonyReduction;
    }

    String[] getValidCardRestrictionsForCoverage() {
        return validCards.clone();
    }

    boolean coversPaymentScope(final PlayerSpellRule requested,
            final String requestedCardRestriction) {
        if (!coversManaConversion(requested)
                || !coversSpellAbilityRestrictions(requested)) {
            return false;
        }
        for (final String existingCardRestriction : validCards) {
            if (coversCardRestriction(existingCardRestriction,
                    requestedCardRestriction)) {
                return true;
            }
        }
        return false;
    }

    boolean matches(final Player player, final Card card, final SpellAbility sa) {
        return card != null && sa != null && sa.isSpell()
                && !sa.isCopied() && !card.isCopiedSpell()
                && sa.getActivatingPlayer() == player
                && card.isValid(validCards, player, null, sa)
                && sa.isValid(validSpellAbilities, player, null, sa);
    }

    boolean grantsHarmony(final Player player, final Card card) {
        if (!harmony || card == null || card.isCopiedSpell()
                || card.getOwner() != player
                || !card.isValid(validCards, player, null, null)) {
            return false;
        }
        for (final String restriction : validSpellAbilities) {
            if (("Spell".equals(restriction) && !card.isLand())
                    || ("Instant".equals(restriction) && card.isInstant())
                    || ("Sorcery".equals(restriction) && card.isSorcery())) {
                return true;
            }
        }
        return false;
    }

    boolean applyManaConversion(final ManaConversionMatrix matrix,
            final Player player, final Card card, final SpellAbility sa) {
        if (harmony || manaConversion.isEmpty()
                || !matches(player, card, sa)) {
            return false;
        }
        AbilityUtils.applyManaColorConversion(matrix, manaConversion);
        return true;
    }

    boolean applyOwnedHarmonyManaConversion(
            final ManaConversionMatrix matrix, final Player player,
            final Card card, final SpellAbility sa) {
        if (!matchesOwnedHarmony(player, card, sa)) {
            return false;
        }
        AbilityUtils.applyManaColorConversion(matrix,
                HARMONY_MANA_CONVERSION);
        return true;
    }

    boolean matchesOwnedHarmony(final Player player, final Card card,
            final SpellAbility sa) {
        return harmony && card != null && card.getOwner() == player
                && sa != null && sa.isSpell()
                && !sa.isCopied() && !card.isCopiedSpell()
                && card.isValid(validCards, player, null, sa)
                && sa.isValid(validSpellAbilities, player, null, sa);
    }

    private String getEffectiveManaConversion() {
        return harmony ? HARMONY_MANA_CONVERSION : manaConversion;
    }

    private static String requireText(final String value, final String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String[] splitRestrictions(final String value,
            final String defaultValue) {
        final String normalized = value == null || value.trim().isEmpty()
                ? defaultValue : value.trim();
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toArray(String[]::new);
    }

    private static void validateCardRestrictions(final String[] restrictions) {
        for (final String restriction : restrictions) {
            final String[] parts = restriction.split("\\.", 2);
            if (!VALID_CARD_BASES.contains(parts[0])) {
                unsupportedRestriction(restriction);
            }
            if (parts.length == 2) {
                for (final String property : parts[1].split("\\+")) {
                    if (!VALID_CARD_PROPERTIES.contains(property)) {
                        unsupportedRestriction(restriction);
                    }
                }
            }
            if (matchingColorMasks(restriction) == 0) {
                throw new IllegalArgumentException(
                        "Player spell rule has an impossible color restriction: "
                                + restriction);
            }
        }
    }

    private boolean coversManaConversion(final PlayerSpellRule requested) {
        final ManaConversionMatrix existingMatrix = conversionMatrix(
                getEffectiveManaConversion());
        final ManaConversionMatrix requestedMatrix = conversionMatrix(
                requested.getEffectiveManaConversion());
        for (final byte source : MagicColor.WUBRGC) {
            final int existingUses = existingMatrix.getPossibleColorUses(source)
                    & 0xFF;
            final int requestedUses = requestedMatrix.getPossibleColorUses(source)
                    & 0xFF;
            if ((existingUses & requestedUses) != requestedUses) {
                return false;
            }
        }
        return true;
    }

    private static ManaConversionMatrix conversionMatrix(
            final String conversion) {
        final ManaConversionMatrix matrix = new ManaConversionMatrix();
        matrix.restoreColorReplacements();
        if (!conversion.isEmpty()) {
            AbilityUtils.applyManaColorConversion(matrix, conversion);
        }
        return matrix;
    }

    private boolean coversSpellAbilityRestrictions(
            final PlayerSpellRule requested) {
        for (final String requestedRestriction
                : requested.validSpellAbilities) {
            boolean covered = false;
            for (final String existingRestriction : validSpellAbilities) {
                if (existingRestriction.equals(requestedRestriction)
                        || "Spell".equals(existingRestriction)) {
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

    private static boolean coversCardRestriction(
            final String existingRestriction,
            final String requestedRestriction) {
        final String existingBase = baseOf(existingRestriction);
        final String requestedBase = baseOf(requestedRestriction);
        if (!existingBase.equals(requestedBase)
                && !"Card".equals(existingBase)
                && !("Permanent".equals(existingBase)
                        && PERMANENT_CARD_BASES.contains(requestedBase))) {
            return false;
        }
        final long existingMasks = matchingColorMasks(existingRestriction);
        final long requestedMasks = matchingColorMasks(requestedRestriction);
        return requestedMasks != 0
                && (requestedMasks & ~existingMasks) == 0;
    }

    private static String baseOf(final String restriction) {
        final int separator = restriction.indexOf('.');
        return separator < 0 ? restriction : restriction.substring(0, separator);
    }

    private static long matchingColorMasks(final String restriction) {
        final int separator = restriction.indexOf('.');
        if (separator < 0) {
            return 0xFFFF_FFFFL;
        }
        final String[] properties = restriction.substring(separator + 1)
                .split("\\+");
        long result = 0;
        for (int colorMask = 0; colorMask <= MagicColor.ALL_COLORS;
                colorMask++) {
            boolean matches = true;
            for (final String property : properties) {
                if (!matchesColorProperty(colorMask, property)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                result |= 1L << colorMask;
            }
        }
        return result;
    }

    private static boolean matchesColorProperty(final int colorMask,
            final String property) {
        return switch (property) {
            case "White" -> (colorMask & MagicColor.WHITE) != 0;
            case "Blue" -> (colorMask & MagicColor.BLUE) != 0;
            case "Black" -> (colorMask & MagicColor.BLACK) != 0;
            case "Red" -> (colorMask & MagicColor.RED) != 0;
            case "Green" -> (colorMask & MagicColor.GREEN) != 0;
            case "Colorless" -> colorMask == 0;
            case "nonWhite" -> (colorMask & MagicColor.WHITE) == 0;
            case "nonBlue" -> (colorMask & MagicColor.BLUE) == 0;
            case "nonBlack" -> (colorMask & MagicColor.BLACK) == 0;
            case "nonRed" -> (colorMask & MagicColor.RED) == 0;
            case "nonGreen" -> (colorMask & MagicColor.GREEN) == 0;
            case "nonColorless" -> colorMask != 0;
            default -> false;
        };
    }

    private static void validateSpellAbilityRestrictions(
            final String[] restrictions) {
        for (final String restriction : restrictions) {
            if (!VALID_SA_BASES.contains(restriction)) {
                unsupportedRestriction(restriction);
            }
        }
    }

    private static void unsupportedRestriction(final String restriction) {
        throw new IllegalArgumentException("Player spell rules support only a "
                + "source-independent restriction subset: " + restriction);
    }

    private static String normalizeManaConversion(final String conversion) {
        if (conversion == null || conversion.trim().isEmpty()) {
            return "";
        }
        final String[] pairs = conversion.trim().split("\\s+");
        for (final String pair : pairs) {
            final boolean additive = pair.contains("->");
            if (additive == pair.contains("<-")
                    || !pair.matches("[^<>-]+(?:->|<-)[^<>-]+")) {
                throw new IllegalArgumentException("Invalid mana conversion: " + pair);
            }
            final String[] sides = pair.split(additive ? "->" : "<-", -1);
            if (sides.length != 2 || !isManaConversionAtom(sides[0])
                    || !isManaConversionAtom(sides[1])) {
                throw new IllegalArgumentException("Invalid mana conversion: " + pair);
            }
            if (manaConversionMask(sides[0]) == 0) {
                throw new IllegalArgumentException(
                        "Invalid mana conversion source selects no mana types: " + pair);
            }
            if (manaConversionMask(sides[1]) == 0) {
                throw new IllegalArgumentException(
                        "Invalid mana conversion destination selects no mana types: " + pair);
            }
        }
        return String.join(" ", pairs);
    }

    private static boolean isManaConversionAtom(final String value) {
        if ("AnyColor".equals(value) || "AnyType".equals(value)) {
            return true;
        }
        final String atom = value.startsWith("non") ? value.substring(3) : value;
        return !atom.isEmpty() && (MANA_LETTERS.matcher(atom).matches()
                || MANA_NAMES.contains(atom.toLowerCase(java.util.Locale.ROOT)));
    }

    private static byte manaConversionMask(final String value) {
        if ("AnyColor".equals(value)) {
            return ManaAtom.ALL_MANA_COLORS;
        }
        if ("AnyType".equals(value)) {
            return ManaAtom.ALL_MANA_TYPES;
        }
        final boolean inverse = value.startsWith("non");
        final String atom = inverse ? value.substring(3) : value;
        byte mask = 0;
        if (MANA_LETTERS.matcher(atom).matches()) {
            for (final char symbol : atom.toCharArray()) {
                mask |= ManaAtom.fromName(symbol);
            }
        } else {
            mask = ManaAtom.fromName(atom);
        }
        return inverse ? (byte) (mask ^ ManaAtom.ALL_MANA_TYPES) : mask;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayerSpellRule)) {
            return false;
        }
        final PlayerSpellRule rule = (PlayerSpellRule) other;
        return genericReduction == rule.genericReduction
                && harmony == rule.harmony
                && harmonyReduction == rule.harmonyReduction
                && key.equals(rule.key)
                && Arrays.equals(validCards, rule.validCards)
                && Arrays.equals(validSpellAbilities, rule.validSpellAbilities)
                && manaConversion.equals(rule.manaConversion);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(key, genericReduction, manaConversion,
                harmony, harmonyReduction);
        result = 31 * result + Arrays.hashCode(validCards);
        result = 31 * result + Arrays.hashCode(validSpellAbilities);
        return result;
    }
}
