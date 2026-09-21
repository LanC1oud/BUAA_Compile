package frontend.token;

import frontend.Lexer;
import frontend.Navigation;

import java.util.Arrays;

public record Token(TokenType type, String value, Navigation location) {

    public Token(TokenType type, String value, Navigation location) {
        this.type = type;
        this.value = value;
        this.location = location;
    }

    public TokenType getType() { return type; }
    public String getValue() { return value; }
    public Navigation getLocation() { return location; }
    
    @Override
    public String toString() {
        String value = escapeValue(this.value);
        if (type == TokenType.StringConst) {
            value = "\"" + value + "\"";
        } else if (type == TokenType.CharConst) {
            value = "'" + value + "'";
        }
        return type + " " + value;
    }
    
    private String escapeValue(String value) {
        value = value.replace("\\", "\\\\");
        for (var entry : Escapes.TABLE.entrySet()) {
            if (entry.getKey() == '\\') continue;
            value = value.replace(String.valueOf(entry.getValue()), "\\" + entry.getKey());
        }
        return value;
    }
    
    public String toDebugString() {
        return this + " " + location;
    }
    
    public boolean is(Token tk) {
        return type == tk.type && value.equals(tk.value);
    }

    public boolean is(TokenType type) {
        return this.type == type;
    }

    public boolean among(TokenType... types) {
        return Arrays.stream(types).anyMatch(this::is);
    }
    
    public boolean equals(Object obj) {
        if (!(obj instanceof Token tk)) {
            return false;
        }
        return this.is(tk);
    }
}
