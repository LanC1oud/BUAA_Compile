package mips;

import java.util.ArrayList;
import java.util.List;

/** 汇编模块：数据段与文本段分开保存，便于后续增加机器码优化。 */
public final class MipsModule {
    private final List<String> data = new ArrayList<>();
    private final List<String> text = new ArrayList<>();

    void data(String line) { data.add(line); }
    void text(String line) { text.add(line); }
    public List<String> getData() { return List.copyOf(data); }
    public List<String> getText() { return List.copyOf(text); }
    public void replaceText(List<String> lines) {
        text.clear();
        text.addAll(lines);
    }

    @Override
    public String toString() { return new MipsPrinter().print(this); }
}
