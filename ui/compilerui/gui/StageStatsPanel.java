package compilerui.gui;

import compilerui.model.CompileResult;
import compilerui.model.Stage;
import compilerui.model.StageStats;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;

/**
 * 阶段耗时/规模统计 + 阶段对比视图。
 *
 * <p>上下两张表：
 * <ul>
 *   <li><b>上：阶段统计</b>——每个阶段的耗时、产物行数、规模指标（token / 节点 / 符号）、错误数；</li>
 *   <li><b>下：错误数演变</b>——错误从词法到语义怎么累积的，直接标出"哪个阶段新引入"。</li>
 * </ul>
 *
 * <p>数据全部来自 {@link CompileResult#stats()}，只读展示；<b>不碰任何产物文本</b>，
 * 所以不会影响导出的 lexer/parser/symbol/error 文件。
 */
public final class StageStatsPanel extends JPanel {

    private static final String[] STAT_COLUMNS = {
            "阶段", "状态", "耗时(ms)", "产物行数", "规模", "错误数"};

    private final DefaultTableModel statModel = new DefaultTableModel(STAT_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable statTable = new JTable(statModel);

    private final DefaultTableModel trendModel = new DefaultTableModel(
            new String[]{"阶段", "错误数", "相对上一阶段"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable trendTable = new JTable(trendModel);

    private final JLabel summary = new JLabel("尚未运行");
    /** 点统计表的"错误数"列时回调，用来跳到「错误」页。 */
    private Runnable onErrorColumnClicked = () -> { };

    public StageStatsPanel() {
        super(new BorderLayout(4, 4));

        statTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        statTable.setRowHeight(24);
        statTable.setDefaultRenderer(Object.class, new StatRenderer());
        statTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && statTable.getSelectedColumn() == 5) {
                onErrorColumnClicked.run();
            }
        });

        trendTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        trendTable.setRowHeight(24);
        trendTable.setDefaultRenderer(Object.class, new TrendRenderer());

        // 不给列宽的话 JTable 会按内容平均分配，「阶段」列会被压成 "语义分析 S…"。
        int[] statWidths = {150, 60, 80, 80, 110, 70};
        for (int i = 0; i < statWidths.length; i++) {
            statTable.getColumnModel().getColumn(i).setPreferredWidth(statWidths[i]);
        }
        int[] trendWidths = {150, 90, 120};
        for (int i = 0; i < trendWidths.length; i++) {
            trendTable.getColumnModel().getColumn(i).setPreferredWidth(trendWidths[i]);
        }
        statTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        trendTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        summary.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JPanel top = new JPanel(new BorderLayout());
        top.add(summary, BorderLayout.NORTH);
        top.add(new JScrollPane(statTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JLabel trendTitle = new JLabel("错误数演变（哪个阶段新引入的错误）"
                + "　—　点「错误数」列可跳到「错误」页");
        trendTitle.setForeground(new Color(0x60, 0x60, 0x60));
        trendTitle.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
        bottom.add(trendTitle, BorderLayout.NORTH);
        bottom.add(new JScrollPane(trendTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER);
    }

    /** 设置点「错误数」列时的动作（由主窗口接到「错误」页）。 */
    public void setOnErrorColumnClicked(Runnable action) {
        this.onErrorColumnClicked = action == null ? () -> { } : action;
    }

    /**
     * 用一次编译的结果刷新两张表。
     *
     * @param results 已跑到的阶段 → 结果
     * @param until   用户选的目标阶段
     */
    public void update(Map<Stage, CompileResult> results, Stage until) {
        statModel.setRowCount(0);
        trendModel.setRowCount(0);

        Map<Stage, StageStats> stats = new EnumMap<>(Stage.class);
        Map<Stage, Integer> errorCounts = new EnumMap<>(Stage.class);
        long totalMillis = 0;

        for (Stage stage : Stage.values()) {
            CompileResult result = results.get(stage);
            if (result == null) {
                statModel.addRow(new Object[]{stage.displayName(), "未运行", "—", "—", "—", "—"});
                continue;
            }
            StageStats stageStats = result.stats();
            stats.put(stage, stageStats);
            errorCounts.put(stage, result.errorCount());
            totalMillis += stageStats.millis();
            statModel.addRow(new Object[]{
                    stage.displayName(),
                    result.noError() ? "通过" : "报错",
                    stageStats.millis(),
                    result.outputLines(),
                    stageStats.metricText(),
                    result.errorCount()});
        }

        // 错误数演变：逐阶段比较，指出新增量。
        Integer previous = null;
        for (Stage stage : Stage.values()) {
            Integer count = errorCounts.get(stage);
            if (count == null) {
                continue;
            }
            String delta;
            if (previous == null) {
                delta = count == 0 ? "—" : "新增 " + count;
            } else if (count.equals(previous)) {
                delta = "无变化";
            } else {
                delta = "新增 " + (count - previous);
            }
            trendModel.addRow(new Object[]{stage.displayName(), count, delta});
            previous = count;
        }

        int finalErrors = errorCounts.getOrDefault(until, 0);
        // 分两行写：面板宽度有限，挤一行会被裁掉。
        summary.setText(String.format(
                "<html>跑到 <b>%s</b>，总耗时 <b>%d ms</b>"
                        + "（词法 %d / 语法 %d / 语义 %d）<br>"
                        + "规模：token <b>%d</b>　AST 节点 <b>%d</b>　符号 <b>%d</b>"
                        + "　最终错误 <b>%d</b></html>",
                until.displayName(), totalMillis,
                millisOf(stats.get(Stage.LEXER)), millisOf(stats.get(Stage.SYNTAX)),
                millisOf(stats.get(Stage.SEMANTIC)),
                metricOf(stats.get(Stage.LEXER)), metricOf(stats.get(Stage.SYNTAX)),
                metricOf(stats.get(Stage.SEMANTIC)), finalErrors));
    }

    public void reset() {
        statModel.setRowCount(0);
        trendModel.setRowCount(0);
        summary.setText("尚未运行");
    }

    private static long millisOf(StageStats stats) {
        return stats == null ? 0 : stats.millis();
    }

    private static long metricOf(StageStats stats) {
        return stats == null ? 0 : stats.metric();
    }

    /** 阶段统计着色：报错红色、"通过"绿色。 */
    private static final class StatRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            setHorizontalAlignment(column <= 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
            if (selected) {
                return component;
            }
            String text = String.valueOf(value);
            if (column == 1) {
                if ("通过".equals(text)) {
                    component.setForeground(new Color(0x00, 0x88, 0x00));
                } else if ("报错".equals(text)) {
                    component.setForeground(new Color(0xCC, 0x00, 0x00));
                } else {
                    component.setForeground(new Color(0x99, 0x99, 0x99));
                }
            } else if (column == 5 && !"—".equals(text) && !"0".equals(text)) {
                component.setForeground(new Color(0xCC, 0x00, 0x00));
            } else {
                component.setForeground(Color.BLACK);
            }
            return component;
        }
    }

    /** 趋势着色：新增错误标橙，非零错误标红，零标绿。 */
    private static final class TrendRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
            if (selected) {
                return component;
            }
            String text = String.valueOf(value);
            if (text.startsWith("新增")) {
                component.setForeground(new Color(0xC0, 0x60, 0x00));
            } else if (column == 1 && !"0".equals(text)) {
                component.setForeground(new Color(0xCC, 0x00, 0x00));
            } else if ("0".equals(text)) {
                component.setForeground(new Color(0x00, 0x88, 0x00));
            } else {
                component.setForeground(new Color(0x55, 0x55, 0x55));
            }
            return component;
        }
    }
}
