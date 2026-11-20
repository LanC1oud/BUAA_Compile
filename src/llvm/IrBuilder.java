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
        String name = type.equals(IrType.VOID) ? "" : function.nextValueName();
        return new Instruction(block, op, type, name, predicate, args);
    }

    public Instruction emit(Opcode op, IrType type, String predicate, Value... args) {
        if (isTerminated())
            throw new IllegalStateException("Cannot emit after terminator");
        List<Value> operands = List.of(args);
        Instruction instruction = make(op, type, predicate, operands);
        block.append(instruction);
        return instruction;
    }

    public Value alloca(IrType type) {
        BasicBlock entry = function.getBlocks().get(0);
        IrType pointerType = IrType.pointer(type);
        String name = function.nextValueName();
        Instruction instruction = new Instruction(entry, Opcode.ALLOCA, pointerType, name, "", List.of());
        entry.prependAlloca(instruction);
        return instruction;
    }

    public Value load(Value address) {
        IrType valueType = address.getType().element();
        return emit(Opcode.LOAD, valueType, "", address);
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
        IrType sourceType = value.getType();
        if (sourceType.equals(type))
            return value;
        if (!sourceType.isInteger() || !type.isInteger())
            throw new IllegalArgumentException("Invalid conversion");
        Opcode opcode = sourceType.bits() < type.bits() ? Opcode.ZEXT : Opcode.TRUNC;
        return emit(opcode, type, "", value);
    }

    public Value gep(Value base, Value index) {
        IrType source = base.getType().element();
        if (source.kind() == IrType.Kind.ARRAY) {
            IrType elementType = source.element();
            IrType resultType = IrType.pointer(elementType);
            Constant firstIndex = Constant.integer(IrType.I32, 0);
            return emit(Opcode.GEP, resultType, "", base, firstIndex, index);
        }
        IrType resultType = IrType.pointer(source);
        return emit(Opcode.GEP, resultType, "", base, index);
    }

    public Value call(Function callee, List<Value> args) {
        List<Value> operands = new ArrayList<>();
        operands.add(callee);
        operands.addAll(args);
        IrType returnType = callee.getReturnType();
        Value[] callOperands = operands.toArray(Value[]::new);
        return emit(Opcode.CALL, returnType, "", callOperands);
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
