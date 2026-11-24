package llvm.instruction;

import llvm.type.IrType;
import llvm.value.*;

import java.util.ArrayList;
import java.util.List;

// 用操作码、带类型的操作数和所属基本块表示一条 IR 指令
public final class Instruction extends Value {
    // 当前支持的指令操作码
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
        PHI,
        CALL,
        BR,
        RET,
        UNREACHABLE
    }

    private final Opcode opcode;
    private BasicBlock parent;
    private final List<Value> operands;
    private final String predicate;

    // 创建指令并登记操作数使用关系，暂不加入基本块
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

    // 取指令操作码
    public Opcode getOpcode() {
        return opcode;
    }

    // 取指令所属的基本块
    public BasicBlock getParent() {
        return parent;
    }

    // 取整数比较使用的比较谓词
    public String getPredicate() {
        return predicate;
    }

    // 取操作数列表的只读快照
    public List<Value> getOperands() {
        return List.copyOf(operands);
    }

    public void resetOperands(List<? extends Value> values) {
        detachOperands();
        operands.addAll(values);
        for (int n = 0; n < operands.size(); n++) operands.get(n).addUse(this, n);
    }

    public void addIncoming(Value value, BasicBlock predecessor) {
        if (opcode != Opcode.PHI || !getType().equals(value.getType()))
            throw new IllegalArgumentException("Invalid phi incoming value");
        int n = operands.size();
        operands.add(value); value.addUse(this, n);
        operands.add(predecessor); predecessor.addUse(this, n + 1);
    }

    public void relocate(BasicBlock target) {
        if (target.getParent() != parent.getParent()) throw new IllegalArgumentException("Cross-function move");
        parent = target;
    }

    // 替换指定位置的同类型操作数，并同步使用关系
    public void replaceOperand(int index, Value value) {
        Value old = operands.get(index);
        if (!old.getType().equals(value.getType()))
            throw new IllegalArgumentException("Operand type mismatch");
        old.removeUse(this, index);
        operands.set(index, value);
        value.addUse(this, index);
    }

    // 解除全部操作数的使用关系并清空操作数列表
    public void detachOperands() {
        for (int i = 0; i < operands.size(); i++)
            operands.get(i).removeUse(this, i);
        operands.clear();
    }

    // 判断是否为跳转、返回或 unreachable 终结指令
    public boolean isTerminator() {
        return opcode == Opcode.BR || opcode == Opcode.RET || opcode == Opcode.UNREACHABLE;
    }
}
