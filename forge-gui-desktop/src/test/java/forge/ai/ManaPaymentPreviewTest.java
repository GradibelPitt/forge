package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.mana.ManaAtom;
import forge.card.mana.ManaCost;
import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.card.CardState;
import forge.game.event.Event;
import forge.game.mana.Mana;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.replacement.ReplacementHandler;
import forge.game.spellability.AlternativeCost;
import forge.game.spellability.OptionalCost;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.zone.ZoneType;
import forge.gamesimulationtests.util.PlayerControllerForTests;
import forge.item.PaperCard;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Field;

public class ManaPaymentPreviewTest extends AITest {
    private static final String RULE_KEY = "test:mana-preview-harmony";

    @Test
    public void floatingPlayerRulePreviewIsPureAndPaysBlueWithRed() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final Card spellCard = spellCard(payer, "Preview Converted Spell",
                "U");
        final SpellAbility spell = spellCard.getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        payer.getSpellRuleRegistry().register(RULE_KEY,
                "Card.nonColorless", "Spell", 0, "AnyType->AnyColor");

        final ManaCostBeingPaid adjusted = new ManaCostBeingPaid(
                new ManaCost("U"));

        final Card mountain = detachedCard(9_001, payer, "Preview Mountain");
        final Mana red = new Mana((byte) ManaAtom.RED, mountain, null, payer);
        payer.getManaPool().addMana(red);
        final List<Mana> poolBefore = poolSnapshot(payer);
        final byte[] matrixBefore = matrixSnapshot(payer);
        final boolean snowBefore = payer.getManaPool().isSnowForColor();
        final EventCounter events = new EventCounter();
        game.subscribeToEvents(events);
        events.gameEvents.set(0);

        spell.getPayingMana().add(red);
        spell.setXManaCostPaid(7);
        spell.setSpendPhyrexianMana(true);
        final List<Mana> payingBefore = new ArrayList<>(spell.getPayingMana());
        final Integer xBefore = spell.getXManaCostPaid();
        final int phyrexianBefore = spell.getSpendPhyrexianMana();
        final Player activatorBefore = spell.getActivatingPlayer();

        final ManaPaymentPreview.Result first = ManaPaymentPreview.preview(
                adjusted, spell, payer, false);
        final ManaPaymentPreview.Result second = ManaPaymentPreview.preview(
                adjusted, spell, payer, false);

        Assert.assertTrue(first.isAdvisoryPayable());
        Assert.assertEquals(first.getFloatingManaUsed(), 1);
        Assert.assertEquals(first.getSources().size(), 1);
        Assert.assertEquals(first.getSources().get(0).getCardId(),
                mountain.getId());
        Assert.assertTrue(first.getSources().get(0).isFloating());
        Assert.assertEquals(second.getStatus(), first.getStatus());
        Assert.assertEquals(second.getFloatingManaUsed(),
                first.getFloatingManaUsed());
        Assert.assertEquals(second.getSources().get(0).getCardId(),
                first.getSources().get(0).getCardId());
        Assert.assertEquals(adjusted.toString(), "{U}");
        assertPoolUnchanged(payer, poolBefore, matrixBefore, snowBefore);
        Assert.assertEquals(events.gameEvents.get(), 0);
        Assert.assertEquals(spell.getPayingMana(), payingBefore);
        Assert.assertEquals(spell.getXManaCostPaid(), xBefore);
        Assert.assertEquals(spell.getSpendPhyrexianMana(), phyrexianBefore);
        Assert.assertSame(spell.getActivatingPlayer(), activatorBefore);
    }

    @Test
    public void manaAbilityPreviewDoesNotMutateAbilityOrAiMemory() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final Player other = game.getPlayers().get(0);
        final SpellAbility spell = spellCard(payer, "Green Preview Spell",
                "G").getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        final Card source = manaCard(9_100, payer, "Any Preview Source",
                "AB$ Mana | Cost$ T | Produced$ G | Amount$ 1");
        final SpellAbility manaAbility = source.getManaAbilities().getFirst();
        manaAbility.setActivatingPlayer(other);
        manaAbility.getManaPart().setExpressChoice("B");
        final Player activatorBefore = manaAbility.getActivatingPlayer();
        final String choiceBefore = manaAbility.getManaPart()
                .getExpressChoice();
        final boolean tappedBefore = source.isTapped();

        final Card remembered = detachedCard(9_101, payer,
                "Remembered Preview Card");
        AiCardMemory.rememberCard(payer, remembered,
                AiCardMemory.MemorySet.PAYS_TAP_COST);
        AiCardMemory.rememberCard(payer, remembered,
                AiCardMemory.MemorySet.PAYS_SAC_COST);
        final Set<Card> tapMemoryBefore = new HashSet<>(
                AiCardMemory.getMemorySet(payer,
                        AiCardMemory.MemorySet.PAYS_TAP_COST));
        final Set<Card> sacMemoryBefore = new HashSet<>(
                AiCardMemory.getMemorySet(payer,
                        AiCardMemory.MemorySet.PAYS_SAC_COST));

        final ManaPaymentPreview.Result preview = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("G")), spell, payer,
                false, List.of(source));

        Assert.assertTrue(preview.isAdvisoryPayable());
        Assert.assertEquals(preview.getSources().size(), 1);
        Assert.assertEquals(preview.getSources().get(0).getCardId(),
                source.getId());
        Assert.assertEquals(preview.getSources().get(0).getGameTimestamp(),
                source.getGameTimestamp());
        Assert.assertFalse(preview.getSources().get(0).isFloating());
        Assert.assertEquals(preview.getCandidateCardsVisited(), 1);
        Assert.assertEquals(preview.getManaAbilityCollectionsRead(), 1);
        Assert.assertEquals(preview.getStaticModeSourcesVisited(), 0);
        Assert.assertSame(manaAbility.getActivatingPlayer(), activatorBefore);
        Assert.assertEquals(manaAbility.getManaPart().getExpressChoice(),
                choiceBefore);
        Assert.assertEquals(source.isTapped(), tappedBefore);
        Assert.assertEquals(AiCardMemory.getMemorySet(payer,
                AiCardMemory.MemorySet.PAYS_TAP_COST), tapMemoryBefore);
        Assert.assertEquals(AiCardMemory.getMemorySet(payer,
                AiCardMemory.MemorySet.PAYS_SAC_COST), sacMemoryBefore);
    }

    @Test
    public void reflectedAndDynamicManaAbilitiesAreNeverEvaluated() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final Player other = game.getPlayers().get(0);
        final SpellAbility spell = spellCard(payer,
                "Unsupported Mana Preview", "G")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);

        final List<Card> sources = List.of(
                manaCard(9_110, payer, "Reflected Source",
                        "AB$ ManaReflected | Cost$ T | ColorOrType$ Type "
                                + "| Valid$ Land.YouCtrl "
                                + "| ReflectProperty$ Produce"),
                manaCard(9_111, payer, "Present Condition Source",
                        "AB$ Mana | Cost$ T | Produced$ G "
                                + "| ConditionPresent$ Card.YouCtrl "
                                + "| ConditionCompare$ GE1"),
                manaCard(9_112, payer, "Turn Condition Source",
                        "AB$ Mana | Cost$ T | Produced$ G "
                                + "| ConditionPlayerTurn$ True"),
                manaCard(9_113, payer, "Dynamic Amount Source",
                        "AB$ Mana | Cost$ T | Produced$ G "
                                + "| Amount$ Count$Valid Card.YouCtrl"));
        final List<String> diagnostics = List.of(
                "reflected mana ability",
                "dynamic mana ability parameter",
                "dynamic mana ability parameter",
                "dynamic mana production");

        for (int i = 0; i < sources.size(); i++) {
            final Card source = sources.get(i);
            final SpellAbility mana = source.getManaAbilities().getFirst();
            mana.setActivatingPlayer(other);
            mana.getManaPart().setExpressChoice("B");
            final Player activatorBefore = mana.getActivatingPlayer();
            final String choiceBefore = mana.getManaPart().getExpressChoice();
            final boolean tappedBefore = source.isTapped();

            final ManaPaymentPreview.Result preview =
                    ManaPaymentPreview.preview(
                            new ManaCostBeingPaid(new ManaCost("G")), spell,
                            payer, false, List.of(source));

            Assert.assertEquals(preview.getStatus(),
                    ManaPaymentPreview.Status.REQUIRES_CHOICE);
            Assert.assertEquals(preview.getDiagnostic(), diagnostics.get(i));
            Assert.assertEquals(preview.getCandidateCardsVisited(), 1);
            Assert.assertEquals(preview.getStaticModeSourcesVisited(), 0);
            Assert.assertSame(mana.getActivatingPlayer(), activatorBefore);
            Assert.assertEquals(mana.getManaPart().getExpressChoice(),
                    choiceBefore);
            Assert.assertEquals(source.isTapped(), tappedBefore);
        }
    }

    @Test
    public void previewDtoCannotExposeMutableGameObjects() {
        for (final Field field
                : ManaPaymentPreview.Source.class.getDeclaredFields()) {
            Assert.assertFalse(Card.class.isAssignableFrom(field.getType()),
                    "Source DTO must not retain a live or simulated Card");
            Assert.assertFalse(SpellAbility.class.isAssignableFrom(
                    field.getType()),
                    "Source DTO must not retain a SpellAbility");
            Assert.assertFalse(Player.class.isAssignableFrom(field.getType()),
                    "Source DTO must not retain a Player");
            Assert.assertFalse(Game.class.isAssignableFrom(field.getType()),
                    "Source DTO must not retain a Game");
        }
        for (final Field field
                : ManaPaymentPreview.Result.class.getDeclaredFields()) {
            Assert.assertFalse(Card.class.isAssignableFrom(field.getType()));
            Assert.assertFalse(SpellAbility.class.isAssignableFrom(
                    field.getType()));
            Assert.assertFalse(Player.class.isAssignableFrom(field.getType()));
            Assert.assertFalse(Game.class.isAssignableFrom(field.getType()));
        }
    }

    @Test(timeOut = 5_000)
    public void candidateInputIsIdentityDeduplicatedAndResourceBounded() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final SpellAbility spell = spellCard(payer, "Bounded Preview", "G")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        final Card source = manaCard(9_125, payer, "Bounded Source",
                "AB$ Mana | Cost$ T | Produced$ G | Amount$ 1");

        final List<Card> duplicateInput = new ArrayList<>(
                Collections.nCopies(1_000, source));
        duplicateInput.add(0, null);
        duplicateInput.add(null);
        final ManaPaymentPreview.Result deduplicated =
                ManaPaymentPreview.preview(
                        new ManaCostBeingPaid(new ManaCost("G")), spell,
                        payer, false, duplicateInput);
        Assert.assertTrue(deduplicated.isAdvisoryPayable());
        Assert.assertEquals(deduplicated.getCandidateCardsVisited(), 1);
        Assert.assertEquals(deduplicated.getSources().size(), 1);

        final Iterable<Card> endlessNulls = () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Card next() {
                return null;
            }
        };
        final ManaPaymentPreview.Result bounded = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("G")), spell, payer,
                false, endlessNulls);
        Assert.assertEquals(bounded.getStatus(),
                ManaPaymentPreview.Status.RESOURCE_LIMIT);
        Assert.assertEquals(bounded.getDiagnostic(),
                "candidate source limit exceeded");
        Assert.assertEquals(bounded.getCandidateCardsVisited(), 0);
        Assert.assertFalse(source.isTapped());
    }

    @Test
    public void failedFloatingPreviewLeavesPoolAndEventsUntouched() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final SpellAbility spell = spellCard(payer, "Failed Preview", "U")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        final Card mountain = detachedCard(9_150, payer, "Failed Mountain");
        payer.getManaPool().addMana(new Mana((byte) ManaAtom.RED, mountain,
                null, payer));
        final List<Mana> poolBefore = poolSnapshot(payer);
        final byte[] matrixBefore = matrixSnapshot(payer);
        final boolean snowBefore = payer.getManaPool().isSnowForColor();
        final EventCounter events = new EventCounter();
        game.subscribeToEvents(events);
        events.gameEvents.set(0);

        final ManaPaymentPreview.Result preview = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("U")), spell, payer,
                false);

        Assert.assertEquals(preview.getStatus(),
                ManaPaymentPreview.Status.ADVISORY_UNPAYABLE);
        assertPoolUnchanged(payer, poolBefore, matrixBefore, snowBefore);
        Assert.assertEquals(events.gameEvents.get(), 0);
    }

    @Test
    public void sameCardIdDifferentLkiTimestampsDoNotCollapse() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final SpellAbility spell = spellCard(payer, "LKI Preview", "2")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        final Card earlier = detachedCard(9_160, payer, "Earlier LKI");
        earlier.setGameTimestamp(11L);
        final Card later = detachedCard(9_160, payer, "Later LKI");
        later.setGameTimestamp(12L);
        payer.getManaPool().addMana(
                new Mana((byte) ManaAtom.RED, earlier, null, payer),
                new Mana((byte) ManaAtom.RED, later, null, payer));

        final ManaPaymentPreview.Result preview = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("2")), spell, payer,
                false);

        Assert.assertTrue(preview.isAdvisoryPayable());
        Assert.assertEquals(preview.getSources().size(), 2);
        Assert.assertEquals(preview.getSources().get(0).getCardId(),
                earlier.getId());
        Assert.assertEquals(preview.getSources().get(1).getCardId(),
                later.getId());
        Assert.assertNotEquals(
                preview.getSources().get(0).getGameTimestamp(),
                preview.getSources().get(1).getGameTimestamp());
    }

    @Test
    public void globalStaticManaConversionIsNotReadByPreview() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final SpellAbility spell = spellCard(payer, "Exceptional Preview", "U")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        final StaticReadCounters reads = new StaticReadCounters();
        final Card malformed = new CountingCard(9_175, game, reads);
        malformed.setName("Malformed Conversion");
        malformed.setOwner(payer);
        malformed.setController(payer, 0L);
        malformed.addStaticAbility("Mode$ ManaConvert | ValidPlayer$ You "
                + "| ValidCard$ Card | ValidSA$ Spell | EffectZone$ All");
        payer.getZone(ZoneType.Battlefield).add(malformed);
        reads.reset();
        final List<Mana> poolBefore = poolSnapshot(payer);
        final byte[] matrixBefore = matrixSnapshot(payer);
        final boolean snowBefore = payer.getManaPool().isSnowForColor();
        final EventCounter events = new EventCounter();
        game.subscribeToEvents(events);
        events.gameEvents.set(0);

        final ManaPaymentPreview.Result preview = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("U")), spell, payer,
                false);

        Assert.assertEquals(preview.getStatus(),
                ManaPaymentPreview.Status.ADVISORY_UNPAYABLE);
        Assert.assertEquals(preview.getStaticModeSourcesVisited(), 0);
        reads.assertNoBroadRead();
        assertPoolUnchanged(payer, poolBefore, matrixBefore, snowBefore);
        Assert.assertEquals(events.gameEvents.get(), 0);
    }

    @Test
    public void offeringEmergeAndUsedToPayStateNeverChanges() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final Card selected = manaCard(9_200, payer, "Selected Creature",
                "AB$ Mana | Cost$ T | Produced$ G | Amount$ 1");
        selected.setUsedToPay(true);
        final Card forest = detachedCard(9_201, payer, "Offering Forest");
        payer.getManaPool().addMana(new Mana((byte) ManaAtom.GREEN, forest,
                null, payer));

        final SpellAbility offering = spellCard(payer, "Offering Preview",
                "G").getSpellAbilities().getFirst();
        offering.setActivatingPlayer(payer);
        offering.addOptionalCost(OptionalCost.Offering);
        offering.setSacrificedAsOffering(selected);
        final ManaPaymentPreview.Result offeringResult =
                ManaPaymentPreview.preview(
                        new ManaCostBeingPaid(new ManaCost("G")), offering,
                        payer, false);
        Assert.assertTrue(offeringResult.isAdvisoryPayable());
        Assert.assertSame(offering.getSacrificedAsOffering(), selected);
        Assert.assertTrue(selected.isUsedToPay());

        final SpellAbility emerge = spellCard(payer, "Emerge Preview",
                "G").getSpellAbilities().getFirst();
        emerge.setActivatingPlayer(payer);
        emerge.setAlternativeCost(AlternativeCost.Emerge);
        emerge.setSacrificedAsEmerge(selected);
        final ManaPaymentPreview.Result emergeResult =
                ManaPaymentPreview.preview(
                        new ManaCostBeingPaid(new ManaCost("G")), emerge,
                        payer, false);
        Assert.assertTrue(emergeResult.isAdvisoryPayable());
        Assert.assertSame(emerge.getSacrificedAsEmerge(), selected);
        Assert.assertTrue(selected.isUsedToPay());

        offering.resetSacrificedAsOffering();
        final ManaPaymentPreview.Result needsChoice =
                ManaPaymentPreview.preview(
                        new ManaCostBeingPaid(new ManaCost("G")), offering,
                        payer, false);
        Assert.assertTrue(needsChoice.requiresChoice());
        Assert.assertNull(offering.getSacrificedAsOffering());
        Assert.assertTrue(selected.isUsedToPay());
    }

    @Test
    public void complexManaActivationCostIsNeverReportedAsFreePayment() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final SpellAbility spell = spellCard(payer,
                "Complex Activation Preview", "G")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        final Card source = manaCard(9_250, payer, "Sacrifice Source",
                "AB$ Mana | Cost$ T Sac<1/CARDNAME> | Produced$ G "
                        + "| Amount$ 1");
        final boolean tappedBefore = source.isTapped();

        final ManaPaymentPreview.Result preview = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("G")), spell, payer,
                false, List.of(source));

        Assert.assertEquals(preview.getStatus(),
                ManaPaymentPreview.Status.REQUIRES_CHOICE);
        Assert.assertFalse(preview.isAdvisoryPayable());
        Assert.assertEquals(preview.getDiagnostic(),
                "unsupported mana activation cost");
        Assert.assertEquals(source.isTapped(), tappedBefore);
        Assert.assertTrue(payer.getZone(ZoneType.Battlefield)
                .contains(source));
    }

    @Test
    public void globalStaticModesAreIgnoredByAdvisorySourcePreview() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final CountingController controller = new CountingController(game,
                payer);
        payer.dangerouslySetController(controller);
        final SpellAbility spell = spellCard(payer,
                "Cost Modifier Preview", "G")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        final Card source = manaCard(9_260, payer, "Modified Mana Source",
                "AB$ Mana | Cost$ T | Produced$ G | Amount$ 1");
        final StaticReadCounters reads = new StaticReadCounters();
        final List<String> scripts = List.of(
                "Mode$ RaiseCost | Type$ Ability | ValidCard$ Card "
                        + "| Amount$ 1 | EffectZone$ All",
                "Mode$ ReduceCost | Type$ Ability | ValidCard$ Card "
                        + "| Amount$ 1 | EffectZone$ All",
                "Mode$ SetCost | Type$ Ability | ValidCard$ Card "
                        + "| Amount$ 3 | RaiseTo$ True | EffectZone$ All",
                "Mode$ ManaConvert | ValidPlayer$ You | ValidCard$ Card "
                        + "| ValidSA$ Spell | ManaConversion$ W->U "
                        + "| EffectZone$ All");
        for (int i = 0; i < scripts.size(); i++) {
            final Card modifier = new CountingCard(9_261 + i, game, reads);
            modifier.setName("Global Static Probe " + i);
            modifier.setOwner(payer);
            modifier.setController(payer, 0L);
            modifier.addStaticAbility(scripts.get(i));
            payer.getZone(ZoneType.Battlefield).add(modifier);
        }
        reads.reset();

        final ManaPaymentPreview.Result preview = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("G")), spell, payer,
                false, List.of(source));

        Assert.assertEquals(preview.getStatus(),
                ManaPaymentPreview.Status.ADVISORY_PAYABLE);
        Assert.assertEquals(preview.getCandidateCardsVisited(), 1);
        Assert.assertEquals(preview.getManaAbilityCollectionsRead(), 1);
        Assert.assertEquals(preview.getStaticModeSourcesVisited(), 0);
        Assert.assertEquals(controller.staticChoices.get(), 0);
        reads.assertNoBroadRead();
    }

    @Test
    public void produceManaReplacementIsNotGuessedByPreview() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final SpellAbility spell = spellCard(payer,
                "Replacement Preview", "G")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        final Card source = manaCard(9_270, payer,
                "Replacement Mana Source",
                "AB$ Mana | Cost$ T | Produced$ G | Amount$ 1");
        final Card replacement = detachedCard(9_271, payer,
                "Mana Replacement");
        replacement.setSVar("ProduceC",
                "DB$ ReplaceMana | ReplaceMana$ C");
        replacement.addReplacementEffect(ReplacementHandler.parseReplacement(
                "Event$ ProduceMana | ActiveZones$ Battlefield "
                        + "| ValidCard$ Card | ReplaceWith$ ProduceC",
                replacement, true));
        payer.getZone(ZoneType.Battlefield).add(replacement);

        final ManaPaymentPreview.Result preview = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("G")), spell, payer,
                false, List.of(source));

        Assert.assertEquals(preview.getStatus(),
                ManaPaymentPreview.Status.ADVISORY_PAYABLE);
        Assert.assertTrue(preview.getDiagnostic().startsWith(
                "advisory only; real payment must revalidate"));
        Assert.assertEquals(preview.getCandidateCardsVisited(), 1);
        Assert.assertEquals(preview.getStaticModeSourcesVisited(), 0);
        Assert.assertFalse(source.isTapped());
    }

    @Test
    public void alternativeHybridSnowColorlessPhyrexianAndPlayEffectAreHandled() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final SpellAbility spell = spellCard(payer, "Cost Shapes", "G")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);

        final Card green = manaCard(9_300, payer, "Green Source",
                "AB$ Mana | Cost$ T | Produced$ G | Amount$ 1");
        Assert.assertTrue(ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("2")), spell, payer,
                false, List.of(green)).getStatus()
                == ManaPaymentPreview.Status.ADVISORY_UNPAYABLE);
        green.getManaAbilities().getFirst().putParam("Amount", "2");
        Assert.assertTrue(ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("2")), spell, payer,
                false, List.of(green)).isAdvisoryPayable());
        Assert.assertTrue(ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("G/U")), spell, payer,
                false, List.of(green)).isAdvisoryPayable());
        Assert.assertTrue(ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("X")), spell, payer,
                false, List.of(green)).requiresChoice());
        final ManaCostBeingPaid selectedX = new ManaCostBeingPaid(
                new ManaCost("X"));
        selectedX.setXManaCostPaid(2, "G");
        Assert.assertTrue(ManaPaymentPreview.preview(selectedX, spell, payer,
                false, List.of(green)).isAdvisoryPayable());
        Assert.assertEquals(selectedX.toString(), "{G}{G}");

        final Card colorless = manaCard(9_301, payer, "Colorless Source",
                "AB$ Mana | Cost$ T | Produced$ C | Amount$ 1");
        Assert.assertTrue(ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("C")), spell, payer,
                false, List.of(colorless)).isAdvisoryPayable());

        final Card snow = snowManaCard(9_302, payer);
        Assert.assertTrue(ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("S")), spell, payer,
                false, List.of(snow)).isAdvisoryPayable());

        green.setTapped(true);
        colorless.setTapped(true);
        snow.setTapped(true);
        final int lifeBefore = payer.getLife();
        final ManaPaymentPreview.Result phyrexian =
                ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("G/P")), spell, payer,
                false);
        Assert.assertTrue(phyrexian.requiresChoice());
        Assert.assertEquals(phyrexian.getDiagnostic(),
                "Phyrexian or life payment requires real payment");
        Assert.assertEquals(phyrexian.getCandidateCardsVisited(), 0);
        Assert.assertEquals(phyrexian.getStaticModeSourcesVisited(), 0);
        Assert.assertEquals(payer.getLife(), lifeBefore);

        final Card alternative = manaCard(9_303, payer,
                "Alternative Source",
                "AB$ Mana | Cost$ T | Produced$ C | Amount$ 1");
        alternative.setTapped(true);
        alternative.addStaticAbility("Mode$ AlternativeCost "
                + "| ValidSA$ Activated | ValidPlayer$ You | Cost$ 0 "
                + "| EffectZone$ All");
        Assert.assertTrue(ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("C")), spell, payer,
                false, List.of(alternative)).requiresChoice());

        final Card mountain = detachedCard(9_304, payer, "Effect Mountain");
        payer.getManaPool().addMana(new Mana((byte) ManaAtom.RED, mountain,
                null, payer));
        payer.getSpellRuleRegistry().register(RULE_KEY + ":effect",
                "Card.nonColorless", "Spell", 0, "AnyType->AnyColor");
        final ManaCostBeingPaid blue = new ManaCostBeingPaid(
                new ManaCost("U"));
        Assert.assertEquals(ManaPaymentPreview.preview(blue, spell, payer,
                true).getStatus(),
                ManaPaymentPreview.Status.REQUIRES_CHOICE);
        spell.setCastFromPlayEffect(true);
        Assert.assertTrue(ManaPaymentPreview.preview(blue, spell, payer,
                true).isAdvisoryPayable());
        Assert.assertTrue(spell.isCastFromPlayEffect());
    }

    @Test
    public void repeatableZeroCostSourceIsAConservativeFalseNegative() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final SpellAbility spell = spellCard(payer,
                "Repeatable Source Preview", "2 G")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        final Card repeatable = manaCard(9_350, payer,
                "Repeatable Zero-Cost Source",
                "AB$ Mana | Cost$ 0 | Produced$ G | Amount$ 1");

        final ManaPaymentPreview.Result preview = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("2 G")), spell, payer,
                false, List.of(repeatable));

        Assert.assertEquals(preview.getStatus(),
                ManaPaymentPreview.Status.ADVISORY_UNPAYABLE);
        Assert.assertEquals(preview.getCandidateCardsVisited(), 1);
        Assert.assertEquals(preview.getManaAbilityCollectionsRead(), 1);
        Assert.assertEquals(preview.getSources().size(), 1);
        Assert.assertFalse(repeatable.isTapped());
        Assert.assertTrue(preview.getDiagnostic().contains(
                "supplied simple sources"));
    }

    @Test(timeOut = 20_000)
    public void twentyThousandNonManaCardsNeverEnumerateBroadAbilities() {
        final Game game = initAndCreateGame();
        final Player payer = game.getPlayers().get(1);
        final SpellAbility spell = spellCard(payer, "Stress Preview", "U")
                .getSpellAbilities().getFirst();
        spell.setActivatingPlayer(payer);
        final StaticReadCounters reads = new StaticReadCounters();
        for (int i = 0; i < 20_000; i++) {
            final Card card = new CountingCard(20_000 + i, game,
                    reads);
            card.setOwner(payer);
            card.setController(payer, 0L);
            card.addStaticAbility("Mode$ IgnoreLegendRule "
                    + "| EffectZone$ All");
            payer.getZone(ZoneType.Battlefield).add(card);
        }
        final Card indexedIsland = manaCard(50_001, payer,
                "Explicit Indexed Island",
                "AB$ Mana | Cost$ T | Produced$ U | Amount$ 1");
        final List<Card> indexedSources = List.of(indexedIsland);
        reads.reset();

        final ManaPaymentPreview.Result first = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("U")), spell, payer,
                false, indexedSources);
        final ManaPaymentPreview.Result second = ManaPaymentPreview.preview(
                new ManaCostBeingPaid(new ManaCost("U")), spell, payer,
                false, indexedSources);

        Assert.assertEquals(first.getStatus(),
                ManaPaymentPreview.Status.ADVISORY_PAYABLE);
        Assert.assertEquals(second.getStatus(), first.getStatus());
        Assert.assertEquals(first.getCandidateCardsVisited(), 1);
        Assert.assertEquals(second.getCandidateCardsVisited(), 1);
        Assert.assertEquals(first.getManaAbilityCollectionsRead(), 1);
        Assert.assertEquals(second.getManaAbilityCollectionsRead(), 1);
        Assert.assertEquals(first.getStaticModeSourcesVisited(), 0);
        Assert.assertEquals(second.getStaticModeSourcesVisited(), 0);
        reads.assertNoBroadRead();
    }

    private static Card spellCard(final Player owner, final String name,
            final String manaCost) {
        final PaperCard paper = new PaperCard(CardRules.fromScript(
                Arrays.asList("Name:" + name, "ManaCost:" + manaCost,
                        "Types:Sorcery",
                        "A:SP$ Draw | NumCards$ 1 "
                                + "| SpellDescription$ Draw a card.",
                        "Oracle:Draw a card.")),
                "TST", CardRarity.Common);
        final Card card = Card.fromPaperCard(paper, owner);
        owner.getZone(ZoneType.Hand).add(card);
        return card;
    }

    private static Card manaCard(final int id, final Player owner,
            final String name, final String abilityScript) {
        final Card card = detachedCard(id, owner, name);
        card.setSickness(false);
        card.addSpellAbility(AbilityFactory.getAbility(abilityScript, card));
        owner.getZone(ZoneType.Battlefield).add(card);
        return card;
    }

    private static Card snowManaCard(final int id, final Player owner) {
        final PaperCard paper = new PaperCard(CardRules.fromScript(
                Arrays.asList("Name:Snow Preview Source", "ManaCost:no cost",
                        "Types:Snow Land",
                        "A:AB$ Mana | Cost$ T | Produced$ G | Amount$ 1",
                        "Oracle:{T}: Add {G}.")),
                "TST", CardRarity.Common);
        final Card card = Card.fromPaperCard(paper, owner);
        card.setSickness(false);
        owner.getZone(ZoneType.Battlefield).add(card);
        return card;
    }

    private static Card detachedCard(final int id, final Player owner,
            final String name) {
        final Card card = new Card(id, owner.getGame());
        card.setName(name);
        card.setOwner(owner);
        card.setController(owner, 0L);
        return card;
    }

    private static List<Mana> poolSnapshot(final Player player) {
        final List<Mana> result = new ArrayList<>();
        for (final Mana mana : player.getManaPool()) {
            result.add(mana);
        }
        return result;
    }

    private static byte[] matrixSnapshot(final Player player) {
        final byte[] result = new byte[ManaAtom.MANATYPES.length];
        for (int i = 0; i < ManaAtom.MANATYPES.length; i++) {
            result[i] = player.getManaPool().getPossibleColorUses(
                    ManaAtom.MANATYPES[i]);
        }
        return result;
    }

    private static void assertPoolUnchanged(final Player player,
            final List<Mana> manaBefore, final byte[] matrixBefore,
            final boolean snowBefore) {
        final List<Mana> manaAfter = poolSnapshot(player);
        Assert.assertEquals(manaAfter.size(), manaBefore.size());
        for (int i = 0; i < manaBefore.size(); i++) {
            Assert.assertSame(manaAfter.get(i), manaBefore.get(i));
        }
        Assert.assertEquals(matrixSnapshot(player), matrixBefore);
        Assert.assertEquals(player.getManaPool().isSnowForColor(),
                snowBefore);
    }

    private static final class EventCounter {
        private final AtomicInteger gameEvents = new AtomicInteger();

        @Subscribe
        public void receive(final Event event) {
            gameEvents.incrementAndGet();
        }
    }

    private static final class CountingCard extends Card {
        private final StaticReadCounters reads;

        private CountingCard(final int id, final Game game,
                final StaticReadCounters reads) {
            super(id, game);
            this.reads = reads;
        }

        @Override
        public List<SpellAbility> getAllPossibleAbilities(final Player player,
                final boolean removeUnplayable) {
            reads.allPossibleAbilities.incrementAndGet();
            return super.getAllPossibleAbilities(player, removeUnplayable);
        }

        @Override
        public void updateStaticAbilities(final List<StaticAbility> abilities,
                final CardState state) {
            reads.staticAbilities.incrementAndGet();
            super.updateStaticAbilities(abilities, state);
        }

        @Override
        public boolean isInPlay() {
            reads.hiddenStaticAbilities.incrementAndGet();
            return super.isInPlay();
        }
    }

    private static final class StaticReadCounters {
        private final AtomicInteger allPossibleAbilities =
                new AtomicInteger();
        private final AtomicInteger staticAbilities = new AtomicInteger();
        private final AtomicInteger hiddenStaticAbilities =
                new AtomicInteger();

        private void reset() {
            allPossibleAbilities.set(0);
            staticAbilities.set(0);
            hiddenStaticAbilities.set(0);
        }

        private void assertNoBroadRead() {
            Assert.assertEquals(allPossibleAbilities.get(), 0,
                    "preview must not enumerate broad abilities");
            Assert.assertEquals(staticAbilities.get(), 0,
                    "preview must not read global static abilities");
            Assert.assertEquals(hiddenStaticAbilities.get(), 0,
                    "preview must not read hidden static abilities");
        }
    }

    private static final class CountingController
            extends PlayerControllerForTests {
        private final AtomicInteger staticChoices = new AtomicInteger();

        private CountingController(final Game game, final Player player) {
            super(game, player, new LobbyPlayerAi("Mana preview", null));
        }

        @Override
        public StaticAbility chooseSingleStaticAbility(
                final List<StaticAbility> possibleStatics) {
            staticChoices.incrementAndGet();
            return possibleStatics.get(0);
        }
    }
}
