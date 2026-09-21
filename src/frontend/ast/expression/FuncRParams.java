package frontend.ast.expression;

import frontend.ast.ASTNode;
import java.util.List;

/**
 * FuncRParams -> Exp { ',' Exp }
 */
public class FuncRParams extends ASTNode {
    private final List<Exp> params;

    public FuncRParams(List<Exp> params) {
        this.params = params;
    }

    public List<Exp> getParams() {
        return params;
    }

    @Override
    protected String getName() {
        return "<FuncRParams>";
    }
}
