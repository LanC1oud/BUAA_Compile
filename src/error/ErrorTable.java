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
        errors.add(new ErrorEntry(type, navigation));
    }
    
    /**
     * 清空所有已记录的错误，让这张表可以重新用于下一次编译。
     *
     * <p>给编译器前端外壳（{@code ui/}）复用 {@code Controller.errors} 用：
     * 那个字段是 {@code static final}，替换引用会被 JVM 拒绝，所以只能原地清空。
     */
    public void clear() {
        errors.clear();
    }
    
    public boolean noError() {
        return errors.isEmpty();
    }
    
    /** @return 已记录的错误条数。 */
    public int size() {
        return errors.size();
    }
    
    /**
     * 按行列排好序的错误条目，只读。
     *
     * <p>给编译器前端外壳（{@code ui/}）用：界面需要拿 {@code ErrorType} 枚举名
     * 拼出更可读的提示，而又<b>不能动 {@link #toString()}</b>——那个格式直接决定
     * {@code error.txt} 的内容，是评测要的格式。
     */
    public List<ErrorEntry> entries() {
        return sorted().toList();
    }
    
    /** 统一的排序口径：先按行，再按列。{@link #toString()} 和 {@link #entries()} 共用。 */
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
