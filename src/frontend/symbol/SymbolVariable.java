package frontend.symbol;

import frontend.symbol.type.SymbolType;
import frontend.symbol.type.Type;

final public class SymbolVariable extends Symbol {

    private FixedValue constantValue;

    /** 数组常量的元素表；与 {@link #constantValue} 互斥。 */
    private FixedArray constantArray;

    /** 是否为 {@code static} 修饰的局部变量（文档：static 只修饰局部变量）。 */
    private boolean isStatic = false;

    /** 是否为函数形参。 */
    private boolean fromParam = false;

    public SymbolVariable(String name, SymbolType type, boolean isGlobal, int symbolTableIndex) {
        super(name, type, isGlobal, symbolTableIndex);
    }

    // ------------------------------------------------------------ 常量值

    public boolean hasConstantValue() {
        return constantValue != null || constantArray != null;
    }

    public void setConstantValue(FixedValue constantValue) {
        this.constantValue = constantValue;
    }

    /** 记录数组常量的元素表。 */
    public void setConstantValue(FixedArray constantArray) {
        this.constantArray = constantArray;
        this.constantValue = null;
    }

    /** @return 数组常量的元素表，或 {@code null}。 */
    public FixedArray getConstantArray() {
        return constantArray;
    }

    public FixedValue getConstantValue() {
        if (constantValue == null) {
            throw new UnsupportedOperationException(
                    "变量 `" + getName() + "` 不是编译期常量");
        }
        return constantValue;
    }

    // ------------------------------------------------------------ 修饰与来源

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic() {
        this.isStatic = true;
    }

    public boolean isFromParam() {
        return fromParam;
    }

    public void setFromParam() {
        this.fromParam = true;
    }

    // ------------------------------------------------------------ 输出

    /**
     * 变量在符号表里的类型名。
     *
     * <p>{@code Const} 与 {@code Static} 不会同时出现：前者来自 {@code const} 声明，
     * 后者只用于 {@code static} 局部变量。
     *
     * @return 如 {@code Int}、{@code ConstInt}、{@code StaticIntArray}
     */
    @Override
    public String getDisplayString() {
        return getType().displayName(isConst(), isStatic);
    }
}

