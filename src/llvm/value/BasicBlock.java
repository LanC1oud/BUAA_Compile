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

    public void prependPhi(Instruction instruction) {
        if (instruction.getParent() != this || instruction.getOpcode() != Instruction.Opcode.PHI)
            throw new IllegalArgumentException("Expected phi");
        int n = 0;
        while (n < instructions.size() && instructions.get(n).getOpcode() == Instruction.Opcode.PHI) n++;
        instructions.add(n, instruction);
    }

    public void insertBeforeTerminator(Instruction instruction) {
        if (instruction.getParent() != this || instruction.isTerminator()) throw new IllegalArgumentException("Invalid insertion");
        instructions.add(isTerminated() ? instructions.size() - 1 : instructions.size(), instruction);
    }

    public void moveBeforeTerminator(Instruction instruction, BasicBlock target) {
        if (!instructions.remove(instruction)) throw new IllegalArgumentException("Missing instruction");
        instruction.relocate(target);
        target.insertBeforeTerminator(instruction);
    }

    public void replaceTerminator(Instruction replacement) {
        if (!isTerminated() || replacement.getParent() != this || !replacement.isTerminator())
            throw new IllegalArgumentException("Invalid terminator replacement");
        Instruction old = instructions.remove(instructions.size() - 1);
        old.detachOperands();
        instructions.add(replacement);
    }

    public void insertBefore(Instruction reference, Instruction instruction) {
        int n = instructions.indexOf(reference);
        if (n < 0 || instruction.getParent() != this) throw new IllegalArgumentException("Invalid insertion point");
        instructions.add(n, instruction);
    }

    /** Split after a call and repair successor PHI predecessor identities. */
    public BasicBlock splitAfter(Instruction reference) {
        int index = instructions.indexOf(reference);
        if (index < 0) throw new IllegalArgumentException("Missing split point");
        BasicBlock continuation = parent.newBlock();
        List<Instruction> tail = new ArrayList<>(instructions.subList(index + 1, instructions.size()));
        instructions.subList(index + 1, instructions.size()).clear();
        for (Instruction i : tail) { i.relocate(continuation); continuation.append(i); }
        if (continuation.isTerminated()) {
            Instruction terminator = tail.get(tail.size() - 1);
            for (Value v : terminator.getOperands()) if (v instanceof BasicBlock target)
                for (Instruction phi : target.getInstructions()) {
                    if (phi.getOpcode() != Instruction.Opcode.PHI) break;
                    for (int n = 1; n < phi.getOperands().size(); n += 2)
                        if (phi.getOperands().get(n) == this) phi.replaceOperand(n, continuation);
                }
        }
        return continuation;
    }

    public void orderByDependencies() {
        List<Instruction> ordered = new ArrayList<>();
        java.util.Set<Instruction> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Instruction i : instructions) if (i.getOpcode() == Instruction.Opcode.PHI) { ordered.add(i); visited.add(i); }
        for (Instruction i : List.copyOf(instructions)) order(i, visited, ordered);
        instructions.clear(); instructions.addAll(ordered);
    }

    private void order(Instruction i, java.util.Set<Instruction> visited, List<Instruction> ordered) {
        if (!visited.add(i)) return;
        for (Value value : i.getOperands()) if (value instanceof Instruction def && def.getParent() == this) order(def, visited, ordered);
        ordered.add(i);
    }

    // 删除已无使用者的指令，并解除其操作数使用关系
    public void remove(Instruction instruction) {
        if (!instruction.getUses().isEmpty())
            throw new IllegalStateException("Instruction is still used");
        if (instructions.remove(instruction))
            instruction.detachOperands();
    }
}
