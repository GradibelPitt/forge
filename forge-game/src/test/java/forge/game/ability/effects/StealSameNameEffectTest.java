package forge.game.ability.effects;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class StealSameNameEffectTest {
    @BeforeClass
    public void initializeLocalization() {
        Lang.createInstance("en-US");
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    private static Card named(final int id, final String name) {
        final Card card = new Card(id, null);
        card.setName(name);
        return card;
    }

    @Test
    public void selectsBattlefieldBeforeEveryOtherZone() {
        final Map<ZoneType, List<Card>> cardsByZone = new EnumMap<>(ZoneType.class);
        final Card battlefield = named(1, "Stolen Spell");
        cardsByZone.put(ZoneType.Battlefield, List.of(battlefield));
        cardsByZone.put(ZoneType.Hand, List.of(named(2, "Stolen Spell")));
        cardsByZone.put(ZoneType.Library, List.of(named(3, "Stolen Spell")));
        cardsByZone.put(ZoneType.Graveyard, List.of(named(4, "Stolen Spell")));

        final StealSameNameEffect.Selection selection =
                StealSameNameEffect.findFirstMatchingZone(cardsByZone, "Stolen Spell");

        Assert.assertEquals(selection.zone(), ZoneType.Battlefield);
        Assert.assertSame(selection.card(), battlefield);
    }

    @Test
    public void fallsBackFromHandToLibraryThenGraveyard() {
        final Map<ZoneType, List<Card>> cardsByZone = new EnumMap<>(ZoneType.class);
        final Card library = named(3, "Stolen Spell");
        final Card graveyard = named(4, "Stolen Spell");
        cardsByZone.put(ZoneType.Hand, List.of(named(2, "Other Card")));
        cardsByZone.put(ZoneType.Library, List.of(library));
        cardsByZone.put(ZoneType.Graveyard, List.of(graveyard));

        final StealSameNameEffect.Selection librarySelection =
                StealSameNameEffect.findFirstMatchingZone(cardsByZone, "Stolen Spell");
        Assert.assertEquals(librarySelection.zone(), ZoneType.Library);
        Assert.assertSame(librarySelection.card(), library);

        cardsByZone.remove(ZoneType.Library);
        final StealSameNameEffect.Selection graveyardSelection =
                StealSameNameEffect.findFirstMatchingZone(cardsByZone, "Stolen Spell");
        Assert.assertEquals(graveyardSelection.zone(), ZoneType.Graveyard);
        Assert.assertSame(graveyardSelection.card(), graveyard);
    }

    @Test
    public void ignoresDifferentNamesAndReportsNoSelection() {
        final Map<ZoneType, List<Card>> cardsByZone = new EnumMap<>(ZoneType.class);
        cardsByZone.put(ZoneType.Battlefield, List.of(named(1, "Different Spell")));
        cardsByZone.put(ZoneType.Hand, List.of(named(2, "Also Different")));

        Assert.assertNull(StealSameNameEffect.findFirstMatchingZone(cardsByZone, "Stolen Spell"));
    }

    @Test
    public void resolveGainsControlOfTheBattlefieldMatchWithoutMovingIt() {
        final Fixture fixture = new Fixture("StealSameName battlefield");
        final Card battlefield = fixture.card(
                fixture.opponent, "Stolen Spell", ZoneType.Battlefield);
        fixture.card(fixture.opponent, "Stolen Spell", ZoneType.Hand);

        new StealSameNameEffect().resolve(fixture.ability("Stolen Spell"));

        Assert.assertEquals(battlefield.getController(), fixture.activator);
        Assert.assertEquals(battlefield.getOwner(), fixture.opponent);
        Assert.assertSame(battlefield.getZone(),
                fixture.activator.getZone(ZoneType.Battlefield));
        Assert.assertTrue(fixture.activator.getCardsIn(ZoneType.Hand).isEmpty());
    }

    @Test
    public void resolveMovesTheExistingHiddenCardAndTransfersOwnership() {
        final Fixture fixture = new Fixture("StealSameName library");
        final Card library = fixture.card(
                fixture.opponent, "Stolen Spell", ZoneType.Library);
        fixture.card(fixture.opponent, "Stolen Spell", ZoneType.Graveyard);

        new StealSameNameEffect().resolve(fixture.ability("Stolen Spell"));

        Assert.assertTrue(fixture.opponent.getCardsIn(ZoneType.Library).isEmpty());
        Assert.assertEquals(fixture.opponent.getCardsIn(ZoneType.Graveyard).size(), 1);
        Assert.assertEquals(fixture.activator.getCardsIn(ZoneType.Hand).size(), 1);
        final Card stolen = fixture.activator.getCardsIn(ZoneType.Hand).get(0);
        Assert.assertEquals(stolen.getId(), library.getId(),
                "the API must move the existing card rather than make a copy");
        Assert.assertEquals(stolen.getOwner(), fixture.activator);
        Assert.assertEquals(stolen.getController(), fixture.activator);
    }

    @Test
    public void repeatedResolutionExhaustsSameNameCardsWithoutControllerUi() {
        final Fixture fixture = new Fixture("StealSameName repeated");
        final int matchingCards = 64;
        for (int i = 0; i < matchingCards; i++) {
            fixture.card(fixture.opponent, "Stolen Spell", ZoneType.Library);
        }
        final SpellAbility ability = fixture.ability("Stolen Spell");
        final StealSameNameEffect effect = new StealSameNameEffect();

        for (int i = 0; i < matchingCards + 16; i++) {
            effect.resolve(ability);
        }

        Assert.assertTrue(fixture.opponent.getCardsIn(ZoneType.Library).isEmpty());
        Assert.assertEquals(
                fixture.activator.getCardsIn(ZoneType.Hand).size(), matchingCards);
    }

    private static final class Fixture {
        private final Game game;
        private final Player activator;
        private final Player opponent;

        private Fixture(final String description) {
            final GameRules rules = new GameRules(GameType.Constructed);
            game = new Game(List.of(), rules,
                    new Match(rules, List.of(), description));
            activator = new Player("Activator", game, 1);
            opponent = new Player("Opponent", game, 2);
            game.getPlayers().add(activator);
            game.getPlayers().add(opponent);
            activator.setTeam(1);
            opponent.setTeam(2);
            game.getPhaseHandler().setPlayerTurn(activator);
            game.setAge(GameStage.Play);
        }

        private Card card(final Player owner, final String name,
                final ZoneType zone) {
            final Card card = Card.fromPaperCard(paper(name), owner);
            card.setController(owner, game.getNextTimestamp());
            owner.getZone(zone).add(card);
            return card;
        }

        private SpellAbility ability(final String triggeringName) {
            final Card host = card(
                    activator, "Jealous Reaper", ZoneType.Battlefield);
            final Card triggeringCard = Card.fromPaperCard(
                    paper(triggeringName), activator);
            triggeringCard.setController(
                    activator, game.getNextTimestamp());
            final SpellAbility ability = AbilityFactory.getAbility(
                    "DB$ StealSameName | ValidTgts$ Opponent", host);
            ability.setActivatingPlayer(activator);
            ability.getTargets().add(opponent);
            ability.setTriggeringObject(AbilityKey.Card, triggeringCard);
            return ability;
        }

        private static PaperCard paper(final String name) {
            return new PaperCard(CardRules.fromScript(Arrays.asList(
                    "Name:" + name,
                    "ManaCost:1",
                    "Types:Artifact",
                    "Oracle:Test card."
            )), "TST", CardRarity.Common);
        }
    }
}
