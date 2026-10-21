package frontend.symbol;

import frontend.ast.function.FuncType;
import frontend.symbol.type.SymbolType;
import frontend.symbol.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

final public class SymbolTable {
    // 作用域编号按进入顺序分配，不是按嵌套深度；空作用域照样占号，官方样例的 5 length Int 印证了这一点。
    private static int counter = 1;

    private static final Set<String> RESERVED = Set.of(
            "main",
            "get_int", "get_char", "get_string",
            "put_int", "put_char", "put_string",
            "printf"
    );

    private final Map<String, SymbolVariable> allVariables;
    private final Map<String, SymbolFunction> allFunctions;
    private final Stack<Map<String, SymbolVariable>> stack;
    private final Stack<Integer> stackOfScopeId;

    private final List<Symbol> outputList = new ArrayList<>();

    public SymbolTable() {
        allVariables = new HashMap<>();
        allFunctions = new HashMap<>();
        stack = new Stack<>();
        stackOfScopeId = new Stack<>();
        stack.push(new HashMap<>());
        stackOfScopeId.push(1);
        installRuntimeFunctions();
    }

    // ------------------------------------------------------------------ 作用域

    /**
     * 把这张表恢复到"刚构造完"的状态：作用域栈只剩全局作用域、编号从 1 重新开始、
     * 已声明符号与输出列表全部清空，运行时库函数重新预注册。
     *
     * <p>编译器前端外壳（{@code ui/}）复用 {@code Controller.symbols} 用：
     * 那个字段是 {@code static final}，替换引用会被 JVM 拒绝
     * （Java 21 下 {@code Field.set} 对 final 字段抛 {@code IllegalAccessException}，
     * {@code MethodHandles.findStaticVarHandle} 对 final 字段返回只读句柄），
     * 所以只能原地复位。
     *
     * <p>{@code counter} 是静态字段，必须一并复位，否则每编译一次作用域编号就往上累，
     * 第二次编译输出的 {@code symbol.txt} 就会和第一次不同。
     */
    public void clear() {
        allVariables.clear();
        allFunctions.clear();
        stack.clear();
        stackOfScopeId.clear();
        outputList.clear();
        counter = 1;
        stack.push(new HashMap<>());
        stackOfScopeId.push(1);
        installRuntimeFunctions();
    }

    public void newScope() {
        counter++;
        stack.push(new HashMap<>());
        stackOfScopeId.push(counter);
    }

    public void exitScope() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("不能弹出全局作用域");
        }
        stack.pop();
        stackOfScopeId.pop();
    }

    public int currentScopeId() {
        return stackOfScopeId.peek();
    }

    // ------------------------------------------------------------------ 定义

    public SymbolVariable addVariable(String name, SymbolType type) {
        if (declaredInCurrentScope(name)) {
            return null;
        }
        SymbolVariable variable = new SymbolVariable(
                name, type, stack.size() == 1, currentScopeId());
        stack.peek().put(name, variable);
        allVariables.put(name, variable);
        outputList.add(variable);
        return variable;
    }

    public SymbolVariable getVariable(String name) {
        var iter = stack.listIterator(stack.size());
        while (iter.hasPrevious()) {
            Map<String, SymbolVariable> variables = iter.previous();
            SymbolVariable var = variables.get(name);
            if (var != null) {
                return var;
            }
        }
        return null;
    }

    public boolean declaredInCurrentScope(String name) {
        return stack.peek().containsKey(name) || isReserved(name)
                || (stack.size() == 1 && allFunctions.containsKey(name));
    }

    // ------------------------------------------------------------------ 函数

    public SymbolFunction addFunction(String name, FuncType type) {
        return addFunction(name, SymbolType.from(type));
    }

    public SymbolFunction addFunction(String name, SymbolType returnType) {
        if (declaredInCurrentScope(name)|| allFunctions.containsKey(name)) {
            return null;
        }
        SymbolFunction function = new SymbolFunction(name, returnType, currentScopeId());
        allFunctions.put(name, function);
        outputList.add(function);
        return function;
    }

    public SymbolVariable addParameter(SymbolFunction function, String name, SymbolType type) {
        SymbolVariable parameter = addVariable(name, type);
        if (parameter == null) {
            return null;
        }
        parameter.setFromParam();
        function.addParameter(parameter);
        return parameter;
    }

    public SymbolFunction getFunction(String name) {
        return allFunctions.get(name);
    }

    public static boolean isReserved(String name) {
        return RESERVED.contains(name);
    }

    @Override
    public String toString() {
        outputList.sort(Symbol::compareTo);
        return outputList.stream().map(Symbol::toString).collect(Collectors.joining("\n"));
    }

    private void installRuntimeFunctions() {
        library("get_int", Type.Int);
        library("get_char", Type.Char);
        library("get_string", Type.Void, Type.CharArray, Type.Int);
        library("put_int", Type.Void, Type.Int);
        library("put_char", Type.Void, Type.Char);
        library("put_string", Type.Void, Type.CharArray);
        library("printf", Type.Void, Type.CharArray);
    }

    private void library(String name, SymbolType returnType, SymbolType... paramTypes) {
        SymbolFunction function = new SymbolFunction(name, returnType, 1);
        for (int i = 0; i < paramTypes.length; i++) {
            SymbolVariable parameter = new SymbolVariable("arg" + i, paramTypes[i], true, 1);
            parameter.setFromParam();
            function.addParameter(parameter);
        }
        allFunctions.put(name, function);
    }
}
