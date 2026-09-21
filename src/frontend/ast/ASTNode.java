package frontend.ast;

abstract public class ASTNode {
    abstract protected String getName();

    public String toString() {
        return getName();
    }
}
