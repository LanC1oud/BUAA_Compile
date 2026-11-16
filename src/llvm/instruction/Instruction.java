package llvm.instruction;

import llvm.type.IrType;
import llvm.value.*;

import java.util.ArrayList;
import java.util.List;

/** 保存操作码、结果类型和操作数，由 IrPrinter 统一输出文本。 */
public final class Instruction extends Value {
    public enum Opcode {
        ALLOCA,
        LOAD,
        STORE,
        ADD,
        SUB,
        MUL,
        SDIV,
        SREM,
        UDIV,
        UREM,
        ICMP,
        ZEXT,
        TRUNC,
        GEP,
        CALL,
        BR,
        RET,
        UNREACHABLE
    }

    private final Opcode opcode;
    private final BasicBlock parent;
    private final List<Value> operands;
    private final String predicate;

    // 构造指令，并在各操作数上登记使用关系
    public Instruction(BasicBlock parent, Opcode opcode, IrType type, String name, String predicate,
                       List<? extends Value> operands) {
        super(type, name);
        this.parent = parent;
        this.opcode = opcode;
        this.predicate = predicate;
        this.operands = new ArrayList<>(operands);
        for (int i = 0; i < this.operands.size(); i++)
            this.operands.get(i).addUse(this, i);
    }

    public Opcode getOpcode() {
        return opcode;
    }

    public BasicBlock getParent() {
        return parent;
    }

    public String getPredicate() {
        return predicate;
    }

    public List<Value> getOperands() {
        return List.copyOf(operands);
    }

    // 替换同类型操作数，并更新新旧值的使用记录
    public void replaceOperand(int index, Value value) {
        Value old = operands.get(index);
        if (!old.getType().equals(value.getType()))
            throw new IllegalArgumentException("Operand type mismatch");
        old.removeUse(this, index);
        operands.set(index, value);
        value.addUse(this, index);
    }

    // 解除该指令对所有操作数的引用
    public void detachOperands() {
        for (int i = 0; i < operands.size(); i++)
            operands.get(i).removeUse(this, i);
        operands.clear();
    }

    // 判断是否为跳转、返回或不可达等基本块终结指令
    public boolean isTerminator() {
        return opcode == Opcode.BR || opcode == Opcode.RET || opcode == Opcode.UNREACHABLE;
    }
}
