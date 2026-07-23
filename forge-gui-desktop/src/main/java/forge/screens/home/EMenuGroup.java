package forge.screens.home;
import forge.util.Localizer;
/**
 * Submenus each belong to a menu group, which
 * is used for several functions, such as expanding
 * and collapsing in the menu.
 * 
 * <br><br><i>(E at beginning of class name denotes an enum.)</i>
 */
public enum EMenuGroup {
    SANCTIONED ("lblSanctionedFormats", true),
    ONLINE ("lblOnlineMultiplayer", true),
    QUEST ("lblQuestMode", false),
    PUZZLE ("lblPuzzleMode", false),
    GAUNTLET ("lblGauntlets", false),
    SETTINGS ("lblGameSettings", true);

    private final String strTitle;
    private final boolean enabled;

    /** @param {@link java.lang.String} */
    EMenuGroup(final String s0, final boolean enabled0) {
        strTitle = s0;
        enabled = enabled0;
    }

    /** @return {@link java.lang.String} */
    public String getTitle() {
        final Localizer localizer = Localizer.getInstance();
        String t = localizer.getMessage(this.strTitle);
        return t;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
