package llvm.type;

import frontend.symbol.type.Type;

import java.util.List;

// 表示 IR 的基本类型及数组、指针和函数类型结构
public record IrType(Kind kind, IrType element, int length, List<IrType> parameters) {
    // 当前 IR 支持的类型种类
    public enum Kind {
        I1, I8, I32, VOID, LABEL, ARRAY, POINTER, FUNCTION
    }

    public static final IrType I1 = simple(Kind.I1), I8 = simple(Kind.I8), I32 = simple(Kind.I32),
            VOID = simple(Kind.VOID), LABEL = simple(Kind.LABEL);

    // 规范化参数列表，保存不可修改的副本
    public IrType {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    // 构造不含元素类型和参数的基本类型
    private static IrType simple(Kind k) {
        return new IrType(k, null, 0, List.of());
    }

    // 构造指向指定类型的指针类型
    public static IrType pointer(IrType base) {
        return new IrType(Kind.POINTER, base, 0, List.of());
    }

    // 构造具有指定元素类型和正长度的数组类型
    public static IrType array(IrType base, int size) {
        if (size <= 0)
            throw new IllegalArgumentException("Array size must be positive");
        return new IrType(Kind.ARRAY, base, size, List.of());
    }

    // 根据返回类型和形参类型构造函数类型
    public static IrType function(IrType result, List<IrType> params) {
        return new IrType(Kind.FUNCTION, result, 0, params);
    }

    // 将前端类型映射到 IR 类型，数组形参转换为元素指针
    public static IrType from(Type type, boolean parameter) {
        if (type.isArray()) {
            Type sourceElementType = type.getElementType();
            IrType elementType = from(sourceElementType, false);
            if (parameter)
                return pointer(elementType);
            int length = type.getLength();
            return array(elementType, length);
        }
        return type.isChar() ? I8 : type.isVoid() ? VOID : I32;
    }

    // 判断是否为 i1、i8 或 i32 整数类型
    public boolean isInteger() {
        return this.equals(I1) || this.equals(I8) || this.equals(I32);
    }

    // 取整数位宽，非整数类型返回零
    public int bits() {
        return equals(I1) ? 1 : equals(I8) ? 8 : equals(I32) ? 32 : 0;
    }

    // 输出 LLVM 类型文本
    @Override
    public String toString() {
        return switch (kind) {
            case ARRAY -> "[" + length + " x " + element + "]";
            case POINTER -> element + "*";
            case FUNCTION -> {
                List<String> parameterTypes = parameters.stream().map(Object::toString).toList();
                String parameterText = String.join(", ", parameterTypes);
                yield element + " (" + parameterText + ")";
            }
            default -> kind.name().toLowerCase();
        };
    }
}
