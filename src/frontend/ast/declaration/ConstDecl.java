package frontend.ast.declaration;

import frontend.token.Token;
import java.util.List;

/**
 * ConstDecl -> 'const' BType ConstDef { ',' ConstDef } ';'
 */
public class ConstDecl extends Decl {
    private final Token constToken;
    private final Token btype;
    private final List<ConstDef> defs;

    public ConstDecl(Token constToken, Token btype, List<ConstDef> defs) {
        this.constToken = constToken;
        this.btype = btype;
        this.defs = defs;
    }

    public Token getConstToken() {
        return constToken;
    }

    public Token getBType() {
        return btype;
    }

    public List<ConstDef> getDefs() {
        return defs;
    }

    @Override
    protected String getName() {
        return "<ConstDecl>";
    }
}
