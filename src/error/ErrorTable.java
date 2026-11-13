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
    
    // 初始化本次编译共用的诊断列表
    public ErrorTable() {
        this.errors = new LinkedList<>();
    }

    // 记录诊断，同一行保留优先级最高的一条
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

    // 清空本次编译的诊断
    public void clear() {
        errors.clear();
    }
    
    // 判断当前是否没有诊断
    public boolean noError() {
        return errors.isEmpty();
    }

    // 取去重后的诊断条数
    public int size() {
        return errors.size();
    }

    // 返回按位置排序的诊断列表
    public List<ErrorEntry> entries() {
        return sorted().toList();
    }

    // 按行号和列号排序
    private Stream<ErrorEntry> sorted() {
        return errors.stream()
                .sorted(Comparator
                        .comparingInt(ErrorEntry::getLine)
                        .thenComparingInt(ErrorEntry::getColumn));
    }
    
    // 按行输出「行号 错误码」
    @Override
    public String toString() {
        return sorted()
                .map(ErrorEntry::toString)
                .collect(Collectors.joining("\n"));
    }
    
    // 显示完整诊断位置和错误总数
    public String toDebugString() {
        if (errors.isEmpty()) {
            return "(no errors)";
        }
        return errors.stream()
                .map(ErrorEntry::toDebugString)
                .collect(Collectors.joining("\n"))
                + "\n(total " + errors.size() + " error(s))";
    }

    // 为试探解析保存当前错误条数
    @Override
    public Object save() {
        return errors.size();
    }

    // 移除试探解析期间追加的错误
    @Override
    public void restore(Object state) {
        int size = (int) state;
        while (errors.size() > size) {
            errors.remove(errors.size() - 1);
        }
    }
}
