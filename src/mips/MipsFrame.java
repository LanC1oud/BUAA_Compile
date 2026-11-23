package mips;

import llvm.instruction.Instruction;
import llvm.type.IrType;
import llvm.value.*;
import java.util.*;
import static mips.MipsLayout.*;

/** 固定栈帧布局；基础路径为每个结果分配独立槽位。 */
public final class MipsFrame {
    public final Map<Value, Integer> objects = new IdentityHashMap<>();
    public final Map<Value, Integer> slots = new IdentityHashMap<>();
    public final int size;
    public final int returnAddress;
    public final boolean needsReturnAddress;

    public MipsFrame(Map<Value, Integer> objects, Map<Value, Integer> slots,
                     int size, int returnAddress, boolean needsReturnAddress) {
        this.objects.putAll(objects);
        this.slots.putAll(slots);
        this.size = size;
        this.returnAddress = returnAddress;
        this.needsReturnAddress = needsReturnAddress;
    }

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
        returnAddress = align(cursor, 4);
        size = align(Math.addExact(returnAddress, 4), 8);
    }
}
