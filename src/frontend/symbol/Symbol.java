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
    
    public void setConst() {
        qualifier.setConst();
    }
    
    public boolean isConst() {
        return qualifier.isConst();
    }
    
    protected Symbol(String name, SymbolType type, boolean isGlobal, int symbolTableIndex) {
        this.name = name;
        this.type = type;
        this.index = count++;
        this.isGlobal = isGlobal;
        this.symbolTableIndex = symbolTableIndex;
    }

    public String getName() {
        return name;
    }
    
    public SymbolType getType() {
        return type;
    }
    
    public boolean isGlobal() {
        return isGlobal;
    }
    
    @Override
    public int compareTo(Symbol symbol) {
        if (symbolTableIndex == symbol.symbolTableIndex) {
            return Integer.compare(index, symbol.index);
        }
        return Integer.compare(symbolTableIndex, symbol.symbolTableIndex);
    }

    /**
     * 符号表输出使用的类型名。
     *
     * <p>放在子类里实现，是因为 {@code Const}/{@code Static} 前缀并不在类型里，
     * 而在符号自己的属性上，只看类型算不出来。
     *
     * @return 如 {@code Int}、{@code ConstIntArray}、{@code IntFunc}
     */
    public abstract String getDisplayString();

    @Override
    public String toString() {
        return symbolTableIndex + " " + name + " " + getDisplayString();
    }
}
