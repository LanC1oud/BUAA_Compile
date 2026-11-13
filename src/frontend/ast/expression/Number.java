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

    // 读取整数字面量或字符字面量的值
    @Override
    public int calculateConst() {
        return valueOf(token);
    }

    // 把整数字面量或字符记号转为编译期整数值
    public static int valueOf(Token token) {
        if (token.is(TokenType.IntConst)) {
            // The magnitude in -2147483648 is parsed before its unary minus.
            // Retain its i32 bit pattern instead of overflowing Java's signed parser.
            return Integer.parseUnsignedInt(token.value());
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
