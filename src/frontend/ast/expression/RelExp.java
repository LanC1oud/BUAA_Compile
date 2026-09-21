package frontend.ast.expression;

import frontend.token.Token;

/**
 * RelExp -> AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
 */
public class RelExp extends BinaryExp {
    public RelExp(AddExp operand) {
        super(operand);
    }

    public RelExp(RelExp left, Token op, AddExp right) {
        super(left, op, right);
    }

    @Override
    protected String getName() {
        return "<RelExp>";
    }
}
