package frontend.token;

import java.util.Map;

import static java.util.Map.entry;

public final class Symbols {
    private Symbols() {}
    public static final Map<Character, TokenType> TABLE = Map.ofEntries(
            entry('+', TokenType.Plus), entry('-', TokenType.Minus),
            entry('*', TokenType.Mult), entry('/', TokenType.Div), entry('%', TokenType.Mod),
            entry('{', TokenType.LBrace), entry('}', TokenType.RBrace),
            entry('[', TokenType.LBracket), entry(']', TokenType.RBracket),
            entry('(', TokenType.LParen), entry(')', TokenType.RParen),
            entry(';', TokenType.Semicolon), entry(',', TokenType.Comma),
            entry(':', TokenType.Colon)
            );
}