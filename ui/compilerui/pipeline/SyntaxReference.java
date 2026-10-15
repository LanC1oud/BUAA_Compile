package compilerui.pipeline;

import frontend.token.Escapes;
import frontend.token.Keywords;
import frontend.token.Symbols;
import frontend.token.TokenType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文法速查数据：直接消费 {@code src/} 里已有的三张表，
 * 不复制一份到这里——那样两边会漂移。
 *
 * <p>顺带给每个条目配了中文说明。说明是<b>界面层的展示文本</b>，
 * 加错了不会影响编译器行为，改起来也就不用碰 {@code src/}。
 */
public final class SyntaxReference {

    /** 一条速查记录。 */
    public record Entry(String shown, String category, String description) { }

    /** 一个分类下的全部条目。 */
    public record Section(String title, List<Entry> entries) { }

    private static final Map<String, String> KEYWORD_NOTES = Map.ofEntries(
            Map.entry("const", "常量声明，值不可再被赋值（对应错误 h）"),
            Map.entry("int", "32 位有符号整数类型"),
            Map.entry("char", "字符类型，与 int 严格区分"),
            Map.entry("void", "函数返回类型：无返回值"),
            Map.entry("main", "程序入口函数名，是关键字而非标识符（不进符号表）"),
            Map.entry("if", "条件分支"),
            Map.entry("else", "if 的否定分支"),
            Map.entry("while", "while 循环"),
            Map.entry("break", "跳出循环（循环外使用报错 m）"),
            Map.entry("continue", "进入下一轮循环（循环外使用报错 m）"),
            Map.entry("return", "函数返回"),
            Map.entry("switch", "多路分支"),
            Map.entry("case", "switch 的分支标签，重复标签报错 n"),
            Map.entry("default", "switch 的默认分支，同层只能有一个"),
            Map.entry("static", "静态局部变量，初值必须是常量"),
            Map.entry("printf", "格式化输出，实参类型要与格式串匹配（否则报错 l）"));

    private static final Map<Character, String> SYMBOL_NOTES = Map.ofEntries(
            Map.entry('+', "加"),
            Map.entry('-', "减 / 取负"),
            Map.entry('*', "乘"),
            Map.entry('/', "除"),
            Map.entry('%', "取模"),
            Map.entry('{', "块开始"),
            Map.entry('}', "块结束"),
            Map.entry('[', "数组下标开始"),
            Map.entry(']', "数组下标结束"),
            Map.entry('(', "括号开始"),
            Map.entry(')', "括号结束（缺失报错 j）"),
            Map.entry(';', "语句结束（缺失报错 i）"),
            Map.entry(',', "分隔符"),
            Map.entry(':', "switch 标签的分隔冒号"));

    /** 这些运算符不在这张单字符表里，但文法里要用，单独列出来。 */
    private static final List<Entry> OPERATOR_ENTRIES = List.of(
            new Entry("!", "运算符", "逻辑非"),
            new Entry("&&", "运算符", "逻辑与（短路求值）"),
            new Entry("||", "运算符", "逻辑或（短路求值）"),
            new Entry("==", "运算符", "相等比较"),
            new Entry("!=", "运算符", "不等比较"),
            new Entry("<", "运算符", "小于"),
            new Entry("<=", "运算符", "小于等于"),
            new Entry(">", "运算符", "大于"),
            new Entry(">=", "运算符", "大于等于"),
            new Entry("=", "运算符", "赋值"));

    private SyntaxReference() { }

    public static List<Section> sections() {
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("关键字（" + Keywords.TABLE.size() + "）", keywordEntries()));
        sections.add(new Section("运算符与分隔符", operatorEntries()));
        sections.add(new Section("转义字符（" + Escapes.TABLE.size() + "）", escapeEntries()));
        return sections;
    }

    private static List<Entry> keywordEntries() {
        List<Entry> entries = new ArrayList<>();
        Keywords.TABLE.forEach((word, type) -> entries.add(
                new Entry(word, type.toString(), KEYWORD_NOTES.getOrDefault(word, "关键字"))));
        entries.sort(Comparator.comparing(entry -> entry.shown()));
        return entries;
    }

    private static List<Entry> operatorEntries() {
        List<Entry> entries = new ArrayList<>();
        Symbols.TABLE.forEach((symbol, type) -> entries.add(
                new Entry(String.valueOf(symbol), type.toString(),
                        SYMBOL_NOTES.getOrDefault(symbol, "符号"))));
        entries.addAll(OPERATOR_ENTRIES);
        entries.sort(Comparator.comparing(entry -> entry.category()));
        return entries;
    }

    private static List<Entry> escapeEntries() {
        List<Entry> entries = new ArrayList<>();
        Map<Character, Character> table = new LinkedHashMap<>(Escapes.TABLE);
        table.forEach((from, to) -> entries.add(
                new Entry("\\" + from, "转义", "表示 " + describe(to))));
        entries.sort(Comparator.comparing(entry -> entry.shown()));
        return entries;
    }

    private static String describe(char value) {
        return switch (value) {
            case '\n' -> "换行 (LF)";
            case '\t' -> "制表符 (TAB)";
            case '\r' -> "回车 (CR)";
            case '\\' -> "反斜杠";
            case '\'' -> "单引号";
            case '"' -> "双引号";
            case '\0' -> "空字符 (NUL)";
            default -> Character.isISOControl(value)
                    ? String.format("控制字符 U+%04X", (int) value)
                    : "字符 " + value;
        };
    }

    /** 语法高亮用：哪些词是保留的 token 文本。 */
    public static Map<String, TokenType> tokenWords() {
        return Keywords.TABLE;
    }
}
