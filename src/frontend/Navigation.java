package frontend;

import utils.Pair;

public record Navigation(Pair<Integer, Integer> start, Pair<Integer, Integer> end) {
    
    public String toString() {
        return String.format("%d:%d-%d:%d", start.first(), start.second(), end.first(), end.second());
    }
}
