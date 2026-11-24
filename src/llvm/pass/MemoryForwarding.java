package llvm.pass;

import llvm.IrModule;
import llvm.instruction.Instruction;
import llvm.instruction.Instruction.Opcode;
import llvm.value.*;
import llvm.type.IrType;
import java.util.*;

/** Local load CSE, store-to-load forwarding and overwritten-store elimination with alias checks. */
public final class MemoryForwarding implements IrPass {
    private record Address(Value base, long offset, int size, Value dynamic) { }
    @Override public void run(IrModule module) {
        for (Function function : module.getFunctions()) if (!function.isExternal())
            for (BasicBlock block : function.getBlocks()) {
                Map<Address, Value> available = new LinkedHashMap<>();
                Map<Address, Instruction> stores = new LinkedHashMap<>();
                for (Instruction i : block.getInstructions()) {
                    var a = i.getOperands();
                    if (i.getOpcode() == Opcode.CALL) {
                        Function callee = (Function)a.get(0);
                        // Output and scalar input syscalls do not modify program memory.
                        if (!callee.isExternal() || callee.getName().equals("get_string")) { available.clear(); stores.clear(); }
                        else if (callee.getName().equals("put_string")) stores.clear();
                    } else if (i.getOpcode() == Opcode.LOAD) {
                        Address address = address(a.get(0), size(i.getType()));
                        Value known = available.get(address);
                        if (known != null && known.getType().equals(i.getType())) { i.replaceAllUsesWith(known); block.remove(i); }
                        else available.put(address, i);
                        stores.keySet().removeIf(previous -> aliases(address, previous));
                    } else if (i.getOpcode() == Opcode.STORE) {
                        Address address = address(a.get(1), size(a.get(0).getType()));
                        Instruction previous = stores.get(address);
                        if (previous != null) block.remove(previous);
                        available.keySet().removeIf(other -> aliases(address, other));
                        stores.keySet().removeIf(other -> aliases(address, other));
                        available.put(address, a.get(0)); stores.put(address, i);
                    }
                }
            }
    }
    private int size(IrType type) { return type.isInteger() && type.bits() <= 8 ? 1 : 4; }
    private Address address(Value pointer, int width) {
        if (pointer instanceof Instruction i && i.getOpcode() == Opcode.GEP) {
            var a = i.getOperands(); Address base = address(a.get(0), width);
            IrType indexed = a.get(0).getType().element(); long offset = base.offset();
            for (int n = 1; n < a.size(); n++) {
                if (n > 1) indexed = indexed.element();
                if (!(a.get(n) instanceof Constant c)) return new Address(base.base(), 0, width, pointer);
                offset += (long)c.getNumber() * bytes(indexed);
            }
            return new Address(base.base(), offset, width, base.dynamic());
        }
        return new Address(pointer, 0, width, null);
    }
    private long bytes(IrType type) {
        return type.kind() == IrType.Kind.ARRAY ? type.length() * bytes(type.element()) : size(type);
    }
    private boolean local(Value value) { return value instanceof Instruction i && i.getOpcode() == Opcode.ALLOCA; }
    private boolean aliases(Address a, Address b) {
        if (a.base() != b.base()) {
            if (local(a.base()) || local(b.base()) || a.base() instanceof GlobalVariable && b.base() instanceof GlobalVariable) return false;
            return true;
        }
        return a.dynamic() != null || b.dynamic() != null || a.offset() < b.offset() + b.size() && b.offset() < a.offset() + a.size();
    }
}
