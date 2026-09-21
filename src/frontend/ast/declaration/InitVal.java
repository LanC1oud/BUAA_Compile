package frontend.ast.declaration;

import frontend.ast.ASTNode;
import frontend.ast.expression.Exp;
import frontend.token.Token;
import java.util.List;

/**
 * InitVal -> Exp | '{' [ Exp { ',' Exp } ] '}' | StringConst
 */
public class InitVal extends ASTNode {
    public enum Kind {
        Exp, List, Str
    }

    private final Kind kind;
    private final Exp exp;
    private final List<Exp> elements;
    private final Token string;

    private InitVal(Kind kind, Exp exp, List<Exp> elements, Token string) {
        this.kind = kind;
        this.exp = exp;
        this.elements = elements;
        this.string = string;
    }

    public static InitVal exp(Exp exp) {
        return new InitVal(Kind.Exp, exp, null, null);
    }

    public static InitVal list(List<Exp> elements) {
        return new InitVal(Kind.List, null, elements, null);
    }

    public static InitVal string(Token string) {
        return new InitVal(Kind.Str, null, null, string);
    }

    public Kind getKind() {
        return kind;
    }

    public Exp getExp() {
        return exp;
    }

    public List<Exp> getElements() {
        return elements;
    }

    public Token getString() {
        return string;
    }

    @Override
    protected String getName() {
        return "<InitVal>";
    }
}
