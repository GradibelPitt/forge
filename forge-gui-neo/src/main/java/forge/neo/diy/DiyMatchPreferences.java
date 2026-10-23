package forge.neo.diy;

import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.neo.NeoSettings;

/** Applies only the match options explicitly changed in Neo's pause menu. */
public final class DiyMatchPreferences {
    private DiyMatchPreferences() { }

    public static void refreshSmartPass() {
        final var prefs = FModel.getPreferences();
        final boolean on = NeoSettings.getBool(NeoSettings.SMART_PASS, false);
        final int level = NeoSettings.smartPassLevel();
        prefs.setPref(FPref.YIELD_AUTO_PASS_RESPECTS_INTERRUPTS, on);
        final boolean cut = NeoSettings.endTurnInterrupts();
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_ATTACKERS, cut);
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_OPPONENT_SPELL, cut);
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_TARGETING, on && level >= 1);
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_MASS_REMOVAL, on && level >= 1);
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_TRIGGERS, on && level >= 2);
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_REVEAL, on && level >= 2);
    }

    public static void refreshAutoPass() {
        FModel.getPreferences().setPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS,
                NeoSettings.getBool(NeoSettings.AUTO_PASS, true));
    }
}
