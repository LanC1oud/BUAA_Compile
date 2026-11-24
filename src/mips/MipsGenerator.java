package mips;

import llvm.IrModule;
import llvm.instruction.Instruction;
import llvm.type.IrType;
import llvm.value.*;
import static mips.MipsLayout.*;

import java.util.*;

// 将结构化 LLVM IR 按基础规则转换为 MIPS
public class MipsGenerator {
    private MipsModule output;
    private final Map<Value, String> labels = new IdentityHashMap<>();
    protected MipsFrame frame;
    protected BasicBlock emittingBlock;
    private String epilogue;
    private int nextBranch;

    // 创建基础 MIPS 生成器
    public MipsGenerator() { }

    // 入口：分配标签，生成全局数据、启动入口与函数
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
        text("main:");
        emit("li $t0, -8");
        emit("and $sp, $sp, $t0");
        jump("jal", label(main));
        emit("li $v0, 10");
        emit("syscall");
        for (Function function : module.getFunctions())
            if (!function.isExternal()) emitFunction(function);
        return output;
    }

    // 按类型将全局初始化常量输出到数据段
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
                    values.add(Integer.toString(sizeOf(constant.getType().element()) == 1 ? (byte) number : number));
                }
                output.data("    " + directive + String.join(", ", values));
            }
        } else {
            for (Constant element : constant.getElements()) emitConstant(element);
        }
    }

    // 递归判断标量或数组常量是否全为零
    private boolean isZero(Constant c) {
        return c.getType().kind() == IrType.Kind.ARRAY
                ? c.getElements().stream().allMatch(this::isZero) : c.getNumber() == 0;
    }

    // 分配栈帧，保存形参并生成函数体与共同出口
    protected void emitFunction(Function function) {
        frame = createFrame(function);
        epilogue = label(function) + "_return";
        text(label(function) + ": # " + function.getName() + ", frame=" + frame.size);
        addOffset("$sp", "$sp", -frame.size);
        if (frame.needsReturnAddress)
            memory("sw", "$ra", frame.returnAddress, "$sp");
        saveCalleeRegisters();
        for (int i = 0; i < function.getArguments().size(); i++) {
            emitArgument(function.getArguments().get(i), i);
        }
        for (BasicBlock block : function.getBlocks()) emitBlock(block);
        text(epilogue + ":");
        restoreCalleeRegisters();
        if (frame.needsReturnAddress)
            memory("lw", "$ra", frame.returnAddress, "$sp");
        addOffset("$sp", "$sp", frame.size);
        jump("jr", "$ra");
    }

    // 为当前函数构建固定栈帧布局
    protected MipsFrame createFrame(Function function) { return new MipsFrame(function); }

    protected void saveCalleeRegisters() { }
    protected void restoreCalleeRegisters() { }

    // 取得寄存器或调用方栈中的形参，并保存到当前函数的值槽
    protected void emitArgument(Argument argument, int index) {
        if (index < 4) emit("move $t0, $a" + index);
        else memory("lw", "$t0", Math.addExact(frame.size, Math.multiplyExact(index - 4, 4)), "$sp");
        save(argument, "$t0");
    }

    // 输出基本块标签，并依次生成块内指令
    protected void emitBlock(BasicBlock block) {
        emittingBlock = block;
        text(label(block) + ":");
        List<Instruction> instructions = block.getInstructions();
        for (int position = 0; position < instructions.size(); position++)
            emitInstruction(instructions.get(position), position);
    }

    // 根据分支操作数生成无条件或条件跳转
    protected void emitBranch(Instruction instruction) {
        List<Value> operands = instruction.getOperands();
        if (operands.size() == 1) jumpTo(operands.get(0));
        else {
            load(operands.get(0), "$t0");
            emitConditionalBranch(operands.get(1), operands.get(2));
        }
    }

    // 根据 $t0 的真假选择出口，条件分支仅跨越一个绝对跳转
    protected void emitConditionalBranch(Value yes, Value no) {
        String nearby = nextBranchLabel();
        jump("beq", "$t0, $zero, " + nearby);
        if (((BasicBlock)yes).getInstructions().get(0).getOpcode() != Instruction.Opcode.PHI) {
            jumpTo(yes);
            text(nearby + ":");
            jumpTo(no);
            return;
        }
        // Dispatch before copies: every conditional branch remains near even with many PHIs.
        String trueEdge = nextBranchLabel();
        jump("j", trueEdge);
        text(nearby + ":");
        jumpTo(no);
        text(trueEdge + ":");
        jumpTo(yes);
    }

    protected void jumpTo(Value target) {
        emitEdgeCopies((BasicBlock) target);
        jump("j", label(target));
    }

    protected Value incoming(Instruction phi) {
        var operands = phi.getOperands();
        for (int n = 0; n < operands.size(); n += 2)
            if (operands.get(n + 1) == emittingBlock) return operands.get(n);
        throw new IllegalStateException("Missing PHI edge");
    }

    // Snapshot all inputs before committing any output, including cyclic loop PHIs.
    protected void emitEdgeCopies(BasicBlock target) {
        List<Instruction> phis = target.getInstructions().stream()
                .filter(i -> i.getOpcode() == Instruction.Opcode.PHI).toList();
        for (int n = 0; n < phis.size(); n++) {
            load(incoming(phis.get(n)), "$t0");
            memory("sw", "$t0", frame.phiScratch + n * 4, "$sp");
        }
        for (int n = 0; n < phis.size(); n++) {
            memory("lw", "$t0", frame.phiScratch + n * 4, "$sp");
            memory("sw", "$t0", frame.slots.get(phis.get(n)), "$sp");
        }
    }

    // 按 IR 操作码生成对应的 MIPS 指令
    protected void emitInstruction(Instruction instruction, int position) {
        List<Value> operands = instruction.getOperands();
        switch (instruction.getOpcode()) {
            case ALLOCA, PHI -> { /* PHIs are simultaneous copies on incoming edges. */ }
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
            case BR -> emitBranch(instruction);
            case RET -> {
                if (!operands.isEmpty()) load(operands.get(0), "$v0");
                jump("j", epilogue);
            }
            case UNREACHABLE -> emit("break 0");
        }
    }

    // 生成整数算术指令并保存计算结果
    protected void arithmetic(Instruction instruction) {
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

    // 按比较谓词生成值为零或一的结果
    protected void compare(Instruction instruction) {
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

    // 按各层元素大小计算下标对应的地址
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

    // 按调用约定放置实参，调用函数或内建系统服务
    private void call(Instruction instruction) {
        List<Value> operands = instruction.getOperands();
        for (int i = 1; i < operands.size(); i++) {
            if (i <= 4) load(operands.get(i), "$a" + (i - 1));
            else {
                load(operands.get(i), "$t0");
                memory("sw", "$t0", Math.multiplyExact(i - 5, 4), "$sp");
            }
        }
        invokeCall(instruction);
    }

    // 执行函数或内建系统调用，并保存非 void 返回值
    protected void invokeCall(Instruction instruction) {
        Function callee = (Function) instruction.getOperands().get(0);
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

    // 将 IR 值或对象地址装入寄存器，必要时从值槽读取
    protected void load(Value value, String register) {
        if (value instanceof Constant c)
            emit("li " + register + ", " + c.getNumber());
        else if (value instanceof GlobalVariable)
            emit("la " + register + ", " + label(value));
        else if (frame.objects.containsKey(value))
            addOffset(register, "$sp", frame.objects.get(value));
        else {
            Integer offset = frame.slots.get(value);
            if (offset == null)
                throw new IllegalArgumentException("No stack slot for " + value.getName());
            memory("lw", register, offset, "$sp");
        }
    }

    // 按整数位宽截断结果，再保存到寄存器或栈槽
    protected void save(Value value, String register) {
        normalize(value, register);
        memory("sw", register, frame.slots.get(value), "$sp");
    }

    // 按 IR 结果位宽规范化寄存器中的整数
    protected void normalize(Value value, String register) {
        if (value.getType().bits() == 8)
            emit("andi " + register + ", " + register + ", 255");
        else if (value.getType().bits() == 1)
            emit("andi " + register + ", " + register + ", 1");
    }

    // 按 IR 位宽将窄整数符号扩展到三十二位
    protected void signExtend(String register, IrType type) {
        if (type.bits() > 0 && type.bits() < 32) {
            int shift = 32 - type.bits();
            emit("sll " + register + ", " + register + ", " + shift);
            emit("sra " + register + ", " + register + ", " + shift);
        }
    }

    // 生成访存指令，大偏移借助临时寄存器合成地址
    protected void memory(String opcode, String register, int offset, String base) {
        if (shortImmediate(offset))
            emit(opcode + " " + register + ", " + offset + "(" + base + ")");
        else {
            addOffset("$t9", base, offset);
            emit(opcode + " " + register + ", 0($t9)");
        }
    }

    // 计算基地址加偏移，处理超出十六位范围的立即数
    protected void addOffset(String target, String base, int offset) {
        if (shortImmediate(offset))
            emit("addiu " + target + ", " + base + ", " + offset);
        else {
            emit("li $t9, " + offset);
            emit("addu " + target + ", " + base + ", $t9");
        }
    }

    // 查询 IR 对象的汇编标签，缺失时报告错误
    protected String label(Value value) {
        String label = labels.get(value);
        if (label == null)
            throw new IllegalArgumentException("Missing assembly label for " + value.getName());
        return label;
    }

    // 向代码段输出一条缩进的汇编指令
    protected void emit(String instruction) {
        text("    " + instruction);
    }

    // 输出跳转指令并补充延迟槽空指令
    protected void jump(String opcode, String target) {
        emit(opcode + " " + target);
        emit("nop");
    }

    // 向当前模块追加标签等不缩进的代码段文本
    protected void text(String line) {
        output.text(line);
    }

    // 分配一个不重复的辅助分支标签
    protected String nextBranchLabel() {
        return "__mips_branch" + nextBranch++;
    }

}
