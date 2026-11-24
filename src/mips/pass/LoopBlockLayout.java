package mips.pass;

import mips.MipsModule;
import java.util.*;

/** Place a simple loop test after its body: one entry jump replaces one jump per iteration. */
public final class LoopBlockLayout implements MipsPass {
    private String label(String line) { String s = line.trim(); int n = s.indexOf(':'); return n < 0 ? null : s.substring(0, n); }
    @Override public void run(MipsModule module) {
        for (int round = 0; round < 32; round++) {
            List<String> lines = module.getText(); Map<String, Integer> labels = new LinkedHashMap<>();
            for (int n = 0; n < lines.size(); n++) { String name = label(lines.get(n)); if (name != null) labels.put(name, n); }
            boolean changed = false;
            for (var entry : labels.entrySet()) {
                int start = entry.getValue(), body = start + 1;
                while (body < lines.size() && label(lines.get(body)) == null) body++;
                if (body >= lines.size() || body < start + 3) continue;
                String condition = lines.get(body - 2).trim();
                if (!(condition.startsWith("beq ") || condition.startsWith("bne ")) || !lines.get(body - 1).trim().equals("nop")) continue;
                String exitLabel = condition.substring(condition.lastIndexOf(',') + 1).trim();
                Integer exit = labels.get(exitLabel);
                if (exit == null || exit <= body + 2 || !lines.get(exit - 2).trim().equals("j " + entry.getKey())
                        || !lines.get(exit - 1).trim().equals("nop")) continue;
                // No outside edge may enter the relocated body, except through this header.
                boolean safe = true;
                for (int n = 0; n < lines.size(); n++) if (n < start || n >= exit) {
                    String s = lines.get(n).trim();
                    if (s.startsWith("j ") || s.startsWith("beq ") || s.startsWith("bne ")) {
                        String target = s.substring(s.startsWith("j ") ? 2 : s.lastIndexOf(',') + 1).trim();
                        Integer index = labels.get(target);
                        if (index != null && index >= body && index < exit) { safe = false; break; }
                    }
                }
                if (!safe) continue;
                List<String> result = new ArrayList<>(lines.subList(0, start));
                result.add("    j " + entry.getKey()); result.add("    nop");
                result.addAll(lines.subList(body, exit - 2));
                result.addAll(lines.subList(start, body - 2));
                if (exit - body < 8000) {
                    String inverted = condition.startsWith("beq") ? "bne" : "beq";
                    result.add("    " + inverted + condition.substring(3, condition.lastIndexOf(',') + 1) + " " + label(lines.get(body)));
                    result.add("    nop");
                } else {
                    result.add(lines.get(body - 2)); result.add("    nop");
                    result.add("    j " + label(lines.get(body))); result.add("    nop");
                }
                result.addAll(lines.subList(exit, lines.size())); module.replaceText(result);
                changed = true; break;
            }
            if (!changed) break;
        }
    }
}
