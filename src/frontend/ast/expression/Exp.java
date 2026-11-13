package frontend.ast.expression;

import frontend.ast.ASTNode;

// Exp -> AddExp
public class Exp extends ASTNode {
    private final AddExp addExp;

    public Exp(AddExp addExp) {
        this.addExp = addExp;
    }

    public AddExp getAddExp() {
        return addExp;
    }

    // 将表达式的常量求值委托给内部加减表达式
    @Override
    public int calculateConst() {
        return addExp.calculateConst();
    }

    @Override
    protected String getName() {
        return "<Exp>";
    }
}
