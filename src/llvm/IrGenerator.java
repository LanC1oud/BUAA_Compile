package llvm;

import frontend.SemanticInfo;
import frontend.ast.*;
import frontend.ast.block.*;
import frontend.ast.declaration.*;
import frontend.ast.expression.*;
import frontend.ast.expression.Number;
import frontend.ast.function.*;
import frontend.ast.statement.*;
import frontend.symbol.*;
import frontend.symbol.type.Type;
import frontend.token.TokenType;
import llvm.instruction.Instruction.Opcode;
import llvm.type.IrType;
import llvm.value.*;
import llvm.verify.IrVerifier;

import java.util.*;

/** AST lowering. Each instance belongs to exactly one compilation. */
public final class IrGenerator {
    private final SemanticInfo info;
    private final IrModule module = new IrModule();
    private final IrBuilder builder = new IrBuilder();
    private final Map<SymbolVariable, Value> addresses = new IdentityHashMap<>();
    private final Map<SymbolFunction, Function> functions = new IdentityHashMap<>();
    private final Deque<BasicBlock> breakTargets = new ArrayDeque<>();
    private final Deque<BasicBlock> continueTargets = new ArrayDeque<>();
    private int staticCounter;
    private final List<llvm.pass.IrPass> passes;
    private String unoptimizedIr;

    public IrGenerator(SemanticInfo info) {
        this(info, List.of());
    }

    public IrGenerator(SemanticInfo info, List<llvm.pass.IrPass> passes) {
        this.info = info;
        this.passes = List.copyOf(passes);
    }

    public String getUnoptimizedIr() {
        return unoptimizedIr;
    }

    public IrModule generate(CompileUnit ast) {
        installRuntime();
        for (Decl decl : ast.getDeclarations())
            emitDecl(decl);
        // Register identities before bodies: this supports recursion without changing frontend visibility.
        for (FuncDef def : ast.getFunctions()) {
            SymbolFunction symbol = info.functionOf(def);
            String name = symbol.getName();
            Type sourceReturnType = (Type) symbol.getType();
            IrType returnType = IrType.from(sourceReturnType, false);
            List<IrType> parameterTypes = new ArrayList<>();
            for (SymbolVariable parameter : symbol.getParameters()) {
                Type sourceParameterType = (Type) parameter.getType();
                IrType parameterType = IrType.from(sourceParameterType, true);
                parameterTypes.add(parameterType);
            }
            Function function = new Function(name, returnType, parameterTypes, false);
            module.addFunction(function);
            functions.put(symbol, function);
        }
        Function main = new Function("main", IrType.I32, List.of(), false);
        module.addFunction(main);
        for (FuncDef def : ast.getFunctions())
            emitFunction(def);
        builder.enterFunction(main);
        emitBlock(ast.getMainFunc().getBlock());
        finishFunction();
        IrVerifier verifier = new IrVerifier();
        verifier.verify(module);
        unoptimizedIr = module.toString();
        if (!passes.isEmpty()) {
            module.runPasses(passes);
            verifier.verify(module);
        }
        return module;
    }

    private void installRuntime() {
        IrType charPointerType = IrType.pointer(IrType.I8);
        external("get_int", IrType.I32);
        external("get_char", IrType.I8);
        external("get_string", IrType.VOID, charPointerType, IrType.I32);
        external("put_int", IrType.VOID, IrType.I32);
        external("put_char", IrType.VOID, IrType.I8);
        external("put_string", IrType.VOID, charPointerType);
    }

    private void external(String name, IrType result, IrType... params) {
        List<IrType> parameterTypes = List.of(params);
        Function function = new Function(name, result, parameterTypes, true);
        module.addFunction(function);
    }

    private void emitFunction(FuncDef def) {
        SymbolFunction functionSymbol = info.functionOf(def);
        Function function = functions.get(functionSymbol);
        builder.enterFunction(function);
        FuncFormalParams formalParams = def.getParams();
        List<FuncFormalParam> params = formalParams == null ? List.of() : formalParams.getParams();
        for (int i = 0; i < params.size(); i++) {
            FuncFormalParam parameter = params.get(i);
            SymbolVariable symbol = info.variableOf(parameter);
            Type sourceType = (Type) symbol.getType();
            Argument arg = function.getArguments().get(i);
            if (sourceType.isArray())
                addresses.put(symbol, arg);
            else {
                Value slot = builder.alloca(arg.getType());
                addresses.put(symbol, slot);
                builder.store(arg, slot);
            }
        }
        emitBlock(def.getBlock());
        finishFunction();
    }

    private void finishFunction() {
        if (!builder.isTerminated()) {
            if (builder.getFunction().getReturnType().equals(IrType.VOID))
                builder.ret(null);
            else
                builder.emit(Opcode.UNREACHABLE, IrType.VOID, "");
        }
    }

    private void emitDecl(Decl decl) {
        if (decl instanceof ConstDecl c)
            for (ConstDef def : c.getDefs())
                emitDefinition(def, def.getInitVal());
        else if (decl instanceof VarDecl v)
            for (VarDef def : v.getDefs())
                emitDefinition(def, def.getInitVal());
    }

    private void emitDefinition(ASTNode def, ASTNode init) {
        SymbolVariable symbol = info.variableOf(def);
        Type sourceType = (Type) symbol.getType();
        IrType type = IrType.from(sourceType, false);
        if (symbol.isGlobal() || symbol.isStatic()) {
            String name = symbol.isStatic()
                    ? ".static." + builder.getFunction().getName() + "." + staticCounter++ + "." + symbol.getName()
                    : symbol.getName();
            Constant initializer = constantInitializer(symbol, type);
            GlobalVariable global = new GlobalVariable(name, initializer, symbol.isConst(), symbol.isStatic());
            module.addGlobal(global);
            addresses.put(symbol, global);
            return;
        }
        Value slot = builder.alloca(type);
        addresses.put(symbol, slot);
        if (init == null)
            return; // Uninitialized automatic objects stay uninitialized.
        if (!sourceType.isArray()) {
            Value value;
            if (symbol.isConst()) {
                int initialValue = symbol.getConstantValue().getValue();
                value = Constant.integer(type, initialValue);
            } else {
                InitVal initializer = (InitVal) init;
                value = emitValue(initializer.getExp());
            }
            builder.store(value, slot);
            return;
        }
        List<? extends ASTNode> elements;
        String string = null;
        if (init instanceof ConstInitVal c) {
            elements = c.getElements();
            if (c.getString() != null)
                string = c.getString().value();
        } else {
            InitVal v = (InitVal) init;
            elements = v.getElements();
            if (v.getString() != null)
                string = v.getString().value();
        }
        // Stores remain at declaration site, including declarations executed repeatedly inside loops.
        IrType elementType = type.element();
        for (int i = 0; i < sourceType.getLength(); i++) {
            Value value;
            if (symbol.isConst()) {
                int initialValue = symbol.getConstantArray().get(i);
                value = Constant.integer(elementType, initialValue);
            } else if (string != null) {
                int character = i < string.length() ? string.charAt(i) : 0;
                value = Constant.integer(elementType, character);
            } else if (elements != null && i < elements.size()) {
                ASTNode element = elements.get(i);
                value = emitValue(element);
            } else {
                value = Constant.integer(elementType, 0);
            }
            Constant index = integer(i);
            Value elementAddress = builder.gep(slot, index);
            builder.store(value, elementAddress);
        }
    }

    private Constant constantInitializer(SymbolVariable symbol, IrType type) {
        if (type.kind() != IrType.Kind.ARRAY) {
            int initialValue = symbol.getConstantValue().getValue();
            return Constant.integer(type, initialValue);
        }
        FixedArray values = symbol.getConstantArray();
        if (values == null)
            throw new IllegalStateException("Missing constant array initializer");
        List<Constant> elements = new ArrayList<>();
        IrType elementType = type.element();
        for (int i = 0; i < type.length(); i++) {
            int initialValue = values.get(i);
            Constant element = Constant.integer(elementType, initialValue);
            elements.add(element);
        }
        return Constant.array(type, elements);
    }

    /** Lvalues return an address, never the loaded value. Array parameters already are T*. */
    private Value emitAddress(LVal lval) {
        SymbolVariable symbol = lval.getSymbol();
        Value address = addresses.get(symbol);
        if (address == null)
            throw new IllegalStateException("Unbound storage: " + lval.getIdent().value());
        if (!lval.isArrayElement())
            return address;
        Value index = emitValue(lval.getIndex());
        return builder.gep(address, index);
    }

    private Constant integer(int n) {
        return Constant.integer(IrType.I32, n);
    }

    private Value emitValue(ASTNode node) {
        if (node instanceof Exp e)
            return emitValue(e.getAddExp());
        if (node instanceof ConstExp e)
            return emitValue(e.getAddExp());
        if (node instanceof Number n) {
            IrType type = n.getToken().is(TokenType.CharConst) ? IrType.I8 : IrType.I32;
            int value = n.calculateConst();
            return Constant.integer(type, value);
        }
        if (node instanceof LVal lval) {
            Value address = emitAddress(lval);
            Type type = info.typeOf(lval);
            if (type.isArray()) {
                SymbolVariable symbol = lval.getSymbol();
                Type sourceType = (Type) symbol.getType();
                if (sourceType.isArray() && symbol.isFromParam())
                    return address;
                Constant firstIndex = integer(0);
                return builder.gep(address, firstIndex);
            }
            // Mutable globals/static always load their current contents, irrespective of known initializers.
            return builder.load(address);
        }
        if (node instanceof PrimaryExp p)
            return switch (p.getKind()) {
                case Paren -> emitValue(p.getExp());
                case LVal -> emitValue(p.getLVal());
                case Number -> emitValue(p.getNumber());
            };
        if (node instanceof UnaryExp u)
            return switch (u.getKind()) {
                case Primary -> emitValue(u.getPrimary());
                case Cast -> {
                    Value operand = emitValue(u.getOperand());
                    Type sourceType = info.typeOf(u);
                    IrType targetType = IrType.from(sourceType, false);
                    yield builder.convert(operand, targetType);
                }
                case Call -> emitCall(u);
                case Unary -> {
                    Value value = emitValue(u.getOperand());
                    yield switch (u.getOp().getToken().getType()) {
                        case Plus -> value;
                        case Minus -> {
                            Constant zero = Constant.integer(value.getType(), 0);
                            yield builder.binary(Opcode.SUB, zero, value);
                        }
                        case Not -> {
                            Constant zero = Constant.integer(value.getType(), 0);
                            Value isZero = builder.compare("eq", value, zero);
                            yield builder.convert(isZero, IrType.I32);
                        }
                        default -> throw new IllegalStateException("Unknown unary operator");
                    };
                }
            };
        if (node instanceof BinaryExp binary) {
            if (binary.isLeaf())
                return emitValue(binary.getLeft());
            Value left = emitValue(binary.getLeft());
            Value right = emitValue(binary.getRight());
            boolean unsigned = left.getType().equals(IrType.I8);
            TokenType op = binary.getOp().getType();
            String predicate = switch (op) {
                case Eq -> "eq";
                case Neq -> "ne";
                case Lt -> unsigned ? "ult" : "slt";
                case Gt -> unsigned ? "ugt" : "sgt";
                case Leq -> unsigned ? "ule" : "sle";
                case Geq -> unsigned ? "uge" : "sge";
                default -> null;
            };
            if (predicate != null) {
                Value comparison = builder.compare(predicate, left, right);
                Type sourceType = info.typeOf(binary);
                IrType resultType = IrType.from(sourceType, false);
                return builder.convert(comparison, resultType);
            }
            Opcode opcode = switch (op) {
                case Plus -> Opcode.ADD;
                case Minus -> Opcode.SUB;
                case Mult -> Opcode.MUL;
                case Div -> unsigned ? Opcode.UDIV : Opcode.SDIV;
                case Mod -> unsigned ? Opcode.UREM : Opcode.SREM;
                default -> throw new IllegalStateException("Logical nodes require emitCond");
            };
            // i8 arithmetic wraps at every AST operation, without poison-producing nsw/nuw flags.
            return builder.binary(opcode, left, right);
        }
        throw new IllegalStateException("Unsupported expression: " + node.getClass());
    }

    private Value emitCall(UnaryExp call) {
        SymbolFunction symbol = info.functionOf(call);
        Function function = functions.get(symbol);
        if (function == null)
            function = module.getFunction(symbol.getName());
        List<Value> args = new ArrayList<>();
        if (call.getParams() != null) {
            for (Exp arg : call.getParams().getParams()) {
                Value argument = emitValue(arg);
                args.add(argument);
            }
        }
        return builder.call(function, args);
    }

    private void emitBlock(Block block) {
        for (BlockItem item : block.getItems()) {
            if (builder.isTerminated())
                break;
            if (item.getItem() instanceof Decl decl)
                emitDecl(decl);
            else
                emitStmt((Stmt) item.getItem());
        }
    }

    private void emitStmt(Stmt stmt) {
        if (stmt == null || builder.isTerminated())
            return;
        if (stmt instanceof BlockStmt s)
            emitBlock(s.getBlock());
        else if (stmt instanceof AssignStmt s) {
            Value address = emitAddress(s.getLVal());
            Value value = emitValue(s.getExp());
            builder.store(value, address);
        } else if (stmt instanceof ExpStmt s) {
            if (s.getExp() != null)
                emitValue(s.getExp());
        } else if (stmt instanceof ReturnStmt s) {
            // The existing frontend accepts a bare return in non-void functions; make it deterministic.
            Value value;
            if (s.hasExp()) {
                value = emitValue(s.getExp());
            } else {
                IrType returnType = builder.getFunction().getReturnType();
                value = returnType.equals(IrType.VOID) ? null : Constant.integer(returnType, 0);
            }
            builder.ret(value);
        } else if (stmt instanceof PrintfStmt s)
            emitPrintf(s);
        else if (stmt instanceof IfStmt s)
            emitIf(s);
        else if (stmt instanceof WhileStmt s)
            emitWhile(s);
        else if (stmt instanceof SwitchStmt s)
            emitSwitch(s);
        else if (stmt instanceof BreakStmt)
            builder.branch(breakTargets.peek());
        else if (stmt instanceof ContinueStmt)
            builder.branch(continueTargets.peek());
        else
            throw new IllegalStateException("Unsupported statement: " + stmt.getClass());
    }

    private void emitPrintf(PrintfStmt stmt) {
        List<Value> args = new ArrayList<>();
        if (stmt.getArgs() != null) {
            for (Exp arg : stmt.getArgs()) {
                Value argument = emitValue(arg);
                args.add(argument);
            }
        }
        String text = stmt.getFormat().value();
        StringBuilder literal = new StringBuilder();
        int index = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '%' && i + 1 < text.length() && "dcs".indexOf(text.charAt(i + 1)) >= 0) {
                emitText(literal.toString());
                literal.setLength(0);
                String name = switch (text.charAt(++i)) {
                    case 'd' -> "put_int";
                    case 'c' -> "put_char";
                    default -> "put_string";
                };
                Function function = module.getFunction(name);
                Value argument = args.get(index++);
                List<Value> arguments = List.of(argument);
                builder.call(function, arguments);
            } else
                literal.append(ch);
        }
        emitText(literal.toString());
    }

    private void emitText(String text) {
        if (text.isEmpty())
            return;
        Function function = module.getFunction("put_string");
        GlobalVariable string = module.internString(text);
        Constant firstIndex = integer(0);
        Value address = builder.gep(string, firstIndex);
        List<Value> arguments = List.of(address);
        builder.call(function, arguments);
    }

    /** Branch-based short circuit lowering; no speculative RHS evaluation and no phi nodes. */
    private void emitCond(ASTNode node, BasicBlock yes, BasicBlock no) {
        if (node instanceof Cond c) {
            emitCond(c.getLOrExp(), yes, no);
            return;
        }
        if (node instanceof BinaryExp b) {
            if (b.isLeaf()) {
                emitCond(b.getLeft(), yes, no);
                return;
            }
            if (b instanceof LOrExp || b instanceof LAndExp) {
                BasicBlock rhs = builder.newBlock();
                if (b instanceof LOrExp)
                    emitCond(b.getLeft(), yes, rhs);
                else
                    emitCond(b.getLeft(), rhs, no);
                builder.positionAt(rhs);
                emitCond(b.getRight(), yes, no);
                return;
            }
        }
        Value value = emitValue(node);
        Constant zero = Constant.integer(value.getType(), 0);
        Value condition = builder.compare("ne", value, zero);
        builder.branch(condition, yes, no);
    }

    private void emitIf(IfStmt stmt) {
        BasicBlock thenBlock = builder.newBlock();
        BasicBlock alternative = builder.newBlock();
        emitCond(stmt.getCond(), thenBlock, alternative);
        builder.positionAt(thenBlock);
        emitStmt(stmt.getThenStmt());
        BasicBlock thenEnd = builder.getBlock();
        builder.positionAt(alternative);
        if (!stmt.hasElse()) {
            builder.positionAt(thenEnd);
            builder.branchIfOpen(alternative);
            builder.positionAt(alternative);
            return;
        }
        emitStmt(stmt.getElseStmt());
        BasicBlock elseEnd = builder.getBlock();
        if (thenEnd.isTerminated() && elseEnd.isTerminated())
            return;
        BasicBlock merge = builder.newBlock();
        builder.positionAt(thenEnd);
        builder.branchIfOpen(merge);
        builder.positionAt(elseEnd);
        builder.branchIfOpen(merge);
        builder.positionAt(merge);
    }

    private void emitWhile(WhileStmt stmt) {
        BasicBlock condition = builder.newBlock();
        BasicBlock body = builder.newBlock();
        BasicBlock exit = builder.newBlock();
        builder.branch(condition);
        builder.positionAt(condition);
        emitCond(stmt.getCond(), body, exit);
        breakTargets.push(exit);
        continueTargets.push(condition);
        builder.positionAt(body);
        emitStmt(stmt.getBody());
        builder.branchIfOpen(condition);
        continueTargets.pop();
        breakTargets.pop();
        builder.positionAt(exit);
    }

    private void emitSwitch(SwitchStmt stmt) {
        Value selector = emitValue(stmt.getExp());
        BasicBlock exit = builder.newBlock();
        BasicBlock fallback = exit;
        List<BasicBlock> labels = new ArrayList<>();
        for (CaseStmt c : stmt.getCases()) {
            BasicBlock block = builder.newBlock();
            labels.add(block);
            if (c.isDefault())
                fallback = block;
        }
        for (int i = 0; i < stmt.getCases().size(); i++) {
            CaseStmt c = stmt.getCases().get(i);
            if (c.isDefault())
                continue;
            BasicBlock next = builder.newBlock();
            int caseValue = c.getNumber().calculateConst();
            Constant caseConstant = Constant.integer(selector.getType(), caseValue);
            Value matches = builder.compare("eq", selector, caseConstant);
            BasicBlock caseBlock = labels.get(i);
            builder.branch(matches, caseBlock, next);
            builder.positionAt(next);
        }
        builder.branch(fallback);
        breakTargets.push(exit);
        for (int i = 0; i < labels.size(); i++) {
            builder.positionAt(labels.get(i));
            for (Stmt body : stmt.getCases().get(i).getStmts())
                emitStmt(body);
            builder.branchIfOpen(i + 1 < labels.size() ? labels.get(i + 1) : exit);
        }
        breakTargets.pop();
        builder.positionAt(exit);
    }
}
