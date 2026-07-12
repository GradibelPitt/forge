package forge.game.ability.effects;

import forge.StaticData;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardZoneTable;
import forge.game.player.Player;
import forge.game.player.PlayerCollection;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.MyRandom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class CardDiscoverEffect extends SpellAbilityEffect {
    @Override
    protected String getStackDescription(final SpellAbility sa) {
        final PlayerCollection players = getDefinedPlayersOrTargeted(sa);
        return players + " discover a card.";
    }

    @Override
    public void resolve(final SpellAbility sa) {
        final Card host = sa.getHostCard();
        final String source = sa.getParamOrDefault("Source", "CardDatabase");
        final String valid = sa.getParamOrDefault("ValidCards", "Card");
        final int optionCount = AbilityUtils.calculateAmount(host,
                sa.getParamOrDefault("OptionCount", "3"), sa);
        final ZoneType destination = ZoneType.smartValueOf(sa.getParamOrDefault("Destination", "Hand"));

        if (optionCount <= 0 || destination == null) {
            return;
        }

        for (final Player player : getDefinedPlayersOrTargeted(sa)) {
            if (player == null || !player.isInGame()) {
                continue;
            }

            final CardCollection candidates = buildCandidates(sa, player, source);
            final CardCollection validCandidates = CardLists.getValidCards(candidates, valid,
                    player, host, sa);
            final List<Card> options = selectUniqueOptions(validCandidates, optionCount, MyRandom.getRandom());
            if (options.isEmpty()) {
                continue;
            }

            final ZoneType origin = source.equalsIgnoreCase("Library") ? ZoneType.Library : ZoneType.None;
            final Card chosen = player.getController().chooseSingleCardForZoneChange(destination,
                    Collections.singletonList(origin), sa, new CardCollection(options), null,
                    "Choose a card to discover", false, player);
            if (chosen == null) {
                continue;
            }

            final Game game = player.getGame();
            final Map<AbilityKey, Object> moveParams = AbilityKey.newMap();
            moveParams.put(AbilityKey.LastStateBattlefield, sa.getLastStateBattlefield());
            moveParams.put(AbilityKey.LastStateGraveyard, sa.getLastStateGraveyard());
            final Card moved = game.getAction().moveTo(player.getZone(destination), chosen, sa, moveParams);
            if (moved != null && moved.getZone() != null) {
                final CardZoneTable table = new CardZoneTable();
                table.put(origin, moved.getZone().getZoneType(), moved);
                table.triggerChangesZoneAll(game, sa);
            }
        }
    }

    private static CardCollection buildCandidates(final SpellAbility sa, final Player discoveringPlayer,
            final String source) {
        final CardCollection candidates = new CardCollection();
        if (source.equalsIgnoreCase("CardDatabase")) {
            for (final PaperCard paperCard : StaticData.instance().getCommonCards().getUniqueCards()) {
                candidates.add(Card.fromPaperCard(paperCard, discoveringPlayer));
            }
            return candidates;
        }
        if (source.equalsIgnoreCase("Library")) {
            final List<Player> owners = AbilityUtils.getDefinedPlayers(sa.getHostCard(),
                    sa.getParamOrDefault("SourceController", "You"), sa);
            for (final Player owner : owners) {
                if (owner != null && owner.isInGame()) {
                    candidates.addAll(owner.getCardsIn(ZoneType.Library));
                }
            }
        }
        return candidates;
    }

    static List<Card> selectUniqueOptions(final Iterable<Card> candidates, final int limit,
            final Random random) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        final Map<String, Card> byName = new LinkedHashMap<>();
        for (final Card card : candidates) {
            if (card != null && card.getName() != null) {
                byName.putIfAbsent(card.getName().toLowerCase(Locale.ROOT), card);
            }
        }
        final List<Card> options = new ArrayList<>(byName.values());
        Collections.shuffle(options, random);
        if (options.size() > limit) {
            return new ArrayList<>(options.subList(0, limit));
        }
        return options;
    }
}
