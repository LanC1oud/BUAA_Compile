package frontend.ast.statement;

import frontend.ast.expression.Exp;

// Stmt -> [Exp] ';'
public class ExpStmt extends Stmt {
    private final Exp exp;

    public ExpStmt(Exp exp) {
        this.exp = exp;
    }

    // expression; null for an empty statement
    public Exp getExp() {
        return exp;
    }

    public boolean isEmpty() {
        return exp == null;
    }
}