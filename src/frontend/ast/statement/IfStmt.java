package frontend.ast.statement;

import frontend.ast.expression.Cond;

/**
 * Stmt -> 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
 */
public class IfStmt extends Stmt {
    private final Cond cond;
    private final Stmt thenStmt;
    private final Stmt elseStmt;

    public IfStmt(Cond cond, Stmt thenStmt, Stmt elseStmt) {
        this.cond = cond;
        this.thenStmt = thenStmt;
        this.elseStmt = elseStmt;
    }

    public Cond getCond() {
        return cond;
    }

    public Stmt getThenStmt() {
        return thenStmt;
    }

    /** @return the else branch, or {@code null} when the statement has no {@code else}. */
    public Stmt getElseStmt() {
        return elseStmt;
    }

    public boolean hasElse() {
        return elseStmt != null;
    }
}
