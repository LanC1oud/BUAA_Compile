package llvm.pass;

import llvm.IrModule;
import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;
import java.util.*;

/** Direct tail recursion becomes a loop with parallel argument PHIs. */
public final class TailRecursion implements IrPass {
    @Override public void run(IrModule module) {
        for (Function f : module.getFunctions()) if (!f.isExternal()) {
            // Reusing storage would change aliasing when a recursive argument points into a local object.
            if (f.getBlocks().stream().flatMap(b -> b.getInstructions().stream()).anyMatch(i -> i.getOpcode() == Opcode.ALLOCA)) continue;
            List<Instruction> calls = new ArrayList<>();
            for (BasicBlock b : f.getBlocks()) {
                var body = b.getInstructions(); if (body.size() < 2) continue;
                Instruction ret = body.get(body.size() - 1), call = body.get(body.size() - 2);
                if (ret.getOpcode() == Opcode.RET && call.getOpcode() == Opcode.CALL && call.getOperands().get(0) == f
                        && (ret.getOperands().isEmpty() && call.getType().equals(IrType.VOID)
                        || ret.getOperands().size() == 1 && ret.getOperands().get(0) == call && call.getUses().size() == 1)) calls.add(call);
            }
            if (calls.isEmpty()) continue;
            BasicBlock header = f.getBlocks().get(0), entry = f.newEntryBlock();
            List<Instruction> phis = new ArrayList<>();
            for (Argument argument : f.getArguments()) {
                Instruction phi = new Instruction(header, Opcode.PHI, argument.getType(), f.nextValueName(), "", List.of());
                argument.replaceAllUsesWith(phi); phi.addIncoming(argument, entry); header.prependPhi(phi); phis.add(phi);
            }
            entry.append(new Instruction(entry, Opcode.BR, IrType.VOID, "", "", List.of(header)));
            for (Instruction call : calls) {
                BasicBlock b = call.getParent(); var operands = call.getOperands();
                for (int n = 0; n < phis.size(); n++) phis.get(n).addIncoming(operands.get(n + 1), b);
                b.replaceTerminator(new Instruction(b, Opcode.BR, IrType.VOID, "", "", List.of(header)));
                b.remove(call);
            }
        }
    }
}
