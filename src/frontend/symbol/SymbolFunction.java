package frontend.symbol;

import frontend.symbol.type.SymbolType;

import java.util.LinkedList;
import java.util.List;

/**
 * 函数符号，记录返回类型与形参列表。
 */
final public class SymbolFunction extends Symbol {

    private final List<SymbolVariable> parameters = new LinkedList<>();

    public SymbolFunction(String name, SymbolType type, int symbolTableIndex) {
        // 函数一定声明在全局作用域
        super(name, type, true, symbolTableIndex);
    }

    public void addParameter(SymbolVariable parameter) {
        parameters.add(parameter);
    }

    public List<SymbolVariable> getParameters() {
        return parameters;
    }

    /**
     * 函数在符号表里的类型名，就是返回类型名加 {@code Func} 后缀。
     *
     * <p>对应样例输出 {@code 1 getDay IntFunc}。{@code void} 函数会得到 {@code VoidFunc}。
     *
     * @return 如 {@code IntFunc}、{@code CharFunc}、{@code VoidFunc}
     */
    @Override
    public String getDisplayString() {
        return getType().displayName(false, false) + "Func";
    }
}

