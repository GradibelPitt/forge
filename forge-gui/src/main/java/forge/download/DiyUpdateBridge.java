package forge.download;

import forge.gui.util.SOptionPane;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/** Starts the embedded, isolated source updater; never opens an official installer. */
public final class DiyUpdateBridge {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final String RESOURCE = "/forge/download/diy-updater.ps1";
    private static volatile DiyUpdateProgress currentProgress;

    private DiyUpdateBridge() { }

    public static boolean isBundled() {
        return DiyUpdateBridge.class.getResource(RESOURCE) != null;
    }

    static String jsonString(final String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    public static boolean start() {
        if (!RUNNING.compareAndSet(false, true)) {
            final DiyUpdateProgress progress = currentProgress;
            if (progress != null) {
                progress.open();
            }
            return false;
        }
        try {
            final String install = System.getProperty("forge.diy.installRoot", "");
            final String app = System.getProperty("forge.diy.appRoot", "");
            if (install.isEmpty() || app.isEmpty()) {
                throw new IOException("请使用 ForgeDIY 一键启动器打开游戏，再执行 DIY 更新。");
            }
            if (SOptionPane.showOptionDialog("将下载新卡和版本资料，三方合并允许的引擎改动并重新编译。\n"
                    + "保留 DIY 界面和自定义机制；Java 删除或合并冲突会停止更新并生成报告。\n"
                    + "首次需要下载源码及编译工具，可能需要较长时间。完成后保存并重启游戏即可使用。",
                    "Forge DIY 增量更新", null, List.of("开始更新", "取消"), 1) != 0) {
                RUNNING.set(false);
                return false;
            }
            final Path job = Path.of(install, "updates", "jobs", UUID.randomUUID().toString());
            Files.createDirectories(job);
            final Path script = job.resolve("diy-updater.ps1");
            try (InputStream in = DiyUpdateBridge.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IOException("DIY 更新脚本缺失，请重新安装已验证的 DIY 运行包。");
                }
                Files.copy(in, script);
            }
            final Path request = job.resolve("request.json");
            Files.writeString(request, "{\"installRoot\":" + jsonString(install)
                    + ",\"appRoot\":" + jsonString(app) + ",\"javaHome\":"
                    + jsonString(System.getProperty("java.home")) + ",\"controllerPid\":"
                    + ProcessHandle.current().pid() + "}", StandardCharsets.UTF_8);
            final Path powershell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell",
                    "v1.0", "powershell.exe");
            final Process process = new ProcessBuilder(powershell.toString(), "-NoProfile", "-NonInteractive",
                    "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", script.toString(),
                    "-Request", request.toString()).redirectErrorStream(true)
                    .redirectOutput(job.resolve("update.log").toFile()).start();
            if (currentProgress != null) {
                currentProgress.disposeCompleted();
            }
            final DiyUpdateProgress progress = new DiyUpdateProgress(job.resolve("update.log"));
            currentProgress = progress;
            progress.open();
            final DiyUpdateDecision decision = new DiyUpdateDecision(job);
            final AtomicBoolean decisionErrorShown = new AtomicBoolean();
            final Runnable pollDecision = () -> {
                try {
                    decision.poll((id, summary) -> progress.requestDecision(summary, proceed -> {
                        try {
                            decision.answer(id, proceed);
                            return true;
                        } catch (IOException e) {
                            progress.append("\n选择未提交：" + e.getMessage() + "\n");
                            return false;
                        }
                    }));
                    decisionErrorShown.set(false);
                } catch (IOException e) {
                    if (decisionErrorShown.compareAndSet(false, true)) {
                        progress.append("\n无法读取测试失败确认：" + e.getMessage() + "\n");
                    }
                }
            };
            final Thread watcher = new Thread(() -> {
                boolean interrupted = false;
                try {
                    try {
                        DiyUpdateLog.follow(process, job.resolve("update.log"), progress::append, pollDecision);
                    } catch (IOException | InterruptedException e) {
                        interrupted = e instanceof InterruptedException;
                        progress.append("\n实时日志读取中断：" + e.getMessage() + "\n更新仍在继续，完整输出保存在日志文件。\n");
                    }
                    // Losing the viewer must not unlock a still-running updater.
                    while (true) {
                        try {
                            if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                                break;
                            }
                            pollDecision.run();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                    final int code = process.exitValue();
                    final Path result = job.resolve("result.txt");
                    final String message = Files.exists(result) ? Files.readString(result, StandardCharsets.UTF_8)
                            : "更新进程已结束，退出码 " + code;
                    progress.finish(code, message);
                } catch (IOException e) {
                    progress.finish(-1, "无法读取更新结果：" + e.getMessage());
                } finally {
                    RUNNING.set(false);
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "Forge-DIY-updater-monitor");
            watcher.setDaemon(true);
            watcher.start();
            return true;
        } catch (IOException | RuntimeException e) {
            RUNNING.set(false);
            show("更新未启动：" + e.getMessage(), "Forge DIY 更新");
            return false;
        }
    }

    private static void show(final String message, final String title) {
        SwingUtilities.invokeLater(() -> SOptionPane.showOptionDialog(message, title, null, List.of("确定"), 0));
    }
}
