package frontend.symbol.type;

final public class SymbolQualifier {
    private boolean isConst = false;
    
    public void setConst() {
        isConst = true;
    }
    
    public boolean isConst() {
        return isConst;
    }
}
