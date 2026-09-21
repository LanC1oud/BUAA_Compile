package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.token.Token;

/**
 * LVal -> Ident ['[' Exp ']']
 */
public class LVal extends ASTNode {
    private final Token ident;
    private final Exp index;

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

    @Override
    protected String getName() {
        return "<LVal>";
    }
}
