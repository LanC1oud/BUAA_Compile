package mips.pass;

import llvm.instruction.Instruction;
import llvm.type.IrType;
import llvm.value.*;
import java.util.*;

/** 不修改 IR 的函数内分析：重算值、融合比较、块内末次使用与跨块结果。 */
final class MipsFunctionPlan {
    private final MipsConstants constants;
    private final Map<Value, Boolean> stackAddresses = new IdentityHashMap<>();
    private final Set<Value> crossBlock = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Instruction> fused = Collections.newSetFromMap(new IdentityHashMap<>());
    record Comparison(Instruction instruction, boolean inverted) { }
    private final Map<Instruction, Comparison> branches = new IdentityHashMap<>();
    private final Map<BasicBlock, Map<Value, Integer>> lastUses = new IdentityHashMap<>();

    MipsFunctionPlan(Function function, MipsConstants constants) {
        this.constants = constants;
        for (BasicBlock block : function.getBlocks()) {
            List<Instruction> instructions = block.getInstructions();
            for (int n = 1; n < instructions.size(); n++) {
                Instruction branch = instructions.get(n), compare = instructions.get(n - 1);
                if (branch.getOpcode() == Instruction.Opcode.BR && branch.getOperands().size() == 3
                        && compare.getOpcode() == Instruction.Opcode.ICMP
                        && branch.getOperands().get(0) == compare && compare.getUses().size() == 1) {
                    List<Instruction> chain = new ArrayList<>(List.of(compare));
                    boolean inverted = false;
                    int cursor = n - 1;
                    while (cursor >= 2 && (compare.getPredicate().equals("eq") || compare.getPredicate().equals("ne"))) {
                        var operands = compare.getOperands();
                        Value candidate = isZero(operands.get(0)) ? operands.get(1)
                                : isZero(operands.get(1)) ? operands.get(0) : null;
                        if (!(candidate instanceof Instruction extension) || extension.getOpcode() != Instruction.Opcode.ZEXT
                                || !extension.getOperands().get(0).getType().equals(IrType.I1)
                                || extension.getUses().size() != 1 || instructions.get(cursor - 1) != extension
                                || !(extension.getOperands().get(0) instanceof Instruction inner)
                                || inner.getOpcode() != Instruction.Opcode.ICMP || inner.getUses().size() != 1
                                || instructions.get(cursor - 2) != inner) break;
                        if (compare.getPredicate().equals("eq")) inverted = !inverted;
                        chain.add(extension);
                        chain.add(inner);
                        compare = inner;
                        cursor -= 2;
                    }
                    fused.addAll(chain);
                    branches.put(branch, new Comparison(compare, inverted));
                }
            }
            for (Instruction instruction : instructions)
                if (instruction.getUses().stream().anyMatch(use -> use.user().getParent() != block))
                    crossBlock.add(instruction);
        }
        for (BasicBlock block : function.getBlocks()) {
            Map<Value, Integer> uses = new IdentityHashMap<>();
            lastUses.put(block, uses);
            List<Instruction> instructions = block.getInstructions();
            for (int n = 0; n < instructions.size(); n++) {
                Instruction instruction = instructions.get(n);
                if (fused.contains(instruction)) continue;
                Comparison comparison = branches.get(instruction);
                List<Value> operands = comparison == null ? instruction.getOperands() : comparison.instruction().getOperands();
                for (Value operand : operands) uses.put(operand, n);
            }
        }
    }

    private boolean isZero(Value value) {
        Constant constant = constants.get(value);
        return constant != null && constant.getNumber() == 0;
    }

    Comparison comparisonFor(Instruction branch) { return branches.get(branch); }
    boolean isFused(Instruction instruction) { return fused.contains(instruction); }
    boolean crossesBlock(Value value) { return crossBlock.contains(value); }
    int lastUse(Value value, BasicBlock block) { return lastUses.get(block).getOrDefault(value, -1); }
    boolean liveAfter(Value value, BasicBlock block, int position) {
        return crossesBlock(value) || lastUse(value, block) > position;
    }

    boolean needsSlot(Instruction instruction) {
        return !instruction.getType().equals(IrType.VOID) && instruction.getOpcode() != Instruction.Opcode.ALLOCA
                && !instruction.getUses().isEmpty() && constants.get(instruction) == null
                && !isFused(instruction) && !isStackAddress(instruction);
    }

    boolean isStackAddress(Value value) {
        Boolean cached = stackAddresses.get(value);
        if (cached != null) return cached;
        boolean result = false;
        if (value instanceof Instruction instruction) {
            if (instruction.getOpcode() == Instruction.Opcode.ALLOCA) result = true;
            else if (instruction.getOpcode() == Instruction.Opcode.GEP) {
                var operands = instruction.getOperands();
                result = isStackAddress(operands.get(0))
                        && operands.subList(1, operands.size()).stream().allMatch(index -> constants.get(index) != null);
            }
        }
        stackAddresses.put(value, result);
        return result;
    }
}
