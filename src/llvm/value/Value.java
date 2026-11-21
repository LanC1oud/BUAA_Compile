package llvm.value;

import llvm.type.IrType;
import llvm.instruction.Instruction;

import java.util.ArrayList;
import java.util.List;

// IR 值的基类，统一保存类型、名字和使用关系
public abstract class Value {
    private final IrType type;
    private final String name;

    // 记录使用当前值的指令及其操作数位置
    public record Use(Instruction user, int index) {
    }

    private final List<Use> uses = new ArrayList<>();

    // 初始化值的类型和不带前缀的名字
    protected Value(IrType type, String name) {
        this.type = type;
        this.name = name;
    }

    // 取值的 IR 类型
    public final IrType getType() {
        return type;
    }

    // 取不带 @ 或 % 前缀的名字
    public final String getName() {
        return name;
    }

    // 取使用记录列表的只读快照
    public final List<Use> getUses() {
        return List.copyOf(uses);
    }

    // 登记一条指令在指定操作数位置对当前值的使用
    public final void addUse(Instruction user, int index) {
        uses.add(new Use(user, index));
    }

    // 删除指定指令和操作数位置对应的使用记录
    public final void removeUse(Instruction user, int index) {
        uses.remove(new Use(user, index));
    }

    // 将当前值的全部使用替换为另一个同类型值
    public final void replaceAllUsesWith(Value value) {
        if (!type.equals(value.getType()))
            throw new IllegalArgumentException("Replacement type mismatch");
        for (Use use : List.copyOf(uses))
            use.user().replaceOperand(use.index(), value);
    }
}
