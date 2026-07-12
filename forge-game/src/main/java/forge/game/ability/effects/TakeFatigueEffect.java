package forge.game.ability.effects;

import forge.game.ability.SpellAbilityEffect;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.util.Lang;

/** Applies one increasing fatigue instance to each affected player. */
public class TakeFatigueEffect extends SpellAbilityEffect {
    @Override
    protected String getStackDescription(final SpellAbility sa) {
        return Lang.joinHomogenous(getTargetPlayers(sa)) + " take fatigue.";
    }

    @Override
    public void resolve(final SpellAbility sa) {
        for (final Player player : getTargetPlayers(sa)) {
            if (player.isInGame()) {
                player.takeFatigue();
            }
        }
    }
}
