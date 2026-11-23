package mips.pass;

import mips.MipsModule;

/** 在 IR 优化之后执行的目标代码优化接口。 */
public interface MipsPass {
    void run(MipsModule module);
}
