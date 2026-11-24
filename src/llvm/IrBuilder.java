package llvm;

import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;

import java.util.*;

/** 管理当前函数和插入位置，构造并插入带类型的指令。 */
public final class IrBuilder {
    private Function function;
    private BasicBlock block;

    public Function getFunction() {
        return function;
    }

    public BasicBlock getBlock() {
        return block;
    }

    // 切换到指定函数，并定位到新建的入口块
    public void enterFunction(Function function) {
        this.function = function;
        block = function.newBlock();
    }

    // 在当前函数创建基本块，保留当前插入位置
    public BasicBlock newBlock() {
        return function.newBlock();
    }

    // 将后续指令的插入位置切换到指定块
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

    // 构造指令并追加到当前块，禁止在终结指令后插入
    public Instruction emit(Opcode op, IrType type, String predicate, Value... args) {
        if (isTerminated())
            throw new IllegalStateException("Cannot emit after terminator");
        Instruction i = make(op, type, predicate, List.of(args));
        block.append(i);
        return i;
    }

    // 在函数入口块分配栈槽，返回其地址
    public Value alloca(IrType type) {
        BasicBlock entry = function.getBlocks().get(0);
        Instruction i =
                new Instruction(entry, Opcode.ALLOCA, IrType.pointer(type), function.nextValueName(), "", List.of());
        entry.prependAlloca(i);
        return i;
    }

    // 从地址读取值，结果类型为指针指向的类型
    public Value load(Value address) {
        return emit(Opcode.LOAD, address.getType().element(), "", address);
    }

    // 将值写入指定地址
    public void store(Value value, Value address) {
        emit(Opcode.STORE, IrType.VOID, "", value, address);
    }

    // 生成双操作数算术指令，结果沿用操作数类型
    public Value binary(Opcode op, Value left, Value right) {
        return emit(op, left.getType(), "", left, right);
    }

    // 按指定谓词生成整数比较，结果为 i1
    public Value compare(String predicate, Value left, Value right) {
        return emit(Opcode.ICMP, IrType.I1, predicate, left, right);
    }

    // 调整整数位宽：扩宽用 zext，缩窄用 trunc
    public Value convert(Value value, IrType type) {
        if (value.getType().equals(type))
            return value;
        if (!value.getType().isInteger() || !type.isInteger())
            throw new IllegalArgumentException("Invalid conversion");
        return emit(value.getType().bits() < type.bits() ? Opcode.ZEXT : Opcode.TRUNC, type, "", value);
    }

    // 按下标计算元素地址，数组对象的指针需加前导零下标
    public Value gep(Value base, Value index) {
        IrType source = base.getType().element();
        return source.kind() == IrType.Kind.ARRAY
                ? emit(Opcode.GEP, IrType.pointer(source.element()), "", base, Constant.integer(IrType.I32, 0), index)
                : emit(Opcode.GEP, IrType.pointer(source), "", base, index);
    }

    // 按目标函数和实参构造 call 指令
    public Value call(Function callee, List<Value> args) {
        List<Value> operands = new ArrayList<>();
        operands.add(callee);
        operands.addAll(args);
        return emit(Opcode.CALL, callee.getReturnType(), "", operands.toArray(Value[]::new));
    }

    // 生成无条件跳转，终结当前块
    public void branch(BasicBlock target) {
        emit(Opcode.BR, IrType.VOID, "", target);
    }

    // 仅在当前块尚未终结时补上跳转
    public void branchIfOpen(BasicBlock target) {
        if (!isTerminated())
            branch(target);
    }

    // 按 i1 条件跳到真分支或假分支
    public void branch(Value condition, BasicBlock yes, BasicBlock no) {
        emit(Opcode.BR, IrType.VOID, "", condition, yes, no);
    }

    // 生成返回指令，空值对应 ret void
    public void ret(Value value) {
        if (value == null)
            emit(Opcode.RET, IrType.VOID, "");
        else
            emit(Opcode.RET, IrType.VOID, "", value);
    }
}
