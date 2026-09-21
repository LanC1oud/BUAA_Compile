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
    
    ErrorType(String type) {
        this.type = type;
    }
    
    public String toString() {
        return type;
    }
    
    public String toDebugString() {
        return String.format("Error(%s, %s)", this.name(), type);
    }
}
