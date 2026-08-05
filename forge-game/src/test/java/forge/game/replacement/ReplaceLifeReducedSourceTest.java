package forge.game.replacement;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.ability.effects.LifeLoseEffect;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReplaceLifeReducedSourceTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void chosenSourceFilterAppliesOnlyToThatSourcesLifeLossEffects() {
        final Fixture fixture = new Fixture();
        final Card chosen = fixture.card("Chosen Source", fixture.opponent, ZoneType.Battlefield);
        final Card other = fixture.card("Other Source", fixture.opponent, ZoneType.Battlefield);
        final Card shield = fixture.card("Source Shield", fixture.player, ZoneType.Battlefield,
                "R:Event$ LifeReduced | ActiveZones$ Battlefield | ValidPlayer$ Player | "
                        + "IsDamage$ False | ValidSource$ Card.ChosenCardStrict | "
                        + "Layer$ CantHappen | Description$ Prevent life loss from the chosen source.");
        shield.setChosenCards(List.of(chosen));

        fixture.resolveLifeLoss(other, 3);
        Assert.assertEquals(fixture.player.getLife(), 17,
                "an unchosen source must not use the shield");

        fixture.resolveLifeLoss(chosen, 4);
        Assert.assertEquals(fixture.player.getLife(), 17,
                "the chosen source's life-loss effect must be prevented");

        Assert.assertEquals(fixture.player.loseLife(2, false, false), 2,
                "source-free life loss must not masquerade as the chosen source");
        Assert.assertEquals(fixture.player.getLife(), 15);
    }

    private static final class Fixture {
        private final Game game;
        private final Player player;
        private final Player opponent;

        private Fixture() {
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(Collections.emptyList(), rules,
                    new Match(rules, Collections.emptyList(), "Life-reduction source test"));
            player = new Player("Player", game, 1);
            opponent = new Player("Opponent", game, 2);
            game.getPlayers().add(player);
            game.getPlayers().add(opponent);
            player.setTeam(1);
            opponent.setTeam(2);
            game.setAge(GameStage.Play);
        }

        private Card card(final String name, final Player controller, final ZoneType zone,
                final String... extraScriptLines) {
            final List<String> script = new ArrayList<>(List.of(
                    "Name:" + name,
                    "ManaCost:1",
                    "Types:Artifact",
                    "Oracle:Test card."
            ));
            script.addAll(List.of(extraScriptLines));
            final Card card = Card.fromPaperCard(new PaperCard(
                    CardRules.fromScript(script), "TST", CardRarity.Common), controller);
            card.setController(controller, game.getNextTimestamp());
            controller.getZone(zone).add(card);
            return card;
        }

        private void resolveLifeLoss(final Card source, final int amount) {
            final SpellAbility ability = AbilityFactory.getAbility(
                    "DB$ LoseLife | ValidTgts$ Player | LifeAmount$ " + amount, source);
            ability.setActivatingPlayer(source.getController());
            ability.getTargets().add(player);
            new LifeLoseEffect().resolve(ability);
        }
    }
}
