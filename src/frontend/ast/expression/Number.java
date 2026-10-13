package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.token.Token;
import frontend.token.TokenType;

// Number -> IntConst | CharConst
public class Number extends ASTNode {
    private final Token token;

    public Number(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }

    @Override
    public int calculateConst() {
        return valueOf(token);
    }

    // throws IllegalArgumentException for any other token
    public static int valueOf(Token token) {
        if (token.is(TokenType.IntConst)) {
            return Integer.parseInt(token.value());
        }
        if (token.is(TokenType.CharConst)) {
            return token.value().isEmpty() ? 0 : token.value().charAt(0);
        }
        throw new IllegalArgumentException(
                "期望 IntConst 或 CharConst，实际是 " + token);
    }

    @Override
    protected String getName() {
        return "<Number>";
    }
}