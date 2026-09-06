import forge.CardStorageReader;
import forge.ImageKeys;
import forge.StaticData;
import forge.ai.LobbyPlayerAi;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardType;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntityCounterTable;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.card.CounterType;
import forge.game.cost.CostRemoveCounter;
import forge.game.cost.PaymentDecision;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.FileSection;
import forge.util.Lang;
import forge.util.Localizer;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Runs Dragon Soul's real script and token against the installed Forge classpath. */
public class DragonSoulRuntimeProbe {
    private static final CounterType DRAGON_SOUL = CounterType.getType("Dragon Soul");
    private static CardRules dragonSoulRules;
    private static int checks;

    public static void main(String[] args) throws Exception {
        final Path app = Path.of(args[0]);
        final Path custom = Path.of(args[1]);
        final String cardCache = args[2];
        final String tokenCache = args[3];
        final Map<String, List<String>> types = FileSection.parseSections(Files.readAllLines(
                app.resolve("res/lists/TypeLists.txt"), StandardCharsets.UTF_8));
        types.forEach(CardType.Helper::parseTypes);
        CardType.Constant.LOADED.set();
        dragonSoulRules = CardRules.fromScript(Files.readAllLines(
                custom.resolve("cards/white/巨龙之魂.txt"), StandardCharsets.UTF_8));
        Lang.createInstance("en-US");
        Localizer.getInstance().initialize("en-US", app.resolve("res/languages").toString());
        ImageKeys.initializeDirs(cardCache + "/", Collections.emptyMap(), tokenCache + "/",
                tokenCache, tokenCache, tokenCache, tokenCache, tokenCache, tokenCache);
        final StaticData data = new StaticData(
                new CardStorageReader(app.resolve("res/cardsfolder").toString(), null, false),
                new CardStorageReader(app.resolve("res/tokenscripts").toString(), null, false),
                new CardStorageReader(custom.resolve("cards").toString(), null, false),
                new CardStorageReader(custom.resolve("tokens").toString(), null, false),
                app.resolve("res/editions").toString(), custom.resolve("editions").toString(),
                app.resolve("res/blockdata").toString(), app.resolve("res/setlookup").toString(),
                "Latest", true, true, true, true);
        checkInstalledCardImage(data);

        final Fixture fixture = new Fixture();
        fixture.checkTriggerFilteringAndResolution();
        fixture.checkCounterPaymentAndPlainToken();
        System.out.println("DRAGON_SOUL_RUNTIME_OK=" + checks);
    }

    private static void checkInstalledCardImage(StaticData data) throws Exception {
        final PaperCard card = data.getCommonCards().getCard("巨龙之魂", "PH01");
        check(card != null, "Dragon Soul must be registered as PH01 #190");
        check("Tyler Walpole".equals(card.getArtist()), "Dragon Soul must retain its artist credit");
        final String cropKey = card.getCardImageKey().replace(".full", ".artcrop");
        final File imageFile = ImageKeys.getImageFile(cropKey);
        check(card.hasImage() && imageFile != null,
                "Dragon Soul's actual PH01 image key must resolve in the installed cache");
        final var image = ImageIO.read(imageFile);
        check(image != null, "Dragon Soul's installed art must decode");
        check(image.getWidth() == 3000 && image.getHeight() == 2190,
                "Dragon Soul's installed art must retain its verified crop dimensions");
    }

    private static final class Fixture {
        private final Game game;
        private final Player you;
        private final Player opponent;
        private final Card host;
        private final Trigger trigger;
        private final SpellAbility activated;

        private Fixture() {
            final GameRules rules = new GameRules(GameType.Constructed);
            final List<RegisteredPlayer> players = Arrays.asList(
                    new RegisteredPlayer(new Deck()).setPlayer(
                            new LobbyPlayerAi("You", Collections.emptySet())),
                    new RegisteredPlayer(new Deck()).setPlayer(
                            new LobbyPlayerAi("Opponent", Collections.emptySet())));
            game = new Match(rules, players, "Dragon Soul runtime probe").createGame();
            you = game.getPlayers().get(0);
            opponent = game.getPlayers().get(1);
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, you);
            game.setAge(GameStage.Play);

            host = CardFactory.getCard(
                    new PaperCard(dragonSoulRules, "PH01", CardRarity.MythicRare), you, game);
            you.getZone(ZoneType.Battlefield).add(host);
            trigger = host.getTriggers().get(0);
            activated = host.getSpellAbilities().stream()
                    .filter(SpellAbility::isActivatedAbility)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Dragon Soul has no activated ability"));
            activated.setActivatingPlayer(you);
        }

        private void checkTriggerFilteringAndResolution() {
            check(!matchesCast(you, "Human spell", "Creature Human", ZoneType.Hand),
                    "a non-Dragon cast from your hand must not trigger");
            check(!matchesCast(you, "Grave Dragon", "Creature Dragon", ZoneType.Graveyard),
                    "a Dragon cast from your graveyard must not trigger");
            check(!matchesCast(opponent, "Enemy Dragon", "Creature Dragon", ZoneType.Hand),
                    "an opponent's Dragon must not trigger");
            check(!matchesUncastDragon(), "a Dragon merely present on the battlefield must not trigger");

            final Map<AbilityKey, Object> creatureDragon = castParams(
                    you, "Hand Dragon", "Creature Dragon", ZoneType.Hand);
            check(trigger.performTest(creatureDragon), "your Dragon cast from your hand must trigger");
            resolveTrigger(creatureDragon);
            check(host.getCounters(DRAGON_SOUL) == 1,
                    "the first matching cast must add exactly one Dragon Soul counter");

            final Map<AbilityKey, Object> kindredDragon = castParams(
                    you, "Kindred Dragon", "Kindred Artifact Dragon", ZoneType.Hand);
            check(trigger.performTest(kindredDragon),
                    "a noncreature Dragon spell cast from your hand must also trigger");
            resolveTrigger(kindredDragon);
            check(host.getCounters(DRAGON_SOUL) == 2,
                    "the second matching cast must add exactly one more counter");
        }

        private void checkCounterPaymentAndPlainToken() {
            check(!activated.getPayCosts().canPay(activated, you, false),
                    "two Dragon Soul counters must not pay the ability cost");
            host.setCounters(DRAGON_SOUL, 6);
            check(activated.getPayCosts().canPay(activated, you, false),
                    "three or more Dragon Soul counters must pay the ability cost");

            payAndResolve();
            check(host.getCounters(DRAGON_SOUL) == 3,
                    "one activation must remove exactly three Dragon Soul counters");
            assertTokens(1);

            payAndResolve();
            check(host.getCounters(DRAGON_SOUL) == 0,
                    "six Dragon Soul counters must support exactly two activations");
            assertTokens(2);
            assertTokenImage();
            check(!activated.getPayCosts().canPay(activated, you, false),
                    "the ability must not be payable after all six counters are spent");
        }

        private boolean matchesCast(Player caster, String name, String type, ZoneType origin) {
            return trigger.performTest(castParams(caster, name, type, origin));
        }

        private boolean matchesUncastDragon() {
            final Card card = make(opponent, "Uncast Dragon", "Creature Dragon", ZoneType.Battlefield);
            final SpellAbility spell = card.getFirstSpellAbility();
            spell.setActivatingPlayer(you);
            final Map<AbilityKey, Object> params = AbilityKey.mapFromCard(card);
            params.put(AbilityKey.SpellAbility, spell);
            params.put(AbilityKey.Activator, you);
            return trigger.performTest(params);
        }

        private Map<AbilityKey, Object> castParams(Player caster, String name, String type,
                                                   ZoneType origin) {
            final Card card = make(caster, name, type, origin);
            final SpellAbility spell = card.getFirstSpellAbility();
            spell.setActivatingPlayer(caster);
            card.setCastFrom(caster.getZone(origin));
            card.setCastSA(spell);
            final Map<AbilityKey, Object> params = AbilityKey.mapFromCard(card);
            params.put(AbilityKey.SpellAbility, spell);
            params.put(AbilityKey.Activator, caster);
            params.put(AbilityKey.CardLKI, card);
            return params;
        }

        private Card make(Player owner, String name, String type, ZoneType zone) {
            final CardRules rules = CardRules.fromScript(Arrays.asList(
                    "Name:" + name,
                    "ManaCost:1",
                    "Types:" + type,
                    "PT:2/2",
                    "Oracle:Runtime fixture."));
            final Card card = CardFactory.getCard(
                    new PaperCard(rules, "TST", CardRarity.Common), owner, game);
            owner.getZone(zone).add(card);
            card.setController(owner, game.getNextTimestamp());
            return card;
        }

        private void resolveTrigger(Map<AbilityKey, Object> params) {
            check(trigger.requirementsCheck(game), "the battlefield trigger requirements must be met");
            final SpellAbility ability = trigger.getOverridingAbility()
                    .copy(host, you, false, true);
            ability.setActivatingPlayer(you);
            ability.setTrigger(trigger);
            trigger.setTriggeringObjects(ability, params);
            AbilityUtils.resolve(ability);
        }

        private void payAndResolve() {
            final CostRemoveCounter remove = activated.getPayCosts().getCostParts().stream()
                    .filter(CostRemoveCounter.class::isInstance)
                    .map(CostRemoveCounter.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("activated ability has no counter-removal cost"));
            final GameEntityCounterTable counters = new GameEntityCounterTable();
            counters.put(null, host, DRAGON_SOUL, 3);
            check(remove.payAsDecided(you, PaymentDecision.counters(counters), activated, false),
                    "the chosen counter payment must succeed");
            AbilityUtils.resolve(activated);
        }

        private void assertTokens(int expected) {
            final List<Card> tokens = you.getCardsIn(ZoneType.Battlefield).stream()
                    .filter(Card::isToken)
                    .toList();
            check(tokens.size() == expected, "each activation must create exactly one token");
            for (Card token : tokens) {
                check(token.getController().equals(you), "you must control the created token");
                check(token.isColorless(), "the unspecified Dragon token must be colorless");
                check(token.getType().isCreature() && token.getType().hasSubtype("Dragon"),
                        "the token must be a Dragon creature");
                check(token.getNetPower() == 5 && token.getNetToughness() == 5,
                        "the Dragon token must be exactly 5/5");
                check(token.getKeywords().isEmpty(),
                        "the Dragon token must not gain an unrequested keyword");
            }
        }

        private void assertTokenImage() {
            final Card token = you.getCardsIn(ZoneType.Battlefield).stream()
                    .filter(Card::isToken)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the created Dragon token is missing"));
            check(token.getImageKey().startsWith(ImageKeys.getTokenKey("c_5_5_dragon")),
                    "the created Dragon token must use the dedicated c_5_5_dragon image family");
            final File imageFile = ImageKeys.getImageFile(token.getImageKey());
            check(imageFile != null, "the Dragon token's actual image key must resolve in cache");
            try {
                final var image = ImageIO.read(imageFile);
                check(image != null, "the installed Dragon token art must decode");
                check(image.getWidth() == 3000 && image.getHeight() == 2100,
                        "the installed Dragon token art must retain its verified crop dimensions");
            } catch (Exception exception) {
                throw new AssertionError("the installed Dragon token art could not be read", exception);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
