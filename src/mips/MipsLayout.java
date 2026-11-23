package mips;

import llvm.type.IrType;

// 提供 MIPS 类型大小、对齐和立即数范围的计算
public final class MipsLayout {

    private MipsLayout() { }

    // 判断数值是否适合有符号十六位立即数
    public static boolean shortImmediate(int value) { return value >= -32768 && value <= 32767; }

    // 计算类型所需的存储字节数
    public static int sizeOf(IrType type) {
        return switch (type.kind()) {
            case I1, I8 -> 1;
            case I32, POINTER -> 4;
            case ARRAY -> Math.multiplyExact(type.length(), sizeOf(type.element()));
            default -> throw new IllegalArgumentException("No MIPS storage size for " + type);
        };
    }

    // 取得类型的字节对齐要求
    public static int alignment(IrType type) {
        return type.kind() == IrType.Kind.ARRAY ? alignment(type.element()) : Math.min(4, sizeOf(type));
    }

    // 将数值向上取整到指定对齐边界
    public static int align(int value, int alignment) {
        return Math.addExact(value, alignment - 1) / alignment * alignment;
    }
}
