package llvm;

import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;

import java.util.*;

public final class IrBuilder {
    private Function function;
    private BasicBlock block;

    public Function getFunction() {
        return function;
    }

    public BasicBlock getBlock() {
        return block;
    }

    public void enterFunction(Function function) {
        this.function = function;
        block = function.newBlock();
    }

    public BasicBlock newBlock() {
        return function.newBlock();
    }

    public void positionAt(BasicBlock block) {
        if (block.getParent() != function)
            throw new IllegalArgumentException("Cross-function insertion");
        this.block = block;
    }

    public boolean isTerminated() {
        return block.isTerminated();
    }

    private Instruction make(Opcode op, IrType type, String predicate, List<? extends Value> args) {
        return new Instruction(
                block, op, type, type.equals(IrType.VOID) ? "" : function.nextValueName(), predicate, args);
    }

    public Instruction emit(Opcode op, IrType type, String predicate, Value... args) {
        if (isTerminated())
            throw new IllegalStateException("Cannot emit after terminator");
        Instruction i = make(op, type, predicate, List.of(args));
        block.append(i);
        return i;
    }

    public Value alloca(IrType type) {
        BasicBlock entry = function.getBlocks().get(0);
        Instruction i =
                new Instruction(entry, Opcode.ALLOCA, IrType.pointer(type), function.nextValueName(), "", List.of());
        entry.prependAlloca(i);
        return i;
    }

    public Value load(Value address) {
        return emit(Opcode.LOAD, address.getType().element(), "", address);
    }

    public void store(Value value, Value address) {
        emit(Opcode.STORE, IrType.VOID, "", value, address);
    }

    public Value binary(Opcode op, Value left, Value right) {
        return emit(op, left.getType(), "", left, right);
    }

    public Value compare(String predicate, Value left, Value right) {
        return emit(Opcode.ICMP, IrType.I1, predicate, left, right);
    }

    public Value convert(Value value, IrType type) {
        if (value.getType().equals(type))
            return value;
        if (!value.getType().isInteger() || !type.isInteger())
            throw new IllegalArgumentException("Invalid conversion");
        return emit(value.getType().bits() < type.bits() ? Opcode.ZEXT : Opcode.TRUNC, type, "", value);
    }

    public Value gep(Value base, Value index) {
        IrType source = base.getType().element();
        return source.kind() == IrType.Kind.ARRAY
                ? emit(Opcode.GEP, IrType.pointer(source.element()), "", base, Constant.integer(IrType.I32, 0), index)
                : emit(Opcode.GEP, IrType.pointer(source), "", base, index);
    }

    public Value call(Function callee, List<Value> args) {
        List<Value> operands = new ArrayList<>();
        operands.add(callee);
        operands.addAll(args);
        return emit(Opcode.CALL, callee.getReturnType(), "", operands.toArray(Value[]::new));
    }

    public void branch(BasicBlock target) {
        emit(Opcode.BR, IrType.VOID, "", target);
    }

    public void branchIfOpen(BasicBlock target) {
        if (!isTerminated())
            branch(target);
    }

    public void branch(Value condition, BasicBlock yes, BasicBlock no) {
        emit(Opcode.BR, IrType.VOID, "", condition, yes, no);
    }

    public void ret(Value value) {
        if (value == null)
            emit(Opcode.RET, IrType.VOID, "");
        else
            emit(Opcode.RET, IrType.VOID, "", value);
    }
}
