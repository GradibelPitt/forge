package forge.game.trigger;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.card.CardFactory;
import forge.game.player.Player;
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

public class TriggerSpellAbilityCastOrCopySharedTypeTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void permanentTriggerRejectsAnotherPermanentWithAnySharedCardType() {
        final Fixture fixture = fixture("shared permanent type trigger test");
        final Card host = card(fixture, "Host", "Creature Human");
        final Trigger trigger = trigger(host, "Permanent", "Permanent");

        final Card earlierArtifact = card(fixture, "Earlier artifact", "Artifact");
        final Card currentArtifactCreature = card(
                fixture, "Current artifact creature", "Artifact Creature Golem");
        recordCast(fixture, earlierArtifact);
        recordCast(fixture, currentArtifactCreature);

        Assert.assertFalse(trigger.performTest(
                spellCastParams(currentArtifactCreature, fixture.controller)),
                "sharing even one permanent type with an earlier permanent must fail");
    }

    @Test
    public void permanentTriggerAcceptsATypeNotUsedByOtherPermanentsThisTurn() {
        final Fixture fixture = fixture("distinct permanent type trigger test");
        final Card host = card(fixture, "Host", "Creature Human");
        final Trigger trigger = trigger(host, "Permanent", "Permanent");

        final Card earlierEnchantment = card(fixture, "Earlier enchantment", "Enchantment");
        final Card currentArtifactCreature = card(
                fixture, "Current artifact creature", "Artifact Creature Golem");
        recordCast(fixture, earlierEnchantment);
        recordCast(fixture, currentArtifactCreature);

        Assert.assertTrue(trigger.performTest(
                spellCastParams(currentArtifactCreature, fixture.controller)));
    }

    @Test
    public void instantOrSorceryTriggerCanCompareAgainstEveryEarlierSpell() {
        final Fixture fixture = fixture("shared type across all spells trigger test");
        final Card host = card(fixture, "Host", "Creature Human");
        final Trigger trigger = trigger(host, "Instant,Sorcery", "Card");

        final Card earlierArtifact = card(fixture, "Earlier artifact", "Artifact");
        final Card currentArtifactInstant = card(
                fixture, "Current artifact instant", "Artifact Instant");
        recordCast(fixture, earlierArtifact);
        recordCast(fixture, currentArtifactInstant);

        Assert.assertFalse(trigger.performTest(
                spellCastParams(currentArtifactInstant, fixture.controller)),
                "the comparison pool is all other spells, not only instants and sorceries");
    }

    private static Trigger trigger(final Card host, final String validCard,
            final String comparisonValid) {
        host.setSVar("TrigDraw", "DB$ Draw | Defined$ You | NumCards$ 1");
        return TriggerHandler.parseTrigger(
                "Mode$ SpellCast | ValidCard$ " + validCard
                        + " | ValidActivatingPlayer$ You"
                        + " | ActivatorThisTurnCastSharedCardType$ EQ0"
                        + " | ActivatorThisTurnCastSharedCardTypeValid$ " + comparisonValid
                        + " | Execute$ TrigDraw",
                host, true);
    }

    private static void recordCast(final Fixture fixture, final Card card) {
        final SpellAbility cast = card.getFirstSpellAbility();
        cast.setActivatingPlayer(fixture.controller);
        fixture.game.getStack().getSpellsCastThisTurn().add(cast);
    }

    private static Map<AbilityKey, Object> spellCastParams(final Card card,
            final Player player) {
        final SpellAbility cast = card.getFirstSpellAbility();
        cast.setActivatingPlayer(player);
        final Map<AbilityKey, Object> params = AbilityKey.mapFromCard(card);
        params.put(AbilityKey.SpellAbility, cast);
        params.put(AbilityKey.Activator, player);
        params.put(AbilityKey.CardLKI, CardCopyService.getLKICopy(card));
        return params;
    }

    private static Card card(final Fixture fixture, final String name,
            final String types) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:0",
                "Types:" + types,
                "A:SP$ Cleanup | SpellDescription$ Test spell.",
                "Oracle:Test card."));
        return CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common),
                fixture.controller, fixture.game);
    }

    private static Fixture fixture(final String title) {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), title));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(controller);
        game.setAge(GameStage.Play);
        return new Fixture(game, controller, opponent);
    }

    private record Fixture(Game game, Player controller, Player opponent) {
    }
}
