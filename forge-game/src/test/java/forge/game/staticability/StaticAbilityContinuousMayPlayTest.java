package forge.game.staticability;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardPlayOption;
import forge.game.player.Player;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.Collections;

public class StaticAbilityContinuousMayPlayTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void chosenNumberCanSupplyAnAlternativeManaCost() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "chosen alternative cost"));
        final Player player = new Player("Player", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);

        final Card effect = new Card(game.nextCardId(), game);
        effect.setName("Dynamic alternative cost effect");
        effect.setOwner(player);
        effect.setController(player, game.getNextTimestamp());
        effect.setChosenNumber(3);
        final StaticAbility mayPlay = new StaticAbility(
                "Mode$ Continuous | MayPlay$ True | "
                        + "MayPlayAltManaCost$ ChosenNumber | "
                        + "MayPlayDontGrantZonePermissions$ True | "
                        + "Affected$ Instant,Sorcery",
                effect, null);

        final Card instant = new Card(game.nextCardId(), game);
        instant.setName("Test Instant");
        instant.setOwner(player);
        instant.setController(player, game.getNextTimestamp());
        instant.addType("Instant");

        StaticAbilityContinuous.applyContinuousAbility(mayPlay,
                new CardCollection(instant), StaticAbilityLayer.RULES);

        final CardPlayOption option = instant.mayPlay(mayPlay);
        Assert.assertNotNull(option);
        Assert.assertNotNull(option.getAltManaCost());
        Assert.assertEquals(option.getAltManaCost().getTotalMana().getGenericCost(), 3);
    }
}
