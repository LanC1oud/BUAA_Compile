package mips;

import llvm.IrModule;
import llvm.instruction.Instruction;
import llvm.type.IrType;
import llvm.value.*;
import mips.pass.MipsPass;
import mips.pass.MipsGenerationOptimization;
import static mips.MipsLayout.*;

import java.util.*;

/**
 * 将结构化 LLVM IR 降低为 MIPS32；可选优化委托给 mips.pass。
 * $t0-$t2 计算，$t9 处理大偏移，不占用 $at/$k0/$k1 或 $s 寄存器。
 * 每个函数一次分配固定栈帧，包含调用传参区、局部对象、值槽和 $ra。
 */
public final class MipsGenerator {
    private final MipsGenerationOptimization optimization;
    private final List<MipsPass> passes;
    private MipsModule output;
    private final Map<Value, String> labels = new IdentityHashMap<>();
    private MipsFrame frame;
    private String epilogue;
    private int nextBranch;

    public MipsGenerator() { this(List.of()); }
    public MipsGenerator(List<MipsPass> passes) { this(false, passes); }
    public MipsGenerator(boolean optimize, List<MipsPass> passes) {
        this.optimization = optimize ? new MipsGenerationOptimization(new Emission()) : null;
        this.passes = List.copyOf(passes);
    }

    public MipsModule generate(IrModule module) {
        output = new MipsModule();
        labels.clear();
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
        frame = optimization == null ? new MipsFrame(function) : optimization.beginFunction(function);
        epilogue = label(function) + "_return";
        output.text(label(function) + ": # " + function.getName() + ", frame=" + frame.size);
        addOffset("$sp", "$sp", -frame.size);
        if (frame.needsReturnAddress) memory("sw", "$ra", frame.returnAddress, "$sp");
        for (int i = 0; i < function.getArguments().size(); i++) {
            Argument argument = function.getArguments().get(i);
            if (optimization != null && optimization.discard(argument)) continue;
            if (i < 4) emit("move $t0, $a" + i);
            else memory("lw", "$t0", Math.addExact(frame.size, Math.multiplyExact(i - 4, 4)), "$sp");
            save(argument, "$t0");
        }
        for (BasicBlock block : function.getBlocks()) {
            if (optimization != null) optimization.beginBlock(block);
            output.text(label(block) + ":");
            List<Instruction> instructions = block.getInstructions();
            for (int position = 0; position < instructions.size(); position++) emitInstruction(instructions.get(position), position);
        }
        if (optimization != null) optimization.endFunction();
        output.text(epilogue + ":");
        if (frame.needsReturnAddress) memory("lw", "$ra", frame.returnAddress, "$sp");
        addOffset("$sp", "$sp", frame.size);
        jump("jr", "$ra");
    }

    private void emitInstruction(Instruction instruction, int position) {
        if (optimization != null && optimization.tryInstruction(instruction, position)) return;
        List<Value> operands = instruction.getOperands();
        switch (instruction.getOpcode()) {
            case ALLOCA -> { /* 地址可以从固定的 $sp 与偏移重新计算，无需额外槽位。 */ }
            case LOAD -> {
                String opcode = sizeOf(instruction.getType()) == 1 ? "lbu" : "lw";
                load(operands.get(0), "$t0");
                emit(opcode + " $t1, 0($t0)");
                save(instruction, "$t1");
            }
            case STORE -> {
                load(operands.get(0), "$t0");
                String opcode = sizeOf(operands.get(0).getType()) == 1 ? "sb" : "sw";
                load(operands.get(1), "$t1");
                emit(opcode + " $t0, 0($t1)");
            }
            case ADD, SUB, MUL, SDIV, SREM, UDIV, UREM -> arithmetic(instruction);
            case ICMP -> compare(instruction);
            case ZEXT, TRUNC -> {
                load(operands.get(0), "$t0");
                save(instruction, "$t0");
            }
            case GEP -> gep(instruction);
            case CALL -> call(instruction);
            case BR -> {
                if (operands.size() == 1) {
                    if (optimization != null) optimization.spillLiveOut();
                    jump("j", label(operands.get(0)));
                }
                else {
                    load(operands.get(0), "$t0");
                    if (optimization != null) optimization.spillLiveOut();
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

    private void gep(Instruction instruction) {
        List<Value> operands = instruction.getOperands();
        load(operands.get(0), "$t0");
        IrType indexed = operands.get(0).getType().element();
        for (int i = 1; i < operands.size(); i++) {
            if (i > 1) indexed = indexed.element();
            int stride = sizeOf(indexed);
            load(operands.get(i), "$t1");
            if (stride == 4) emit("sll $t1, $t1, 2");
            else if (stride != 1) {
                emit("li $t2, " + stride);
                emit("multu $t1, $t2");
                emit("mflo $t1");
            }
            emit("addu $t0, $t0, $t1");
        }
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
        if (optimization != null) optimization.beforeCall();
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
        if (optimization != null && optimization.tryLoad(value, register)) return;
        if (value instanceof Constant c) emit("li " + register + ", " + c.getNumber());
        else if (value instanceof GlobalVariable) emit("la " + register + ", " + label(value));
        else if (frame.objects.containsKey(value)) addOffset(register, "$sp", frame.objects.get(value));
        else {
            Integer offset = frame.slots.get(value);
            if (offset == null) throw new IllegalArgumentException("No stack slot for " + value.getName());
            memory("lw", register, offset, "$sp");
        }
        if (optimization != null) optimization.loaded(value, register);
    }

    private void save(Value value, String register) {
        if (optimization != null && optimization.discard(value)) return;
        if (value.getType().bits() == 8) emit("andi " + register + ", " + register + ", 255");
        else if (value.getType().bits() == 1) emit("andi " + register + ", " + register + ", 1");
        if (optimization == null || !optimization.trySave(value, register))
            memory("sw", register, frame.slots.get(value), "$sp");
        if (optimization != null) optimization.loaded(value, register);
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
        if (optimization != null && optimization.redundantOffset(target, base, offset)) return;
        if (shortImmediate(offset)) emit("addiu " + target + ", " + base + ", " + offset);
        else {
            emit("li $t9, " + offset);
            emit("addu " + target + ", " + base + ", $t9");
        }
    }

    private String label(Value value) {
        String label = labels.get(value);
        if (label == null) throw new IllegalArgumentException("Missing assembly label for " + value.getName());
        return label;
    }
    private void emit(String instruction) {
        if (optimization != null) optimization.onEmit(instruction);
        output.text("    " + instruction);
    }
    private void jump(String opcode, String target) { emit(opcode + " " + target); emit("nop"); }

    private final class Emission implements MipsEmission {
        public void emit(String instruction) { MipsGenerator.this.emit(instruction); }
        public void text(String line) { output.text(line); }
        public void load(Value value, String register) { MipsGenerator.this.load(value, register); }
        public void save(Value value, String register) { MipsGenerator.this.save(value, register); }
        public void memory(String opcode, String register, int offset, String base) {
            MipsGenerator.this.memory(opcode, register, offset, base);
        }
        public void addOffset(String target, String base, int offset) { MipsGenerator.this.addOffset(target, base, offset); }
        public void signExtend(String register, IrType type) { MipsGenerator.this.signExtend(register, type); }
        public void jump(String opcode, String target) { MipsGenerator.this.jump(opcode, target); }
        public String label(Value value) { return MipsGenerator.this.label(value); }
        public String nextBranchLabel() { return "__mips_branch" + nextBranch++; }
    }
}
