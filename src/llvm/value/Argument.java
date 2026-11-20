package llvm.value;

import llvm.type.IrType;

public final class Argument extends Value {
    private final Function parent;

    public Argument(Function parent, IrType type, int index) {
        super(type, "arg" + index);
        this.parent = parent;
    }

    public Function getParent() {
        return parent;
    }
}
