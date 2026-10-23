import forge.game.phase.PhaseType;
import forge.gamemodes.match.PriorityPass;
import forge.gamemodes.match.PriorityPass.Mode;

public final class PriorityPassProbe {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        forge.util.Localizer.getInstance().initialize("en-US", System.getProperty("forge.test.app") + "/res/languages/");
        PriorityPass pass = new PriorityPass();
        pass.start(Mode.UNTIL_RESPONSE, 4);
        check(pass.shouldYield(4, PhaseType.MAIN1), "2 must yield an empty priority window");
        pass.response();
        check(!pass.shouldYield(4, PhaseType.MAIN1), "2 must stop for a response");
        check(pass.mustStop(4, PhaseType.MAIN1), "smart pass must not swallow the response");

        pass.start(Mode.WHOLE_TURN, 4);
        pass.response();
        check(pass.shouldYield(4, PhaseType.MAIN2), "6 must ignore responses");
        check(pass.shouldYield(4, PhaseType.END_OF_TURN), "6 must include the end step");
        check(pass.shouldYield(4, PhaseType.CLEANUP), "6 must include cleanup priority");
        check(!pass.shouldYield(5, PhaseType.UPKEEP), "6 must expire on the next turn, including extra turns");

        pass.start(Mode.UNTIL_END_STEP, 5);
        pass.response();
        check(pass.shouldYield(5, PhaseType.MAIN2), "0 must continue to the end step");
        check(!pass.shouldYield(5, PhaseType.END_OF_TURN), "0 must stop at the end step");
        check(pass.mustStop(5, PhaseType.END_OF_TURN), "0 must override smart pass and phase skipping");
        pass.start(Mode.UNTIL_END_STEP, 5);
        check(!pass.shouldYield(5, PhaseType.END_OF_TURN), "0 pressed at the end step must stay here");
        check(!pass.shouldYield(5, PhaseType.CLEANUP), "a skipped end step must not yield into another turn");
        pass.start(Mode.WHOLE_TURN, 5);
        pass.start(Mode.OFF, 5);
        check(!pass.shouldYield(5, PhaseType.MAIN1), "cancel must disarm the yield");
        check(!pass.mustStop(5, PhaseType.MAIN1), "cancel must release the temporary stop");
        System.out.println("PRIORITY_PASS_MODES_OK");
    }
}
