package frontend.ast.statement;

import frontend.ast.expression.Exp;

/**
 * Stmt -> [Exp] ';'
 */
public class ExpStmt extends Stmt {
    private final Exp exp;

    public ExpStmt(Exp exp) {
        this.exp = exp;
    }

    /** @return the expression, or {@code null} for an empty statement ({@code ;}). */
    public Exp getExp() {
        return exp;
    }

    public boolean isEmpty() {
        return exp == null;
    }
}
