package forge.game.ability;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementResult;
import forge.game.spellability.SpellAbility;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class AbilityUtilsReplacementUnlessTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void paidUnlessCostLeavesReplacementEventUnreplaced() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(),
                        "replacement unless test"));
        final Player controller = new Player("Controller", game, 1);
        game.getPlayers().add(controller);

        final CardRules cardRules = CardRules.fromScript(Arrays.asList(
                "Name:Replacement Unless Test",
                "ManaCost:0",
                "Types:Artifact",
                "R:Event$ DamageDone | ActiveZones$ Battlefield"
                        + " | ValidTarget$ You | ReplaceWith$ PreventUnlessPaid"
                        + " | PreventionEffect$ True",
                "SVar:PreventUnlessPaid:DB$ ReplaceDamage | Amount$ ShieldAmount"
                        + " | UnlessCost$ 1 | UnlessPayer$ ReplacedSourceController",
                "SVar:ShieldAmount:ReplaceCount$DamageAmount",
                "Oracle:Test card."));
        final Card card = CardFactory.getCard(
                new PaperCard(cardRules, "TST", CardRarity.Common),
                controller, game);
        final ReplacementEffect replacement = card.getReplacementEffects().get(0);
        final SpellAbility replacementAbility = replacement.getOverridingAbility();
        replacementAbility.setReplacementEffect(replacement);

        final Map<AbilityKey, Object> originalParams = AbilityKey.newMap();
        replacementAbility.setReplacingObject(AbilityKey.OriginalParams,
                originalParams);

        AbilityUtils.markSkippedReplacementEffectAsNotReplaced(
                replacementAbility);

        Assert.assertEquals(originalParams.get(AbilityKey.ReplacementResult),
                ReplacementResult.NotReplaced);
    }
}
