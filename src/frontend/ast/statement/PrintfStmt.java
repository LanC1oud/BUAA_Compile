package frontend.ast.statement;

import frontend.ast.expression.Exp;
import frontend.token.Token;
import java.util.List;

// Stmt -> 'printf' '(' StringConst { ',' Exp } ')' ';'
public class PrintfStmt extends Stmt {
    private final Token printfToken;
    private final Token format;
    private final List<Exp> args;

    public PrintfStmt(Token printfToken, Token format, List<Exp> args) {
        this.printfToken = printfToken;
        this.format = format;
        this.args = args;
    }

    public Token getPrintfToken() {
        return printfToken;
    }

    public Token getFormat() {
        return format;
    }

    public List<Exp> getArgs() {
        return args;
    }
}