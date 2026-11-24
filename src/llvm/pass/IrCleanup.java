package llvm.pass;

import llvm.IrModule;
import llvm.analysis.ControlFlow;
import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;
import java.util.*;

/** Constant/copy propagation, algebraic simplification, CFG cleanup and mark/sweep DCE. */
public final class IrCleanup implements IrPass {
    public void removeUnreachable(IrModule module) {
        for (Function function : module.getFunctions()) if (!function.isExternal()) prune(function);
    }
    @Override public void run(IrModule module) {
        for (Function function : module.getFunctions()) if (!function.isExternal()) {
            for (int iteration = 0; iteration < 8; iteration++) {
                boolean changed = fold(function);
                changed |= prune(function);
                changed |= deadCode(function);
                if (!changed) break;
            }
        }
    }
    public static boolean pure(Instruction i) {
        return switch (i.getOpcode()) {
            case ALLOCA, LOAD, ADD, SUB, MUL, SDIV, SREM, UDIV, UREM, ICMP, ZEXT, TRUNC, GEP, PHI -> true;
            default -> false;
        };
    }
    public static boolean movable(Instruction i) {
        return switch (i.getOpcode()) {
            case ADD, SUB, MUL, ICMP, ZEXT, TRUNC, GEP -> true;
            default -> false;
        };
    }
    public static boolean equal(Value a, Value b) {
        return a == b || a instanceof Constant x && b instanceof Constant y && x.getType().isInteger()
                && y.getType().equals(x.getType()) && x.getNumber() == y.getNumber();
    }
    private boolean fold(Function f) {
        boolean changed = false;
        for (BasicBlock b : f.getBlocks()) for (Instruction i : b.getInstructions()) {
            Value replacement = simplify(i);
            if (replacement != null && replacement != i) {
                i.replaceAllUsesWith(replacement); b.remove(i); changed = true;
            }
            if (i.getOpcode() == Opcode.BR && i.getOperands().size() == 3) {
                var a = i.getOperands();
                Value target = a.get(0) instanceof Constant c ? a.get(c.getNumber() != 0 ? 1 : 2)
                        : a.get(1) == a.get(2) ? a.get(1) : null;
                if (target != null) {
                    b.replaceTerminator(new Instruction(b, Opcode.BR, IrType.VOID, "", "", List.of(target)));
                    changed = true;
                }
            }
        }
        return changed;
    }
    private Value simplify(Instruction i) {
        var a = i.getOperands(); IrType type = i.getType();
        if (i.getOpcode() == Opcode.PHI) {
            Value common = null;
            for (int n = 0; n < a.size(); n += 2) {
                Value v = a.get(n); if (v == i) continue;
                if (common == null) common = v; else if (!equal(common, v)) return null;
            }
            return common;
        }
        if (i.getOpcode() == Opcode.LOAD) return readConstant(a.get(0));
        if (i.getOpcode() == Opcode.ZEXT || i.getOpcode() == Opcode.TRUNC) {
            Value source = a.get(0);
            if (source instanceof Constant c) return Constant.integer(type, c.getNumber());
            if (source instanceof Instruction inner && inner.getOpcode() == Opcode.ZEXT
                    && type.equals(inner.getOperands().get(0).getType())) return inner.getOperands().get(0);
            if (i.getOpcode() == Opcode.ZEXT && source instanceof Instruction inner && inner.getOpcode() == Opcode.ZEXT)
                i.resetOperands(inner.getOperands());
            return null;
        }
        if (a.size() != 2 || !type.isInteger()) return null;
        Value left = a.get(0), right = a.get(1);
        Constant zero = Constant.integer(type, 0);
        if (i.getOpcode() == Opcode.ADD && is(right, 0) || i.getOpcode() == Opcode.SUB && is(right, 0)) return left;
        if (i.getOpcode() == Opcode.ADD && is(left, 0)) return right;
        if (i.getOpcode() == Opcode.SUB && equal(left, right)) return zero;
        if (i.getOpcode() == Opcode.MUL) {
            if (is(left, 0) || is(right, 0)) return zero;
            if (is(left, 1)) return right; if (is(right, 1)) return left;
        }
        if ((i.getOpcode() == Opcode.SDIV || i.getOpcode() == Opcode.UDIV) && is(right, 1)) return left;
        if ((i.getOpcode() == Opcode.SREM || i.getOpcode() == Opcode.UREM) && is(right, 1)) return zero;
        if (i.getOpcode() == Opcode.ICMP && equal(left, right)) {
            boolean yes = Set.of("eq", "sle", "sge", "ule", "uge").contains(i.getPredicate());
            return Constant.integer(type, yes ? 1 : 0);
        }
        if (!(left instanceof Constant x) || !(right instanceof Constant y)) return null;
        int l = x.getNumber(), r = y.getNumber();
        Integer result = switch (i.getOpcode()) {
            case ADD -> l + r;
            case SUB -> l - r;
            case MUL -> l * r;
            case UDIV -> r == 0 ? null : Integer.divideUnsigned(l, r);
            case UREM -> r == 0 ? null : Integer.remainderUnsigned(l, r);
            case SDIV, SREM -> {
                int p = signed(x), q = signed(y), minimum = x.getType().bits() == 32 ? Integer.MIN_VALUE : -(1 << (x.getType().bits() - 1));
                if (q == 0 || p == minimum && q == -1) yield null;
                yield i.getOpcode() == Opcode.SDIV ? p / q : p % q;
            }
            case ICMP -> {
                int s = Integer.compare(signed(x), signed(y)), u = Integer.compareUnsigned(l, r);
                boolean yes = switch (i.getPredicate()) {
                    case "eq" -> l == r; case "ne" -> l != r;
                    case "slt" -> s < 0; case "sle" -> s <= 0; case "sgt" -> s > 0; case "sge" -> s >= 0;
                    case "ult" -> u < 0; case "ule" -> u <= 0; case "ugt" -> u > 0; case "uge" -> u >= 0;
                    default -> throw new IllegalArgumentException("Unknown predicate");
                };
                yield yes ? 1 : 0;
            }
            default -> null;
        };
        return result == null ? null : Constant.integer(type, result);
    }
    private static int signed(Constant c) { int shift = 32 - c.getType().bits(); return c.getNumber() << shift >> shift; }
    private static boolean is(Value v, int n) { return v instanceof Constant c && c.getNumber() == n; }
    private Constant readConstant(Value address) {
        if (address instanceof GlobalVariable g && g.isReadOnly() && g.getInitializer().getType().isInteger()) return g.getInitializer();
        if (address instanceof Instruction i && i.getOpcode() == Opcode.GEP) {
            var a = i.getOperands();
            if (a.get(0) instanceof GlobalVariable g && g.isReadOnly() && a.size() == 3
                    && a.get(1) instanceof Constant first && first.getNumber() == 0 && a.get(2) instanceof Constant index) {
                int n = index.getNumber(); var elements = g.getInitializer().getElements();
                if (n >= 0 && n < elements.size()) return elements.get(n);
            }
        }
        return null;
    }
    private boolean prune(Function f) {
        ControlFlow cfg = new ControlFlow(f); boolean changed = false;
        for (BasicBlock b : f.getBlocks()) if (cfg.reachable.contains(b)) for (Instruction i : b.getInstructions()) {
            if (i.getOpcode() != Opcode.PHI) break;
            var a = i.getOperands(); List<Value> incoming = new ArrayList<>();
            for (int n = 0; n < a.size(); n += 2) {
                BasicBlock p = (BasicBlock) a.get(n + 1);
                if (cfg.reachable.contains(p) && cfg.successors.get(p).contains(b)) {
                    incoming.add(a.get(n)); incoming.add(p);
                }
            }
            if (incoming.size() != a.size()) { i.resetOperands(incoming); changed = true; }
        }
        Set<BasicBlock> dead = new LinkedHashSet<>(f.getBlocks()); dead.removeAll(cfg.reachable);
        if (!dead.isEmpty()) { f.removeBlocks(dead); changed = true; }
        return changed;
    }
    private boolean deadCode(Function f) {
        Set<Instruction> live = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Instruction> todo = new ArrayDeque<>();
        for (BasicBlock b : f.getBlocks()) for (Instruction i : b.getInstructions()) if (!pure(i)) todo.add(i);
        while (!todo.isEmpty()) {
            Instruction i = todo.remove(); if (!live.add(i)) continue;
            for (Value operand : i.getOperands()) if (operand instanceof Instruction def) todo.add(def);
        }
        List<Instruction> dead = new ArrayList<>();
        for (BasicBlock b : f.getBlocks()) for (Instruction i : b.getInstructions()) if (!live.contains(i)) dead.add(i);
        for (Instruction i : dead) i.detachOperands();
        for (Instruction i : dead) i.getParent().remove(i);
        return !dead.isEmpty();
    }
}
