package compilerui.gui;

import compilerui.model.CompileResult;
import compilerui.model.CompilerSettings;
import compilerui.model.DisplayError;
import compilerui.model.Stage;
import compilerui.pipeline.Pipeline;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * 编译器前端外壳（Swing）。
 *
 * <p>传文本代码 → 选编译到哪一步 → 选导出目录 → 运行 / 导出。
 *
 * <p>界面只依赖 {@link Pipeline} 这一层接口，不碰 {@code src/} 的实现细节。
 * 错误展示是"派生视图"：读 {@link Pipeline#displayErrors()} 里的结构化条目重新排版，
 * {@code error.txt} 的格式（{@code ErrorTable.toString()}）保持不变。
 */
public final class CompilerUi extends JFrame {

    private static final String TITLE = "Compile by LanC1oud";
    private static final String PREF_FILE = ".compiler-ui.properties";
    private static final Path WORK_DIR = Paths.get("").toAbsolutePath().normalize();

    /** 错误表列：主格式一列，中文说明和源码行各一列。 */
    private static final String[] ERROR_COLUMNS = {"位置与错误码", "说明", "源码"};

    // ---- 组件 ----
    private final CodeEditor editor = new CodeEditor();
    private final JTabbedPane outputTabs = new JTabbedPane();
    private final JTabbedPane resultTabs = new JTabbedPane();
    private final TextPanel payloadArea = new TextPanel();
    private final TextPanel logArea = new TextPanel();
    private final AstTreePanel astPanel = new AstTreePanel();
    private final SyntaxPanel syntaxPanel = new SyntaxPanel();
    private final StageStatsPanel statsPanel = new StageStatsPanel();

    private final DefaultTableModel errorModel;
    private final JTable errorTable;

    private final JTextField dirField = new JTextField();
    private final JTextField fileNameField = new JTextField();
    private final JLabel statusLabel = new JLabel("就绪");
    private final JButton runButton = new JButton("▶ 运行");
    private final JButton exportButton = new JButton("导出结果");
    private final JCheckBox trailingNewlineBox = new JCheckBox("末尾换行", false);
    private final JCheckBox debugTokens = new JCheckBox("Token");
    private final JCheckBox debugAst = new JCheckBox("Token+AST", true);
    private final JCheckBox debugSymbols = new JCheckBox("符号表");
    private final JCheckBox debugErrors = new JCheckBox("错误表", true);
    private final Map<Stage, JRadioButton> stageButtons = new EnumMap<>(Stage.class);

    // ---- 状态 ----
    private final Pipeline pipeline = new Pipeline();
    private CompilerSettings settings = CompilerSettings.defaults();
    private Map<Stage, CompileResult> lastResults = new EnumMap<>(Stage.class);
    /** 与 {@link #errorModel} 的行一一对应，供点行跳转时取出结构化错误。 */
    private List<DisplayError> lastDisplayErrors = List.of();
    private boolean running = false;

    public CompilerUi() {
        super(TITLE);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 620));

        errorModel = new DefaultTableModel(ERROR_COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        errorTable = new JTable(errorModel);

        setContentPane(buildRoot());
        applyFont(getContentPane());
        loadPreferences();
        pack();
        setSize(1240, 760);
        setLocationRelativeTo(null);
    }

    // ==================================================================
    // 界面搭建
    // ==================================================================

    private JComponent buildRoot() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 10, 8, 10));
        root.add(buildTop(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, buildOutputTabs());
        split.setResizeWeight(0.52);
        split.setDividerLocation(640);
        root.add(split, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.add(statusLabel, BorderLayout.WEST);
        JLabel hint = new JLabel("提示：Cmd/Ctrl+Enter 运行；点错误行即跳到对应源码");
        hint.setForeground(new Color(0x70, 0x70, 0x70));
        south.add(hint, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildTop() {
        JPanel top = new JPanel();
        top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
        top.add(buildStageRow());
        top.add(buildConfigRow());
        return top;
    }

    /** 行 1：阶段单选 + 源文件操作。 */
    private JComponent buildStageRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        row.setBorder(titled("编译到哪一步"));
        ButtonGroup group = new ButtonGroup();
        for (Stage stage : Stage.values()) {
            JRadioButton radio = new JRadioButton(stage.displayName());
            radio.addActionListener(e -> onStageChanged());
            group.add(radio);
            stageButtons.put(stage, radio);
            row.add(radio);
        }
        stageButtons.get(Stage.SEMANTIC).setSelected(true);

        row.add(javax.swing.Box.createHorizontalStrut(14));
        JButton open = new JButton("打开文件…");
        open.addActionListener(e -> openSourceFile());
        JButton clear = new JButton("清空");
        clear.addActionListener(e -> editor.setText(""));
        row.add(open);
        row.add(clear);
        return row;
    }

    /** 行 2：导出参数 + 调试开关 + 动作。 */
    private JComponent buildConfigRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        row.setBorder(titled("导出与调试"));

        row.add(new JLabel("目录:"));
        dirField.setPreferredSize(new Dimension(280, 26));
        row.add(dirField);
        JButton browse = new JButton("浏览…");
        browse.addActionListener(e -> chooseDirectory());
        row.add(browse);
        row.add(new JLabel("文件名:"));
        fileNameField.setPreferredSize(new Dimension(110, 26));
        row.add(fileNameField);
        row.add(trailingNewlineBox);

        row.add(javax.swing.Box.createHorizontalStrut(10));
        JPanel debug = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        debug.setBorder(BorderFactory.createTitledBorder("调试输出"));
        debug.add(debugTokens);
        debug.add(debugAst);
        debug.add(debugSymbols);
        debug.add(debugErrors);
        row.add(debug);

        row.add(javax.swing.Box.createHorizontalStrut(10));
        runButton.addActionListener(e -> runCompile());
        exportButton.addActionListener(e -> exportResult());
        exportButton.setEnabled(false);
        row.add(runButton);
        row.add(exportButton);
        return row;
    }

    private JComponent buildOutputTabs() {
        // 统计面板里点「错误数」列 → 切到「错误」页
        statsPanel.setOnErrorColumnClicked(() -> outputTabs.setSelectedIndex(1));

        resultTabs.addTab("编译结果", payloadArea);
        resultTabs.addTab("近似 AST", astPanel);
        outputTabs.addTab("输出", resultTabs);
        outputTabs.addTab("错误", buildErrorTab());
        outputTabs.addTab("阶段对比", statsPanel);
        outputTabs.addTab("文法速查", syntaxPanel);
        outputTabs.addTab("日志", logArea);
        return outputTabs;
    }

    private JComponent buildErrorTab() {
        errorTable.setRowHeight(24);
        errorTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        errorTable.getColumnModel().getColumn(0).setPreferredWidth(330);
        errorTable.getColumnModel().getColumn(1).setPreferredWidth(230);
        errorTable.getColumnModel().getColumn(2).setPreferredWidth(280);
        errorTable.setDefaultRenderer(Object.class, new ErrorRenderer());
        // 点一行就跳到源码对应行。
        errorTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                jumpToSelectedError();
            }
        });

        JPanel panel = new JPanel(new BorderLayout());
        JLabel hint = new JLabel("<html>格式：<b>line行号：错误码——错误类型英文名</b>"
                + "（例 line5：i——ExpectedSemicolon）。点一行，编辑器跳到该行。"
                + "<b>导出的 error 文件仍是评测要求的原始格式，未做任何改动。</b></html>");
        hint.setForeground(new Color(0x70, 0x70, 0x70));
        hint.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        panel.add(hint, BorderLayout.NORTH);
        panel.add(new JScrollPane(errorTable), BorderLayout.CENTER);
        return panel;
    }

    private static javax.swing.border.Border titled(String title) {
        return BorderFactory.createTitledBorder(title);
    }

    /** macOS 下默认字体常常缺中文字形，按系统字体逐级回退。 */
    private static void applyFont(Component component) {
        Font base = new JLabel().getFont();
        if (base == null) {
            return;
        }
        Font chosen = base;
        for (String family : new String[]{"PingFang SC", "Hiragino Sans GB",
                "Microsoft YaHei", "Noto Sans CJK SC"}) {
            Font candidate = new Font(family, base.getStyle(), base.getSize());
            if (candidate.getFamily().equalsIgnoreCase(family)) {
                chosen = candidate;
                break;
            }
        }
        applyFontRecursive(component, chosen);
    }

    private static void applyFontRecursive(Component component, Font font) {
        if (!(component instanceof JTable) && !(component instanceof javax.swing.JTree)) {
            component.setFont(font);
        }
        if (component instanceof javax.swing.text.JTextComponent) {
            component.setFont(new Font(Font.MONOSPACED, Font.PLAIN, font.getSize()));
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                applyFontRecursive(child, font);
            }
        }
    }

    // ==================================================================
    // 动作
    // ==================================================================

    private Stage selectedStage() {
        for (Map.Entry<Stage, JRadioButton> entry : stageButtons.entrySet()) {
            if (entry.getValue().isSelected()) {
                return entry.getKey();
            }
        }
        return Stage.SEMANTIC;
    }

    /** 换阶段时同步默认文件名。 */
    private void onStageChanged() {
        Stage stage = selectedStage();
        settings = settings.withStage(stage);
        fileNameField.setText(settings.fileName());
        setStatus("目标阶段：" + stage.displayName());
    }

    private CompilerSettings currentSettings() {
        String fileName = fileNameField.getText().trim();
        return settings
                .withOutputDir(dirField.getText().trim())
                .withFileName(fileName.isEmpty() ? selectedStage().defaultFileName() : fileName)
                .withTrailingNewline(trailingNewlineBox.isSelected())
                .withDebug(debugTokens.isSelected(), debugSymbols.isSelected(),
                        debugAst.isSelected(), debugErrors.isSelected());
    }

    private void openSourceFile() {
        JFileChooser chooser = new JFileChooser(WORK_DIR.toFile());
        chooser.setDialogTitle("选择 SysY 源文件");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "SysY 源文件 (*.sy, *.sysy, *.txt, *.bak)", "sy", "sysy", "txt", "bak"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Path path = chooser.getSelectedFile().toPath();
            editor.setText(Files.readString(path, StandardCharsets.UTF_8));
            setStatus("已载入 " + path.getFileName() + "（" + path + "）");
        } catch (IOException ex) {
            errorDialog("读取文件失败", ex);
        }
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser(resolveOutputDir(dirField.getText()).toFile());
        chooser.setDialogTitle("选择导出目录");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            dirField.setText(chooser.getSelectedFile().getAbsolutePath());
            savePreferences();
        }
    }

    private void runCompile() {
        if (running) {
            return;
        }
        String code = editor.getText();
        if (code.isBlank()) {
            errorDialog("没有代码", new IOException("左侧代码区是空的，请先粘贴或打开一个源文件。"));
            return;
        }
        Stage until = selectedStage();
        pipeline.setSettings(currentSettings());
        setRunning(true);
        setStatus("正在编译到「" + until.displayName() + "」…");

        new SwingWorker<Map<Stage, CompileResult>, Void>() {
            private Path temp;

            @Override
            protected Map<Stage, CompileResult> doInBackground() throws Exception {
                temp = Files.createTempFile("compiler-ui-", ".sy");
                Files.writeString(temp, code, StandardCharsets.UTF_8);
                pipeline.compile(temp.toString(), until);
                Map<Stage, CompileResult> collected = new EnumMap<>(Stage.class);
                for (Stage stage : Stage.values()) {
                    CompileResult result = pipeline.resultOf(stage);
                    if (result != null) {
                        collected.put(stage, result);
                    }
                }
                return collected;
            }

            @Override
            protected void done() {
                setRunning(false);
                try {
                    Map<Stage, CompileResult> collected = get();
                    lastResults = collected;
                    exportButton.setEnabled(!collected.isEmpty());
                    showStage(until);
                    populateErrors(pipeline.displayErrors());
                    statsPanel.update(collected, until);

                    String crash = pipeline.crashMessage();
                    if (crash != null) {
                        // 解析器直接抛异常，不是正常的错误恢复：给一段说明而不是堆栈。
                        showCrashNotice(crash, until);
                        setStatus("「" + until.displayName() + "」解析中断：" + crash);
                        return;
                    }
                    CompileResult last = collected.get(until);
                    if (last == null) {
                        setStatus("没有产出结果");
                    } else if (last.noError()) {
                        setStatus("「" + until.displayName() + "」通过，可以导出了。");
                    } else {
                        outputTabs.setSelectedIndex(1);
                        setStatus("「" + until.displayName() + "」报错 "
                                + lastDisplayErrors.size() + " 处；点错误行可跳到源码。");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setStatus("已中断");
                } catch (ExecutionException ex) {
                    errorModel.setRowCount(0);
                    lastDisplayErrors = List.of();
                    statsPanel.reset();
                    payloadArea.setText(stackTrace(ex.getCause()));
                    outputTabs.setSelectedIndex(0);
                    setStatus("编译失败：" + ex.getCause());
                } finally {
                    if (temp != null) {
                        try {
                            Files.deleteIfExists(temp);
                        } catch (IOException ignored) {
                            // 临时文件删不掉不影响使用
                        }
                    }
                }
            }
        }.execute();
    }

    /**
     * 解析器中途抛异常时的说明。
     *
     * <p>这不是界面崩了，而是编译器对某些严重非法输入<b>不走错误恢复</b>、直接抛异常。
     * 把它说清楚，比丢一坨 Java 堆栈有用得多。
     */
    private void showCrashNotice(String crash, Stage until) {
        payloadArea.setText("""
                编译器在「%s」阶段中断（这是编译器本身的健壮性问题，不是界面故障）。

                %s

                说明：遇到严重非法输入时，Parser 会直接抛异常而不是像其他情况那样
                记录错误码继续解析。已经跑完的阶段的产物仍可在「输出」页查看，
                错误表里也已收录中断前记录到的错误。

                （仓库里的 testfile.invalid-sysy.bak 就是一个会触发这种情况的例子。）
                """.formatted(until.displayName(), crash));
        resultTabs.setSelectedIndex(0);
        outputTabs.setSelectedIndex(0);
    }

    private void showStage(Stage stage) {
        CompileResult result = lastResults.get(stage);
        if (result == null) {
            payloadArea.setText("（尚未运行到「" + stage.displayName() + "」阶段，点击「▶ 运行」）");
            logArea.setText("");
            astPanel.setAstText(null);
            return;
        }
        payloadArea.setText(result.noError()
                ? orEmpty(result.payload())
                : "本阶段未通过，详见「错误」页。");
        logArea.setText(orEmpty(result.log()));
        // 近似 AST 用语法阶段的原始文本；没有就提示去勾 Token+AST 开关。
        CompileResult syntax = lastResults.get(Stage.SYNTAX);
        astPanel.setAstText(syntax == null ? null : syntax.payload());
    }

    /**
     * 用结构化错误条目填表。
     *
     * <p>主格式由 {@link DisplayError#headline()} 给出：
     * {@code line行号：错误码——错误类型英文名}。
     * 这里完全没碰 {@code ErrorTable.toString()}，所以导出的 error 文件
     * 仍是评测要的 {@code "行 错误码"} 格式。
     */
    private void populateErrors(List<DisplayError> errors) {
        lastDisplayErrors = errors == null ? List.of() : List.copyOf(errors);
        errorModel.setRowCount(0);
        if (lastDisplayErrors.isEmpty()) {
            return;
        }
        List<String> sourceLines = editor.getText().lines().toList();
        for (DisplayError error : lastDisplayErrors) {
            int line = error.line();
            String source = line >= 1 && line <= sourceLines.size()
                    ? sourceLines.get(line - 1).strip() : "";
            errorModel.addRow(new Object[]{error.headline(), error.chineseDescription(), source});
        }
    }

    /** 点错误行 → 编辑器跳到该行（并在状态栏给出完整提示）。 */
    private void jumpToSelectedError() {
        int row = errorTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = errorTable.convertRowIndexToModel(row);
        if (modelRow < 0 || modelRow >= lastDisplayErrors.size()) {
            return;
        }
        DisplayError error = lastDisplayErrors.get(modelRow);
        if (editor.jumpToLine(error.line())) {
            setStatus("已跳到 " + error.detail());
        }
    }

    // ==================================================================
    // 导出
    // ==================================================================

    private Path resolveOutputDir(String text) {
        if (text == null || text.isBlank()) {
            return WORK_DIR;
        }
        Path path = Paths.get(text);
        return path.isAbsolute() ? path : WORK_DIR.resolve(path);
    }

    private void exportResult() {
        Stage stage = selectedStage();
        CompileResult chosen = lastResults.get(stage);
        if (chosen == null && !lastResults.isEmpty()) {
            chosen = lastResults.get(
                    lastResults.keySet().stream().reduce((a, b) -> b).orElse(stage));
        }
        if (chosen == null) {
            errorDialog("还没有结果", new IOException("请先点击「▶ 运行」生成编译结果。"));
            return;
        }
        Path dir = resolveOutputDir(dirField.getText());
        String fileName = fileNameField.getText().isBlank()
                ? chosen.stage().defaultFileName() : fileNameField.getText().trim();
        Path target = dir.resolve(fileName);
        try {
            Files.createDirectories(dir);
            // 这里写的就是产物原文：有错时与 error.txt 同格式，没有被界面改写。
            Files.writeString(target,
                    chosen.outputText() + (trailingNewlineBox.isSelected() ? "\n" : ""),
                    StandardCharsets.UTF_8);
            setStatus("已导出 " + chosen.stage().displayName() + " → " + target
                    + (chosen.noError() ? "" : "（内容为原始错误格式）"));
            savePreferences();
        } catch (IOException ex) {
            errorDialog("导出失败", ex);
        }
    }

    // ==================================================================
    // 偏好设置
    // ==================================================================

    private void loadPreferences() {
        Path file = WORK_DIR.resolve(PREF_FILE);
        CompilerSettings base = CompilerSettings.defaults();
        if (Files.isRegularFile(file)) {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(file)) {
                properties.load(in);
                base = new CompilerSettings(
                        parseStage(properties.getProperty("stage"), base.stage()),
                        properties.getProperty("outputDir", base.outputDir()),
                        properties.getProperty("fileName", base.fileName()),
                        Boolean.parseBoolean(properties.getProperty("trailingNewline", "false")),
                        Boolean.parseBoolean(properties.getProperty("displayTokens", "false")),
                        Boolean.parseBoolean(properties.getProperty("displaySymbols", "false")),
                        Boolean.parseBoolean(properties.getProperty("displayTokensWithAst", "true")),
                        Boolean.parseBoolean(properties.getProperty("displayErrors", "true")));
            } catch (IOException ignored) {
                // 配置坏了就用默认值
            }
        }
        settings = base;
        applySettingsToWidgets();
    }

    private void applySettingsToWidgets() {
        JRadioButton radio = stageButtons.get(settings.stage());
        if (radio != null) {
            radio.setSelected(true);
        }
        dirField.setText(settings.outputDir());
        fileNameField.setText(settings.fileName());
        trailingNewlineBox.setSelected(settings.trailingNewline());
        debugTokens.setSelected(settings.displayTokens());
        debugAst.setSelected(settings.displayTokensWithAst());
        debugSymbols.setSelected(settings.displaySymbols());
        debugErrors.setSelected(settings.displayErrors());
    }

    private static Stage parseStage(String value, Stage fallback) {
        if (value != null) {
            for (Stage stage : Stage.values()) {
                if (stage.name().equalsIgnoreCase(value.trim())) {
                    return stage;
                }
            }
        }
        return fallback;
    }

    private void savePreferences() {
        Properties properties = new Properties();
        CompilerSettings compile = currentSettings();
        properties.setProperty("stage", compile.stage().name());
        properties.setProperty("outputDir", compile.outputDir() == null ? "" : compile.outputDir());
        properties.setProperty("fileName", compile.fileName() == null ? "" : compile.fileName());
        properties.setProperty("trailingNewline", Boolean.toString(compile.trailingNewline()));
        properties.setProperty("displayTokens", Boolean.toString(compile.displayTokens()));
        properties.setProperty("displaySymbols", Boolean.toString(compile.displaySymbols()));
        properties.setProperty("displayTokensWithAst", Boolean.toString(compile.displayTokensWithAst()));
        properties.setProperty("displayErrors", Boolean.toString(compile.displayErrors()));
        try {
            Files.createDirectories(WORK_DIR);
            try (var out = Files.newOutputStream(WORK_DIR.resolve(PREF_FILE))) {
                properties.store(out, "compiler-ui preferences");
            }
        } catch (IOException ignored) {
            // 存偏好失败不该打断使用
        }
    }

    // ==================================================================
    // 小工具
    // ==================================================================

    private void setRunning(boolean value) {
        running = value;
        runButton.setEnabled(!value);
        exportButton.setEnabled(!value && !lastResults.isEmpty());
        setCursor(Cursor.getPredefinedCursor(value ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void errorDialog(String title, Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stackTrace(Throwable throwable) {
        StringBuilder builder = new StringBuilder(String.valueOf(throwable)).append('\n');
        for (StackTraceElement element : throwable.getStackTrace()) {
            builder.append("    at ").append(element).append('\n');
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CompilerUi().setVisible(true));
    }

    /** 错误表着色：错误码一列红色，说明黑色，源码行灰色。 */
    private static final class ErrorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            if (!selected) {
                if (column == 0) {
                    component.setForeground(new Color(0xCC, 0x00, 0x00));
                } else if (column == 1) {
                    component.setForeground(new Color(0x33, 0x33, 0x33));
                } else {
                    component.setForeground(new Color(0x77, 0x77, 0x77));
                }
            }
            return component;
        }
    }

    /** 只读文本面板：正文和日志共用。 */
    private static final class TextPanel extends JPanel {
        private final JTextArea area = new JTextArea();

        TextPanel() {
            super(new BorderLayout());
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            add(new JScrollPane(area), BorderLayout.CENTER);
        }

        void setText(String text) {
            area.setText(text == null ? "" : text);
            area.setCaretPosition(0);
        }
    }
}
