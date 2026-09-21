package frontend.ast.statement;

import frontend.ast.expression.Cond;

/**
 * Stmt -> 'while' '(' Cond ')' Stmt
 */
public class WhileStmt extends Stmt {
    private final Cond cond;
    private final Stmt body;

    public WhileStmt(Cond cond, Stmt body) {
        this.cond = cond;
        this.body = body;
    }

    public Cond getCond() {
        return cond;
    }

    public Stmt getBody() {
        return body;
    }
}
