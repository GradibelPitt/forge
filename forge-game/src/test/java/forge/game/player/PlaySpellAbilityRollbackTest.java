package forge.game.player;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.cost.Cost;
import forge.game.cost.CostPayment;
import forge.game.spellability.AbilityActivated;
import forge.game.spellability.TargetRestrictions;
import forge.game.trigger.TriggerAlways;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

public class PlaySpellAbilityRollbackTest {
    @BeforeClass
    public void initializeLocalizer() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void failedFaceDownCastAlwaysRestoresVisibleCardState() {
        final Fixture fixture = fixture();
        fixture.source.setFaceDown(true);
        fixture.source.updateStateForView();
        fixture.game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;

        PlaySpellAbility.rollbackCardStateAfterFailedPlay(
                fixture.player, fixture.source, true, false);

        Assert.assertFalse(fixture.source.isFaceDown());
    }

    @Test
    public void failedTriggerAfterPrerequisitesUsesFullManualRollback() {
        final Fixture fixture = fixture();
        final Card target = cardInZone(2, fixture.game, fixture.player, ZoneType.Battlefield);
        final AbilityActivated ability = new AbilityActivated(fixture.source, Cost.Zero,
                new TargetRestrictions(Map.of("ValidTgts", "Any"))) {
            private static final long serialVersionUID = 1L;

            @Override
            public void resolve() {
            }
        };
        ability.setActivatingPlayer(fixture.player);
        ability.setTrigger(new TriggerAlways(Collections.emptyMap(), fixture.source, true));
        ability.getTargets().add(target);
        fixture.game.getStack().freezeStack(ability);
        final CostPayment payment = new CostPayment(Cost.Zero, ability);

        PlaySpellAbility.rollbackFailedAbility(
                ability, null, -1, payment, fixture.source, true);

        Assert.assertTrue(ability.getTargets().isEmpty());
        Assert.assertFalse(fixture.game.getStack().isFrozen());
    }

    @Test
    public void declinedTriggerBeforePrerequisitesOnlyRefundsPayment() {
        final Fixture fixture = fixture();
        final Card target = cardInZone(2, fixture.game, fixture.player, ZoneType.Battlefield);
        final AbilityActivated ability = triggeredAbility(fixture, target);
        fixture.game.getStack().freezeStack(ability);

        PlaySpellAbility.rollbackFailedAbility(
                ability, null, -1, new CostPayment(Cost.Zero, ability), fixture.source, false);

        Assert.assertTrue(ability.getTargets().contains(target),
                "a trigger declined before cost payment must not be over-rolled back");
        Assert.assertTrue(fixture.game.getStack().isFrozen(),
                "the caller owns stack unfreezing after a pre-requisite decline");
    }

    private static AbilityActivated triggeredAbility(final Fixture fixture, final Card target) {
        final AbilityActivated ability = new AbilityActivated(fixture.source, Cost.Zero,
                new TargetRestrictions(Map.of("ValidTgts", "Any"))) {
            private static final long serialVersionUID = 1L;

            @Override
            public void resolve() {
            }
        };
        ability.setActivatingPlayer(fixture.player);
        ability.setTrigger(new TriggerAlways(Collections.emptyMap(), fixture.source, true));
        ability.getTargets().add(target);
        return ability;
    }

    private static Fixture fixture() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Play rollback test"));
        final Player player = new Player("Player", game, 1);
        game.getPlayers().add(player);
        final Card source = cardInZone(1, game, player, ZoneType.Battlefield);
        return new Fixture(game, player, source);
    }

    private static Card cardInZone(final int id, final Game game, final Player owner, final ZoneType zone) {
        final String name = "Play Rollback Test Card " + id;
        final CardRules rules = new CardRules.Reader().readCard(java.util.List.of(
                "Name:" + name,
                "ManaCost:no cost",
                "Types:Creature Bear",
                "PT:2/2",
                "Oracle:"
        ), name);
        final Card card = CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common), owner, id, game);
        owner.getZone(zone).add(card);
        return card;
    }

    private record Fixture(Game game, Player player, Card source) {
    }
}
