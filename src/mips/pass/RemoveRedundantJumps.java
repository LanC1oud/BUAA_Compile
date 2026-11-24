package mips.pass;

import mips.MipsModule;
import java.util.ArrayList;

/** 删除直接跳到下一标签的 j/nop，保持延迟槽开关下行为一致。 */
public final class RemoveRedundantJumps implements MipsPass {
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
