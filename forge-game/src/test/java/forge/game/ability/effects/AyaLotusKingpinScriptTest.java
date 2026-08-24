package forge.game.ability.effects;

import forge.CardStorageReader;
import forge.ImageKeys;
import forge.StaticData;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class AyaLotusKingpinScriptTest {
    @BeforeClass
    public void initializeCardData() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", path("forge-gui", "res", "languages"));
        ImageKeys.initializeDirs(path("custom", "cards", "pictures") + File.separator,
                Collections.emptyMap(), "", "", "", "", "", "", "");
        if (StaticData.instance() == null) {
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
    }

    @Test
    public void realScriptCreatesTreasuresBeforeARestrictedEmblemChoice() throws Exception {
        final Fixture fixture = fixture();
        final Card aya = fixture.aya;

        Assert.assertEquals(aya.getManaCost().getCMC(), 5);
        Assert.assertEquals(aya.getNetPower(), 5);
        Assert.assertEquals(aya.getNetToughness(), 3);
        Assert.assertFalse(aya.getType().isLegendary());

        Assert.assertEquals(aya.getTriggers().size(), 1);
        final SpellAbility treasures = aya.getTriggers().get(0).ensureAbility();
        Assert.assertEquals(treasures.getApi(), ApiType.Token);
        Assert.assertEquals(treasures.getParam("TokenAmount"), "3");
        Assert.assertEquals(treasures.getParam("TokenScript"), "c_a_treasure_sac");

        final SpellAbility choice = treasures.getSubAbility();
        Assert.assertNotNull(choice, "the emblem choice must follow Treasure creation");
        Assert.assertEquals(choice.getApi(), ApiType.Charm);
        Assert.assertEquals(choice.getParam("ChoiceRestriction"), "ThisGame");
        Assert.assertEquals(choice.getParam("CharmNum"), "1");
        final List<AbilitySub> modes = choice.getAdditionalAbilityList("Choices");
        Assert.assertEquals(modes.size(), 3);

        for (final AbilitySub mode : modes) {
            Assert.assertEquals(mode.getApi(), ApiType.Effect);
            Assert.assertEquals(mode.getParam("EffectOwner"), "You");
            Assert.assertEquals(mode.getParam("Duration"), "Permanent");
            mode.setActivatingPlayer(fixture.controller);
            AbilityUtils.resolve(mode);
        }

        Assert.assertEquals(fixture.controller.getCardsIn(ZoneType.Command).stream()
                .filter(Card::isEmblem).count(), 3L);
        assertEmblem(fixture, "Emblem — 艾雅的青玉财宝", ApiType.MakeCard,
                "CreateJadeGolem");
        assertEmblem(fixture, "Emblem — 艾雅的爆裂财宝", ApiType.DealDamage,
                "DealRandomDamage");
        assertEmblem(fixture, "Emblem — 艾雅的智谋财宝", ApiType.Draw,
                "DrawCard");
    }

    @Test
    public void registeredPrintUsesTheNormalizedArtworkFilename() {
        final PaperCard registered = StaticData.instance().getCommonCards()
                .getAllCards("艾雅，玉莲帮主").stream()
                .filter(card -> "PH01".equals(card.getEdition()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing PH01 Aya registration"));
        Assert.assertEquals(registered.getArtist(), "James Ryman");
        Assert.assertEquals(registered.getCardImageKey(), "PH01/艾雅,玉莲帮主.full");
    }

    private static void assertEmblem(final Fixture fixture, final String name,
                                     final ApiType expectedApi, final String execute) {
        final Card emblem = fixture.controller.getCardsIn(ZoneType.Command).stream()
                .filter(card -> name.equals(card.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing emblem " + name));
        Assert.assertTrue(emblem.isEmblem());
        Assert.assertEquals(emblem.getTriggers().size(), 1);

        final Trigger trigger = emblem.getTriggers().get(0);
        Assert.assertEquals(trigger.getMode(), TriggerType.Sacrificed);
        Assert.assertTrue(trigger.getActiveZone().contains(ZoneType.Command));
        Assert.assertEquals(trigger.getParam("ValidCard"), "Treasure.token+YouCtrl");
        Assert.assertEquals(trigger.getParam("Execute"), execute);

        final SpellAbility effect = trigger.ensureAbility();
        Assert.assertEquals(effect.getApi(), expectedApi);
        if (expectedApi == ApiType.MakeCard) {
            Assert.assertEquals(effect.getParam("Name"), "青玉魔像");
            Assert.assertEquals(effect.getParam("Zone"), "Battlefield");
            Assert.assertTrue(effect.hasParam("Conjure"));
        } else if (expectedApi == ApiType.DealDamage) {
            Assert.assertEquals(effect.getParam("ValidTgts"),
                    "Player.Other,Permanent.YouDontCtrl");
            Assert.assertEquals(effect.getParam("NumDmg"), "2");
            Assert.assertTrue(effect.hasParam("TargetsAtRandom"));
        } else if (expectedApi == ApiType.Draw) {
            Assert.assertEquals(effect.getParam("Defined"), "You");
            Assert.assertEquals(effect.getParam("NumCards"), "1");
            Assert.assertEquals(effect.getPayCosts().getTotalMana().getCMC(), 1,
                    "the draw emblem must require a {1} payment");
        }

    }

    private static Fixture fixture() throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Aya Lotus Kingpin script test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        controller.setLife(20, null);
        opponent.setLife(20, null);
        game.getPhaseHandler().setPlayerTurn(controller);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "multicolor",
                "艾雅，玉莲帮主.txt").toAbsolutePath().normalize();
        final CardRules rulesText = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8),
                "艾雅，玉莲帮主");
        final Card aya = CardFactory.getCard(
                new PaperCard(rulesText, "PH01", CardRarity.Rare), controller, game);
        return new Fixture(controller, aya);
    }

    private static String path(final String first, final String... more) {
        Path result = Paths.get("..", first);
        for (final String element : more) {
            result = result.resolve(element);
        }
        return result.toAbsolutePath().normalize().toString();
    }

    private static final class Fixture {
        private final Player controller;
        private final Card aya;

        private Fixture(final Player controller, final Card aya) {
            this.controller = controller;
            this.aya = aya;
        }
    }
}
