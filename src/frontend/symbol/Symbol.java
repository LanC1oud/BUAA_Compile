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

    public abstract String getDisplayString();

    @Override
    public String toString() {
        return symbolTableIndex + " " + name + " " + getDisplayString();
    }
}