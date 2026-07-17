package forge.deck.construction;

import forge.deck.DeckSection;
import forge.item.PaperCard;

/** One exact-printing manual deck edit. Invalid values are reported by plan(), not thrown here. */
public final class DeckConstructionEdit {
    public enum Type {
        ADD,
        REMOVE,
        MOVE
    }

    private final Type type;
    private final PaperCard card;
    private final DeckSection fromSection;
    private final DeckSection toSection;
    private final int amount;

    private DeckConstructionEdit(final Type type, final PaperCard card,
            final DeckSection fromSection, final DeckSection toSection, final int amount) {
        this.type = type;
        this.card = card;
        this.fromSection = fromSection;
        this.toSection = toSection;
        this.amount = amount;
    }

    public static DeckConstructionEdit add(final PaperCard card, final DeckSection section, final int amount) {
        return new DeckConstructionEdit(Type.ADD, card, null, section, amount);
    }

    public static DeckConstructionEdit remove(final PaperCard card, final DeckSection section, final int amount) {
        return new DeckConstructionEdit(Type.REMOVE, card, section, null, amount);
    }

    public static DeckConstructionEdit move(final PaperCard card, final DeckSection fromSection,
            final DeckSection toSection, final int amount) {
        return new DeckConstructionEdit(Type.MOVE, card, fromSection, toSection, amount);
    }

    public Type getType() {
        return type;
    }

    public PaperCard getCard() {
        return card;
    }

    public DeckSection getFromSection() {
        return fromSection;
    }

    public DeckSection getToSection() {
        return toSection;
    }

    public int getAmount() {
        return amount;
    }
}
