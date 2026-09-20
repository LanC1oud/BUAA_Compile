package frontend.token;

import java.util.Map;

import static java.util.Map.entry;

public final class Escapes {
    private Escapes() {}
    public static final Map<Character, Character> TABLE = Map.ofEntries(
            entry('a', '\u0007'), entry('b', '\b'), entry('f', '\f'),
            entry('n', '\n'), entry('t', '\t'), entry('v', '\u000B'),
            entry('\\', '\\'), entry('\'', '\''), entry('"', '"'),
            entry('0', '\0')
            );
}