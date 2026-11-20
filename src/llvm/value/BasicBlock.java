package llvm.value;

import llvm.instruction.Instruction;
import llvm.type.IrType;

import java.util.ArrayList;
import java.util.List;

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

    public boolean isTerminated() {
        return !instructions.isEmpty() && instructions.get(instructions.size() - 1).isTerminator();
    }

    public void append(Instruction instruction) {
        if (isTerminated())
            throw new IllegalStateException("Instruction after terminator");
        if (instruction.getParent() != this)
            throw new IllegalArgumentException("Wrong parent");
        instructions.add(instruction);
    }

    public void prependAlloca(Instruction instruction) {
        if (instruction.getParent() != this || instruction.getOpcode() != Instruction.Opcode.ALLOCA)
            throw new IllegalArgumentException("Expected entry alloca");
        int index = 0;
        while (index < instructions.size() && instructions.get(index).getOpcode() == Instruction.Opcode.ALLOCA)
            index++;
        instructions.add(index, instruction);
    }

    public void remove(Instruction instruction) {
        if (!instruction.getUses().isEmpty())
            throw new IllegalStateException("Instruction is still used");
        if (instructions.remove(instruction))
            instruction.detachOperands();
    }
}
