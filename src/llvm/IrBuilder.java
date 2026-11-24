package llvm;

import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;

import java.util.*;

// 管理 IR 的当前插入位置，提供常用指令的构造接口
public final class IrBuilder {
    private Function function;
    private BasicBlock block;

    // 取当前正在构造的函数
    public Function getFunction() {
        return function;
    }

    // 取当前插入指令的基本块
    public BasicBlock getBlock() {
        return block;
    }

    // 切换到指定函数并创建入口块
    public void enterFunction(Function function) {
        this.function = function;
        block = function.newBlock();
    }

    // 在当前函数中新建基本块，不切换插入位置
    public BasicBlock newBlock() {
        return function.newBlock();
    }

    // 将插入位置切换到当前函数内的指定块
    public void positionAt(BasicBlock block) {
        if (block.getParent() != function)
            throw new IllegalArgumentException("Cross-function insertion");
        this.block = block;
    }

    // 判断当前基本块是否已有终结指令
    public boolean isTerminated() {
        return block.isTerminated();
    }

    // 创建指令并分配结果名，暂不插入基本块
    private Instruction make(Opcode op, IrType type, String predicate, List<? extends Value> args) {
        String name = type.equals(IrType.VOID) ? "" : function.nextValueName();
        return new Instruction(block, op, type, name, predicate, args);
    }

    // 创建指令并追加到当前尚未终结的基本块
    public Instruction emit(Opcode op, IrType type, String predicate, Value... args) {
        if (isTerminated())
            throw new IllegalStateException("Cannot emit after terminator");
        List<Value> operands = List.of(args);
        Instruction instruction = make(op, type, predicate, operands);
        block.append(instruction);
        return instruction;
    }

    // 在函数入口块分配局部存储，返回其地址
    public Value alloca(IrType type) {
        BasicBlock entry = function.getBlocks().get(0);
        IrType pointerType = IrType.pointer(type);
        String name = function.nextValueName();
        Instruction instruction = new Instruction(entry, Opcode.ALLOCA, pointerType, name, "", List.of());
        entry.prependAlloca(instruction);
        return instruction;
    }

    // 从指定地址读取值
    public Value load(Value address) {
        IrType valueType = address.getType().element();
        return emit(Opcode.LOAD, valueType, "", address);
    }

    // 将值写入指定地址
    public void store(Value value, Value address) {
        emit(Opcode.STORE, IrType.VOID, "", value, address);
    }

    // 生成二元算术指令
    public Value binary(Opcode op, Value left, Value right) {
        return emit(op, left.getType(), "", left, right);
    }

    // 按比较谓词生成结果为 i1 的整数比较
    public Value compare(String predicate, Value left, Value right) {
        return emit(Opcode.ICMP, IrType.I1, predicate, left, right);
    }

    // 按目标整数位宽生成零扩展或截断，类型相同时直接返回
    public Value convert(Value value, IrType type) {
        IrType sourceType = value.getType();
        if (sourceType.equals(type))
            return value;
        if (!sourceType.isInteger() || !type.isInteger())
            throw new IllegalArgumentException("Invalid conversion");
        Opcode opcode = sourceType.bits() < type.bits() ? Opcode.ZEXT : Opcode.TRUNC;
        return emit(opcode, type, "", value);
    }

    // 根据数组或元素指针计算下标对应的地址
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

    // 生成函数调用并返回调用结果
    public Value call(Function callee, List<Value> args) {
        List<Value> operands = new ArrayList<>();
        operands.add(callee);
        operands.addAll(args);
        IrType returnType = callee.getReturnType();
        Value[] callOperands = operands.toArray(Value[]::new);
        return emit(Opcode.CALL, returnType, "", callOperands);
    }

    // 生成到指定基本块的无条件跳转
    public void branch(BasicBlock target) {
        emit(Opcode.BR, IrType.VOID, "", target);
    }

    // 当前块尚未终结时补上无条件跳转
    public void branchIfOpen(BasicBlock target) {
        if (!isTerminated())
            branch(target);
    }

    // 根据 i1 条件跳转到真假出口
    public void branch(Value condition, BasicBlock yes, BasicBlock no) {
        emit(Opcode.BR, IrType.VOID, "", condition, yes, no);
    }

    // 生成返回指令，value 为 null 时返回 void
    public void ret(Value value) {
        if (value == null)
            emit(Opcode.RET, IrType.VOID, "");
        else
            emit(Opcode.RET, IrType.VOID, "", value);
    }
}
