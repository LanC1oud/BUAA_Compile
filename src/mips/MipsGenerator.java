package mips;

import llvm.IrModule;
import llvm.instruction.Instruction;
import llvm.type.IrType;
import llvm.value.*;
import mips.pass.MipsPass;

import java.util.*;

/**
 * 将结构化 LLVM IR 降低为 MIPS32。优化模式在块内使用 $t3-$t8，按需落栈；
 * $t0-$t2 计算，$t9 处理大偏移，不占用 $at/$k0/$k1 或 $s 寄存器。
 * 每个函数一次分配固定栈帧，包含调用传参区、局部对象、值槽和 $ra。
 */
public final class MipsGenerator {
    private static final Set<String> REGISTER_WRITERS = Set.of("li", "la", "move", "addiu", "addu", "subu",
            "and", "andi", "xor", "xori", "sll", "srl", "sra", "slt", "sltu", "sltiu", "mflo", "mfhi", "lw", "lbu");
    private final boolean optimize;
    private final List<MipsPass> passes;
    private MipsModule output;
    private final Map<Value, String> labels = new IdentityHashMap<>();
    private Frame frame;
    private String epilogue;
    private int nextBranch;
    private MipsConstants constants;
    private MipsFunctionPlan plan;
    private MipsLocalRegisters locals;
    private BasicBlock currentBlock;
    private int position;
    // 仅记录不可变 IR 值当前所在的寄存器；跨基本块、调用时全部失效。
    private final Map<String, Value> registers = new LinkedHashMap<>();

    public MipsGenerator() { this(List.of()); }
    public MipsGenerator(List<MipsPass> passes) { this(false, passes); }
    public MipsGenerator(boolean optimize, List<MipsPass> passes) {
        this.optimize = optimize;
        this.passes = List.copyOf(passes);
    }

    public MipsModule generate(IrModule module) {
        output = new MipsModule();
        labels.clear();
        registers.clear();
        nextBranch = 0;
        int globalId = 0, functionId = 0;
        for (GlobalVariable global : module.getGlobals())
            labels.put(global, "__mips_g" + globalId++);
        for (Function function : module.getFunctions()) {
            if (function.isExternal()) continue;
            String label = "__mips_f" + functionId++;
            labels.put(function, label);
            int blockId = 0;
            for (BasicBlock block : function.getBlocks())
                labels.put(block, label + "_b" + blockId++);
        }
        Function main = module.getFunction("main");
        if (main == null || main.isExternal() || !main.getArguments().isEmpty())
            throw new IllegalArgumentException("Expected a defined main() function");
        for (GlobalVariable global : module.getGlobals()) {
            output.data(".align " + (alignment(global.getInitializer().getType()) == 4 ? 2 : 0));
            output.data(label(global) + ": # " + global.getName());
            emitConstant(global.getInitializer());
        }
        output.text("main:");
        // MARS 的初始 $sp 不保证八字节对齐；入口统一对齐后再调用用户 main。
        emit("li $t0, -8");
        emit("and $sp, $sp, $t0");
        jump("jal", label(main));
        emit("li $v0, 10");
        emit("syscall");
        for (Function function : module.getFunctions())
            if (!function.isExternal()) emitFunction(function);
        for (MipsPass pass : passes) pass.run(output);
        return output;
    }

    private void emitConstant(Constant constant) {
        if (constant.getType().kind() != IrType.Kind.ARRAY) {
            boolean byteObject = sizeOf(constant.getType()) == 1;
            int number = byteObject ? (byte) constant.getNumber() : constant.getNumber();
            output.data("    " + (byteObject ? ".byte " : ".word ") + number);
            return;
        }
        // 零初始化数组用 .space，避免大数组输出大量零字面值。
        if (isZero(constant)) {
            output.data("    .space " + sizeOf(constant.getType()));
        } else if (constant.getType().element().isInteger()) {
            List<Constant> elements = constant.getElements();
            String directive = sizeOf(constant.getType().element()) == 1 ? ".byte " : ".word ";
            for (int i = 0; i < elements.size(); i += 16) {
                List<String> values = new ArrayList<>();
                for (int j = i; j < Math.min(i + 16, elements.size()); j++) {
                    int number = elements.get(j).getNumber();
                    // MARS 检查 .byte 的有符号范围；用相同位模式的负数字面值避免警告。
                    values.add(Integer.toString(sizeOf(constant.getType().element()) == 1 ? (byte) number : number));
                }
                output.data("    " + directive + String.join(", ", values));
            }
        } else {
            for (Constant element : constant.getElements()) emitConstant(element);
        }
    }

    private boolean isZero(Constant c) {
        return c.getType().kind() == IrType.Kind.ARRAY
                ? c.getElements().stream().allMatch(this::isZero) : c.getNumber() == 0;
    }

    private void emitFunction(Function function) {
        constants = optimize ? new MipsConstants(function) : null;
        plan = optimize ? new MipsFunctionPlan(function, constants) : null;
        frame = new Frame(function, plan);
        locals = optimize ? new MipsLocalRegisters(plan,
                (value, register) -> memory("sw", register, frame.slots.get(value), "$sp")) : null;
        currentBlock = null;
        registers.clear();
        epilogue = label(function) + "_return";
        output.text(label(function) + ": # " + function.getName() + ", frame=" + frame.size);
        addOffset("$sp", "$sp", -frame.size);
        if (!optimize || frame.needsReturnAddress) memory("sw", "$ra", frame.returnAddress, "$sp");
        for (int i = 0; i < function.getArguments().size(); i++) {
            Argument argument = function.getArguments().get(i);
            if (optimize && argument.getUses().isEmpty()) continue;
            if (i < 4) emit("move $t0, $a" + i);
            else memory("lw", "$t0", Math.addExact(frame.size, Math.multiplyExact(i - 4, 4)), "$sp");
            save(argument, "$t0");
        }
        for (BasicBlock block : function.getBlocks()) {
            registers.clear();
            currentBlock = block;
            if (optimize) locals.begin(block);
            output.text(label(block) + ":");
            List<Instruction> instructions = block.getInstructions();
            for (position = 0; position < instructions.size(); position++) emitInstruction(instructions.get(position));
        }
        currentBlock = null;
        output.text(epilogue + ":");
        if (!optimize || frame.needsReturnAddress) memory("lw", "$ra", frame.returnAddress, "$sp");
        addOffset("$sp", "$sp", frame.size);
        jump("jr", "$ra");
    }

    private void emitInstruction(Instruction instruction) {
        // 常量在每次使用时直接装入；栈上的可变对象从不参与常量传播。
        if (optimize && (constants.get(instruction) != null || plan.isFused(instruction))) return;
        List<Value> operands = instruction.getOperands();
        switch (instruction.getOpcode()) {
            case ALLOCA -> { /* 地址可以从固定的 $sp 与偏移重新计算，无需额外槽位。 */ }
            case LOAD -> {
                Integer offset = optimize ? stackAddress(operands.get(0)) : null;
                String opcode = sizeOf(instruction.getType()) == 1 ? "lbu" : "lw";
                if (offset != null) memory(opcode, "$t1", offset, "$sp");
                else {
                    load(operands.get(0), "$t0");
                    emit(opcode + " $t1, 0($t0)");
                }
                save(instruction, "$t1");
            }
            case STORE -> {
                load(operands.get(0), "$t0");
                Integer offset = optimize ? stackAddress(operands.get(1)) : null;
                String opcode = sizeOf(operands.get(0).getType()) == 1 ? "sb" : "sw";
                if (offset != null) memory(opcode, "$t0", offset, "$sp");
                else {
                    load(operands.get(1), "$t1");
                    emit(opcode + " $t0, 0($t1)");
                }
            }
            case ADD, SUB, MUL, SDIV, SREM, UDIV, UREM -> arithmetic(instruction);
            case ICMP -> compare(instruction);
            case ZEXT, TRUNC -> {
                load(operands.get(0), "$t0");
                save(instruction, "$t0");
            }
            case GEP -> {
                if (!optimize || stackAddress(instruction) == null) gep(instruction);
            }
            case CALL -> call(instruction);
            case BR -> {
                if (operands.size() == 1) {
                    if (optimize) locals.spillLiveOut();
                    jump("j", label(operands.get(0)));
                }
                else {
                    Constant condition = optimize ? constants.get(operands.get(0)) : null;
                    if (condition != null) {
                        locals.spillLiveOut();
                        jump("j", label(operands.get(condition.getNumber() != 0 ? 1 : 2)));
                        break;
                    }
                    MipsFunctionPlan.Comparison comparison = optimize ? plan.comparisonFor(instruction) : null;
                    if (comparison != null) {
                        branchComparison(comparison.instruction(), operands.get(comparison.inverted() ? 2 : 1),
                                operands.get(comparison.inverted() ? 1 : 2));
                        break;
                    }
                    load(operands.get(0), "$t0");
                    if (optimize) locals.spillLiveOut();
                    String nearby = "__mips_branch" + nextBranch++;
                    // 条件分支只跳过一个绝对跳转，避免大函数超出 16 位分支范围。
                    jump("beq", "$t0, $zero, " + nearby);
                    jump("j", label(operands.get(1)));
                    output.text(nearby + ":");
                    jump("j", label(operands.get(2)));
                }
            }
            case RET -> {
                if (!operands.isEmpty()) load(operands.get(0), "$v0");
                jump("j", epilogue);
            }
            case UNREACHABLE -> emit("break 0");
        }
    }

    private void arithmetic(Instruction instruction) {
        if (optimize && selectArithmetic(instruction)) return;
        load(instruction.getOperands().get(0), "$t0");
        load(instruction.getOperands().get(1), "$t1");
        if (instruction.getOpcode() == Instruction.Opcode.SDIV || instruction.getOpcode() == Instruction.Opcode.SREM) {
            signExtend("$t0", instruction.getType());
            signExtend("$t1", instruction.getType());
        }
        switch (instruction.getOpcode()) {
            case ADD -> emit("addu $t2, $t0, $t1");
            case SUB -> emit("subu $t2, $t0, $t1");
            case MUL -> { emit("mult $t0, $t1"); emit("mflo $t2"); }
            case SDIV, SREM, UDIV, UREM -> {
                boolean unsigned = instruction.getOpcode() == Instruction.Opcode.UDIV
                        || instruction.getOpcode() == Instruction.Opcode.UREM;
                emit((unsigned ? "divu" : "div") + " $t0, $t1");
                boolean remainder = instruction.getOpcode() == Instruction.Opcode.SREM
                        || instruction.getOpcode() == Instruction.Opcode.UREM;
                emit((remainder ? "mfhi" : "mflo") + " $t2");
            }
            default -> throw new IllegalArgumentException("Not arithmetic");
        }
        save(instruction, "$t2");
    }

    private void compare(Instruction instruction) {
        var operands = instruction.getOperands();
        load(operands.get(0), "$t0");
        load(operands.get(1), "$t1");
        String predicate = instruction.getPredicate();
        if (predicate.startsWith("s")) {
            signExtend("$t0", operands.get(0).getType());
            signExtend("$t1", operands.get(1).getType());
        }
        String less = predicate.startsWith("u") ? "sltu" : "slt";
        switch (predicate) {
            case "eq", "ne" -> {
                emit("xor $t2, $t0, $t1");
                emit(predicate.equals("eq") ? "sltiu $t2, $t2, 1" : "sltu $t2, $zero, $t2");
            }
            case "slt", "ult" -> emit(less + " $t2, $t0, $t1");
            case "sgt", "ugt" -> emit(less + " $t2, $t1, $t0");
            case "sle", "ule" -> { emit(less + " $t2, $t1, $t0"); emit("xori $t2, $t2, 1"); }
            case "sge", "uge" -> { emit(less + " $t2, $t0, $t1"); emit("xori $t2, $t2, 1"); }
            default -> throw new IllegalArgumentException("Unsupported predicate: " + predicate);
        }
        save(instruction, "$t2");
    }

    /** 只融合紧邻分支且没有其他使用者的比较，保持远跳转与延迟槽行为。 */
    private void branchComparison(Instruction comparison, Value yes, Value no) {
        var operands = comparison.getOperands();
        load(operands.get(0), "$t0");
        load(operands.get(1), "$t1");
        String predicate = comparison.getPredicate();
        if (predicate.startsWith("s")) {
            signExtend("$t0", operands.get(0).getType());
            signExtend("$t1", operands.get(1).getType());
        }
        String nearby = "__mips_branch" + nextBranch++;
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
        output.text(nearby + ":");
        jump("j", label(no));
    }

    private void gep(Instruction instruction) {
        List<Value> operands = instruction.getOperands();
        load(operands.get(0), "$t0");
        IrType indexed = operands.get(0).getType().element();
        int constantOffset = 0;
        for (int i = 1; i < operands.size(); i++) {
            if (i > 1) indexed = indexed.element();
            int stride = sizeOf(indexed);
            Constant index = optimize ? constants.get(operands.get(i)) : null;
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

    private void call(Instruction instruction) {
        List<Value> operands = instruction.getOperands();
        Function callee = (Function) operands.get(0);
        for (int i = 1; i < operands.size(); i++) {
            if (i <= 4) load(operands.get(i), "$a" + (i - 1));
            else {
                load(operands.get(i), "$t0");
                memory("sw", "$t0", Math.multiplyExact(i - 5, 4), "$sp");
            }
        }
        if (optimize) locals.beforeCall(position);
        if (callee.isExternal()) {
            int syscall = switch (callee.getName()) {
                case "get_int" -> 5;
                case "get_char" -> 12;
                case "get_string" -> 8;
                case "put_int" -> 1;
                case "put_char" -> 11;
                case "put_string" -> 4;
                default -> throw new IllegalArgumentException("Unknown external function: " + callee.getName());
            };
            emit("li $v0, " + syscall);
            emit("syscall");
        } else jump("jal", label(callee));
        if (!instruction.getType().equals(IrType.VOID)) save(instruction, "$v0");
    }

    private void load(Value value, String register) {
        Constant known = optimize ? constants.get(value) : null;
        Value key = known != null ? known : value;
        if (optimize) {
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
        }
        Integer address = optimize ? stackAddress(value) : null;
        if (key instanceof Constant c) emit("li " + register + ", " + c.getNumber());
        else if (address != null) addOffset(register, "$sp", address);
        else if (value instanceof GlobalVariable) emit("la " + register + ", " + label(value));
        else if (frame.objects.containsKey(value)) addOffset(register, "$sp", frame.objects.get(value));
        else {
            Integer offset = frame.slots.get(value);
            if (offset == null) throw new IllegalArgumentException("No stack slot for " + value.getName());
            memory("lw", register, offset, "$sp");
        }
        if (optimize) registers.put(register, key);
    }

    private void save(Value value, String register) {
        if (optimize && value.getUses().isEmpty()) return;
        if (value.getType().bits() == 8) emit("andi " + register + ", " + register + ", 255");
        else if (value.getType().bits() == 1) emit("andi " + register + ", " + register + ", 1");
        if (optimize && currentBlock != null) {
            String home = locals.assign(value, position);
            if (!home.equals(register)) emit("move " + home + ", " + register);
        } else memory("sw", register, frame.slots.get(value), "$sp");
        if (optimize) registers.put(register, value);
    }

    private static boolean sameValue(Value left, Value right) {
        return left == right || left instanceof Constant a && right instanceof Constant b
                && a.getType().equals(b.getType()) && a.getNumber() == b.getNumber();
    }

    /** 仅对固定局部对象及全常量 GEP 返回偏移，不猜测数组形参的别名。 */
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

    private void mask(String target, String source, int bits) {
        if (bits >= 0 && bits <= 65535) emit("andi " + target + ", " + source + ", " + bits);
        else {
            emit("li $t2, " + bits);
            emit("and " + target + ", " + source + ", $t2");
        }
    }

    private void signExtend(String register, IrType type) {
        if (type.bits() > 0 && type.bits() < 32) {
            int shift = 32 - type.bits();
            emit("sll " + register + ", " + register + ", " + shift);
            emit("sra " + register + ", " + register + ", " + shift);
        }
    }

    private void memory(String opcode, String register, int offset, String base) {
        if (shortImmediate(offset)) emit(opcode + " " + register + ", " + offset + "(" + base + ")");
        else {
            addOffset("$t9", base, offset);
            emit(opcode + " " + register + ", 0($t9)");
        }
    }

    private void addOffset(String target, String base, int offset) {
        if (optimize && offset == 0 && target.equals(base)) return;
        if (shortImmediate(offset)) emit("addiu " + target + ", " + base + ", " + offset);
        else {
            emit("li $t9, " + offset);
            emit("addu " + target + ", " + base + ", $t9");
        }
    }

    private static boolean shortImmediate(int value) { return value >= -32768 && value <= 32767; }
    private String label(Value value) {
        String label = labels.get(value);
        if (label == null) throw new IllegalArgumentException("Missing assembly label for " + value.getName());
        return label;
    }
    private void emit(String instruction) {
        if (optimize) {
            int space = instruction.indexOf(' ');
            String opcode = space < 0 ? instruction : instruction.substring(0, space);
            if (opcode.equals("jal") || opcode.equals("syscall")) registers.clear();
            else if (REGISTER_WRITERS.contains(opcode)) {
                int comma = instruction.indexOf(',', space + 1);
                String destination = instruction.substring(space + 1, comma < 0 ? instruction.length() : comma);
                registers.remove(destination);
            }
        }
        output.text("    " + instruction);
    }
    private void jump(String opcode, String target) { emit(opcode + " " + target); emit("nop"); }

    static int sizeOf(IrType type) {
        return switch (type.kind()) {
            case I1, I8 -> 1;
            case I32, POINTER -> 4;
            case ARRAY -> Math.multiplyExact(type.length(), sizeOf(type.element()));
            default -> throw new IllegalArgumentException("No MIPS storage size for " + type);
        };
    }
    private static int alignment(IrType type) {
        return type.kind() == IrType.Kind.ARRAY ? alignment(type.element()) : Math.min(4, sizeOf(type));
    }
    private static int align(int value, int alignment) {
        return Math.addExact(value, alignment - 1) / alignment * alignment;
    }

    private static final class Frame {
        final Map<Value, Integer> objects = new IdentityHashMap<>();
        final Map<Value, Integer> slots = new IdentityHashMap<>();
        final int size;
        final int returnAddress;
        final boolean needsReturnAddress;

        Frame(Function function, MipsFunctionPlan plan) {
            int outgoing = 0;
            boolean callsFunction = false;
            for (BasicBlock block : function.getBlocks())
                for (Instruction i : block.getInstructions())
                    if (i.getOpcode() == Instruction.Opcode.CALL) {
                        outgoing = Math.max(outgoing, Math.multiplyExact(Math.max(0, i.getOperands().size() - 5), 4));
                        if (!((Function) i.getOperands().get(0)).isExternal()) callsFunction = true;
                    }
            needsReturnAddress = callsFunction;
            if (plan != null) {
                int cursor = outgoing;
                for (Argument argument : function.getArguments()) {
                    if (argument.getUses().isEmpty()) continue;
                    slots.put(argument, cursor);
                    cursor = Math.addExact(cursor, 4);
                }
                for (BasicBlock block : function.getBlocks()) {
                    for (Instruction i : block.getInstructions()) {
                        if (i.getOpcode() == Instruction.Opcode.ALLOCA && !i.getUses().isEmpty()) {
                            IrType object = i.getType().element();
                            cursor = align(cursor, alignment(object));
                            objects.put(i, cursor);
                            cursor = Math.addExact(cursor, sizeOf(object));
                        }
                    }
                }
                cursor = align(cursor, 4);
                for (BasicBlock block : function.getBlocks()) {
                    for (Instruction i : block.getInstructions()) {
                        if (plan.needsSlot(i) && plan.crossesBlock(i)) {
                            slots.put(i, cursor);
                            cursor = Math.addExact(cursor, 4);
                        }
                    }
                }
                int localBase = cursor, peak = 0;
                for (BasicBlock block : function.getBlocks()) {
                    PriorityQueue<Interval> active = new PriorityQueue<>(Comparator.comparingInt(Interval::end));
                    PriorityQueue<Integer> free = new PriorityQueue<>();
                    int count = 0;
                    List<Instruction> instructions = block.getInstructions();
                    for (int n = 0; n < instructions.size(); n++) {
                        Instruction i = instructions.get(n);
                        if (!plan.needsSlot(i) || plan.crossesBlock(i)) continue;
                        while (!active.isEmpty() && active.peek().end() <= n) free.add(active.remove().slot());
                        int slot = free.isEmpty() ? count++ : free.remove();
                        slots.put(i, Math.addExact(localBase, Math.multiplyExact(slot, 4)));
                        active.add(new Interval(plan.lastUse(i, block), slot));
                    }
                    peak = Math.max(peak, count);
                }
                returnAddress = Math.addExact(localBase, Math.multiplyExact(peak, 4));
                size = align(Math.addExact(returnAddress, callsFunction ? 4 : 0), 8);
                return;
            }
            int cursor = outgoing;
            for (Argument argument : function.getArguments()) {
                slots.put(argument, cursor);
                cursor = Math.addExact(cursor, 4);
            }
            for (BasicBlock block : function.getBlocks()) {
                for (Instruction i : block.getInstructions()) {
                    if (i.getOpcode() == Instruction.Opcode.ALLOCA) {
                        IrType object = i.getType().element();
                        cursor = align(cursor, alignment(object));
                        objects.put(i, cursor);
                        cursor = Math.addExact(cursor, sizeOf(object));
                    } else if (!i.getType().equals(IrType.VOID)) {
                        cursor = align(cursor, 4);
                        slots.put(i, cursor);
                        cursor = Math.addExact(cursor, 4);
                    }
                }
            }
            returnAddress = align(cursor, 4);
            size = align(Math.addExact(returnAddress, 4), 8);
        }

        private record Interval(int end, int slot) { }
    }
}
