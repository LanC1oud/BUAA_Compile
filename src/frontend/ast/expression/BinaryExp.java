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

    /**
     * 在编译期求出这一层表达式的值。
     *
     * <p>由于文法左递归，{@code a + b + c} 被解析成 {@code AddExp(AddExp(a, +, b), +, c)}，
     * 即整棵树向<b>左</b>嵌套。所以求值必须先递归求出已经累积的左值，再把本结点的
     * {@code (op, right)} 折进去，这样才保持左结合性。
     *
     * @return 表达式的值
     * @throws UnsupportedOperationException 子表达式不是常量（非常量变量、函数调用、逻辑非）
     * @throws ArithmeticException 除数为 0 或对 0 取模
     */
    @Override
    public int calculateConst() {
        int value = left.calculateConst();
        if (!isLeaf()) {
            value = ConstValue.calculate(value, right.calculateConst(), op.getType());
        }
        return value;
    }
}
