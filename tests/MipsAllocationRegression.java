import llvm.*;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;
import llvm.verify.IrVerifier;
import mips.MipsGenerator;
import mips.pass.OptimizedMipsGenerator;
import mips.pass.RemoveRedundantJumps;
import java.nio.file.*;
import java.util.*;

/** Direct SSA fixtures: cross-block spills, shared comparisons and narrow signed branches. */
public final class MipsAllocationRegression {
    private static Constant c(int n) { return Constant.integer(IrType.I32, n); }
    public static void main(String[] args) throws Exception {
        try { utils.Configure.class.getField("enableDelaySlotRemoval").setBoolean(null, false); }
        catch (NoSuchFieldException ignored) { /* Saved compiler predates this option. */ }
        IrModule module = new IrModule();
        IrBuilder builder = new IrBuilder();
        Function print = module.addFunction(new Function("put_int", IrType.VOID, List.of(IrType.I32), true));
        Function cross = module.addFunction(new Function("cross", IrType.I32, List.of(IrType.I32), false));
        builder.enterFunction(cross);
        List<Value> live = new ArrayList<>();
        for (int n = 1; n <= 10; n++) live.add(builder.binary(Opcode.ADD, cross.getArguments().get(0), c(n)));
        BasicBlock yes = builder.newBlock(), no = builder.newBlock();
        builder.branch(builder.compare("slt", cross.getArguments().get(0), c(0)), yes, no);
        for (BasicBlock block : List.of(yes, no)) {
            builder.positionAt(block);
            Value sum = c(0);
            for (Value value : live) sum = builder.binary(Opcode.ADD, sum, value);
            builder.ret(block == no ? builder.binary(Opcode.MUL, sum, c(2)) : sum);
        }
        Function shared = module.addFunction(new Function("shared_cmp", IrType.I32, List.of(IrType.I32), false));
        builder.enterFunction(shared);
        Value compared = builder.compare("slt", shared.getArguments().get(0), c(0));
        yes = builder.newBlock(); no = builder.newBlock();
        builder.branch(compared, yes, no);
        for (BasicBlock block : List.of(yes, no)) {
            builder.positionAt(block);
            builder.ret(builder.convert(compared, IrType.I32));
        }
        List<Function> predicates = new ArrayList<>();
        for (String predicate : List.of("eq", "ne", "slt", "sgt", "sle", "sge", "ult", "ugt", "ule", "uge")) {
            Function function = module.addFunction(new Function("branch_" + predicate, IrType.I32,
                    List.of(IrType.I8, IrType.I8), false));
            predicates.add(function);
            builder.enterFunction(function);
            yes = builder.newBlock(); no = builder.newBlock();
            builder.branch(builder.compare(predicate, function.getArguments().get(0), function.getArguments().get(1)), yes, no);
            builder.positionAt(yes); builder.ret(c(1));
            builder.positionAt(no); builder.ret(c(0));
        }
        Function main = module.addFunction(new Function("main", IrType.I32, List.of(), false));
        builder.enterFunction(main);
        Value a = builder.call(cross, List.of(c(2))), b = builder.call(cross, List.of(c(-3)));
        builder.call(print, List.of(a)); builder.call(print, List.of(b));
        builder.call(print, List.of(builder.call(shared, List.of(c(-3)))));
        builder.call(print, List.of(builder.call(shared, List.of(c(2)))));
        for (Function function : predicates) builder.call(print, List.of(builder.call(function,
                List.of(Constant.integer(IrType.I8, 255), Constant.integer(IrType.I8, 1)))));
        builder.ret(c(0));
        new IrVerifier().verify(module);
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        String before = module.toString();
        Files.writeString(directory.resolve("allocation.s"), new MipsGenerator().generate(module).toString());
        MipsGenerator optimized = new OptimizedMipsGenerator(List.of(new RemoveRedundantJumps()));
        String assembly = optimized.generate(module).toString();
        if (!assembly.equals(optimized.generate(module).toString()) || !before.equals(module.toString()))
            throw new AssertionError("Allocation changed IR or leaked state");
        Files.writeString(directory.resolve("allocation-opt.s"), assembly);
        System.out.println("MIPS allocation/branch model generated");
    }
}
