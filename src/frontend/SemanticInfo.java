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

    // 按节点身份记录类型
    void putType(ASTNode node, Type type) {
        types.put(node, type);
    }

    // 按节点身份记录符号绑定
    void bind(ASTNode node, Symbol symbol) {
        bindings.put(node, symbol);
    }

    // 查询已分析的节点类型
    public Type typeOf(ASTNode node) {
        Type type = types.get(node);
        if (type == null)
            throw new IllegalStateException("Missing semantic type: " + node.getClass());
        return type;
    }

    // 取节点绑定的变量符号
    public SymbolVariable variableOf(ASTNode node) {
        return (SymbolVariable) required(node);
    }

    // 取节点绑定的函数符号
    public SymbolFunction functionOf(ASTNode node) {
        return (SymbolFunction) required(node);
    }

    // 取已记录的绑定，缺失时抛出异常
    private Symbol required(ASTNode node) {
        Symbol symbol = bindings.get(node);
        if (symbol == null)
            throw new IllegalStateException("Missing binding: " + node.getClass());
        return symbol;
    }
}
