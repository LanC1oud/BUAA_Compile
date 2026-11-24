package llvm.type;

import frontend.symbol.type.Type;

import java.util.List;

/** 表示目标无关的 IR 类型，包括整数、数组、指针和函数签名。 */
public record IrType(Kind kind, IrType element, int length, List<IrType> parameters) {
    public enum Kind {
        I1, I8, I32, VOID, LABEL, ARRAY, POINTER, FUNCTION
    }

    public static final IrType I1 = simple(Kind.I1), I8 = simple(Kind.I8), I32 = simple(Kind.I32),
            VOID = simple(Kind.VOID), LABEL = simple(Kind.LABEL);

    public IrType {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    private static IrType simple(Kind k) {
        return new IrType(k, null, 0, List.of());
    }

    // 构造指向指定类型的指针
    public static IrType pointer(IrType base) {
        return new IrType(Kind.POINTER, base, 0, List.of());
    }

    // 构造元素类型和长度固定的数组类型
    public static IrType array(IrType base, int size) {
        if (size <= 0)
            throw new IllegalArgumentException("Array size must be positive");
        return new IrType(Kind.ARRAY, base, size, List.of());
    }

    // 用返回类型和形参类型构造函数签名
    public static IrType function(IrType result, List<IrType> params) {
        return new IrType(Kind.FUNCTION, result, 0, params);
    }

    // 将语义类型映射到 IR：char 对应 i8，int 对应 i32，数组形参转为元素指针
    public static IrType from(Type type, boolean parameter) {
        if (type.isArray())
            return parameter ? pointer(from(type.getElementType(), false))
                    : array(from(type.getElementType(), false), type.getLength());
        return type.isChar() ? I8 : type.isVoid() ? VOID : I32;
    }

    public boolean isInteger() {
        return this.equals(I1) || this.equals(I8) || this.equals(I32);
    }

    // 获取整数位宽，供转换指令的生成和校验使用
    public int bits() {
        return equals(I1) ? 1 : equals(I8) ? 8 : equals(I32) ? 32 : 0;
    }

    // 输出 LLVM 文本中的类型写法
    @Override
    public String toString() {
        return switch (kind) {
            case ARRAY -> "[" + length + " x " + element + "]";
            case POINTER -> element + "*";
            case FUNCTION ->
                    element + " (" + String.join(", ", parameters.stream().map(Object::toString).toList()) + ")";
            default -> kind.name().toLowerCase();
        };
    }
}
