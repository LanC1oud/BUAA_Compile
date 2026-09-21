package frontend.ast.statement;

import frontend.ast.ASTNode;
import frontend.ast.expression.Number;
import frontend.token.Token;
import java.util.List;

/**
 * CaseStmt -> 'case' Number ':' { Stmt } | 'default' ':' { Stmt }
 */
public class CaseStmt extends ASTNode {
    private final Token keyword;
    private final Number number;
    private final List<Stmt> stmts;

    public CaseStmt(Token keyword, Number number, List<Stmt> stmts) {
        this.keyword = keyword;
        this.number = number;
        this.stmts = stmts;
    }

    /** @return the {@code case} or {@code default} keyword token. */
    public Token getKeyword() {
        return keyword;
    }

    /** @return the label value, or {@code null} for the {@code default} branch. */
    public Number getNumber() {
        return number;
    }

    public boolean isDefault() {
        return number == null;
    }

    public List<Stmt> getStmts() {
        return stmts;
    }

    @Override
    protected String getName() {
        return "<CaseStmt>";
    }
}
