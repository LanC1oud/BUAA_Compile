package mips;

import llvm.instruction.Instruction;
import llvm.type.IrType;
import llvm.value.*;
import java.util.*;
import static mips.MipsLayout.*;

// 保存函数栈帧布局，并提供未优化的槽位分配方式
public final class MipsFrame {
    public final Map<Value, Integer> objects = new IdentityHashMap<>();
    public final Map<Value, Integer> slots = new IdentityHashMap<>();
    public final int size;
    public final int returnAddress;
    public final boolean needsReturnAddress;
    public final int phiScratch;

    // 接收已有的对象偏移、值槽和栈帧大小
    public MipsFrame(Map<Value, Integer> objects, Map<Value, Integer> slots,
                     int size, int returnAddress, boolean needsReturnAddress) {
        this(objects, slots, size, returnAddress, needsReturnAddress, 0);
    }

    public MipsFrame(Map<Value, Integer> objects, Map<Value, Integer> slots,
                     int size, int returnAddress, boolean needsReturnAddress, int phiScratch) {
        this.objects.putAll(objects);
        this.slots.putAll(slots);
        this.size = size;
        this.returnAddress = returnAddress;
        this.needsReturnAddress = needsReturnAddress;
        this.phiScratch = phiScratch;
    }

    // 为形参、局部对象和指令结果分配独立栈空间
    public MipsFrame(Function function) {
        int outgoing = 0;
        for (BasicBlock block : function.getBlocks())
            for (Instruction i : block.getInstructions())
                if (i.getOpcode() == Instruction.Opcode.CALL) {
                    outgoing = Math.max(outgoing, Math.multiplyExact(Math.max(0, i.getOperands().size() - 5), 4));
                }
        needsReturnAddress = true;
        int cursor = outgoing;
        for (Argument argument : function.getArguments()) {
            slots.put(argument, cursor);
            cursor = Math.addExact(cursor, 4);
        }
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction i : block.getInstructions()) {
                if (i.getOpcode() == Instruction.Opcode.ALLOCA) {
                    IrType object = i.getType().element();
                    cursor = align(cursor, alignment(object));
                    objects.put(i, cursor);
                    cursor = Math.addExact(cursor, sizeOf(object));
                } else if (!i.getType().equals(IrType.VOID)) {
                    cursor = align(cursor, 4);
                    slots.put(i, cursor);
                    cursor = Math.addExact(cursor, 4);
                }
            }
        }
        phiScratch = align(cursor, 4);
        int phis = function.getBlocks().stream().mapToInt(b -> (int)b.getInstructions().stream()
                .filter(i -> i.getOpcode() == Instruction.Opcode.PHI).count()).max().orElse(0);
        returnAddress = Math.addExact(phiScratch, Math.multiplyExact(phis, 4));
        size = align(Math.addExact(returnAddress, 4), 8);
    }
}
