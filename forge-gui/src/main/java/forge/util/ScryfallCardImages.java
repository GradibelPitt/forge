package forge.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.StaticData;
import forge.card.CardEdition;
import forge.item.IPaperCard;
import forge.item.PaperCard;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Resolves real full-card scans; a missing translation and a failed request are distinct. */
public final class ScryfallCardImages {
    static final String API = "https://api.scryfall.com/cards/";
    public static final String USER_AGENT = "ForgeDIY/1.0 (https://github.com/GradibelPitt/forge-diy-runtime)";
    private static long nextRequest;

    @FunctionalInterface
    interface JsonSource {
        JsonObject read(String url) throws IOException;
    }

    private final JsonSource source;

    public ScryfallCardImages() {
        this(ScryfallCardImages::readJson);
    }

    ScryfallCardImages(JsonSource source) {
        this.source = source;
    }

    public String resolve(PaperCard card, String face) throws IOException {
        CardEdition edition = StaticData.instance().getEditions().get(card.getEdition());
        String set = edition == null ? card.getEdition().toLowerCase(java.util.Locale.ROOT)
                : edition.getScryfallCode();
        String path = ImageUtil.getScryfallDownloadUrl(card, face, set, "en", false);
        String imageFace = path.contains("face=back") ? "back" : "front";
        String printPath = IPaperCard.NO_COLLECTOR_NUMBER.equals(card.getCollectorNumber())
                ? null : path.substring(0, path.indexOf('?'));
        return resolve(card.getName(), printPath, imageFace);
    }

    String resolve(String name, String englishPrintPath, String face) throws IOException {
        JsonObject original = englishPrintPath == null ? null : source.read(API + englishPrintPath);
        if (original == null) {
            original = find(name, null, "en", face);
        }
        String oracle = string(original, "oracle_id");
        // A meld back/specialized face has its own identity, supplied by the exact print lookup.
        String searchName = original == null ? name : string(original, "name");
        String chineseFallback = null;
        for (String language : new String[] {"zhs", "zht"}) {
            if (englishPrintPath != null) {
                String path = englishPrintPath.substring(0, englishPrintPath.lastIndexOf('/') + 1) + language;
                JsonObject translated = source.read(API + path);
                if (matches(translated, language, oracle)) {
                    String url = imageUrl(translated, face);
                    if (url != null && isHighResolution(translated)) {
                        return url;
                    }
                    if (chineseFallback == null) { chineseFallback = url; }
                }
            }
            JsonObject translated = find(searchName, oracle, language, face);
            if (translated != null) {
                String url = imageUrl(translated, face);
                if (isHighResolution(translated)) { return url; }
                if (chineseFallback == null) { chineseFallback = url; }
            }
        }
        String fallback = imageUrl(original, face);
        if (fallback != null && isHighResolution(original)) {
            return fallback;
        }
        JsonObject english = find(searchName, oracle, "en", face);
        String englishUrl = imageUrl(english, face);
        if (englishUrl != null && isHighResolution(english)) { return englishUrl; }
        if (chineseFallback != null) { return chineseFallback; }
        return fallback != null ? fallback : englishUrl;
    }

    private JsonObject find(String name, String oracle, String language, String face) throws IOException {
        String identity = oracle == null ? "!\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                : "oracleid:" + oracle;
        String url = API + "search?include_multilingual=true&include_extras=true&unique=prints&order=released&dir=desc&q="
                + URLEncoder.encode(identity + " lang:" + language, StandardCharsets.UTF_8);
        Set<String> pages = new HashSet<>();
        JsonObject fallback = null;
        while (url != null) {
            if (!url.startsWith(API + "search?") || !pages.add(url) || pages.size() > 20) {
                throw new IOException("Invalid or excessive Scryfall pagination");
            }
            JsonObject page = source.read(url);
            if (page == null) {
                return fallback;
            }
            if (!page.has("data") || !page.get("data").isJsonArray()) {
                throw new IOException("Invalid Scryfall search response");
            }
            for (JsonElement element : page.getAsJsonArray("data")) {
                JsonObject candidate = element.getAsJsonObject();
                if (matches(candidate, language, oracle) && imageUrl(candidate, face) != null) {
                    if (isHighResolution(candidate)) { return candidate; }
                    if (fallback == null) { fallback = candidate; }
                }
            }
            boolean more = page.has("has_more") && page.get("has_more").getAsBoolean();
            url = more ? string(page, "next_page") : null;
            if (more && url == null) {
                throw new IOException("Missing Scryfall next page");
            }
        }
        return fallback;
    }

    private static boolean isHighResolution(JsonObject card) {
        if (card == null || "lowres".equals(string(card, "image_status"))) { return false; }
        if (card.has("highres_image") && !card.get("highres_image").isJsonNull()) {
            return card.get("highres_image").getAsBoolean();
        }
        return "highres_scan".equals(string(card, "image_status"));
    }

    private static boolean matches(JsonObject card, String language, String oracle) {
        return card != null && language.equals(string(card, "lang"))
                && (oracle == null || oracle.equals(string(card, "oracle_id")));
    }

    static String imageUrl(JsonObject card, String face) {
        if (card == null || "placeholder".equals(string(card, "image_status"))
                || "missing".equals(string(card, "image_status"))) {
            return null;
        }
        JsonObject images = card.has("image_uris") && card.get("image_uris").isJsonObject()
                ? card.getAsJsonObject("image_uris") : null;
        if (images == null && card.has("card_faces") && card.get("card_faces").isJsonArray()) {
            int index = "back".equals(face) ? 1 : 0;
            if (card.getAsJsonArray("card_faces").size() <= index) {
                return null;
            }
            JsonObject part = card.getAsJsonArray("card_faces").get(index).getAsJsonObject();
            images = part.has("image_uris") && part.get("image_uris").isJsonObject()
                    ? part.getAsJsonObject("image_uris") : null;
        }
        for (String size : new String[] {"png", "large", "normal"}) {
            String url = string(images, size);
            if (url != null && url.startsWith("https://cards.scryfall.io/")) {
                return url;
            }
        }
        return null;
    }

    private static String string(JsonObject object, String field) {
        return object == null || !object.has(field) || object.get(field).isJsonNull()
                ? null : object.get(field).getAsString();
    }

    private static synchronized void paceRequests() throws IOException {
        long wait = nextRequest - System.currentTimeMillis();
        if (wait > 1000) {
            throw new IOException("Scryfall is cooling down; retry later");
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted Scryfall request", e);
            }
        }
        nextRequest = System.currentTimeMillis() + 120;
    }

    private static JsonObject readJson(String url) throws IOException {
        paceRequests();
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status == 404) {
                return null;
            }
            if (status == 429) {
                long seconds = 60;
                try {
                    seconds = Math.max(seconds, Long.parseLong(connection.getHeaderField("Retry-After")));
                } catch (NumberFormatException ignored) { }
                synchronized (ScryfallCardImages.class) {
                    nextRequest = System.currentTimeMillis() + Math.min(seconds, 3600) * 1000;
                }
            }
            if (status != 200) {
                throw new IOException("Scryfall HTTP " + status);
            }
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            } catch (RuntimeException e) {
                throw new IOException("Invalid Scryfall JSON", e);
            }
        } finally {
            connection.disconnect();
        }
    }
}
