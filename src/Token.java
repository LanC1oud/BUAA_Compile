public class Token {
    private final TokenType type;
    private final String value;
    private final int lineNum;

    public Token(TokenType type, String value, int lineNum) {
        this.type = type;
        this.value = value;
        this.lineNum = lineNum;
    }

    public TokenType getType() { return type; }
    public String getValue() { return value; }
    public int getLineNum() { return lineNum; }

    @Override
    public String toString() {
        return String.format("Token{type=%s, value='%s', line=%d}", type, value, lineNum);
    }
}
