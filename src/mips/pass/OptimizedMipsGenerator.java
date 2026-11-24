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
    private MipsGlobalRegisters globals;
    private MipsRanges ranges;
    private BasicBlock currentBlock;
    private int position;

    private String operand(Value value, String scratch) {
        String home = globals.homes.getOrDefault(value, locals.get(value));
        if (home != null) return home;
        Constant c = constants.get(value);
        if (c != null && c.getNumber() == 0) return "$zero";
        load(value, scratch); return scratch;
    }

    private String result(Value value) {
        return globals.homes.containsKey(value) ? globals.homes.get(value) : locals.assign(value, position);
    }

    @Override protected void arithmetic(Instruction i) {
        if (i.getOpcode() != Instruction.Opcode.ADD && i.getOpcode() != Instruction.Opcode.SUB
                && i.getOpcode() != Instruction.Opcode.MUL) { super.arithmetic(i); return; }
        String a = operand(i.getOperands().get(0), "$t0"), b = operand(i.getOperands().get(1), "$t1"), out = result(i);
        switch (i.getOpcode()) {
            case ADD -> emit("addu " + out + ", " + a + ", " + b);
            case SUB -> emit("subu " + out + ", " + a + ", " + b);
            case MUL -> { emit("mult " + a + ", " + b); emit("mflo " + out); }
            default -> throw new IllegalStateException();
        }
        save(i, out);
    }

    @Override protected void normalize(Value value, String register) {
        if (value instanceof Instruction i && (i.getOpcode() == Instruction.Opcode.ICMP
                || i.getOpcode() == Instruction.Opcode.ZEXT
                || i.getOpcode() == Instruction.Opcode.LOAD && value.getType().equals(IrType.I8))) return;
        super.normalize(value, register);
    }

    @Override protected void compare(Instruction i) {
        var operands = i.getOperands(); String predicate = i.getPredicate();
        if (predicate.startsWith("s") && operands.get(0).getType().bits() < 32) { super.compare(i); return; }
        String a = operand(operands.get(0), "$t0"), b = operand(operands.get(1), "$t1"), out = result(i);
        String less = predicate.startsWith("u") ? "sltu" : "slt";
        switch (predicate) {
            case "eq", "ne" -> {
                emit("xor " + out + ", " + a + ", " + b);
                emit(predicate.equals("eq") ? "sltiu " + out + ", " + out + ", 1" : "sltu " + out + ", $zero, " + out);
            }
            case "slt", "ult" -> emit(less + " " + out + ", " + a + ", " + b);
            case "sgt", "ugt" -> emit(less + " " + out + ", " + b + ", " + a);
            case "sle", "ule" -> { emit(less + " " + out + ", " + b + ", " + a); emit("xori " + out + ", " + out + ", 1"); }
            case "sge", "uge" -> { emit(less + " " + out + ", " + a + ", " + b); emit("xori " + out + ", " + out + ", 1"); }
            default -> throw new IllegalArgumentException("Unknown predicate");
        }
        save(i, out);
    }

    // 创建使用指定后处理 Pass 的优化生成器
    public OptimizedMipsGenerator(List<MipsPass> passes) {
        this.passes = List.copyOf(passes);
    }

    // 生成优化汇编，再依次执行目标代码 Pass
    @Override
    public MipsModule generate(IrModule module) {
        MipsModule output = super.generate(module);
        for (MipsPass pass : passes) pass.run(output);
        if (utils.Configure.enablePeephole) new MipsPeephole().run(output);
        if (utils.Configure.enableLoopLayout) {
            new LoopBlockLayout().run(output);
            if (utils.Configure.enablePeephole) new MipsPeephole().run(output);
        }
        // Internal passes operate on explicit empty delay slots. Strip them only at the end.
        if (utils.Configure.enableDelaySlotRemoval)
            output.replaceText(output.getText().stream().filter(line -> !line.trim().equals("nop")).toList());
        return output;
    }

    // 初始化函数分析、优化栈帧和块内寄存器管理
    @Override
    protected MipsFrame createFrame(Function function) {
        constants = new MipsConstants(function);
        plan = new MipsFunctionPlan(function, constants);
        globals = new MipsGlobalRegisters(function, plan);
        ranges = new MipsRanges(function);
        MipsFrame layout = MipsFrameOptimization.allocate(function, plan, globals);
        locals = new MipsLocalRegisters(plan,
                (value, register) -> memory("sw", register, layout.slots.get(value), "$sp"));
        currentBlock = null;
        registers.clear();
        return layout;
    }

    @Override protected void saveCalleeRegisters() {
        for (var entry : globals.savedOffsets.entrySet()) memory("sw", entry.getKey(), entry.getValue(), "$sp");
    }

    @Override protected void restoreCalleeRegisters() {
        for (var entry : globals.savedOffsets.entrySet()) memory("lw", entry.getKey(), entry.getValue(), "$sp");
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
        String resident = globals.homes.getOrDefault(value, locals.get(value));
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
        else if (value instanceof Instruction i && plan.singleGlobalAddress(i)) {
            var a = i.getOperands(); emit("la " + register + ", " + label(a.get(0)));
            IrType type = a.get(0).getType().element(); int offset = 0;
            for (int n = 1; n < a.size(); n++) {
                if (n > 1) type = type.element();
                offset += constants.get(a.get(n)).getNumber() * sizeOf(type);
            }
            if (offset != 0) addOffset(register, register, offset);
        }
        else super.load(value, register);
        registers.put(register, key);
    }

    // 省去无用结果写入，块内结果优先保存在寄存器
    @Override
    protected void save(Value value, String register) {
        if (value.getUses().isEmpty()) return;
        normalize(value, register);
        String global = globals.homes.get(value);
        if (global != null) {
            if (!global.equals(register)) emit("move " + global + ", " + register);
        } else if (currentBlock != null) {
            String home = locals.get(value);
            if (home == null) home = locals.assign(value, position);
            if (!home.equals(register)) emit("move " + home + ", " + register);
        } else memory("sw", register, frame.slots.get(value), "$sp");
        registers.put(register, value);
    }

    private Object location(Value value) {
        String register = globals.homes.getOrDefault(value, locals.get(value));
        return register != null ? register : frame.slots.containsKey(value) ? frame.slots.get(value) : value;
    }

    // Schedule simultaneous PHI assignments. A cycle uses one reserved scratch register.
    @Override protected void emitEdgeCopies(BasicBlock target) {
        record Copy(Instruction destination, Value source, Object sourceLocation) { }
        Map<Object, Copy> copies = new LinkedHashMap<>();
        registers.clear();
        for (Instruction phi : target.getInstructions()) {
            if (phi.getOpcode() != Instruction.Opcode.PHI) break;
            if (phi.getUses().isEmpty()) continue;
            Object destination = location(phi); Value source = incoming(phi);
            Object from = location(source);
            if (!destination.equals(from)) copies.put(destination, new Copy(phi, source, from));
        }
        while (!copies.isEmpty()) {
            Object ready = null;
            for (Object destination : copies.keySet())
                if (copies.values().stream().noneMatch(c -> c.sourceLocation().equals(destination))) { ready = destination; break; }
            if (ready == null) {
                Copy first = copies.values().iterator().next();
                load(first.source(), "$t2");
                Object source = first.sourceLocation();
                for (var entry : List.copyOf(copies.entrySet())) if (entry.getValue().sourceLocation().equals(source)) {
                    Copy old = entry.getValue(); copies.put(entry.getKey(), new Copy(old.destination(), old.source(), "$t2"));
                }
            } else {
                Copy copy = copies.remove(ready);
                String source;
                if (copy.sourceLocation() instanceof String resident) source = resident;
                else { load(copy.source(), "$t0"); source = "$t0"; }
                if (ready instanceof String register) {
                    if (!register.equals(source)) emit("move " + register + ", " + source);
                } else memory("sw", source, (Integer)ready, "$sp");
            }
        }
        registers.clear();
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
                String address = offset != null ? "$sp" : operand(operands.get(0), "$t0");
                String destination = result(instruction);
                memory(sizeOf(instruction.getType()) == 1 ? "lbu" : "lw", destination, offset != null ? offset : 0, address);
                save(instruction, destination);
                return true;
            }
            case STORE -> {
                Integer offset = stackAddress(operands.get(1));
                String value = operand(operands.get(0), "$t0");
                String address = offset != null ? "$sp" : operand(operands.get(1), "$t1");
                memory(sizeOf(operands.get(0).getType()) == 1 ? "sb" : "sw", value, offset != null ? offset : 0, address);
                return true;
            }
            case GEP -> {
                if (stackAddress(instruction) == null && !plan.singleGlobalAddress(instruction)) optimizedGep(instruction);
                return true;
            }
            case ADD, SUB, MUL, SDIV, SREM, UDIV, UREM -> { return selectArithmetic(instruction); }
            case BR -> {
                if (operands.size() == 1) return false;
                Constant condition = constants.get(operands.get(0));
                if (condition != null) {
                    locals.spillLiveOut();
                    jumpTo(operands.get(condition.getNumber() != 0 ? 1 : 2));
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
        String predicate = comparison.getPredicate();
        boolean narrowSigned = predicate.startsWith("s") && operands.get(0).getType().bits() < 32;
        String a = narrowSigned ? "$t0" : operand(operands.get(0), "$t0");
        String b = narrowSigned ? "$t1" : operand(operands.get(1), "$t1");
        if (narrowSigned) {
            load(operands.get(0), "$t0"); load(operands.get(1), "$t1");
            signExtend("$t0", operands.get(0).getType());
            signExtend("$t1", operands.get(1).getType());
        }
        String nearby = nextBranchLabel();
        String branch;
        if (predicate.equals("eq") || predicate.equals("ne")) {
            branch = predicate.equals("eq") ? "bne" : "beq";
            locals.spillLiveOut();
            jump(branch, a + ", " + b + ", " + nearby);
        } else {
            String less = predicate.startsWith("u") ? "sltu" : "slt";
            boolean reversed = predicate.endsWith("gt") || predicate.endsWith("le");
            emit(less + " $t2, " + (reversed ? b + ", " + a : a + ", " + b));
            branch = predicate.endsWith("le") || predicate.endsWith("ge") ? "bne" : "beq";
            locals.spillLiveOut();
            jump(branch, "$t2, $zero, " + nearby);
        }
        // Keep the short branch in range regardless of the number of PHI copies.
        String trueEdge = nextBranchLabel();
        jump("j", trueEdge);
        text(nearby + ":");
        jumpTo(no);
        text(trueEdge + ":");
        jumpTo(yes);
    }

    // 合并常量下标偏移，生成其余下标的地址计算
    private void optimizedGep(Instruction instruction) {
        List<Value> operands = instruction.getOperands();
        String base = operand(operands.get(0), "$t0");
        IrType indexed = operands.get(0).getType().element();
        int constantOffset = 0;
        String scaled = null;
        for (int i = 1; i < operands.size(); i++) {
            if (i > 1) indexed = indexed.element();
            int stride = sizeOf(indexed);
            Constant index = constants.get(operands.get(i));
            if (index != null) {
                constantOffset += index.getNumber() * stride;
                continue;
            }
            String indexRegister = operand(operands.get(i), "$t1");
            if (stride == 4) { emit("sll $t2, " + indexRegister + ", 2"); scaled = "$t2"; }
            else if (stride != 1) {
                emit("li $t2, " + stride); emit("multu " + indexRegister + ", $t2"); emit("mflo $t2"); scaled = "$t2";
            } else scaled = indexRegister;
        }
        String destination = result(instruction);
        if (scaled != null) { emit("addu " + destination + ", " + base + ", " + scaled); base = destination; }
        if (constantOffset != 0 || !destination.equals(base)) addOffset(destination, base, constantOffset);
        save(instruction, destination);
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
                || (op == Instruction.Opcode.SUB && number != Integer.MIN_VALUE && shortImmediate(-number))) {
            String source = operand(left, "$t0"), destination = result(instruction);
            emit("addiu " + destination + ", " + source + ", " + (op == Instruction.Opcode.SUB ? -number : number));
            save(instruction, destination);
            return true;
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
            if (divisor == 0) return false;
            if (divisor == Integer.MIN_VALUE || Integer.bitCount(Math.abs(divisor)) != 1)
                return utils.Configure.enableConstantDivision && reciprocalDivision(instruction, left, divisor, false);
            load(left, "$t0");
            signExtend("$t0", instruction.getType());
            int shift = Integer.numberOfTrailingZeros(Math.abs(divisor));
            if (shift == 0) {
                if (op == Instruction.Opcode.SREM) emit("li $t2, 0");
                else emit(divisor < 0 ? "subu $t2, $zero, $t0" : "move $t2, $t0");
            } else if (ranges.nonNegative(left)) {
                if (op == Instruction.Opcode.SREM) mask("$t2", "$t0", Math.abs(divisor) - 1);
                else {
                    emit("srl $t2, $t0, " + shift);
                    if (divisor < 0) emit("subu $t2, $zero, $t2");
                }
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
        } else if ((op == Instruction.Opcode.UDIV || op == Instruction.Opcode.UREM) && number != 0
                && utils.Configure.enableConstantDivision) return reciprocalDivision(instruction, left, number, true);
        else return false;
        save(instruction, "$t2");
        return true;
    }

    private boolean reciprocalDivision(Instruction instruction, Value numerator, int divisor, boolean unsigned) {
        load(numerator, "$t0");
        boolean remainder = instruction.getOpcode() == Instruction.Opcode.SREM || instruction.getOpcode() == Instruction.Opcode.UREM;
        if (!unsigned) {
            signExtend("$t0", instruction.getType());
            if (divisor == Integer.MIN_VALUE) {
                emit("li $t1, " + Integer.MIN_VALUE);
                emit("xor $t2, $t0, $t1"); emit("sltiu $t2, $t2, 1");
            } else {
                DivisionMagic.Signed magic = DivisionMagic.signed(divisor);
                emit("li $t1, " + magic.multiplier()); emit("mult $t0, $t1"); emit("mfhi $t2");
                if (divisor > 0 && magic.multiplier() < 0) emit("addu $t2, $t2, $t0");
                if (divisor < 0 && magic.multiplier() > 0) emit("subu $t2, $t2, $t0");
                if (magic.shift() > 0) emit("sra $t2, $t2, " + magic.shift());
                emit("srl $t1, $t2, 31"); emit("addu $t2, $t2, $t1");
            }
        } else if (instruction.getType().bits() == 8) {
            // n<=255: ceil(2^16/d) has insufficient error to cross any quotient boundary.
            int reciprocal = (65536 + divisor - 1) / divisor;
            emit("li $t1, " + reciprocal); emit("multu $t0, $t1"); emit("mflo $t2"); emit("srl $t2, $t2, 16");
        } else {
            DivisionMagic.Unsigned magic = DivisionMagic.unsigned(divisor);
            emit("li $t1, " + magic.multiplier()); emit("multu $t0, $t1"); emit("mfhi $t2");
            if (magic.add()) { emit("subu $t1, $t0, $t2"); emit("srl $t1, $t1, 1"); emit("addu $t2, $t2, $t1"); }
            if (magic.shift() > 0) emit("srl $t2, $t2, " + magic.shift());
        }
        if (remainder) {
            emit("li $t1, " + divisor); emit("mult $t2, $t1"); emit("mflo $t1"); emit("subu $t2, $t0, $t1");
        }
        save(instruction, "$t2"); return true;
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
