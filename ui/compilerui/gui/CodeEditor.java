package compilerui.gui;

import compilerui.pipeline.SyntaxHighlighter;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码编辑区：左边行号栏 + 语法高亮 + 按行跳转（供错误列表双击定位）。
 *
 * <p>行号栏是自定义的 {@code row header view}：跟随 {@link JTextPane} 的字体度量，
 * 并且用 {@code setMaxSize} 允许它跟着视口一起滚动。
 */
public final class CodeEditor extends JScrollPane {

    /** 与 {@link #highlightAll()} 里用的高亮色一致，跳转时好擦掉。 */
    private static final Color CURRENT_LINE = new Color(0xFF, 0xF3, 0xC4);

    private final JTextPane pane = new JTextPane();
    private final LineNumberView lineNumbers = new LineNumberView(pane);
    private final Timer highlightTimer;
    private int highlightedLine = -1;

    public CodeEditor() {
        super();
        pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        setViewportView(pane);
        setRowHeaderView(lineNumbers);
        lineNumbers.setBackground(new Color(0xF5, 0xF5, 0xF5));

        // 边打字边高亮会卡，所以节流到停笔 250ms 后重刷。
        highlightTimer = new Timer(250, e -> highlightAll());
        highlightTimer.setRepeats(false);
        pane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                repaintLineNumbersLater();
                highlightTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                repaintLineNumbersLater();
                highlightTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // 属性变化，不需要重算高亮
            }
        });
        pane.addCaretListener(e -> repaintLineNumbersLater());
    }

    private void repaintLineNumbersLater() {
        SwingUtilities.invokeLater(lineNumbers::repaint);
    }

    // ------------------------------------------------------------------ 文本

    public String getText() {
        return pane.getText();
    }

    public void setText(String text) {
        pane.setText(text);
        pane.setCaretPosition(0);
        clearHighlight();
        SwingUtilities.invokeLater(() -> {
            highlightAll();
            lineNumbers.revalidate();
            lineNumbers.repaint();
        });
    }

    public JTextPane pane() {
        return pane;
    }

    public void highlightAll() {
        StyledDocument document = pane.getStyledDocument();
        try {
            SyntaxHighlighter.apply(document);
        } catch (BadLocationException ignored) {
            // 文档正在被替换时可能越界，下一轮会重刷
        }
        lineNumbers.repaint();
    }

    // ------------------------------------------------------------------ 跳转

    /**
     * 把光标移到第 {@code line} 行（从 1 开始），并高亮整行。
     *
     * @return 该行是否是真实存在的行
     */
    public boolean jumpToLine(int line) {
        StyledDocument document = pane.getStyledDocument();
        int totalLines = document.getDefaultRootElement().getElementCount();
        clearHighlight();
        if (line < 1 || line > totalLines) {
            return false;
        }
        var element = document.getDefaultRootElement().getElement(line - 1);
        int start = element.getStartOffset();
        int length = Math.max(0, element.getEndOffset() - start - 1);
        // 整行淡黄底，方便一眼看到出错位置。
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setBackground(style, CURRENT_LINE);
        document.setCharacterAttributes(start, length, style, false);
        highlightedLine = line;
        pane.setCaretPosition(start);
        pane.requestFocusInWindow();
        // 滚动到可见区域中间；位置算不出来就算了，光标已经到位。
        try {
            var rectangle = pane.modelToView2D(start);
            if (rectangle != null) {
                var viewport = getViewport();
                viewport.setViewPosition(new java.awt.Point(
                        0, Math.max(0, (int) rectangle.getY() - viewport.getHeight() / 2)));
            }
        } catch (BadLocationException ignored) {
            // 文档正在变化，忽略本次滚动
        }
        return true;
    }

    /** 擦掉上一条整行高亮（重新上色一遍最省事，也不用记录旧属性）。 */
    public void clearHighlight() {
        if (highlightedLine < 0) {
            return;
        }
        highlightedLine = -1;
        highlightAll();
    }

    // ------------------------------------------------------------------ 行号栏

    private static final class LineNumberView extends javax.swing.JComponent {
        private final JTextPane target;

        LineNumberView(JTextPane target) {
            this.target = target;
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            setForeground(new Color(0x99, 0x99, 0x99));
        }

        private List<Integer> lineStarts() {
            var root = target.getDocument().getDefaultRootElement();
            List<Integer> starts = new ArrayList<>(root.getElementCount());
            for (int i = 0; i < root.getElementCount(); i++) {
                starts.add(root.getElement(i).getStartOffset());
            }
            return starts;
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            int width = Math.max(getFontMetrics(getFont()).stringWidth("999"),
                    getFontMetrics(getFont()).stringWidth(Integer.toString(Math.max(1, lineStarts().size())))) + 12;
            return new java.awt.Dimension(width, target.getHeight());
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            var insets = target.getInsets();
            var metrics = getFontMetrics(getFont());
            int lineHeight = metrics.getHeight();
            int y = insets.top;
            java.awt.Rectangle clip = graphics.getClipBounds();
            List<Integer> starts = lineStarts();
            int caretLine = currentCaretLine();
            for (int index = 0; index < starts.size(); index++) {
                try {
                    var rectangle = target.modelToView2D(starts.get(index));
                    if (rectangle == null) {
                        continue;
                    }
                    int baseline = (int) rectangle.getY() + metrics.getAscent();
                    // 只画可见区间，行数多时也不卡。
                    if (baseline >= clip.y - lineHeight && baseline <= clip.y + clip.height + lineHeight) {
                        String label = Integer.toString(index + 1);
                        int x = getWidth() - metrics.stringWidth(label) - 6;
                        graphics.setColor(index + 1 == caretLine
                                ? new Color(0x33, 0x33, 0x33) : getForeground());
                        graphics.drawString(label, x, baseline);
                    }
                    y = baseline;
                } catch (BadLocationException ignored) {
                    // 文档变化中，跳过这一帧
                }
            }
        }

        private int currentCaretLine() {
            var root = target.getDocument().getDefaultRootElement();
            return root.getElementIndex(target.getCaretPosition()) + 1;
        }
    }
}
