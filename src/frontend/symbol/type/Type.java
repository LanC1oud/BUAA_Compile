package frontend.symbol.type;

import frontend.ast.expression.ConstExp;
import frontend.ast.function.FuncType;

import java.util.List;

/**
 * SysY 的类型：{@code int}、{@code char}、{@code int[]}、{@code char[]}、{@code void}，共 5 种。
 *
 * <p>这 5 个实例都是单例，类型比较请直接用 {@link #equals} 或 {@code ==}。
 *
 * <p><b>与参考实现的区别</b>：参考用 bit 宽度区分类型（{@code I8} 表示 char），而
 * {@code TypeInt(8).is("int")} 返回 true，等于让 {@code int} 和 {@code char} 变成同一类，
 * 于是 {@code compatible()} 里"都是 int 就兼容"这条规则得以成立。
 * 但 2026 文法明确要求 {@code int} 和 {@code char} 是两种不同类型、不允许隐式混用，
 * 所以这里改为按 {@link Kind} 判断身份，不再使用宽度。
 */
final public class Type extends SymbolType {

    /** 5 种类型的种类。 */
    public enum Kind {
        Int("int", "Int"),
        Char("char", "Char"),
        IntArray("int[]", "IntArray"),
        CharArray("char[]", "CharArray"),
        Void("void", "Void");

        /** 小写形式，用于 {@code is(String)} 与调试输出。 */
        private final String lowerName;
        /** 符号表输出使用的基础类型名，数组种类自带 {@code Array} 后缀。 */
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

        /**
         * 去掉 {@code Array} 后缀的词根，用于在类型名前拼 {@code Const}/{@code Static} 前缀。
         *
         * @return {@code Int}、{@code Char} 或 {@code Void}
         */
        public String baseName() {
            return displayName.endsWith("Array")
                    ? displayName.substring(0, displayName.length() - "Array".length())
                    : displayName;
        }
    }

    public static final Type Int = new Type(Kind.Int, null, 0);
    public static final Type Char = new Type(Kind.Char, null, 0);
    public static final Type Void = new Type(Kind.Void, null, 0);
    public static final Type IntArray = new Type(Kind.IntArray, Int, 0);
    public static final Type CharArray = new Type(Kind.CharArray, Char, 0);

    private final Kind kind;
    private final Type elementType;
    private final int length;

    private Type(Kind kind, Type elementType, int length) {
        this.kind = kind;
        this.elementType = elementType;
        this.length = length;
    }

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

    // -------------------------------------------------- 符号表类型名（13 种）

    /**
     * 拼装符号表要求的类型名，例如 {@code Int}、{@code ConstIntArray}、{@code StaticCharArray}。
     *
     * <p>13 种名字本质上只有两个维度：<b>变量 / 常量 / 静态</b> 与 <b>标量 / 数组</b>。
     * 数组维度由类型自身决定（{@link #isArray()}），所以这里只需再给出 const/static 两个修饰，
     * 用一处分支就能覆盖全部 9 个变量名，不必写 9 个方法。
     *
     * @param isConst 是否为常量，加 {@code Const} 前缀
     * @param isStatic 是否为静态局部变量，加 {@code Static} 前缀
     * @return 类型名
     */
    public String displayName(boolean isConst, boolean isStatic) {
        String prefix = isConst ? "Const" : (isStatic ? "Static" : "");
        return prefix + kind.baseName() + (isArray() ? "Array" : "");
    }

    /** @return 符号表输出使用的基础类型名，如 {@code Int}、{@code IntArray}。 */
    public String getDisplayName() {
        return kind.getDisplayName();
    }

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
        // 只有数组才需要比长度：int a[] 与 int a[5] 属于同一类型 —— 后者可传给前者。
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
