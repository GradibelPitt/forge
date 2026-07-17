package forge.game.keyword;

/**
 * The DIY Harmony keyword is intentionally distinct from Magic's Harmonize
 * alternative-cost keyword. Its rules behavior is supplied by the owning
 * player's game-scoped spell-rule registry; this instance provides the
 * visible card keyword and reminder text only.
 */
public final class HarmonyKeyword extends SimpleKeyword {
    @Override
    public String getTitle() {
        return "调和";
    }
}
