package forge.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CardTranslationTest {
    @TempDir
    Path directory;

    private void write(String filename, String content) throws Exception {
        Files.writeString(directory.resolve(filename), content, StandardCharsets.UTF_8);
    }

    private void load() {
        CardTranslation.preloadTranslation("zh-CN", directory.toString());
    }

    @AfterEach
    void reset() {
        CardTranslation.preloadTranslation("en-US", directory.toString());
    }

    @Test
    void keepsLegacyRowsAndAddsOrOverridesCustomRows() throws Exception {
        write("cardnames-zh-CN.txt", "Old DIY|旧卡|生物|旧规则\nSame|原名|原类别|原规则\n");
        write("cardnames-zh-CN-custom.txt", "Same|新名|新类别|新规则\nNew DIY|新卡|法术|新效果\n");
        load();
        assertEquals("旧卡", CardTranslation.getTranslatedName("Old DIY"));
        assertEquals("旧规则", CardTranslation.getTranslatedOracle("Old DIY"));
        assertEquals("新名", CardTranslation.getTranslatedName("Same"));
        assertEquals("新类别", CardTranslation.getTranslatedType("Same", "fallback"));
        assertEquals("新规则", CardTranslation.getTranslatedOracle("Same"));
        assertEquals("新效果", CardTranslation.getTranslatedOracle("New DIY"));
        assertEquals("Unknown", CardTranslation.getTranslatedName("Unknown"));
    }

    @Test
    void absentOverlayIsSilentAndAcceptsTrailingDirectorySeparator() throws Exception {
        write("cardnames-zh-CN.txt", "Old|旧卡|法术|规则\n");
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        try (PrintStream capture = new PrintStream(errors)) {
            System.setErr(capture);
            CardTranslation.preloadTranslation("zh-CN", directory + "/");
        } finally {
            System.setErr(previous);
        }
        assertEquals("", errors.toString());
        assertEquals("旧卡", CardTranslation.getTranslatedName("Old"));
    }

    @Test
    void reusesOracleEscapesClassCleanupAndFunctionalVariants() throws Exception {
        write("cardnames-zh-CN.txt", "");
        write("cardnames-zh-CN-custom.txt", "Variant$C|变体|法术|一VERT二\\n//Level_2//\\n三\\n//Level_3//\\n四\n");
        load();
        assertEquals("变体", CardTranslation.getTranslatedName("Variant"));
        assertEquals("变体", CardTranslation.getTranslatedName("Variant $C"));
        assertEquals("一|二\r\n\r\n三\r\n\r\n四", CardTranslation.getTranslatedOracle("Variant $C"));
    }

    @Test
    void acceptsUtf8BomAndIgnoresCommentsInOverlay() throws Exception {
        write("cardnames-zh-CN.txt", "");
        write("cardnames-zh-CN-custom.txt", "\uFEFFNew|新卡|法术|规则\n# Example|not a card|type|oracle\n");
        load();
        assertEquals("新卡", CardTranslation.getTranslatedName("New"));
        assertEquals("# Example", CardTranslation.getTranslatedName("# Example"));
    }

    @Test
    void retainsLegacyFieldFallbacksAndLastRecordWins() throws Exception {
        write("cardnames-zh-CN.txt", "Same|原名|原类别|原规则\n");
        write("cardnames-zh-CN-custom.txt", "Same|一次\nSame|二次\n");
        load();
        assertEquals("二次", CardTranslation.getTranslatedName("Same"));
        assertEquals("原类别", CardTranslation.getTranslatedType("Same", "fallback"));
        assertEquals("原规则", CardTranslation.getTranslatedOracle("Same"));
    }

    @Test
    void reloadDiscardsDeletedCustomEntries() throws Exception {
        write("cardnames-zh-CN.txt", "Same|原名|类别|原规则\n");
        write("cardnames-zh-CN-custom.txt", "Same|新名|类别|新规则\nNew|新卡|类别|规则\n");
        load();
        Files.delete(directory.resolve("cardnames-zh-CN-custom.txt"));
        load();
        assertEquals("原名", CardTranslation.getTranslatedName("Same"));
        assertEquals("New", CardTranslation.getTranslatedName("New"));
    }

    @Test
    void englishDoesNotLoadTranslationFiles() throws Exception {
        write("cardnames-en-US-custom.txt", "Name|Changed|Type|Oracle\n");
        CardTranslation.preloadTranslation("en-US", directory.toString());
        assertEquals("Name", CardTranslation.getTranslatedName("Name"));
        assertEquals("", CardTranslation.getTranslatedOracle("Name"));
    }

    @Test
    void onlyLoadsSelectedLanguage() throws Exception {
        write("cardnames-fr-FR.txt", "Name|Base|Type|Oracle\n");
        write("cardnames-fr-FR-custom.txt", "Name|Personnalise|Type|Oracle\n");
        write("cardnames-zh-CN-custom.txt", "Name|中文|类别|规则\n");
        CardTranslation.preloadTranslation("fr-FR", directory.toString());
        assertEquals("Personnalise", CardTranslation.getTranslatedName("Name"));
    }

    @Test
    void reportsMissingBaseButStillLoadsOverlay() throws Exception {
        write("cardnames-zh-CN-custom.txt", "Name|中文|类别|规则\n");
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        try (PrintStream capture = new PrintStream(errors)) {
            System.setErr(capture);
            load();
        } finally {
            System.setErr(previous);
        }
        assertTrue(errors.toString().contains("cardnames-zh-CN.txt"));
        assertEquals("中文", CardTranslation.getTranslatedName("Name"));
    }
}
