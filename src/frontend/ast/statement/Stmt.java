package frontend.ast.statement;

import frontend.ast.ASTNode;
import frontend.ast.block.Block;
import frontend.ast.expression.Cond;
import frontend.ast.expression.Exp;
import frontend.ast.expression.LVal;

// Stmt -> LVal '=' Exp ';' | [Exp] ';' | Block | 'if' | 'while' | 'switch'
// | 'break' ';' | 'continue' ';' | 'return' [Exp] ';' | 'printf' ...
public abstract class Stmt extends ASTNode {
    @Override
    protected String getName() {
        return "<Stmt>";
    }
}