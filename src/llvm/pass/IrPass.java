package llvm.pass;

import llvm.IrModule;

/** 可选的目标无关 IR 变换，默认不配置 Pass。 */
@FunctionalInterface
public interface IrPass {
    // 对传入的 IR 模块执行一次变换
    void run(IrModule module);
}
