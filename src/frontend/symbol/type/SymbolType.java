package frontend.symbol.type;

import frontend.ast.function.FuncType;

abstract public class SymbolType {

    abstract public boolean is(String type);

    abstract public String getDisplayName();

    abstract public String displayName(boolean isConst, boolean isStatic);

    public static SymbolType from(FuncType funcType) {
        return Type.from(funcType);
    }

    public static String getDisplayString(SymbolType type) {
        return type.getDisplayName();
    }
}