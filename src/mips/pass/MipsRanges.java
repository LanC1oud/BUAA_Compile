package mips.pass;

import llvm.analysis.ControlFlow;
import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.value.*;
import java.util.*;

/** Conservative integer intervals; guarded induction seeds are proved not to wrap. */
final class MipsRanges {
    private record Range(long low, long high) { }
    private final Map<Value, Range> known = new IdentityHashMap<>();
    MipsRanges(Function f) {
        if (!utils.Configure.enableRangeAnalysis) return;
        ControlFlow cfg = new ControlFlow(f);
        for (var loop : cfg.loops.entrySet()) {
            BasicBlock header = loop.getKey(); var body = header.getInstructions();
            Instruction branch = body.get(body.size() - 1);
            if (branch.getOpcode() != Opcode.BR || branch.getOperands().size() != 3) continue;
            Value condition = branch.getOperands().get(0); boolean inverted = false;
            while (condition instanceof Instruction cmp && cmp.getOpcode() == Opcode.ICMP
                    && Set.of("eq", "ne").contains(cmp.getPredicate())) {
                var a = cmp.getOperands(); Value value = zero(a.get(0)) ? a.get(1) : zero(a.get(1)) ? a.get(0) : null;
                if (value instanceof Instruction ext && ext.getOpcode() == Opcode.ZEXT) value = ext.getOperands().get(0);
                if (!(value instanceof Instruction inner) || inner.getOpcode() != Opcode.ICMP) break;
                if (cmp.getPredicate().equals("eq")) inverted = !inverted;
                condition = value;
            }
            if (!(condition instanceof Instruction cmp) || cmp.getOpcode() != Opcode.ICMP
                    || !Set.of("slt", "sle").contains(cmp.getPredicate())
                    || !(cmp.getOperands().get(0) instanceof Instruction phi) || phi.getOpcode() != Opcode.PHI
                    || phi.getParent() != header || phi.getType().bits() != 32
                    || !(cmp.getOperands().get(1) instanceof Constant bound)) continue;
            BasicBlock inside = (BasicBlock)branch.getOperands().get(inverted ? 2 : 1),
                    outside = (BasicBlock)branch.getOperands().get(inverted ? 1 : 2);
            if (!loop.getValue().contains(inside) || loop.getValue().contains(outside)) continue;
            long limit = (long)bound.getNumber() + (cmp.getPredicate().equals("sle") ? 1 : 0), low = Long.MAX_VALUE, high = Long.MIN_VALUE;
            boolean valid = true, initial = false, backedge = false;
            for (int n = 0; n < phi.getOperands().size(); n += 2) {
                Value value = phi.getOperands().get(n); BasicBlock predecessor = (BasicBlock)phi.getOperands().get(n + 1);
                if (!loop.getValue().contains(predecessor)) {
                    if (!(value instanceof Constant c) || c.getNumber() < 0) { valid = false; break; }
                    long x = ((Constant)value).getNumber(); low = Math.min(low, x); high = Math.max(high, x); initial = true;
                } else {
                    if (!(value instanceof Instruction add) || add.getOpcode() != Opcode.ADD || !cfg.dominates(inside, predecessor)) { valid = false; break; }
                    var a = add.getOperands(); Value step = a.get(0) == phi ? a.get(1) : a.get(1) == phi ? a.get(0) : null;
                    if (!(step instanceof Constant c) || c.getNumber() <= 0 || limit + c.getNumber() - 1 > Integer.MAX_VALUE) { valid = false; break; }
                    high = Math.max(high, limit + ((Constant)step).getNumber() - 1); backedge = true;
                }
            }
            if (valid && initial && backedge) known.put(phi, new Range(low, high));
        }
        for (int round = 0; round < 8; round++) {
            boolean changed = false;
            for (BasicBlock b : f.getBlocks()) for (Instruction i : b.getInstructions()) if (!known.containsKey(i)) {
                Range range = infer(i); if (range != null) { known.put(i, range); changed = true; }
            }
            if (!changed) break;
        }
    }
    private boolean zero(Value v) { return v instanceof Constant c && c.getNumber() == 0; }
    private Range range(Value v) {
        if (v instanceof Constant c && v.getType().isInteger()) return new Range(c.getNumber(), c.getNumber());
        if (known.containsKey(v)) return known.get(v);
        if (v.getType().bits() == 1 || v.getType().bits() == 8) return new Range(0, (1L << v.getType().bits()) - 1);
        return null;
    }
    boolean nonNegative(Value value) {
        if (!utils.Configure.enableRangeAnalysis) return false;
        Range r = range(value); return r != null && r.low() >= 0
                && (value.getType().bits() == 32 || r.high() < (1L << (value.getType().bits() - 1)));
    }
    private Range infer(Instruction i) {
        if (!i.getType().isInteger()) return null;
        if (i.getType().bits() < 32) return range(i);
        var a = i.getOperands();
        if (i.getOpcode() == Opcode.ZEXT) return range(a.get(0));
        if (i.getOpcode() == Opcode.PHI) {
            long lo = Long.MAX_VALUE, hi = Long.MIN_VALUE;
            for (int n = 0; n < a.size(); n += 2) {
                Range r = range(a.get(n)); if (r == null) return null;
                lo = Math.min(lo, r.low()); hi = Math.max(hi, r.high());
            }
            return new Range(lo, hi);
        }
        if (a.size() != 2) return null;
        Range x = range(a.get(0)), y = range(a.get(1)); if (x == null || y == null) return null;
        long low, high;
        switch (i.getOpcode()) {
            case ADD -> { low = x.low() + y.low(); high = x.high() + y.high(); }
            case SUB -> { low = x.low() - y.high(); high = x.high() - y.low(); }
            case MUL -> {
                long[] products = {x.low()*y.low(),x.low()*y.high(),x.high()*y.low(),x.high()*y.high()};
                low = Arrays.stream(products).min().orElseThrow(); high = Arrays.stream(products).max().orElseThrow();
            }
            default -> { return null; }
        }
        return low < Integer.MIN_VALUE || high > Integer.MAX_VALUE ? null : new Range(low, high);
    }
}
