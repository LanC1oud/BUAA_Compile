package frontend.ast.expression;

import frontend.ast.ASTNode;

/**
 * ConstExp -> AddExp
 */
public class ConstExp extends ASTNode {
    private final AddExp addExp;

    public ConstExp(AddExp addExp) {
        this.addExp = addExp;
    }

    public AddExp getAddExp() {
        return addExp;
    }

    @Override
    protected String getName() {
        return "<ConstExp>";
    }
    
    public int calculate() {
        return addExp.calculateConst();
    }
}
