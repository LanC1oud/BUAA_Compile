package frontend.ast.statement;

import frontend.ast.expression.Exp;
import frontend.ast.expression.LVal;

/**
 * Stmt -> LVal '=' Exp ';'
 */
public class AssignStmt extends Stmt {
    private final LVal lval;
    private final Exp exp;

    public AssignStmt(LVal lval, Exp exp) {
        this.lval = lval;
        this.exp = exp;
    }

    public LVal getLVal() {
        return lval;
    }

    public Exp getExp() {
        return exp;
    }
}
