package llvm.value;

import llvm.type.IrType;

import java.util.ArrayList;
import java.util.List;

// 保存函数签名、形参和基本块，统一分配函数内的名字
public final class Function extends Value {
    private final List<Argument> arguments = new ArrayList<>();
    private final List<BasicBlock> blocks = new ArrayList<>();
    private final boolean external;
    private int valueCounter, blockCounter;

    // 根据函数签名创建形参，并记录是否为外部声明
    public Function(String name, IrType result, List<IrType> params, boolean external) {
        super(IrType.pointer(IrType.function(result, params)), name);
        this.external = external;
        for (int i = 0; i < params.size(); i++) {
            IrType parameterType = params.get(i);
            Argument argument = new Argument(this, parameterType, i);
            arguments.add(argument);
        }
    }

    // 取函数签名中的返回类型
    public IrType getReturnType() {
        return getType().element().element();
    }

    // 取形参列表的只读快照
    public List<Argument> getArguments() {
        return List.copyOf(arguments);
    }

    // 按创建顺序取基本块列表的只读快照
    public List<BasicBlock> getBlocks() {
        return List.copyOf(blocks);
    }

    // 判断是否为没有函数体的外部声明
    public boolean isExternal() {
        return external;
    }

    // 分配当前函数内唯一的指令结果名
    public String nextValueName() {
        return "v" + valueCounter++;
    }

    // 创建基本块，分配名字并加入当前函数
    public BasicBlock newBlock() {
        BasicBlock block = new BasicBlock(this, "bb" + blockCounter++);
        blocks.add(block);
        return block;
    }

    public BasicBlock newEntryBlock() {
        BasicBlock block = new BasicBlock(this, "bb" + blockCounter++);
        blocks.add(0, block);
        return block;
    }

    public void removeBlocks(java.util.Set<BasicBlock> removed) {
        for (BasicBlock block : removed)
            for (var instruction : block.getInstructions()) instruction.detachOperands();
        blocks.removeAll(removed);
    }
}
