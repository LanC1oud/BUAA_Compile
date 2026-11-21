package llvm;

import llvm.instruction.Instruction;
import llvm.value.*;
import llvm.type.IrType;

import java.util.List;
import java.util.stream.Collectors;

// 将结构化 IR 打印为 LLVM 12 的带类型指针文本
public final class IrPrinter {
    // 按全局对象、函数和基本块顺序输出完整模块
    public String print(IrModule module) {
        StringBuilder out = new StringBuilder("; SysY 2026 / LLVM 12 typed pointers\n\n");
        for (GlobalVariable g : module.getGlobals()) {
            String initializerText = typed(g.getInitializer());
            out.append('@')
                    .append(g.getName())
                    .append(" = ")
                    .append(g.isInternal() ? "internal " : "")
                    .append(g.isReadOnly() ? "constant " : "global ")
                    .append(initializerText)
                    .append('\n');
        }
        out.append('\n');
        for (Function f : module.getFunctions()) {
            String parameterText = f.getArguments()
                    .stream()
                    .map(argument -> f.isExternal() ? argument.getType().toString() : typed(argument))
                    .collect(Collectors.joining(", "));
            out.append(f.isExternal() ? "declare " : "define ")
                    .append(f.getReturnType())
                    .append(" @")
                    .append(f.getName())
                    .append('(')
                    .append(parameterText)
                    .append(')');
            if (f.isExternal()) {
                out.append('\n');
                continue;
            }
            out.append(" {\n");
            for (BasicBlock block : f.getBlocks()) {
                out.append(block.getName()).append(":\n");
                for (Instruction i : block.getInstructions()) {
                    String instructionText = instruction(i);
                    out.append("  ").append(instructionText).append('\n');
                }
            }
            out.append("}\n\n");
        }
        return out.toString();
    }

    // 输出值的引用文本：常量内容或带 @、% 前缀的名字
    public String ref(Value value) {
        if (value instanceof Constant c) {
            if (c.getType().kind() != IrType.Kind.ARRAY)
                return Integer.toString(c.getNumber());
            if (c.getElements().stream().allMatch(e -> e.getNumber() == 0))
                return "zeroinitializer";
            String elementText = c.getElements().stream().map(this::typed).collect(Collectors.joining(", "));
            return "[" + elementText + "]";
        }
        return (value instanceof GlobalVariable || value instanceof Function ? "@" : "%") + value.getName();
    }

    // 输出“类型 值引用”形式的操作数
    public String typed(Value value) {
        return value.getType() + " " + ref(value);
    }

    // 将一条指令格式化为 LLVM 文本
    private String instruction(Instruction instruction) {
        List<Value> operands = instruction.getOperands();
        IrType resultType = instruction.getType();
        String body = switch (instruction.getOpcode()) {
            case ALLOCA -> "alloca " + resultType.element();
            case LOAD -> "load " + resultType + ", " + typed(operands.get(0));
            case STORE -> "store " + typed(operands.get(0)) + ", " + typed(operands.get(1));
            case ICMP -> "icmp " + instruction.getPredicate() + " " +
                    typed(operands.get(0)) + ", " + ref(operands.get(1));
            case ZEXT, TRUNC -> {
                String opcode = instruction.getOpcode().name().toLowerCase();
                String operandText = typed(operands.get(0));
                yield opcode + " " + operandText + " to " + resultType;
            }
            case GEP -> {
                Value base = operands.get(0);
                IrType sourceType = base.getType().element();
                String operandText = operands.stream().map(this::typed).collect(Collectors.joining(", "));
                yield "getelementptr " + sourceType + ", " + operandText;
            }
            case CALL -> {
                Value callee = operands.get(0);
                List<Value> arguments = operands.subList(1, operands.size());
                String argumentText = arguments.stream().map(this::typed).collect(Collectors.joining(", "));
                yield "call " + resultType + " " + ref(callee) + "(" + argumentText + ")";
            }
            case BR -> {
                String operandText = operands.stream().map(this::typed).collect(Collectors.joining(", "));
                yield "br " + operandText;
            }
            case RET -> "ret " + (operands.isEmpty() ? "void" : typed(operands.get(0)));
            case UNREACHABLE -> "unreachable";
            default -> {
                String opcode = instruction.getOpcode().name().toLowerCase();
                String left = typed(operands.get(0));
                String right = ref(operands.get(1));
                yield opcode + " " + left + ", " + right;
            }
        };
        String resultPrefix = resultType.equals(IrType.VOID) ? "" : ref(instruction) + " = ";
        return resultPrefix + body;
    }
}
