package forge.game;

import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbilityNoCleanupDamage;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * After attackers are final, lets the attacking player reserve at most one
     * eligible defending creature for each attacker. Flying and Menace use the
     * mode's reverse hierarchy, and each blocker can be reserved only once.
     */
    public static void chooseForcedBlockers(final Combat combat) {
        final Player attackingPlayer = combat.getAttackingPlayer();
        if (!isActive(attackingPlayer.getGame()) || attackingPlayer.getController() == null) {
            return;
        }

        final Set<Card> reserved = new HashSet<>();
        for (final Card attacker : combat.getAttackers()) {
            final Player defender = combat.getDefenderPlayerByAttacker(attacker);
            if (defender == null) {
                continue;
            }

            final CardCollection candidates = new CardCollection();
            for (final Card blocker : defender.getCreaturesInPlay()) {
                if (!reserved.contains(blocker)
                        && CombatUtil.canHearthstoneForceBlock(attacker, blocker, combat)) {
                    candidates.add(blocker);
                }
            }
            if (candidates.isEmpty()) {
                continue;
            }

            final Card chosen = attackingPlayer.getController()
                    .chooseHearthstoneBlocker(attacker, candidates);
            if (chosen != null && candidates.contains(chosen)) {
                combat.setHearthstoneForcedBlocker(attacker, chosen);
                reserved.add(chosen);
            }
        }
    }

    /** Applies reserved forced blocks after the defending player declares blocks. */
    public static void applyForcedBlockers(final Combat combat, final Player defender) {
        if (!isActive(defender.getGame())) {
            return;
        }

        for (final Map.Entry<Card, Card> entry : combat.getHearthstoneForcedBlockers().entrySet()) {
            final Card attacker = entry.getKey();
            final Card blocker = entry.getValue();
            if (!combat.getAttackers().contains(attacker)
                    || !blocker.isInPlay()
                    || blocker.getController() != defender
                    || !CombatUtil.canHearthstoneForceBlock(attacker, blocker, null)) {
                continue;
            }

            final int resultingBlockerCount = combat.getBlockers(attacker).size()
                    + (combat.isBlocking(blocker, attacker) ? 0 : 1);
            if (!CombatUtil.canAttackerBeBlockedWithAmountIgnoringMenace(
                    attacker, resultingBlockerCount, combat)) {
                continue;
            }

            final List<Card> previousAttackers = new ArrayList<>(
                    combat.getAttackersBlockedBy(blocker));
            combat.undoBlockingAssignment(blocker);
            if (CombatUtil.canHearthstoneForceBlock(attacker, blocker, combat)) {
                combat.addBlocker(attacker, blocker);
                if (CombatUtil.validateBlocks(combat, defender,
                        combat.getHearthstoneForcedBlockers().keySet()) != null) {
                    combat.undoBlockingAssignment(blocker);
                    for (final Card previousAttacker : previousAttackers) {
                        if (combat.getAttackers().contains(previousAttacker)
                                && CombatUtil.canBlock(previousAttacker, blocker, combat)) {
                            combat.addBlocker(previousAttacker, blocker);
                        }
                    }
                }
            }
        }
    }
}
