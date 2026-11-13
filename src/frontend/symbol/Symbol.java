package frontend.symbol;

import frontend.symbol.type.SymbolQualifier;
import frontend.symbol.type.SymbolType;

abstract public class Symbol implements Comparable<Symbol> {
    private static int count = 0;
    private final int symbolTableIndex;
    private final int index;
    private final String name;
    private final SymbolType type;
    private final boolean isGlobal;
    private final SymbolQualifier qualifier = new SymbolQualifier();

    // 标记为常量，供常量上下文和赋值检查使用
    public void setConst() {
        qualifier.setConst();
    }

    // 判断符号是否带有 const 限定
    public boolean isConst() {
        return qualifier.isConst();
    }

    // 记录声明的名称、类型、作用域及登记顺序
    protected Symbol(String name, SymbolType type, boolean isGlobal, int symbolTableIndex) {
        this.name = name;
        this.type = type;
        this.index = count++;
        this.isGlobal = isGlobal;
        this.symbolTableIndex = symbolTableIndex;
    }

    // 取声明的标识符名称
    public String getName() {
        return name;
    }

    // 取变量类型或函数返回类型
    public SymbolType getType() {
        return type;
    }

    // 判断符号是否声明在全局作用域
    public boolean isGlobal() {
        return isGlobal;
    }

    // 先按作用域编号排序，同层按登记顺序排序
    @Override
    public int compareTo(Symbol symbol) {
        if (symbolTableIndex == symbol.symbolTableIndex) {
            return Integer.compare(index, symbol.index);
        }
        return Integer.compare(symbolTableIndex, symbol.symbolTableIndex);
    }

    // 由子类提供符号表中的类型标签
    public abstract String getDisplayString();

    // 输出作用域编号、名称和类型标签
    @Override
    public String toString() {
        return symbolTableIndex + " " + name + " " + getDisplayString();
    }
}
