package frontend;

import error.ErrorTable;
import error.ErrorType;
import frontend.ast.ASTNode;
import frontend.ast.CompileUnit;
import frontend.ast.block.Block;
import frontend.ast.block.BlockItem;
import frontend.ast.declaration.ConstDecl;
import frontend.ast.declaration.ConstDef;
import frontend.ast.declaration.ConstInitVal;
import frontend.ast.declaration.Decl;
import frontend.ast.declaration.InitVal;
import frontend.ast.declaration.VarDecl;
import frontend.ast.declaration.VarDef;
import frontend.ast.expression.*;
import frontend.ast.expression.Number;
import frontend.ast.function.FuncDef;
import frontend.ast.function.FuncFormalParam;
import frontend.ast.function.FuncType;
import frontend.ast.function.MainFuncDef;
import frontend.ast.statement.AssignStmt;
import frontend.ast.statement.BlockStmt;
import frontend.ast.statement.BreakStmt;
import frontend.ast.statement.CaseStmt;
import frontend.ast.statement.ContinueStmt;
import frontend.ast.statement.ExpStmt;
import frontend.ast.statement.IfStmt;
import frontend.ast.statement.PrintfStmt;
import frontend.ast.statement.ReturnStmt;
import frontend.ast.statement.Stmt;
import frontend.ast.statement.SwitchStmt;
import frontend.ast.statement.WhileStmt;
import frontend.symbol.SymbolFunction;
import frontend.symbol.SymbolTable;
import frontend.symbol.SymbolVariable;
import frontend.symbol.type.SymbolType;
import frontend.symbol.type.Type;
import frontend.token.Token;
import frontend.token.TokenType;
import utils.ConstFolder;
import utils.Controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final public class Traverser {

    private final CompileUnit ast;
    private final ErrorTable errors = Controller.errors;
    private final SymbolTable symbols = Controller.symbols;

    private boolean returnTypeIsVoid = false;
    private Type currentFunctionType = null;

    private int loopDepth = 0;
    private int breakDepth = 0;

    private final Map<ASTNode, Type> types = new IdentityHashMap<>();

    private boolean insideCondition = false;

    public Traverser(CompileUnit ast) {
        this.ast = ast;
    }

    public void spawn() {
        for (Decl decl : ast.getDeclarations()) {
            analyzeDecl(decl);
        }
        for (FuncDef func : ast.getFunctions()) {
            analyzeFuncDef(func);
        }
        analyzeMainFuncDef(ast.getMainFunc());
    }

    private void analyzeFuncDef(FuncDef func) {
        SymbolFunction symbol = symbols.addFunction(
                func.getIdent().value(), func.getFuncType());
        if (symbol == null) {
            //b:DuplicatedDeclaration (still analyse the body)
            errors.add(ErrorType.DuplicatedDeclaration, func.getIdent().location());
            symbol = new SymbolFunction(
                    func.getIdent().value(), Type.from(func.getFuncType()), symbols.currentScopeId());
        }

        symbols.newScope();
        List<FuncFormalParam> params = func.getParams() == null
                ? List.of() : func.getParams().getParams();
        for (FuncFormalParam param : params) {
            Type paramType = paramTypeOf(param);
            if (symbols.addParameter(symbol, param.getIdent().value(), paramType) == null) {
                //b:DuplicatedDeclaration
                errors.add(ErrorType.DuplicatedDeclaration, param.getIdent().location());
                // 重名只影响作用域中的定义，不能改变函数声明的形参数量。
                SymbolVariable duplicate = new SymbolVariable(param.getIdent().value(),
                        paramType, false, symbols.currentScopeId());
                duplicate.setFromParam();
                symbol.addParameter(duplicate);
            }
        }

        Type declaredReturn = Type.from(func.getFuncType());
        returnTypeIsVoid = func.getFuncType().isVoid();
        currentFunctionType = declaredReturn;
        visitBlock(func.getBlock());
        checkMissingReturn(func.getBlock(), func.getFuncType().isVoid());
        symbols.exitScope();
    }

    private void analyzeMainFuncDef(MainFuncDef main) {
        if (main == null) {
            return;
        }
        symbols.newScope();
        returnTypeIsVoid = false;
        currentFunctionType = Type.Int;
        visitBlock(main.getBlock());
        checkMissingReturn(main.getBlock(), false);
        symbols.exitScope();
    }

    private void checkMissingReturn(Block body, boolean isVoid) {
        if (isVoid || body == null) {
            return;
        }
        List<BlockItem> items = body.getItems();
        if (!items.isEmpty() && items.get(items.size() - 1).getItem() instanceof ReturnStmt) {
            return;
        }
        Token end = body.getEndToken();
        if (end == null) {
            return;
        }
        //g:MissingReturnStatement
        errors.add(ErrorType.MissingReturnStatement, end.location());
    }

    private Type paramTypeOf(FuncFormalParam param) {
        Type base = param.getBType().is(TokenType.Char) ? Type.Char : Type.Int;
        return param.isArray() ? Type.ofArray(base, 0) : base;
    }


    private void analyzeDecl(Decl decl) {
        if (decl instanceof ConstDecl constDecl) {
            analyzeConstDecl(constDecl);
        } else if (decl instanceof VarDecl varDecl) {
            analyzeVarDecl(varDecl);
        }
    }

    private void analyzeConstDecl(ConstDecl decl) {
        Type base = decl.getBType().is(TokenType.Char) ? Type.Char : Type.Int;
        for (ConstDef def : decl.getDefs()) {
            Type declared = declaredType(def.getLength(), base, def.getIdent());
            SymbolVariable symbol = symbols.addVariable(def.getIdent().value(), declared);
            if (symbol == null) {
                //b:DuplicatedDeclaration
                errors.add(ErrorType.DuplicatedDeclaration, def.getIdent().location());
                continue;
            }
            symbol.setConst();
            analyzeConstInitVal(def.getInitVal(), declared);
            recordConstantValue(symbol, declared, def.getInitVal());
        }
    }

    private void analyzeVarDecl(VarDecl decl) {
        Type base = decl.getBType().is(TokenType.Char) ? Type.Char : Type.Int;
        for (VarDef def : decl.getDefs()) {
            Type declared = declaredType(def.getLength(), base, def.getIdent());
            SymbolVariable symbol = symbols.addVariable(def.getIdent().value(), declared);
            if (symbol == null) {
                //b:DuplicatedDeclaration
                errors.add(ErrorType.DuplicatedDeclaration, def.getIdent().location());
                continue;
            }
            if (decl.isStatic()) {
                symbol.setStatic();
            }
            analyzeInitVal(def.getInitVal(), declared, symbol.isGlobal());
            if (symbol.isGlobal() || symbol.isStatic()) {
                recordVariableValue(symbol, declared, def.getInitVal());
            }
        }
    }

    private Type declaredType(ConstExp length, Type base, Token ident) {
        if (length == null) {
            return base;
        }
        Type lengthType = analyzeConstExp(length);
        if (lengthType != null && !lengthType.isInt()) {
            errors.add(ErrorType.MismatchedParameterType, lineOf(length));
        }
        Integer size = ConstFolder.fold(length);
        if (size == null || size <= 0) {
            return Type.ofArray(base, 0);
        }
        return Type.ofArray(base, size);
    }

    private void recordConstantValue(SymbolVariable symbol, Type declared, ConstInitVal init) {
        if (init == null) {
            return;
        }
        if (!declared.isArray()) {
            Integer value = ConstFolder.fold(init.getExp());
            if (value != null) {
                symbol.setConstantValue(new frontend.symbol.FixedValue(value));
            }
            return;
        }
        if (declared.getLength() > 0) {
            symbol.setConstantValue(frontend.symbol.FixedArray.from(
                    declared.getLength(), init));
        }
    }

    private void recordVariableValue(SymbolVariable symbol, Type declared, InitVal init) {
        if (!declared.isArray()) {
            if (init == null) {
                symbol.setConstantValue(new frontend.symbol.FixedValue(0));
                return;
            }
            Integer value = ConstFolder.fold(init.getExp());
            if (value != null) {
                symbol.setConstantValue(new frontend.symbol.FixedValue(value));
            }
            return;
        }
        if (declared.getLength() <= 0) {
            return;
        }
        if (init == null) {
            symbol.setConstantValue(new frontend.symbol.FixedArray(declared.getLength()));
            return;
        }
        symbol.setConstantValue(frontend.symbol.FixedArray.from(declared.getLength(), init));
    }

    private void analyzeConstInitVal(ConstInitVal init, Type declared) {
        if (init == null) {
            return;
        }
        if (!declared.isArray()) {
            if (init.getKind() == ConstInitVal.Kind.Exp) {
                Type actual = analyzeConstExp(init.getExp());
                checkInitializerType(actual, declared, init.getExp());
            }
            return;
        }
        if (init.getKind() == ConstInitVal.Kind.Str) {
            checkStringInitializer(init.getString(), declared);
            return;
        }
        analyzeConstElements(init.getElements(), declared);
    }

    private void analyzeInitVal(InitVal init, Type declared, boolean constContext) {
        if (init == null) {
            return;
        }
        if (!declared.isArray()) {
            if (init.getKind() == InitVal.Kind.Exp) {
                Type actual = analyzeAddExp(init.getExp().getAddExp(), constContext);
                checkInitializerType(actual, declared, init.getExp());
            }
            return;
        }
        if (init.getKind() == InitVal.Kind.Str) {
            checkStringInitializer(init.getString(), declared);
            return;
        }
        List<Exp> elements = init.getElements();
        if (elements == null) {
            return;
        }
        if (elements.size() > declared.getLength()) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType, initTokenLine(init));
        }
        for (Exp element : elements) {
            Type actual = analyzeAddExp(element.getAddExp(), constContext);
            checkInitializerType(actual, declared.getElementType(), element);
        }
    }

    private void analyzeConstElements(List<ConstExp> elements, Type declared) {
        if (elements == null) {
            return;
        }
        for (ConstExp element : elements) {
            Type actual = analyzeConstExp(element);
            checkInitializerType(actual, declared.getElementType(), element);
        }
    }

    private void checkStringInitializer(Token string, Type declared) {
        if (string == null) {
            return;
        }
        // 文法允许字符串按数组长度截断，不要求为结尾的 '\0' 额外留空间。
        if (!declared.getElementType().isChar()) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType, string.location());
        }
    }

    private Navigation initTokenLine(InitVal init) {
        if (init.getElements() != null && !init.getElements().isEmpty()) {
            return lineOf(init.getElements().get(0));
        }
        return DeclarationFallback;
    }

    private void checkInitializerType(Type actual, Type declared, ASTNode node) {
        if (actual == null || declared == null) {
            return;
        }
        if (!compatible(declared, actual)) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType, lineOf(node));
        }
    }

    private void visitBlock(Block block) {
        if (block == null) {
            return;
        }
        for (BlockItem item : block.getItems()) {
            visitBlockItem(item);
        }
    }

    private void visitBlockItem(BlockItem item) {
        if (item.getType() == BlockItem.Type.Decl) {
            analyzeDecl((Decl) item.getItem());
        } else {
            visitStmt((Stmt) item.getItem());
        }
    }

    private void visitStmt(Stmt stmt) {
        if (stmt == null) {
            return;
        }
        if (stmt instanceof BlockStmt blockStmt) {
            symbols.newScope();
            visitBlock(blockStmt.getBlock());
            symbols.exitScope();
        } else if (stmt instanceof IfStmt ifStmt) {
            analyzeCond(ifStmt.getCond());
            visitStmt(ifStmt.getThenStmt());
            visitStmt(ifStmt.getElseStmt());
        } else if (stmt instanceof WhileStmt whileStmt) {
            analyzeCond(whileStmt.getCond());
            loopDepth++;
            breakDepth++;
            visitStmt(whileStmt.getBody());
            loopDepth--;
            breakDepth--;
        } else if (stmt instanceof SwitchStmt switchStmt) {
            analyzeSwitch(switchStmt);
        } else if (stmt instanceof BreakStmt breakStmt) {
            if (breakDepth == 0) {
                //m:InvalidLoopControl
                errors.add(ErrorType.InvalidLoopControl, breakStmt.getToken().location());
            }
        } else if (stmt instanceof ContinueStmt continueStmt) {
            if (loopDepth == 0) {
                //m:InvalidLoopControl
                errors.add(ErrorType.InvalidLoopControl, continueStmt.getToken().location());
            }
        } else if (stmt instanceof ReturnStmt returnStmt) {
            analyzeReturn(returnStmt);
        } else if (stmt instanceof PrintfStmt printfStmt) {
            analyzePrintf(printfStmt);
        } else if (stmt instanceof AssignStmt assignStmt) {
            analyzeAssign(assignStmt);
        } else if (stmt instanceof ExpStmt expStmt) {
            if (expStmt.getExp() != null) {
                analyzeExp(expStmt.getExp());
            }
        }
    }

    // Stmt -> 'switch' '(' Exp ')' '{' { CaseStmt } '}'
    private void analyzeSwitch(SwitchStmt stmt) {
        Type selector = analyzeExp(stmt.getExp());
        boolean selectorIsBasic = selector != null && selector.isBasic();
        if (selector != null && !selectorIsBasic) {
            errors.add(ErrorType.MismatchedParameterType, lineOf(stmt.getExp()));
        }

        Set<Integer> usedLabels = new HashSet<>();
        boolean defaultSeen = false;

        breakDepth++;           // switch 里 break 合法
        try {
            for (CaseStmt caseStmt : stmt.getCases()) {
                if (caseStmt.isDefault()) {
                    if (defaultSeen) {
                        //n:DuplicatedCaseLabel
                        errors.add(ErrorType.DuplicatedCaseLabel, caseStmt.getKeyword().location());
                    }
                    defaultSeen = true;
                } else {
                    Number number = caseStmt.getNumber();
                    Integer value = ConstFolder.fold(number);
                    if (value != null) {
                        if (!usedLabels.add(value)) {
                            //n:DuplicatedCaseLabel
                            errors.add(ErrorType.DuplicatedCaseLabel,
                                    caseStmt.getKeyword().location());
                        }
                    }
                    Type labelType = number.getToken().is(TokenType.CharConst) ? Type.Char : Type.Int;
                    if (selectorIsBasic && !labelType.equals(selector)) {
                        //e:MismatchedParameterType
                        errors.add(ErrorType.MismatchedParameterType,
                                number.getToken().location());
                    }
                }
                for (Stmt body : caseStmt.getStmts()) {
                    visitStmt(body);
                }
            }
        } finally {
            breakDepth--;
        }
    }

    // Stmt -> LVal '=' Exp ';'
    private void analyzeAssign(AssignStmt stmt) {
        LVal target = stmt.getLVal();
        Type left = analyzeLVal(target, false);
        SymbolVariable symbol = target == null ? null : target.getSymbol();
        if (symbol != null && symbol.isConst()) {
            //h:AssignToConstant
            errors.add(ErrorType.AssignToConstant, target.getIdent().location());
        }
        Type right = analyzeExp(stmt.getExp());
        if (left != null && right != null && (!left.isBasic() || !left.equals(right))) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType, lineOf(target));
        }
    }

    // Stmt -> 'return' [Exp] ';'
    private void analyzeReturn(ReturnStmt stmt) {
        if (!stmt.hasExp()) {
            return;
        }
        Type actual = analyzeExp(stmt.getExp());
        if (returnTypeIsVoid) {
            //f:MismatchedReturnType
            errors.add(ErrorType.MismatchedReturnType, stmt.getReturnToken().location());
            return;
        }
        Type declared = currentFunctionType;
        if (actual != null && declared != null
                && !actual.equals(declared)) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType, stmt.getReturnToken().location());
        }
    }

    // Stmt -> 'printf' '(' StringConst { ',' Exp } ')' ';'
    private void analyzePrintf(PrintfStmt stmt) {
        List<Character> specs = formatSpecifiers(stmt.getFormat());
        List<Exp> args = stmt.getArgs() == null ? List.of() : stmt.getArgs();

        // 先分析实参，拿到它们的类型
        List<Type> argTypes = new ArrayList<>();
        for (Exp arg : args) {
            argTypes.add(analyzeExp(arg));
        }

        if (specs.size() != args.size()) {
            //l:MismatchedFormatArgument
            errors.add(ErrorType.MismatchedFormatArgument, stmt.getPrintfToken().location());
            return;
        }

        // e: 格式字符与实参类型必须对应  %d -> int，%c -> char，%s -> char[]
        for (int i = 0; i < specs.size(); i++) {
            Type actual = argTypes.get(i);
            if (actual == null) {
                continue;
            }
            if (!matchesFormat(specs.get(i), actual)) {
                errors.add(ErrorType.MismatchedParameterType, stmt.getPrintfToken().location());
            }
        }
    }

    private List<Character> formatSpecifiers(Token format) {
        List<Character> specs = new ArrayList<>();
        if (format == null) {
            return specs;
        }
        String text = format.value();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '%' || i + 1 >= text.length()) {
                continue;
            }
            char spec = text.charAt(++i);
            if (spec != '%') {
                specs.add(spec);
            }
        }
        return specs;
    }

    private boolean matchesFormat(char spec, Type actual) {
        return switch (spec) {
            case 'd' -> actual.isInt();
            case 'c' -> actual.isChar();
            case 's' -> actual.isArray() && actual.getElementType().isChar();
            default -> true;
        };
    }

    private Type analyzeExp(Exp exp) {
        if (exp == null) {
            return null;
        }
        Type cached = types.get(exp);
        if (cached != null) {
            return cached;
        }
        Type type = analyzeAddExp(exp.getAddExp(), false);
        put(exp, type);
        return type;
    }

    private Type analyzeConstExp(ConstExp exp) {
        if (exp == null) {
            return null;
        }
        Type cached = types.get(exp);
        if (cached != null) {
            return cached;
        }
        Type type = analyzeAddExp(exp.getAddExp(), true);
        put(exp, type);
        return type;
    }

    private <T> T withConditionContext(java.util.function.Supplier<T> action) {
        boolean saved = insideCondition;
        insideCondition = true;
        try {
            return action.get();
        } finally {
            insideCondition = saved;
        }
    }

    private Type analyzeCond(Cond cond) {
        if (cond == null) {
            return null;
        }
        Type type = withConditionContext(() -> analyzeLOr(cond.getLOrExp()));
        put(cond, type);
        return type;
    }

    private Type analyzeLOr(ASTNode node) {
        return analyzeBinary(node, true, false);
    }

    private Type analyzeLAnd(ASTNode node) {
        return analyzeBinary(node, true, false);
    }

    private Type analyzeEq(ASTNode node) {
        return analyzeBinary(node, false, false);
    }

    private Type analyzeRel(ASTNode node) {
        return analyzeBinary(node, false, false);
    }

    private Type analyzeAddExp(ASTNode node, boolean constContext) {
        return analyzeBinary(node, false, constContext);
    }

    private Type analyzeMulExp(ASTNode node, boolean constContext) {
        return analyzeBinary(node, false, constContext);
    }

    private Type analyzeBinary(ASTNode node, boolean resultIsInt, boolean constContext) {
        if (node == null) {
            return null;
        }
        Type cached = types.get(node);
        if (cached != null) {
            return cached;
        }
        if (!(node instanceof BinaryExp binary)) {
            return null;
        }
        Type left = analyzeOperand(binary.getLeft(), constContext);
        if (binary.isLeaf()) {
            Type result = resultIsInt && left != null ? Type.Int : left;
            if (resultIsInt && left != null && !left.isBasic()) {
                errors.add(ErrorType.MismatchedParameterType, lineOf(binary.getLeft()));
            }
            put(node, result);
            return result;
        }
        Type right = analyzeOperand(binary.getRight(), constContext);

        Type result;
        if (resultIsInt) {
            checkLogicalOperand(left, binary.getOp());
            checkLogicalOperand(right, binary.getOp());
            result = Type.Int;
        } else {
            result = checkSameType(left, right, binary.getOp());
        }
        put(node, result);
        return result;
    }

    private void checkLogicalOperand(Type operand, Token op) {
        if (operand == null || operand.isBasic()) {
            return;
        }
        //e:MismatchedParameterType
        errors.add(ErrorType.MismatchedParameterType,
                op == null ? DeclarationFallback : op.location());
    }

    private Type analyzeOperand(ASTNode node, boolean constContext) {
        if (node == null) {
            return null;
        }
        if (node instanceof BinaryExp) {
            Type cached = types.get(node);
            if (cached != null) {
                return cached;
            }
            if (node instanceof LOrExp) {
                return analyzeLOr(node);
            }
            if (node instanceof LAndExp) {
                return analyzeLAnd(node);
            }
            if (node instanceof EqExp) {
                return analyzeEq(node);
            }
            if (node instanceof RelExp) {
                return analyzeRel(node);
            }
            if (node instanceof AddExp) {
                return analyzeAddExp(node, constContext);
            }
            if (node instanceof MulExp) {
                return analyzeMulExp(node, constContext);
            }
            return null;
        }
        if (node instanceof UnaryExp unary) {
            return analyzeUnaryExp(unary, constContext);
        }
        return null;
    }

    private Type checkSameType(Type left, Type right, Token op) {
        if (left == null || right == null) {
            return null;
        }
        if (left.equals(right) && left.isBasic()) {
            return left;
        }
        //e:MismatchedParameterType
        errors.add(ErrorType.MismatchedParameterType,
                op == null ? DeclarationFallback : op.location());
        return null;
    }

    private Type analyzeUnaryExp(UnaryExp exp, boolean constContext) {
        if (exp == null) {
            return null;
        }
        Type cached = types.get(exp);
        if (cached != null) {
            return cached;
        }
        Type type = switch (exp.getKind()) {
            case Primary -> analyzePrimaryExp(exp.getPrimary(), constContext);
            case Cast -> analyzeCast(exp, constContext);
            case Unary -> analyzeUnaryOp(exp, constContext);
            case Call -> analyzeCall(exp, constContext);
        };
        put(exp, type);
        return type;
    }

    private Type analyzeCast(UnaryExp exp, boolean constContext) {
        Type operand = analyzeUnaryExp(exp.getOperand(), constContext);
        if (operand != null && !operand.isBasic()) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType, lineOf(exp));
        }
        checkConsecutiveUnary(exp, operand);
        return exp.getCastType().is(TokenType.Char) ? Type.Char : Type.Int;
    }

    private Type analyzeUnaryOp(UnaryExp exp, boolean constContext) {
        Type operand = analyzeUnaryExp(exp.getOperand(), constContext);
        if (exp.getOp() != null && exp.getOp().getToken().is(TokenType.Not) && !insideCondition) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType, exp.getOp().getToken().location());
        }
        checkConsecutiveUnary(exp, operand);

        if (operand != null && !operand.isBasic()) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType,
                    exp.getOp().getToken().location());
            return null;
        }
        return exp.getOp().getToken().is(TokenType.Not) ? Type.Int : operand;
    }

    private void checkConsecutiveUnary(UnaryExp outer, Type operandType) {
        if (operandType == null || outer.getOp() == null) {
            return;
        }
        // getOperand() 的返回类型本就是 UnaryExp，且只有 Unary/Cast 两种形态才非空，
        // 所以普通空值判断即可，不必用 instanceof 模式（保持 Java 17 语言级别兼容）。
        UnaryExp inner = outer.getOperand();
        if (inner == null) {
            return;
        }
        if (inner.getKind() != UnaryExp.Kind.Unary || inner.getOp() == null) {
            return;
        }
        if (outer.getOp().getToken().is(inner.getOp().getToken())) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType, outer.getOp().getToken().location());
        }
    }

    // UnaryExp -> Ident '(' [FuncRParams] ')'
    private Type analyzeCall(UnaryExp exp, boolean constContext) {
        Token ident = exp.getIdent();
        List<Exp> args = exp.getParams() == null
                ? List.of() : exp.getParams().getParams();

        List<Type> argTypes = new ArrayList<>();
        for (Exp arg : args) {
            argTypes.add(analyzeExp(arg));
        }

        SymbolFunction function = symbols.getFunction(ident.value());
        if (symbols.getVariable(ident.value()) != null) {
            // 已定义的变量不能当函数调用；局部变量也会隐藏同名的全局函数。
            errors.add(ErrorType.MismatchedParameterType, ident.location());
            return null;
        }
        if (function == null) {
            //c:UndefinedReference
            errors.add(ErrorType.UndefinedReference, ident.location());
            return null;
        }
        if (constContext) {
            //c:UndefinedReference
            errors.add(ErrorType.UndefinedReference, ident.location());
            return null;
        }
        List<SymbolVariable> params = function.getParameters();
        if (args.size() != params.size()) {
            //d:MismatchedParameterCount
            errors.add(ErrorType.MismatchedParameterCount, ident.location());
            return function.getType() == null ? null : (Type) function.getType();
        }
        for (int i = 0; i < params.size(); i++) {
            Type formal = (Type) params.get(i).getType();
            Type actual = argTypes.get(i);
            if (actual == null) {
                continue;
            }
            if (!compatible(formal, actual)) {
                //e:MismatchedParameterType
                errors.add(ErrorType.MismatchedParameterType, ident.location());
                break;
            }
        }
        return (Type) function.getType();
    }

    private Type analyzePrimaryExp(PrimaryExp exp, boolean constContext) {
        if (exp == null) {
            return null;
        }
        Type cached = types.get(exp);
        if (cached != null) {
            return cached;
        }
        Type type = switch (exp.getKind()) {
            case Paren -> analyzeAddExp(exp.getExp().getAddExp(), constContext);
            case LVal -> analyzeLVal(exp.getLVal(), constContext);
            case Number -> analyzeNumber(exp.getNumber());
        };
        put(exp, type);
        return type;
    }

    private Type analyzeNumber(Number number) {
        if (number == null || number.getToken() == null) {
            return null;
        }
        return number.getToken().is(TokenType.CharConst) ? Type.Char : Type.Int;
    }

    private Type analyzeLVal(LVal lval, boolean constContext) {
        if (lval == null) {
            return null;
        }
        Type cached = types.get(lval);
        if (cached != null) {
            return cached;
        }
        Token ident = lval.getIdent();
        SymbolVariable symbol = symbols.getVariable(ident.value());
        if (symbol == null) {
            errors.add(symbols.getFunction(ident.value()) == null
                    ? ErrorType.UndefinedReference : ErrorType.MismatchedParameterType,
                    ident.location());
            return null;
        }
        if (constContext && !symbol.isConst()) {
            //c:UndefinedReference
            errors.add(ErrorType.UndefinedReference, ident.location());
            return null;
        }
        lval.setSymbol(symbol);
        Type type = (Type) symbol.getType();

        if (!lval.isArrayElement()) {
            put(lval, type);
            return type;
        }
        if (!type.isArray()) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType, ident.location());
            return null;
        }
        Type index = analyzeAddExp(lval.getIndex().getAddExp(), constContext);
        if (index != null && !index.isInt()) {
            //e:MismatchedParameterType
            errors.add(ErrorType.MismatchedParameterType, ident.location());
        }
        Type element = type.getElementType();
        put(lval, element);
        return element;
    }

    private void put(ASTNode node, Type type) {
        if (node != null && type != null) {
            types.put(node, type);
            if (node instanceof BinaryExp binary) {
                binary.setCharResult(type.isChar());
            } else if (node instanceof UnaryExp unary) {
                unary.setCharResult(type.isChar());
            }
        }
    }

    private boolean compatible(Type formal, Type actual) {
        if (formal == null || actual == null) {
            return true;
        }
        if (formal.isArray() || actual.isArray()) {
            return formal.isPassableTo(actual) || actual.isPassableTo(formal);
        }
        return formal.equals(actual);
    }

    private Navigation lineOf(ASTNode node) {
        if (node instanceof LVal lval && lval.getIdent() != null) {
            return lval.getIdent().location();
        }
        if (node instanceof Number number && number.getToken() != null) {
            return number.getToken().location();
        }
        if (node instanceof Exp exp) {
            return lineOf(exp.getAddExp());
        }
        if (node instanceof ConstExp constExp) {
            return lineOf(constExp.getAddExp());
        }
        if (node instanceof BinaryExp binary && binary.getOp() != null) {
            return binary.getOp().location();
        }
        if (node instanceof BinaryExp binary && binary.getLeft() != null) {
            return lineOf(binary.getLeft());
        }
        if (node instanceof UnaryExp unary) {
            return switch (unary.getKind()) {
                case Call -> unary.getIdent() == null
                        ? DeclarationFallback : unary.getIdent().location();
                case Cast -> unary.getCastType() == null
                        ? DeclarationFallback : unary.getCastType().location();
                case Unary -> lineOf(unary.getOperand());
                case Primary -> lineOf(unary.getPrimary());
            };
        }
        if (node instanceof PrimaryExp primary) {
            return switch (primary.getKind()) {
                case LVal -> lineOf(primary.getLVal());
                case Number -> lineOf(primary.getNumber());
                case Paren -> lineOf(primary.getExp());
            };
        }
        return DeclarationFallback;
    }

    private static final Navigation DeclarationFallback =
            new Navigation(new utils.Pair<>(0, 0), new utils.Pair<>(0, 0));
}
