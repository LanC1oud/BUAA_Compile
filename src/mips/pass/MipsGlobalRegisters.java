package mips.pass;

import llvm.analysis.ControlFlow;
import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.value.*;
import java.util.*;

/** PHI-edge-aware liveness and optimistic graph coloring for callee-saved registers. */
final class MipsGlobalRegisters {
    final Map<Value, String> homes = new IdentityHashMap<>();
    final Map<String, Integer> savedOffsets = new LinkedHashMap<>();
    private final Map<Value, Set<Value>> graph = new LinkedHashMap<>();
    private final Map<Value, Double> weights = new IdentityHashMap<>();
    private final Map<Value, List<Value>> preferences = new IdentityHashMap<>();
    private static Set<Value> set() { return Collections.newSetFromMap(new IdentityHashMap<>()); }

    MipsGlobalRegisters(Function function, MipsFunctionPlan plan) {
        if (!utils.Configure.enableGlobalRegisters) return;
        ControlFlow cfg = new ControlFlow(function);
        List<Value> values = new ArrayList<>(function.getArguments());
        for (BasicBlock b : cfg.blocks) for (Instruction i : b.getInstructions()) if (plan.needsSlot(i)) values.add(i);
        Set<Value> possible = set(); possible.addAll(values);
        for (Value v : values) {
            double weight = 0;
            for (Value.Use use : v.getUses()) {
                BasicBlock block = use.user().getParent();
                if (use.user().getOpcode() == Opcode.PHI) block = (BasicBlock)use.user().getOperands().get(use.index() + 1);
                weight += Math.pow(8, Math.min(4, cfg.loopDepth.getOrDefault(block, 0)));
            }
            // Cold, single-use temporaries are cheaper in the caller-saved pool.
            if (weight >= 3) { graph.put(v, set()); weights.put(v, weight); }
        }
        Map<BasicBlock, Set<Value>> defs = new IdentityHashMap<>(), uses = new IdentityHashMap<>(),
                in = new IdentityHashMap<>(), out = new IdentityHashMap<>();
        for (BasicBlock b : cfg.blocks) {
            Set<Value> def = set(), use = set();
            for (Instruction i : b.getInstructions()) {
                if (i.getOpcode() != Opcode.PHI) for (Value v : i.getOperands()) if (possible.contains(v) && !def.contains(v)) use.add(v);
                if (possible.contains(i)) def.add(i);
                if (i.getOpcode() == Opcode.PHI) for (int n = 0; n < i.getOperands().size(); n += 2) {
                    Value source = i.getOperands().get(n);
                    preferences.computeIfAbsent(i, k -> new ArrayList<>()).add(source);
                    preferences.computeIfAbsent(source, k -> new ArrayList<>()).add(i);
                }
            }
            defs.put(b, def); uses.put(b, use); in.put(b, set()); out.put(b, set());
        }
        boolean changed;
        do {
            changed = false;
            for (int n = cfg.blocks.size() - 1; n >= 0; n--) {
                BasicBlock b = cfg.blocks.get(n); Set<Value> next = set();
                for (BasicBlock successor : cfg.successors.get(b)) {
                    next.addAll(in.get(successor));
                    for (Instruction phi : successor.getInstructions()) {
                        if (phi.getOpcode() != Opcode.PHI) break;
                        var a = phi.getOperands();
                        for (int k = 0; k < a.size(); k += 2) if (a.get(k + 1) == b && possible.contains(a.get(k))) next.add(a.get(k));
                    }
                }
                Set<Value> entering = set(); entering.addAll(next); entering.removeAll(defs.get(b)); entering.addAll(uses.get(b));
                if (!next.equals(out.get(b)) || !entering.equals(in.get(b))) changed = true;
                out.put(b, next); in.put(b, entering);
            }
        } while (changed);
        for (BasicBlock b : cfg.blocks) {
            Set<Value> live = set(); live.addAll(out.get(b));
            List<Instruction> instructions = b.getInstructions();
            for (int n = instructions.size() - 1; n >= 0; n--) {
                Instruction i = instructions.get(n);
                live.remove(i);
                if (graph.containsKey(i)) for (Value v : live) interfere(i, v);
                if (i.getOpcode() != Opcode.PHI) for (Value v : i.getOperands()) if (possible.contains(v)) live.add(v);
            }
            if (b == cfg.blocks.get(0)) for (Argument argument : function.getArguments()) {
                for (Value v : live) interfere(argument, v);
            }
        }
        color();
    }
    private void interfere(Value a, Value b) {
        if (a == b || !graph.containsKey(a) || !graph.containsKey(b)) return;
        graph.get(a).add(b); graph.get(b).add(a);
    }
    private void color() {
        Set<Value> remaining = new LinkedHashSet<>(graph.keySet()); List<Value> stack = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Value chosen = null; double best = Double.POSITIVE_INFINITY;
            for (Value v : remaining) {
                long degree = graph.get(v).stream().filter(remaining::contains).count();
                if (degree < 8) { chosen = v; break; }
                double cost = weights.get(v) / Math.max(1, degree);
                if (cost < best) { best = cost; chosen = v; }
            }
            remaining.remove(chosen); stack.add(chosen);
        }
        for (int n = stack.size() - 1; n >= 0; n--) {
            Value v = stack.get(n); Set<String> unavailable = new HashSet<>();
            for (Value neighbor : graph.get(v)) if (homes.containsKey(neighbor)) unavailable.add(homes.get(neighbor));
            String chosen = null;
            for (Value peer : preferences.getOrDefault(v, List.of())) {
                String color = homes.get(peer);
                if (color != null && !unavailable.contains(color)) { chosen = color; break; }
            }
            if (chosen == null) for (int k = 0; k < 8; k++) if (!unavailable.contains("$s" + k)) { chosen = "$s" + k; break; }
            if (chosen != null) homes.put(v, chosen);
        }
    }
}
