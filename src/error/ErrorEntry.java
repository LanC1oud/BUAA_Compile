package error;

import frontend.Navigation;

public class ErrorEntry {
    private final ErrorType type;
    private final Navigation navigation;
    
    public ErrorEntry(ErrorType type, Navigation navigation) {
        this.type = type;
        this.navigation = navigation;
    }
    
    public int getLine() { return navigation.start().first(); }
    public int getColumn() { return navigation.start().second(); }
    
    /**
     * @return 错误类型（枚举）。
     *
     * <p>只读访问器，给编译器前端外壳（{@code ui/}）拼可读提示用：
     * 界面上要显示 {@code ErrorType} 的<b>枚举名</b>（如 {@code ExpectedSemicolon}），
     * 而 {@link #toString()} 只给单字母错误码。加这个访问器不影响
     * {@link #toString()} 的输出，因此 {@code error.txt} 的内容一个字符都不会变。
     */
    public ErrorType getType() { return type; }
    
    public String toString() {
        return String.format("%s %s", navigation.start().first(), type);
    }
    
    public String toDebugString() {
        return String.format("%s @%s", type.toDebugString(), navigation);
    }
}
