package frontend.symbol.type;

import frontend.ast.expression.ConstExp;
import frontend.ast.function.FuncType;

import java.util.List;

final public class Type extends SymbolType {

    public enum Kind {
        Int("int", "Int"),
        Char("char", "Char"),
        IntArray("int[]", "Int"),
        CharArray("char[]", "Char"),
        Void("void", "Void");

        public static String displayName(Kind kind, boolean isConst, boolean isStatic, boolean isArray) {
            String prefix = isConst ? "Const" : (isStatic ? "Static" : "");
            return prefix + kind.displayName + (isArray ? "Array" : "");
        }

        private final String lowerName;
        private final String displayName;

        Kind(String lowerName, String displayName) {
            this.lowerName = lowerName;
            this.displayName = displayName;
        }

        public String getLowerName() {
            return lowerName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // ------------------------------------------------------------------ 单例

    public static final Type Int = new Type(Kind.Int, null, 0);
    public static final Type Char = new Type(Kind.Char, null, 0);
    public static final Type Void = new Type(Kind.Void, null, 0);
    public static final Type IntArray = new Type(Kind.IntArray, Int, 0);
    public static final Type CharArray = new Type(Kind.CharArray, Char, 0);

    private final Kind kind;
    // 数组的元素类型；非数组为 null。
    private final Type elementType;
    // 数组长度。0 既表示"长度为 0"，也表示"长度未知"，后者用于函数形参 int a[]。
    private final int length;

    private Type(Kind kind, Type elementType, int length) {
        this.kind = kind;
        this.elementType = elementType;
        this.length = length;
    }

    // -------------------------------------------------------------- 构造入口

    public static Type from(FuncType funcType) {
        if (funcType.isInt()) {
            return Int;
        }
        if (funcType.isChar()) {
            return Char;
        }
        return Void;
    }

    public static Type ofArray(Type elementType, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("数组长度不能为负：" + length);
        }
        if (elementType == Int) {
            return length == 0 ? IntArray : new Type(Kind.IntArray, Int, length);
        }
        if (elementType == Char) {
            return length == 0 ? CharArray : new Type(Kind.CharArray, Char, length);
        }
        throw new IllegalArgumentException("数组元素类型只能是 int 或 char，实际是 " + elementType);
    }

    public static Type ofArray(Type elementType, List<ConstExp> lengths) {
        if (lengths == null || lengths.isEmpty() || lengths.get(0) == null) {
            return ofArray(elementType, 0);
        }
        return ofArray(elementType, lengths.get(0).calculate());
    }

    // ------------------------------------------------------------------ 查询

    public Kind getKind() {
        return kind;
    }

    public Type getElementType() {
        if (elementType == null) {
            throw new UnsupportedOperationException(toString() + " 不是数组类型");
        }
        return elementType;
    }

    public int getLength() {
        if (kind != Kind.IntArray && kind != Kind.CharArray) {
            throw new UnsupportedOperationException(toString() + " 不是数组类型");
        }
        return length;
    }

    public boolean isInt() {
        return kind == Kind.Int;
    }

    public boolean isChar() {
        return kind == Kind.Char;
    }

    public boolean isVoid() {
        return kind == Kind.Void;
    }

    public boolean isArray() {
        return kind == Kind.IntArray || kind == Kind.CharArray;
    }

    public boolean isBasic() {
        return kind == Kind.Int || kind == Kind.Char;
    }

    public boolean is(String type) {
        return kind.getLowerName().equalsIgnoreCase(type);
    }

    public String getDisplayName() {
        return kind.getDisplayName();
    }

    @Override
    public String displayName(boolean isConst, boolean isStatic) {
        return Kind.displayName(kind, isConst, isStatic, isArray());
    }

    // size in bytes; throws UnsupportedOperationException for void or an unknown length
    public int sizeof() {
        return switch (kind) {
            case Int -> 4;
            case Char -> 1;
            case Void -> throw new UnsupportedOperationException("void 没有大小");
            case IntArray, CharArray -> {
                if (length == 0) {
                    throw new UnsupportedOperationException("长度未知的数组没有大小：" + this);
                }
                yield elementType.sizeof() * length;
            }
        };
    }

    // ------------------------------------------------------------------类型关系
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Type type)) {
            return false;
        }
        if (kind != type.kind) {
            return false;
        }
        if (isArray()) {
            return length == type.length;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return isArray() ? kind.hashCode() * 31 + length : kind.hashCode();
    }

    public boolean isPassableTo(Type parameter) {
        if (this == parameter) {
            return true;
        }
        if (!isArray() || !parameter.isArray()) {
            return false;
        }
        return elementType == parameter.elementType;
    }

    @Override
    public String toString() {
        if (isArray()) {
            return "[" + length + " x " + elementType + "]";
        }
        return kind.getLowerName();
    }
}