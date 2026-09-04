package forge.game.keyword;

import forge.ImageKeys;
import forge.card.CardDb;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardStateName;
import forge.card.CardType;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class MysteryTest {
    private static final String PUBLIC_NAME = "蓝色奥秘";
    private static final String PUBLIC_ORACLE = "你的对手隐藏了一些秘密。";

    @Test
    public void mysteryIsAVisiblePermanentCardInHand() {
        final Fixture fixture = fixture();
        final Card mystery = mystery(fixture);

        fixture.controller.getZone(ZoneType.Hand).add(mystery);

        Assert.assertFalse(mystery.isFaceDown());
        Assert.assertEquals(mystery.getName(), "Test Counterspell Mystery");
        Assert.assertTrue(mystery.isEnchantment());
        Assert.assertTrue(mystery.getType().hasSubtype("Mystery"));
        Assert.assertTrue(mystery.isValid("Permanent", fixture.controller, mystery, null));
        Assert.assertEquals(mystery.getOracleText(), "Counter target instant or sorcery spell.");
    }

    @Test
    public void mysteryCastUsesTheSharedFaceDownCharacteristics() {
        final Fixture fixture = fixture();
        final Card mystery = mystery(fixture);
        fixture.controller.getZone(ZoneType.Hand).add(mystery);

        final SpellAbility castFaceDown = mystery.getSpellAbilities().stream()
                .filter(SpellAbility::isCastFaceDown)
                .findFirst()
                .orElseThrow();

        Assert.assertTrue(castFaceDown.isKeyword(Keyword.MYSTERY));
        Assert.assertEquals(castFaceDown.getPayCosts().toSimpleString(), "{1}{U}{U}");

        mystery.setSplitStateToPlayAbility(castFaceDown);

        Assert.assertTrue(mystery.isFaceDown());
        Assert.assertEquals(mystery.getCurrentStateName(), CardStateName.FaceDown);
        Assert.assertEquals(mystery.getName(), PUBLIC_NAME);
        Assert.assertEquals(mystery.getManaCost().getShortString(), "1 {U} {U}");
        Assert.assertTrue(mystery.isEnchantment());
        Assert.assertTrue(mystery.getType().hasSubtype("Mystery"));
        Assert.assertTrue(mystery.getColor().hasBlue());
        Assert.assertEquals(mystery.getOracleText(), PUBLIC_ORACLE);
        Assert.assertEquals(ImageKeys.MYSTERY_CARD_IMAGE_KEY, "c:蓝色奥秘|TOKEN_HS|[8]");
        Assert.assertEquals(mystery.getFacedownImageKey(), ImageKeys.MYSTERY_CARD_IMAGE_KEY);

        mystery.forceTurnFaceUp();
        Assert.assertEquals(mystery.getName(), "Test Counterspell Mystery");
        Assert.assertEquals(mystery.getOracleText(), "Counter target instant or sorcery spell.");
    }

    @Test
    public void mysteryPublicImageKeyUsesTheTokenHsCollectorNumber() {
        final CardDb.CardRequest request = CardDb.CardRequest.fromString(
                ImageKeys.MYSTERY_CARD_IMAGE_KEY.substring(ImageKeys.CARD_PREFIX.length()));

        Assert.assertEquals(request.cardName, PUBLIC_NAME);
        Assert.assertEquals(request.edition, "TOKEN_HS");
        Assert.assertEquals(request.collectorNumber, "8");
    }

    @Test
    public void everyBattlefieldEntryTurnsAMysteryFaceDown() {
        final Fixture fixture = fixture();
        final Card mystery = mystery(fixture);
        fixture.controller.getZone(ZoneType.Hand).add(mystery);

        final Card moved = fixture.game.getAction().moveToPlay(mystery, null, null);

        Assert.assertSame(moved, mystery);
        Assert.assertTrue(moved.isInPlay());
        Assert.assertTrue(moved.isFaceDown());
        Assert.assertEquals(moved.getName(), PUBLIC_NAME);
        Assert.assertTrue(moved.isEnchantment());
        Assert.assertTrue(moved.getType().hasSubtype("Mystery"));
        Assert.assertEquals(moved.getView().getCurrentState().getImageKey(
                List.of(fixture.controller.getView())), ImageKeys.MYSTERY_CARD_IMAGE_KEY);
        Assert.assertTrue(moved.getView().canFaceDownBeShownTo(fixture.controller.getView()));
        Assert.assertFalse(moved.getView().canFaceDownBeShownTo(fixture.opponent.getView()));
    }

    @Test
    public void mysteryCannotBeCastUsingItsFaceUpPermanentSpell() {
        final Fixture fixture = fixture();
        final Card mystery = mystery(fixture);
        fixture.controller.getZone(ZoneType.Hand).add(mystery);

        final SpellAbility faceUpSpell = mystery.getSpellAbilities().stream()
                .filter(SpellAbility::isSpell)
                .filter(spell -> !spell.isCastFaceDown())
                .findFirst()
                .orElseThrow();
        faceUpSpell.setActivatingPlayer(fixture.controller);
        fixture.game.getPhaseHandler().setPlayerTurn(fixture.controller);

        Assert.assertFalse(faceUpSpell.getRestrictions().canPlay(mystery, faceUpSpell));
    }

    @Test
    public void freeRevealIsAvailableOnlyDuringAnOpponentsTurn() {
        final Fixture fixture = fixture();
        final Card mystery = mystery(fixture);
        fixture.controller.getZone(ZoneType.Hand).add(mystery);
        fixture.game.getAction().moveToPlay(mystery, null, null);

        final SpellAbility reveal = mystery.getSpellAbilities().stream()
                .filter(SpellAbility::isMysteryUp)
                .findFirst()
                .orElseThrow();
        reveal.setActivatingPlayer(fixture.controller);

        fixture.game.getPhaseHandler().setPlayerTurn(fixture.controller);
        Assert.assertFalse(reveal.getRestrictions().checkTimingRestrictions(mystery, reveal));

        fixture.game.getPhaseHandler().setPlayerTurn(fixture.opponent);
        Assert.assertTrue(reveal.getRestrictions().checkTimingRestrictions(mystery, reveal));
        Assert.assertTrue(reveal.getPayCosts().isFree());
    }

    @Test
    public void mysteryCannotRevealWithoutALegalMysteryEffectTarget() {
        final Fixture fixture = fixture();
        final Card mystery = mystery(fixture);
        fixture.controller.getZone(ZoneType.Hand).add(mystery);
        fixture.game.getAction().moveToPlay(mystery, null, null);

        final SpellAbility reveal = mystery.getSpellAbilities().stream()
                .filter(SpellAbility::isMysteryUp)
                .findFirst()
                .orElseThrow();
        reveal.setActivatingPlayer(fixture.controller);

        Assert.assertFalse(reveal.canPlay());

        fixture.game.getStack().add(instantSpell(fixture));

        Assert.assertTrue(reveal.canPlay());
    }

    @Test
    public void targetlessMysteryCanRevealWithoutAStackTarget() {
        final Fixture fixture = fixture();
        final Card mystery = mystery(fixture,
                "DB$ Draw | NumCards$ 1 | SpellDescription$ Draw a card.",
                "Draw a card.");
        fixture.controller.getZone(ZoneType.Hand).add(mystery);
        fixture.game.getAction().moveToPlay(mystery, null, null);

        final SpellAbility reveal = mystery.getSpellAbilities().stream()
                .filter(SpellAbility::isMysteryUp)
                .findFirst()
                .orElseThrow();
        reveal.setActivatingPlayer(fixture.controller);

        Assert.assertTrue(reveal.canPlay());
    }

    @Test
    public void revealingCreatesTheMysteryEffectWithoutAnEarlySacrifice() {
        final Fixture fixture = fixture();
        final Card mystery = mystery(fixture);

        final Trigger revealTrigger = mystery.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.TurnFaceUp)
                .filter(trigger -> trigger.getKeyword() != null
                        && trigger.getKeyword().getKeyword() == Keyword.MYSTERY)
                .findFirst()
                .orElseThrow();
        final SpellAbility mysteryEffect = revealTrigger.getOverridingAbility();

        Assert.assertEquals(mysteryEffect.getApi(), ApiType.Counter);
        Assert.assertNotNull(mysteryEffect.getTargetRestrictions());
        Assert.assertEquals(mysteryEffect.getTargetRestrictions().getValidTgts(),
                new String[] { "Instant", "Sorcery" });
    }

    @Test
    public void revealedMysteryLeavesOnlyAfterItsRevealTriggerFinishes() {
        final Fixture fixture = fixture();
        final Card mystery = mystery(fixture);
        fixture.controller.getZone(ZoneType.Hand).add(mystery);
        fixture.game.getAction().moveToPlay(mystery, null, null);
        mystery.forceTurnFaceUp();

        final Trigger revealTrigger = mystery.getTriggers().stream()
                .filter(trigger -> trigger.getMode() == TriggerType.TurnFaceUp)
                .filter(trigger -> trigger.getKeyword() != null
                        && trigger.getKeyword().getKeyword() == Keyword.MYSTERY)
                .findFirst()
                .orElseThrow();
        final SpellAbility mysteryEffect = revealTrigger.ensureAbility();
        mysteryEffect.setActivatingPlayer(fixture.controller);
        final WrappedAbility pendingReveal = new WrappedAbility(revealTrigger,
                mysteryEffect, null);
        pendingReveal.setActivatingPlayer(fixture.controller);

        fixture.game.getStack().addSimultaneousStackEntry(pendingReveal);
        fixture.game.getAction().checkStateEffects(false);

        Assert.assertTrue(mystery.isInPlay());

        fixture.game.getStack().clearSimultaneousStack();
        fixture.game.getAction().checkStateEffects(false);

        Assert.assertFalse(mystery.isInPlay());
        Assert.assertTrue(fixture.controller.getZone(ZoneType.Graveyard)
                .contains(mystery));
    }

    private static Card mystery(final Fixture fixture) {
        return mystery(fixture,
                "DB$ Counter | TargetType$ Spell | ValidTgts$ Instant,Sorcery"
                        + " | TgtPrompt$ Select target instant or sorcery spell"
                        + " | SpellDescription$ Counter target instant or sorcery spell.",
                "Counter target instant or sorcery spell.");
    }

    private static Card mystery(final Fixture fixture, final String effect,
            final String oracle) {
        final CardRules rules = CardRules.fromScript(List.of(
                "Name:Test Counterspell Mystery",
                "ManaCost:2 U",
                "Types:Enchantment Mystery",
                "K:Mystery",
                "SVar:MysteryEffect:" + effect,
                "Oracle:" + oracle
        ));
        final Card card = CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Uncommon),
                fixture.controller, fixture.game);
        card.setController(fixture.controller, fixture.game.getNextTimestamp());
        return card;
    }

    private static SpellAbility instantSpell(final Fixture fixture) {
        final CardRules rules = CardRules.fromScript(List.of(
                "Name:Test Instant",
                "ManaCost:U",
                "Types:Instant",
                "A:SP$ Draw | NumCards$ 1 | SpellDescription$ Draw a card.",
                "Oracle:Draw a card."
        ));
        final Card card = CardFactory.getCard(
                new PaperCard(rules, "TST", CardRarity.Common),
                fixture.opponent, fixture.game);
        card.setController(fixture.opponent, fixture.game.getNextTimestamp());
        final SpellAbility spell = card.getSpellAbilities().stream()
                .filter(SpellAbility::isSpell)
                .findFirst()
                .orElseThrow();
        spell.setActivatingPlayer(fixture.opponent);
        return spell;
    }

    private static Fixture fixture() {
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", "../forge-gui/res/languages");
        CardType.Constant.ENCHANTMENT_TYPES.add("Mystery");
        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(List.of(), rules,
                new Match(rules, List.of(), "Mystery test"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.getPhaseHandler().setPlayerTurn(opponent);
        game.setAge(GameStage.Play);
        return new Fixture(game, controller, opponent);
    }

    private record Fixture(Game game, Player controller, Player opponent) {
    }
}
