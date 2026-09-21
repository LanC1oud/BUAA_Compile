package frontend.ast.expression;

import frontend.token.Token;

/**
 * LAndExp -> EqExp | LAndExp '&&' EqExp
 */
public class LAndExp extends BinaryExp {
    public LAndExp(EqExp operand) {
        super(operand);
    }

    public LAndExp(LAndExp left, Token op, EqExp right) {
        super(left, op, right);
    }

    @Override
    protected String getName() {
        return "<LAndExp>";
    }
}
