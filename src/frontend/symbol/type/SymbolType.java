package frontend.symbol.type;

import frontend.ast.function.FuncType;

abstract public class SymbolType {

    // 按类型名称判断是否匹配
    abstract public boolean is(String type);

    // 取类型的基础显示名
    abstract public String getDisplayName();

    // 生成含声明修饰的类型标签
    abstract public String displayName(boolean isConst, boolean isStatic);

    // 将函数返回类型节点转换为语义类型
    public static SymbolType from(FuncType funcType) {
        return Type.from(funcType);
    }

    // 取用于符号表显示的基础类型名
    public static String getDisplayString(SymbolType type) {
        return type.getDisplayName();
    }
}
