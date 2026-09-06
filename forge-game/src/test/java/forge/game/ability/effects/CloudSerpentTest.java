package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardType;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.card.CardFactory;
import forge.game.cost.CostBehold;
import forge.game.cost.CostPartMana;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.FileSection;
import forge.util.FileUtil;
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
import java.util.List;
import java.util.Map;

public class CloudSerpentTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
        if (!CardType.Constant.LOADED.isSet()) {
            final Path typeList = Paths.get("..", "forge-gui", "res", "lists",
                    "TypeLists.txt").toAbsolutePath().normalize();
            final Map<String, List<String>> contents = FileSection.parseSections(
                    FileUtil.readFile(typeList.toString()));
            for (final String sectionName : contents.keySet()) {
                CardType.Helper.parseTypes(sectionName, contents.get(sectionName));
            }
            CardType.Constant.LOADED.set();
        }
    }

    @Test
    public void beholdFromHandClonesBeforeTheCopiedDragonIsCast() throws Exception {
        final TestContext context = context("Cloud Serpent hand behold test");
        final Card serpent = cloudSerpent(context);
        final SpellAbility transform = transformAbility(serpent);
        final CostBehold behold = transform.getPayCosts().getCostParts().stream()
                .filter(CostBehold.class::isInstance)
                .map(CostBehold.class::cast)
                .findFirst().orElseThrow();

        Assert.assertEquals(transform.getPayCosts().getTotalMana().getCMC(), 1,
                "the hand transformation must charge one blue mana");
        Assert.assertEquals(transform.getPayCosts().getTotalMana().toString(), "{U}");
        Assert.assertTrue(transform.hasParam("KeepCloneOnStack"));
        Assert.assertEquals(behold.getType(), "Dragon.Other");
        Assert.assertFalse(behold.canPay(transform, context.player, false),
                "Cloud Serpent must not behold itself");

        final Card dragon = moveTo(context, ZoneType.Hand,
                dragon(context, "Shivan Dragon"));
        Assert.assertTrue(dragon.isInZone(ZoneType.Hand));
        Assert.assertSame(dragon.getController(), context.player);
        Assert.assertTrue(dragon.getType().hasSubtype("Dragon"));
        Assert.assertEquals(dragon.getBasicSpells().getFirst()
                .getPayCosts().getTotalMana().toString(), "{4}{R}{R}",
                "the test Dragon must expose its printed casting cost");
        Assert.assertTrue(dragon.isValid("Dragon.Other", context.player,
                serpent, transform));
        Assert.assertEquals(behold.getMaxAmountX(transform, context.player, false),
                Integer.valueOf(1));
        Assert.assertTrue(behold.canPay(transform, context.player, false));

        resolveTransform(transform, dragon, context.player);
        assertCopiedValues(serpent, dragon);

        final SpellAbility copiedCast = serpent.getBasicSpells().getFirst();
        copiedCast.setActivatingPlayer(context.player);
        Assert.assertEquals(copiedCast.getPayCosts().getCostParts().stream()
                .filter(CostPartMana.class::isInstance).count(), 1L,
                "the copied spell must have exactly one payable mana component");
        Assert.assertEquals(copiedCast.getPayCosts().getTotalMana().toString(),
                dragon.getManaCost().toString(),
                "casting must use the copied Dragon's normal mana cost");

        final Card stackSerpent = context.game.getAction().moveToStack(serpent, copiedCast);
        copiedCast.setHostCard(stackSerpent);
        assertCopiedValues(stackSerpent, dragon);
        Assert.assertEquals(stackSerpent.getBasicSpells().getFirst()
                .getPayCosts().getTotalMana().toString(),
                dragon.getManaCost().toString(),
                "the copied cost must survive the move from hand to stack");

        final Card permanent = context.game.getAction().moveTo(
                ZoneType.Battlefield, stackSerpent, copiedCast, null);
        assertCopiedValues(permanent, dragon);

        final Card graveyardCard = context.game.getAction().moveTo(
                ZoneType.Graveyard, permanent, copiedCast, null);
        Assert.assertEquals(graveyardCard.getName(), "云端翔龙",
                "the copy effect must end after the resulting permanent leaves the battlefield");
        Assert.assertEquals(graveyardCard.getManaCost().toString(), "{1}{U}{U}");
    }

    @Test
    public void beholdCanCloneAControlledBattlefieldDragon() throws Exception {
        final TestContext context = context("Cloud Serpent battlefield behold test");
        final Card serpent = cloudSerpent(context);
        final Card dragon = moveTo(context, ZoneType.Battlefield,
                dragon(context, "Shivan Dragon"));
        final SpellAbility transform = transformAbility(serpent);
        final CostBehold behold = transform.getPayCosts().getCostParts().stream()
                .filter(CostBehold.class::isInstance)
                .map(CostBehold.class::cast)
                .findFirst().orElseThrow();

        Assert.assertTrue(dragon.isInZone(ZoneType.Battlefield));
        Assert.assertSame(dragon.getController(), context.player);
        Assert.assertTrue(dragon.getType().hasSubtype("Dragon"));
        Assert.assertTrue(dragon.isValid("Dragon.Other", context.player,
                serpent, transform));
        Assert.assertEquals(behold.getMaxAmountX(transform, context.player, false),
                Integer.valueOf(1));
        Assert.assertTrue(behold.canPay(transform, context.player, false));
        resolveTransform(transform, dragon, context.player);
        assertCopiedValues(serpent, dragon);
    }

    private static void resolveTransform(final SpellAbility transform,
            final Card dragon, final Player player) {
        transform.setActivatingPlayer(player);
        transform.addCostToHashList(CardCopyService.getLKICopy(dragon),
                "Revealed", true);
        AbilityUtils.resolve(transform);
    }

    private static void assertCopiedValues(final Card actual, final Card expected) {
        Assert.assertEquals(actual.getName(), expected.getName());
        Assert.assertEquals(actual.getManaCost(), expected.getManaCost());
        Assert.assertEquals(actual.getType().toString(), expected.getType().toString());
        Assert.assertEquals(actual.getCurrentPower(), expected.getCurrentPower());
        Assert.assertEquals(actual.getCurrentToughness(), expected.getCurrentToughness());
    }

    private static SpellAbility transformAbility(final Card serpent) {
        return serpent.getSpellAbilities().stream()
                .filter(ability -> !ability.isSpell() && ability.getApi() == ApiType.Clone)
                .findFirst().orElseThrow();
    }

    private static Card cloudSerpent(final TestContext context) throws Exception {
        final Path script = Paths.get("..", "custom", "cards", "blue",
                "云端翔龙.txt").toAbsolutePath().normalize();
        final CardRules rules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "云端翔龙");
        final Card card = CardFactory.getCard(
                new PaperCard(rules, "PH01", CardRarity.Rare),
                context.player, context.game);
        return moveTo(context, ZoneType.Hand, card);
    }

    private static Card dragon(final TestContext context, final String name) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:4 R R",
                "Types:Creature Dragon",
                "PT:5/5",
                "K:Flying",
                "Oracle:Flying"));
        return CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Rare),
                context.player, context.game);
    }

    private static Card moveTo(final TestContext context,
            final ZoneType zone, final Card card) {
        card.setController(context.player, context.game.getNextTimestamp());
        context.player.getZone(zone).add(card);
        return card;
    }

    private static TestContext context(final String title) {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), title));
        final Player player = new Player("Player", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(player);
        game.getPlayers().add(opponent);
        player.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(player);
        game.setAge(GameStage.Play);
        return new TestContext(game, player);
    }

    private record TestContext(Game game, Player player) {
    }
}
