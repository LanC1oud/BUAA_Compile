package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.token.Token;

/**
 * Number -> IntConst | CharConst
 */
public class Number extends ASTNode {
    private final Token token;

    public Number(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }

    @Override
    protected String getName() {
        return "<Number>";
    }
}
