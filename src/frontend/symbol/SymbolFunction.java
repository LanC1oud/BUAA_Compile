package frontend.symbol;

import frontend.symbol.type.SymbolType;

import java.util.LinkedList;
import java.util.List;

final public class SymbolFunction extends Symbol {

    private final List<SymbolVariable> parameters = new LinkedList<>();

    public SymbolFunction(String name, SymbolType type, int symbolTableIndex) {
        super(name, type, true, symbolTableIndex);
    }

    public void addParameter(SymbolVariable parameter) {
        parameters.add(parameter);
    }

    public List<SymbolVariable> getParameters() {
        return parameters;
    }

    @Override
    public String getDisplayString() {
        return getType().displayName(false, false) + "Func";
    }
}