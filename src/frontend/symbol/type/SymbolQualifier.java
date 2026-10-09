package frontend.symbol.type;

/**
 * 类型限定符，目前只支持 {@code const}。
 *
 * <p>它挂在 {@link frontend.symbol.Symbol} 上而不是类型上：{@code const int} 和 {@code int}
 * 是同一种类型，区别只在符号自身的属性。所以符号表输出时 {@code Const} 前缀要由符号来拼，
 * 这也是 {@link SymbolType#displayName(boolean, boolean)} 需要一个参数的原因。
 */
final public class SymbolQualifier {
    private boolean isConst = false;

    public void setConst() {
        isConst = true;
    }

    public boolean isConst() {
        return isConst;
    }
}
