package frontend.ast.declaration;

import frontend.ast.ASTNode;

/**
 * Decl -> ConstDecl | VarDecl
 * <p>
 * {@code <Decl>} is one of the few non-terminals that must NOT be printed.
 */
public abstract class Decl extends ASTNode {
    @Override
    protected String getName() {
        return "<Decl>";
    }
}
