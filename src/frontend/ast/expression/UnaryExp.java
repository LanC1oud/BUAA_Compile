package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.token.Token;
import frontend.token.TokenType;

// UnaryExp -> PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp
// UnaryExp | '(' BType ')' UnaryExp
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

    // callee name; null unless this is a call
    public Token getIdent() {
        return ident;
    }

    // actual parameters; null when there are none
    public FuncRParams getParams() {
        return params;
    }

    public UnaryOp getOp() {
        return op;
    }

    // int/char token of an explicit cast; null otherwise
    public Token getCastType() {
        return castType;
    }

    public UnaryExp getOperand() {
        return operand;
    }

    // throws UnsupportedOperationException for calls and for logical not
    @Override
    public int calculateConst() {
        return switch (kind) {
            case Primary -> primary.calculateConst();
            case Call -> throw new UnsupportedOperationException(
                    "函数调用 `" + ident.value() + "` 没有编译期值");
            case Unary -> calculateUnary();
            case Cast -> cast(operand.calculateConst(), castType);
        };
    }

    private int calculateUnary() {
        int value = operand.calculateConst();
        return switch (op.getToken().getType()) {
            case Plus -> value;
            case Minus -> -value;
            case Not -> throw new UnsupportedOperationException("常量表达式中不能出现逻辑非");
            default -> throw new UnsupportedOperationException(
                    "一元运算符 " + op.getToken() + " 没有编译期值");
        };
    }

    // explicit cast. char is unsigned char (mod 256), so narrow with & 0xFF, not (byte):
    // (char)255 must yield 255. throws IllegalArgumentException for other targets
    public static int cast(int value, Token castType) {
        if (castType.is(TokenType.Int)) {
            return value;
        }
        if (castType.is(TokenType.Char)) {
            return value & 0xFF;
        }
        throw new IllegalArgumentException("期望 int 或 char 转换，实际是 " + castType);
    }

    @Override
    protected String getName() {
        return "<UnaryExp>";
    }
}