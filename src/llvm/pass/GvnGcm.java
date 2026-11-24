package llvm.pass;

import llvm.IrModule;
import llvm.analysis.ControlFlow;
import llvm.instruction.Instruction;
import llvm.value.*;
import java.util.*;

/** Dominator-scoped value numbering followed by loop-depth-aware global code motion. */
public final class GvnGcm implements IrPass {
    private record Key(Object opcode, Object type, String predicate, List<Object> operands) { }
    private record Number(Object type, int value) { }
    @Override public void run(IrModule module) {
        for (Function f : module.getFunctions()) if (!f.isExternal()) {
            ControlFlow cfg = new ControlFlow(f);
            if (utils.Configure.enableGvn) number(f.getBlocks().get(0), cfg, new HashMap<>());
            if (utils.Configure.enableGcm) motion(f);
        }
    }
    private Object key(Value v) { return v instanceof Constant c && c.getType().isInteger() ? new Number(c.getType(), c.getNumber()) : v; }
    private void number(BasicBlock b, ControlFlow cfg, Map<Key, Instruction> inherited) {
        Map<Key, Instruction> available = new HashMap<>(inherited);
        for (Instruction i : b.getInstructions()) if (IrCleanup.movable(i) || switch (i.getOpcode()) {
            case SDIV, SREM, UDIV, UREM -> true; default -> false;
        }) {
            List<Object> operands = new ArrayList<>(); for (Value v : i.getOperands()) operands.add(key(v));
            Key expression = new Key(i.getOpcode(), i.getType(), i.getPredicate(), List.copyOf(operands));
            Instruction previous = available.get(expression);
            if (previous == null && (i.getOpcode() == Instruction.Opcode.ADD || i.getOpcode() == Instruction.Opcode.MUL
                    || i.getOpcode() == Instruction.Opcode.ICMP && Set.of("eq", "ne").contains(i.getPredicate()))) {
                Collections.reverse(operands);
                previous = available.get(new Key(i.getOpcode(), i.getType(), i.getPredicate(), List.copyOf(operands)));
            }
            if (previous != null) { i.replaceAllUsesWith(previous); b.remove(i); }
            else available.put(expression, i);
        }
        for (BasicBlock child : cfg.children.get(b)) number(child, cfg, available);
    }
    private void motion(Function f) {
        ControlFlow cfg = new ControlFlow(f);
        Map<Instruction, BasicBlock> early = new IdentityHashMap<>(), late = new IdentityHashMap<>();
        List<Instruction> candidates = new ArrayList<>();
        for (BasicBlock b : f.getBlocks()) for (Instruction i : b.getInstructions())
            if (IrCleanup.movable(i) && !i.getUses().isEmpty()) candidates.add(i);
        Set<Instruction> moving = Collections.newSetFromMap(new IdentityHashMap<>()); moving.addAll(candidates);
        for (Instruction i : candidates) early(i, cfg, moving, early);
        for (Instruction i : candidates) late(i, cfg, moving, early, late, Collections.newSetFromMap(new IdentityHashMap<>()));
        for (Instruction i : candidates) {
            BasicBlock target = late.get(i);
            if (target != null && target != i.getParent()) i.getParent().moveBeforeTerminator(i, target);
        }
        for (BasicBlock b : f.getBlocks()) b.orderByDependencies();
    }
    private BasicBlock early(Instruction i, ControlFlow cfg, Set<Instruction> moving, Map<Instruction, BasicBlock> positions) {
        if (positions.containsKey(i)) return positions.get(i);
        BasicBlock best = cfg.blocks.get(0);
        for (Value v : i.getOperands()) if (v instanceof Instruction def) {
            BasicBlock location = moving.contains(def) ? early(def, cfg, moving, positions) : def.getParent();
            if (cfg.depth.get(location) > cfg.depth.get(best)) best = location;
        }
        positions.put(i, best); return best;
    }
    private BasicBlock late(Instruction i, ControlFlow cfg, Set<Instruction> moving, Map<Instruction, BasicBlock> early,
                            Map<Instruction, BasicBlock> positions, Set<Instruction> visiting) {
        if (positions.containsKey(i)) return positions.get(i);
        if (!visiting.add(i)) return i.getParent();
        BasicBlock latest = null;
        for (Value.Use use : i.getUses()) {
            Instruction user = use.user(); BasicBlock location;
            if (user.getOpcode() == Instruction.Opcode.PHI) location = (BasicBlock) user.getOperands().get(use.index() + 1);
            else location = moving.contains(user) ? late(user, cfg, moving, early, positions, visiting) : user.getParent();
            latest = cfg.commonDominator(latest, location);
        }
        visiting.remove(i);
        if (latest == null) latest = i.getParent();
        BasicBlock best = latest, earliest = early.get(i);
        for (BasicBlock b = latest; b != null && cfg.dominates(earliest, b); b = cfg.idom.get(b)) {
            if (cfg.loopDepth.get(b) < cfg.loopDepth.get(best)) best = b;
            if (b == earliest) break;
        }
        positions.put(i, best); return best;
    }
}
