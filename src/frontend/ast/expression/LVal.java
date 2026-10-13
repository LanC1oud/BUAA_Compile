package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.symbol.SymbolVariable;
import frontend.token.Token;

// LVal -> Ident ['[' Exp ']']
public class LVal extends ASTNode {
    private final Token ident;
    private final Exp index;

    private SymbolVariable symbol;

    public LVal(Token ident, Exp index) {
        this.ident = ident;
        this.index = index;
    }

    public Token getIdent() {
        return ident;
    }

    // subscript expression; null for a scalar variable
    public Exp getIndex() {
        return index;
    }

    public boolean isArrayElement() {
        return index != null;
    }

    // resolved symbol; null before semantic analysis
    public SymbolVariable getSymbol() {
        return symbol;
    }

    public void setSymbol(SymbolVariable symbol) {
        this.symbol = symbol;
    }

    // throws UnsupportedOperationException if the symbol is unresolved or not a constant
    @Override
    public int calculateConst() {
        if (symbol == null) {
            throw new UnsupportedOperationException(
                    "符号 `" + ident.value() + "` 尚未解析");
        }
        if (symbol.getConstantArray() != null) {
            if (index == null) {
                throw new UnsupportedOperationException(
                        "常量数组 `" + ident.value() + "` 整体没有标量值");
            }
            return symbol.getConstantArray().get(index.calculateConst());
        }
        return symbol.getConstantValue().getValue();
    }

    @Override
    protected String getName() {
        return "<LVal>";
    }
}