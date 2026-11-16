package llvm.value;

import llvm.type.IrType;

import java.util.ArrayList;
import java.util.List;

/** 表示函数签名、形参和基本块；外部函数只有声明。 */
public final class Function extends Value {
    private final List<Argument> arguments = new ArrayList<>();
    private final List<BasicBlock> blocks = new ArrayList<>();
    private final boolean external;
    private int valueCounter, blockCounter;

    // 构造函数指针类型，并按签名创建形参值
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

    // 为当前函数中的指令结果分配唯一名称
    public String nextValueName() {
        return "v" + valueCounter++;
    }

    // 创建带唯一标签的基本块并登记到函数体
    public BasicBlock newBlock() {
        BasicBlock block = new BasicBlock(this, "bb" + blockCounter++);
        blocks.add(block);
        return block;
    }
}
