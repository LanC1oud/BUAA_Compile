package mips.pass;

import mips.MipsModule;
import java.util.*;

/** Delay-slot-safe jump threading, unreachable assembly removal and fallthrough cleanup. */
public final class MipsPeephole implements MipsPass {
    private String label(String line) {
        String text = line.trim(); int colon = text.indexOf(':');
        return colon < 0 ? null : text.substring(0, colon);
    }
    @Override public void run(MipsModule module) {
        for (int round = 0; round < 4; round++) {
            List<String> lines = module.getText();
            Map<String, Integer> labels = new LinkedHashMap<>();
            for (int n = 0; n < lines.size(); n++) { String name = label(lines.get(n)); if (name != null) labels.put(name, n); }
            Map<String, String> forwarding = new HashMap<>();
            for (var entry : labels.entrySet()) {
                int n = entry.getValue() + 1;
                while (n < lines.size() && label(lines.get(n)) != null) n++;
                if (n < lines.size() && lines.get(n).trim().startsWith("j ")) forwarding.put(entry.getKey(), lines.get(n).trim().substring(2));
            }
            List<String> threaded = new ArrayList<>();
            for (int n = 0; n < lines.size(); n++) {
                String line = lines.get(n), text = line.trim();
                boolean direct = text.startsWith("j "), conditional = text.startsWith("beq ") || text.startsWith("bne ");
                if (direct || conditional) {
                    int split = direct ? 1 : text.lastIndexOf(',');
                    String target = text.substring(split + 1).trim(), finalTarget = target;
                    Set<String> visited = new HashSet<>();
                    while (forwarding.containsKey(finalTarget) && visited.add(finalTarget)) finalTarget = forwarding.get(finalTarget);
                    // Conservatively allow <= 8000 text lines for a short branch (pseudo instructions can expand).
                    if (direct || labels.containsKey(finalTarget) && Math.abs(labels.get(finalTarget) - n) < 8000)
                        line = direct ? "    j " + finalTarget : "    " + text.substring(0, split + 1) + " " + finalTarget;
                }
                if (text.matches("move (\\$\\w+), \\1") || text.matches("addiu (\\$\\w+), \\1, 0")) continue;
                threaded.add(line);
            }
            module.replaceText(reachable(threaded));
            new RemoveRedundantJumps().run(module);
            if (module.getText().equals(lines)) break;
        }
    }
    private List<String> reachable(List<String> lines) {
        Map<String, Integer> labels = new HashMap<>();
        for (int n = 0; n < lines.size(); n++) { String name = label(lines.get(n)); if (name != null) labels.put(name, n); }
        BitSet live = new BitSet(); ArrayDeque<Integer> todo = new ArrayDeque<>(); todo.add(0);
        while (!todo.isEmpty()) {
            int n = todo.remove(); if (n >= lines.size() || live.get(n)) continue;
            live.set(n); String text = lines.get(n).trim();
            boolean direct = text.startsWith("j "), call = text.startsWith("jal "), conditional = text.startsWith("beq ") || text.startsWith("bne "), ret = text.startsWith("jr ");
            if (direct || call || conditional || ret) {
                if (n + 1 < lines.size()) {
                    if (!lines.get(n + 1).trim().equals("nop")) throw new IllegalStateException("Unexpected occupied delay slot");
                    live.set(n + 1);
                }
                if (!ret) {
                    String target = text.substring(conditional ? text.lastIndexOf(',') + 1 : text.indexOf(' ') + 1).trim();
                    Integer destination = labels.get(target);
                    if (destination == null) throw new IllegalStateException("Missing assembly target " + target);
                    todo.add(destination);
                }
                if (call || conditional) todo.add(n + 2);
            } else if (!text.startsWith("break")) todo.add(n + 1);
        }
        List<String> result = new ArrayList<>();
        for (int n = 0; n < lines.size(); n++) if (live.get(n)) result.add(lines.get(n));
        return result;
    }
}
