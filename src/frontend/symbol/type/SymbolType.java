package frontend.symbol.type;

import frontend.ast.function.FuncType;

/**
 * 符号类型的基类。
 *
 * <p>本工程里它只有一个实现 {@link Type}，保留这一层是为了让
 * {@code Symbol} / {@code SymbolVariable} / {@code SymbolFunction} / {@code SymbolTable}
 * 的字段与参数不必改写。类型的具体能力（长度、元素类型、sizeof 等）都在 {@link Type} 上。
 *
 * <p>注意这里<b>不再提供 {@code compatible()}</b>。参考实现里的
 * {@code compatible()} 会放行"int 与 char 互相兼容"，那是旧文法的规则；
 * 2026 要求两者严格不相等，直接用 {@link Type#equals} 即可。
 */
abstract public class SymbolType {

    /**
     * 按 5 种类型的名字判断，取值只能是
     * {@code "int"}、{@code "char"}、{@code "int[]"}、{@code "char[]"}、{@code "void"}。
     */
    abstract public boolean is(String type);

    /** @return 符号表输出使用的基础类型名，如 {@code Int}、{@code CharArray}。 */
    abstract public String getDisplayName();

    /**
     * 拼装符号表要求的类型名。
     *
     * <p>{@code Const}/{@code Static} 是符号的属性而非类型的属性，所以由符号把自己的修饰位传进来。
     * 之所以在基类上开这个方法，是为了让 {@code SymbolVariable} 不必把 {@link SymbolType}
     * 向下强转成 {@link Type}。
     *
     * @param isConst 常量加 {@code Const} 前缀
     * @param isStatic 静态局部变量加 {@code Static} 前缀
     * @return 类型名
     */
    abstract public String displayName(boolean isConst, boolean isStatic);

    /**
     * 把函数返回类型（{@code void} / {@code int} / {@code char}）转成类型对象。
     */
    public static SymbolType from(FuncType funcType) {
        return Type.from(funcType);
    }

    /**
     * 符号表输出使用的类型名。
     *
     * <p>数组与指向数组元素的指针在输出里都写作 {@code IntArray}，这一点与参考实现一致。
     *
     * @param type 待渲染的类型
     * @return 类型名
     */
    public static String getDisplayString(SymbolType type) {
        return type.getDisplayName();
    }
}

