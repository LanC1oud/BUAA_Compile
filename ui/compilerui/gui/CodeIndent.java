package compilerui.gui;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import java.awt.AWTKeyStroke;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/** 源码编辑区的软 Tab：插入四个空格，选中代码时整体右移。 */
final class CodeIndent {
    private static final String INDENT = "    ";

    static void install(JTextComponent editor) {
        KeyStroke tab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
        int forward = KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS;
        Set<AWTKeyStroke> traversal = new HashSet<>(editor.getFocusTraversalKeys(forward));
        traversal.remove(tab);
        editor.setFocusTraversalKeys(forward, traversal);
        editor.getInputMap().put(tab, "indent-code");
        editor.getActionMap().put("indent-code", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int start = editor.getSelectionStart();
                int end = editor.getSelectionEnd();
                if (start == end) {
                    editor.replaceSelection(INDENT);
                    return;
                }

                Document doc = editor.getDocument();
                Element lines = doc.getDefaultRootElement();
                int first = lines.getElementIndex(start);
                // 选区恰好结束在下一行行首时，那一行不参与缩进。
                int last = lines.getElementIndex(end - 1);
                try {
                    for (int i = last; i >= first; i--) {
                        doc.insertString(lines.getElement(i).getStartOffset(), INDENT, null);
                    }
                } catch (BadLocationException ex) {
                    throw new IllegalStateException(ex);
                }
                editor.select(start + INDENT.length(), end + (last - first + 1) * INDENT.length());
            }
        });
    }
}
