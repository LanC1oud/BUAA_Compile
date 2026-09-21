package frontend.ast.statement;

import frontend.ast.block.Block;

/**
 * Stmt -> Block
 */
public class BlockStmt extends Stmt {
    private final Block block;

    public BlockStmt(Block block) {
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }
}
