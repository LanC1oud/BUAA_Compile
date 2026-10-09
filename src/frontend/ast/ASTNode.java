package frontend.ast;

abstract public class ASTNode {
    abstract protected String getName();

    public String toString() {
        return getName();
    }

    /**
     * 在编译期求出该子树的值。
     *
     * <p>只有表达式结点参与常量求值，其它结点沿用这里的默认实现，表示"向它要值"是调用方的错误。
     *
     * @return 子树的常量值
     * @throws UnsupportedOperationException 该结点无法给出常量值
     */
    public int calculateConst() {
        throw new UnsupportedOperationException(
                getName() + " 无法作为常量求值");
    }
}
