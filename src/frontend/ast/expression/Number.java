package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.token.Token;
import frontend.token.TokenType;

/**
 * Number -> IntConst | CharConst
 */
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

    /**
     * 读取数字字面量。
     *
     * <p>整型常量在本文法中是十进制；字符常量的转义在词法阶段已经解码，所以它的值就是
     * 字符本身的码位。
     *
     * @param token {@code IntConst} 或 {@code CharConst} 记号
     * @return 字面量的数值
     * @throws IllegalArgumentException 记号不是这两种字面量
     */
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
