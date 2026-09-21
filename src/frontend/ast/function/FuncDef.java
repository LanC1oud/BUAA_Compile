package frontend.ast.function;

import frontend.ast.ASTNode;
import frontend.ast.block.Block;
import frontend.token.Token;

/**
 * FuncDef -> FuncType Ident '(' [FuncFParams] ')' Block
 */
public class FuncDef extends ASTNode {
    private final FuncType funcType;
    private final Token ident;
    private final FuncFormalParams params;
    private final Block block;

    public FuncDef(FuncType funcType, Token ident, FuncFormalParams params, Block block) {
        this.funcType = funcType;
        this.ident = ident;
        this.params = params;
        this.block = block;
    }

    public FuncType getFuncType() {
        return funcType;
    }

    public Token getIdent() {
        return ident;
    }

    /** @return the formal parameter list, or {@code null} when the function takes no parameters. */
    public FuncFormalParams getParams() {
        return params;
    }

    public Block getBlock() {
        return block;
    }

    @Override
    protected String getName() {
        return "<FuncDef>";
    }
}
