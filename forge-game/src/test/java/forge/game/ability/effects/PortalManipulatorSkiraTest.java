package forge.game.ability.effects;

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
import forge.game.card.CardCollection;
import forge.game.card.CardFactory;
import forge.game.card.CardPlayOption;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
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
import java.util.Map;

public class PortalManipulatorSkiraTest {
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
    public void discardedBatchSnapshotsItsLeastManaValueAsTheAlternativeCost()
            throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Skira alternative cost"));
        final Player player = new Player("Player", game, 1);
        game.getPlayers().add(player);
        player.setTeam(1);
        game.getPhaseHandler().setPlayerTurn(player);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "blue",
                "传送门操控师斯奇拉.txt").toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8),
                "传送门操控师斯奇拉");
        final Card skira = CardFactory.getCard(
                new PaperCard(cardRules, "PH01", CardRarity.MythicRare), player, game);
        game.getAction().moveTo(ZoneType.Battlefield, skira, null, null);

        final Trigger discardTrigger = skira.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.DiscardedAll)
                .findFirst().orElseThrow();
        final CardCollection discarded = new CardCollection(Arrays.asList(
                card(game, player, "Five Mana Instant", "5", "Instant"),
                card(game, player, "Two Mana Sorcery", "1 U", "Sorcery")));
        final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
        runParams.put(AbilityKey.Player, player);
        runParams.put(AbilityKey.Cards, discarded);

        Assert.assertTrue(discardTrigger.performTest(runParams));
        final SpellAbility triggered = discardTrigger.getOverridingAbility()
                .copy(skira, player, false, true);
        triggered.setActivatingPlayer(player);
        triggered.setTrigger(discardTrigger);
        discardTrigger.setTriggeringObjects(triggered, runParams);
        AbilityUtils.resolve(triggered);

        final Card effect = player.getCardsIn(ZoneType.Command).stream()
                .filter(card -> card.getName().contains("传送门操控师斯奇拉"))
                .findFirst().orElseThrow();
        Assert.assertEquals(effect.getChosenNumber(), 2);

        final Card spell = card(game, player, "Spell To Cast", "4 U", "Instant");
        game.getAction().moveTo(ZoneType.Hand, spell, null, null);
        game.getAction().checkStaticAbilities();

        final CardPlayOption option = spell.mayPlay(player).stream()
                .filter(play -> play.getHost() == effect)
                .findFirst().orElseThrow();
        Assert.assertEquals(option.getAltManaCost().getTotalMana().getGenericCost(), 2);
        Assert.assertFalse(option.grantsZonePermissions());
    }

    private static Card card(final Game game, final Player player,
            final String name, final String manaCost, final String type) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:" + manaCost,
                "Types:" + type,
                "Oracle:Test card."));
        return CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common), player, game);
    }
}
