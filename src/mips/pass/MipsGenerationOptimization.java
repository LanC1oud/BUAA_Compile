package mips.pass;

import llvm.instruction.Instruction;
import llvm.type.IrType;
import llvm.value.*;
import mips.MipsEmission;
import mips.MipsFrame;
import java.util.*;
import static mips.MipsLayout.*;

/**
 * 生成期间的优化入口。不修改 IR；通过 MipsEmission 复用基础发射操作。
 * 这些优化依赖 IR 的类型和使用关系，因此在降低过程中调用，而非事后解析汇编。
 * 生成完成后的文本优化仍使用 MipsPass。
 */
public final class MipsGenerationOptimization {
    private static final Set<String> REGISTER_WRITERS = Set.of("li", "la", "move", "addiu", "addu", "subu",
            "and", "andi", "xor", "xori", "sll", "srl", "sra", "slt", "sltu", "sltiu", "mflo", "mfhi", "lw", "lbu");
    private final MipsEmission emission;
    // 仅缓存不可变 IR 值；基本块和调用边界使缓存失效。
    private final Map<String, Value> registers = new LinkedHashMap<>();
    private MipsConstants constants;
    private MipsFunctionPlan plan;
    private MipsLocalRegisters locals;
    private MipsFrame frame;
    private BasicBlock currentBlock;
    private int position;

    public MipsGenerationOptimization(MipsEmission emission) { this.emission = emission; }

    public MipsFrame beginFunction(Function function) {
        constants = new MipsConstants(function);
        plan = new MipsFunctionPlan(function, constants);
        frame = MipsFrameOptimization.allocate(function, plan);
        locals = new MipsLocalRegisters(plan,
                (value, register) -> emission.memory("sw", register, frame.slots.get(value), "$sp"));
        currentBlock = null;
        registers.clear();
        return frame;
    }

    public void beginBlock(BasicBlock block) {
        registers.clear();
        currentBlock = block;
        locals.begin(block);
    }

    public void endFunction() { currentBlock = null; }
    public void beforeCall() { locals.beforeCall(position); }
    public void spillLiveOut() { locals.spillLiveOut(); }
    public boolean discard(Value value) { return value.getUses().isEmpty(); }

    /** 返回 true 表示已生成替代代码，或该指令可重算而无需发射。 */
    public boolean tryInstruction(Instruction instruction, int position) {
        this.position = position;
        if (constants.get(instruction) != null || plan.isFused(instruction)) return true;
        var operands = instruction.getOperands();
        switch (instruction.getOpcode()) {
            case LOAD -> {
                Integer offset = stackAddress(operands.get(0));
                if (offset == null) return false;
                emission.memory(sizeOf(instruction.getType()) == 1 ? "lbu" : "lw", "$t1", offset, "$sp");
                emission.save(instruction, "$t1");
                return true;
            }
            case STORE -> {
                Integer offset = stackAddress(operands.get(1));
                if (offset == null) return false;
                emission.load(operands.get(0), "$t0");
                emission.memory(sizeOf(operands.get(0).getType()) == 1 ? "sb" : "sw", "$t0", offset, "$sp");
                return true;
            }
            case GEP -> {
                if (stackAddress(instruction) == null) gep(instruction);
                return true;
            }
            case ADD, SUB, MUL, SDIV, SREM, UDIV, UREM -> { return selectArithmetic(instruction); }
            case BR -> {
                if (operands.size() == 1) return false;
                Constant condition = constants.get(operands.get(0));
                if (condition != null) {
                    locals.spillLiveOut();
                    emission.jump("j", emission.label(operands.get(condition.getNumber() != 0 ? 1 : 2)));
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

    /** 命中常量、寄存器或可重算栈地址时接管读取，否则回退到基础加载。 */
    public boolean tryLoad(Value value, String register) {
        Constant known = constants.get(value);
        Value key = known != null ? known : value;
        if (sameValue(registers.get(register), key)) return true;
        String resident = locals.get(value);
        if (resident != null) {
            if (!resident.equals(register)) emission.emit("move " + register + ", " + resident);
            registers.put(register, key);
            return true;
        }
        String cached = null;
        for (var entry : registers.entrySet())
            if (sameValue(entry.getValue(), key)) {
                cached = entry.getKey();
                if (cached.equals(register)) break;
            }
        if (cached != null) {
            if (!cached.equals(register)) emission.emit("move " + register + ", " + cached);
            registers.put(register, key);
            return true;
        }
        Integer address = stackAddress(value);
        if (key instanceof Constant c) emission.emit("li " + register + ", " + c.getNumber());
        else if (address != null) emission.addOffset(register, "$sp", address);
        else return false;
        registers.put(register, key);
        return true;
    }

    public void loaded(Value value, String register) { registers.put(register, value); }

    /** 值已按位宽规范化；块内优先保存在寄存器，形参仍写入栈槽。 */
    public boolean trySave(Value value, String register) {
        if (currentBlock == null) return false;
        String home = locals.assign(value, position);
        if (!home.equals(register)) emission.emit("move " + home + ", " + register);
        return true;
    }

    public boolean redundantOffset(String target, String base, int offset) {
        return offset == 0 && target.equals(base);
    }

    public void onEmit(String instruction) {
        int space = instruction.indexOf(' ');
        String opcode = space < 0 ? instruction : instruction.substring(0, space);
        if (opcode.equals("jal") || opcode.equals("syscall")) registers.clear();
        else if (REGISTER_WRITERS.contains(opcode)) {
            int comma = instruction.indexOf(',', space + 1);
            String destination = instruction.substring(space + 1, comma < 0 ? instruction.length() : comma);
            registers.remove(destination);
        }
    }

    /** 只融合紧邻分支且没有其他使用者的比较，保持远跳转与延迟槽行为。 */
    private void branchComparison(Instruction comparison, Value yes, Value no) {
        var operands = comparison.getOperands();
        emission.load(operands.get(0), "$t0");
        emission.load(operands.get(1), "$t1");
        String predicate = comparison.getPredicate();
        if (predicate.startsWith("s")) {
            emission.signExtend("$t0", operands.get(0).getType());
            emission.signExtend("$t1", operands.get(1).getType());
        }
        String nearby = emission.nextBranchLabel();
        String branch;
        if (predicate.equals("eq") || predicate.equals("ne")) {
            branch = predicate.equals("eq") ? "bne" : "beq";
            locals.spillLiveOut();
            emission.jump(branch, "$t0, $t1, " + nearby);
        } else {
            String less = predicate.startsWith("u") ? "sltu" : "slt";
            boolean reversed = predicate.endsWith("gt") || predicate.endsWith("le");
            emission.emit(less + " $t2, " + (reversed ? "$t1, $t0" : "$t0, $t1"));
            branch = predicate.endsWith("le") || predicate.endsWith("ge") ? "bne" : "beq";
            locals.spillLiveOut();
            emission.jump(branch, "$t2, $zero, " + nearby);
        }
        emission.jump("j", emission.label(yes));
        emission.text(nearby + ":");
        emission.jump("j", emission.label(no));
    }

    private void gep(Instruction instruction) {
        List<Value> operands = instruction.getOperands();
        emission.load(operands.get(0), "$t0");
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
            emission.load(operands.get(i), "$t1");
            if (stride == 4) emission.emit("sll $t1, $t1, 2");
            else if (stride != 1) {
                emission.emit("li $t2, " + stride);
                emission.emit("multu $t1, $t2");
                emission.emit("mflo $t1");
            }
            emission.emit("addu $t0, $t0, $t1");
        }
        if (constantOffset != 0) emission.addOffset("$t0", "$t0", constantOffset);
        emission.save(instruction, "$t0");
    }

    private static boolean sameValue(Value left, Value right) {
        return left == right || left instanceof Constant a && right instanceof Constant b
                && a.getType().equals(b.getType()) && a.getNumber() == b.getNumber();
    }

    /** 只折叠固定局部对象及全常量 GEP，不猜测数组形参的别名。 */
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
            emission.load(left, "$t0");
            emission.emit("addiu $t2, $t0, " + (op == Instruction.Opcode.SUB ? -number : number));
        } else if (op == Instruction.Opcode.MUL && (number == 0 || number == -1 || Integer.bitCount(number) == 1)) {
            emission.load(left, "$t0");
            if (number == 0) emission.emit("li $t2, 0");
            else if (number == -1) emission.emit("subu $t2, $zero, $t0");
            else emission.emit("sll $t2, $t0, " + Integer.numberOfTrailingZeros(number));
        } else if ((op == Instruction.Opcode.UDIV || op == Instruction.Opcode.UREM) && Integer.bitCount(number) == 1) {
            emission.load(left, "$t0");
            if (op == Instruction.Opcode.UDIV) emission.emit("srl $t2, $t0, " + Integer.numberOfTrailingZeros(number));
            else mask("$t2", "$t0", number - 1);
        } else if (op == Instruction.Opcode.SDIV || op == Instruction.Opcode.SREM) {
            int divisor = MipsConstants.signed(c);
            if (divisor == 0 || divisor == Integer.MIN_VALUE || Integer.bitCount(Math.abs(divisor)) != 1) return false;
            emission.load(left, "$t0");
            emission.signExtend("$t0", instruction.getType());
            int shift = Integer.numberOfTrailingZeros(Math.abs(divisor));
            if (shift == 0) {
                if (op == Instruction.Opcode.SREM) emission.emit("li $t2, 0");
                else emission.emit(divisor < 0 ? "subu $t2, $zero, $t0" : "move $t2, $t0");
            } else {
                // 负数先加 2^k-1 再算术右移，保持有符号除法向零截断。
                emission.emit("sra $t1, $t0, 31");
                mask("$t1", "$t1", Math.abs(divisor) - 1);
                emission.emit("addu $t2, $t0, $t1");
                emission.emit("sra $t2, $t2, " + shift);
                if (op == Instruction.Opcode.SREM) {
                    emission.emit("sll $t2, $t2, " + shift);
                    emission.emit("subu $t2, $t0, $t2");
                } else if (divisor < 0) emission.emit("subu $t2, $zero, $t2");
            }
        } else return false;
        emission.save(instruction, "$t2");
        return true;
    }

    private void mask(String target, String source, int bits) {
        if (bits >= 0 && bits <= 65535) emission.emit("andi " + target + ", " + source + ", " + bits);
        else {
            emission.emit("li $t2, " + bits);
            emission.emit("and " + target + ", " + source + ", $t2");
        }
    }
}
