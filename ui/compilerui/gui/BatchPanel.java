package compilerui.gui;

import compilerui.model.CompilerSettings;
import compilerui.pipeline.BatchRunner;
import compilerui.pipeline.BatchRunner.Sample;
import compilerui.pipeline.BatchRunner.SampleOutcome;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * 批量编译面板：选一个样例文件夹，把所有 {@code .txt} 编译后写到输出文件夹。
 *
 * <p>输出结构为 {@code <输出文件夹>/result of "样例名"/{lexer,parser,symbol,error}.txt}。
 * 本面板只在前端使用，不改变命令行 {@code Compiler.main()} 的行为。
 */
public final class BatchPanel extends JPanel {

    private final JTextField sampleDirField = new JTextField();
    private final JTextField outputDirField = new JTextField();
    private final JButton runButton = new JButton("▶ 批量编译");
    private final JProgressBar progress = new JProgressBar();
    private final JLabel summary = new JLabel(
            "就绪。样例可放在所选文件夹本身，或放在它的下一级子目录里。");
    private final DefaultTableModel model;
    private final JTable table;
    /** 由主窗口注入：提供当前调试开关等设置。 */
    private java.util.function.Supplier<CompilerSettings> settingsSupplier =
            CompilerSettings::defaults;
    /** 由主窗口注入：用于把状态写到主窗口状态栏。 */
    private java.util.function.Consumer<String> statusConsumer = text -> { };

    private boolean running = false;
    private List<SampleOutcome> outcomes = List.of();

    public BatchPanel() {
        super(new BorderLayout(6, 6));

        model = new DefaultTableModel(
                new String[]{"样例", "词法", "语法", "语义", "错误数", "结果", "输出目录"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.setRowHeight(24);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setDefaultRenderer(Object.class, new OutcomeRenderer());

        add(buildHeader(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        Path sample = BatchRunner.defaultSampleDir();
        Path output = BatchRunner.defaultOutputDir();
        sampleDirField.setText(sample.toString());
        outputDirField.setText(output.toString());
    }

    /** 主窗口注入编译设置来源。 */
    public void setSettingsSupplier(java.util.function.Supplier<CompilerSettings> supplier) {
        this.settingsSupplier = supplier == null ? CompilerSettings::defaults : supplier;
    }

    /** 主窗口注入状态栏写入方式。 */
    public void setStatusConsumer(java.util.function.Consumer<String> consumer) {
        this.statusConsumer = consumer == null ? text -> { } : consumer;
    }

    public String sampleDirText() {
        return sampleDirField.getText().trim();
    }

    public String outputDirText() {
        return outputDirField.getText().trim();
    }

    public void setSampleDirText(String text) {
        sampleDirField.setText(text);
    }

    public void setOutputDirText(String text) {
        outputDirField.setText(text);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));

        // 分两行放：右方面板不会太宽，挤一行会把执行按钮裁掉。
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        row1.setBorder(BorderFactory.createTitledBorder("批量编译"));
        row1.add(new JLabel("样例文件夹:"));
        sampleDirField.setPreferredSize(new Dimension(240, 26));
        row1.add(sampleDirField);
        JButton browseSample = new JButton("浏览…");
        browseSample.addActionListener(e -> chooseDir(sampleDirField, "选择样例文件夹"));
        row1.add(browseSample);
        header.add(row1);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        row2.add(new JLabel("输出文件夹:"));
        outputDirField.setPreferredSize(new Dimension(240, 26));
        row2.add(outputDirField);
        JButton browseOutput = new JButton("浏览…");
        browseOutput.addActionListener(e -> chooseDir(outputDirField, "选择输出文件夹"));
        row2.add(browseOutput);

        runButton.addActionListener(e -> runBatch());
        row2.add(javax.swing.Box.createHorizontalStrut(10));
        row2.add(runButton);
        header.add(row2);

        JPanel row3 = new JPanel(new BorderLayout(6, 2));
        row3.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));
        summary.setForeground(new Color(0x55, 0x55, 0x55));
        row3.add(summary, BorderLayout.NORTH);
        progress.setStringPainted(true);
        progress.setVisible(false);
        row3.add(progress, BorderLayout.SOUTH);
        header.add(row3);
        return header;
    }

    private void chooseDir(JTextField target, String title) {
        Path initial = resolve(target.getText());
        JFileChooser chooser = new JFileChooser(initial.toFile());
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private static Path resolve(String text) {
        if (text == null || text.isBlank()) {
            return Paths.get("").toAbsolutePath().normalize();
        }
        Path path = Paths.get(text);
        return path.isAbsolute() ? path : Paths.get("").toAbsolutePath().normalize().resolve(path);
    }

    private void runBatch() {
        if (running) {
            return;
        }
        Path sampleDir = resolve(sampleDirField.getText());
        Path outputDir = resolve(outputDirField.getText());

        List<Sample> samples;
        try {
            if (!Files.isDirectory(sampleDir)) {
                Files.createDirectories(sampleDir);
            }
            samples = BatchRunner.scanSamples(sampleDir);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "扫描失败：" + ex.getMessage(),
                    "批量编译", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (samples.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "在 " + sampleDir + " 里没找到 .txt 样例。\n\n"
                            + "样例可以放在该文件夹本身，或放在它的下一级子目录里。",
                    "批量编译", JOptionPane.WARNING_MESSAGE);
            return;
        }
        model.setRowCount(0);
        outcomes = List.of();
        setRunning(true);
        progress.setVisible(true);
        progress.setMinimum(0);
        progress.setMaximum(samples.size());
        progress.setValue(0);
        summary.setText("扫描到 " + samples.size() + " 个样例，正在编译…");
        statusConsumer.accept("批量编译：0/" + samples.size() + "…");

        CompilerSettings settings = settingsSupplier.get();
        new SwingWorker<List<SampleOutcome>, SampleOutcome>() {
            @Override
            protected List<SampleOutcome> doInBackground() {
                // BatchRunner 内部串行；用 publish 把每个样例的结果实时送回 EDT。
                return BatchRunner.run(samples, outputDir, settings,
                        (index, total, outcome) -> {
                            publish(outcome);
                            SwingUtilities.invokeLater(() -> {
                                progress.setValue(index);
                                summary.setText("正在编译 " + index + "/" + total + "…");
                            });
                        });
            }

            @Override
            protected void process(List<SampleOutcome> chunks) {
                for (SampleOutcome outcome : chunks) {
                    addRow(outcome);
                }
            }

            @Override
            protected void done() {
                setRunning(false);
                progress.setVisible(false);
                try {
                    outcomes = get();
                    model.setRowCount(0);
                    outcomes.forEach(BatchPanel.this::addRow);
                    long passed = BatchRunner.passedCount(outcomes);
                    long withErrorFile = outcomes.stream().filter(SampleOutcome::hasErrorFile).count();
                    summary.setText(String.format(
                            "完成：共 %d 个样例，通过 %d，写出 %d 个 error.txt　→　%s",
                            outcomes.size(), passed, withErrorFile, outputDir));
                    statusConsumer.accept("批量编译完成：通过 " + passed + "/" + outcomes.size()
                            + "，输出到 " + outputDir);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    summary.setText("已中断");
                    statusConsumer.accept("批量编译已中断");
                } catch (ExecutionException ex) {
                    summary.setText("批量编译失败：" + ex.getCause());
                    JOptionPane.showMessageDialog(BatchPanel.this,
                            "批量编译失败：" + ex.getCause(), "批量编译", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void addRow(SampleOutcome outcome) {
        Object[] row = new Object[7];
        row[0] = outcome.sample().display();
        List<compilerui.model.CompileResult> results = outcome.results();
        for (int i = 0; i < 3; i++) {
            if (outcome.failure() != null && results.isEmpty()) {
                row[1 + i] = "—";
            } else if (i < results.size()) {
                int errs = results.get(i).errorCount();
                row[1 + i] = errs == 0 ? "ok" : String.valueOf(errs);
            } else {
                row[1 + i] = "—";
            }
        }
        int finalErrors = outcome.finalErrorCount();
        row[4] = finalErrors < 0 ? "—" : String.valueOf(finalErrors);
        if (outcome.failure() != null && results.isEmpty()) {
            row[5] = "异常";
        } else if (outcome.passed()) {
            row[5] = "通过";
        } else if (finalErrors < 0) {
            row[5] = "解析中断";
        } else {
            row[5] = "有错误";
        }
        row[6] = outcome.outputDir() == null ? "—" : outcome.outputDir().toString();
        model.addRow(row);
    }

    private void setRunning(boolean value) {
        running = value;
        runButton.setEnabled(!value);
        setCursor(Cursor.getPredefinedCursor(value ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private static final class OutcomeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            if (selected) {
                return component;
            }
            String text = String.valueOf(value);
            if (column == 0 || column == 6) {
                component.setForeground(Color.BLACK);
            } else if ("ok".equals(text) || "通过".equals(text)) {
                component.setForeground(new Color(0x00, 0x88, 0x00));
            } else if ("—".equals(text)) {
                component.setForeground(new Color(0x99, 0x99, 0x99));
            } else if ("异常".equals(text)) {
                component.setForeground(new Color(0x99, 0x33, 0xCC));
            } else {
                component.setForeground(new Color(0xCC, 0x00, 0x00));
            }
            return component;
        }
    }
}
