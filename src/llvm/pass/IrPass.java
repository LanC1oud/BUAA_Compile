package llvm.pass;

import llvm.IrModule;

/** Optional target-independent transformation. Empty pass list is the default. */
@FunctionalInterface
public interface IrPass {
    void run(IrModule module);
}
