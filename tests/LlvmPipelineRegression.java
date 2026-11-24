import compilerui.model.Stage;
import compilerui.pipeline.Pipeline;
import llvm.*;
import llvm.type.IrType;
import llvm.value.*;
import llvm.instruction.Instruction;
import llvm.verify.IrVerifier;

import java.util.List;

/** In-process state isolation and structural model regression checks. */
public final class LlvmPipelineRegression {
    private static void check(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Pipeline p = new Pipeline();
        String a = "int f(){static int x;x=x+1;return x;}int main(){printf(\"hello%d\",f());return 0;}";
        p.compileContent(a, "A", Stage.LLVM);
        check(p.crashMessage() == null && p.resultOf(Stage.LLVM) != null, "compile A: " + p.crashMessage());
        String first = p.resultOf(Stage.LLVM).payload();
        p.compileContent("int main(){return 0;}", "B", Stage.LLVM);
        String second = p.resultOf(Stage.LLVM).payload();
        check(!second.contains(".str.") && !second.contains(".static.") && !second.contains("@f("), "module leakage");
        p.compileContent(a, "A2", Stage.LLVM);
        check(first.equals(p.resultOf(Stage.LLVM).payload()), "unstable naming");
        p.compileContent("int main(){missing=1;return 0;}", "bad", Stage.LLVM);
        check(p.resultOf(Stage.LLVM) == null && !p.rawErrors().isEmpty(), "IR after semantic failure");
        p.compileContent("int main(){return 0;}", "B2", Stage.LLVM);
        check(p.resultOf(Stage.LLVM).payload().equals(second), "error state leakage");

        IrModule m = new IrModule();
        IrBuilder b = new IrBuilder();
        m.internString("\"\\\n");
        Function add = m.addFunction(new Function("add", IrType.I32, List.of(IrType.I32, IrType.I32), false));
        b.enterFunction(add);
        Value sum = b.binary(Instruction.Opcode.ADD, add.getArguments().get(0), add.getArguments().get(1));
        b.ret(sum);
        Function main = m.addFunction(new Function("main", IrType.I32, List.of(), false));
        b.enterFunction(main);
        b.ret(b.call(add, List.of(Constant.integer(IrType.I32, 1), Constant.integer(IrType.I32, 2))));
        new IrVerifier().verify(m);
        Value old = add.getArguments().get(1), replacement = Constant.integer(IrType.I32, 9);
        old.replaceAllUsesWith(replacement);
        check(old.getUses().isEmpty() && replacement.getUses().size() == 1, "use replacement");
        new IrVerifier().verify(m);
        boolean refused = false;
        try {
            b.binary(Instruction.Opcode.ADD, Constant.integer(IrType.I32, 1), Constant.integer(IrType.I32, 2));
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "instruction appended after return");
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), m.toString());
        System.out.println("LLVM pipeline/model regression passed");
    }
}
