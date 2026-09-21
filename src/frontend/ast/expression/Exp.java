package frontend.ast.expression;

import frontend.ast.ASTNode;

/**
 * Exp -> AddExp
 */
public class Exp extends ASTNode {
    private final AddExp addExp;

    public Exp(AddExp addExp) {
        this.addExp = addExp;
    }

    public AddExp getAddExp() {
        return addExp;
    }

    @Override
    protected String getName() {
        return "<Exp>";
    }
}
