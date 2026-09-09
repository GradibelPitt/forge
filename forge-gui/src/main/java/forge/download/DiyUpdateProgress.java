package forge.download;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** A modeless, bounded view of the full on-disk update log. All widgets live on the EDT. */
final class DiyUpdateProgress {
    private static final int MAX_CHARACTERS = 200000;
    private final Path log;
    private final long started = System.nanoTime();
    private JFrame window;
    private JTextArea text;
    private JLabel status;
    private JButton close;
    private JCheckBox follow;
    private Timer clock;
    private JPanel decisionPanel;
    private JButton proceed;
    private JButton retain;
    private Predicate<Boolean> answer;
    private boolean awaitingDecision;
    private boolean completed;
    private boolean previousCarriageReturn;

    DiyUpdateProgress(final Path logFile) {
        log = logFile;
    }

    private void initialize() {
        if (window != null) {
            return;
        }
        window = new JFrame("Forge DIY 更新 · 实时日志");
        window.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        final JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.setBackground(new Color(43, 33, 24));
        status = new JLabel("正在启动更新…");
        status.setForeground(new Color(246, 214, 162));
        status.setFont(status.getFont().deriveFont(Font.BOLD, 16));
        panel.add(status, BorderLayout.NORTH);
        text = new JTextArea();
        ((javax.swing.text.DefaultCaret) text.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        text.setBackground(new Color(27, 23, 19));
        text.setForeground(new Color(237, 230, 217));
        text.setMargin(new java.awt.Insets(10, 10, 10, 10));
        final JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(740, 390));
        panel.add(scroll, BorderLayout.CENTER);
        final JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setOpaque(false);
        decisionPanel = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        decisionPanel.setOpaque(false);
        final JLabel question = new JLabel("测试未全部通过，请查看上方失败列表后选择：");
        question.setForeground(new Color(246, 214, 162));
        decisionPanel.add(question);
        final JPanel choices = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        choices.setOpaque(false);
        retain = new JButton("保留当前版本");
        proceed = new JButton("已知晓失败，继续更新");
        retain.addActionListener(event -> submitDecision(false));
        proceed.addActionListener(event -> submitDecision(true));
        choices.add(retain);
        choices.add(proceed);
        decisionPanel.add(choices);
        decisionPanel.setVisible(false);
        bottom.add(decisionPanel, BorderLayout.NORTH);
        follow = new JCheckBox("跟随最新输出", true);
        follow.setOpaque(false);
        follow.setForeground(text.getForeground());
        bottom.add(follow, BorderLayout.WEST);
        close = new JButton("隐藏（继续更新）");
        close.addActionListener(event -> window.setVisible(false));
        bottom.add(close, BorderLayout.EAST);
        final JTextArea location = new JTextArea("完整日志：" + log);
        location.setEditable(false);
        location.setLineWrap(true);
        location.setWrapStyleWord(true);
        location.setRows(2);
        location.setOpaque(false);
        location.setForeground(text.getForeground());
        bottom.add(location, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);
        window.setContentPane(panel);
        window.pack();
        window.setMinimumSize(new Dimension(560, 360));
        window.setLocationRelativeTo(null);
        clock = new Timer(1000, event -> {
            if (awaitingDecision) {
                return;
            }
            final long seconds = (System.nanoTime() - started) / 1000000000L;
            status.setText("正在更新 · 已用 " + seconds / 60 + " 分 " + seconds % 60 + " 秒");
        });
        clock.start();
    }

    void requestDecision(final String summary, final Predicate<Boolean> submit) {
        SwingUtilities.invokeLater(() -> {
            initialize();
            awaitingDecision = true;
            answer = submit;
            appendText("\n──────── 需要你的选择 ────────\n" + summary + "\n");
            status.setText("测试未全部通过 · 等待你的选择");
            close.setText("隐藏（等待选择）");
            proceed.setEnabled(true);
            retain.setEnabled(true);
            decisionPanel.setVisible(true);
            window.pack();
            window.setVisible(true);
            window.toFront();
            retain.requestFocusInWindow();
        });
    }

    private void submitDecision(final boolean continueUpdate) {
        if (!awaitingDecision || answer == null) {
            return;
        }
        if (answer.test(continueUpdate)) {
            awaitingDecision = false;
            proceed.setEnabled(false);
            retain.setEnabled(false);
            decisionPanel.setVisible(false);
            close.setText("隐藏（继续更新）");
            status.setText(continueUpdate ? "已确认测试失败，继续构建并检查 DIY 保护" : "正在保留当前版本…");
        }
    }

    void open() {
        SwingUtilities.invokeLater(() -> {
            initialize();
            window.setVisible(true);
            window.setState(JFrame.NORMAL);
            window.toFront();
        });
    }

    void append(final String value) {
        SwingUtilities.invokeLater(() -> {
            initialize();
            appendText(value);
        });
    }

    private void appendText(final String value) {
        final StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            final char character = value.charAt(i);
            if (character == '\r') {
                normalized.append('\n');
            } else if (character != '\n' || !previousCarriageReturn) {
                normalized.append(character);
            }
            previousCarriageReturn = character == '\r';
        }
        text.append(normalized.toString());
        final int excess = text.getDocument().getLength() - MAX_CHARACTERS;
        if (excess > 0) {
            text.replaceRange("", 0, excess);
        }
        if (follow.isSelected()) {
            text.setCaretPosition(text.getDocument().getLength());
        }
    }

    void finish(final int code, final String result) {
        SwingUtilities.invokeLater(() -> {
            initialize();
            completed = true;
            awaitingDecision = false;
            decisionPanel.setVisible(false);
            clock.stop();
            status.setText(code == 0 ? "更新检查完成" : "更新已停止 · 请查看下方原因");
            appendText("\n──────── 更新结果 ────────\n" + result + "\n");
            close.setText("关闭");
            window.setVisible(true);
            window.toFront();
        });
    }

    void disposeCompleted() {
        SwingUtilities.invokeLater(() -> {
            if (completed && window != null) {
                clock.stop();
                window.dispose();
            }
        });
    }
}
