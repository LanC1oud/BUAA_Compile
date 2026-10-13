package frontend.ast.declaration;

import frontend.ast.ASTNode;

// Decl -> ConstDecl | VarDecl. <Decl> must NOT be printed.
public abstract class Decl extends ASTNode {
    @Override
    protected String getName() {
        return "<Decl>";
    }
}
