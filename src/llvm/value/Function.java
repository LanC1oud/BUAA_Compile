package llvm.value;

import llvm.type.IrType;

import java.util.ArrayList;
import java.util.List;

public final class Function extends Value {
    private final List<Argument> arguments = new ArrayList<>();
    private final List<BasicBlock> blocks = new ArrayList<>();
    private final boolean external;
    private int valueCounter, blockCounter;

    public Function(String name, IrType result, List<IrType> params, boolean external) {
        super(IrType.pointer(IrType.function(result, params)), name);
        this.external = external;
        for (int i = 0; i < params.size(); i++)
            arguments.add(new Argument(this, params.get(i), i));
    }

    public IrType getReturnType() {
        return getType().element().element();
    }

    public List<Argument> getArguments() {
        return List.copyOf(arguments);
    }

    public List<BasicBlock> getBlocks() {
        return List.copyOf(blocks);
    }

    public boolean isExternal() {
        return external;
    }

    public String nextValueName() {
        return "v" + valueCounter++;
    }

    public BasicBlock newBlock() {
        BasicBlock block = new BasicBlock(this, "bb" + blockCounter++);
        blocks.add(block);
        return block;
    }
}
