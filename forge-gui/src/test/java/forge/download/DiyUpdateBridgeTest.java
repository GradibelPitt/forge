package forge.download;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DiyUpdateBridgeTest {
    @TempDir Path temp;

    @Test void decisionWaitsForExplicitChoiceAndRejectsReusedOrStaleAnswers() throws Exception {
        DiyUpdateDecision decision = new DiyUpdateDecision(temp);
        var requests = new java.util.ArrayList<String>();
        decision.poll((id, summary) -> requests.add(summary));
        assertTrue(requests.isEmpty());
        String first = "1".repeat(32);
        Files.writeString(temp.resolve("test-decision.request"), first + "\n2 项测试失败", StandardCharsets.UTF_8);
        decision.poll((id, summary) -> requests.add(summary));
        decision.poll((id, summary) -> requests.add(summary));
        assertEquals(java.util.List.of("2 项测试失败"), requests);
        Path response = temp.resolve("test-decision-" + first + ".response");
        assertFalse(Files.exists(response), "Reading a request must never approve it");
        decision.answer(first, true);
        assertEquals("continue", Files.readString(response));
        assertThrows(java.io.IOException.class, () -> decision.answer(first, false));
        String next = "2".repeat(32);
        Files.writeString(temp.resolve("test-decision.request"), next + "\n另一项失败", StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> decision.answer(first, true));
        decision.poll((id, summary) -> requests.add(summary));
        decision.answer(next, false);
        assertEquals("stop", Files.readString(temp.resolve("test-decision-" + next + ".response")));
        assertEquals(2, requests.size());
    }

    @Test void invalidDecisionCannotWriteOutsideJob() throws Exception {
        DiyUpdateDecision decision = new DiyUpdateDecision(temp);
        Files.writeString(temp.resolve("test-decision.request"), "../outside\nTest failure");
        assertThrows(java.io.IOException.class, () -> decision.poll((id, summary) -> fail("Invalid request displayed")));
        assertThrows(java.io.IOException.class, () -> decision.answer("../outside", true));
    }

    @Test void progressViewBoundsHistoryAndAllowsReadingOlderOutput() throws Exception {
        assumeTrue(!java.awt.GraphicsEnvironment.isHeadless());
        DiyUpdateProgress progress = new DiyUpdateProgress(temp.resolve("update.log"));
        progress.append("first\r");
        progress.append("\nsecond\rthird");
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        var textField = DiyUpdateProgress.class.getDeclaredField("text");
        var followField = DiyUpdateProgress.class.getDeclaredField("follow");
        var windowField = DiyUpdateProgress.class.getDeclaredField("window");
        var clockField = DiyUpdateProgress.class.getDeclaredField("clock");
        for (var field : java.util.List.of(textField, followField, windowField, clockField)) { field.setAccessible(true); }
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    var text = (javax.swing.JTextArea) textField.get(progress);
                    assertEquals("first\nsecond\nthird", text.getText());
                    ((javax.swing.JCheckBox) followField.get(progress)).setSelected(false);
                    text.setCaretPosition(0);
                } catch (IllegalAccessException e) { throw new RuntimeException(e); }
            });
            progress.append("new output");
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { assertEquals(0, ((javax.swing.JTextArea) textField.get(progress)).getCaretPosition()); }
                catch (IllegalAccessException e) { throw new RuntimeException(e); }
            });
            progress.append("x".repeat(220000));
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { assertEquals(200000, ((javax.swing.JTextArea) textField.get(progress)).getDocument().getLength()); }
                catch (IllegalAccessException e) { throw new RuntimeException(e); }
            });
        } finally {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    ((javax.swing.Timer) clockField.get(progress)).stop();
                    ((javax.swing.JFrame) windowField.get(progress)).dispose();
                } catch (IllegalAccessException e) { throw new RuntimeException(e); }
            });
        }
    }

    @Test void streamsBeforeExitAndPreservesSplitUtf8AndFinalOutput() throws Exception {
        Path log = temp.resolve("live.log");
        Path gate = temp.resolve("continue");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), LogWriter.class.getName(), gate.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        var worker = Executors.newSingleThreadExecutor();
        var early = new CountDownLatch(1);
        var output = new StringBuffer();
        try {
            var completed = worker.submit(() -> {
                DiyUpdateLog.follow(process, log, chunk -> {
                    output.append(chunk);
                    if (output.toString().contains("下载中")) { early.countDown(); }
                });
                return null;
            });
            assertTrue(early.await(10, TimeUnit.SECONDS), "No log appeared before process exit");
            assertTrue(process.isAlive(), "Test producer should still be waiting");
            Files.writeString(gate, "continue");
            completed.get(10, TimeUnit.SECONDS);
            assertEquals(7, process.exitValue());
            assertEquals("开始\r10%\r下载中\n" + "x".repeat(20000) + "错误：停止", output.toString());
        } finally {
            Files.writeString(gate, "continue");
            process.destroyForcibly();
            worker.shutdownNow();
        }
    }

    public static final class LogWriter {
        public static void main(final String[] args) throws Exception {
            System.out.write("开始\r10%\r".getBytes(StandardCharsets.UTF_8));
            final byte[] split = "下载中\n".getBytes(StandardCharsets.UTF_8);
            System.out.write(split, 0, 2);
            System.out.flush();
            Thread.sleep(500);
            System.out.write(split, 2, split.length - 2);
            System.out.flush();
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!Files.exists(Path.of(args[0])) && System.nanoTime() < deadline) { Thread.sleep(30); }
            System.out.write(("x".repeat(20000) + "错误：停止").getBytes(StandardCharsets.UTF_8));
            System.out.flush();
            System.exit(7);
        }
    }

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
