package forge.game.ability.effects;

import com.google.common.eventbus.Subscribe;
import forge.CardStorageReader;
import forge.ImageKeys;
import forge.StaticData;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.GamePieceType;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.event.GameEventCardChangeZone;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AyaLotusKingpinScriptTest {
    private static final String[] EMBLEM_NAMES = {
            "Emblem — Aya's Jade Treasure",
            "Emblem — Aya's Burst Treasure",
            "Emblem — Aya's Cunning Treasure"
    };

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
    public void realScriptCreatesTreasuresThenChoosesARegisteredEntityEmblem()
            throws Exception {
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
        Assert.assertEquals(treasures.getParam("TokenOwner"), "You");

        final SpellAbility choice = treasures.getSubAbility();
        Assert.assertNotNull(choice, "the entity choice must follow Treasure creation");
        Assert.assertEquals(choice.getApi(), ApiType.GenericChoice);
        Assert.assertEquals(choice.getParam("Defined"), "You");
        Assert.assertEquals(choice.getParam("ChoiceAmount"), "1");
        Assert.assertFalse(choice.hasParam("ChoiceRestriction"));
        final List<AbilitySub> modes = choice.getAdditionalAbilityList("Choices");
        Assert.assertEquals(modes.size(), 3);

        for (final AbilitySub mode : modes) {
            Assert.assertEquals(mode.getApi(), ApiType.MakeCard);
            Assert.assertEquals(mode.getParam("Defined"), "You");
            Assert.assertEquals(mode.getParam("Zone"), "Command");
            Assert.assertTrue(mode.hasParam("AsEmblem"));
            Assert.assertEquals(mode.getParam("PresentZone"), "Command");
            Assert.assertEquals(mode.getParam("PresentCompare"), "EQ0");
            Assert.assertTrue(mode.getParam("IsPresent").startsWith(
                    "Emblem.YouCtrl+named"));
            Assert.assertNotNull(StaticData.instance().getCommonCards()
                    .getUniqueByName(mode.getParam("Name")),
                    "every emblem choice must resolve to a loaded entity card");
            mode.setActivatingPlayer(fixture.controller);
            AbilityUtils.resolve(mode);
        }

        Assert.assertEquals(fixture.controller.getCardsIn(ZoneType.Command).stream()
                .filter(Card::isEmblem).count(), 3L);
        assertEmblem(fixture, "Emblem — Aya's Jade Treasure", ApiType.MakeCard,
                "CreateJadeGolem");
        assertEmblem(fixture, "Emblem — Aya's Burst Treasure", ApiType.DealDamage,
                "DealRandomDamage");
        assertEmblem(fixture, "Emblem — Aya's Cunning Treasure", ApiType.MakeCard,
                "ConjureForgedPotion");
    }

    @Test
    public void existingCommandZoneEmblemsAreTheOnlyChoiceHistory() throws Exception {
        final Fixture fixture = fixture();
        fixture.controller.getZone(ZoneType.Battlefield).add(fixture.aya);
        final SpellAbility treasures = fixture.aya.getTriggers().get(0).ensureAbility();
        treasures.setActivatingPlayer(fixture.controller);
        final SpellAbility choice = treasures.getSubAbility();
        final List<AbilitySub> modes = choice.getAdditionalAbilityList("Choices");

        for (int chosen = 0; chosen < modes.size(); chosen++) {
            final AbilitySub mode = modes.get(chosen);
            Assert.assertTrue(isChoiceAvailable(fixture, choice, mode));
            AbilityUtils.resolve(mode);
            Assert.assertFalse(isChoiceAvailable(fixture, choice, mode),
                    "the corresponding command-zone entity must disable only its own choice");
            for (int remaining = chosen + 1; remaining < modes.size(); remaining++) {
                Assert.assertTrue(isChoiceAvailable(fixture, choice, modes.get(remaining)));
            }
        }

        Assert.assertEquals(modes.stream()
                .filter(mode -> isChoiceAvailable(fixture, choice, mode)).count(), 0L);
    }

    @Test
    public void nestedResolutionTimeChoiceCanBeStackedWithoutAnUnchosenCharmMode()
            throws Exception {
        final Fixture fixture = fixture();
        fixture.controller.getZone(ZoneType.Battlefield).add(fixture.aya);
        final SpellAbility treasures = fixture.aya.getTriggers().get(0).ensureAbility();
        treasures.setActivatingPlayer(fixture.controller);

        final SpellAbilityStackInstance stackInstance = new SpellAbilityStackInstance(treasures);

        Assert.assertNotNull(stackInstance);
        Assert.assertEquals(treasures.getApi(), ApiType.Token);
        Assert.assertEquals(treasures.getSubAbility().getApi(), ApiType.GenericChoice);
        Assert.assertNull(treasures.getSubAbility().getSubAbility());
    }

    @Test
    public void entityEmblemMovesDirectlyFromNoZoneToCommand() throws Exception {
        final Fixture fixture = fixture();
        fixture.controller.getZone(ZoneType.Battlefield).add(fixture.aya);
        final SpellAbility treasures = fixture.aya.getTriggers().get(0).ensureAbility();
        treasures.setActivatingPlayer(fixture.controller);
        final AbilitySub gain = treasures.getSubAbility()
                .getAdditionalAbilityList("Choices").get(0);
        final ZoneChangeRecorder recorder = new ZoneChangeRecorder();
        fixture.controller.getGame().subscribeToEvents(recorder);

        AbilityUtils.resolve(gain);

        final List<GameEventCardChangeZone> emblemMoves = recorder.events.stream()
                .filter(event -> gain.getParam("Name").equals(event.card().getName()))
                .toList();
        Assert.assertEquals(emblemMoves.size(), 1,
                "an entity emblem must not be staged in None as an ordinary card");
        Assert.assertNull(emblemMoves.get(0).from());
        Assert.assertEquals(emblemMoves.get(0).to().zoneType(), ZoneType.Command);
        Assert.assertFalse(fixture.controller.getGame().getTriggerHandler()
                .isTriggerSuppressed(TriggerType.ChangesZone));
    }

    @Test
    public void cunningEmblemConjuresTheTokenHsForgedPotionIntoHand()
            throws Exception {
        final Fixture fixture = fixture();
        fixture.controller.getZone(ZoneType.Battlefield).add(fixture.aya);
        final SpellAbility treasures = fixture.aya.getTriggers().get(0).ensureAbility();
        treasures.setActivatingPlayer(fixture.controller);
        final AbilitySub gainCunning = treasures.getSubAbility()
                .getAdditionalAbilityList("Choices").stream()
                .filter(mode -> "Emblem — Aya's Cunning Treasure"
                        .equals(mode.getParam("Name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing Cunning emblem choice"));
        gainCunning.setActivatingPlayer(fixture.controller);
        AbilityUtils.resolve(gainCunning);

        final Card cunning = fixture.controller.getCardsIn(ZoneType.Command).stream()
                .filter(card -> "Emblem — Aya's Cunning Treasure"
                        .equals(card.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing Cunning emblem entity"));
        final SpellAbility conjure = cunning.getTriggers().get(0).ensureAbility();
        conjure.setActivatingPlayer(fixture.controller);

        AbilityUtils.resolve(conjure);

        final Card potion = fixture.controller.getCardsIn(ZoneType.Hand).stream()
                .filter(card -> "伪造的药水".equals(card.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cunning emblem did not conjure a potion"));
        Assert.assertNotNull(potion.getPaperCard());
        Assert.assertEquals(potion.getPaperCard().getEdition(), "TOKEN_HS");
        Assert.assertEquals(potion.getZone().getZoneType(), ZoneType.Hand);
    }

    @Test
    public void registeredPrintUsesTheExpectedArtworkSet() {
        final PaperCard registered = StaticData.instance().getCommonCards()
                .getAllCards("艾雅，玉莲帮主").stream()
                .filter(card -> "PH01".equals(card.getEdition()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing PH01 Aya registration"));
        Assert.assertEquals(registered.getArtist(), "James Ryman");
        Assert.assertEquals(registered.getCardImageKey(), "PH01/艾雅,玉莲帮主.full");

        for (final String emblemName : EMBLEM_NAMES) {
            final PaperCard emblem = StaticData.instance().getCommonCards()
                    .getAllCards(emblemName).stream()
                    .filter(card -> "TOKEN_HS".equals(card.getEdition()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing TOKEN_HS registration for " + emblemName));
            Assert.assertEquals(emblem.getArtist(), "Custom");
            Assert.assertEquals(emblem.getCardImageKey(), "TOKEN_HS/" + emblemName + ".full");
        }
    }

    private static void assertEmblem(final Fixture fixture, final String name,
                                     final ApiType expectedApi, final String execute) {
        final Card emblem = fixture.controller.getCardsIn(ZoneType.Command).stream()
                .filter(card -> name.equals(card.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing emblem " + name));
        Assert.assertTrue(emblem.isEmblem());
        Assert.assertEquals(emblem.getZone().getZoneType(), ZoneType.Command);
        Assert.assertEquals(emblem.getGamePieceType(), GamePieceType.EFFECT,
                "an entity-backed emblem must remain an intangible command-zone effect");
        Assert.assertNotNull(emblem.getPaperCard(),
                "the emblem must retain its independently loaded PaperCard definition");
        Assert.assertEquals(emblem.getTriggers().size(), 1);

        final Trigger trigger = emblem.getTriggers().get(0);
        Assert.assertEquals(trigger.getMode(), TriggerType.Sacrificed);
        Assert.assertTrue(trigger.getActiveZone().contains(ZoneType.Command));
        Assert.assertEquals(trigger.getParam("ValidCard"), "Treasure.token+YouCtrl");
        Assert.assertEquals(trigger.getParam("Execute"), execute);

        final SpellAbility effect = trigger.ensureAbility();
        Assert.assertEquals(effect.getApi(), expectedApi);
        if ("CreateJadeGolem".equals(execute)) {
            Assert.assertEquals(effect.getParam("Name"), "青玉魔像");
            Assert.assertEquals(effect.getParam("Zone"), "Battlefield");
            Assert.assertTrue(effect.hasParam("Conjure"));
        } else if ("ConjureForgedPotion".equals(execute)) {
            Assert.assertEquals(effect.getParam("Name"), "伪造的药水");
            Assert.assertEquals(effect.getParam("Defined"), "You");
            Assert.assertEquals(effect.getParam("Amount"), "1");
            Assert.assertEquals(effect.getParam("Zone"), "Hand");
            Assert.assertTrue(effect.hasParam("Conjure"));
            Assert.assertNotNull(StaticData.instance().getCommonCards()
                    .getAllCards("伪造的药水").stream()
                    .filter(card -> "TOKEN_HS".equals(card.getEdition()))
                    .findFirst().orElse(null),
                    "the Cunning emblem must reuse the registered TOKEN_HS potion");
        } else if (expectedApi == ApiType.DealDamage) {
            Assert.assertEquals(effect.getParam("ValidTgts"),
                    "Player.Other,Permanent.YouDontCtrl");
            Assert.assertEquals(effect.getParam("NumDmg"), "2");
            Assert.assertTrue(effect.hasParam("TargetsAtRandom"));
        }

    }

    private static boolean isChoiceAvailable(final Fixture fixture,
                                             final SpellAbility choice,
                                             final AbilitySub mode) {
        return mode.getRestrictions().checkOtherRestrictions(
                fixture.aya, mode, choice.getActivatingPlayer());
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

    private static final class ZoneChangeRecorder {
        private final List<GameEventCardChangeZone> events = new ArrayList<>();

        @Subscribe
        public void onChangeZone(final GameEventCardChangeZone event) {
            events.add(event);
        }
    }
}
