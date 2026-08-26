package forge.game;

import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityNoCleanupDamage;
import forge.game.zone.ZoneType;

/** Engine-owned rules for the Hearthstone game variant. */
public final class HearthstoneMode {
    public static final int STARTING_LIFE = 30;
    public static final int MAXIMUM_HAND_SIZE = 10;
    private static final String BASIC_LAND_SPELLBOOK =
            "Plains,Island,Swamp,Mountain,Forest";

    private HearthstoneMode() {
    }

    public static boolean isActive(final Game game) {
        return game != null && game.getRules().hasAppliedVariant(GameType.Hearthstone);
    }

    /**
     * Builds the upkeep choice from a transient rules source. The source is never
     * placed in a zone, so the mode does not expose an artifact, emblem, or other
     * game object.
     */
    public static SpellAbility createUpkeepResourceAbility(final Player player) {
        final Game game = player.getGame();
        final Card source = new Card(game.nextCardId(), game);
        source.setOwner(player);
        source.setController(player, game.getNextTimestamp());
        source.setName(GameType.Hearthstone.toString());

        final SpellAbility ability = AbilityFactory.getAbility(
                "DB$ MakeCard | Defined$ You | Conjure$ True | Spellbook$ "
                        + BASIC_LAND_SPELLBOOK + " | Zone$ Hand",
                source);
        ability.setActivatingPlayer(player);
        return ability;
    }

    public static void grantUpkeepResource(final Player player) {
        if (isActive(player.getGame())) {
            AbilityUtils.resolve(createUpkeepResourceAbility(player));
        }
    }

    /** Clears ordinary marked damage, while Hearthstone damage remains marked. */
    public static void cleanupMarkedDamage(final Game game) {
        for (final Card card : game.getCardsIncludePhasingIn(ZoneType.Battlefield)) {
            if (!isActive(game) && !StaticAbilityNoCleanupDamage.damageNotRemoved(card)) {
                card.setDamage(0);
            }
            card.setHasBeenDealtDeathtouchDamage(false);
        }
    }

}
