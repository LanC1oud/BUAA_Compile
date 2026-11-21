package llvm.value;

import llvm.type.IrType;

// 表示全局变量、局部 static 或字符串的模块级存储
public final class GlobalVariable extends Value {
    private final Constant initializer;
    private final boolean readOnly;
    private final boolean internal;

    // 根据初值建立全局存储，并记录只读和内部链接属性
    public GlobalVariable(String name, Constant init, boolean readOnly, boolean internal) {
        super(IrType.pointer(init.getType()), name);
        this.initializer = init;
        this.readOnly = readOnly;
        this.internal = internal;
    }

    // 取全局存储的常量初值
    public Constant getInitializer() {
        return initializer;
    }

    // 判断是否应输出为 constant 只读对象
    public boolean isReadOnly() {
        return readOnly;
    }

    // 判断是否只在当前模块内部可见
    public boolean isInternal() {
        return internal;
    }
}
