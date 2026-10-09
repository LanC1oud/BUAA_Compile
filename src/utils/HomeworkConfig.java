package utils;

final public class HomeworkConfig {
    public enum Hw {
        Lexer, Syntax, Semantic
    }
    
    // switch the homework stage
    public static Hw hw = Hw.Semantic;

    public static String getTarget() {
        return switch (hw) {
            case Lexer -> "lexer.txt";
            case Syntax -> "parser.txt";
            case Semantic -> "symbol.txt";
        };
    }
}
