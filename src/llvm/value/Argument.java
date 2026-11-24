package llvm.value;

import llvm.type.IrType;

/** 表示函数入口接收到的一个形参值。 */
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
