package mips;

// 将汇编模块输出为课程 MARS 可用的文本
public final class MipsPrinter {
    // 拼接数据段、代码段和入口声明
    public String print(MipsModule module) {
        return "# SysY 2026 / MIPS32 / MARS 4.6\n.data\n"
                + String.join("\n", module.getData())
                + "\n\n.text\n.globl main\n"
                + String.join("\n", module.getText()) + "\n";
    }
}
