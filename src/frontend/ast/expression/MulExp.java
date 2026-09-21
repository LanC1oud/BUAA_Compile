package frontend.ast.expression;

import frontend.token.Token;

/**
 * MulExp -> UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
 */
public class MulExp extends BinaryExp {
    public MulExp(UnaryExp operand) {
        super(operand);
    }

    public MulExp(MulExp left, Token op, UnaryExp right) {
        super(left, op, right);
    }

    @Override
    protected String getName() {
        return "<MulExp>";
    }
}
