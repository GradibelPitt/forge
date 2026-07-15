package forge.gamemodes.match;

import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.util.Lang;
import forge.util.Localizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;

class HostedMatchTest {
    @BeforeAll
    static void initializeLocalizer() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    void matchConfigurationDisablesExperimentalSnapshotRestore() {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Hosted match test"));
        game.EXPERIMENTAL_RESTORE_SNAPSHOT = true;

        HostedMatch.disableExperimentalSnapshotRestore(game);

        assertFalse(game.EXPERIMENTAL_RESTORE_SNAPSHOT);
    }
}
