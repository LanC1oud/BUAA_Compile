package frontend.ast.function;

import frontend.ast.ASTNode;
import java.util.List;

/**
 * FuncFParams -> FuncFParam { ',' FuncFParam }
 */
public class FuncFormalParams extends ASTNode {
    private final List<FuncFormalParam> params;

    public FuncFormalParams(List<FuncFormalParam> params) {
        this.params = params;
    }

    public List<FuncFormalParam> getParams() {
        return params;
    }

    @Override
    protected String getName() {
        return "<FuncFParams>";
    }
}
