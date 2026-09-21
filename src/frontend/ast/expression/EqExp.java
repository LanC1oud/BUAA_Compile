package frontend.ast.expression;

import frontend.token.Token;

/**
 * EqExp -> RelExp | EqExp ('==' | '!=') RelExp
 */
public class EqExp extends BinaryExp {
    public EqExp(RelExp operand) {
        super(operand);
    }

    public EqExp(EqExp left, Token op, RelExp right) {
        super(left, op, right);
    }

    @Override
    protected String getName() {
        return "<EqExp>";
    }
}
