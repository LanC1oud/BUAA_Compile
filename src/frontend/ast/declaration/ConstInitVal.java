package frontend.ast.declaration;

import frontend.ast.ASTNode;
import frontend.ast.expression.ConstExp;
import frontend.ast.expression.Exp;
import frontend.token.Token;
import java.util.List;

/**
 * ConstInitVal -> ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}' | StringConst
 */
public class ConstInitVal extends ASTNode {
    public enum Kind {
        Exp, List, Str
    }

    private final Kind kind;
    private final ConstExp exp;
    private final List<ConstExp> elements;
    private final Token string;

    private ConstInitVal(Kind kind, ConstExp exp, List<ConstExp> elements, Token string) {
        this.kind = kind;
        this.exp = exp;
        this.elements = elements;
        this.string = string;
    }

    public static ConstInitVal exp(ConstExp exp) {
        return new ConstInitVal(Kind.Exp, exp, null, null);
    }

    public static ConstInitVal list(List<ConstExp> elements) {
        return new ConstInitVal(Kind.List, null, elements, null);
    }

    public static ConstInitVal string(Token string) {
        return new ConstInitVal(Kind.Str, null, null, string);
    }

    public Kind getKind() {
        return kind;
    }

    public ConstExp getExp() {
        return exp;
    }

    public List<ConstExp> getElements() {
        return elements;
    }

    public Token getString() {
        return string;
    }

    @Override
    protected String getName() {
        return "<ConstInitVal>";
    }
}
