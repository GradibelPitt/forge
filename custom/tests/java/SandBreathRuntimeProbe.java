import forge.CardStorageReader;
import forge.StaticData;
import forge.ai.LobbyPlayerAi;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardType;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.AbilityUtils;
import forge.game.card.*;
import forge.game.cost.CostBehold;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import forge.util.FileSection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Run against the installed Forge classpath; args: app directory, custom source directory. */
public class SandBreathRuntimeProbe {
    private static CardRules sandBreath;
    private static int passed;

    public static void main(String[] args) throws Exception {
        Path app = Paths.get(args[0]);
        Path custom = Paths.get(args[1]);
        Map<String, List<String>> types = FileSection.parseSections(Files.readAllLines(
                app.resolve("res/lists/TypeLists.txt"), StandardCharsets.UTF_8));
        types.forEach(CardType.Helper::parseTypes);
        CardType.Constant.LOADED.set();
        sandBreath = new CardRules.Reader().readCard(Files.readAllLines(
                custom.resolve("cards/white/沙尘吐息.txt"), StandardCharsets.UTF_8), "沙尘吐息");
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", app.resolve("res/languages").toString());
        new StaticData(
                new CardStorageReader(app.resolve("res/cardsfolder").toString(), null, false),
                new CardStorageReader(app.resolve("res/tokenscripts").toString(), null, false),
                new CardStorageReader(custom.resolve("cards").toString(), null, false),
                new CardStorageReader(custom.resolve("tokens").toString(), null, false),
                app.resolve("res/editions").toString(), custom.resolve("editions").toString(),
                app.resolve("res/blockdata").toString(), app.resolve("res/setlookup").toString(),
                "Latest", true, true, true, true);
        scenario(false, false, false);
        scenario(true, false, false);
        scenario(false, true, false);
        scenario(true, true, false);
        scenario(false, false, true);
        System.out.println("SAND_BREATH_RUNTIME_OK=" + passed);
    }

    private static Card make(Game game, Player owner, String name, String type, ZoneType zone) {
        CardRules rules = new CardRules.Reader().readCard(Arrays.asList(
                "Name:" + name, "ManaCost:0", "Types:" + type, "PT:2/2", "Oracle:Test fixture."), name);
        Card card = CardFactory.getCard(new PaperCard(rules, "PH01", CardRarity.Common), owner, game);
        owner.getZone(zone).add(card);
        card.setController(owner, game.getNextTimestamp());
        return card;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void scenario(boolean paid, boolean opposingTarget, boolean dragonArrivesLate) {
        GameRules rules = new GameRules(GameType.Constructed);
        List<RegisteredPlayer> players = Arrays.asList(
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("Caster", Collections.emptySet())),
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("Opponent", Collections.emptySet())));
        Game game = new Match(rules, players, "Sand Breath probe").createGame();
        Player caster = game.getPlayers().get(0), opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, caster);
        game.setAge(GameStage.Play);
        Card target = make(game, opposingTarget ? opponent : caster, "Target", "Creature Bear", ZoneType.Battlefield);
        Card spell = CardFactory.getCard(new PaperCard(sandBreath, "PH01", CardRarity.Common), caster, game);
        caster.getZone(ZoneType.Hand).add(spell);
        SpellAbility ability = spell.getFirstSpellAbility();
        ability.setActivatingPlayer(caster);
        check(ability.canTarget(target), "Any player's creature must be a legal target");
        Card land = make(game, caster, "Land", "Land", ZoneType.Battlefield);
        check(!ability.canTarget(land), "Noncreatures must not be legal targets");
        OptionalCostValue choice = GameActionUtil.getOptionalCostValues(ability).stream()
                .filter(value -> value.getType() == OptionalCost.Generic).findFirst()
                .orElseThrow(() -> new AssertionError("The real casting path must offer Behold"));
        CostBehold behold = (CostBehold) choice.getCost().getCostParts().stream()
                .filter(part -> part instanceof CostBehold).findFirst()
                .orElseThrow(() -> new AssertionError("The optional cost must actually be Behold"));
        check(!behold.canPay(ability, caster, false), "No Dragon cannot pay Behold");
        Card dragon = make(game, caster, "Dragon", "Creature Dragon", ZoneType.Hand);
        check(behold.canPay(ability, caster, false), "A Dragon in hand can pay Behold");
        caster.getZone(ZoneType.Hand).remove(dragon);
        caster.getZone(ZoneType.Battlefield).add(dragon);
        check(behold.canPay(ability, caster, false), "A controlled Dragon can pay Behold");
        caster.getZone(ZoneType.Battlefield).remove(dragon);
        caster.getZone(ZoneType.Graveyard).add(dragon);
        check(!behold.canPay(ability, caster, false), "A Dragon in the graveyard cannot pay Behold");
        if (paid) ability = GameActionUtil.addOptionalCosts(ability, Collections.singletonList(choice));
        ability.getTargets().add(target);
        if (dragonArrivesLate) make(game, caster, "Late Dragon", "Creature Dragon", ZoneType.Battlefield);
        AbilityUtils.resolve(ability);
        check(target.getCounters(CounterType.getType("P1P1")) == 1, "Always add exactly one +1/+1 counter");
        check(target.getCounters(CounterType.getType("SHIELD")) == (paid ? 1 : 0),
                "The additional shield must depend only on the casting-time Behold choice");
        passed++;
    }
}
