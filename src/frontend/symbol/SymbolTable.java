package frontend.symbol;

import frontend.ast.function.FuncType;
import frontend.symbol.type.SymbolType;

import java.util.*;
import java.util.stream.Collectors;

final public class SymbolTable {
    private static int counter = 1;  // Global scope is 1
    
    private final Map<String, SymbolVariable> allVariables;
    private final Map<String, SymbolFunction> allFunctions;
    private final Stack<Map<String, SymbolVariable>> stack;
    private final Stack<Integer> stackOfScopeId;
    private final List<Symbol> outputList = new ArrayList<>();
    
    public SymbolTable() {
        allVariables = new HashMap<>();
        allFunctions = new HashMap<>();
        stack = new Stack<>() {{
            push(new HashMap<>());
        }};
        stackOfScopeId = new Stack<>() {{
            push(1);
        }};
    }
    
    public void newScope() {
        counter++;
        stack.push(new HashMap<>());
        stackOfScopeId.push(counter);
    }
    
    public void exitScope() {
        stack.pop();
        stackOfScopeId.pop();
    }
    
    /**
     * 语言内置的名字不允许被重新声明。
     */
    private boolean bumpKeepIdentifier(String name) {
        return name.equals("printf") || name.equals("getint") || name.equals("getchar");
    }
    
    public SymbolVariable addVariable(String name, SymbolType type) {
        if (stack.peek().containsKey(name) || bumpKeepIdentifier(name)) {
            // 这里表示重定义
            return null;
        }
        // stack.size() == 1 是判断是否是全局变量的标志
        SymbolVariable variable = new SymbolVariable(name, type, stack.size() == 1, stackOfScopeId.peek());
        allVariables.put(name, variable);
        stack.peek().put(name, variable);
        outputList.add(variable);
        return variable;
    }

    public SymbolVariable getVariable(String name) {
        var iter = stack.listIterator(stack.size());
        while (iter.hasPrevious()) {
            var variables = iter.previous();
            if (variables.containsKey(name)) {
                SymbolVariable var = variables.get(name);
                assert var.getName().equals(name) : "Inconsistent variable name for `" + name + "`";
                return var;
            }
        }
        return null;
    }
    
    public SymbolFunction addFunction(String name, FuncType type) {
        if (stack.peek().containsKey(name) || allFunctions.containsKey(name) || bumpKeepIdentifier(name)) {
            return null;
        }
        SymbolFunction function = new SymbolFunction(name, SymbolType.from(type), stackOfScopeId.peek());
        allFunctions.put(name, function);
        outputList.add(function);
        return function;
    }
    
    public SymbolFunction getFunction(String name) {
        return allFunctions.get(name);
    }
    
    public SymbolVariable addParameter(SymbolFunction function, String name, SymbolType type) {
        SymbolVariable parameter = addVariable(name, type);
        if (parameter == null) {
            return null;
        }
        allVariables.put(name, parameter);
        stack.peek().put(name, parameter);
        function.addParameter(parameter);
        return parameter;
    }
    
    public String toString() {
        outputList.sort(Symbol::compareTo);
        return outputList.stream().map(Symbol::toString).collect(Collectors.joining("\n"));
    }
}
