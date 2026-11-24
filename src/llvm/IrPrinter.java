package llvm;

import llvm.instruction.Instruction;
import llvm.value.*;
import llvm.type.IrType;

import java.util.List;
import java.util.stream.Collectors;

/** 将结构化 IR 输出为使用带类型指针的 LLVM 12 文本。 */
public final class IrPrinter {
    // 依次输出全局对象、外部声明和函数定义
    public String print(IrModule module) {
        StringBuilder out = new StringBuilder("; SysY 2026 / LLVM 12 typed pointers\n\n");
        for (GlobalVariable g : module.getGlobals())
            out.append('@')
                    .append(g.getName())
                    .append(" = ")
                    .append(g.isInternal() ? "internal " : "")
                    .append(g.isReadOnly() ? "constant " : "global ")
                    .append(typed(g.getInitializer()))
                    .append('\n');
        out.append('\n');
        for (Function f : module.getFunctions()) {
            out.append(f.isExternal() ? "declare " : "define ")
                    .append(f.getReturnType())
                    .append(" @")
                    .append(f.getName())
                    .append('(')
                    .append(f.getArguments()
                            .stream()
                            .map(a -> f.isExternal() ? a.getType().toString() : typed(a))
                            .collect(Collectors.joining(", ")))
                    .append(')');
            if (f.isExternal()) {
                out.append('\n');
                continue;
            }
            out.append(" {\n");
            for (BasicBlock block : f.getBlocks()) {
                out.append(block.getName()).append(":\n");
                for (Instruction i : block.getInstructions())
                    out.append("  ").append(instruction(i)).append('\n');
            }
            out.append("}\n\n");
        }
        return out.toString();
    }

    // 格式化常量或名字：全局符号用 @，局部符号用 %
    public String ref(Value value) {
        if (value instanceof Constant c) {
            if (c.getType().kind() != IrType.Kind.ARRAY)
                return Integer.toString(c.getNumber());
            if (c.getElements().stream().allMatch(e -> e.getNumber() == 0))
                return "zeroinitializer";
            return "[" + c.getElements().stream().map(this::typed).collect(Collectors.joining(", ")) + "]";
        }
        return (value instanceof GlobalVariable || value instanceof Function ? "@" : "%") + value.getName();
    }

    // 拼接操作数类型和引用文本
    public String typed(Value value) {
        return value.getType() + " " + ref(value);
    }

    // 按操作码格式化指令，有结果时补上结果名
    private String instruction(Instruction i) {
        List<Value> a = i.getOperands();
        String body = switch (i.getOpcode()) {
            case ALLOCA -> "alloca " + i.getType().element();
            case LOAD -> "load " + i.getType() + ", " + typed(a.get(0));
            case STORE -> "store " + typed(a.get(0)) + ", " + typed(a.get(1));
            case ICMP -> "icmp " + i.getPredicate() + " " + typed(a.get(0)) + ", " + ref(a.get(1));
            case ZEXT, TRUNC -> i.getOpcode().name().toLowerCase() + " " + typed(a.get(0)) + " to " + i.getType();
            case GEP -> "getelementptr " + a.get(0).getType().element() + ", " +
                    a.stream().map(this::typed).collect(Collectors.joining(", "));
            case CALL -> "call " + i.getType() + " " + ref(a.get(0)) + "(" +
                    a.subList(1, a.size()).stream().map(this::typed).collect(Collectors.joining(", ")) + ")";
            case BR -> "br " + a.stream().map(this::typed).collect(Collectors.joining(", "));
            case RET -> "ret " + (a.isEmpty() ? "void" : typed(a.get(0)));
            case UNREACHABLE -> "unreachable";
            default -> i.getOpcode().name().toLowerCase() + " " + typed(a.get(0)) + ", " + ref(a.get(1));
        };
        return (i.getType().equals(IrType.VOID) ? "" : ref(i) + " = ") + body;
    }
}
