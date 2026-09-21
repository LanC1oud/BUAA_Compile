package frontend;

import error.ErrorType;
import frontend.token.*;
import error.ErrorTable;
import utils.Controller;
import utils.Pair;

import java.io.FileReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Character.*;

public class Lexer {
    private final ErrorTable errors = Controller.errors;
    
    static class Reader {
        private final PushbackReader reader;
        private int lineno = 1;
        private int column = 0;
        private int lastColumn = -1;
        
        public Reader(String source) throws IOException {
            reader = new PushbackReader(new FileReader(source));
        }
        
        public int read() throws IOException {
            int chr = reader.read();
            if (chr == -1) {
                return -1;
            } else if (chr == '\n') {
                lineno++;
                lastColumn = column;
                column = 0;
            } else {
                column++;
            }
            return chr;
        }
        
        public void unread(int chr) throws IOException {
            if (chr == -1) {
                return;
            }
            if (chr == '\n') {
                lineno--;
                column = lastColumn;
            } else {
                column--;
            }
            reader.unread(chr);
        }
        
        public Pair<Integer, Integer> location() {
            return new Pair<>(lineno, column);
        }
    }
    
    private final String source;
    
    private final Reader reader;
    private final TokenStream tokens = new TokenStream();
    
    public Lexer(String source) throws IOException {
        this.source = source;
        reader = new Reader(source);
    }
    
    public Lexer lex() throws IOException {
        int c;
        while ((c = reader.read()) != -1) {
            if (isWhitespace(c)) {
                continue;
            }
            reader.unread(c);
            if (isAsciiLetter(c) || c == '_') {
                readIdentifier();
            } else if (isAsciiDigit(c)) {
                readNumber();
            } else if (c == '"') {
                readString();
            } else if (c == '\'') {
                readCharConst();
            } else if (c == '/') {
                readSlash();
            } else {
                readOp();
            }
        }
        
        return this;
    }
    
    private void readIdentifier() throws IOException {
        StringBuilder identifier = new StringBuilder();
        int c = reader.read();
        Pair<Integer, Integer> start = reader.location();
        while (isAsciiLetter(c) || isAsciiDigit(c) || c == '_') {
            identifier.append((char) c);
            c = reader.read();
        }
        reader.unread(c);
        addToken(
                Keywords.TABLE.getOrDefault(identifier.toString(), TokenType.Ident),
                identifier.toString(), start
        );
    }
    
    private void readNumber() throws IOException {
        int c = reader.read();
        Pair<Integer, Integer> start = reader.location();
        StringBuilder number = new StringBuilder();
        do {
            number.append((char) c);
        } while (isAsciiDigit(c = reader.read()));
        reader.unread(c);
        addToken(TokenType.IntConst, number.toString(), start);
    }
    
    private void readString() throws IOException {
        Pair<Integer, Integer> start = reader.location();
        int c = reader.read();
        StringBuilder str = new StringBuilder();
        while ((c = reader.read()) != '"') {
            if (c == -1) {
                errors.add(ErrorType.InvalidToken, location());
                break;
            }
            if (c == '\\') {
                c = reader.read();
                if (c == -1) {
                    errors.add(ErrorType.InvalidToken, location());
                    break;
                }
                if (!Escapes.TABLE.containsKey((char) c)) {
                    errors.add(ErrorType.InvalidToken, location());
                    str.append('\\').append((char) c);
                } else {
                    str.append(Escapes.TABLE.get((char) c));
                }
            } else {
                str.append((char) c);
            }
        }
        addToken(TokenType.StringConst, str.toString(), start);
    }
    
    private void readCharConst() throws IOException {
        int c = reader.read();
        Pair<Integer, Integer> start = reader.location();
        c = reader.read();
        if (c == '\\') {
            c = reader.read();
            Character escaped = c == -1 ? null : Escapes.TABLE.get((char) c);
            if (escaped == null) {
                // Keep scanning after an invalid escape instead of unboxing
                // null (which used to abort the whole lexer with NPE).
                errors.add(ErrorType.InvalidToken, location());
            } else {
                c = escaped;
            }
        }
        addToken(TokenType.CharConst, String.valueOf((char) c), start);
        reader.read();
    }
    
    private void readSlash() throws IOException {
        int c;
        Pair<Integer, Integer> start = reader.location();
        // The dispatch code leaves the first slash in the pushback reader.
        // Consume it before looking at the second character; otherwise
        // "/*" is mistaken for a line comment starting with the first '/'.
        reader.read();
        c = reader.read();
        if (c == '/') {
            while (c != -1 && c != '\n') {
                c = reader.read();
            }
        } else if (c == '*') {
            while ((c = reader.read()) != -1) {
                int next = reader.read();
                if (c == '*' && next == '/') {
                    break;
                }
                reader.unread(next);
            }
        } else {
            reader.unread(c);
            addToken(TokenType.Div, "/", start);
        }
    }
    
    private void readOp() throws IOException {
        int c = reader.read();
        Pair<Integer, Integer> start = reader.location();
        switch (c) {
            case '|' -> {
                c = reader.read();
                if (c != '|') {
                    errors.add(ErrorType.InvalidToken, new Navigation(start, start));
                    reader.unread(c);
                    addToken(TokenType.Or, "|", start);
                } else {
                    addToken(TokenType.Or, "||", start);
                }
            }
            case '&' -> {
                c = reader.read();
                if (c != '&') {
                    errors.add(ErrorType.InvalidToken, new Navigation(start, start));
                    reader.unread(c);
                    addToken(TokenType.And, "&", start);
                } else {
                    addToken(TokenType.And, "&&", start);
                }
            }
            case '!' -> {
                if ((c = reader.read()) == '=') {
                    addToken(TokenType.Neq, "!=", start);
                } else {
                    reader.unread(c);
                    addToken(TokenType.Not, "!", start);
                }
            }
            case '=' -> {
                if ((c = reader.read()) == '=') {
                    addToken(TokenType.Eq, "==", start);
                } else {
                    reader.unread(c);
                    addToken(TokenType.Assign, "=", start);
                }
            }
            case '<' -> {
                if ((c = reader.read()) == '=') {
                    addToken(TokenType.Leq, "<=", start);
                } else {
                    reader.unread(c);
                    addToken(TokenType.Lt, "<", start);
                }
            }
            case '>' -> {
                if ((c = reader.read()) == '=') {
                    addToken(TokenType.Geq, ">=", start);
                } else {
                    reader.unread(c);
                    addToken(TokenType.Gt, ">", start);
                }
            }
            default -> {
                TokenType type = Symbols.TABLE.get((char) c);
                if (type == null) {
                    errors.add(ErrorType.InvalidToken, location());
                } else {
                    addToken(type, String.valueOf((char) c), start);
                }
            }
        }
    }

    private static boolean isAsciiLetter(int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }

    private static boolean isAsciiDigit(int c) {
        return c >= '0' && c <= '9';
    }
    
    public TokenStream emit() {
        tokens.freeze();
        return tokens;
    }
    
    private void addToken(TokenType type, String value, Pair<Integer, Integer> start) {
        tokens.add(new Token(type, value, new Navigation(start, reader.location())));
    }
    
    private Navigation location() {
        return new Navigation(reader.location(), reader.location());
    }
}
