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
import forge.game.card.CounterType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

/** Runs against the installed Forge classpath; no production engine changes. */
public class NozdormuShieldRegression {
    private static final CounterType SHIELD = CounterType.getType("SHIELD");
    private static final CounterType P1P1 = CounterType.getType("P1P1");

    public static void main(String[] args) throws Exception {
        Path script = Path.of(args[0]);
        // Read first so an absent card is a clear red-phase failure.
        CardRules cardRules = CardRules.fromScript(Files.readAllLines(script, StandardCharsets.UTF_8));
        String res = Path.of(args[1]).toAbsolutePath().toString();
        Files.createDirectories(Path.of(args[2]));
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", res + "/languages");
        new StaticData(new CardStorageReader(args[2], null, true), null,
                res + "/editions", args[2], res + "/blockdata", "Latest", true, true);
        GameRules rules = new GameRules(GameType.Constructed);
        Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Nozdormu shield regression"));
        Player you = new Player("You", game, 1);
        Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(you);
        game.getPlayers().add(opponent);
        you.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(you);
        game.setAge(GameStage.Play);
        Card host = CardFactory.getCard(new PaperCard(cardRules, "PH01", CardRarity.MythicRare), you, game);
        you.getZone(ZoneType.Battlefield).add(host);
        Card shielded = add(game, you, "Shielded", "Creature Human", ZoneType.Battlefield);
        shielded.setCounters(SHIELD, 2);
        Card bare = add(game, you, "Bare", "Creature Dragon", ZoneType.Battlefield);
        Card enemy = add(game, opponent, "Enemy", "Creature Human", ZoneType.Battlefield);
        enemy.setCounters(SHIELD, 1);
        Card enemyBare = add(game, opponent, "Enemy bare", "Creature Human", ZoneType.Battlefield);
        Card artifact = add(game, you, "Artifact", "Artifact", ZoneType.Battlefield);
        Card inHand = add(game, you, "In hand", "Creature Human", ZoneType.Hand);
        inHand.setCounters(SHIELD, 1);
        Trigger trigger = host.getTriggers().get(0);
        check(trigger.performTest(AbilityKey.mapFromPlayer(you)), "your end step triggers");
        check(!trigger.performTest(AbilityKey.mapFromPlayer(opponent)), "opponent end step excluded");
        check("End of Turn".equals(trigger.getParam("Phase")), "end-step timing");
        SpellAbility ability = trigger.getOverridingAbility();
        ability.setActivatingPlayer(you);
        AbilityUtils.resolve(ability);
        check(shielded.getCounters(P1P1) == 3 && shielded.getCounters(SHIELD) == 2,
                "three counters per creature, independent of shield count");
        check(bare.getCounters(P1P1) == 0 && bare.getCounters(SHIELD) == 1,
                "newly shielded creature does not grow in the same resolution");
        check(host.getCounters(P1P1) == 0 && host.getCounters(SHIELD) == 1, "host receives shield");
        check(enemy.getCounters(P1P1) == 0 && enemy.getCounters(SHIELD) == 1, "enemy unchanged");
        check(enemyBare.getCounters(SHIELD) == 0, "unshielded enemy excluded");
        check(artifact.getCounters(SHIELD) == 0 && inHand.getCounters(P1P1) == 0, "type and zone filters");
        bare.setCounters(SHIELD, 0);
        AbilityUtils.resolve(ability);
        check(shielded.getCounters(P1P1) == 6 && host.getCounters(P1P1) == 3, "later end step grows shielded creatures");
        check(bare.getCounters(P1P1) == 0 && bare.getCounters(SHIELD) == 1, "lost shield is replenished");
        System.out.println("NOZDORMU_RUNTIME_REGRESSION=OK (11 checks)");
    }

    private static Card add(Game game, Player player, String name, String types, ZoneType zone) {
        CardRules rules = CardRules.fromScript(Arrays.asList("Name:" + name, "ManaCost:1",
                "Types:" + types, "PT:1/1", "Oracle:Regression fixture."));
        Card card = CardFactory.getCard(new PaperCard(rules, "TST", CardRarity.Common), player, game);
        player.getZone(zone).add(card);
        return card;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
