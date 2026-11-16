package llvm.value;

import llvm.type.IrType;
import llvm.instruction.Instruction;

import java.util.ArrayList;
import java.util.List;

/** 带类型的 IR 值基类，并记录哪些指令使用了它。 */
public abstract class Value {
    private final IrType type;
    private final String name;

    // 一次使用由指令和操作数下标共同确定
    public record Use(Instruction user, int index) {
    }

    private final List<Use> uses = new ArrayList<>();

    protected Value(IrType type, String name) {
        this.type = type;
        this.name = name;
    }

    public final IrType getType() {
        return type;
    }

    public final String getName() {
        return name;
    }

    public final List<Use> getUses() {
        return List.copyOf(uses);
    }

    // 登记一处对当前值的使用
    public final void addUse(Instruction user, int index) {
        uses.add(new Use(user, index));
    }

    // 删除指定指令在指定操作数位置的使用记录
    public final void removeUse(Instruction user, int index) {
        uses.remove(new Use(user, index));
    }

    // 用同类型的值替换所有使用处，并同步更新使用记录
    public final void replaceAllUsesWith(Value value) {
        if (!type.equals(value.getType()))
            throw new IllegalArgumentException("Replacement type mismatch");
        for (Use use : List.copyOf(uses))
            use.user().replaceOperand(use.index(), value);
    }
}
