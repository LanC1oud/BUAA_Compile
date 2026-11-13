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

    // 建立变量、常量或形参的符号
    public SymbolVariable(String name, SymbolType type, boolean isGlobal, int symbolTableIndex) {
        super(name, type, isGlobal, symbolTableIndex);
    }

    // ------------------------------------------------------------------ 常量值

    // 判断是否已保存编译期值
    public boolean hasConstantValue() {
        return constantValue != null || constantArray != null;
    }

    // 保存标量初值
    public void setConstantValue(FixedValue constantValue) {
        this.constantValue = constantValue;
    }

    // 保存数组初值
    public void setConstantValue(FixedArray constantArray) {
        this.constantArray = constantArray;
        this.constantValue = null;
    }

    // 取数组的编译期值，尚未记录时返回 null
    public FixedArray getConstantArray() {
        return constantArray;
    }

    // 读取已保存的标量值，缺失时抛出异常
    public FixedValue getConstantValue() {
        if (constantValue == null) {
            throw new UnsupportedOperationException(
                    "变量 `" + getName() + "` 不是编译期常量");
        }
        return constantValue;
    }

    // ------------------------------------------------------------------ 修饰与来源

    // 判断是否为 static 局部变量
    public boolean isStatic() {
        return isStatic;
    }

    // 记录 static 声明修饰
    public void setStatic() {
        this.isStatic = true;
    }

    // 判断符号是否来自形参
    public boolean isFromParam() {
        return fromParam;
    }

    // 标记为函数形参
    public void setFromParam() {
        this.fromParam = true;
    }

    // ------------------------------------------------------------------ 输出

    // 生成含 const、static 或数组信息的类型标签
    @Override
    public String getDisplayString() {
        return getType().displayName(isConst(), isStatic);
    }
}
