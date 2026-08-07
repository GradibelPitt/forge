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
import forge.game.card.CardCopyService;
import forge.game.card.CardFactory;
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

public class ShadowOfDemiseTest {
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
    public void realScriptTracksSuccessiveInstantAndSorcerySpellsInHand()
            throws Exception {
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Shadow of Demise clone test"));
        final Player player = new Player("Player", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(player);
        game.getPlayers().add(opponent);
        player.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(player);
        game.setAge(GameStage.Play);

        final Path script = Paths.get("..", "custom", "cards", "multicolor",
                "殒命暗影.txt").toAbsolutePath().normalize();
        final CardRules shadowRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "殒命暗影");
        Card shadow = shadow(game, player, shadowRules);

        Assert.assertEquals(shadow.getName(), "殒命暗影");
        Assert.assertEquals(shadow.getManaCost().getCMC(), 0);
        Assert.assertTrue(shadow.getStaticAbilities().isEmpty(),
                "an empty history must leave a castable zero-mana no-op spell");

        final Card instant = spell(game, player, "First Insight", "2 U", "Instant",
                "A:SP$ Draw | NumCards$ 2 | SpellDescription$ Draw two cards.");
        resolveTrackingTrigger(shadow, instant, player);

        Assert.assertEquals(shadow.getName(), "First Insight");
        Assert.assertEquals(shadow.getManaCost(), instant.getManaCost());
        Assert.assertEquals(shadow.getFirstSpellAbility().getApi(),
                instant.getFirstSpellAbility().getApi());
        Assert.assertTrue(shadow.getStaticAbilities().isEmpty(),
                "the copied spell must use the copied card's normal cast permissions");

        final Card sorcery = spell(game, player, "Final Edict", "B B", "Sorcery",
                "A:SP$ Destroy | ValidTgts$ Creature | SpellDescription$ Destroy target creature.");
        resolveTrackingTrigger(shadow, sorcery, player);

        Assert.assertEquals(shadow.getName(), "Final Edict");
        Assert.assertEquals(shadow.getManaCost(), sorcery.getManaCost());
        Assert.assertEquals(shadow.getFirstSpellAbility().getApi(),
                sorcery.getFirstSpellAbility().getApi());
        Assert.assertTrue(shadow.getTriggers().stream().anyMatch(trigger ->
                        trigger.getMode() == TriggerType.SpellCast
                                && trigger.getActiveZone().contains(ZoneType.Hand)),
                "the retained hand trigger must continue tracking later spells");

        final Card artifact = spell(game, player, "Ignored Relic", "1", "Artifact",
                "A:SP$ Cleanup | SpellDescription$ Test spell.");
        final Trigger trigger = trackingTrigger(shadow);
        Assert.assertFalse(trigger.performTest(spellCastParams(artifact, player)),
                "non-instant and non-sorcery spells must not replace the remembered spell");
    }

    @Test
    public void emptyHistoryReturnsNoSpellAndResolvesAsNoOp() throws Exception {
        final TestContext context = context("Shadow of Demise empty history test");
        final CardRules shadowRules = shadowRules();
        final Card firstShadow = shadow(context.game, context.player, shadowRules);
        final Card secondShadow = shadow(context.game, context.player, shadowRules);

        Assert.assertFalse(trackingTrigger(secondShadow).performTest(
                        spellCastParams(firstShadow, context.player)),
                "a physical Shadow of Demise must not become the remembered spell");
        Assert.assertEquals(secondShadow.getName(), "殒命暗影");
        Assert.assertEquals(secondShadow.getFirstSpellAbility().getApi(),
                firstShadow.getFirstSpellAbility().getApi());

        final SpellAbility emptySpell = firstShadow.getFirstSpellAbility();
        emptySpell.setActivatingPlayer(context.player);
        AbilityUtils.resolve(emptySpell);

        Assert.assertEquals(firstShadow.getName(), "殒命暗影");
        Assert.assertEquals(firstShadow.getManaCost().getCMC(), 0);
    }

    @Test
    public void copiedShadowIsSkippedAndPreviousNonShadowSpellIsRetained()
            throws Exception {
        final TestContext context = context("Shadow of Demise skip self test");
        final CardRules shadowRules = shadowRules();
        final Card castShadow = shadow(context.game, context.player, shadowRules);
        final Card waitingShadow = shadow(context.game, context.player, shadowRules);
        final Card previousSpell = spell(context.game, context.player, "First Insight",
                "2 U", "Instant", "A:SP$ Cleanup | SpellDescription$ Test spell.");

        resolveTrackingTrigger(castShadow, previousSpell, context.player);
        resolveTrackingTrigger(waitingShadow, previousSpell, context.player);
        Assert.assertEquals(castShadow.getName(), "First Insight");
        Assert.assertEquals(waitingShadow.getName(), "First Insight");
        Assert.assertTrue(castShadow.isValid(
                        "Instant.printedNamed殒命暗影", context.player, waitingShadow, null),
                "the printed-name filter must survive cloning");

        Assert.assertFalse(trackingTrigger(waitingShadow).performTest(
                        spellCastParams(castShadow, context.player)),
                "casting a copied Shadow of Demise must retain the earlier non-Shadow spell");
        Assert.assertEquals(waitingShadow.getName(), previousSpell.getName());
        Assert.assertEquals(waitingShadow.getManaCost(), previousSpell.getManaCost());
        Assert.assertEquals(waitingShadow.getFirstSpellAbility().getApi(),
                previousSpell.getFirstSpellAbility().getApi());

        final SpellAbility copiedSpell = castShadow.getFirstSpellAbility();
        copiedSpell.setActivatingPlayer(context.player);
        AbilityUtils.resolve(copiedSpell);
    }

    private static void resolveTrackingTrigger(final Card shadow, final Card cast,
            final Player player) {
        final Trigger trigger = trackingTrigger(shadow);
        final Map<AbilityKey, Object> runParams = spellCastParams(cast, player);
        Assert.assertTrue(trigger.performTest(runParams));

        final SpellAbility triggered = trigger.getOverridingAbility()
                .copy(shadow, player, false, true);
        triggered.setActivatingPlayer(player);
        triggered.setTrigger(trigger);
        trigger.setTriggeringObjects(triggered, runParams);
        AbilityUtils.resolve(triggered);
    }

    private static Trigger trackingTrigger(final Card shadow) {
        return shadow.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.SpellCast)
                .findFirst().orElseThrow();
    }

    private static CardRules shadowRules() throws Exception {
        final Path script = Paths.get("..", "custom", "cards", "multicolor",
                "殒命暗影.txt").toAbsolutePath().normalize();
        return new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "殒命暗影");
    }

    private static Card shadow(final Game game, final Player owner,
            final CardRules rules) {
        final Card card = CardFactory.getCard(
                new PaperCard(rules, "PH01", CardRarity.MythicRare), owner, game);
        return game.getAction().moveTo(ZoneType.Hand, card, null, null);
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

    private static Map<AbilityKey, Object> spellCastParams(final Card card,
            final Player player) {
        final SpellAbility cast = card.getFirstSpellAbility();
        cast.setActivatingPlayer(player);
        final Map<AbilityKey, Object> params = AbilityKey.mapFromCard(card);
        params.put(AbilityKey.SpellAbility, cast);
        params.put(AbilityKey.Activator, player);
        params.put(AbilityKey.CardLKI, CardCopyService.getLKICopy(card));
        return params;
    }

    private static Card spell(final Game game, final Player owner,
            final String name, final String manaCost, final String type,
            final String ability) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:" + manaCost,
                "Types:" + type,
                ability,
                "Oracle:Test card."));
        return CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common), owner, game);
    }
}
