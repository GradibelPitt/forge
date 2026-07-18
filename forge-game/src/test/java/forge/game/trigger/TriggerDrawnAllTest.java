package forge.game.trigger;

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
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class TriggerDrawnAllTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
        if (StaticData.instance() == null) {
            final String cards = Paths.get("..", "forge-gui", "res", "cardsfolder")
                    .toAbsolutePath().normalize().toString();
            final String editions = Paths.get("..", "forge-gui", "res", "editions")
                    .toAbsolutePath().normalize().toString();
            final String customEditions = Paths.get("..", "custom", "editions")
                    .toAbsolutePath().normalize().toString();
            final String blockData = Paths.get("..", "forge-gui", "res", "blockdata")
                    .toAbsolutePath().normalize().toString();
            new StaticData(new CardStorageReader(cards, null, true), null, editions,
                    customEditions, blockData, "Latest", true, true);
        }
    }

    @Test
    public void drawingSeveralCardsCreatesOneBatchTriggerWithTheActualAmount() throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "DrawnAll trigger test"));
        final Player player = new Player("Player", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);
        game.getPhaseHandler().setPlayerTurn(player);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "colorless", "符文秘银杖.txt")
                .toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "符文秘银杖");
        final Card rod = CardFactory.getCard(
                new PaperCard(cardRules, "PH01", CardRarity.Rare), player, game);
        game.getAction().moveTo(ZoneType.Battlefield, rod, null, null);
        game.getStack().clearSimultaneousStack();
        game.getTriggerHandler().resetActiveTriggers();

        for (int i = 0; i < 3; i++) {
            final Card card = new Card(game.nextCardId(), game);
            card.setName("Draw test " + i);
            card.setOwner(player);
            card.setController(player, game.getNextTimestamp());
            player.getZone(ZoneType.Library).add(card);
        }

        final CardCollection drawn = new CardCollection(player.drawCards(5));
        Assert.assertEquals(drawn.size(), 3);

        final Field entriesField = game.getStack().getClass()
                .getDeclaredField("simultaneousStackEntryList");
        entriesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<SpellAbility> entries = (List<SpellAbility>) entriesField.get(game.getStack());

        Assert.assertEquals(entries.size(), 1,
                "one drawCards call should create one DrawnAll trigger, not one trigger per card");
        final SpellAbility triggered = entries.get(0);
        Assert.assertEquals(triggered.getTrigger().getMode(), TriggerType.DrawnAll);
        Assert.assertEquals(triggered.getTriggeringObject(AbilityKey.Amount), 3);
        Assert.assertEquals(((CardCollection) triggered.getTriggeringObject(AbilityKey.Cards)).size(), 3);
        Assert.assertEquals(triggered.getTriggeringObject(AbilityKey.Player), player);
    }
}
