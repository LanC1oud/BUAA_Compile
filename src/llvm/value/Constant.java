package llvm.value;

import llvm.type.IrType;

import java.util.List;

// 表示编译期已知的整数常量或数组常量
public final class Constant extends Value {
    private final int number;
    private final List<Constant> elements;

    // 保存常量类型、整数值或数组元素
    private Constant(IrType type, int number, List<Constant> elements) {
        super(type, "");
        this.number = number;
        this.elements = List.copyOf(elements);
    }

    // 构造整数常量，并按 i1 或 i8 位宽截断
    public static Constant integer(IrType type, int value) {
        if (!type.isInteger())
            throw new IllegalArgumentException("Expected integer type");
        int normalizedValue = value;
        if (type.equals(IrType.I8))
            normalizedValue = value & 255;
        else if (type.equals(IrType.I1))
            normalizedValue = value & 1;
        return new Constant(type, normalizedValue, List.of());
    }

    // 检查元素类型和数量后构造数组常量
    public static Constant array(IrType type, List<Constant> elements) {
        if (type.kind() != IrType.Kind.ARRAY || type.length() != elements.size() ||
                elements.stream().anyMatch(c -> !c.getType().equals(type.element())))
            throw new IllegalArgumentException("Invalid aggregate constant");
        return new Constant(type, 0, elements);
    }

    // 取整数常量的数值
    public int getNumber() {
        return number;
    }

    // 取数组常量的只读元素列表
    public List<Constant> getElements() {
        return elements;
    }
}
