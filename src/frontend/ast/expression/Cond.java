package frontend.ast.expression;

import frontend.ast.ASTNode;

/**
 * Cond -> LOrExp
 */
public class Cond extends ASTNode {
    private final LOrExp lOrExp;

    public Cond(LOrExp lOrExp) {
        this.lOrExp = lOrExp;
    }

    public LOrExp getLOrExp() {
        return lOrExp;
    }

    @Override
    protected String getName() {
        return "<Cond>";
    }
}
