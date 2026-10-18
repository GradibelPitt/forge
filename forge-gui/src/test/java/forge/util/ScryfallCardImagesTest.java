package forge.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScryfallCardImagesTest {
    private static final String PRINT = "tst/1/en";
    private static final String ORACLE = "00000000-0000-0000-0000-000000000001";
    private static final String CDN = "https://cards.scryfall.io/large/front/";

    private static JsonObject card(String language, String label) {
        JsonObject card = new JsonObject();
        card.addProperty("name", "Test Card");
        card.addProperty("oracle_id", ORACLE);
        card.addProperty("lang", language);
        card.addProperty("image_status", "highres_scan");
        JsonObject images = new JsonObject();
        images.addProperty("large", CDN + label + ".jpg");
        images.addProperty("art_crop", "https://cards.scryfall.io/art_crop/" + label + ".jpg");
        card.add("image_uris", images);
        return card;
    }

    private static JsonObject page(JsonObject... cards) {
        JsonObject page = new JsonObject();
        JsonArray data = new JsonArray();
        for (JsonObject card : cards) { data.add(card); }
        page.add("data", data);
        page.addProperty("has_more", false);
        return page;
    }

    private static class Fixture implements ScryfallCardImages.JsonSource {
        final Map<String, JsonObject> replies = new HashMap<>();
        final List<String> requested = new ArrayList<>();
        String fail;

        Fixture() {
            replies.put(PRINT, card("en", "english"));
        }

        public JsonObject read(String url) throws IOException {
            String key = URLDecoder.decode(url.substring(ScryfallCardImages.API.length()), StandardCharsets.UTF_8);
            if (key.startsWith("search?") && key.contains("q=")) {
                Assertions.assertTrue(key.contains("include_multilingual=true"));
                Assertions.assertTrue(key.contains("unique=prints"));
                key = key.substring(key.indexOf("q=") + 2);
            }
            requested.add(key);
            if (key.equals(fail)) { throw new IOException("HTTP 429"); }
            return replies.get(key);
        }

        void search(String language, JsonObject... cards) {
            replies.put("oracleid:" + ORACLE + " lang:" + language, page(cards));
        }

        String resolve() throws IOException {
            return new ScryfallCardImages(this).resolve("Test Card", PRINT, "front");
        }
    }

    @Test
    public void exactSimplifiedFullScanWins() throws Exception {
        Fixture fixture = new Fixture();
        fixture.replies.put("tst/1/zhs", card("zhs", "simplified"));
        Assertions.assertEquals(fixture.resolve(), CDN + "simplified.jpg");
        Assertions.assertEquals(fixture.requested, List.of(PRINT, "tst/1/zhs"));
    }

    @Test
    public void otherSimplifiedPrintingWinsBeforeTraditionalOrEnglish() throws Exception {
        Fixture fixture = new Fixture();
        fixture.search("zhs", card("zhs", "other-set"));
        fixture.replies.put("tst/1/zht", card("zht", "traditional"));
        Assertions.assertEquals(fixture.resolve(), CDN + "other-set.jpg");
        Assertions.assertFalse(fixture.requested.contains("tst/1/zht"));
    }

    @Test
    public void traditionalWinsWhenSimplifiedDoesNotExist() throws Exception {
        Fixture fixture = new Fixture();
        fixture.search("zht", card("zht", "traditional"));
        Assertions.assertEquals(fixture.resolve(), CDN + "traditional.jpg");
    }

    @Test
    public void englishIsUsedOnlyAfterBothChineseSearches() throws Exception {
        Fixture fixture = new Fixture();
        Assertions.assertEquals(fixture.resolve(), CDN + "english.jpg");
        Assertions.assertEquals(fixture.requested.size(), 5);
        Assertions.assertTrue(fixture.requested.get(2).endsWith("lang:zhs"));
        Assertions.assertTrue(fixture.requested.get(4).endsWith("lang:zht"));
    }

    @Test
    public void chinesePlaceholderIsNotAChineseScan() throws Exception {
        Fixture fixture = new Fixture();
        JsonObject placeholder = card("zhs", "placeholder");
        placeholder.addProperty("image_status", "placeholder");
        fixture.replies.put("tst/1/zhs", placeholder);
        fixture.search("zhs", placeholder, card("zhs", "real"));
        Assertions.assertEquals(fixture.resolve(), CDN + "real.jpg");
    }

    @Test
    public void wrongLanguageAndWrongIdentityCannotMasqueradeAsChinese() throws Exception {
        Fixture fixture = new Fixture();
        fixture.replies.put("tst/1/zhs", card("en", "english-redirect"));
        JsonObject wrongCard = card("zhs", "different-card");
        wrongCard.addProperty("oracle_id", "different");
        fixture.search("zhs", wrongCard, card("en", "wrong-language"));
        Assertions.assertEquals(fixture.resolve(), CDN + "english.jpg");
    }

    @Test
    public void transientFailureMustNotPermanentlyCacheEnglish() throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail = "oracleid:" + ORACLE + " lang:zhs";
        Assertions.assertThrows(IOException.class, fixture::resolve);
        Assertions.assertFalse(fixture.requested.contains("tst/1/zht"));
        fixture.fail = null;
        fixture.search("zhs", card("zhs", "after-retry"));
        Assertions.assertEquals(fixture.resolve(), CDN + "after-retry.jpg");
    }

    @Test
    public void doubleFacedCardsUseTheRequestedFace() throws Exception {
        Fixture fixture = new Fixture();
        JsonObject translated = card("zhs", "unused");
        translated.remove("image_uris");
        JsonArray faces = new JsonArray();
        faces.add(card("zhs", "front"));
        faces.add(card("zhs", "back"));
        translated.add("card_faces", faces);
        fixture.replies.put("tst/1/zhs", translated);
        Assertions.assertEquals(new ScryfallCardImages(fixture).resolve("Test Card", PRINT, "back"), CDN + "back.jpg");
    }

    @Test
    public void missingBackImageDoesNotSubstituteTheFront() {
        JsonObject translated = card("zhs", "unused");
        translated.remove("image_uris");
        JsonArray faces = new JsonArray();
        faces.add(card("zhs", "front"));
        faces.add(new JsonObject());
        translated.add("card_faces", faces);
        Assertions.assertNull(ScryfallCardImages.imageUrl(translated, "back"));
    }

    @Test
    public void splitAndFlipCardsKeepTheirSingleCompleteScan() {
        Assertions.assertEquals(ScryfallCardImages.imageUrl(card("zhs", "both-halves"), "front"), CDN + "both-halves.jpg");
    }

    @Test
    public void missingCollectorNumberStillSearchesByExactCardName() throws Exception {
        Fixture fixture = new Fixture();
        fixture.replies.put("!\"Test Card\" lang:en", page(card("en", "english")));
        fixture.search("zhs", card("zhs", "simplified"));
        Assertions.assertEquals(new ScryfallCardImages(fixture).resolve("Test Card", null, "front"), CDN + "simplified.jpg");
    }

    @Test
    public void searchContinuesPastPlaceholderOnlyPages() throws Exception {
        Fixture fixture = new Fixture();
        JsonObject first = page();
        first.addProperty("has_more", true);
        first.addProperty("next_page", ScryfallCardImages.API + "search?page=2");
        fixture.replies.put("oracleid:" + ORACLE + " lang:zhs", first);
        fixture.replies.put("search?page=2", page(card("zhs", "second-page")));
        Assertions.assertEquals(fixture.resolve(), CDN + "second-page.jpg");
    }

    @Test
    public void imageMustBeAFullScanFromScryfall() {
        JsonObject cropOnly = card("zhs", "crop-only");
        cropOnly.getAsJsonObject("image_uris").remove("large");
        Assertions.assertNull(ScryfallCardImages.imageUrl(cropOnly, "front"));
        cropOnly.getAsJsonObject("image_uris").addProperty("normal", "https://other.invalid/card.jpg");
        Assertions.assertNull(ScryfallCardImages.imageUrl(cropOnly, "front"));
    }

    @Test
    public void chineseCacheDoesNotReuseAnUnverifiedEnglishCacheKey() {
        Assertions.assertEquals(CardImagePolicy.officialImageKey("M21/Opt.full"), "M21/Opt.full.zh-CN");
        Assertions.assertTrue(CardImagePolicy.isOfficialImageKey("M21/Opt.full.zh-CN"));
        Assertions.assertFalse(CardImagePolicy.isOfficialImageKey("PH01/马克扎尔的小鬼.full"));
        Assertions.assertFalse(CardImagePolicy.isOfficialImageKey("PH01/卡.artcrop"));
    }

    @Test
    public void originalMagicArtIsFullEvenWithAnArtistAndCropPreference() {
        forge.card.CardRules rules = forge.card.CardRules.getUnsupportedCardNamed("Original Magic");
        forge.item.PaperCard card = new forge.item.PaperCard(rules, "TST", forge.card.CardRarity.Common,
                1, false, "1", "Original Artist", null);
        Assertions.assertFalse(CardImagePolicy.isCustom(card));
        Assertions.assertFalse(CardImagePolicy.prefersArtCrop(card));
    }

    @Test
    public void cardStudioCropAndFinishedDiyPrintsRemainDistinct() {
        forge.card.CardRules rules = forge.card.CardRules.getUnsupportedCardNamed("DIY");
        rules.setCustom();
        forge.item.PaperCard crop = new forge.item.PaperCard(rules, "PH01", forge.card.CardRarity.Common,
                1, false, "1", "Custom", null);
        forge.item.PaperCard full = new forge.item.PaperCard(rules, "PH01", forge.card.CardRarity.Common,
                2, false, "1a", "", null);
        Assertions.assertTrue(CardImagePolicy.prefersArtCrop(crop));
        Assertions.assertTrue(CardImagePolicy.isCustom(full));
        Assertions.assertFalse(CardImagePolicy.prefersArtCrop(full));
    }
}
