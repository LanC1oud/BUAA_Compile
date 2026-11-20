package llvm.value;

import llvm.type.IrType;

public final class GlobalVariable extends Value {
    private final Constant initializer;
    private final boolean readOnly;
    private final boolean internal;

    public GlobalVariable(String name, Constant init, boolean readOnly, boolean internal) {
        super(IrType.pointer(init.getType()), name);
        this.initializer = init;
        this.readOnly = readOnly;
        this.internal = internal;
    }

    public Constant getInitializer() {
        return initializer;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean isInternal() {
        return internal;
    }
}
