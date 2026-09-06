import forge.CardStorageReader;
import forge.ImageKeys;
import forge.StaticData;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Lang;
import forge.util.Localizer;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/** Resolves the real card script against a legendary nontoken target. */
public final class ShatteredReflectionsResolutionProbe {
    private static final String SPELL_NAME = "破碎映像";
    private static final String TARGET_NAME = "生命的缚誓者艾欧娜尔";

    public static void main(final String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: <custom-root> <languages-root> <empty-data-root>");
        }

        final Path custom = Path.of(args[0]).toAbsolutePath().normalize();
        final Path languages = Path.of(args[1]).toAbsolutePath().normalize();
        final Path empty = Path.of(args[2]).toAbsolutePath().normalize();

        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", languages.toString());
        ImageKeys.initializeDirs("", Collections.emptyMap(), "", "", "", "", "", "", "");
        new StaticData(
                new CardStorageReader(empty.resolve("cards").toString(), null, false),
                new CardStorageReader(custom.resolve("cards").toString(), null, false),
                empty.resolve("editions").toString(),
                custom.resolve("editions").toString(),
                empty.resolve("blockdata").toString(),
                "Latest",
                true,
                true);

        final PaperCard spellPaper = StaticData.instance().getCommonCards()
                .getUniqueByName(SPELL_NAME);
        final PaperCard targetPaper = StaticData.instance().getCommonCards()
                .getUniqueByName(TARGET_NAME);
        require(spellPaper != null, "spell is missing from the custom card database");
        require(targetPaper != null, "target is missing from the custom card database");

        final GameRules rules = new GameRules(GameType.Constructed);
        final Game game = new Game(Collections.emptyList(), rules,
                new Match(rules, Collections.emptyList(),
                        "Shattered Reflections resolution probe"));
        final Player controller = new Player("Controller", game, 1);
        final Player opponent = new Player("Opponent", game, 2);
        controller.setFirstController(new PlayerControllerAi(
                game, controller, new LobbyPlayerAi("Controller", Collections.emptySet())));
        opponent.setFirstController(new PlayerControllerAi(
                game, opponent, new LobbyPlayerAi("Opponent", Collections.emptySet())));
        game.getPlayers().add(controller);
        game.getPlayers().add(opponent);
        controller.setTeam(1);
        opponent.setTeam(2);
        game.setAge(GameStage.Play);

        final Card target = CardFactory.getCard(targetPaper, controller, game);
        game.getAction().moveTo(ZoneType.Battlefield, target, null, null);
        require(target.getType().isLegendary(), "fixture target must be legendary");
        require(!target.isToken(), "fixture target must be nontoken");

        final Card spell = CardFactory.getCard(spellPaper, controller, game);
        game.getAction().moveTo(ZoneType.Graveyard, spell, null, null);
        final SpellAbility ability = spell.getSpellAbilities().get(0);
        ability.setActivatingPlayer(controller);
        ability.getTargets().add(target);

        final int battlefieldBefore = controller.getCardsIn(ZoneType.Battlefield).size();
        final int handBefore = controller.getCardsIn(ZoneType.Hand).size();
        final int libraryBefore = controller.getCardsIn(ZoneType.Library).size();

        AbilityUtils.resolve(ability);

        require(controller.getCardsIn(ZoneType.Battlefield).size() == battlefieldBefore + 1,
                "one battlefield duplicate was not created");
        require(controller.getCardsIn(ZoneType.Hand).size() == handBefore + 1,
                "one hand duplicate was not created");
        require(controller.getCardsIn(ZoneType.Library).size() == libraryBefore + 1,
                "one library duplicate was not created");

        final List<Card> duplicates = List.of(
                onlyDuplicate(controller.getCardsIn(ZoneType.Battlefield), target),
                onlyDuplicate(controller.getCardsIn(ZoneType.Hand), target),
                onlyDuplicate(controller.getCardsIn(ZoneType.Library), target));
        for (final Card duplicate : duplicates) {
            require(!duplicate.getType().isLegendary(),
                    duplicate.getZone() + " duplicate is still legendary");
            require(!duplicate.isToken(),
                    duplicate.getZone() + " duplicate became a token instead of a conjured card");
        }
        require(!spell.getRemembered().iterator().hasNext(),
                "temporary remembered cards were not cleared");

        System.out.println("SHATTERED_REFLECTIONS_RESOLUTION=OK");
    }

    private static Card onlyDuplicate(final Iterable<Card> cards, final Card original) {
        Card found = null;
        for (final Card card : cards) {
            if (card.getId() == original.getId() || !TARGET_NAME.equals(card.getName())) {
                continue;
            }
            require(found == null, "more than one duplicate was created in " + card.getZone());
            found = card;
        }
        require(found != null, "no duplicate was created in the expected zone");
        return found;
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
