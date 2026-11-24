package llvm.verify;

import llvm.IrModule;
import llvm.instruction.Instruction;
import llvm.type.IrType;
import llvm.value.*;

import java.util.*;

// 检查 IR 的结构、类型和使用关系是否合法
public final class IrVerifier {
    // 条件不满足时抛出带原因的 IR 校验异常
    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException("Invalid IR: " + message);
    }

    // 遍历模块，检查命名、归属、基本块终结和使用关系
    public void verify(IrModule module) {
        Set<String> names = new HashSet<>();
        Set<Value> values = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Instruction> instructions = Collections.newSetFromMap(new IdentityHashMap<>());
        for (GlobalVariable g : module.getGlobals()) {
            require(names.add(g.getName()), "duplicate global name");
            require(g.getType().element().equals(g.getInitializer().getType()), "global initializer type");
            values.add(g);
        }
        for (Function f : module.getFunctions()) {
            require(names.add(f.getName()), "duplicate function name");
            values.add(f);
            Set<String> locals = new HashSet<>();
            for (Argument arg : f.getArguments()) {
                require(arg.getParent() == f && locals.add(arg.getName()), "argument ownership/name");
                values.add(arg);
            }
            if (f.isExternal()) {
                require(f.getBlocks().isEmpty(), "external function body");
                continue;
            }
            require(!f.getBlocks().isEmpty(), "missing entry");
            BasicBlock entry = f.getBlocks().get(0);
            for (BasicBlock block : f.getBlocks()) {
                require(block.getParent() == f && locals.add(block.getName()), "block ownership/name");
                values.add(block);
                List<Instruction> body = block.getInstructions();
                require(block.isTerminated(), "unterminated block " + block.getName());
                for (int j = 0; j < body.size(); j++) {
                    Instruction i = body.get(j);
                    instructions.add(i);
                    values.add(i);
                    require(i.getParent() == block, "instruction ownership");
                    require(!i.isTerminator() || j == body.size() - 1, "nonfinal terminator");
                    require(i.getType().equals(IrType.VOID) ? i.getName().isEmpty() : locals.add(i.getName()),
                            "result name");
                    verifyInstruction(i);
                    List<Value> operands = i.getOperands();
                    for (int k = 0; k < operands.size(); k++) {
                        Value v = operands.get(k);
                        values.add(v);
                        require(v.getUses().contains(new Value.Use(i, k)), "missing use registration");
                        if (v instanceof BasicBlock target) {
                            require(target.getParent() == f && f.getBlocks().contains(target)
                                            && (i.getOpcode() != Instruction.Opcode.BR || target != entry),
                                    "invalid branch target/entry backedge");
                        } else if (v instanceof Argument arg)
                            require(arg.getParent() == f, "cross-function argument");
                        else if (v instanceof Instruction def)
                            require(def.getParent().getParent() == f, "cross-function value");
                        else if (v instanceof Function callee)
                            require(module.getFunctions().contains(callee), "unknown callee");
                        else if (v instanceof GlobalVariable global)
                            require(module.getGlobals().contains(global), "unknown global");
                    }
                }
            }
            verifyDominance(f);
        }
        for (Value value : values)
            for (Value.Use use : value.getUses()) {
                require(instructions.contains(use.user()) && use.index() < use.user().getOperands().size() &&
                                use.user().getOperands().get(use.index()) == value,
                        "dangling use");
            }
    }

    private void verifyDominance(Function function) {
        llvm.analysis.ControlFlow cfg = new llvm.analysis.ControlFlow(function);
        Map<Instruction, Integer> positions = new IdentityHashMap<>();
        for (BasicBlock block : function.getBlocks()) {
            boolean ordinary = false; List<Instruction> body = block.getInstructions();
            for (int n = 0; n < body.size(); n++) {
                Instruction i = body.get(n); positions.put(i, n);
                if (i.getOpcode() == Instruction.Opcode.PHI) {
                    require(!ordinary, "PHI after ordinary instruction");
                    Set<BasicBlock> predecessors = new HashSet<>();
                    for (int k = 1; k < i.getOperands().size(); k += 2) predecessors.add((BasicBlock)i.getOperands().get(k));
                    require(predecessors.equals(new HashSet<>(cfg.predecessors.get(block))), "incomplete PHI predecessors");
                } else ordinary = true;
            }
        }
        for (BasicBlock block : function.getBlocks()) for (Instruction i : block.getInstructions()) {
            var operands = i.getOperands();
            for (int n = 0; n < operands.size(); n++) if (operands.get(n) instanceof Instruction def) {
                BasicBlock use = i.getOpcode() == Instruction.Opcode.PHI ? (BasicBlock)operands.get(n + 1) : block;
                require(positions.containsKey(def), "use of removed definition");
                if (!cfg.reachable.contains(use)) continue;
                require(cfg.dominates(def.getParent(), use), "definition does not dominate use: " + def.getName());
                if (def.getParent() == use) require(positions.get(def) < (i.getOpcode() == Instruction.Opcode.PHI
                        ? use.getInstructions().size() : positions.get(i)), "definition follows use");
            }
        }
    }

    // 按操作码检查指令的操作数数量、类型及相关约束
    private void verifyInstruction(Instruction i) {
        List<Value> a = i.getOperands();
        IrType result = i.getType();
        switch (i.getOpcode()) {
            case ALLOCA -> require(a.isEmpty() && result.kind() == IrType.Kind.POINTER &&
                            i.getParent() == i.getParent().getParent().getBlocks().get(0),
                    "alloca type/placement");
            case LOAD -> require(a.size() == 1 && a.get(0).getType().kind() == IrType.Kind.POINTER &&
                            result.equals(a.get(0).getType().element()),
                    "load type");
            case STORE -> require(a.size() == 2 && result.equals(IrType.VOID) &&
                            a.get(1).getType().equals(IrType.pointer(a.get(0).getType())),
                    "store type");
            case ADD, SUB, MUL, SDIV, SREM, UDIV, UREM ->
                    require(a.size() == 2 && result.isInteger() && result.equals(a.get(0).getType()) &&
                                    result.equals(a.get(1).getType()),
                            "binary types");
            case ICMP -> require(a.size() == 2 && result.equals(IrType.I1) && a.get(0).getType().isInteger() &&
                            a.get(0).getType().equals(a.get(1).getType()) &&
                            Set.of("eq", "ne", "slt", "sle", "sgt", "sge", "ult", "ule", "ugt", "uge")
                                    .contains(i.getPredicate()),
                    "icmp types/predicate");
            case ZEXT, TRUNC -> {
                require(a.size() == 1 && result.isInteger() && a.get(0).getType().isInteger(), "conversion types");
                int from = a.get(0).getType().bits(), to = result.bits();
                require(i.getOpcode() == Instruction.Opcode.ZEXT ? from < to : from > to, "conversion width");
            }
            case GEP -> {
                require(a.size() >= 2 && a.get(0).getType().kind() == IrType.Kind.POINTER, "GEP pointer");
                IrType source = a.get(0).getType().element();
                boolean array = source.kind() == IrType.Kind.ARRAY;
                require(a.size() == (array ? 3 : 2) && result.equals(IrType.pointer(array ? source.element() : source)),
                        "GEP result/index count");
                for (Value index : a.subList(1, a.size()))
                    require(index.getType().equals(IrType.I32), "GEP index type");
                if (array)
                    require(a.get(1) instanceof Constant c && c.getNumber() == 0, "array GEP first index");
            }
            case PHI -> {
                require(result.isInteger() || result.kind() == IrType.Kind.POINTER, "phi result type");
                require(!a.isEmpty() && a.size() % 2 == 0, "phi incoming pairs");
                Set<BasicBlock> incoming = Collections.newSetFromMap(new IdentityHashMap<>());
                for (int n = 0; n < a.size(); n += 2) {
                    require(a.get(n).getType().equals(result) && a.get(n + 1) instanceof BasicBlock, "phi pair type");
                    require(incoming.add((BasicBlock) a.get(n + 1)), "duplicate phi predecessor");
                    BasicBlock predecessor = (BasicBlock) a.get(n + 1);
                    require(predecessor.isTerminated(), "phi predecessor terminator");
                    Instruction terminator = predecessor.getInstructions().get(predecessor.getInstructions().size() - 1);
                    require(terminator.getOpcode() == Instruction.Opcode.BR && terminator.getOperands().contains(i.getParent()),
                            "phi incoming edge");
                }
            }
            case CALL -> {
                require(!a.isEmpty() && a.get(0) instanceof Function, "call target");
                Function f = (Function) a.get(0);
                require(result.equals(f.getReturnType()) && a.size() == f.getArguments().size() + 1, "call signature");
                for (int j = 1; j < a.size(); j++)
                    require(a.get(j).getType().equals(f.getArguments().get(j - 1).getType()), "call argument type");
            }
            case BR -> {
                require(result.equals(IrType.VOID) && (a.size() == 1 || a.size() == 3), "branch arity");
                if (a.size() == 3)
                    require(a.get(0).getType().equals(IrType.I1), "branch condition must be i1");
                for (Value target : a.subList(a.size() == 3 ? 1 : 0, a.size()))
                    require(target instanceof BasicBlock, "branch label");
            }
            case RET -> {
                IrType expected = i.getParent().getParent().getReturnType();
                require(result.equals(IrType.VOID) &&
                                (expected.equals(IrType.VOID) ? a.isEmpty()
                                        : a.size() == 1 && a.get(0).getType().equals(expected)),
                        "return type");
            }
            case UNREACHABLE -> require(result.equals(IrType.VOID) && a.isEmpty(), "unreachable operands");
        }
    }
}
