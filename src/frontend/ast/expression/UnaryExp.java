package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.token.Token;
import frontend.token.TokenType;

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

    /**
     * 在编译期求出该表达式的值。
     *
     * @return 表达式的值
     * @throws UnsupportedOperationException 函数调用没有编译期值，或常量表达式中出现逻辑非
     */
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

    /** 对操作数施加一元运算符。 */
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

    /**
     * 显式类型转换。
     *
     * <p>2026 文法里 {@code char} 是 <b>unsigned char</b>（取值 0~255，溢出模 256），
     * 所以窄化到 char 用 {@code value & 0xFF}，而<b>不是</b> C 的 {@code (byte)}：
     * {@code (char)255} 应当得到 {@code 255}，用有符号字节会错成 {@code -1}。
     * 参考实现按有符号处理，这里必须改。
     *
     * @param value 待转换的值
     * @param castType 转换目标记号（{@code int} 或 {@code char}）
     * @return 转换后的值
     * @throws IllegalArgumentException 目标既不是 int 也不是 char
     */
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
