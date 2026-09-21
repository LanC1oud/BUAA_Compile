package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.token.Token;

public abstract class BinaryExp extends ASTNode {
    private final ASTNode left;
    private final Token op;
    private final ASTNode right;

    protected BinaryExp(ASTNode left) {
        this.left = left;
        this.op = null;
        this.right = null;
    }

    protected BinaryExp(ASTNode left, Token op, ASTNode right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    public ASTNode getLeft() {
        return left;
    }

    /** @return the operator token, or {@code null} for a leaf node. */
    public Token getOp() {
        return op;
    }

    /** @return the right operand, or {@code null} for a leaf node. */
    public ASTNode getRight() {
        return right;
    }

    public boolean isLeaf() {
        return op == null;
    }
}
