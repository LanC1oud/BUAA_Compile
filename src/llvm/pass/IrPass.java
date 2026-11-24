package llvm.pass;

import llvm.IrModule;

// 定义模块级 IR 变换的统一接口
@FunctionalInterface
public interface IrPass {
    // 对指定模块执行一次 IR 变换
    void run(IrModule module);
}
