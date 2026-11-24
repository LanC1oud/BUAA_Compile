package frontend.ast;

abstract public class ASTNode {
    abstract protected String getName();

    public String toString() {
        return getName();
    }

    public int calculateConst() {
        throw new UnsupportedOperationException(
                getName() + " 无法作为常量求值");
    }
}