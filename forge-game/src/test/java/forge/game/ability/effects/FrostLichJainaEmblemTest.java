package forge.game.ability.effects;

import forge.CardStorageReader;
import forge.StaticData;
import forge.card.CardRules;
import forge.card.CardRarity;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardFactory;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityContinuous;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
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
import java.util.Map;

public class FrostLichJainaEmblemTest {
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
    public void emblemDamagesAnOpponentsCreatureWhenItBecomesTappedAndGainsLife() throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Frost Lich Jaina emblem test"));
        final Player jainaController = new Player("Jaina controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(jainaController);
        game.getPlayers().add(opponent);
        jainaController.setTeam(1);
        opponent.setTeam(2);
        jainaController.setLife(20, null);
        opponent.setLife(20, null);
        game.getPhaseHandler().setPlayerTurn(jainaController);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "blue", "冰霜女巫吉安娜.txt")
                .toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "冰霜女巫吉安娜");
        final Card jaina = CardFactory.getCard(
                new PaperCard(cardRules, "PH01", CardRarity.MythicRare), jainaController, game);

        SpellAbility ultimate = null;
        for (final SpellAbility ability : jaina.getSpellAbilities()) {
            if (ability.hasParam("Name") && ability.getParam("Name").startsWith("Emblem")) {
                ultimate = ability;
                break;
            }
        }
        Assert.assertNotNull(ultimate, "Jaina's ultimate should be parsed from the real card script");
        ultimate.setActivatingPlayer(jainaController);
        AbilityUtils.resolve(ultimate);

        final Card emblem = jainaController.getCardsIn(ZoneType.Command).stream()
                .filter(Card::isEmblem)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Jaina's ultimate did not create an emblem"));
        game.getAction().checkStaticAbilities();
        Assert.assertEquals(emblem.getStaticAbilities().size(), 1,
                "the emblem should carry its lifelink static ability");
        final StaticAbility lifelink = emblem.getStaticAbilities().get(0);
        Assert.assertTrue(emblem.isEmblem());
        Assert.assertEquals(lifelink.getParam("Affected"), "Emblem.Self");
        Assert.assertTrue(game.getCardsIn(ZoneType.Command).contains(emblem));
        Assert.assertTrue(emblem.isValid("Emblem.Self", jainaController, emblem, lifelink));
        Assert.assertTrue(lifelink.getActiveZone().contains(ZoneType.Command));
        Assert.assertTrue(StaticAbilityContinuous.getAffectedCards(lifelink, CardCollection.EMPTY)
                .contains(emblem), "the lifelink static ability should affect its own emblem");
        Assert.assertTrue(emblem.hasKeyword(Keyword.LIFELINK), "the emblem should have lifelink");

        final Card creature = new Card(game.nextCardId(), game);
        creature.setName("Opponent creature");
        creature.setOwner(opponent);
        creature.addType("Creature");
        creature.setBasePower(5);
        creature.setBaseToughness(5);
        game.getAction().moveTo(ZoneType.Battlefield, creature, null, null);

        Assert.assertEquals(emblem.getTriggers().size(), 1, "the emblem should carry its tap trigger");
        final Trigger trigger = emblem.getTriggers().get(0);
        final Map<AbilityKey, Object> runParams = AbilityKey.mapFromCard(creature);
        runParams.put(AbilityKey.Attacker, false);
        runParams.put(AbilityKey.Cause, null);
        runParams.put(AbilityKey.Player, opponent);
        runParams.put(AbilityKey.FirstTime, true);
        Assert.assertTrue(trigger.performTest(runParams), "the emblem trigger should accept an opponent's creature");
        Assert.assertNotNull(game.getZoneOf(emblem), "the emblem should have a registered command zone");
        Assert.assertEquals(game.getZoneOf(emblem).getZoneType(), ZoneType.Command);
        Assert.assertTrue(trigger.phasesCheck(game), "the emblem trigger should be active in the current phase");
        Assert.assertTrue(trigger.checkActivationLimit(), "the emblem trigger should not have reached a turn limit");
        Assert.assertTrue(trigger.requirementsCheck(game), "the emblem trigger should meet its game requirements");
        Assert.assertTrue(trigger.meetsRequirementsOnTriggeredObjects(game, runParams),
                "the emblem trigger should meet its triggered-object requirements");
        game.getTriggerHandler().resetActiveTriggers();
        Assert.assertTrue(game.getTriggerHandler().getActiveTrigger(TriggerType.Taps, runParams).contains(trigger),
                "the emblem tap trigger should be registered while the emblem is in the command zone");
        Assert.assertFalse(game.getStack().isFrozen(), "the test stack should accept triggered abilities immediately");
        Assert.assertTrue(creature.tap(false, null, opponent));
        Assert.assertTrue(game.getStack().hasSimultaneousStackEntries(),
                "tapping the opponent's creature should put the emblem trigger in the simultaneous queue");
        game.getStack().clearSimultaneousStack();

        final SpellAbility damage = trigger.getOverridingAbility().copy(emblem, jainaController, false, true);
        damage.setActivatingPlayer(jainaController);
        damage.setTrigger(trigger);
        trigger.setTriggeringObjects(damage, runParams);
        AbilityUtils.resolve(damage);

        Assert.assertEquals(creature.getDamage(), 3, "the tapped creature should take 3 damage");
        Assert.assertEquals(jainaController.getLife(), 23, "lifelink should gain 3 life");
        Assert.assertEquals(trigger.getMode(), TriggerType.Taps);
        Assert.assertTrue(trigger.getActiveZone().contains(ZoneType.Command));
    }
}
