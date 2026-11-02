package llvm.value;

import llvm.type.IrType;
import llvm.instruction.Instruction;

import java.util.ArrayList;
import java.util.List;

public abstract class Value {
    private final IrType type;
    private final String name;

    public record Use(Instruction user, int index) {
    }

    private final List<Use> uses = new ArrayList<>();

    protected Value(IrType type, String name) {
        this.type = type;
        this.name = name;
    }

    public final IrType getType() {
        return type;
    }

    public final String getName() {
        return name;
    }

    public final List<Use> getUses() {
        return List.copyOf(uses);
    }

    public final void addUse(Instruction user, int index) {
        uses.add(new Use(user, index));
    }

    public final void removeUse(Instruction user, int index) {
        uses.remove(new Use(user, index));
    }

    public final void replaceAllUsesWith(Value value) {
        if (!type.equals(value.getType()))
            throw new IllegalArgumentException("Replacement type mismatch");
        for (Use use : List.copyOf(uses))
            use.user().replaceOperand(use.index(), value);
    }
}
