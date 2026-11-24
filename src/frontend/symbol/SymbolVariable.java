package frontend.symbol;

import frontend.symbol.type.SymbolType;
import frontend.symbol.type.Type;

final public class SymbolVariable extends Symbol {

    private FixedValue constantValue;

    // 数组常量的元素表；与 constantValue 互斥。
    private FixedArray constantArray;

    // static 局部变量（文档：static 只修饰局部变量）。
    private boolean isStatic = false;

    private boolean fromParam = false;

    public SymbolVariable(String name, SymbolType type, boolean isGlobal, int symbolTableIndex) {
        super(name, type, isGlobal, symbolTableIndex);
    }

    // ------------------------------------------------------------------ 常量值

    public boolean hasConstantValue() {
        return constantValue != null || constantArray != null;
    }

    public void setConstantValue(FixedValue constantValue) {
        this.constantValue = constantValue;
    }

    public void setConstantValue(FixedArray constantArray) {
        this.constantArray = constantArray;
        this.constantValue = null;
    }

    // element table of a constant array, or null
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

    // ------------------------------------------------------------------ 修饰与来源

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

    // ------------------------------------------------------------------ 输出

    @Override
    public String getDisplayString() {
        return getType().displayName(isConst(), isStatic);
    }
}