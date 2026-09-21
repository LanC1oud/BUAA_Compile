package frontend.ast.expression;

import frontend.token.Token;

/**
 * LOrExp -> LAndExp | LOrExp '||' LAndExp
 */
public class LOrExp extends BinaryExp {
    public LOrExp(LAndExp operand) {
        super(operand);
    }

    public LOrExp(LOrExp left, Token op, LAndExp right) {
        super(left, op, right);
    }

    @Override
    protected String getName() {
        return "<LOrExp>";
    }
}
