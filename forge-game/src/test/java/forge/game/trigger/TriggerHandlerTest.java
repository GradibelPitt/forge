package forge.game.trigger;

import forge.game.card.Card;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.Collections;

public class TriggerHandlerTest {
    @Test
    public void staticNewGameTriggerUsesStack() {
        final Trigger trigger = TriggerType.NewGame.createTrigger(
                Collections.singletonMap("Static", "True"), new Card(1, null), false);

        AssertJUnit.assertTrue(TriggerHandler.shouldUseStack(trigger));
    }

    @Test
    public void preFirstTurnNewGameTriggerResolvesBeforePhasesBegin() {
        final java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("Static", "True");
        params.put("ResolveBeforeFirstTurn", "True");
        final Trigger trigger = TriggerType.NewGame.createTrigger(params, new Card(1, null), false);

        AssertJUnit.assertFalse(TriggerHandler.shouldUseStack(trigger));
    }

    @Test
    public void staticPhaseTriggerDoesNotUseStack() {
        final Trigger trigger = TriggerType.Phase.createTrigger(
                Collections.singletonMap("Static", "True"), new Card(1, null), false);

        AssertJUnit.assertFalse(TriggerHandler.shouldUseStack(trigger));
    }
}
