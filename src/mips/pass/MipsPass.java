package mips.pass;

import mips.MipsModule;

// 定义汇编生成后执行的目标代码优化接口
public interface MipsPass {
    // 对已生成的汇编模块执行一次优化
    void run(MipsModule module);
}
