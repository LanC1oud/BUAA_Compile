package frontend.ast.expression;

import frontend.ast.ASTNode;

// ConstExp -> AddExp
public class ConstExp extends ASTNode {
    private final AddExp addExp;

    public ConstExp(AddExp addExp) {
        this.addExp = addExp;
    }

    public AddExp getAddExp() {
        return addExp;
    }

    // 将常量表达式委托给内部加减表达式求值
    public int calculate() {
        return addExp.calculateConst();
    }

    // 通过统一入口对常量表达式求值
    @Override
    public int calculateConst() {
        return addExp.calculateConst();
    }

    @Override
    protected String getName() {
        return "<ConstExp>";
    }
}
