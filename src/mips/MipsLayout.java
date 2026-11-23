package mips;

import llvm.type.IrType;

/** MIPS 存储大小、对齐与立即数范围。 */
public final class MipsLayout {
    private MipsLayout() { }

    public static boolean shortImmediate(int value) { return value >= -32768 && value <= 32767; }

    public static int sizeOf(IrType type) {
        return switch (type.kind()) {
            case I1, I8 -> 1;
            case I32, POINTER -> 4;
            case ARRAY -> Math.multiplyExact(type.length(), sizeOf(type.element()));
            default -> throw new IllegalArgumentException("No MIPS storage size for " + type);
        };
    }
    public static int alignment(IrType type) {
        return type.kind() == IrType.Kind.ARRAY ? alignment(type.element()) : Math.min(4, sizeOf(type));
    }
    public static int align(int value, int alignment) {
        return Math.addExact(value, alignment - 1) / alignment * alignment;
    }
}
