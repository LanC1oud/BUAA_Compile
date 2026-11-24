package llvm.pass;

import llvm.IrModule;
import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;
import java.util.*;

/** Bounded CFG inlining, including multiple returns and PHIs; recursive SCCs stay calls. */
public final class SmallFunctionInline implements IrPass {
    @Override public void run(IrModule module) {
        Set<Function> recursive = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Function f : module.getFunctions()) if (reaches(f, f, new HashSet<>())) recursive.add(f);
        for (int round = 0; round < 3; round++) {
            boolean changed = false;
            for (Function caller : module.getFunctions()) if (!caller.isExternal()) {
                int budget = Math.max(0, 600 - size(caller));
                List<Instruction> calls = caller.getBlocks().stream().flatMap(b -> b.getInstructions().stream())
                        .filter(i -> i.getOpcode() == Opcode.CALL).toList();
                for (Instruction call : calls) {
                    Function callee = (Function)call.getOperands().get(0);
                    if (callee == caller || callee.isExternal() || recursive.contains(callee) || size(callee) > 80
                            || size(callee) > budget || callee.getBlocks().size() > 12
                            || callee.getBlocks().stream().flatMap(b -> b.getInstructions().stream()).noneMatch(i -> i.getOpcode() == Opcode.RET)) continue;
                    inline(call, callee, caller); budget -= size(callee); changed = true;
                }
            }
            if (!changed) break;
            if (utils.Configure.enableIrCleanup) new IrCleanup().run(module);
        }
    }
    private int size(Function f) { return f.getBlocks().stream().mapToInt(b -> b.getInstructions().size()).sum(); }
    private boolean reaches(Function current, Function target, Set<Function> visited) {
        if (!visited.add(current)) return false;
        for (BasicBlock b : current.getBlocks()) for (Instruction i : b.getInstructions()) if (i.getOpcode() == Opcode.CALL) {
            Function callee = (Function)i.getOperands().get(0);
            if (callee == target || reaches(callee, target, visited)) return true;
        }
        return false;
    }
    private void inline(Instruction call, Function callee, Function caller) {
        BasicBlock site = call.getParent(), continuation = site.splitAfter(call);
        Map<Value, Value> mapping = new IdentityHashMap<>();
        for (int n = 0; n < callee.getArguments().size(); n++) mapping.put(callee.getArguments().get(n), call.getOperands().get(n + 1));
        for (BasicBlock b : callee.getBlocks()) mapping.put(b, caller.newBlock());
        // Register all definitions first, since backedge PHIs reference later definitions.
        for (BasicBlock b : callee.getBlocks()) for (Instruction original : b.getInstructions()) {
            if (original.getOpcode() == Opcode.RET) continue;
            BasicBlock parent = original.getOpcode() == Opcode.ALLOCA ? caller.getBlocks().get(0) : (BasicBlock)mapping.get(b);
            Instruction copy = new Instruction(parent, original.getOpcode(), original.getType(),
                    original.getType().equals(IrType.VOID) ? "" : caller.nextValueName(), original.getPredicate(), List.of());
            mapping.put(original, copy);
            if (original.getOpcode() == Opcode.ALLOCA) parent.prependAlloca(copy); else parent.append(copy);
        }
        List<Value> returned = new ArrayList<>();
        for (BasicBlock b : callee.getBlocks()) for (Instruction original : b.getInstructions()) {
            List<Value> operands = original.getOperands().stream().map(v -> mapping.getOrDefault(v, v)).toList();
            if (original.getOpcode() == Opcode.RET) {
                BasicBlock copy = (BasicBlock)mapping.get(b);
                copy.append(new Instruction(copy, Opcode.BR, IrType.VOID, "", "", List.of(continuation)));
                if (!operands.isEmpty()) { returned.add(operands.get(0)); returned.add(copy); }
            } else ((Instruction)mapping.get(original)).resetOperands(operands);
        }
        if (!call.getType().equals(IrType.VOID)) {
            Value replacement;
            if (returned.size() == 2) replacement = returned.get(0);
            else {
                Instruction phi = new Instruction(continuation, Opcode.PHI, call.getType(), caller.nextValueName(), "", returned);
                continuation.prependPhi(phi); replacement = phi;
            }
            call.replaceAllUsesWith(replacement);
        }
        site.remove(call);
        site.append(new Instruction(site, Opcode.BR, IrType.VOID, "", "", List.of(mapping.get(callee.getBlocks().get(0)))));
    }
}
