package frontend.token;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class TokenStream {
    private final List<Token> tokens = new LinkedList<>();
    private int index = 0;
    private boolean frozen = false;
    
    public void freeze() {
        frozen = true;
    }
    
    private void assertFrozen() {
        if (!frozen) {
            throw new IllegalStateException("Cannot access token stream before freezing");
        }
    }
    
    private void assertNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("Cannot access token stream after freezing");
        }
    }
    
    public void add(Token token) {
        assertNotFrozen();
        tokens.add(token);
    }
    
    public Token next() {
        assertFrozen();
        if (index >= tokens.size()) {
            return null;
        }
        return tokens.get(index++);
    }
    
    public Token consume(TokenType type) {
        assertFrozen();
        Token next = peek();
        if (next == null || !next.is(type)) {
            throw new IllegalStateException("Expecting " + type + " but got " + next);
        }
        return next();
    }
    
    public Token checkConsume(TokenType... types) {
        assertFrozen();
        if (!among(types)) {
            return null;
        }
        return next();
    }
    
    public boolean among(TokenType... types) {
        assertFrozen();
        return peek().among(types);
    }
    
    public Token peek(int next) {
        assertFrozen();
        int pos = index + next;
        if (pos < 0 || pos >= tokens.size()) {
            return null;
        }
        return tokens.get(pos);
    }
    
    public Token peek() { return peek(0);}
    
    public boolean eof() { return peek(0) == null;}
    
    public String toString() {
        return tokens.stream().map(Token::toString).collect(Collectors.joining("\n"));
    }
    
    public String toDebugString() {
        return tokens.stream().map(Token::toDebugString).collect(Collectors.joining("\n"));
    }
}
