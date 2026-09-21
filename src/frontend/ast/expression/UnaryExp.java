package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.token.Token;

/**
 * UnaryExp -> PrimaryExp
 * | Ident '(' [FuncRParams] ')'
 * | UnaryOp UnaryExp
 * | '(' BType ')' UnaryExp
 */
public class UnaryExp extends ASTNode {
    public enum Kind {
        Primary, Call, Unary, Cast
    }

    private final Kind kind;
    private final PrimaryExp primary;
    private final Token ident;
    private final FuncRParams params;
    private final UnaryOp op;
    private final Token castType;
    private final UnaryExp operand;

    private UnaryExp(Kind kind, PrimaryExp primary, Token ident, FuncRParams params,
                     UnaryOp op, Token castType, UnaryExp operand) {
        this.kind = kind;
        this.primary = primary;
        this.ident = ident;
        this.params = params;
        this.op = op;
        this.castType = castType;
        this.operand = operand;
    }

    public static UnaryExp primary(PrimaryExp primary) {
        return new UnaryExp(Kind.Primary, primary, null, null, null, null, null);
    }

    public static UnaryExp call(Token ident, FuncRParams params) {
        return new UnaryExp(Kind.Call, null, ident, params, null, null, null);
    }

    public static UnaryExp unary(UnaryOp op, UnaryExp operand) {
        return new UnaryExp(Kind.Unary, null, null, null, op, null, operand);
    }

    public static UnaryExp cast(Token castType, UnaryExp operand) {
        return new UnaryExp(Kind.Cast, null, null, null, null, castType, operand);
    }

    public Kind getKind() {
        return kind;
    }

    public PrimaryExp getPrimary() {
        return primary;
    }

    /** @return the callee name token for a function call, otherwise {@code null}. */
    public Token getIdent() {
        return ident;
    }

    /** @return the actual parameters, or {@code null} when the call has none. */
    public FuncRParams getParams() {
        return params;
    }

    public UnaryOp getOp() {
        return op;
    }

    /** @return the {@code int}/{@code char} token of an explicit cast, otherwise {@code null}. */
    public Token getCastType() {
        return castType;
    }

    /** @return the operand of a unary operation or of an explicit cast. */
    public UnaryExp getOperand() {
        return operand;
    }

    @Override
    protected String getName() {
        return "<UnaryExp>";
    }
}
