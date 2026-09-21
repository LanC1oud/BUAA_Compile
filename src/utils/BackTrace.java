package utils;

import java.util.Stack;

public class BackTrace implements AutoCloseable {

    public interface Traceable {
        Object save();
        void restore(Object state);
    }

    private final Traceable[] objects;
    private final Stack<Object[]> states = new Stack<>();

    public BackTrace(Traceable... objects) {
        this.objects = objects;
    }

    public BackTrace save() {
        Object[] state = new Object[objects.length];
        for (int i = 0; i < objects.length; i++) {
            state[i] = objects[i].save();
        }
        states.push(state);
        return this;
    }

    public void restore() {
        Object[] state = states.pop();
        for (int i = 0; i < objects.length; i++) {
            objects[i].restore(state[i]);
        }
    }

    @Override
    public void close() {
        restore();
    }
}
