package forge.game.ability.effects;

import forge.card.CardRules;
import forge.card.CardType;
import forge.card.MagicColor;
import forge.game.CardTraitBase;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.item.PaperCard;
import forge.util.Expressions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Conservative lightweight prefilter for database-backed card discovery.
 *
 * <p>This class deliberately supports only clauses that can be proven from
 * immutable {@link CardRules}. Unknown clauses match the lightweight stage and
 * make the filter incomplete, leaving {@link Card#isValid} as the final
 * semantic authority after bounded materialization.</p>
 */
final class CardDiscoverCandidateFilter {
    enum Capability {
        STATIC_EXACT,
        STATIC_PREFILTER_DYNAMIC_FINAL,
        DYNAMIC_ONLY
    }

    private static final class Clause {
        private final Predicate<PaperCard> predicate;
        private final boolean complete;
        private final boolean constrained;
        private final boolean contextIndependent;

        private Clause(final Predicate<PaperCard> predicate, final boolean complete,
                final boolean constrained, final boolean contextIndependent) {
            this.predicate = predicate;
            this.complete = complete;
            this.constrained = constrained;
            this.contextIndependent = contextIndependent;
        }
    }

    private final Predicate<PaperCard> predicate;
    private final Capability capability;
    private final boolean contextIndependent;

    private CardDiscoverCandidateFilter(final Predicate<PaperCard> predicate,
            final Capability capability, final boolean contextIndependent) {
        this.predicate = predicate;
        this.capability = capability;
        this.contextIndependent = contextIndependent;
    }

    static CardDiscoverCandidateFilter compile(final String restrictions,
            final Card source, final CardTraitBase spellAbility) {
        final List<Predicate<PaperCard>> alternatives = new ArrayList<>();
        boolean allComplete = true;
        boolean allConstrained = true;
        boolean allContextIndependent = true;
        for (final String restriction : restrictions.split(",")) {
            final Clause alternative = compileAlternative(restriction.trim(), source, spellAbility);
            alternatives.add(alternative.predicate);
            allComplete &= alternative.complete;
            allConstrained &= alternative.constrained;
            allContextIndependent &= alternative.contextIndependent;
        }
        final Capability capability = allComplete ? Capability.STATIC_EXACT
                : allConstrained ? Capability.STATIC_PREFILTER_DYNAMIC_FINAL
                : Capability.DYNAMIC_ONLY;
        return new CardDiscoverCandidateFilter(paperCard -> {
            for (final Predicate<PaperCard> alternative : alternatives) {
                if (alternative.test(paperCard)) {
                    return true;
                }
            }
            return false;
        }, capability, allContextIndependent);
    }

    boolean matches(final PaperCard paperCard) {
        return paperCard != null && predicate.test(paperCard);
    }

    boolean isComplete() {
        return capability == Capability.STATIC_EXACT;
    }

    Capability getCapability() {
        return capability;
    }

    boolean isContextIndependent() {
        return contextIndependent;
    }

    private static Clause compileAlternative(final String restriction,
            final Card source, final CardTraitBase spellAbility) {
        if (restriction.isEmpty() || restriction.startsWith("!")) {
            return unknownClause();
        }

        final String[] parts = restriction.split("\\.", 2);
        Clause result = compileBase(parts[0]);
        if (parts.length == 1) {
            return result;
        }

        for (final String property : parts[1].split("\\+")) {
            final Clause propertyClause = compileProperty(property, source, spellAbility);
            final Predicate<PaperCard> prior = result.predicate;
            result = new Clause(
                    paperCard -> prior.test(paperCard) && propertyClause.predicate.test(paperCard),
                    result.complete && propertyClause.complete,
                    result.constrained || propertyClause.constrained,
                    result.contextIndependent && propertyClause.contextIndependent);
        }
        return result;
    }

    private static Clause compileBase(final String base) {
        if (base.equals("Card") || base.equals("card")) {
            return new Clause(paperCard -> true, true, false, true);
        }
        if (base.equals("Spell")) {
            return exactClause(paperCard -> {
                final CardType type = paperCard.getRules().getType();
                return type.isInstant() || type.isSorcery() || type.isAura();
            });
        }
        if (base.equals("Permanent")) {
            return exactClause(paperCard -> paperCard.getRules().getType().isPermanent());
        }
        if (isKnownType(base)) {
            return exactClause(paperCard -> paperCard.getRules().getType().hasStringType(base));
        }
        return unknownClause();
    }

    private static Clause compileProperty(final String property, final Card source,
            final CardTraitBase spellAbility) {
        if (property.startsWith("cmc") && property.length() > 5
                && isComparison(property.substring(3, 5))) {
            final String amountExpression = property.substring(5);
            final Integer rightSide = evaluateAmount(amountExpression, source, spellAbility);
            if (rightSide != null) {
                return new Clause(paperCard -> Expressions.compare(
                        paperCard.getRules().getManaCost().getCMC(), property, rightSide),
                        true, true, amountExpression.matches("-?\\d+"));
            }
            return unknownClause();
        }
        final String positiveColor = normalizeColor(property);
        if (positiveColor != null) {
            return exactClause(paperCard -> matchesColor(paperCard, positiveColor));
        }
        if (property.startsWith("non")) {
            final String excludedColor = normalizeColor(property.substring(3));
            if (excludedColor != null) {
                return exactClause(paperCard -> !matchesColor(paperCard, excludedColor));
            }
        }
        if (property.startsWith("non") && isKnownType(property.substring(3))) {
            final String excludedType = property.substring(3);
            return exactClause(paperCard -> !paperCard.getRules().getType().hasStringType(excludedType));
        }
        if (isKnownType(property)) {
            return exactClause(paperCard -> paperCard.getRules().getType().hasStringType(property));
        }
        return unknownClause();
    }

    private static Integer evaluateAmount(final String expression, final Card source,
            final CardTraitBase spellAbility) {
        if (expression.matches("-?\\d+")) {
            return Integer.parseInt(expression);
        }
        if (source == null || !source.hasSVar(expression)) {
            return null;
        }
        try {
            return AbilityUtils.calculateAmount(source, expression, spellAbility);
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    private static boolean isComparison(final String operator) {
        return operator.equals("LT") || operator.equals("LE") || operator.equals("EQ")
                || operator.equals("GE") || operator.equals("GT") || operator.equals("NE")
                || operator.equals("M2");
    }

    private static String normalizeColor(final String property) {
        for (final String color : MagicColor.Constant.COLORS_AND_COLORLESS) {
            if (color.equalsIgnoreCase(property)) {
                return color;
            }
        }
        return null;
    }

    private static boolean matchesColor(final PaperCard paperCard, final String color) {
        if (MagicColor.Constant.COLORLESS.equals(color)) {
            return paperCard.getRules().getColor().isColorless();
        }
        return paperCard.getRules().getColor().hasAnyColor(MagicColor.fromName(color));
    }

    private static boolean isKnownType(final String type) {
        return CardType.isACardType(type) || CardType.isASupertype(type)
                || CardType.isACreatureType(type) || CardType.isALandType(type)
                || CardType.isAnArtifactType(type) || CardType.isAnEnchantmentType(type)
                || CardType.isASpellType(type) || CardType.isAPlaneswalkerType(type)
                || CardType.isADungeonType(type) || CardType.isABattleType(type)
                || CardType.isAPlanarType(type);
    }

    private static Clause exactClause(final Predicate<PaperCard> predicate) {
        return new Clause(predicate, true, true, true);
    }

    private static Clause unknownClause() {
        return new Clause(paperCard -> true, false, false, false);
    }
}
