package frontend.symbol.type;

import frontend.ast.function.FuncType;

abstract public class SymbolType {

    abstract public boolean is(String type);

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

    public static SymbolType from(FuncType funcType) {
        return Type.from(funcType);
    }

    public static String getDisplayString(SymbolType type) {
        return type.getDisplayName();
    }
}
