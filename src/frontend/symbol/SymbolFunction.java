package frontend.symbol;

import frontend.symbol.type.SymbolType;

import java.util.LinkedList;
import java.util.List;

final public class SymbolFunction extends Symbol {

    private final List<SymbolVariable> parameters = new LinkedList<>();

    // 建立函数符号，保存声明的返回类型
    public SymbolFunction(String name, SymbolType type, int symbolTableIndex) {
        super(name, type, true, symbolTableIndex);
    }

    // 按声明顺序追加形参
    public void addParameter(SymbolVariable parameter) {
        parameters.add(parameter);
    }

    // 取有序形参列表，供调用检查使用
    public List<SymbolVariable> getParameters() {
        return parameters;
    }

    // 用返回类型和 Func 后缀生成函数标签
    @Override
    public String getDisplayString() {
        return getType().displayName(false, false) + "Func";
    }
}
