package mips;

import llvm.instruction.Instruction;
import llvm.value.*;
import java.util.IdentityHashMap;
import java.util.Map;

/** 仅供后端重算常量使用，不替换 IR，也不传播可变内存的内容。 */
final class MipsConstants {
    private final Map<Value, Constant> values = new IdentityHashMap<>();

    MipsConstants(Function function) {
        for (BasicBlock block : function.getBlocks()) {
            for (Instruction instruction : block.getInstructions()) {
                Constant result = evaluate(instruction);
                if (result != null) values.put(instruction, result);
            }
        }
    }

    Constant get(Value value) { return value instanceof Constant c ? c : values.get(value); }

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

    static int signed(Constant value) {
        int shift = 32 - value.getType().bits();
        return value.getNumber() << shift >> shift;
    }
}
