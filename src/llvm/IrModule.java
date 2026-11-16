package llvm;

import llvm.value.*;
import llvm.type.IrType;
import llvm.pass.IrPass;

import java.util.*;

/** 保存一个编译单元的全局对象、函数和可复用的字符串常量。 */
public final class IrModule {
    private final List<GlobalVariable> globals = new ArrayList<>();
    private final Map<String, Function> functions = new LinkedHashMap<>();
    private final Map<String, GlobalVariable> strings = new LinkedHashMap<>();

    public List<GlobalVariable> getGlobals() {
        return List.copyOf(globals);
    }

    public List<Function> getFunctions() {
        return List.copyOf(functions.values());
    }

    // 检查模块级名称冲突后登记全局对象
    public void addGlobal(GlobalVariable global) {
        if (functions.containsKey(global.getName()) ||
                globals.stream().anyMatch(g -> g.getName().equals(global.getName())))
            throw new IllegalArgumentException("Duplicate module name: " + global.getName());
        globals.add(global);
    }

    // 在共享的模块级命名空间登记函数
    public Function addFunction(Function function) {
        if (functions.containsKey(function.getName()) ||
                globals.stream().anyMatch(g -> g.getName().equals(function.getName())))
            throw new IllegalArgumentException("Duplicate module name: " + function.getName());
        functions.put(function.getName(), function);
        return function;
    }

    public Function getFunction(String name) {
        return functions.get(name);
    }

    // 按内容复用字符串；首次出现时生成以零结尾的 i8 数组常量
    public GlobalVariable internString(String text) {
        return strings.computeIfAbsent(text, key -> {
            List<Constant> bytes = new ArrayList<>();
            for (int i = 0; i < key.length(); i++)
                bytes.add(Constant.integer(IrType.I8, key.charAt(i)));
            bytes.add(Constant.integer(IrType.I8, 0));
            GlobalVariable global = new GlobalVariable(
                    ".str." + strings.size(), Constant.array(IrType.array(IrType.I8, bytes.size()), bytes), true, true);
            addGlobal(global);
            return global;
        });
    }

    // 按配置顺序在当前模块执行各个 Pass
    public void runPasses(List<IrPass> passes) {
        for (IrPass pass : passes)
            pass.run(this);
    }

    // 委托 IrPrinter 输出 LLVM IR 文本
    @Override
    public String toString() {
        return new IrPrinter().print(this);
    }
}
