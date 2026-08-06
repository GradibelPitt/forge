package forge.game.ability.effects;

import forge.CardStorageReader;
import forge.StaticData;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.card.CounterType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class DemonfireCustomTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
        if (StaticData.instance() == null) {
            final String cards = Paths.get("..", "forge-gui", "res", "cardsfolder")
                    .toAbsolutePath().normalize().toString();
            final String editions = Paths.get("..", "forge-gui", "res", "editions")
                    .toAbsolutePath().normalize().toString();
            final String customEditions = Paths.get("..", "custom", "editions")
                    .toAbsolutePath().normalize().toString();
            final String blockData = Paths.get("..", "forge-gui", "res", "blockdata")
                    .toAbsolutePath().normalize().toString();
            new StaticData(new CardStorageReader(cards, null, true), null, editions,
                    customEditions, blockData, "Latest", true, true);
        }
    }

    @Test
    public void realScriptCountersOnlyAControlledDemonAndDamagesAnOpponentsDemon() throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Demonfire custom test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(controller);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "black", "demonfire_custom.txt")
                .toAbsolutePath().normalize();
        final CardRules demonfireRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "demonfire_custom");

        final Card controlledDemon = creature(game, controller, "Controlled Demon");
        Assert.assertTrue(controlledDemon.isCreature());
        Assert.assertTrue(controlledDemon.getType().hasSubtype("Demon"));
        Assert.assertSame(controlledDemon.getController(), controller);
        resolveDemonfire(game, controller, demonfireRules, controlledDemon, true);
        Assert.assertEquals(controlledDemon.getCounters(CounterType.getType("P1P1")), 2,
                "a Demon controlled by the caster should get two +1/+1 counters");
        Assert.assertEquals(controlledDemon.getDamage(), 0,
                "the controlled Demon branch should replace the damage branch");

        final Card opposingDemon = creature(game, opponent, "Opposing Demon");
        resolveDemonfire(game, controller, demonfireRules, opposingDemon, false);
        Assert.assertEquals(opposingDemon.getCounters(CounterType.getType("P1P1")), 0,
                "an opponent's Demon must not qualify for the counter branch");
        Assert.assertEquals(opposingDemon.getDamage(), 2,
                "an opponent's Demon should take 2 damage");
    }

    private static void resolveDemonfire(final Game game, final Player controller,
                                         final CardRules rules, final Card target,
                                         final boolean controlledDemon) {
        final Card demonfire = CardFactory.getCard(
                new PaperCard(rules, "PH01", CardRarity.Common), controller, game);
        demonfire.setController(controller, game.getNextTimestamp());
        final SpellAbility spell = demonfire.getSpellAbilities().getFirst();
        spell.setActivatingPlayer(controller);
        spell.getTargets().add(target);
        Assert.assertEquals(
                target.isValid("Creature.Demon+YouCtrl", controller, demonfire, spell),
                controlledDemon);
        Assert.assertEquals(AbilityUtils.calculateAmount(
                demonfire, "IsControlledDemon", spell), controlledDemon ? 1 : 0);
        AbilityUtils.resolve(spell);
    }

    private static Card creature(final Game game, final Player owner, final String name) {
        final Card card = new Card(game.nextCardId(), game);
        card.setName(name);
        card.setOwner(owner);
        card.setController(owner, game.getNextTimestamp());
        card.addType("Creature");
        card.addType("Demon");
        card.setBasePower(3);
        card.setBaseToughness(3);
        return game.getAction().moveTo(ZoneType.Battlefield, card, null, null);
    }
}
