package error;

import frontend.Navigation;
import utils.BackTrace;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class ErrorTable implements BackTrace.Traceable {
    private final List<ErrorEntry> errors;
    
    public ErrorTable() {
        this.errors = new LinkedList<>();
    }
    
    public void add(ErrorType type, Navigation navigation) {
        errors.add(new ErrorEntry(type, navigation));
    }
    
    public boolean noError() {
        return errors.isEmpty();
    }
    
    @Override
    public String toString() {
        return errors.stream()
                .sorted(Comparator
                        .comparingInt(ErrorEntry::getLine)
                        .thenComparingInt(ErrorEntry::getColumn))
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
