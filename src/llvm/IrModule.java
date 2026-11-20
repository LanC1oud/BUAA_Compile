package llvm;

import llvm.value.*;
import llvm.type.IrType;
import llvm.pass.IrPass;

import java.util.*;

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

    public void addGlobal(GlobalVariable global) {
        if (functions.containsKey(global.getName()) ||
                globals.stream().anyMatch(g -> g.getName().equals(global.getName())))
            throw new IllegalArgumentException("Duplicate module name: " + global.getName());
        globals.add(global);
    }

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

    public void runPasses(List<IrPass> passes) {
        for (IrPass pass : passes)
            pass.run(this);
    }

    @Override
    public String toString() {
        return new IrPrinter().print(this);
    }
}
