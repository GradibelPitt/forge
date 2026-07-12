package forge.game.mana;

import forge.card.MagicColor;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.AbilityManaPart;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import forge.util.Lang;
import forge.util.Localizer;

public class ManaPoolPersistentManaTest {
    @BeforeClass
    public void initializeLocalizer() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void persistentManaSurvivesPhasesButNotCleanup() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Persistent mana test"));
        final Player player = new Player("Player", game, 1);
        final Card source = new Card(1, game);
        source.setOwner(player);

        final Map<String, String> params = new HashMap<>();
        params.put("Produced", "G");
        params.put("PersistentMana", "True");
        final AbilityManaPart manaAbility = new AbilityManaPart(source, params);

        player.getManaPool().addMana(new Mana(MagicColor.GREEN, source, manaAbility, player));
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, player);
        Assert.assertEquals(player.getManaPool().clearPool(true).size(), 0);
        Assert.assertEquals(player.getManaPool().totalMana(), 1);

        game.getPhaseHandler().devModeSet(PhaseType.CLEANUP, player);
        Assert.assertEquals(player.getManaPool().clearPool(true).size(), 1);
        Assert.assertTrue(player.getManaPool().isEmpty());
    }
}
