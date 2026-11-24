package mips.pass;

import llvm.instruction.Instruction;
import llvm.type.IrType;
import llvm.value.*;
import mips.MipsFrame;
import java.util.*;
import static mips.MipsLayout.*;

// 复用临时值栈槽，省去无用槽位和叶子函数返回地址槽
final class MipsFrameOptimization {
    // 禁止实例化栈帧优化工具类
    private MipsFrameOptimization() { }

    // 根据使用分析分配对象和回退槽，并复用块内槽位
    static MipsFrame allocate(Function function, MipsFunctionPlan plan, MipsGlobalRegisters globals) {
        Map<Value, Integer> objects = new IdentityHashMap<>();
        Map<Value, Integer> slots = new IdentityHashMap<>();
        int outgoing = 0;
        boolean callsFunction = false;
        for (BasicBlock block : function.getBlocks())
            for (Instruction i : block.getInstructions())
                if (i.getOpcode() == Instruction.Opcode.CALL) {
                    outgoing = Math.max(outgoing, Math.multiplyExact(Math.max(0, i.getOperands().size() - 5), 4));
                    if (!((Function) i.getOperands().get(0)).isExternal()) callsFunction = true;
                }
        int cursor = outgoing;
        for (Argument argument : function.getArguments()) {
            if (argument.getUses().isEmpty() || globals.homes.containsKey(argument)) continue;
            slots.put(argument, cursor);
            cursor = Math.addExact(cursor, 4);
        }
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction i : block.getInstructions()) {
                if (i.getOpcode() == Instruction.Opcode.ALLOCA && !i.getUses().isEmpty()) {
                    IrType object = i.getType().element();
                    cursor = align(cursor, alignment(object));
                    objects.put(i, cursor);
                    cursor = Math.addExact(cursor, sizeOf(object));
                }
            }
        }
        cursor = align(cursor, 4);
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction i : block.getInstructions()) {
                if (plan.needsSlot(i) && !globals.homes.containsKey(i) && plan.crossesBlock(i)) {
                    slots.put(i, cursor);
                    cursor = Math.addExact(cursor, 4);
                }
            }
        }
        int localBase = cursor, peak = 0;
        for (BasicBlock block : function.getBlocks()) {
            PriorityQueue<Interval> active = new PriorityQueue<>(Comparator.comparingInt(Interval::end));
            PriorityQueue<Integer> free = new PriorityQueue<>();
            int count = 0;
            List<Instruction> instructions = block.getInstructions();
            for (int n = 0; n < instructions.size(); n++) {
                Instruction i = instructions.get(n);
                if (!plan.needsSlot(i) || globals.homes.containsKey(i) || plan.crossesBlock(i)) continue;
                while (!active.isEmpty() && active.peek().end() <= n) free.add(active.remove().slot());
                int slot = free.isEmpty() ? count++ : free.remove();
                slots.put(i, Math.addExact(localBase, Math.multiplyExact(slot, 4)));
                active.add(new Interval(plan.lastUse(i, block), slot));
            }
            peak = Math.max(peak, count);
        }
        cursor = Math.addExact(localBase, Math.multiplyExact(peak, 4));
        // main has no caller in the IR, so it need not preserve startup's saved registers.
        if (!function.getName().equals("main") || !function.getUses().isEmpty())
            for (String register : new TreeSet<>(globals.homes.values())) {
                globals.savedOffsets.put(register, cursor); cursor = Math.addExact(cursor, 4);
            }
        int returnAddress = cursor;
        int size = align(Math.addExact(returnAddress, callsFunction ? 4 : 0), 8);
        return new MipsFrame(objects, slots, size, returnAddress, callsFunction);
    }

    // 记录临时值的末次使用位置与占用槽号
    private record Interval(int end, int slot) { }
}
