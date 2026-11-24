package compilerui.gui;

import compilerui.pipeline.IrRunner;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.concurrent.ExecutionException;

final class IrRunPanel extends JPanel {
    private final JTextArea ir = new JTextArea();
    private final JTextArea input = new JTextArea();
    private final JTextArea expected = new JTextArea();
    private final JTextArea output = new JTextArea();
    private final JTextArea diagnostics = new JTextArea();
    private final JCheckBox compare = new JCheckBox("对比预期输出");
    private final JButton run = new JButton("运行 IR");
    private final JLabel status = new JLabel("就绪");
    private final JTabbedPane results = new JTabbedPane();

    IrRunPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        CodeIndent.install(ir);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(run);
        actions.add(compare);
        actions.add(status);
        add(actions, BorderLayout.NORTH);

        JPanel data = new JPanel(new GridLayout(2, 1, 0, 6));
        data.add(field("标准输入（stdin）", input));
        data.add(field("预期输出", expected));
        expected.setEnabled(false);
        compare.addActionListener(e -> expected.setEnabled(compare.isSelected()));
        JSplitPane editors = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, field("LLVM IR", ir), data);
        editors.setResizeWeight(0.65);

        output.setEditable(false);
        diagnostics.setEditable(false);
        results.addTab("实际输出（stdout）", new JScrollPane(output));
        results.addTab("编译与运行诊断", new JScrollPane(diagnostics));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editors, results);
        split.setResizeWeight(0.65);
        add(split, BorderLayout.CENTER);
        run.addActionListener(e -> runIr());
    }

    private static JScrollPane field(String title, JTextArea text) {
        JScrollPane pane = new JScrollPane(text);
        pane.setBorder(BorderFactory.createTitledBorder(title));
        return pane;
    }

    private void runIr() {
        String code = ir.getText();
        if (code.isBlank()) {
            status.setText("请先粘贴 LLVM IR");
            ir.requestFocusInWindow();
            return;
        }
        String stdin = input.getText();
        String wanted = compare.isSelected() ? expected.getText() : null;
        run.setEnabled(false);
        output.setText("");
        diagnostics.setText("");
        results.setSelectedIndex(0);
        status.setText("正在编译并运行…");

        new SwingWorker<IrRunner.Result, Void>() {
            @Override
            protected IrRunner.Result doInBackground() throws Exception {
                return IrRunner.run(code, stdin);
            }

            @Override
            protected void done() {
                run.setEnabled(true);
                try {
                    IrRunner.Result result = get();
                    output.setText(result.output());
                    output.setCaretPosition(0);
                    diagnostics.setText(result.diagnostics());
                    diagnostics.setCaretPosition(0);
                    String message = result.status();
                    if (result.success() && wanted != null) {
                        boolean match = result.output().replace("\r\n", "\n")
                                .equals(wanted.replace("\r\n", "\n"));
                        message += match ? "，输出符合预期" : "，输出与预期不一致";
                    }
                    status.setText(message);
                    if (!result.success() && !result.diagnostics().isBlank()) {
                        results.setSelectedIndex(1);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    status.setText("运行已中断");
                } catch (ExecutionException ex) {
                    diagnostics.setText(ex.getCause().toString());
                    results.setSelectedIndex(1);
                    status.setText("运行失败");
                }
            }
        }.execute();
    }
}
