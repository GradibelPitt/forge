package forge.game.ability;

/**
 * Marks an effect failure that is known to occur before that effect mutates
 * game state. The ability resolver may skip only that effect and continue.
 */
public final class RecoverableEffectException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public RecoverableEffectException(final String message) {
        super(message);
    }
}
