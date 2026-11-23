package compilerui.gui;

import compilerui.pipeline.IdeSession;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

final class SysyIdePanel extends JPanel {
    private final CodeEditor editor = new CodeEditor();
    private final JTextArea transcript = new JTextArea(7, 50);
    private final JTextField input = new JTextField();
    private final JButton run = new JButton("▶  运行");
    private final JButton stop = new JButton("停止");
    private final JLabel status = new JLabel("就绪");
    private IdeSession session;
    private boolean stopping;

    SysyIdePanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.add(new JLabel("  main.sy"), BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(status);
        actions.add(run);
        actions.add(stop);
        toolbar.add(actions, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

        Color ink = new Color(0x18212b);
        Color text = new Color(0xe4edf5);
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setBackground(ink);
        transcript.setForeground(text);
        transcript.setMargin(new Insets(6, 8, 6, 8));
        input.setBackground(ink);
        input.setForeground(text);
        input.setCaretColor(text);
        input.setToolTipText("输入一行，按 Enter 发送给程序");
        input.getAccessibleContext().setAccessibleName("终端输入");
        JPanel prompt = new JPanel(new BorderLayout(6, 0));
        prompt.setBackground(ink);
        JLabel arrow = new JLabel("  ❯");
        arrow.setForeground(new Color(0x86d4b0));
        prompt.add(arrow, BorderLayout.WEST);
        prompt.add(input, BorderLayout.CENTER);
        JPanel terminal = new JPanel(new BorderLayout());
        terminal.add(new JLabel("终端"), BorderLayout.NORTH);
        terminal.add(new JScrollPane(transcript), BorderLayout.CENTER);
        terminal.add(prompt, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editor, terminal);
        split.setResizeWeight(0.78);
        split.setDividerLocation(0.78);
        add(split, BorderLayout.CENTER);

        editor.setText("""
                int main() {
                    printf("n = ");
                    int n = get_int();
                    printf("n + 1 = %d\\n", n + 1);
                    return 0;
                }
                """);
        input.setEnabled(false);
        stop.setEnabled(false);
        run.addActionListener(e -> start());
        stop.addActionListener(e -> stopSession());
        input.addActionListener(e -> send());
    }

    private void start() {
        String source = editor.getText();
        if (source.isBlank()) {
            status.setText("请先输入 SysY 代码");
            editor.pane().requestFocusInWindow();
            return;
        }
        IdeSession current = new IdeSession();
        session = current;
        stopping = false;
        transcript.setText("");
        input.setText("");
        run.setEnabled(false);
        stop.setEnabled(true);
        status.setText("编译中…");
        new SwingWorker<IdeSession.Result, String>() {
            @Override
            protected IdeSession.Result doInBackground() throws Exception {
                Thread cleanup = new Thread(current::stop, "sysy-ide-exit");
                Runtime.getRuntime().addShutdownHook(cleanup);
                try {
                    return current.run(source, this::publish, () -> SwingUtilities.invokeLater(() -> {
                        if (session == current && !stopping) {
                            status.setText("运行中 · 回车发送输入");
                            input.setEnabled(true);
                            input.requestFocusInWindow();
                        }
                    }));
                } finally {
                    Runtime.getRuntime().removeShutdownHook(cleanup);
                }
            }

            @Override
            protected void process(List<String> chunks) {
                if (session == current) append(String.join("", chunks));
            }

            @Override
            protected void done() {
                try {
                    IdeSession.Result result = get();
                    status.setText(stopping ? "已停止" : result.status());
                    if (!stopping && result.programExited()) {
                        String text = transcript.getText();
                        String separator = text.isEmpty() || text.endsWith("\n") ? "" : "\n";
                        append(separator + "程序已结束（" + result.status() + "）\n");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    status.setText("已中断");
                } catch (ExecutionException ex) {
                    status.setText(stopping ? "已停止" : "运行失败");
                    if (!stopping) append("\n" + ex.getCause().getMessage() + "\n");
                } finally {
                    session = null;
                    input.setEnabled(false);
                    stop.setEnabled(false);
                    run.setEnabled(true);
                }
            }
        }.execute();
    }

    private void send() {
        String line = input.getText();
        IdeSession current = session;
        input.setText("");
        input.setEnabled(false);
        append(line + "\n");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                current.sendLine(line);
                return null;
            }

            @Override
            protected void done() {
                if (session != current || stopping) return;
                try {
                    get();
                    input.setEnabled(true);
                    input.requestFocusInWindow();
                } catch (ExecutionException ex) {
                    append("\n输入未送达：" + ex.getCause().getMessage() + "\n");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void stopSession() {
        stopping = true;
        stop.setEnabled(false);
        input.setEnabled(false);
        status.setText("正在停止…");
        new Thread(session::stop, "sysy-ide-stop").start();
    }

    private void append(String text) {
        transcript.append(text);
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }
}
