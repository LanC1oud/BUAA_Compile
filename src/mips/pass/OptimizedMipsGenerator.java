package mips.pass;

import llvm.IrModule;
import llvm.instruction.Instruction;
import llvm.type.IrType;
import llvm.value.*;
import mips.MipsFrame;
import mips.MipsGenerator;
import mips.MipsModule;

import java.util.*;
import static mips.MipsLayout.*;

// 集中管理优化状态和指令选择，继承基础降低并执行后处理 Pass
public final class OptimizedMipsGenerator extends MipsGenerator {
    private static final Set<String> REGISTER_WRITERS = Set.of("li", "la", "move", "addiu", "addu", "subu",
            "and", "andi", "xor", "xori", "sll", "srl", "sra", "slt", "sltu", "sltiu", "mflo", "mfhi", "lw", "lbu");
    private final List<MipsPass> passes;
    // 仅缓存不可变 IR 值；基本块和调用边界使缓存失效。
    private final Map<String, Value> registers = new LinkedHashMap<>();
    private MipsConstants constants;
    private MipsFunctionPlan plan;
    private MipsLocalRegisters locals;
    private BasicBlock currentBlock;
    private int position;

    // 创建使用指定后处理 Pass 的优化生成器
    public OptimizedMipsGenerator(List<MipsPass> passes) {
        this.passes = List.copyOf(passes);
    }

    // 生成优化汇编，再依次执行目标代码 Pass
    @Override
    public MipsModule generate(IrModule module) {
        MipsModule output = super.generate(module);
        for (MipsPass pass : passes) pass.run(output);
        return output;
    }

    // 初始化函数分析、优化栈帧和块内寄存器管理
    @Override
    protected MipsFrame createFrame(Function function) {
        constants = new MipsConstants(function);
        plan = new MipsFunctionPlan(function, constants);
        MipsFrame layout = MipsFrameOptimization.allocate(function, plan);
        locals = new MipsLocalRegisters(plan,
                (value, register) -> memory("sw", register, layout.slots.get(value), "$sp"));
        currentBlock = null;
        registers.clear();
        return layout;
    }

    // 跳过没有使用者的形参，保留其余形参的传递约定
    @Override
    protected void emitArgument(Argument argument, int index) {
        if (!argument.getUses().isEmpty()) super.emitArgument(argument, index);
    }

    // 生成函数，并在结束时清理当前块状态
    @Override
    protected void emitFunction(Function function) {
        try {
            super.emitFunction(function);
        } finally {
            currentBlock = null;
        }
    }

    // 进入基本块时更新寄存器分配和缓存状态
    @Override
    protected void emitBlock(BasicBlock block) {
        registers.clear();
        currentBlock = block;
        locals.begin(block);
        super.emitBlock(block);
    }

    // 记录指令位置，优先使用优化选择，未接管时回退到基础降低
    @Override
    protected void emitInstruction(Instruction instruction, int position) {
        this.position = position;
        if (!tryOptimizedInstruction(instruction)) super.emitInstruction(instruction, position);
    }

    // 在已放置实参后保存跨调用存活的寄存器结果
    @Override
    protected void invokeCall(Instruction instruction) {
        locals.beforeCall(position);
        super.invokeCall(instruction);
    }

    // 在跳转前写回跨块值，条件值须先装入临时寄存器
    @Override
    protected void emitBranch(Instruction instruction) {
        var operands = instruction.getOperands();
        if (operands.size() == 1) {
            locals.spillLiveOut();
            super.emitBranch(instruction);
        } else {
            load(operands.get(0), "$t0");
            locals.spillLiveOut();
            emitConditionalBranch(operands.get(1), operands.get(2));
        }
    }

    // 优先复用寄存器、常量或固定地址，其余值使用基础加载
    @Override
    protected void load(Value value, String register) {
        Constant known = constants.get(value);
        Value key = known != null ? known : value;
        if (sameValue(registers.get(register), key)) return;
        String resident = locals.get(value);
        if (resident != null) {
            if (!resident.equals(register)) emit("move " + register + ", " + resident);
            registers.put(register, key);
            return;
        }
        String cached = null;
        for (var entry : registers.entrySet())
            if (sameValue(entry.getValue(), key)) {
                cached = entry.getKey();
                if (cached.equals(register)) break;
            }
        if (cached != null) {
            if (!cached.equals(register)) emit("move " + register + ", " + cached);
            registers.put(register, key);
            return;
        }
        Integer address = stackAddress(value);
        if (key instanceof Constant c) emit("li " + register + ", " + c.getNumber());
        else if (address != null) addOffset(register, "$sp", address);
        else super.load(value, register);
        registers.put(register, key);
    }

    // 省去无用结果写入，块内结果优先保存在寄存器
    @Override
    protected void save(Value value, String register) {
        if (value.getUses().isEmpty()) return;
        normalize(value, register);
        if (currentBlock != null) {
            String home = locals.assign(value, position);
            if (!home.equals(register)) emit("move " + home + ", " + register);
        } else memory("sw", register, frame.slots.get(value), "$sp");
        registers.put(register, value);
    }

    // 省略不会改变地址的原地加零
    @Override
    protected void addOffset(String target, String base, int offset) {
        if (offset == 0 && target.equals(base)) return;
        super.addOffset(target, base, offset);
    }

    // 根据指令写入目标更新缓存，调用时使缓存全部失效
    @Override
    protected void emit(String instruction) {
        int space = instruction.indexOf(' ');
        String opcode = space < 0 ? instruction : instruction.substring(0, space);
        if (opcode.equals("jal") || opcode.equals("syscall")) registers.clear();
        else if (REGISTER_WRITERS.contains(opcode)) {
            int comma = instruction.indexOf(',', space + 1);
            String destination = instruction.substring(space + 1, comma < 0 ? instruction.length() : comma);
            registers.remove(destination);
        }
        super.emit(instruction);
    }

    // 尝试优化或省略当前指令，已接管时返回真
    private boolean tryOptimizedInstruction(Instruction instruction) {
        if (constants.get(instruction) != null || plan.isFused(instruction)) return true;
        var operands = instruction.getOperands();
        switch (instruction.getOpcode()) {
            case LOAD -> {
                Integer offset = stackAddress(operands.get(0));
                if (offset == null) return false;
                memory(sizeOf(instruction.getType()) == 1 ? "lbu" : "lw", "$t1", offset, "$sp");
                save(instruction, "$t1");
                return true;
            }
            case STORE -> {
                Integer offset = stackAddress(operands.get(1));
                if (offset == null) return false;
                load(operands.get(0), "$t0");
                memory(sizeOf(operands.get(0).getType()) == 1 ? "sb" : "sw", "$t0", offset, "$sp");
                return true;
            }
            case GEP -> {
                if (stackAddress(instruction) == null) optimizedGep(instruction);
                return true;
            }
            case ADD, SUB, MUL, SDIV, SREM, UDIV, UREM -> { return selectArithmetic(instruction); }
            case BR -> {
                if (operands.size() == 1) return false;
                Constant condition = constants.get(operands.get(0));
                if (condition != null) {
                    locals.spillLiveOut();
                    jump("j", label(operands.get(condition.getNumber() != 0 ? 1 : 2)));
                    return true;
                }
                MipsFunctionPlan.Comparison comparison = plan.comparisonFor(instruction);
                if (comparison == null) return false;
                branchComparison(comparison.instruction(), operands.get(comparison.inverted() ? 2 : 1),
                        operands.get(comparison.inverted() ? 1 : 2));
                return true;
            }
            default -> { return false; }
        }
    }

    // 将比较融合为条件分支，保留远跳转与延迟槽处理
    private void branchComparison(Instruction comparison, Value yes, Value no) {
        var operands = comparison.getOperands();
        load(operands.get(0), "$t0");
        load(operands.get(1), "$t1");
        String predicate = comparison.getPredicate();
        if (predicate.startsWith("s")) {
            signExtend("$t0", operands.get(0).getType());
            signExtend("$t1", operands.get(1).getType());
        }
        String nearby = nextBranchLabel();
        String branch;
        if (predicate.equals("eq") || predicate.equals("ne")) {
            branch = predicate.equals("eq") ? "bne" : "beq";
            locals.spillLiveOut();
            jump(branch, "$t0, $t1, " + nearby);
        } else {
            String less = predicate.startsWith("u") ? "sltu" : "slt";
            boolean reversed = predicate.endsWith("gt") || predicate.endsWith("le");
            emit(less + " $t2, " + (reversed ? "$t1, $t0" : "$t0, $t1"));
            branch = predicate.endsWith("le") || predicate.endsWith("ge") ? "bne" : "beq";
            locals.spillLiveOut();
            jump(branch, "$t2, $zero, " + nearby);
        }
        jump("j", label(yes));
        text(nearby + ":");
        jump("j", label(no));
    }

    // 合并常量下标偏移，生成其余下标的地址计算
    private void optimizedGep(Instruction instruction) {
        List<Value> operands = instruction.getOperands();
        load(operands.get(0), "$t0");
        IrType indexed = operands.get(0).getType().element();
        int constantOffset = 0;
        for (int i = 1; i < operands.size(); i++) {
            if (i > 1) indexed = indexed.element();
            int stride = sizeOf(indexed);
            Constant index = constants.get(operands.get(i));
            if (index != null) {
                constantOffset += index.getNumber() * stride;
                continue;
            }
            load(operands.get(i), "$t1");
            if (stride == 4) emit("sll $t1, $t1, 2");
            else if (stride != 1) {
                emit("li $t2, " + stride);
                emit("multu $t1, $t2");
                emit("mflo $t1");
            }
            emit("addu $t0, $t0, $t1");
        }
        if (constantOffset != 0) addOffset("$t0", "$t0", constantOffset);
        save(instruction, "$t0");
    }

    // 按对象身份或整数常量的类型和值判断是否相同
    private static boolean sameValue(Value left, Value right) {
        return left == right || left instanceof Constant a && right instanceof Constant b
                && a.getType().equals(b.getType()) && a.getNumber() == b.getNumber();
    }

    // 计算固定局部对象或全常量 GEP 的栈偏移
    private Integer stackAddress(Value value) {
        Integer object = frame.objects.get(value);
        if (object != null) return object;
        if (!(value instanceof Instruction i) || i.getOpcode() != Instruction.Opcode.GEP) return null;
        var operands = i.getOperands();
        Integer offset = stackAddress(operands.get(0));
        if (offset == null) return null;
        IrType type = operands.get(0).getType().element();
        for (int n = 1; n < operands.size(); n++) {
            if (n > 1) type = type.element();
            Constant index = constants.get(operands.get(n));
            if (index == null) return null;
            offset += index.getNumber() * sizeOf(type);
        }
        return offset;
    }

    // 尝试用立即数、移位和掩码替换常量算术
    private boolean selectArithmetic(Instruction instruction) {
        var op = instruction.getOpcode();
        Value left = instruction.getOperands().get(0), right = instruction.getOperands().get(1);
        Constant c = constants.get(right);
        if ((op == Instruction.Opcode.ADD || op == Instruction.Opcode.MUL) && c == null) {
            c = constants.get(left);
            Value swap = left; left = right; right = swap;
        }
        if (c == null) return false;
        int number = c.getNumber();
        if ((op == Instruction.Opcode.ADD && shortImmediate(number))
                || (op == Instruction.Opcode.SUB && shortImmediate(-number))) {
            load(left, "$t0");
            emit("addiu $t2, $t0, " + (op == Instruction.Opcode.SUB ? -number : number));
        } else if (op == Instruction.Opcode.MUL && (number == 0 || number == -1 || Integer.bitCount(number) == 1)) {
            load(left, "$t0");
            if (number == 0) emit("li $t2, 0");
            else if (number == -1) emit("subu $t2, $zero, $t0");
            else emit("sll $t2, $t0, " + Integer.numberOfTrailingZeros(number));
        } else if ((op == Instruction.Opcode.UDIV || op == Instruction.Opcode.UREM) && Integer.bitCount(number) == 1) {
            load(left, "$t0");
            if (op == Instruction.Opcode.UDIV) emit("srl $t2, $t0, " + Integer.numberOfTrailingZeros(number));
            else mask("$t2", "$t0", number - 1);
        } else if (op == Instruction.Opcode.SDIV || op == Instruction.Opcode.SREM) {
            int divisor = MipsConstants.signed(c);
            if (divisor == 0 || divisor == Integer.MIN_VALUE || Integer.bitCount(Math.abs(divisor)) != 1) return false;
            load(left, "$t0");
            signExtend("$t0", instruction.getType());
            int shift = Integer.numberOfTrailingZeros(Math.abs(divisor));
            if (shift == 0) {
                if (op == Instruction.Opcode.SREM) emit("li $t2, 0");
                else emit(divisor < 0 ? "subu $t2, $zero, $t0" : "move $t2, $t0");
            } else {
                // 负数先加 2^k-1 再算术右移，保持有符号除法向零截断。
                emit("sra $t1, $t0, 31");
                mask("$t1", "$t1", Math.abs(divisor) - 1);
                emit("addu $t2, $t0, $t1");
                emit("sra $t2, $t2, " + shift);
                if (op == Instruction.Opcode.SREM) {
                    emit("sll $t2, $t2, " + shift);
                    emit("subu $t2, $t0, $t2");
                } else if (divisor < 0) emit("subu $t2, $zero, $t2");
            }
        } else return false;
        save(instruction, "$t2");
        return true;
    }

    // 生成按位掩码操作，处理超出无符号十六位的掩码
    private void mask(String target, String source, int bits) {
        if (bits >= 0 && bits <= 65535) emit("andi " + target + ", " + source + ", " + bits);
        else {
            emit("li $t2, " + bits);
            emit("and " + target + ", " + source + ", $t2");
        }
    }
}
