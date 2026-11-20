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

    // 建立全局作用域并预注册库函数
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

    // 压入新作用域并分配编号
    public void newScope() {
        counter++;
        stack.push(new HashMap<>());
        stackOfScopeId.push(counter);
    }

    // 弹出当前局部作用域
    public void exitScope() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("不能弹出全局作用域");
        }
        stack.pop();
        stackOfScopeId.pop();
    }

    // 取当前作用域编号
    public int currentScopeId() {
        return stackOfScopeId.peek();
    }

    // ------------------------------------------------------------------ 定义

    // 登记变量或常量，冲突时返回 null
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

    // 从内向外查找可见变量
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

    // 检查同层重名、保留名和全局函数名冲突
    public boolean declaredInCurrentScope(String name) {
        return stack.peek().containsKey(name) || isReserved(name)
                || (stack.size() == 1 && allFunctions.containsKey(name));
    }

    // ------------------------------------------------------------------ 函数

    // 从文法节点取得返回类型并登记函数
    public SymbolFunction addFunction(String name, FuncType type) {
        return addFunction(name, SymbolType.from(type));
    }

    // 在全局函数表登记函数，冲突时返回 null
    public SymbolFunction addFunction(String name, SymbolType returnType) {
        if (declaredInCurrentScope(name)|| allFunctions.containsKey(name)) {
            return null;
        }
        SymbolFunction function = new SymbolFunction(name, returnType, currentScopeId());
        allFunctions.put(name, function);
        outputList.add(function);
        return function;
    }

    // 登记局部形参并追加到函数签名
    public SymbolVariable addParameter(SymbolFunction function, String name, SymbolType type) {
        SymbolVariable parameter = addVariable(name, type);
        if (parameter == null) {
            return null;
        }
        parameter.setFromParam();
        function.addParameter(parameter);
        return parameter;
    }

    // 按名称查询全局函数表
    public SymbolFunction getFunction(String name) {
        return allFunctions.get(name);
    }

    // 判断名称是否保留
    public static boolean isReserved(String name) {
        return RESERVED.contains(name);
    }

    // 按作用域和登记顺序输出符号
    @Override
    public String toString() {
        outputList.sort(Symbol::compareTo);
        return outputList.stream().map(Symbol::toString).collect(Collectors.joining("\n"));
    }

    // 预注册运行时库函数
    private void installRuntimeFunctions() {
        library("get_int", Type.Int);
        library("get_char", Type.Char);
        library("get_string", Type.Void, Type.CharArray, Type.Int);
        library("put_int", Type.Void, Type.Int);
        library("put_char", Type.Void, Type.Char);
        library("put_string", Type.Void, Type.CharArray);
        library("printf", Type.Void, Type.CharArray);
    }

    // 构造库函数签名并写入函数表
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
