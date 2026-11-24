package mips.pass;

import llvm.instruction.Instruction;
import llvm.value.*;
import java.util.IdentityHashMap;
import java.util.Map;

// 分析可重算的整数常量，不修改 IR 或传播可变内存
final class MipsConstants {
    private final Map<Value, Constant> values = new IdentityHashMap<>();

    // 扫描函数指令并记录可在编译期求出的结果
    MipsConstants(Function function) {
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                Constant result = evaluate(instruction);
                if (result != null) values.put(instruction, result);
            }
        }
    }

    // 取得字面常量或已求出的常量，未知时返回空值
    Constant get(Value value) { return value instanceof Constant c ? c : values.get(value); }

    // 尝试求出整数指令的常量结果，保留未定义运算
    private Constant evaluate(Instruction instruction) {
        if (!instruction.getType().isInteger()) return null;
        var operands = instruction.getOperands();
        if (operands.isEmpty()) return null;
        Constant left = get(operands.get(0));
        if (left == null) return null;
        if (instruction.getOpcode() == Instruction.Opcode.ZEXT || instruction.getOpcode() == Instruction.Opcode.TRUNC)
            return Constant.integer(instruction.getType(), left.getNumber());
        if (operands.size() != 2) return null;
        Constant right = get(operands.get(1));
        if (right == null) return null;
        int a = left.getNumber(), b = right.getNumber();
        Integer result = switch (instruction.getOpcode()) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;
            case UDIV -> b == 0 ? null : Integer.divideUnsigned(a, b);
            case UREM -> b == 0 ? null : Integer.remainderUnsigned(a, b);
            case SDIV, SREM -> {
                int x = signed(left), y = signed(right);
                int minimum = left.getType().bits() == 32 ? Integer.MIN_VALUE : -(1 << (left.getType().bits() - 1));
                // 保留除零和有符号除法溢出的原始行为，不将未定义结果折成常量。
                if (y == 0 || x == minimum && y == -1) yield null;
                yield instruction.getOpcode() == Instruction.Opcode.SDIV ? x / y : x % y;
            }
            case ICMP -> {
                int s = Integer.compare(signed(left), signed(right));
                int u = Integer.compareUnsigned(a, b);
                boolean test = switch (instruction.getPredicate()) {
                    case "eq" -> a == b;
                    case "ne" -> a != b;
                    case "slt" -> s < 0;
                    case "sle" -> s <= 0;
                    case "sgt" -> s > 0;
                    case "sge" -> s >= 0;
                    case "ult" -> u < 0;
                    case "ule" -> u <= 0;
                    case "ugt" -> u > 0;
                    case "uge" -> u >= 0;
                    default -> throw new IllegalArgumentException("Unsupported predicate: " + instruction.getPredicate());
                };
                yield test ? 1 : 0;
            }
            default -> null;
        };
        return result == null ? null : Constant.integer(instruction.getType(), result);
    }

    // 按常量的整数位宽解释其有符号值
    static int signed(Constant value) {
        int shift = 32 - value.getType().bits();
        return value.getNumber() << shift >> shift;
    }
}
