package frontend.ast.expression;

import frontend.token.Token;

/**
 * AddExp -> MulExp | AddExp ('+' | '-') MulExp
 */
public class AddExp extends BinaryExp {
    public AddExp(MulExp operand) {
        super(operand);
    }

    public AddExp(AddExp left, Token op, MulExp right) {
        super(left, op, right);
    }

    @Override
    protected String getName() {
        return "<AddExp>";
    }
}
