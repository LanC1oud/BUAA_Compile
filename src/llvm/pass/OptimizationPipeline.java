package llvm.pass;

import llvm.IrModule;
import llvm.verify.IrVerifier;
import utils.Configure;

/** Ordered, independently switchable IR optimizations. The caller owns the master switch. */
public final class OptimizationPipeline implements IrPass {
    @Override public void run(IrModule module) {
        // Unreachable predecessors must be removed before constructing SSA.
        if (Configure.enableIrCleanup) new IrCleanup().run(module);
        else if (Configure.enableMem2Reg || Configure.enableGvn || Configure.enableGcm)
            new IrCleanup().removeUnreachable(module);
        if (Configure.enableGlobalScalarPromotion) new GlobalScalarPromotion().run(module);
        if (Configure.enableMem2Reg) new Mem2Reg().run(module);
        if (Configure.enableIrCleanup) new IrCleanup().run(module);
        if (Configure.enableTailRecursion) new TailRecursion().run(module);
        if (Configure.enableInlining) new SmallFunctionInline().run(module);
        if (Configure.enableMem2Reg) new Mem2Reg().run(module);
        if (Configure.enableIrCleanup) new IrCleanup().run(module);
        if (Configure.enableGvn || Configure.enableGcm) new GvnGcm().run(module);
        if (Configure.enableMemoryForwarding) new MemoryForwarding().run(module);
        if (Configure.enableIrCleanup) new IrCleanup().run(module);
        if (Configure.enableGvn || Configure.enableGcm) new GvnGcm().run(module);
        if (Configure.enableIrCleanup) new IrCleanup().run(module);
        new IrVerifier().verify(module);
    }
}
