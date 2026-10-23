package forge.util;

import forge.StaticData;
import forge.card.CardEdition;
import forge.item.IPaperCard;
import forge.localinstance.properties.ForgeConstants;

import java.io.File;

public final class CardImagePolicy {
    private static final String CHINESE_FULL_SUFFIX = ".zh-CN";
    private static final String QUALITY_SUFFIX = CHINESE_FULL_SUFFIX + ".v2";

    private CardImagePolicy() { }

    public static boolean isCustom(IPaperCard card) {
        if (card == null) {
            return false;
        }
        if (card.getRules().isCustom()) {
            return true;
        }
        CardEdition edition = StaticData.instance() == null ? null
                : StaticData.instance().getEditions().get(card.getEdition());
        return edition != null && edition.getType() == CardEdition.Type.CUSTOM_SET;
    }

    public static String officialImageKey(String fullImageKey) {
        return fullImageKey + QUALITY_SUFFIX;
    }

    public static String legacyOfficialImageKey(String fullImageKey) {
        return fullImageKey + CHINESE_FULL_SUFFIX;
    }

    public static boolean prefersArtCrop(IPaperCard card) {
        // Card Studio marks crop prints with @Custom. Finished DIY scans have no artist marker.
        return isCustom(card) && !card.getArtist().isEmpty();
    }

    public static boolean isOfficialImageKey(String key) {
        return key != null && (key.endsWith(QUALITY_SUFFIX) || key.endsWith(CHINESE_FULL_SUFFIX));
    }

    public static File officialImageFile(String key) {
        return new File(ForgeConstants.CACHE_CARD_PICS_DIR, key + ".jpg");
    }
}
