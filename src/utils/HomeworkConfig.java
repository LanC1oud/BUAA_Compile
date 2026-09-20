package utils;

final public class HomeworkConfig {
    public enum Hw {
        Lexer,
    }
    
    // switch the homework stage
    public static Hw hw = Hw.Lexer;

    public static String getTarget() {
        return switch (hw) {
            case Lexer -> "lexer.txt";
        };
    }
}
