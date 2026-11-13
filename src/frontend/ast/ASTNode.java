package frontend.ast;

abstract public class ASTNode {
    abstract protected String getName();

    public String toString() {
        return getName();
    }

    // 默认拒绝常量求值，可求值的表达式节点自行实现
    public int calculateConst() {
        throw new UnsupportedOperationException(
                getName() + " 无法作为常量求值");
    }
}
