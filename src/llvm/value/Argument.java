package llvm.value;

import llvm.type.IrType;

// 表示函数入口接收到的形参值
public final class Argument extends Value {
    private final Function parent;

    // 创建属于指定函数的形参，按位置分配名字
    public Argument(Function parent, IrType type, int index) {
        super(type, "arg" + index);
        this.parent = parent;
    }

    // 取形参所属的函数
    public Function getParent() {
        return parent;
    }
}
