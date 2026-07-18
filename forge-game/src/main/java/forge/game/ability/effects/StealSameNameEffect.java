package forge.game.ability.effects;

import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Takes one card matching the spell that caused the enclosing trigger, using
 * the fixed battlefield, hand, library, graveyard priority required by the
 * custom card that owns this API.
 */
public class StealSameNameEffect extends SpellAbilityEffect {
    private static final List<ZoneType> ZONE_PRIORITY = List.of(
            ZoneType.Battlefield,
            ZoneType.Hand,
            ZoneType.Library,
            ZoneType.Graveyard);

    record Selection(ZoneType zone, List<Card> cards) {
        Selection {
            cards = List.copyOf(cards);
        }
    }

    static Selection findFirstMatchingZone(
            final Map<ZoneType, ? extends Iterable<Card>> cardsByZone,
            final String name) {
        if (cardsByZone == null || name == null) {
            return null;
        }
        for (final ZoneType zone : ZONE_PRIORITY) {
            final Iterable<Card> cards = cardsByZone.get(zone);
            if (cards == null) {
                continue;
            }
            final CardCollection matches = new CardCollection();
            for (final Card card : cards) {
                if (card != null && Objects.equals(name, card.getName())) {
                    matches.add(card);
                }
            }
            if (!matches.isEmpty()) {
                return new Selection(zone, matches);
            }
        }
        return null;
    }

    @Override
    protected String getStackDescription(final SpellAbility sa) {
        return "Take a card or permanent with the same name as the triggering spell.";
    }

    @Override
    public void resolve(final SpellAbility sa) {
        if (sa == null || sa.getActivatingPlayer() == null) {
            return;
        }
        final Object triggeringObject = sa.getTriggeringObject(AbilityKey.Card);
        if (!(triggeringObject instanceof Card triggeringCard)) {
            return;
        }

        final Player activator = sa.getActivatingPlayer();
        final List<Player> targets = getTargetPlayers(sa);
        if (targets.isEmpty()) {
            return;
        }
        final Player opponent = targets.get(0);
        final Map<ZoneType, Iterable<Card>> cardsByZone =
                new EnumMap<>(ZoneType.class);
        for (final ZoneType zone : ZONE_PRIORITY) {
            cardsByZone.put(zone, opponent.getCardsIn(zone));
        }

        final Selection selection = findFirstMatchingZone(
                cardsByZone, triggeringCard.getName());
        if (selection == null) {
            return;
        }

        final Card chosen;
        if (selection.cards().size() == 1) {
            chosen = selection.cards().get(0);
        } else {
            final CardCollection choices = new CardCollection(selection.cards());
            final CardCollection selected = new CardCollection(
                    activator.getController().chooseCardsForEffect(
                            choices, sa,
                            "Choose a card named " + triggeringCard.getName(),
                            1, 1, false, null));
            if (selected.isEmpty()) {
                return;
            }
            chosen = selected.get(0);
        }

        final Game game = activator.getGame();
        if (selection.zone() == ZoneType.Battlefield) {
            if (!chosen.isInPlay() || !chosen.canBeControlledBy(activator)) {
                return;
            }
            chosen.addTempController(activator, game.getNextTimestamp());
            game.getAction().controllerChangeZoneCorrection(chosen);
            return;
        }

        final Map<AbilityKey, Object> moveParams = AbilityKey.newMap();
        moveParams.put(AbilityKey.LastStateBattlefield,
                sa.getLastStateBattlefield());
        moveParams.put(AbilityKey.LastStateGraveyard,
                sa.getLastStateGraveyard());
        final Card moved = game.getAction().moveTo(
                activator.getZone(ZoneType.Hand), chosen, sa, moveParams);
        if (moved != null && moved.getZone() == activator.getZone(ZoneType.Hand)) {
            moved.setOwner(activator);
            moved.setController(activator, game.getNextTimestamp());
        }
    }
}
