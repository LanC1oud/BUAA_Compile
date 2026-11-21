package llvm.value;

import llvm.instruction.Instruction;
import llvm.type.IrType;

import java.util.ArrayList;
import java.util.List;

// 保存函数内一个基本块的指令序列
public final class BasicBlock extends Value {
    private final Function parent;
    private final List<Instruction> instructions = new ArrayList<>();

    // 创建属于指定函数的空基本块
    public BasicBlock(Function parent, String name) {
        super(IrType.LABEL, name);
        this.parent = parent;
    }

    // 取基本块所属的函数
    public Function getParent() {
        return parent;
    }

    // 按执行顺序取指令列表的只读快照
    public List<Instruction> getInstructions() {
        return List.copyOf(instructions);
    }

    // 判断最后一条指令是否为终结指令
    public boolean isTerminated() {
        return !instructions.isEmpty() && instructions.get(instructions.size() - 1).isTerminator();
    }

    // 向尚未终结的基本块追加所属指令
    public void append(Instruction instruction) {
        if (isTerminated())
            throw new IllegalStateException("Instruction after terminator");
        if (instruction.getParent() != this)
            throw new IllegalArgumentException("Wrong parent");
        instructions.add(instruction);
    }

    // 将 alloca 插入块首已有的连续分配指令之后
    public void prependAlloca(Instruction instruction) {
        if (instruction.getParent() != this || instruction.getOpcode() != Instruction.Opcode.ALLOCA)
            throw new IllegalArgumentException("Expected entry alloca");
        int index = 0;
        while (index < instructions.size() && instructions.get(index).getOpcode() == Instruction.Opcode.ALLOCA)
            index++;
        instructions.add(index, instruction);
    }

    // 删除已无使用者的指令，并解除其操作数使用关系
    public void remove(Instruction instruction) {
        if (!instruction.getUses().isEmpty())
            throw new IllegalStateException("Instruction is still used");
        if (instructions.remove(instruction))
            instruction.detachOperands();
    }
}
