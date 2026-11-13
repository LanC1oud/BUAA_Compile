package error;

import frontend.Navigation;

public class ErrorEntry {
    private final ErrorType type;
    private final Navigation navigation;
    
    // 将诊断类别与源码位置配对
    public ErrorEntry(ErrorType type, Navigation navigation) {
        this.type = type;
        this.navigation = navigation;
    }
    
    // 取出错记号的起始行号
    public int getLine() { return navigation.start().first(); }
    // 取出错记号的起始列号
    public int getColumn() { return navigation.start().second(); }

    // 取错误类别的优先级，用于同行取舍
    public int getPriority() { return type.priority(); }

    // 取诊断类别
    public ErrorType getType() { return type; }
    
    // 生成评测要求的「行号 错误码」
    public String toString() {
        return String.format("%s %s", navigation.start().first(), type);
    }
    
    // 显示诊断类别及完整位置
    public String toDebugString() {
        return String.format("%s @%s", type.toDebugString(), navigation);
    }
}
