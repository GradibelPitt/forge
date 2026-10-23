package forge.game;

import java.util.ArrayList;
import java.util.List;
import com.google.common.eventbus.Subscribe;
import forge.ai.LobbyPlayerAi;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.game.ability.AbilityFactory;
import forge.game.ability.effects.FlipCoinEffect;
import forge.game.ability.effects.RollDiceEffect;
import forge.game.card.Card;
import forge.game.event.*;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.item.PaperCard;
import org.testng.Assert;
import org.testng.annotations.Test;

public class NeoAnimationEventTest {
    private Game game() {
        final var players = List.of(new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("Player", null)),
                new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("Opponent", null)));
        final var rules = new GameRules(GameType.Constructed);
        return new Game(players, rules, new Match(rules, players, "Neo event tests"));
    }

    private Card card(Game game, String name) {
        return Card.fromPaperCard(new PaperCard(CardRules.fromScript(List.of("Name:" + name,
                "ManaCost:1", "Types:Instant", "Oracle:Test")), "TST", CardRarity.Common), game.getPlayers().get(0));
    }

    public static final class Events {
        final List<GameEvent> seen = new ArrayList<>();
        @Subscribe public void collect(GameEvent event) { seen.add(event); }
    }

    @Test public void diceDisplayUsesTheResolvedResults() throws Exception {
        final Game game = game();
        final Events events = new Events(); game.subscribeToEvents(events);
        final var player = game.getPlayers().get(0);
        final var ability = AbilityFactory.getAbility("DB$ RollDice | Amount$ 3 | Sides$ 6", card(game,"Dice"));
        ability.setActivatingPlayer(player);
        final var roll = RollDiceEffect.class.getDeclaredMethod("rollDiceForPlayer", SpellAbility.class,
                forge.game.player.Player.class, int.class, int.class, int.class, int.class, List.class, boolean.class);
        roll.setAccessible(true);
        final int total = (int)roll.invoke(null, ability, player, 3, 6, 0, 2, null, false);
        final var batch = events.seen.stream().filter(GameEventRollDice.class::isInstance)
                .map(GameEventRollDice.class::cast).toList();
        Assert.assertEquals(batch.size(),1);
        Assert.assertEquals(batch.get(0).results().size(),3);
        Assert.assertEquals(batch.get(0).results().stream().mapToInt(Integer::intValue).sum(),total);
        Assert.assertEquals(batch.get(0).sides(),6);
        Assert.assertTrue(batch.get(0).results().stream().allMatch(n -> n>=3 && n<=8));
        Assert.assertEquals(events.seen.stream().filter(GameEventRollDie.class::isInstance).count(),3L);
    }

    @Test public void coinDisplayUsesTheActualFaceAndPlayer() throws Exception {
        final Game game = game(); final Events events = new Events(); game.subscribeToEvents(events);
        final var player=game.getPlayers().get(0);
        final var ability=AbilityFactory.getAbility("DB$ FlipCoin | NoCall$ True",card(game,"Coin"));
        ability.setActivatingPlayer(player);
        final var flip=FlipCoinEffect.class.getDeclaredMethod("flipCoin",forge.game.player.Player.class,
                SpellAbility.class,int.class,Boolean.class,String.class);
        flip.setAccessible(true);
        for (boolean heads : new boolean[]{true,false}) {
            Assert.assertEquals(flip.invoke(null,player,ability,1,heads,""),heads);
            final var event=(GameEventFlipCoin)events.seen.stream().filter(GameEventFlipCoin.class::isInstance).reduce((a,b)->b).orElseThrow();
            Assert.assertEquals(event.heads(),heads); Assert.assertSame(event.flipper(),player.getView());
            Assert.assertFalse(event.startingToss());
        }
    }

    @Test public void counterspellViewCarriesTheTargetWithoutChangingTheTargets() {
        final Game game=game();
        final var target=AbilityFactory.getAbility("SP$ Draw | Cost$ U | NumCards$ 1",card(game,"Draw"));
        target.setActivatingPlayer(game.getPlayers().get(0));
        final var counter=AbilityFactory.getAbility("SP$ Counter | Cost$ U | ValidTgts$ Spell",card(game,"Counter"));
        counter.setActivatingPlayer(game.getPlayers().get(0)); counter.getTargets().add(target);
        final var view=new SpellAbilityStackInstance(counter).getView();
        Assert.assertTrue(view.getTargetCards().contains(target.getHostCard().getView()));
        Assert.assertTrue(counter.getTargets().getTargetCards().isEmpty());
        Assert.assertEquals(counter.getTargets().getTargetSpells(),List.of(target));
    }

    @Test public void legacyConstructorsAndVisitorsRemainUsable() {
        Assert.assertNull(new GameEventRollDie().planar());
        Assert.assertNull(new GameEventFlipCoin().flipper());
        Assert.assertNull(new GameEventRollDice(6,List.of(3)).visit(new IGameEventVisitor.Base<String>()));
        Assert.assertEquals(new GameEventRollDie(PlanarDice.Chaos).planar(),PlanarDice.Chaos);
    }
}
