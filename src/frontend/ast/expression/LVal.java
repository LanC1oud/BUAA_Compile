package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.symbol.SymbolVariable;
import frontend.token.Token;

/**
 * LVal -> Ident ['[' Exp ']']
 */
public class LVal extends ASTNode {
    private final Token ident;
    private final Exp index;

    /**
     * 该名字解析出的符号，由语义分析阶段回填。建树期间为 null，
     * 常量求值就是通过它拿到变量保存的常量值。
     */
    private SymbolVariable symbol;

    public LVal(Token ident, Exp index) {
        this.ident = ident;
        this.index = index;
    }

    public Token getIdent() {
        return ident;
    }

    /** @return the subscript expression, or {@code null} for a scalar variable. */
    public Exp getIndex() {
        return index;
    }

    public boolean isArrayElement() {
        return index != null;
    }

    /** @return 已解析的符号，语义分析之前为 null。 */
    public SymbolVariable getSymbol() {
        return symbol;
    }

    public void setSymbol(SymbolVariable symbol) {
        this.symbol = symbol;
    }

    /**
     * 对常量变量的引用求值。
     *
     * <p>标量常量直接取值；常量数组要再用下标去查元素表，下标本身也必须是编译期常量。
     *
     * @return 被引用对象的常量值
     * @throws UnsupportedOperationException 符号尚未解析、或该变量不是编译期常量
     * @throws IndexOutOfBoundsException 常量下标越界
     */
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
