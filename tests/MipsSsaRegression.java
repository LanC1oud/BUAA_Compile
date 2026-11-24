import llvm.*;
import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;
import llvm.verify.IrVerifier;
import mips.*;
import mips.pass.OptimizedMipsGenerator;
import utils.Configure;
import java.nio.file.*;
import java.util.*;

/** Direct PHIs test parallel cycles independently of mem2reg, plus full-width unsigned division. */
public final class MipsSsaRegression {
    private static Constant c(int n) { return Constant.integer(IrType.I32, n); }
    public static void main(String[] args) throws Exception {
        Configure.enableDelaySlotRemoval = false;
        IrModule module = new IrModule(); IrBuilder b = new IrBuilder();
        Function print = module.addFunction(new Function("put_int", IrType.VOID, List.of(IrType.I32), true));
        Function space = module.addFunction(new Function("put_char", IrType.VOID, List.of(IrType.I32), true));
        Function cycle = module.addFunction(new Function("cycle", IrType.I32, List.of(IrType.I32), false));
        b.enterFunction(cycle); BasicBlock entry = cycle.getBlocks().get(0), header = b.newBlock(), body = b.newBlock(), exit = b.newBlock();
        b.branch(header);
        List<Instruction> phis = new ArrayList<>();
        for (int n = 0; n < 5; n++) {
            Instruction phi = new Instruction(header, Opcode.PHI, IrType.I32, cycle.nextValueName(), "", List.of());
            header.prependPhi(phi); phis.add(phi);
        }
        b.positionAt(header); b.branch(b.compare("slt", phis.get(3), cycle.getArguments().get(0)), body, exit);
        b.positionAt(body);
        Value sum = b.binary(Opcode.ADD, phis.get(4), b.binary(Opcode.ADD, b.binary(Opcode.MUL, phis.get(0), c(100)),
                b.binary(Opcode.ADD, b.binary(Opcode.MUL, phis.get(1), c(10)), phis.get(2))));
        Value next = b.binary(Opcode.ADD, phis.get(3), c(1)); b.branch(header);
        for (int n = 0; n < 3; n++) { phis.get(n).addIncoming(c(n + 1), entry); phis.get(n).addIncoming(phis.get((n + 1) % 3), body); }
        phis.get(3).addIncoming(c(0), entry); phis.get(3).addIncoming(next, body);
        phis.get(4).addIncoming(c(0), entry); phis.get(4).addIncoming(sum, body);
        b.positionAt(exit); b.ret(phis.get(4));
        int[] divisors = {3,5,7,10,31,127,65535,123456789,2147483647,Integer.MIN_VALUE+1,-2,-1};
        Function division = module.addFunction(new Function("unsigned", IrType.VOID, List.of(IrType.I32), false));
        b.enterFunction(division);
        for (int d : divisors) for (Opcode opcode : List.of(Opcode.UDIV, Opcode.UREM)) {
            b.call(print, List.of(b.binary(opcode, division.getArguments().get(0), c(d)))); b.call(space, List.of(c(32)));
        }
        b.ret(null);
        Function main = module.addFunction(new Function("main", IrType.I32, List.of(), false)); b.enterFunction(main);
        b.call(print, List.of(b.call(cycle, List.of(c(37))))); b.call(space, List.of(c(32)));
        for (int n : new int[]{0,1,127,65535,2147483647,Integer.MIN_VALUE,Integer.MIN_VALUE+1,-65537,-2,-1}) b.call(division, List.of(c(n)));
        b.ret(c(0)); new IrVerifier().verify(module);
        Path out = Path.of(args[0]); String ir = module.toString(); Files.writeString(out.resolve("ssa.ll"), ir);
        Files.writeString(out.resolve("ssa-plain.s"), new MipsGenerator().generate(module).toString());
        Files.writeString(out.resolve("ssa-opt.s"), new OptimizedMipsGenerator(List.of()).generate(module).toString());
        Configure.enableGlobalRegisters = false;
        Files.writeString(out.resolve("ssa-no-global.s"), new OptimizedMipsGenerator(List.of()).generate(module).toString());
        Configure.enableGlobalRegisters = true;
        Configure.enablePeephole = false; Configure.enableLoopLayout = false;
        Files.writeString(out.resolve("ssa-no-peephole.s"), new OptimizedMipsGenerator(List.of()).generate(module).toString());
        Configure.enablePeephole = true; Configure.enableLoopLayout = true;
        if (!ir.equals(module.toString())) throw new AssertionError("Backend mutated SSA");
        System.out.println("Direct PHI/unsigned division fixtures generated");
    }
}
