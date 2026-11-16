package llvm.value;

import llvm.instruction.Instruction;
import llvm.type.IrType;

import java.util.ArrayList;
import java.util.List;

/** 保存函数中的有序指令，块本身也可作为跳转目标。 */
public final class BasicBlock extends Value {
    private final Function parent;
    private final List<Instruction> instructions = new ArrayList<>();

    public BasicBlock(Function parent, String name) {
        super(IrType.LABEL, name);
        this.parent = parent;
    }

    public Function getParent() {
        return parent;
    }

    public List<Instruction> getInstructions() {
        return List.copyOf(instructions);
    }

    // 检查块是否以跳转、返回等终结指令收尾
    public boolean isTerminated() {
        return !instructions.isEmpty() && instructions.get(instructions.size() - 1).isTerminator();
    }

    // 向未终结的块追加一条属于本块的指令
    public void append(Instruction instruction) {
        if (isTerminated())
            throw new IllegalStateException("Instruction after terminator");
        if (instruction.getParent() != this)
            throw new IllegalArgumentException("Wrong parent");
        instructions.add(instruction);
    }

    // 将栈槽分配集中插在块首，排在已有 alloca 之后
    public void prependAlloca(Instruction instruction) {
        if (instruction.getParent() != this || instruction.getOpcode() != Instruction.Opcode.ALLOCA)
            throw new IllegalArgumentException("Expected entry alloca");
        int index = 0;
        while (index < instructions.size() && instructions.get(index).getOpcode() == Instruction.Opcode.ALLOCA)
            index++;
        instructions.add(index, instruction);
    }

    // 删除未被引用的指令，同时解除它对操作数的引用
    public void remove(Instruction instruction) {
        if (!instruction.getUses().isEmpty())
            throw new IllegalStateException("Instruction is still used");
        if (instructions.remove(instruction))
            instruction.detachOperands();
    }
}
