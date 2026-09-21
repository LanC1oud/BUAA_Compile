package frontend.ast.declaration;

import frontend.ast.ASTNode;
import frontend.ast.expression.ConstExp;
import frontend.token.Token;

/**
 * ConstDef -> Ident [ '[' ConstExp ']' ] '=' ConstInitVal
 */
public class ConstDef extends ASTNode {
    private final Token ident;
    private final ConstExp length;
    private final ConstInitVal initVal;

    public ConstDef(Token ident, ConstExp length, ConstInitVal initVal) {
        this.ident = ident;
        this.length = length;
        this.initVal = initVal;
    }

    public Token getIdent() {
        return ident;
    }

    /** @return the array-length expression, or {@code null} for a scalar. */
    public ConstExp getLength() {
        return length;
    }

    public boolean isArray() {
        return length != null;
    }

    public ConstInitVal getInitVal() {
        return initVal;
    }

    @Override
    protected String getName() {
        return "<ConstDef>";
    }
}
