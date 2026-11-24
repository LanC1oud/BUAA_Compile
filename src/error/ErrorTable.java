package error;

import frontend.Navigation;
import utils.BackTrace;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ErrorTable implements BackTrace.Traceable {
    private final List<ErrorEntry> errors;
    
    public ErrorTable() {
        this.errors = new LinkedList<>();
    }

    public void add(ErrorType type, Navigation navigation) {
        ErrorEntry entry = new ErrorEntry(type, navigation);
        for (int i = 0; i < errors.size(); i++) {
            ErrorEntry existing = errors.get(i);
            if (existing.getLine() != entry.getLine()) {
                continue;
            }
            // 同一行已有一条：只留下优先级更高的那条
            if (entry.getPriority() > existing.getPriority()) {
                errors.set(i, entry);
            }
            return;
        }
        errors.add(entry);
    }

    public void clear() {
        errors.clear();
    }
    
    public boolean noError() {
        return errors.isEmpty();
    }

    public int size() {
        return errors.size();
    }

    public List<ErrorEntry> entries() {
        return sorted().toList();
    }

    private Stream<ErrorEntry> sorted() {
        return errors.stream()
                .sorted(Comparator
                        .comparingInt(ErrorEntry::getLine)
                        .thenComparingInt(ErrorEntry::getColumn));
    }
    
    @Override
    public String toString() {
        return sorted()
                .map(ErrorEntry::toString)
                .collect(Collectors.joining("\n"));
    }
    
    public String toDebugString() {
        if (errors.isEmpty()) {
            return "(no errors)";
        }
        return errors.stream()
                .map(ErrorEntry::toDebugString)
                .collect(Collectors.joining("\n"))
                + "\n(total " + errors.size() + " error(s))";
    }

    @Override
    public Object save() {
        return errors.size();
    }

    @Override
    public void restore(Object state) {
        int size = (int) state;
        while (errors.size() > size) {
            errors.remove(errors.size() - 1);
        }
    }
}
