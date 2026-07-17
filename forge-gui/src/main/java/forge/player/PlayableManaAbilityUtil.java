package forge.player;

import forge.game.GameActionUtil;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.util.collect.FCollectionView;

import java.util.ArrayList;
import java.util.List;

/**
 * Narrow, shared lookup for mana-payment UI paths.
 *
 * <p>Payment highlighting and card selection must not ask a card for every
 * possible spell/activated ability. That broad query expands alternative
 * spell costs and performs unrelated playability work. This helper starts at
 * the card's mana-ability collection, adds only the existing alternative-cost
 * variants of those abilities, and applies the same {@code canPlay(true)}
 * filter used by the former callers.</p>
 */
public final class PlayableManaAbilityUtil {
    private PlayableManaAbilityUtil() {
    }

    public static List<SpellAbility> getPlayableManaAbilities(final Card card, final Player player) {
        if (card == null || player == null) {
            return new ArrayList<>();
        }
        final FCollectionView<SpellAbility> manaAbilities = card.getManaAbilities();
        if (manaAbilities.isEmpty()) {
            return new ArrayList<>();
        }

        final List<SpellAbility> candidates = new ArrayList<>();
        for (final SpellAbility ability : manaAbilities) {
            candidates.add(ability);
            candidates.addAll(GameActionUtil.getAlternativeCosts(ability, player, false));
        }

        candidates.removeIf(ability -> {
            ability.setActivatingPlayer(player);
            return !ability.canPlay(true);
        });
        return candidates;
    }

    public static boolean hasPlayableManaAbility(final Card card, final Player player) {
        if (card == null || player == null) {
            return false;
        }
        final FCollectionView<SpellAbility> manaAbilities = card.getManaAbilities();
        for (final SpellAbility ability : manaAbilities) {
            if (isPlayable(ability, player)) {
                return true;
            }
            for (final SpellAbility alternative : GameActionUtil.getAlternativeCosts(ability, player, false)) {
                if (isPlayable(alternative, player)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPlayable(final SpellAbility ability, final Player player) {
        ability.setActivatingPlayer(player);
        return ability.canPlay(true);
    }
}
