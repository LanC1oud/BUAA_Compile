package frontend.ast.expression;

import frontend.ast.ASTNode;
import utils.ConstValue;
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

    // operator token; null for a leaf node
    public Token getOp() {
        return op;
    }

    // right operand; null for a leaf node
    public ASTNode getRight() {
        return right;
    }

    public boolean isLeaf() {
        return op == null;
    }

    @Override
    public int calculateConst() {
        int value = left.calculateConst();
        if (!isLeaf()) {
            value = ConstValue.calculate(value, right.calculateConst(), op.getType());
        }
        return value;
    }
}