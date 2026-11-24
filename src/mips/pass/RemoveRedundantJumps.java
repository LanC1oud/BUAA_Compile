package mips.pass;

import mips.MipsModule;
import java.util.ArrayList;

// 删除直接跳到下一标签的跳转及其延迟槽空指令
public final class RemoveRedundantJumps implements MipsPass {
    // 扫描代码段，删除跳到紧邻目标标签的 j/nop
    @Override
    public void run(MipsModule module) {
        var lines = module.getText();
        var result = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("j ") && i + 2 < lines.size()
                    && lines.get(i + 1).trim().equals("nop")
                    && lines.get(i + 2).equals(line.substring(2) + ":")) {
                i++;
            } else {
                result.add(lines.get(i));
            }
        }
        module.replaceText(result);
    }
}
