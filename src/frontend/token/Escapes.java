package frontend.token;

import java.util.Map;

import static java.util.Map.entry;

public final class Escapes {
    private Escapes() {}
    public static final Map<Character, Character> TABLE = Map.ofEntries(
            entry('n', '\n'), entry('t', '\t'), entry('r', '\r'),
            entry('0', '\0'), entry('\\', '\\'), entry('\'', '\'')
            );
}
