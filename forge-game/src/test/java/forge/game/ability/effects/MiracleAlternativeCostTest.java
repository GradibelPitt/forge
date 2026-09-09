package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.cost.Cost;
import forge.game.cost.CostAdjustment;
import forge.game.cost.CostDiscard;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.AlternativeCost;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

public class MiracleAlternativeCostTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void miraclePlayUsesNativeKeywordProvenanceWithoutAScriptMarker() {
        final Fixture fixture = fixture();
        final SpellAbility play = miraclePlayAbility(fixture.card);

        Assert.assertEquals(play.getParam("PlayCost"), "B R R");
        Assert.assertFalse(play.hasParam("AlternativeCost"));
        Assert.assertTrue(play.isKeyword(Keyword.MIRACLE));
        Assert.assertTrue(play.copy(fixture.card, fixture.player, false).isKeyword(Keyword.MIRACLE));
    }

    @Test
    public void miracleMarkerCanExcludeOnlyMiracleFromAnAdditionalCost() {
        final Fixture fixture = fixture();
        final SpellAbility base = fixture.card.getFirstSpellAbility();
        base.setActivatingPlayer(fixture.player);

        final SpellAbility miracle = base.copyWithManaCostReplaced(
                fixture.player, new Cost("B R R", false));
        PlayEffect.applyKeywordAlternativeCost(miraclePlayAbility(fixture.card), miracle);

        Assert.assertFalse(base.isValid(
                "Spell.Miracle", fixture.player, fixture.card, base));
        Assert.assertTrue(base.isValid(
                "Spell.!Miracle", fixture.player, fixture.card, base));
        Assert.assertTrue(miracle.isValid(
                "Spell.Miracle", fixture.player, fixture.card, miracle));
        Assert.assertFalse(miracle.isValid(
                "Spell.!Miracle", fixture.player, fixture.card, miracle));

        final Cost normalCost = CostAdjustment.adjust(base.getPayCosts(), base, false);
        final Cost miracleCost = CostAdjustment.adjust(
                miracle.getPayCosts(), miracle, false);
        Assert.assertTrue(normalCost.getCostParts().stream()
                .anyMatch(CostDiscard.class::isInstance));
        Assert.assertFalse(miracleCost.getCostParts().stream()
                .anyMatch(CostDiscard.class::isInstance));
    }

    @Test
    public void sameCostOrScriptTextDoesNotMakeAnOrdinaryCastAMiracle() {
        final Fixture fixture = fixture();
        final SpellAbility ordinaryPlay = AbilityFactory.getAbility(
                "DB$ Play | Defined$ Self | PlayCost$ B R R | AlternativeCost$ Miracle",
                fixture.card);
        final SpellAbility spell = fixture.card.getFirstSpellAbility().copyWithManaCostReplaced(
                fixture.player, new Cost("B R R", false));

        PlayEffect.applyKeywordAlternativeCost(ordinaryPlay, spell);

        Assert.assertFalse(spell.isAlternativeCost(AlternativeCost.Miracle));
    }

    @Test
    public void freeCastDoesNotCountAsPayingMiracleCost() {
        final Fixture fixture = fixture();
        final SpellAbility play = miraclePlayAbility(fixture.card);
        play.putParam("WithoutManaCost", "True");
        final SpellAbility spell = fixture.card.getFirstSpellAbility().copyWithNoManaCost();

        PlayEffect.applyKeywordAlternativeCost(play, spell);

        Assert.assertFalse(spell.isAlternativeCost(AlternativeCost.Miracle));
    }

    @Test
    public void unrelatedPlayKeepsItsOwnAlternativeCost() {
        final Fixture fixture = fixture();
        final SpellAbility ordinaryPlay = AbilityFactory.getAbility(
                "DB$ Play | Defined$ Self | PlayCost$ B R R", fixture.card);
        final SpellAbility spell = fixture.card.getFirstSpellAbility().copyWithManaCostReplaced(
                fixture.player, new Cost("B R R", false));
        spell.setAlternativeCost(AlternativeCost.Flashback);

        PlayEffect.applyKeywordAlternativeCost(ordinaryPlay, spell);

        Assert.assertTrue(spell.isAlternativeCost(AlternativeCost.Flashback));
        Assert.assertFalse(spell.isAlternativeCost(AlternativeCost.Miracle));
    }

    private static SpellAbility miraclePlayAbility(final Card card) {
        final Trigger drawn = card.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.Drawn)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Miracle has no Drawn trigger"));
        final SpellAbility reveal = drawn.getOverridingAbility();
        final SpellAbility immediateTrigger = reveal.getSubAbility();
        final SpellAbility play = immediateTrigger.getAdditionalAbility("Execute");
        Assert.assertNotNull(play);
        return play;
    }

    private static Fixture fixture() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Miracle alternative cost test"));
        final Player player = new Player("Player", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);

        final CardRules cardRules = CardRules.fromScript(Arrays.asList(
                "Name:Miracle Test",
                "ManaCost:B R R",
                "Types:Sorcery",
                "K:Miracle:B R R",
                "S:Mode$ RaiseCost | ValidCard$ Card.Self | "
                        + "ValidSpell$ Spell.!Miracle | Type$ Spell | "
                        + "Cost$ Discard<1/Instant;Sorcery/instant or sorcery> | "
                        + "EffectZone$ All",
                "A:SP$ Draw | NumCards$ 1 | SpellDescription$ Draw a card.",
                "Oracle:Test miracle spell."));
        final Card card = CardFactory.getCard(
                new PaperCard(cardRules, "TST", CardRarity.Rare), player, game);
        card.setController(player, game.getNextTimestamp());
        player.getZone(ZoneType.Hand).add(card);
        return new Fixture(card, player);
    }

    private record Fixture(Card card, Player player) {
    }
}
