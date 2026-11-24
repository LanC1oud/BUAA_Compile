package mips;

/** 输出课程 MARS 可直接汇编的文本，不使用宏。 */
public final class MipsPrinter {
    public String print(MipsModule module) {
        return "# SysY 2026 / MIPS32 / MARS 4.6\n.data\n"
                + String.join("\n", module.getData())
                + "\n\n.text\n.globl main\n"
                + String.join("\n", module.getText()) + "\n";
    }
}
