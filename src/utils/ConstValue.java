package utils;

import frontend.token.TokenType;

/**
 * Evaluation of a binary operator over two 32-bit constant values, mirroring the arithmetic
 * behaviour of the reference compiler.
 *
 * <p>Division and remainder follow Java's (and C's) truncation-towards-zero rule and throw
 * {@link ArithmeticException} on a zero divisor; callers that must not abort the whole
 * compilation are expected to catch it.
 */
public final class ConstValue {

    private ConstValue() {
    }

    public static int calculate(int left, int right, TokenType op) {
        return switch (op) {
            // AddExp
            case Plus -> left + right;
            case Minus -> left - right;
            // MulExp
            case Mult -> left * right;
            case Div -> left / right;
            case Mod -> left % right;
            // RelExp
            case Lt -> left < right ? 1 : 0;
            case Gt -> left > right ? 1 : 0;
            case Leq -> left <= right ? 1 : 0;
            case Geq -> left >= right ? 1 : 0;
            // EqExp
            case Eq -> left == right ? 1 : 0;
            case Neq -> left != right ? 1 : 0;
            // LAndExp / LOrExp
            case And -> left != 0 && right != 0 ? 1 : 0;
            case Or -> left != 0 || right != 0 ? 1 : 0;
            default -> throw new IllegalArgumentException(
                    "Operator " + op + " is not a binary operator");
        };
    }
}

