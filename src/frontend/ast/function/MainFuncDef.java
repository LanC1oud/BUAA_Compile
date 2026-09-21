package frontend.ast.function;

import frontend.ast.ASTNode;
import frontend.ast.block.Block;
import frontend.token.Token;

public class MainFuncDef extends ASTNode {
    private final Token identifier;
    private final Block block;

    public MainFuncDef(Token identifier, Block block) {
        this.identifier = identifier;
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }

    public Token getIdentifier() {
        return identifier;
    }

    @Override
    protected String getName() {
        return "<MainFuncDef>";
    }
}
