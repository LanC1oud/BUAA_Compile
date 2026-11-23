package mips.pass;

import llvm.value.*;
import java.util.*;
import java.util.function.BiConsumer;

// 管理块内临时寄存器，并按需将存活值写回栈槽
final class MipsLocalRegisters {
    private static final List<String> POOL = List.of("$t3", "$t4", "$t5", "$t6", "$t7", "$t8");
    private final MipsFunctionPlan plan;
    private final BiConsumer<Value, String> spill;
    private final Map<String, Value> contents = new LinkedHashMap<>();
    private final Map<Value, String> homes = new IdentityHashMap<>();
    private final Set<Value> dirty = Collections.newSetFromMap(new IdentityHashMap<>());
    private BasicBlock block;

    // 接收值使用分析和寄存器溢出时的写回操作
    MipsLocalRegisters(MipsFunctionPlan plan, BiConsumer<Value, String> spill) {
        this.plan = plan;
        this.spill = spill;
    }

    // 清空旧寄存器分配并切换到指定基本块
    void begin(BasicBlock block) { clear(); this.block = block; }

    // 查询值当前驻留的块内寄存器
    String get(Value value) { return homes.get(value); }

    // 回收已失效的值并为新结果分配寄存器
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

    // 写回调用后仍存活的值，再清空块内寄存器
    void beforeCall(int position) {
        for (Value value : List.copyOf(contents.values()))
            if (plan.liveAfter(value, block, position)) writeBack(value);
        clear();
    }

    // 将跨基本块使用的寄存器结果写回栈槽
    void spillLiveOut() {
        for (Value value : List.copyOf(contents.values())) if (plan.crossesBlock(value)) writeBack(value);
    }

    // 将尚未写回的值保存到对应回退槽
    private void writeBack(Value value) {
        if (dirty.remove(value)) spill.accept(value, homes.get(value));
    }

    // 解除指定寄存器与原值的绑定
    private void remove(String register) {
        Value old = contents.remove(register);
        homes.remove(old);
        dirty.remove(old);
    }

    // 清空寄存器内容、值的位置和待写回标记
    private void clear() { contents.clear(); homes.clear(); dirty.clear(); }
}
