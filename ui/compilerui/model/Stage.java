package compilerui.model;

public enum Stage {
    LEXER("词法分析 Lexer", "lexer.txt"),
    SYNTAX("语法分析 Syntax", "parser.txt"),
    SEMANTIC("语义分析 Semantic", "symbol.txt");

    private final String displayName;
    private final String defaultFileName;

    Stage(String displayName, String defaultFileName) {
        this.displayName = displayName;
        this.defaultFileName = defaultFileName;
    }

    public String displayName() {
        return displayName;
    }

    /** 默认导出文件名，与 HomeworkConfig.getTarget() 保持一致。 */
    public String defaultFileName() {
        return defaultFileName;
    }
}
