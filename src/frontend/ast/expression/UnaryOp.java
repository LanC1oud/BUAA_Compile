package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.token.Token;

/**
 * UnaryOp -> '+' | '-' | '!'
 */
public class UnaryOp extends ASTNode {
    private final Token token;

    public UnaryOp(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }

    @Override
    protected String getName() {
        return "<UnaryOp>";
    }
}
