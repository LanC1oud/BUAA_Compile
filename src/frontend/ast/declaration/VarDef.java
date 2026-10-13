package frontend.ast.declaration;

import frontend.ast.ASTNode;
import frontend.ast.expression.ConstExp;
import frontend.token.Token;

// VarDef -> Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal
public class VarDef extends ASTNode {
    private final Token ident;
    private final ConstExp length;
    private final InitVal initVal;

    public VarDef(Token ident, ConstExp length, InitVal initVal) {
        this.ident = ident;
        this.length = length;
        this.initVal = initVal;
    }

    public Token getIdent() {
        return ident;
    }

    // array length expression; null for a scalar
    public ConstExp getLength() {
        return length;
    }

    public boolean isArray() {
        return length != null;
    }

    // initial value; null when there is no =
    public InitVal getInitVal() {
        return initVal;
    }

    public boolean hasInitVal() {
        return initVal != null;
    }

    @Override
    protected String getName() {
        return "<VarDef>";
    }
}