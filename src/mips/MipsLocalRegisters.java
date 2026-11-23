package mips;

import llvm.value.*;
import java.util.*;
import java.util.function.BiConsumer;

/** 块内结果分配到独立寄存器；被驱逐、跨块或跨调用时才写回回退栈槽。 */
final class MipsLocalRegisters {
    private static final List<String> POOL = List.of("$t3", "$t4", "$t5", "$t6", "$t7", "$t8");
    private final MipsFunctionPlan plan;
    private final BiConsumer<Value, String> spill;
    private final Map<String, Value> contents = new LinkedHashMap<>();
    private final Map<Value, String> homes = new IdentityHashMap<>();
    private final Set<Value> dirty = Collections.newSetFromMap(new IdentityHashMap<>());
    private BasicBlock block;

    MipsLocalRegisters(MipsFunctionPlan plan, BiConsumer<Value, String> spill) {
        this.plan = plan;
        this.spill = spill;
    }

    void begin(BasicBlock block) { clear(); this.block = block; }
    String get(Value value) { return homes.get(value); }

    String assign(Value value, int position) {
        for (String register : POOL) {
            Value old = contents.get(register);
            if (old != null && !plan.liveAfter(old, block, position)) remove(register);
        }
        String chosen = null;
        for (String register : POOL) if (!contents.containsKey(register)) { chosen = register; break; }
        if (chosen == null) {
            int farthest = -1;
            for (String register : POOL) {
                Value old = contents.get(register);
                int end = plan.lastUse(old, block);
                if (end <= position) end = Integer.MAX_VALUE;
                if (end > farthest) { chosen = register; farthest = end; }
            }
            writeBack(contents.get(chosen));
            remove(chosen);
        }
        contents.put(chosen, value);
        homes.put(value, chosen);
        dirty.add(value);
        return chosen;
    }

    void beforeCall(int position) {
        for (Value value : List.copyOf(contents.values()))
            if (plan.liveAfter(value, block, position)) writeBack(value);
        clear();
    }

    void spillLiveOut() {
        for (Value value : List.copyOf(contents.values())) if (plan.crossesBlock(value)) writeBack(value);
    }

    private void writeBack(Value value) {
        if (dirty.remove(value)) spill.accept(value, homes.get(value));
    }

    private void remove(String register) {
        Value old = contents.remove(register);
        homes.remove(old);
        dirty.remove(old);
    }

    private void clear() { contents.clear(); homes.clear(); dirty.clear(); }
}
