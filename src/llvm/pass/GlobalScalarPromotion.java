package llvm.pass;

import llvm.IrModule;
import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;
import java.util.*;

/** A private scalar of nonrecursive main has one lifetime and can join local SSA. */
public final class GlobalScalarPromotion implements IrPass {
    @Override public void run(IrModule module) {
        Function main = module.getFunction("main");
        if (main == null || main.isExternal() || !main.getUses().isEmpty()) return;
        BasicBlock entry = main.getBlocks().get(0);
        for (GlobalVariable global : module.getGlobals()) {
            if (global.isReadOnly() || !global.getInitializer().getType().isInteger() || global.getUses().isEmpty()
                    || !global.getUses().stream().allMatch(u -> u.user().getParent().getParent() == main
                    && (u.user().getOpcode() == Opcode.LOAD && u.index() == 0 || u.user().getOpcode() == Opcode.STORE && u.index() == 1))) continue;
            Instruction slot = new Instruction(entry, Opcode.ALLOCA, global.getType(), main.nextValueName(), "", List.of());
            entry.prependAlloca(slot); global.replaceAllUsesWith(slot);
            Instruction initialize = new Instruction(entry, Opcode.STORE, IrType.VOID, "", "", List.of(global.getInitializer(), slot));
            Instruction first = entry.getInstructions().stream().filter(i -> i.getOpcode() != Opcode.ALLOCA).findFirst().orElseThrow();
            entry.insertBefore(first, initialize);
        }
    }
}
