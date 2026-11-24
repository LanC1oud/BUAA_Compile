package llvm.value;

import llvm.type.IrType;

import java.util.List;

/** 表示整数或数组常量，可用于操作数和初值。 */
public final class Constant extends Value {
    private final int number;
    private final List<Constant> elements;

    private Constant(IrType type, int number, List<Constant> elements) {
        super(type, "");
        this.number = number;
        this.elements = List.copyOf(elements);
    }

    // 创建整数常量，i1 和 i8 只保留对应位宽的低位
    public static Constant integer(IrType type, int value) {
        if (!type.isInteger())
            throw new IllegalArgumentException("Expected integer type");
        return new Constant(type,
                type.equals(IrType.I8) ? value & 255
                        : type.equals(IrType.I1) ? value & 1
                        : value,
                List.of());
    }

    // 检查元素数量和类型后构造数组常量
    public static Constant array(IrType type, List<Constant> elements) {
        if (type.kind() != IrType.Kind.ARRAY || type.length() != elements.size() ||
                elements.stream().anyMatch(c -> !c.getType().equals(type.element())))
            throw new IllegalArgumentException("Invalid aggregate constant");
        return new Constant(type, 0, elements);
    }

    public int getNumber() {
        return number;
    }

    public List<Constant> getElements() {
        return elements;
    }
}
