package forge.neo.diy;

import java.util.List;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.neo.NeoSettings;
import forge.neo.ui.UiScale;
import javafx.scene.Scene;
import javafx.scene.text.Font;

/** Typography for the embedded match, including explicit Chinese glyph coverage. */
public final class DiyMatchAppearance {
    private DiyMatchAppearance() { }

    public static void install(final Scene scene) {
        scene.getStylesheets().add(DiyMatchAppearance.class.getResource("match.css").toExternalForm());
        UiScale.setOverride(NeoSettings.getScale());
        apply(scene);
        scene.heightProperty().addListener((o, oldHeight, newHeight) -> apply(scene));
    }

    public static void apply(final Scene scene) {
        final String language = FModel.getPreferences().getPref(FPref.UI_LANGUAGE);
        final String font = language.startsWith("zh") ? chineseFont() : null;
        // The embedded scene loses height to Swing's tabs; do not shrink the default text with it.
        scene.getRoot().setStyle(UiScale.rootStyle(Math.max(1080, scene.getHeight()))
                + (font == null ? "" : "-fx-font-family: '" + font + "';"));
    }

    private static String chineseFont() {
        final List<String> families = Font.getFamilies();
        for (String family : List.of("Microsoft YaHei UI", "Microsoft YaHei", "Noto Sans CJK SC",
                "Source Han Sans SC", "PingFang SC")) {
            if (families.contains(family)) { return family; }
        }
        return null;
    }
}
