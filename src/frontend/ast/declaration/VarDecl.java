package frontend.ast.declaration;

import frontend.token.Token;
import java.util.List;

/**
 * VarDecl -> [ 'static' ] BType VarDef { ',' VarDef } ';'
 */
public class VarDecl extends Decl {
    private final Token staticToken;
    private final Token btype;
    private final List<VarDef> defs;

    public VarDecl(Token staticToken, Token btype, List<VarDef> defs) {
        this.staticToken = staticToken;
        this.btype = btype;
        this.defs = defs;
    }

    public Token getStaticToken() {
        return staticToken;
    }

    public boolean isStatic() {
        return staticToken != null;
    }

    public Token getBType() {
        return btype;
    }

    public List<VarDef> getDefs() {
        return defs;
    }

    @Override
    protected String getName() {
        return "<VarDecl>";
    }
}
