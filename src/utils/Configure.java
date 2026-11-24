package utils;

public class Configure {

    public static String source = "testfile.txt";
    public static String target = HomeworkConfig.getTarget();
    public static String error = "error.txt";
    // Master switch; the CLI and GUI select their optimized pipeline independently.
    public static boolean optimize = true;
    public static boolean enableMem2Reg = true;
    public static boolean enableGlobalScalarPromotion = true;
    public static boolean enableIrCleanup = true;
    public static boolean enableInlining = true;
    public static boolean enableTailRecursion = true;
    public static boolean enableGvn = true;
    public static boolean enableGcm = true;
    public static boolean enableMemoryForwarding = true;
    public static boolean enableGlobalRegisters = true;
    public static boolean enableConstantDivision = true;
    public static boolean enableRangeAnalysis = true;
    public static boolean enablePeephole = true;
    public static boolean enableLoopLayout = true;
    // Course MARS requires delayed branching OFF. Use --delay-slots for compatible assembly.
    public static boolean enableDelaySlotRemoval = true;
    public static java.util.List<llvm.pass.IrPass> irPasses =
            java.util.List.of(new llvm.pass.OptimizationPipeline());
    public static java.util.List<mips.pass.MipsPass> mipsPasses =
            java.util.List.of(new mips.pass.RemoveRedundantJumps());
    public static String irBeforeOptimization = null;
    
    
    public static class debug {
        public static boolean displayTokens = false;
        public static boolean displayErrors = false;
        public static boolean displaySymbols = false;
        public static boolean displayTokensWithAst = false;
    }
}
