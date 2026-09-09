package forge.download;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DiyUpdateBridgeTest {
    @TempDir Path temp;

    @Test void quotesWindowsPathsAndControlCharacters() {
        assertEquals("\"C:\\\\DIY \\" + "\"test\\\"\\n\\r\\t\"",
                DiyUpdateBridge.jsonString("C:\\DIY \"test\"\n\r\t"));
    }

    @Test void rulesAreBundledAndProtectDiy() throws Exception {
        assertTrue(DiyUpdateBridge.isBundled());
        try (InputStream in = DiyUpdateBridge.class.getResourceAsStream("/forge/download/diy-updater.ps1")) {
            assertNotNull(in);
            String rules = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(rules.contains("'--3way', '--index'"));
            assertTrue(rules.contains("Write-ActivePointer"));
            assertTrue(rules.contains("Assert-DiyClasses"));
            assertFalse(rules.contains("-upgrade.jar"));
            assertFalse(rules.contains("System.exit"));
        }
    }

    @Test void extractedScriptLoadsInWindowsPowerShellWithoutUpdating() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        Path script = temp.resolve("embedded rules.ps1");
        try (InputStream in = DiyUpdateBridge.class.getResourceAsStream("/forge/download/diy-updater.ps1")) {
            assertNotNull(in);
            Files.copy(in, script);
        }
        Path log = temp.resolve("parse.log");
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", script.toString(),
                "-LibraryOnly").redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Embedded script load timed out");
        assertEquals(0, process.exitValue(), Files.readString(log));
    }
}
