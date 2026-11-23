import compilerui.model.CompilerSettings;
import compilerui.model.Stage;
import compilerui.pipeline.BatchRunner;
import compilerui.pipeline.Pipeline;
import llvm.*;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;
import mips.MipsGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** State isolation, batch artifact cleanup, pass wiring and direct IR execution fixtures. */
public final class MipsPipelineRegression {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        Pipeline pipeline = new Pipeline();
        String source = "int f(){static int n;n=n+1;return n;}int main(){printf(\"v=%d\",f());return 0;}";
        pipeline.compileContent(source, "first", Stage.MIPS);
        check(pipeline.crashMessage() == null, pipeline.crashMessage());
        String first = pipeline.resultOf(Stage.MIPS).payload();
        check(pipeline.resultOf(Stage.LLVM) != null, "missing retained IR");
        pipeline.compileContent("int main(){missing=1;return 0;}", "bad", Stage.MIPS);
        check(pipeline.resultOf(Stage.MIPS) == null && pipeline.resultOf(Stage.LLVM) == null
                && !pipeline.rawErrors().isEmpty(), "target code after frontend failure");
        pipeline.compileContent(source, "again", Stage.MIPS);
        check(first.equals(pipeline.resultOf(Stage.MIPS).payload()), "MIPS state leakage");
        pipeline.compileContent(source, "llvm-only", Stage.LLVM);
        check(pipeline.resultOf(Stage.MIPS) == null, "LLVM stage generated MIPS");

        var sample = new BatchRunner.Sample(Path.of("sample.txt"), "sample", source, "");
        var settings = CompilerSettings.defaults();
        var result = BatchRunner.run(List.of(sample), out, settings, null).get(0);
        check(result.passed() && result.lastStage() == Stage.MIPS, "MIPS batch failed");
        Path dir = result.outputDir();
        check(Files.exists(dir.resolve("mips.txt")), "batch missing MIPS");
        var bad = new BatchRunner.Sample(sample.path(), sample.name(), "int main(){missing=1;return 0;}", "");
        BatchRunner.run(List.of(bad), out, settings, null);
        check(!Files.exists(dir.resolve("mips.txt")) && !Files.exists(dir.resolve("llvm_ir.txt"))
                && Files.size(dir.resolve("error.txt")) > 0, "stale failed batch products");
        BatchRunner.run(List.of(sample), out, settings, null);
        check(!Files.exists(dir.resolve("error.txt")) && Files.exists(dir.resolve("mips.txt")), "batch recovery");
        BatchRunner.run(List.of(sample), out, settings.withStage(Stage.SYNTAX), null);
        check(!Files.exists(dir.resolve("mips.txt")) && !Files.exists(dir.resolve("llvm_ir.txt")), "stale later-stage products");

        IrModule module = new IrModule();
        IrBuilder builder = new IrBuilder();
        Function print = module.addFunction(new Function("put_int", IrType.VOID, List.of(IrType.I32), true));
        Function divide = module.addFunction(new Function("u_div", IrType.I32, List.of(IrType.I32), false));
        builder.enterFunction(divide);
        builder.ret(builder.binary(Opcode.UDIV, divide.getArguments().get(0), c(IrType.I32, Integer.MIN_VALUE)));
        Function remainder = module.addFunction(new Function("u_rem", IrType.I32, List.of(IrType.I32), false));
        builder.enterFunction(remainder);
        builder.ret(builder.binary(Opcode.UREM, remainder.getArguments().get(0), c(IrType.I32, Integer.MIN_VALUE)));
        Function smallDivide = module.addFunction(new Function("s_div8", IrType.I8, List.of(IrType.I8), false));
        builder.enterFunction(smallDivide);
        builder.ret(builder.binary(Opcode.SDIV, smallDivide.getArguments().get(0), c(IrType.I8, 252)));
        Function smallRemainder = module.addFunction(new Function("s_rem8", IrType.I8, List.of(IrType.I8), false));
        builder.enterFunction(smallRemainder);
        builder.ret(builder.binary(Opcode.SREM, smallRemainder.getArguments().get(0), c(IrType.I8, 252)));
        Function main = module.addFunction(new Function("main", IrType.I32, List.of(), false));
        builder.enterFunction(main);
        // Unsigned i32 ops and signed narrow IR ops are not normally emitted by the frontend.
        Value udiv = builder.binary(Opcode.UDIV, c(IrType.I32, -1), c(IrType.I32, 2));
        builder.call(print, List.of(udiv));
        Value urem = builder.binary(Opcode.UREM, c(IrType.I32, -1), c(IrType.I32, 2));
        builder.call(print, List.of(urem));
        Value small = builder.binary(Opcode.SDIV, c(IrType.I8, 249), c(IrType.I8, 3));
        builder.call(print, List.of(builder.convert(small, IrType.I32)));
        for (String predicate : List.of("eq", "ne", "slt", "sgt", "sle", "sge", "ult", "ugt", "ule", "uge")) {
            Value compared = builder.compare(predicate, c(IrType.I8, 255), c(IrType.I8, 1));
            builder.call(print, List.of(builder.convert(compared, IrType.I32)));
        }
        builder.call(print, List.of(builder.call(divide, List.of(c(IrType.I32, -1)))));
        builder.call(print, List.of(builder.call(remainder, List.of(c(IrType.I32, -1)))));
        builder.call(print, List.of(builder.convert(builder.call(smallDivide, List.of(c(IrType.I8, 249))), IrType.I32)));
        builder.call(print, List.of(builder.convert(builder.call(smallRemainder, List.of(c(IrType.I8, 249))), IrType.I32)));
        builder.ret(c(IrType.I32, 0));
        String before = module.toString();
        MipsGenerator generator = new MipsGenerator();
        String assembly = generator.generate(module).toString();
        check(assembly.equals(generator.generate(module).toString()), "generator reuse state leakage");
        check(before.equals(module.toString()), "backend mutated IR");
        Files.writeString(out.resolve("model.s"), assembly);
        MipsGenerator optimized = new MipsGenerator(true, List.of(new mips.pass.RemoveRedundantJumps()));
        String faster = optimized.generate(module).toString();
        check(faster.equals(optimized.generate(module).toString()), "optimized generator reuse state leakage");
        check(before.equals(module.toString()), "optimized backend mutated IR");
        Files.writeString(out.resolve("model-opt.s"), faster);
        System.out.println("MIPS pipeline/model/batch checks passed");
    }

    private static Constant c(IrType type, int number) { return Constant.integer(type, number); }
}
