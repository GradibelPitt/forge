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
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

public class EonarTreefolkTest {
    @BeforeClass
    public void initializeCardAndTokenData() {
        Lang.createInstance("en-US");
        final String languages = path("forge-gui", "res", "languages");
        Localizer.getInstance().initialize("en-US", languages);

        new StaticData(
                new CardStorageReader(path("forge-gui", "res", "cardsfolder"), null, false),
                new CardStorageReader(path("forge-gui", "res", "tokenscripts"), null, false),
                new CardStorageReader(path("custom", "cards"), null, false),
                new CardStorageReader(path("custom", "tokens"), null, false),
                path("forge-gui", "res", "editions"),
                path("custom", "editions"),
                path("forge-gui", "res", "blockdata"),
                path("forge-gui", "res", "setlookup"),
                "Latest",
                true,
                true,
                true,
                true
        );
    }

    private static String path(final String first, final String... more) {
        Path result = Paths.get("..", first);
        for (final String element : more) {
            result = result.resolve(element);
        }
        return result.toAbsolutePath().normalize().toString();
    }

    @Test
    public void exhaustingEonarBeforeCombatCreatesATreefolkAtBeginningOfCombat() throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Eonar Treefolk test"));
        final Player controller = new Player("Eonar controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        controller.setLife(20, null);
        opponent.setLife(20, null);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, controller);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "multicolor",
                "生命的缚誓者艾欧娜尔.txt").toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "生命的缚誓者艾欧娜尔");
        final Card eonar = CardFactory.getCard(
                new PaperCard(cardRules, "PH01", CardRarity.MythicRare), controller, game);
        game.getAction().moveTo(ZoneType.Battlefield, eonar, null, null);

        final Trigger exhaustTrigger = eonar.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.AbilityCast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eonar is missing its exhaust trigger"));
        final Trigger combatTrigger = eonar.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.Phase)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eonar is missing its beginning-of-combat trigger"));
        final SpellAbility exhaustAbility = eonar.getSpellAbilities().stream()
                .filter(ability -> ability.hasParam("Exhaust"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eonar is missing an exhaust ability"));
        exhaustAbility.setActivatingPlayer(controller);

        final Map<AbilityKey, Object> exhaustParams = AbilityKey.newMap();
        exhaustParams.put(AbilityKey.SpellAbility, exhaustAbility);
        exhaustParams.put(AbilityKey.Activator, controller);
        Assert.assertTrue(exhaustTrigger.performTest(exhaustParams),
                "the real exhaust ability should satisfy Eonar's marker trigger");

        final SpellAbility markExhausted = exhaustTrigger.getOverridingAbility()
                .copy(eonar, controller, false, true);
        markExhausted.setActivatingPlayer(controller);
        markExhausted.setTrigger(exhaustTrigger);
        exhaustTrigger.setTriggeringObjects(markExhausted, exhaustParams);
        AbilityUtils.resolve(markExhausted);

        final Card marker = controller.getCardsIn(ZoneType.Command).stream()
                .filter(card -> "Eonar Exhausted This Turn".equals(card.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "resolving the exhaust trigger should create Eonar's turn marker"));
        Assert.assertEquals(marker.getController(), controller);
        Assert.assertTrue(marker.isValid("Effect.namedEonar Exhausted This Turn+YouCtrl",
                controller, eonar, combatTrigger));

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN, controller);
        final Map<AbilityKey, Object> phaseParams = AbilityKey.mapFromPlayer(controller);
        Assert.assertTrue(combatTrigger.performTest(phaseParams));
        Assert.assertTrue(combatTrigger.phasesCheck(game));
        Assert.assertTrue(combatTrigger.requirementsCheck(game),
                "Eonar's combat trigger should see its command-zone turn marker");

        final SpellAbility createTreefolk = combatTrigger.getOverridingAbility()
                .copy(eonar, controller, false, true);
        createTreefolk.setActivatingPlayer(controller);
        createTreefolk.setTrigger(combatTrigger);
        combatTrigger.setTriggeringObjects(createTreefolk, phaseParams);
        Assert.assertTrue(StaticData.instance().getAllTokens()
                .containsRule("g_5_5_treefolk_reach"));
        Assert.assertNotNull(StaticData.instance().getAllTokens()
                .getToken("g_5_5_treefolk_reach", "PH01"));
        AbilityUtils.resolve(createTreefolk);

        final Card treefolk = controller.getCardsIn(ZoneType.Battlefield).stream()
                .filter(Card::isToken)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Eonar did not create a token; battlefield: "
                        + controller.getCardsIn(ZoneType.Battlefield)));
        Assert.assertEquals(treefolk.getNetPower(), 5);
        Assert.assertEquals(treefolk.getNetToughness(), 5);
        Assert.assertTrue(treefolk.hasKeyword(Keyword.REACH));
    }
}
