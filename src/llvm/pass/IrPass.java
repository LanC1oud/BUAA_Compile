package llvm.pass;

import llvm.IrModule;

// 定义模块级 IR 变换的统一接口
@FunctionalInterface
public interface IrPass {
    void run(IrModule module);
}
