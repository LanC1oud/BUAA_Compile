package compilerui.gui;

import compilerui.pipeline.SyntaxReference;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;

/**
 * 文法速查面板：关键字、运算符、转义字符。
 *
 * <p>数据全部来自 {@code src/frontend/token/} 的三张表（见 {@link SyntaxReference}），
 * 这里只负责排版；表格不可编辑，纯展示。
 */
public final class SyntaxPanel extends JPanel {

    public SyntaxPanel() {
        super(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        for (SyntaxReference.Section section : SyntaxReference.sections()) {
            tabs.addTab(section.title(), buildSection(section));
        }
        add(tabs, BorderLayout.CENTER);
    }

    private static JPanel buildSection(SyntaxReference.Section section) {
        String[] columns = {"写法", "Token 类型", "说明"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (SyntaxReference.Entry entry : section.entries()) {
            model.addRow(new Object[]{entry.shown(), entry.category(), entry.description()});
        }
        JTable table = new JTable(model);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(420);
        table.setAutoCreateRowSorter(true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JLabel note = new JLabel("<html><i>词法阶段就按这张表识别；"
                + "转义字符只在字符/字符串常量里生效。</i></html>");
        note.setForeground(new java.awt.Color(0x70, 0x70, 0x70));
        note.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        panel.add(note, BorderLayout.SOUTH);
        return panel;
    }

    /** 供"关于"或测试用的条目总数。 */
    public static int entryCount() {
        List<SyntaxReference.Section> sections = SyntaxReference.sections();
        int total = 0;
        for (SyntaxReference.Section section : sections) {
            total += section.entries().size();
        }
        return total;
    }
}
