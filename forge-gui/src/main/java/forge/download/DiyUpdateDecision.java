package forge.download;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Exchanges an explicit per-run test-failure decision with the embedded updater. */
final class DiyUpdateDecision {
    private final Path job;
    private String shown;

    DiyUpdateDecision(final Path directory) {
        job = directory;
    }

    void poll(final BiConsumer<String, String> display) throws IOException {
        final Path request = job.resolve("test-decision.request");
        if (!Files.exists(request)) {
            return;
        }
        final String value = Files.readString(request, StandardCharsets.UTF_8);
        final int end = value.indexOf('\n');
        if (end < 0) {
            throw new IOException("测试失败确认请求不完整。");
        }
        final String id = value.substring(0, end).trim();
        if (!id.matches("[a-f0-9]{32}")) {
            throw new IOException("测试失败确认编号无效。");
        }
        if (!id.equals(shown) && !Files.exists(job.resolve("test-decision-" + id + ".response"))) {
            display.accept(id, value.substring(end + 1));
            shown = id;
        }
    }

    void answer(final String id, final boolean proceed) throws IOException {
        if (!id.matches("[a-f0-9]{32}")) {
            throw new IOException("测试失败确认编号无效。");
        }
        final String current = Files.readString(job.resolve("test-decision.request"), StandardCharsets.UTF_8);
        if (!current.startsWith(id + "\n") && !current.startsWith(id + "\r\n")) {
            throw new IOException("确认请求已变化，请等待新的失败列表。");
        }
        final Path response = job.resolve("test-decision-" + id + ".response");
        if (Files.exists(response)) {
            throw new IOException("本次选择已经提交。");
        }
        final Path temporary = job.resolve("decision-" + UUID.randomUUID() + ".tmp");
        Files.writeString(temporary, proceed ? "continue" : "stop", StandardCharsets.UTF_8);
        Files.move(temporary, response, StandardCopyOption.ATOMIC_MOVE);
    }
}
