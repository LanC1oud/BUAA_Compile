package utils;

public class Configure {

    public static String source = "testfile.txt";
    public static String target = HomeworkConfig.getTarget();
    public static String error = "error.txt";
    // Future optimizations are opt-in; the default pipeline is already executable.
    public static boolean optimize = false;
    public static java.util.List<llvm.pass.IrPass> irPasses = java.util.List.of();
    public static String irBeforeOptimization = null;
    
    
    public static class debug {
        public static boolean displayTokens = false;
        public static boolean displayErrors = false;
        public static boolean displaySymbols = false;
        public static boolean displayTokensWithAst = false;
    }
}
