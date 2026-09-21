package frontend.ast.block;

import frontend.ast.ASTNode;
import frontend.ast.declaration.Decl;
import frontend.ast.statement.Stmt;

public class BlockItem extends ASTNode {
    public enum Type {
        Decl, Stmt
    }

    private final Type type;
    private final Object item;

    public BlockItem(Decl decl) {
        this.type = Type.Decl;
        this.item = decl;
    }

    public BlockItem(Stmt stmt) {
        this.type = Type.Stmt;
        this.item = stmt;
    }

    public Type getType() {
        return type;
    }

    public Object getItem() {
        return item;
    }

    @Override
    protected String getName() {
        return "<BlockItem>";
    }
}
