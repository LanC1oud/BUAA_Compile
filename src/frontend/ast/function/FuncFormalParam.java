package frontend.ast.function;

import frontend.ast.ASTNode;
import frontend.token.Token;

/**
 * FuncFParam -> BType Ident ['[' ']']
 */
public class FuncFormalParam extends ASTNode {
    private final Token btype;
    private final Token ident;
    private final boolean array;

    public FuncFormalParam(Token btype, Token ident, boolean array) {
        this.btype = btype;
        this.ident = ident;
        this.array = array;
    }

    public Token getBType() {
        return btype;
    }

    public Token getIdent() {
        return ident;
    }

    /** @return {@code true} for {@code int a[]}, {@code false} for {@code int a}. */
    public boolean isArray() {
        return array;
    }

    @Override
    protected String getName() {
        return "<FuncFParam>";
    }
}
