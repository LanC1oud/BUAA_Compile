package frontend.symbol.type;

final public class SymbolQualifier {
    private boolean isConst = false;

    // 登记 const 限定
    public void setConst() {
        isConst = true;
    }

    // 读取 const 限定
    public boolean isConst() {
        return isConst;
    }
}
