package forge.gui.framework;

import forge.screens.home.EMenuGroup;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConstructedOnlyHomeModeTest {
    @Test
    public void standaloneProgressionModesAreNotRegisteredForStartup() throws IOException {
        final String documentIds = readMainSource("forge/gui/framework/EDocID.java");
        final String home = readMainSource("forge/screens/home/VHomeUI.java");
        final String control = readMainSource("forge/control/FControl.java");
        final String bazaar = readMainSource("forge/screens/bazaar/VBazaarUI.java");
        final String deckController = readMainSource("forge/screens/deckeditor/controllers/DeckController.java");
        final String model = readSiblingSource(
                "forge-gui/src/main/java/forge/model/FModel.java");
        final String achievements = readSiblingSource(
                "forge-gui/src/main/java/forge/localinstance/achievements/AchievementCollection.java");

        Assert.assertFalse(documentIds.contains("screens.home.quest"));
        Assert.assertFalse(documentIds.contains("screens.home.gauntlet"));
        Assert.assertFalse(documentIds.contains("screens.home.puzzle"));
        Assert.assertFalse(home.contains("VSubmenuQuest"));
        Assert.assertFalse(home.contains("VSubmenuGauntlet"));
        Assert.assertFalse(home.contains("VSubmenuPuzzle"));
        Assert.assertFalse(home.contains("VSubmenuTutorial"));
        Assert.assertFalse(control.contains("QuestDataIO"));
        Assert.assertFalse(control.contains("lblLoadingQuest"));
        Assert.assertFalse(bazaar.substring(
                bazaar.indexOf("public void instantiate()"),
                bazaar.indexOf("private void initializeBazaar()")).contains("FModel.getQuest()"));
        Assert.assertFalse(deckController.contains("VSubmenuGauntlet"));
        Assert.assertFalse(model.contains("new QuestAchievements()"));
        Assert.assertFalse(model.contains("new PuzzleAchievements()"));
        Assert.assertFalse(achievements.contains("getAchievements(GameType.Quest)"));
        Assert.assertFalse(achievements.contains("getAchievements(GameType.Puzzle)"));

        Assert.assertFalse(EMenuGroup.QUEST.isEnabled());
        Assert.assertFalse(EMenuGroup.PUZZLE.isEnabled());
        Assert.assertFalse(EMenuGroup.GAUNTLET.isEnabled());
    }

    @Test
    public void constructedAndOnlinePlayRemainRegistered() throws IOException {
        final String documentIds = readMainSource("forge/gui/framework/EDocID.java");
        final String home = readMainSource("forge/screens/home/VHomeUI.java");

        Assert.assertTrue(documentIds.contains("HOME_CONSTRUCTED (VSubmenuConstructed.SINGLETON_INSTANCE)"));
        Assert.assertTrue(documentIds.contains("HOME_NETWORK (VSubmenuOnlineLobby.SINGLETON_INSTANCE)"));
        Assert.assertTrue(documentIds.contains("HOME_NET_DECKS (VSubmenuOnlineDecks.SINGLETON_INSTANCE)"));
        Assert.assertTrue(home.contains("allSubmenus.add(VSubmenuConstructed.SINGLETON_INSTANCE)"));
        Assert.assertTrue(home.contains("allSubmenus.add(VSubmenuOnlineLobby.SINGLETON_INSTANCE)"));
        Assert.assertTrue(home.contains("allSubmenus.add(VSubmenuOnlineDecks.SINGLETON_INSTANCE)"));

        Assert.assertTrue(EMenuGroup.SANCTIONED.isEnabled());
        Assert.assertTrue(EMenuGroup.ONLINE.isEnabled());
    }

    private static String readMainSource(final String relativePath) throws IOException {
        return Files.readString(Path.of("src", "main", "java").resolve(relativePath));
    }

    private static String readSiblingSource(final String relativePath) throws IOException {
        return Files.readString(Path.of("..").resolve(relativePath).normalize());
    }
}
