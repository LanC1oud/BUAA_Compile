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

    // 取数组下标表达式，未写下标时返回 null
    public Exp getIndex() {
        return index;
    }

    // 判断左值是否写了数组下标
    public boolean isArrayElement() {
        return index != null;
    }

    // 取语义分析绑定的符号，尚未绑定时返回 null
    public SymbolVariable getSymbol() {
        return symbol;
    }

    // 保存名字查找命中的变量符号
    public void setSymbol(SymbolVariable symbol) {
        this.symbol = symbol;
    }

    // 从已绑定符号读取标量常量值或数组元素值
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
