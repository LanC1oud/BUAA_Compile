package frontend;

import frontend.ast.ASTNode;
import frontend.symbol.Symbol;
import frontend.symbol.SymbolFunction;
import frontend.symbol.SymbolVariable;
import frontend.symbol.type.Type;

import java.util.IdentityHashMap;
import java.util.Map;

/** One compilation's semantic facts. Identity keys preserve lexical shadowing. */
public final class SemanticInfo {
    private final Map<ASTNode, Type> types = new IdentityHashMap<>();
    private final Map<ASTNode, Symbol> bindings = new IdentityHashMap<>();

    void putType(ASTNode node, Type type) {
        types.put(node, type);
    }

    void bind(ASTNode node, Symbol symbol) {
        bindings.put(node, symbol);
    }

    public Type typeOf(ASTNode node) {
        Type type = types.get(node);
        if (type == null)
            throw new IllegalStateException("Missing semantic type: " + node.getClass());
        return type;
    }

    public SymbolVariable variableOf(ASTNode node) {
        return (SymbolVariable) required(node);
    }

    public SymbolFunction functionOf(ASTNode node) {
        return (SymbolFunction) required(node);
    }

    private Symbol required(ASTNode node) {
        Symbol symbol = bindings.get(node);
        if (symbol == null)
            throw new IllegalStateException("Missing binding: " + node.getClass());
        return symbol;
    }
}
