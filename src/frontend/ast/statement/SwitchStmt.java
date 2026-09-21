package frontend.ast.statement;

import frontend.ast.expression.Exp;
import java.util.List;

/**
 * Stmt -> 'switch' '(' Exp ')' '{' { CaseStmt } '}'
 */
public class SwitchStmt extends Stmt {
    private final Exp exp;
    private final List<CaseStmt> cases;

    public SwitchStmt(Exp exp, List<CaseStmt> cases) {
        this.exp = exp;
        this.cases = cases;
    }

    public Exp getExp() {
        return exp;
    }

    public List<CaseStmt> getCases() {
        return cases;
    }
}
