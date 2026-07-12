package forge.game.keyword;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardDamageTable;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import java.util.Map;

public class Boarding extends KeywordWithAmount {
    public static boolean hasMetThreshold(final int damagedFriendlyCharacters, final int threshold) {
        return damagedFriendlyCharacters >= threshold;
    }

    public static void processDamageBatch(final Game game, final CardDamageTable damageMap) {
        for (final Map.Entry<GameEntity, Map<Card, Integer>> entry : damageMap.columnMap().entrySet()) {
            final int damage = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
            if (damage <= 0) {
                continue;
            }

            final GameEntity damaged = entry.getKey();
            if (damaged instanceof Player player) {
                player.recordFriendlyCharacterDamaged(damaged.getId());
            } else if (damaged instanceof Card card) {
                card.getController().recordFriendlyCharacterDamaged(damaged.getId());
            }
        }

        for (final Player player : game.getPlayers()) {
            resolveForPlayer(player);
        }
    }

    public static void resolveForPlayer(final Player player) {
        final int damagedFriendlyCharacters = player.getFriendlyCharactersDamagedThisTurnCount();
        final CardCollection candidates = new CardCollection(player.getCardsIn(ZoneType.Hand));
        candidates.addAll(player.getCardsIn(ZoneType.Library));

        for (final Card card : candidates) {
            if (card.hasKeyword(Keyword.BOARDING)
                    && hasMetThreshold(damagedFriendlyCharacters, card.getKeywordMagnitude(Keyword.BOARDING))) {
                player.getGame().getAction().moveToPlay(card, player, null, AbilityKey.newMap());
            }
        }
    }
}
