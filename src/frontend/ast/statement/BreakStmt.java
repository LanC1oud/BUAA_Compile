package frontend.ast.statement;

import frontend.token.Token;

/**
 * Stmt -> 'break' ';'
 */
public class BreakStmt extends Stmt {
    private final Token token;

    public BreakStmt(Token token) {
        this.token = token;
    }

    /** @return the {@code break} keyword, needed to report error m at the right line. */
    public Token getToken() {
        return token;
    }
}
