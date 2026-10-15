package compilerui.gui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * AST 折叠视图。
 *
 * <p><b>这是近似分组，不是精确语法树。</b> {@code ParserWatcher} 输出的是扁平先序文本
 * （{@code <NodeName>} 与 token 行混在一起），没有嵌套标记，仅凭它无法还原真实父子关系。
 * 所以这里做的分组规则很直白：
 * <ul>
 *   <li>遇到非终结符行 {@code <NodeName>} 就建一个节点；</li>
 *   <li>紧随其后的终结符行（token）全部挂到它下面；</li>
 *   <li>缩进只作视觉提示，不代表真实层级。</li>
 * </ul>
 * 想要真正的树，得让 {@code Parser.emit()} 输出带嵌套结构或 JSON——那属于后端改动。
 */
public final class AstTreePanel extends JPanel {

    private static final String DISCLAIMER =
            "近似分组视图：按先序文本把 token 挂在最近的非终结符下，缩进不代表真实层级。"
                    + "精确语法树需要后端输出嵌套结构。";

    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("（尚未运行）");
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final JTree tree = new JTree(model);
    private final JLabel note = new JLabel();

    public AstTreePanel() {
        super(new BorderLayout(4, 4));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);

        note.setText("<html><i>" + DISCLAIMER + "</i></html>");
        note.setForeground(new Color(0x70, 0x70, 0x70));
        note.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 4, 2, 4));

        add(note, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    /** 用语法阶段的扁平文本重建视图。传空文本就清空。 */
    public void setAstText(String astText) {
        DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode("编译单元");
        if (astText == null || astText.isBlank()) {
            newRoot.add(new DefaultMutableTreeNode("（没有 AST 文本——需要勾选「Token+AST」调试开关后重跑）"));
            model.setRoot(newRoot);
            return;
        }
        DefaultMutableTreeNode currentNonTerminal = null;
        for (String rawLine : astText.lines().toList()) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("<") && line.endsWith(">")) {
                currentNonTerminal = new DefaultMutableTreeNode(line);
                newRoot.add(currentNonTerminal);
            } else if (currentNonTerminal != null) {
                // 终结符挂到最近的非终结符下。
                currentNonTerminal.add(new DefaultMutableTreeNode(line));
            }
        }
        if (newRoot.getChildCount() == 0) {
            newRoot.add(new DefaultMutableTreeNode("（没解析出节点，可能是词法/语法阶段就报错了）"));
        }
        model.setRoot(newRoot);
        expandFirstLevels();
    }

    private void expandFirstLevels() {
        // 展开到第 2 层：既能看到整体结构，也不至于刷出几万行。
        int row = 0;
        while (row < tree.getRowCount()) {
            tree.expandRow(row);
            row++;
        }
        for (int i = tree.getRowCount() - 1; i >= 0; i--) {
            if (tree.getPathForRow(i).getPathCount() > 3) {
                tree.collapseRow(i);
            }
        }
    }
}
