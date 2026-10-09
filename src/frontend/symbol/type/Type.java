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

    /** 5 种类型的种类，并携带拼装符号表类型名所需的词根。 */
    public enum Kind {
        Int("int", "Int"),
        Char("char", "Char"),
        IntArray("int[]", "Int"),
        CharArray("char[]", "Char"),
        Void("void", "Void");

        /**
         * 把 kind 名字拼成符号表要的类型名。
         *
         * @param isConst 常量加 {@code Const} 前缀
         * @param isStatic 静态局部变量加 {@code Static} 前缀
         * @param isArray 数组加 {@code Array} 后缀
         * @return 如 {@code Int}、{@code ConstIntArray}、{@code StaticCharArray}
         */
        public static String displayName(Kind kind, boolean isConst, boolean isStatic, boolean isArray) {
            String prefix = isConst ? "Const" : (isStatic ? "Static" : "");
            return prefix + kind.displayName + (isArray ? "Array" : "");
        }

        /** 小写形式，用于 {@code is(String)} 与调试输出。 */
        private final String lowerName;
        /** 词根，如 {@code Int}、{@code Char}；数组的 {@code Array} 后缀由调用方补。 */
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
    /** 数组的元素类型；非数组为 null。 */
    private final Type elementType;
    /**
     * 数组长度。0 既表示"长度为 0"也表示"长度未知"，
     * 后者用于函数形参 {@code int a[]}。
     */
    private final int length;

    private Type(Kind kind, Type elementType, int length) {
        this.kind = kind;
        this.elementType = elementType;
        this.length = length;
    }

    // -------------------------------------------------------------- 构造入口

    /**
     * 把函数返回类型（{@code void} / {@code int} / {@code char}）转成类型对象。
     */
    public static Type from(FuncType funcType) {
        if (funcType.isInt()) {
            return Int;
        }
        if (funcType.isChar()) {
            return Char;
        }
        return Void;
    }

    /**
     * 构造一个一维数组类型。
     *
     * <p>注意 5 个单例已经覆盖了最常用的场景；当数组带具体长度时才会新建实例。
     *
     * @param elementType 元素类型，只能是 {@code int} 或 {@code char}
     * @param length 数组长度；0 表示长度未知（函数形参 {@code int a[]}）
     * @throws IllegalArgumentException 元素类型不是一个基本类型，或长度为负
     */
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

    /**
     * 按 {@link ConstExp} 给出的长度构造一维数组类型。
     *
     * <p>对照参考实现的 {@code TyArray.from(base, indices)}：那边支持多维（从内往外折叠，
     * 遇到 null 折成指针），而本文法只有一维，所以这里直接取第一个长度即可。
     * 若长度表达式为 null，则视为长度未知，返回对应的数组单例。
     *
     * @param elementType 元素类型
     * @param lengths 各维长度表达式，只会用到第 0 个
     * @return 数组类型
     */
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

    /** @return 数组的元素类型；非数组抛出异常。 */
    public Type getElementType() {
        if (elementType == null) {
            throw new UnsupportedOperationException(toString() + " 不是数组类型");
        }
        return elementType;
    }

    /**
     * @return 数组长度；0 可能表示长度未知（形参数组）
     * @throws UnsupportedOperationException 不是数组
     */
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

    /**
     * @return 是否是"可以参与运算"的基本类型，即 {@code int} 或 {@code char}
     */
    public boolean isBasic() {
        return kind == Kind.Int || kind == Kind.Char;
    }

    /**
     * 按 5 种类型的名字判断。
     *
     * <p>只认 {@code "int"}、{@code "char"}、{@code "int[]"}、{@code "char[]"}、{@code "void"}。
     * <b>刻意不接受 {@code "i32"}/{@code "i8"} 这类宽度别名</b>，否则 int 和 char 又会被混为一谈。
     */
    public boolean is(String type) {
        return kind.getLowerName().equalsIgnoreCase(type);
    }

    /** @return 符号表输出用的类型名，如 {@code Int}、{@code IntArray}。 */
    public String getDisplayName() {
        return kind.getDisplayName();
    }

    /**
     * 拼装符号表要求的类型名，带上符号自己的修饰前缀。
     *
     * <p>{@code Const} 与 {@code Static} 不会同时出现：前者来自 {@code const} 声明，
     * 后者只用于 {@code static} 局部变量。数组后缀由类型自身决定，不用调用方再传。
     *
     * @param isConst 常量加 {@code Const} 前缀
     * @param isStatic 静态局部变量加 {@code Static} 前缀
     * @return 如 {@code Int}、{@code ConstInt}、{@code StaticIntArray}
     */
    @Override
    public String displayName(boolean isConst, boolean isStatic) {
        return Kind.displayName(kind, isConst, isStatic, isArray());
    }

    /**
     * @return 类型占用的字节数，用于后续代码生成阶段
     * @throws UnsupportedOperationException 对 {@code void} 取大小，或数组长度未知
     */
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

    // ------------------------------------------------------------------ 类型关系

    /**
     * 两个类型是否完全一致。
     *
     * <p>这就是 2026 的类型匹配规则：<b>不允许 int 与 char 隐式混用</b>。
     * 参考实现的 {@code compatible()} 里额外放行"两者都是 int"，那是旧文法的规则，
     * 这里不要照抄。
     */
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

    /**
     * 数组之间是否可互相传递：元素类型相同即可，长度不参与比较。
     *
     * <p>对应文档第 10 页的例子：{@code f2(t)} 中形参是 {@code int x[]}、实参是 {@code int t[5]}，
     * 这是合法的。
     */
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

