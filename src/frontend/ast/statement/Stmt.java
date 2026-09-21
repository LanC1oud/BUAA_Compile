package frontend.ast.statement;

import frontend.ast.ASTNode;
import frontend.ast.block.Block;
import frontend.ast.expression.Cond;
import frontend.ast.expression.Exp;
import frontend.ast.expression.LVal;

/**
 * Stmt -> LVal '=' Exp ';'
 * | [Exp] ';'
 * | Block
 * | 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
 * | 'while' '(' Cond ')' Stmt
 * | 'switch' '(' Exp ')' '{' { CaseStmt } '}'
 * | 'break' ';'
 * | 'continue' ';'
 * | 'return' [Exp] ';'
 * | 'printf' '(' StringConst { ',' Exp } ')' ';'
 * <p>
 * Every concrete statement prints the same {@code <Stmt>} marker.
 */
public abstract class Stmt extends ASTNode {
    @Override
    protected String getName() {
        return "<Stmt>";
    }
}
