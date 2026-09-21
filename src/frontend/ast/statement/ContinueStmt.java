package frontend.ast.statement;

import frontend.token.Token;

/**
 * Stmt -> 'continue' ';'
 */
public class ContinueStmt extends Stmt {
    private final Token token;

    public ContinueStmt(Token token) {
        this.token = token;
    }

    /** @return the {@code continue} keyword, needed to report error m at the right line. */
    public Token getToken() {
        return token;
    }
}
