package frontend.ast.block;

import frontend.ast.ASTNode;
import frontend.token.Token;
import java.util.LinkedList;
import java.util.List;

public class Block extends ASTNode {
    private final List<BlockItem> blocks;
    private Token endToken;

    public Block() {
        this.blocks = new LinkedList<>();
    }

    public void addBlock(BlockItem block) {
        blocks.add(block);
    }

    public List<BlockItem> getItems() {
        return blocks;
    }

    public void setEndToken(Token endToken) {
        this.endToken = endToken;
    }

    public Token getEndToken() {
        return endToken;
    }

    @Override
    protected String getName() {
        return "<Block>";
    }
}
