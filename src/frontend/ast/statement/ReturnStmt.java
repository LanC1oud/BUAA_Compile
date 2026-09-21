package frontend.ast.statement;

import frontend.ast.expression.Exp;
import frontend.token.Token;

/**
 * Stmt -> 'return' [Exp] ';'
 */
public class ReturnStmt extends Stmt {
    private final Token returnToken;
    private final Exp exp;

    public ReturnStmt(Token returnToken, Exp exp) {
        this.returnToken = returnToken;
        this.exp = exp;
    }

    public Token getReturnToken() {
        return returnToken;
    }

    /** @return the returned expression, or {@code null} for a bare {@code return;}. */
    public Exp getExp() {
        return exp;
    }

    public boolean hasExp() {
        return exp != null;
    }
}
