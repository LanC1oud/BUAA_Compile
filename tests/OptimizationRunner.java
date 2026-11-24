import utils.Configure;

/** Test-only feature toggles without rewriting Configure or recompiling the compiler. */
public final class OptimizationRunner {
    public static void main(String[] args) throws Exception {
        if (!args[0].isEmpty()) for (String name : args[0].split(",")) Configure.class.getField(name).setBoolean(null, false);
        Compiler.main(java.util.Arrays.copyOfRange(args, 1, args.length));
    }
}
