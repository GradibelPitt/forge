package forge.game.card;

import forge.game.keyword.Keyword;
import forge.item.PaperCard;

/** Shared invariants for cards that exist only to establish game rules. */
public final class GameRuleCard {
    private GameRuleCard() {
    }

    public static boolean isGameRule(final Card card) {
        return card != null && card.hasKeyword(Keyword.GAME_RULE);
    }

    public static boolean isGameRule(final PaperCard paperCard) {
        return paperCard != null
                && Keyword.getKeywordSet(paperCard).contains(Keyword.GAME_RULE);
    }

    public static boolean canMaterialize(final PaperCard paperCard) {
        return paperCard != null && !isGameRule(paperCard);
    }
}
