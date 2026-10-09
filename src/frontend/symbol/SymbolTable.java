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

/**
 * 符号表：作用域栈 + 输出列表。
 *
 * <p>与 2024 参考实现的差异（都是 2026 文法要求导致的）：
 * <ul>
 *   <li>{@code main} 也是保留标识符（它是关键字，不能被重定义），但它<b>不进符号表</b>。</li>
 *   <li>运行时库函数（7 个）预注册进函数表，能被查到、但<b>不进输出列表</b>。</li>
 *   <li>{@link #addVariable} 不再自动补 {@code newScope()}：形参与函数体最外层<b>共用</b>一个作用域号，
 *       由遍历器决定什么时候真正开新作用域。</li>
 * </ul>
 */
final public class SymbolTable {
    /**
     * 作用域编号计数器。全局作用域是 1，之后每进入一个新作用域就分配下一个编号。
     *
     * <p>编号是"进入该作用域之前进入过的作用域数量 + 1"，也就是按<b>进入顺序</b>递增，
     * 而不是按嵌套深度。官方样例 {@code 5 length Int} 证明了这个区别：
     * {@code getDay}/{@code fun}/{@code main} 的函数体依次是 2、3、4，
     * main 里的嵌套块才是 5；若按深度编号，嵌套块只会得到 3。
     */
    private static int counter = 1;

    /**
     * 语言保留的标识符。它们不能被用户重定义：
     * {@code main} 是关键字，其余是运行时库函数名。
     */
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

    /** 只有用户声明的符号进入这里，用于输出 {@code symbol.txt}。 */
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
     * 开一个新作用域，并分配下一个作用域编号。
     *
     * <p>形参与函数体最外层共用同一个作用域：调用方只为整个函数开一次作用域，
     * 嵌套块才需要再开。
     */
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

    /** @return 当前作用域的编号。 */
    public int currentScopeId() {
        return stackOfScopeId.peek();
    }

    // ------------------------------------------------------------------ 定义

    /**
     * 在当前作用域声明一个变量。
     *
     * @return 新符号；名字已被占用（当前作用域已有同名，或是保留标识符）时返回 {@code null}，
     *         由调用方报错误 b
     */
    public SymbolVariable addVariable(String name, SymbolType type) {
        if (stack.peek().containsKey(name) || isReserved(name)) {
            return null;
        }
        SymbolVariable variable = new SymbolVariable(
                name, type, stack.size() == 1, currentScopeId());
        stack.peek().put(name, variable);
        // 同名变量可能存在于外层作用域，这里用 put 覆盖全局索引，与参考实现一致
        allVariables.put(name, variable);
        outputList.add(variable);
        return variable;
    }

    /** 由内向外查找变量。 */
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

    /** @return 当前作用域内是否已经声明了该名字（用于判断错误 b）。 */
    public boolean declaredInCurrentScope(String name) {
        return stack.peek().containsKey(name) || isReserved(name);
    }

    // ------------------------------------------------------------------ 函数

    /** 声明一个函数，返回类型从 {@link FuncType} 推出。 */
    public SymbolFunction addFunction(String name, FuncType type) {
        return addFunction(name, SymbolType.from(type));
    }

    /** 声明一个函数，直接给出返回类型。 */
    public SymbolFunction addFunction(String name, SymbolType returnType) {
        if (stack.peek().containsKey(name) || allFunctions.containsKey(name) || isReserved(name)) {
            return null;
        }
        SymbolFunction function = new SymbolFunction(name, returnType, currentScopeId());
        allFunctions.put(name, function);
        outputList.add(function);
        return function;
    }

    /**
     * 为函数登记一个形参。形参进入当前作用域，并记录到函数的形参表里。
     *
     * @return 新符号；形参重名时返回 {@code null}
     */
    public SymbolVariable addParameter(SymbolFunction function, String name, SymbolType type) {
        SymbolVariable parameter = addVariable(name, type);
        if (parameter == null) {
            return null;
        }
        parameter.setFromParam();
        function.addParameter(parameter);
        return parameter;
    }

    /** 按名字查找函数（含运行时库函数）。 */
    public SymbolFunction getFunction(String name) {
        return allFunctions.get(name);
    }

    /** @return 是否是保留标识符（{@code main} 或运行时库函数名）。 */
    public static boolean isReserved(String name) {
        return RESERVED.contains(name);
    }

    // ------------------------------------------------------------------ 输出

    @Override
    public String toString() {
        outputList.sort(Symbol::compareTo);
        return outputList.stream().map(Symbol::toString).collect(Collectors.joining("\n"));
    }

    // ------------------------------------------------------------------ 运行时库

    /**
     * 预注册 2026 文法的 7 个运行时库函数。
     *
     * <p>它们必须能被查到，否则用到 {@code get_int()} 之类的地方会误报错误 c；
     * 但它们<b>不能进入输出列表</b>，符号表里只列用户声明的符号。
     */
    private void installRuntimeFunctions() {
        library("get_int", Type.Int);
        library("get_char", Type.Int);
        library("get_string", Type.Int, Type.CharArray, Type.Int);
        library("put_int", Type.Void, Type.Int);
        library("put_char", Type.Void, Type.Char);
        library("put_string", Type.Void, Type.CharArray);
        library("printf", Type.Void, Type.CharArray);
    }

    /** 登记一个库函数；第一个参数是返回类型，其余是形参类型。 */
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
