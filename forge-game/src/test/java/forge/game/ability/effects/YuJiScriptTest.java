package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.GamePieceType;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class YuJiScriptTest {
    public static void main(final String[] args) throws Exception {
        final YuJiScriptTest test = new YuJiScriptTest();
        test.initializeLocalization();
        test.realScriptParsesNativeChallengeAndNamedCardChains();
    }

    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
    }

    @Test
    public void realScriptParsesNativeChallengeAndNamedCardChains()
            throws Exception {
        final Path script = Paths.get("..", "custom", "cards", "multicolor",
                "于吉.txt").toAbsolutePath().normalize();
        final CardRules cardRules = new CardRules.Reader().readCard(
                Files.readAllLines(script, StandardCharsets.UTF_8), "于吉");

        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(), "Yu Ji script test"));
        final Player controller = new Player("Controller", game, 1);
        final Player challenger = new Player("Challenger", game, 2);
        final Player cursedOpponent = new Player("Cursed opponent", game, 3);
        game.getPlayers().add(controller);
        game.getPlayers().add(challenger);
        game.getPlayers().add(cursedOpponent);
        controller.setTeam(1);
        challenger.setTeam(2);
        cursedOpponent.setTeam(3);

        final Card yuJi = CardFactory.getCard(
                new PaperCard(cardRules, "BT3K", CardRarity.MythicRare),
                controller, game);
        Assert.assertEquals(yuJi.getManaCost().getCMC(), 5);
        Assert.assertEquals(yuJi.getNetPower(), 3);
        Assert.assertEquals(yuJi.getNetToughness(), 4);
        Assert.assertEquals(yuJi.getTriggers().size(), 1);
        Assert.assertEquals(yuJi.getTriggers().get(0).getParam("ValidCard"),
                "Card.!named于吉");
        Assert.assertEquals(yuJi.getTriggers().get(0)
                .getParam("ActivatorThisTurnCast"), "EQ1");

        final SpellAbility chooseName = yuJi.getTriggers().get(0).ensureAbility();
        Assert.assertEquals(chooseName.getApi(), ApiType.NameCard);
        final SpellAbility askChallenges = chooseName.getSubAbility();
        Assert.assertEquals(askChallenges.getApi(), ApiType.GenericChoice);
        Assert.assertEquals(askChallenges.getAdditionalAbilityList("Choices").size(), 2);
        Assert.assertEquals(askChallenges.getParam("Defined"),
                "Opponent.!HasCardsInCommand_Effect.namedEmblem — 于吉的缠怨_GE1");

        final SpellAbility checkChallenges = askChallenges.getSubAbility();
        Assert.assertEquals(checkChallenges.getApi(), ApiType.Branch);
        final SpellAbility revealHand = checkChallenges
                .getAdditionalAbility("TrueSubAbility");
        final SpellAbility checkNamedCard = revealHand.getSubAbility();
        Assert.assertEquals(revealHand.getApi(), ApiType.RevealHand);
        Assert.assertEquals(checkNamedCard.getApi(), ApiType.Branch);

        final SpellAbility punishChallengers = checkNamedCard
                .getAdditionalAbility("TrueSubAbility");
        final SpellAbility createEmblem = punishChallengers
                .getAdditionalAbility("RepeatSubAbility");
        Assert.assertEquals(punishChallengers.getApi(), ApiType.RepeatEach);
        Assert.assertEquals(createEmblem.getApi(), ApiType.Effect);
        Assert.assertEquals(createEmblem.getParam("Name"),
                "Emblem — 于吉的缠怨");

        final SpellAbility castNamed = punishChallengers.getSubAbility();
        Assert.assertEquals(castNamed.getApi(), ApiType.Play);
        Assert.assertTrue(castNamed.hasParam("CopyFromChosenName"));
        Assert.assertTrue(castNamed.hasParam("RememberPlayed"));
        Assert.assertFalse(castNamed.hasParam("WithoutManaCost"));

        final Card normalSpell = card(game, controller, "普通咒语");
        Assert.assertTrue(normalSpell.isValid(new String[]{"Card.!named于吉"},
                controller, yuJi, chooseName));
        Assert.assertFalse(yuJi.isValid(new String[]{"Card.!named于吉"},
                controller, yuJi, chooseName));

        askChallenges.setActivatingPlayer(controller);
        Assert.assertEquals(AbilityUtils.getDefinedPlayers(yuJi,
                askChallenges.getParam("Defined"), askChallenges).size(), 2);

        final Card curse = card(game, cursedOpponent, "Emblem — 于吉的缠怨");
        curse.setGamePieceType(GamePieceType.EFFECT);
        cursedOpponent.getZone(ZoneType.Command).add(curse);
        Assert.assertTrue(cursedOpponent.getCardsIn(ZoneType.Command)
                .contains(curse));
        Assert.assertTrue(curse.isValid(
                new String[]{"Effect.namedEmblem — 于吉的缠怨"}, controller, yuJi,
                askChallenges));
        Assert.assertTrue(cursedOpponent.hasProperty(
                "HasCardsInCommand_Effect.namedEmblem — 于吉的缠怨_GE1", controller,
                yuJi, askChallenges));
        final List<Player> eligible = AbilityUtils.getDefinedPlayers(yuJi,
                askChallenges.getParam("Defined"), askChallenges);
        Assert.assertEquals(eligible, List.of(challenger));
    }

    private static Card card(final Game game, final Player owner,
                             final String name) {
        final CardRules rules = CardRules.fromScript(Arrays.asList(
                "Name:" + name,
                "ManaCost:1",
                "Types:Effect",
                "Oracle:"));
        return CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common), owner, game);
    }
}
