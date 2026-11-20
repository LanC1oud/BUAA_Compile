package llvm.instruction;

import llvm.type.IrType;
import llvm.value.*;

import java.util.ArrayList;
import java.util.List;

/** Structured opcode + typed operands; text is produced only by IrPrinter. */
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

    public void replaceOperand(int index, Value value) {
        Value old = operands.get(index);
        if (!old.getType().equals(value.getType()))
            throw new IllegalArgumentException("Operand type mismatch");
        old.removeUse(this, index);
        operands.set(index, value);
        value.addUse(this, index);
    }

    public void detachOperands() {
        for (int i = 0; i < operands.size(); i++)
            operands.get(i).removeUse(this, i);
        operands.clear();
    }

    public boolean isTerminator() {
        return opcode == Opcode.BR || opcode == Opcode.RET || opcode == Opcode.UNREACHABLE;
    }
}
