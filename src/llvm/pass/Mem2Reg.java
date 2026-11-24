package llvm.pass;

import llvm.IrModule;
import llvm.analysis.ControlFlow;
import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;
import java.util.*;

/** Iterated dominance-frontier promotion of nonescaping scalar allocas. */
public final class Mem2Reg implements IrPass {
    @Override public void run(IrModule module) {
        for (Function f : module.getFunctions()) if (!f.isExternal()) promote(f);
    }
    private void promote(Function f) {
        ControlFlow cfg = new ControlFlow(f);
        // Array parameters are pointers stored in entry allocas. Their single dominating
        // definition can be forwarded without needing an undefined pointer constant.
        for (Instruction slot : f.getBlocks().get(0).getInstructions()) {
            if (slot.getOpcode() != Opcode.ALLOCA || slot.getType().element().kind() != IrType.Kind.POINTER
                    || !slot.getUses().stream().allMatch(u -> u.user().getOpcode() == Opcode.LOAD && u.index() == 0
                    || u.user().getOpcode() == Opcode.STORE && u.index() == 1)) continue;
            List<Instruction> stores = slot.getUses().stream().map(Value.Use::user).filter(i -> i.getOpcode() == Opcode.STORE).toList();
            if (stores.size() != 1) continue;
            Instruction store = stores.get(0);
            boolean dominates = slot.getUses().stream().map(Value.Use::user).filter(i -> i.getOpcode() == Opcode.LOAD)
                    .allMatch(load -> cfg.dominates(store.getParent(), load.getParent()) && (store.getParent() != load.getParent()
                    || store.getParent().getInstructions().indexOf(store) < load.getParent().getInstructions().indexOf(load)));
            if (!dominates) continue;
            for (Value.Use use : slot.getUses()) if (use.user().getOpcode() == Opcode.LOAD) {
                Instruction load = use.user(); load.replaceAllUsesWith(store.getOperands().get(0)); load.getParent().remove(load);
            }
            store.getParent().remove(store); slot.getParent().remove(slot);
        }
        List<Instruction> variables = f.getBlocks().get(0).getInstructions().stream()
                .filter(i -> i.getOpcode() == Opcode.ALLOCA && i.getType().element().isInteger())
                .filter(i -> i.getUses().stream().allMatch(u -> u.user().getOpcode() == Opcode.LOAD && u.index() == 0
                        || u.user().getOpcode() == Opcode.STORE && u.index() == 1)).toList();
        if (variables.isEmpty()) return;
        Set<Instruction> variableSet = Collections.newSetFromMap(new IdentityHashMap<>()); variableSet.addAll(variables);
        Map<BasicBlock, Map<Instruction, Instruction>> phis = new IdentityHashMap<>();
        Map<Instruction, Deque<Value>> stacks = new IdentityHashMap<>();
        for (Instruction variable : variables) {
            IrType type = variable.getType().element();
            stacks.put(variable, new ArrayDeque<>(List.of(Constant.integer(type, 0))));
            Set<BasicBlock> definitions = new LinkedHashSet<>();
            for (Value.Use use : variable.getUses()) if (use.user().getOpcode() == Opcode.STORE) definitions.add(use.user().getParent());
            ArrayDeque<BasicBlock> work = new ArrayDeque<>(definitions); Set<BasicBlock> placed = new LinkedHashSet<>();
            while (!work.isEmpty()) for (BasicBlock target : cfg.frontier.get(work.remove())) if (placed.add(target)) {
                Instruction phi = new Instruction(target, Opcode.PHI, type, f.nextValueName(), "", List.of());
                target.prependPhi(phi); phis.computeIfAbsent(target, key -> new IdentityHashMap<>()).put(variable, phi);
                if (!definitions.contains(target)) work.add(target);
            }
        }
        rename(f.getBlocks().get(0), cfg, variables, variableSet, phis, stacks);
        for (Instruction v : variables) if (v.getUses().isEmpty()) v.getParent().remove(v);
    }
    private void rename(BasicBlock b, ControlFlow cfg, List<Instruction> variables, Set<Instruction> variableSet,
                        Map<BasicBlock, Map<Instruction, Instruction>> phis, Map<Instruction, Deque<Value>> stacks) {
        Map<Instruction, Integer> pushed = new IdentityHashMap<>();
        for (Instruction v : variables) {
            Instruction phi = phis.getOrDefault(b, Map.of()).get(v);
            if (phi != null) { stacks.get(v).push(phi); pushed.merge(v, 1, Integer::sum); }
        }
        for (Instruction i : b.getInstructions()) {
            var a = i.getOperands();
            if (i.getOpcode() == Opcode.LOAD && a.get(0) instanceof Instruction v && variableSet.contains(v)) {
                i.replaceAllUsesWith(stacks.get(v).peek()); b.remove(i);
            } else if (i.getOpcode() == Opcode.STORE && a.get(1) instanceof Instruction v && variableSet.contains(v)) {
                stacks.get(v).push(a.get(0)); pushed.merge(v, 1, Integer::sum); b.remove(i);
            }
        }
        for (BasicBlock successor : cfg.successors.get(b)) for (Instruction v : variables) {
            Instruction phi = phis.getOrDefault(successor, Map.of()).get(v);
            if (phi != null) phi.addIncoming(stacks.get(v).peek(), b);
        }
        for (BasicBlock child : cfg.children.get(b)) rename(child, cfg, variables, variableSet, phis, stacks);
        for (var entry : pushed.entrySet()) for (int n = 0; n < entry.getValue(); n++) stacks.get(entry.getKey()).pop();
    }
}
