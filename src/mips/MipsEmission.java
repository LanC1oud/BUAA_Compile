package mips;

import llvm.type.IrType;
import llvm.value.Value;

/** 供生成期间的优化调用的基础发射操作；所有指令仍经过生成器统一输出。 */
public interface MipsEmission {
    void emit(String instruction);
    void text(String line);
    void load(Value value, String register);
    void save(Value value, String register);
    void memory(String opcode, String register, int offset, String base);
    void addOffset(String target, String base, int offset);
    void signExtend(String register, IrType type);
    void jump(String opcode, String target);
    String label(Value value);
    String nextBranchLabel();
}
