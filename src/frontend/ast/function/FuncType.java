package frontend.ast.function;

import frontend.ast.ASTNode;
import frontend.token.Token;
import frontend.token.TokenType;

/**
 * FuncType -> 'void' | 'int' | 'char'
 */
public class FuncType extends ASTNode {
    private final Token token;

    public FuncType(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }

    public boolean isVoid() {
        return token.is(TokenType.Void);
    }

    public boolean isInt() {
        return token.is(TokenType.Int);
    }

    public boolean isChar() {
        return token.is(TokenType.Char);
    }

    @Override
    protected String getName() {
        return "<FuncType>";
    }
}
