package forge.game;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardStateName;
import forge.card.CardType;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCopyService;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class OfficialCardSyncTest {
    @BeforeClass
    public void initializeTypes() {
        Path resources = Path.of(System.getProperty("forge.cardSync.resources", "../forge-gui/res"));
        forge.util.Lang.createInstance("en-US");
        Map<String, List<String>> sections = forge.util.FileSection.parseSections(
                forge.util.FileUtil.readFile(resources.resolve("lists/TypeLists.txt").toString()));
        sections.forEach(CardType.Helper::parseTypes);
        CardType.Constant.LOADED.set();
    }

    @Test
    public void negativePowerAssignsPositiveCombatDamageOnlyWhenApplicable() {
        Fixture f = new Fixture();
        Card loot = f.card("Name:Loot, the Anomaly", "ManaCost:2 B",
                "Types:Legendary Creature Beast Horror", "PT:-2/4",
                "S:Mode$ CombatDamageNegatePower | ValidCard$ Card.Self+powerLT0");
        f.player.getZone(ZoneType.Battlefield).add(loot);
        Assert.assertEquals(loot.getNetPower(), -2);
        Assert.assertEquals(loot.getNetCombatDamage(), 2);
        loot.getCurrentState().setBasePower(-4);
        Assert.assertEquals(loot.getNetCombatDamage(), 4);
        loot.getCurrentState().setBasePower(3);
        Assert.assertEquals(loot.getNetCombatDamage(), 3);
        Card ordinary = f.card("Name:Ordinary", "ManaCost:1", "Types:Creature", "PT:-2/4");
        f.player.getZone(ZoneType.Battlefield).add(ordinary);
        Assert.assertEquals(ordinary.getNetCombatDamage(), -2);
        loot.addStaticAbility("Mode$ AssignNoCombatDamage | ValidCard$ Card.Self");
        Assert.assertEquals(loot.getNetCombatDamage(), 0);
    }

    @Test
    public void zeroLoyaltyExceptionEndsWhenItsSourceLeaves() throws Exception {
        Fixture f = new Fixture();
        Card lurker = f.card("Name:Sanctum Lurker", "ManaCost:2 B", "Types:Creature Horror", "PT:3/2",
                "S:Mode$ IgnorePlaneswalkerZeroLoyaltyRule | ValidCard$ Planeswalker.YouCtrl");
        Card walker = f.card("Name:Jace", "ManaCost:no cost", "Types:Planeswalker Jace", "Loyalty:0");
        Card opponentWalker = Card.fromPaperCard(new PaperCard(CardRules.fromScript(List.of(
                "Name:Opponent Jace", "ManaCost:no cost", "Types:Planeswalker Jace", "Loyalty:0")),
                "TST", CardRarity.Common), f.opponent);
        f.player.getZone(ZoneType.Battlefield).add(lurker);
        f.player.getZone(ZoneType.Battlefield).add(walker);
        f.opponent.getZone(ZoneType.Battlefield).add(opponentWalker);
        Assert.assertFalse(f.zeroLoyaltyDeaths(f.player).contains(walker));
        Assert.assertTrue(f.zeroLoyaltyDeaths(f.opponent).contains(opponentWalker));
        f.player.getZone(ZoneType.Battlefield).remove(lurker);
        f.player.getZone(ZoneType.Graveyard).add(lurker);
        Assert.assertTrue(f.zeroLoyaltyDeaths(f.player).contains(walker));
    }

    @Test
    public void vengeanceCanOnlyBeCastFromGraveyard() {
        Fixture f = new Fixture();
        Card hela = f.hela();
        SpellAbility vengeance = hela.getState(CardStateName.Secondary).getSpellAbilities().get(0);
        vengeance.setActivatingPlayer(f.player);
        Assert.assertEquals(vengeance.getRestrictions().getZone(), ZoneType.Graveyard);
        for (ZoneType zone : List.of(ZoneType.Hand, ZoneType.Library, ZoneType.Exile)) {
            hela.setZone(f.player.getZone(zone));
            Assert.assertFalse(vengeance.getRestrictions().checkOtherRestrictions(hela, vengeance, f.player));
        }
        hela.setZone(f.player.getZone(ZoneType.Graveyard));
        Assert.assertTrue(vengeance.getRestrictions().checkOtherRestrictions(hela, vengeance, f.player));
        Assert.assertEquals(hela.getName(), "Hela, Mistress of Darkness");
        Assert.assertEquals(hela.getManaCost().getCMC(), 6);
    }

    @Test
    public void vengeanceExilesOnResolutionAndCounteringButNotTheCreatureSpell() {
        Fixture f = new Fixture();
        Card hela = f.hela();
        hela.setState(CardStateName.Secondary, true);
        SpellAbility vengeance = hela.getFirstSpellAbility();
        vengeance.setActivatingPlayer(f.player);
        ReplacementEffect exile = hela.getReplacementEffects().stream()
                .filter(re -> "Vengeance".equals(re.getParam("Description"))).findFirst().orElseThrow();
        Map<AbilityKey, Object> params = AbilityKey.newMap();
        params.put(AbilityKey.Affected, hela);
        params.put(AbilityKey.Origin, ZoneType.Stack);
        params.put(AbilityKey.Destination, ZoneType.Graveyard);
        params.put(AbilityKey.StackSa, vengeance);
        params.put(AbilityKey.Fizzle, false);
        Assert.assertTrue(exile.canReplace(params));
        params.put(AbilityKey.Fizzle, true);
        Assert.assertTrue(exile.canReplace(params));
        Assert.assertEquals(exile.getOverridingAbility().getParam("Destination"), "Exile");
        params.put(AbilityKey.Destination, ZoneType.Exile);
        Assert.assertFalse(exile.canReplace(params));
        params.put(AbilityKey.Destination, ZoneType.Graveyard);
        hela.setState(CardStateName.Original, true);
        params.put(AbilityKey.StackSa, hela.getFirstSpellAbility());
        Assert.assertFalse(exile.canReplace(params));
        Card copy = CardCopyService.getLKICopy(hela);
        copy.setState(CardStateName.Secondary, true);
        Assert.assertTrue(copy.getReplacementEffects().stream()
                .anyMatch(re -> "Vengeance".equals(re.getParam("Description"))));
    }

    private static final class Fixture {
        private final Game game;
        private final Player player;
        private final Player opponent;

        private Fixture() {
            GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(List.of(), rules, new Match(rules, List.of(), "Official card sync"));
            player = new Player("Controller", game, 1);
            opponent = new Player("Opponent", game, 2);
            player.setTeam(1);
            opponent.setTeam(2);
            game.getPlayers().add(player);
            game.getPlayers().add(opponent);
            game.getPhaseHandler().setPlayerTurn(player);
            game.setAge(GameStage.Play);
        }

        private Card card(String... lines) {
            return Card.fromPaperCard(new PaperCard(CardRules.fromScript(List.of(lines)),
                    "TST", CardRarity.Common), player);
        }

        private Card hela() {
            return card("Name:Hela, Mistress of Darkness", "ManaCost:4 B B",
                    "Types:Legendary Creature God Sorcerer Villain", "PT:6/6", "AlternateMode:Vengeance",
                    "ALTERNATE", "Name:Dark Tutor", "ManaCost:1 B B", "Types:Sorcery Vengeance",
                    "A:SP$ LoseLife | Defined$ You | LifeAmount$ 3");
        }

        private CardCollection zeroLoyaltyDeaths(Player controller) throws Exception {
            Method check = GameAction.class.getDeclaredMethod("handlePlaneswalkerRule", Player.class, CardCollection.class);
            check.setAccessible(true);
            CardCollection deaths = new CardCollection();
            check.invoke(game.getAction(), controller, deaths);
            return deaths;
        }
    }
}
