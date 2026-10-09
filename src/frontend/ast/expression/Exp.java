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

    /**
     * 在编译期求出该表达式的值。
     *
     * <p>这一层转发是必需的：{@code PrimaryExp} 的括号分支里装的是 {@code Exp} 而不是
     * {@code ConstExp}，所以 {@code (1 + 2) * 3} 只有在这里转发之后才能被折叠。
     *
     * @return 表达式的值
     * @throws UnsupportedOperationException 该表达式不是常量
     */
    @Override
    public int calculateConst() {
        return addExp.calculateConst();
    }

    @Override
    protected String getName() {
        return "<Exp>";
    }
}
