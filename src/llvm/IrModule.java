package llvm;

import llvm.value.*;
import llvm.type.IrType;
import llvm.pass.IrPass;

import java.util.*;

// 保存完整程序的全局对象、函数和去重后的字符串常量
public final class IrModule {
    private final List<GlobalVariable> globals = new ArrayList<>();
    private final Map<String, Function> functions = new LinkedHashMap<>();
    private final Map<String, GlobalVariable> strings = new LinkedHashMap<>();

    // 取全局对象列表的只读快照
    public List<GlobalVariable> getGlobals() {
        return List.copyOf(globals);
    }

    // 按登记顺序取函数列表的只读快照
    public List<Function> getFunctions() {
        return List.copyOf(functions.values());
    }

    // 登记全局对象，并检查模块级重名
    public void addGlobal(GlobalVariable global) {
        if (functions.containsKey(global.getName()) ||
                globals.stream().anyMatch(g -> g.getName().equals(global.getName())))
            throw new IllegalArgumentException("Duplicate module name: " + global.getName());
        globals.add(global);
    }

    // 登记函数并检查模块级重名，返回该函数
    public Function addFunction(Function function) {
        if (functions.containsKey(function.getName()) ||
                globals.stream().anyMatch(g -> g.getName().equals(function.getName())))
            throw new IllegalArgumentException("Duplicate module name: " + function.getName());
        functions.put(function.getName(), function);
        return function;
    }

    // 按名称查找函数，不存在时返回 null
    public Function getFunction(String name) {
        return functions.get(name);
    }

    // 复用或创建带末尾零的全局字符串常量
    public GlobalVariable internString(String text) {
        return strings.computeIfAbsent(text, key -> {
            List<Constant> bytes = new ArrayList<>();
            for (int i = 0; i < key.length(); i++) {
                char character = key.charAt(i);
                Constant value = Constant.integer(IrType.I8, character);
                bytes.add(value);
            }
            Constant terminator = Constant.integer(IrType.I8, 0);
            bytes.add(terminator);
            String name = ".str." + strings.size();
            IrType arrayType = IrType.array(IrType.I8, bytes.size());
            Constant initializer = Constant.array(arrayType, bytes);
            GlobalVariable global = new GlobalVariable(name, initializer, true, true);
            addGlobal(global);
            return global;
        });
    }

    // 按列表顺序执行模块级 IR 变换
    public void runPasses(List<IrPass> passes) {
        for (IrPass pass : passes)
            pass.run(this);
    }

    // 将整个模块转换为 LLVM IR 文本
    @Override
    public String toString() {
        return new IrPrinter().print(this);
    }
}
