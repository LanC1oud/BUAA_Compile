package error;

public enum ErrorType {
    InvalidToken("a");
    
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
