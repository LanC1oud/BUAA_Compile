package error;

public enum ErrorType {
    InvalidToken("a"),
    DuplicatedDeclaration("b"),
    UndefinedReference("c"),
    MismatchedParameterCount("d"),
    MismatchedParameterType("e"),
    MismatchedReturnType("f"),
    MissingReturnStatement("g"),
    AssignToConstant("h"),
    ExpectedSemicolon("i"),
    ExpectedRParen("j"),
    ExpectedRBracket("k"),
    MismatchedFormatArgument("l"),
    InvalidLoopControl("m"),
    DuplicatedCaseLabel("n"),
    ;

    final private String type;
    
    // 保存评测使用的单字母错误码
    ErrorType(String type) {
        this.type = type;
    }
    
    // 取单字母错误码
    public String toString() {
        return type;
    }
    
    // 同时显示错误类别名与错误码
    public String toDebugString() {
        return String.format("Error(%s, %s)", this.name(), type);
    }

    // 同行诊断优先级：词法高于语法，语法高于语义
    public int priority() {
        return switch (this) {
            case InvalidToken -> 30;
            case ExpectedSemicolon, ExpectedRParen, ExpectedRBracket -> 20;
            default -> 10;
        };
    }
}
