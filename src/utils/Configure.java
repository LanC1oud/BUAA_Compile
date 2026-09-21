package utils;

public class Configure {
    
    public static String source = "testfile.txt";
    public static String target = HomeworkConfig.getTarget();
    public static String error = "error.txt";
    
    
    public static class debug {
        public static boolean displayTokens = false;
        public static boolean displayErrors = false;
        public static boolean displaySymbols = false;
        public static boolean displayTokensWithAst = false;
    }
}
