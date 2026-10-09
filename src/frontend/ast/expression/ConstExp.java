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

    /**
     * 在编译期求出该常量表达式的值。
     *
     * @return 表达式的值
     * @throws UnsupportedOperationException 该表达式不是常量
     */
    public int calculate() {
        return addExp.calculateConst();
    }

    /**
     * 与 {@link #calculate()} 等价的统一入口。
     *
     * <p>{@link frontend.ast.ASTNode} 上的 {@code calculateConst()} 是常量求值的通用入口，
     * 这里必须重写，否则经通用入口求值会被基类的默认实现当成"无法求值"。
     */
    @Override
    public int calculateConst() {
        return addExp.calculateConst();
    }

    @Override
    protected String getName() {
        return "<ConstExp>";
    }
    
    public int calculate() {
        return addExp.calculateConst();
    }
}
