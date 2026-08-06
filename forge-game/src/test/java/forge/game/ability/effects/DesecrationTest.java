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
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCopyService;
import forge.game.card.CardFactory;
import forge.game.card.CardPlayOption;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityContinuous;
import forge.game.staticability.StaticAbilityLayer;
import forge.game.trigger.Trigger;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DesecrationTest {
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
    public void damagedCreatureDeathGrantsOnlyTheRememberedCardAFreeGraveyardCast()
            throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Desecration test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(controller);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "black", "亵渎.txt")
                .toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "亵渎");
        Card desecration = CardFactory.getCard(
                new PaperCard(cardRules, "PH01", CardRarity.Rare), controller, game);
        desecration = game.getAction().moveTo(ZoneType.Stack, desecration, null, null);

        final Card damagedCreature = addCreature(
                game, opponent, "Creature damaged by Desecration");
        final Card unrelatedCreature = addCreature(
                game, opponent, "Creature not damaged by Desecration");
        desecration.getDamageHistory().registerDamage(
                1, false, desecration, damagedCreature, new HashMap<>());

        final SpellAbility watcherAbility = AbilityFactory.getAbility(
                desecration.getSVar("DBWatchDeaths"), desecration);
        watcherAbility.setActivatingPlayer(controller);
        AbilityUtils.resolve(watcherAbility);

        final Card watcher = controller.getCardsIn(ZoneType.Command).stream()
                .filter(card -> !card.getTriggers().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("the spell did not create its death watcher"));
        Assert.assertEquals(watcher.getRememberedCount(), 1);
        final Trigger deathTrigger = watcher.getTriggers().get(0);

        final Card graveyardDesecration = game.getAction().moveTo(
                ZoneType.Graveyard, desecration, null, null);
        final Map<AbilityKey, Object> damagedDies = zoneChangeParams(damagedCreature);
        Assert.assertTrue(deathTrigger.performTest(damagedDies),
                "a creature damaged by the remembered spell should satisfy the death condition");
        Assert.assertFalse(deathTrigger.performTest(zoneChangeParams(unrelatedCreature)),
                "an unrelated creature death must not grant the graveyard cast");

        final SpellAbility grant = deathTrigger.getOverridingAbility()
                .copy(watcher, controller, false, true);
        grant.setActivatingPlayer(controller);
        grant.setTrigger(deathTrigger);
        deathTrigger.setTriggeringObjects(grant, damagedDies);
        AbilityUtils.resolve(grant);

        final Card permissionEffect = controller.getCardsIn(ZoneType.Command).stream()
                .filter(card -> !card.getStaticAbilities().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("the death did not create a cast permission"));
        Assert.assertEquals(permissionEffect.getRememberedCount(), 1);
        final StaticAbility freeCast = permissionEffect.getStaticAbilities().get(0);
        Assert.assertEquals(freeCast.getParam("AffectedZone"), "Graveyard");
        Assert.assertTrue(freeCast.hasParam("MayPlayWithoutManaCost"));
        Assert.assertTrue(StaticAbilityContinuous.getAffectedCards(
                freeCast, CardCollection.EMPTY).contains(graveyardDesecration));

        StaticAbilityContinuous.applyContinuousAbility(
                freeCast,
                new CardCollection(graveyardDesecration),
                StaticAbilityLayer.RULES);
        final CardPlayOption option = graveyardDesecration.mayPlay(freeCast);
        Assert.assertNotNull(option);
        Assert.assertEquals(option.getPayManaCost(), CardPlayOption.PayManaCost.NO);
        Assert.assertTrue(option.grantsZonePermissions(),
                "the permission must allow casting the remembered card from the graveyard");
    }

    private static Map<AbilityKey, Object> zoneChangeParams(final Card card) {
        final Card lki = CardCopyService.getLKICopy(card);
        final Map<AbilityKey, Object> params = AbilityKey.mapFromCard(card);
        params.put(AbilityKey.CardLKI, lki);
        params.put(AbilityKey.Origin, ZoneType.Battlefield.name());
        params.put(AbilityKey.Destination, ZoneType.Graveyard.name());
        params.put(AbilityKey.Cause, null);
        return params;
    }

    private static Card addCreature(final Game game, final Player owner, final String name) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:1 B",
                "Types:Creature Horror",
                "PT:1/1",
                "Oracle:Test card."));
        final Card card = CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common), owner, game);
        return game.getAction().moveTo(ZoneType.Battlefield, card, null, null);
    }
}
