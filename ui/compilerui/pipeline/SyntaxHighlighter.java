package compilerui.pipeline;

import frontend.token.Keywords;
import frontend.token.Symbols;
import frontend.token.TokenType;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 编辑区的 SysY 语法高亮。
 *
 * <p>自己写了个很轻的扫描器：只区分注释 / 字符串 / 字符 / 数字 / 标识符 / 运算符，
 * 足够上色用。编译器自己的 {@code frontend.Lexer} 是<b>从文件读</b>的、
 * 还会往 {@code Controller.errors} 里写错误，不适合给"边打字边高亮"当后端，
 * 所以这里刻意不复用它。
 */
public final class SyntaxHighlighter {

    private static final Color KEYWORD = new Color(0x00, 0x00, 0xCC);
    private static final Color OPERATOR = new Color(0x88, 0x66, 0x00);
    private static final Color NUMBER = new Color(0x00, 0x88, 0x00);
    private static final Color CHAR_LITERAL = new Color(0xCC, 0x00, 0x88);
    private static final Color STRING_LITERAL = new Color(0x00, 0x80, 0x80);
    private static final Color COMMENT = new Color(0x99, 0x99, 0x99);
    private static final Color PLAIN = new Color(0x20, 0x20, 0x20);

    private SyntaxHighlighter() { }

    /** 重新给整个文档上色。文档长的时候会有点慢，所以调用方可以节流。 */
    public static void apply(StyledDocument document) throws BadLocationException {
        String text = document.getText(0, document.getLength());
        SimpleAttributeSet style = new SimpleAttributeSet();
        // 先全部刷成普通色，再逐个覆盖。
        StyleConstants.setForeground(style, PLAIN);
        document.setCharacterAttributes(0, text.length(), style, true);

        for (Span span : scan(text)) {
            SimpleAttributeSet color = new SimpleAttributeSet();
            StyleConstants.setForeground(color, colorOf(span.kind()));
            if (span.kind() == Kind.COMMENT || span.kind() == Kind.STRING) {
                StyleConstants.setItalic(color, true);
            }
            if (span.kind() == Kind.KEYWORD) {
                StyleConstants.setBold(color, true);
            }
            document.setCharacterAttributes(span.start(), span.length(), color, true);
        }
    }

    private static Color colorOf(Kind kind) {
        return switch (kind) {
            case KEYWORD -> KEYWORD;
            case OPERATOR -> OPERATOR;
            case NUMBER -> NUMBER;
            case CHAR -> CHAR_LITERAL;
            case STRING -> STRING_LITERAL;
            case COMMENT -> COMMENT;
            case IDENT, PLAIN -> PLAIN;
        };
    }

    private enum Kind { KEYWORD, OPERATOR, NUMBER, CHAR, STRING, COMMENT, IDENT, PLAIN }

    private record Span(int start, int length, Kind kind) { }

    /** 单遍扫描出所有需要上色的区间；未覆盖的部分保持 PLAIN。 */
    private static List<Span> scan(String text) {
        List<Span> spans = new ArrayList<>();
        int i = 0;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);

            // 注释：// 到行尾，/* 到 */
            if (c == '/' && i + 1 < length && text.charAt(i + 1) == '/') {
                int end = text.indexOf('\n', i);
                end = end < 0 ? length : end;
                spans.add(new Span(i, end - i, Kind.COMMENT));
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                end = end < 0 ? length : end + 2;
                spans.add(new Span(i, end - i, Kind.COMMENT));
                i = end;
                continue;
            }
            // 字符串 / 字符字面量，支持反斜杠转义
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < length) {
                    char inner = text.charAt(j);
                    if (inner == '\\') {
                        j += 2;
                        continue;
                    }
                    if (inner == c || inner == '\n') {
                        break;
                    }
                    j++;
                }
                int end = Math.min(j + 1, length);
                spans.add(new Span(i, end - i, c == '"' ? Kind.STRING : Kind.CHAR));
                i = end;
                continue;
            }
            // 数字：十进制整数（SysY 只有 int/char 常量）
            if (Character.isDigit(c)) {
                int j = i;
                while (j < length && Character.isDigit(text.charAt(j))) {
                    j++;
                }
                spans.add(new Span(i, j - i, Kind.NUMBER));
                i = j;
                continue;
            }
            // 标识符 / 关键字
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < length && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) {
                    j++;
                }
                String word = text.substring(i, j);
                if (Keywords.TABLE.containsKey(word)) {
                    spans.add(new Span(i, j - i, Kind.KEYWORD));
                } else {
                    spans.add(new Span(i, j - i, Kind.IDENT));
                }
                i = j;
                continue;
            }
            // 运算符 / 分隔符：两字符优先，再单字符
            if (i + 1 < length && isTwoCharOperator(text.substring(i, i + 2))) {
                spans.add(new Span(i, 2, Kind.OPERATOR));
                i += 2;
                continue;
            }
            if (Symbols.TABLE.containsKey(c) || isSingleCharOperator(c)) {
                spans.add(new Span(i, 1, Kind.OPERATOR));
                i++;
                continue;
            }
            i++;
        }
        return spans;
    }

    private static boolean isTwoCharOperator(String pair) {
        return switch (pair) {
            case "==", "!=", "<=", ">=", "&&", "||" -> true;
            default -> false;
        };
    }

    private static boolean isSingleCharOperator(char c) {
        return c == '!' || c == '<' || c == '>' || c == '=';
    }

    /** 供速查面板复用：TokenType → 是否关键字。 */
    public static boolean isKeywordToken(TokenType type) {
        return Keywords.TABLE.containsValue(type);
    }
}
