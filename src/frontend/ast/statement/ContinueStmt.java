package frontend.ast.statement;

import frontend.token.Token;

// Stmt -> 'continue' ';'
public class ContinueStmt extends Stmt {
    private final Token token;

    public ContinueStmt(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }
}