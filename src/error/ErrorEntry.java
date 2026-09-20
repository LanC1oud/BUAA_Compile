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
    
    public String toString() {
        return String.format("%s %s", navigation.start().first(), type);
    }
    
    public String toDebugString() {
        return String.format("%s @%s", type.toDebugString(), navigation);
    }
}
