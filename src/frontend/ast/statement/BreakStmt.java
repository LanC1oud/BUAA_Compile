package frontend.ast.statement;

import frontend.token.Token;

// Stmt -> 'break' ';'
public class BreakStmt extends Stmt {
    private final Token token;

    public BreakStmt(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }
}