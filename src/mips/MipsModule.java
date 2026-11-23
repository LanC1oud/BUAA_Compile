package mips;

import java.util.ArrayList;
import java.util.List;

// 分别保存 MIPS 数据段和代码段
public final class MipsModule {
    private final List<String> data = new ArrayList<>();
    private final List<String> text = new ArrayList<>();

    // 向数据段追加一行文本
    void data(String line) { data.add(line); }

    // 向代码段追加一行文本
    void text(String line) { text.add(line); }

    // 取得数据段的只读快照
    public List<String> getData() { return List.copyOf(data); }

    // 取得代码段的只读快照
    public List<String> getText() { return List.copyOf(text); }

    // 用优化后的文本替换整个代码段
    public void replaceText(List<String> lines) {
        text.clear();
        text.addAll(lines);
    }

    // 将模块转换为完整汇编文本
    @Override
    public String toString() { return new MipsPrinter().print(this); }
}
