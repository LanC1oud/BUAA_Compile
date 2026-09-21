package utils;

final public class HomeworkConfig {
    public enum Hw {
        Lexer, Syntax
    }
    
    // switch the homework stage
    public static Hw hw = Hw.Syntax;

    public static String getTarget() {
        return switch (hw) {
            case Lexer -> "lexer.txt";
            case Syntax -> "parser.txt";
        };
    }
}
