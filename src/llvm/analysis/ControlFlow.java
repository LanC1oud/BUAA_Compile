package llvm.analysis;

import llvm.instruction.Instruction;
import llvm.value.*;
import java.util.*;

/** CFG, dominators, dominance frontiers and natural-loop membership. */
public final class ControlFlow {
    public final Function function;
    public final List<BasicBlock> blocks;
    public final Map<BasicBlock, List<BasicBlock>> successors = new IdentityHashMap<>(), predecessors = new IdentityHashMap<>();
    public final Set<BasicBlock> reachable = new LinkedHashSet<>();
    public final Map<BasicBlock, Set<BasicBlock>> dominators = new IdentityHashMap<>(), frontier = new IdentityHashMap<>();
    public final Map<BasicBlock, BasicBlock> idom = new IdentityHashMap<>();
    public final Map<BasicBlock, List<BasicBlock>> children = new IdentityHashMap<>();
    public final Map<BasicBlock, Integer> depth = new IdentityHashMap<>(), loopDepth = new IdentityHashMap<>();
    public final Map<BasicBlock, Set<BasicBlock>> loops = new LinkedHashMap<>();

    public ControlFlow(Function function) {
        this.function = function; blocks = function.getBlocks();
        for (BasicBlock b : blocks) {
            successors.put(b, new ArrayList<>()); predecessors.put(b, new ArrayList<>());
            children.put(b, new ArrayList<>()); frontier.put(b, new LinkedHashSet<>()); loopDepth.put(b, 0);
        }
        for (BasicBlock b : blocks) {
            var body = b.getInstructions();
            if (body.isEmpty()) continue;
            Instruction term = body.get(body.size() - 1);
            if (term.getOpcode() != Instruction.Opcode.BR) continue;
            for (Value v : term.getOperands()) if (v instanceof BasicBlock target && !successors.get(b).contains(target)) {
                successors.get(b).add(target); predecessors.get(target).add(b);
            }
        }
        if (blocks.isEmpty()) return;
        visit(blocks.get(0));
        BasicBlock entry = blocks.get(0);
        for (BasicBlock b : reachable) dominators.put(b, b == entry ? new LinkedHashSet<>(List.of(b)) : new LinkedHashSet<>(reachable));
        boolean changed;
        do {
            changed = false;
            for (BasicBlock b : reachable) {
                if (b == entry) continue;
                Set<BasicBlock> set = new LinkedHashSet<>(reachable);
                for (BasicBlock p : predecessors.get(b)) if (reachable.contains(p)) set.retainAll(dominators.get(p));
                set.add(b);
                if (!set.equals(dominators.get(b))) { dominators.put(b, set); changed = true; }
            }
        } while (changed);
        depth.put(entry, 0);
        for (BasicBlock b : reachable) if (b != entry) {
            BasicBlock parent = null;
            for (BasicBlock d : dominators.get(b)) if (d != b && (parent == null || dominators.get(d).size() > dominators.get(parent).size())) parent = d;
            idom.put(b, parent); children.get(parent).add(b);
        }
        assignDepth(entry, 0);
        for (BasicBlock b : reachable) if (predecessors.get(b).size() > 1) {
            for (BasicBlock p : predecessors.get(b)) {
                if (!reachable.contains(p)) continue;
                BasicBlock runner = p;
                while (runner != null && runner != idom.get(b)) { frontier.get(runner).add(b); runner = idom.get(runner); }
            }
        }
        for (BasicBlock tail : reachable) for (BasicBlock head : successors.get(tail)) if (dominates(head, tail)) {
            Set<BasicBlock> loop = loops.computeIfAbsent(head, key -> new LinkedHashSet<>(List.of(head)));
            ArrayDeque<BasicBlock> todo = new ArrayDeque<>();
            if (loop.add(tail)) todo.add(tail);
            while (!todo.isEmpty()) for (BasicBlock p : predecessors.get(todo.remove()))
                if (reachable.contains(p) && loop.add(p) && p != head) todo.add(p);
        }
        for (Set<BasicBlock> loop : loops.values()) for (BasicBlock b : loop) loopDepth.merge(b, 1, Integer::sum);
    }
    private void visit(BasicBlock start) {
        ArrayDeque<BasicBlock> todo = new ArrayDeque<>(List.of(start));
        while (!todo.isEmpty()) { BasicBlock b = todo.remove(); if (reachable.add(b)) todo.addAll(successors.get(b)); }
    }
    private void assignDepth(BasicBlock b, int d) { depth.put(b, d); for (BasicBlock c : children.get(b)) assignDepth(c, d + 1); }
    public boolean dominates(BasicBlock a, BasicBlock b) { return dominators.containsKey(b) && dominators.get(b).contains(a); }
    public BasicBlock commonDominator(BasicBlock a, BasicBlock b) {
        if (a == null) return b;
        while (depth.get(a) > depth.get(b)) a = idom.get(a);
        while (depth.get(b) > depth.get(a)) b = idom.get(b);
        while (a != b) { a = idom.get(a); b = idom.get(b); }
        return a;
    }
}
