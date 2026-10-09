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
import frontend.ast.expression.BinaryExp;
import frontend.ast.expression.Cond;
import frontend.ast.expression.ConstExp;
import frontend.ast.expression.Exp;
import frontend.ast.expression.LVal;
import frontend.ast.expression.Number;
import frontend.ast.expression.PrimaryExp;
import frontend.ast.expression.UnaryExp;
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

/**
 * 语义分析：遍历 AST，建立符号表并报出错误 b ~ n。
 *
 * <p>结构沿用 2024 参考实现的 {@code Traverser}，但按 2026 文法做了这些改动：
 * <ul>
 *   <li><b>类型严格相等</b>：{@code int} 与 {@code char} 不允许隐式混用，一切类型比较都用
 *       {@link Type#equals}；数组实参传给数组形参走 {@link Type#isPassableTo}。</li>
 *   <li><b>错误 e 覆盖 7 类场景</b>：多类型混合运算、返回值、赋值、实参、{@code case} 值、
 *       {@code printf} 格式、函数与变量互相误用。参考实现只覆盖其中 3 类。</li>
 *   <li><b>新增 switch / case</b>：参考实现的语句分派里没有这两种，错误 n 也完全缺失。</li>
 *   <li><b>m 需要两个深度</b>：{@code break} 在循环或 {@code switch} 里都合法，
 *       {@code continue} 只在循环里合法，单个 {@code loopDepth} 表达不了。</li>
 *   <li><b>g 只看函数末尾</b>：文档要求如此，不做控制流分析。</li>
 *   <li><b>数组相关检查</b>：下标必须是 {@code int}、不能对非数组做下标访问、数组长度必须为正。</li>
 *   <li><b>ConstExp 的折叠是"尽力而为"</b>：折不出来只影响常量值的记录，不额外报错
 *       （语法阶段已保证出现在这些位置的表达式是 ConstExp）。</li>
 * </ul>
 */
final public class Traverser {

    private final CompileUnit ast;
    private final ErrorTable errors = Controller.errors;
    private final SymbolTable symbols = Controller.symbols;

    /** 当前函数是否是 {@code void}；决定 return 语句是否允许带值。 */
    private boolean returnTypeIsVoid = false;
    /** 当前函数的返回类型，用于检查返回值的类型。 */
    private Type currentFunctionType = null;
    /** 当前函数是否出现过带值的 {@code return}；用于错误 g。 */
    private boolean returnWithValueFound = false;

    /** 循环嵌套深度，用于 {@code continue} 的合法性。 */
    private int loopDepth = 0;
    /** 循环或 {@code switch} 的嵌套深度，用于 {@code break} 的合法性。 */
    private int breakDepth = 0;

    /** 每个表达式的类型，由 {@link #analyzeExp} 填充。 */
    private final Map<ASTNode, Type> types = new IdentityHashMap<>();

    public Traverser(CompileUnit ast) {
        this.ast = ast;
    }

    // ================================================================== 入口

    /** 遍历整个编译单元，填充 {@link Controller#symbols} 并报错。 */
    public void spawn() {
        // 先分析全局声明，让所有全局名字（含前向引用）在函数体里都可见
        for (Decl decl : ast.getDeclarations()) {
            analyzeDecl(decl);
        }
        // 再逐个函数
        for (FuncDef func : ast.getFunctions()) {
            analyzeFuncDef(func);
        }
        analyzeMainFuncDef(ast.getMainFunc());
    }

    // ================================================================== 函数

    private void analyzeFuncDef(FuncDef func) {
        SymbolFunction symbol = symbols.addFunction(
                func.getIdent().value(), func.getFuncType());
        if (symbol == null) {
            // 错误 b：但函数体仍然要完整分析，否则会漏掉体内的其它错误
            errors.add(ErrorType.DuplicatedDeclaration, func.getIdent().location());
            symbol = new SymbolFunction(
                    func.getIdent().value(), Type.from(func.getFuncType()), symbols.currentScopeId());
        }

        // 形参与函数体最外层共用一个作用域号：只开一次作用域
        symbols.newScope();
        List<FuncFormalParam> params = func.getParams() == null
                ? List.of() : func.getParams().getParams();
        for (FuncFormalParam param : params) {
            Type paramType = paramTypeOf(param);
            if (symbols.addParameter(symbol, param.getIdent().value(), paramType) == null) {
                errors.add(ErrorType.DuplicatedDeclaration, param.getIdent().location());
            }
        }

        Type declaredReturn = Type.from(func.getFuncType());
        returnTypeIsVoid = func.getFuncType().isVoid();
        currentFunctionType = declaredReturn;
        returnWithValueFound = false;
        visitBlock(func.getBlock());
        checkMissingReturn(func.getBlock(), func.getFuncType().isVoid());
        symbols.exitScope();
    }

    private void analyzeMainFuncDef(MainFuncDef main) {
        if (main == null) {
            return;
        }
        // main 不进符号表（它是关键字），但函数体要照常分析，且必须有 return
        symbols.newScope();
        returnTypeIsVoid = false;
        currentFunctionType = Type.Int;
        returnWithValueFound = false;
        visitBlock(main.getBlock());
        checkMissingReturn(main.getBlock(), false);
        symbols.exitScope();
    }

    /** 错误 g：非 void 函数必须在函数体<b>末尾</b>出现带值的 {@code return}。 */
    private void checkMissingReturn(Block body, boolean isVoid) {
        if (isVoid || returnWithValueFound || body == null) {
            return;
        }
        Token end = body.getEndToken();
        if (end == null) {
            return;
        }
        errors.add(ErrorType.MissingReturnStatement, end.location());
    }

    /** 形参类型：{@code int x[]} 的元素类型是 int，但整体是"长度未知的数组"。 */
    private Type paramTypeOf(FuncFormalParam param) {
        Type base = param.getBType().is(TokenType.Char) ? Type.Char : Type.Int;
        return param.isArray() ? Type.ofArray(base, 0) : base;
    }

    // ================================================================== 声明

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
                errors.add(ErrorType.DuplicatedDeclaration, def.getIdent().location());
                continue;
            }
            if (decl.isStatic()) {
                symbol.setStatic();
            }
            analyzeInitVal(def.getInitVal(), declared);
            // 全局变量与 static 局部变量的初值必须是常量，这里记下它们的值
            if (symbol.isGlobal() || symbol.isStatic()) {
                recordVariableValue(symbol, declared, def.getInitVal());
            }
        }
    }

    /**
     * 求出定义的类型。
     *
     * <p>数组长度必须是能折出来的正整数；折不出来时退化成"长度未知的数组"，只影响后续
     * 下标检查的精度，不再额外报错。
     */
    private Type declaredType(ConstExp length, Type base, Token ident) {
        if (length == null) {
            return base;
        }
        Integer size = ConstFolder.fold(length);
        if (size == null || size <= 0) {
            return Type.ofArray(base, 0);
        }
        return Type.ofArray(base, size);
    }

    /** 把常量标量/数组的折叠结果记进符号，供常量表达式求值使用。 */
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

    // ================================================================== 初值

    private void analyzeConstInitVal(ConstInitVal init, Type declared) {
        if (init == null) {
            return;
        }
        if (!declared.isArray()) {
            if (init.getKind() == ConstInitVal.Kind.Exp) {
                analyzeConstExp(init.getExp());
            }
            return;
        }
        if (init.getKind() == ConstInitVal.Kind.Str) {
            checkStringLength(init.getString(), declared);
            return;
        }
        analyzeConstElements(init.getElements(), declared);
    }

    private void analyzeInitVal(InitVal init, Type declared) {
        if (init == null) {
            return;
        }
        if (!declared.isArray()) {
            if (init.getKind() == InitVal.Kind.Exp) {
                analyzeExp(init.getExp());
            }
            return;
        }
        if (init.getKind() == InitVal.Kind.Str) {
            checkStringLength(init.getString(), declared);
            return;
        }
        // 列表初始化：逐个元素检查类型，元素个数不能超过数组长度
        List<Exp> elements = init.getElements();
        if (elements == null) {
            return;
        }
        if (elements.size() > declared.getLength()) {
            errors.add(ErrorType.MismatchedParameterType, initTokenLine(init));
        }
        for (Exp element : elements) {
            Type actual = analyzeExp(element);
            if (actual != null && actual.isBasic() && !actual.equals(declared.getElementType())) {
                errors.add(ErrorType.MismatchedParameterType, initTokenLine(init));
            }
        }
    }

    private void analyzeConstElements(List<ConstExp> elements, Type declared) {
        if (elements == null) {
            return;
        }
        for (ConstExp element : elements) {
            analyzeConstExp(element);
        }
    }

    /** 字符串初始化只检查长度：字符数 + 结尾的 {@code '\0'} 不能超过数组容量。 */
    private void checkStringLength(Token string, Type declared) {
        if (string == null || declared.getLength() <= 0) {
            return;
        }
        if (string.value().length() + 1 > declared.getLength()) {
            errors.add(ErrorType.MismatchedParameterType, string.location());
        }
    }

    /** 初始化列表没有自己的记号，借用列表里第一个表达式的行号。 */
    private Navigation initTokenLine(InitVal init) {
        if (init.getElements() != null && !init.getElements().isEmpty()) {
            return lineOf(init.getElements().get(0));
        }
        return DeclarationFallback;
    }

    // ================================================================== 语句块

    /** 分析函数体最外层的块：<b>不</b>新开作用域（已由调用方开好）。 */
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

    // ================================================================== 语句

    private void visitStmt(Stmt stmt) {
        if (stmt == null) {
            return;
        }
        if (stmt instanceof BlockStmt blockStmt) {
            // 嵌套块才开新作用域
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
                errors.add(ErrorType.InvalidLoopControl, breakStmt.getToken().location());
            }
        } else if (stmt instanceof ContinueStmt continueStmt) {
            if (loopDepth == 0) {
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

    /** Stmt -> 'switch' '(' Exp ')' '{' { CaseStmt } '}' */
    private void analyzeSwitch(SwitchStmt stmt) {
        Type selector = analyzeExp(stmt.getExp());
        boolean selectorIsBasic = selector != null && selector.isBasic();

        Set<Integer> usedLabels = new HashSet<>();
        boolean defaultSeen = false;

        breakDepth++;           // switch 里 break 合法
        try {
            for (CaseStmt caseStmt : stmt.getCases()) {
                if (caseStmt.isDefault()) {
                    if (defaultSeen) {
                        errors.add(ErrorType.DuplicatedCaseLabel, caseStmt.getKeyword().location());
                    }
                    defaultSeen = true;
                } else {
                    Number number = caseStmt.getNumber();
                    Integer value = ConstFolder.fold(number);
                    if (value != null) {
                        if (!usedLabels.add(value)) {
                            errors.add(ErrorType.DuplicatedCaseLabel,
                                    number.getToken().location());
                        }
                    }
                    // 错误 e：case 的值类型必须与 switch 的表达式的类型一致
                    Type labelType = number.getToken().is(TokenType.CharConst) ? Type.Char : Type.Int;
                    if (selectorIsBasic && !labelType.equals(selector)) {
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

    /** Stmt -> LVal '=' Exp ';' */
    private void analyzeAssign(AssignStmt stmt) {
        LVal target = stmt.getLVal();
        Type left = analyzeLVal(target);
        // 错误 h：给常量赋值（含常量数组的元素）
        SymbolVariable symbol = target == null ? null : target.getSymbol();
        if (symbol != null && symbol.isConst()) {
            errors.add(ErrorType.AssignToConstant, target.getIdent().location());
        }
        Type right = analyzeExp(stmt.getExp());
        // 错误 e：赋值左右值类型不符（报在左值所在行）
        if (left != null && right != null && !compatible(left, right)) {
            errors.add(ErrorType.MismatchedParameterType, lineOf(target));
        }
    }

    /** Stmt -> 'return' [Exp] ';' */
    private void analyzeReturn(ReturnStmt stmt) {
        if (!stmt.hasExp()) {
            // void 函数可以直接 return;，非 void 函数会由错误 g 兜住
            return;
        }
        Type actual = analyzeExp(stmt.getExp());
        returnWithValueFound = true;
        // 错误 f：void 函数里出现带值的 return
        if (returnTypeIsVoid) {
            errors.add(ErrorType.MismatchedReturnType, stmt.getReturnToken().location());
            return;
        }
        // 错误 e：返回值的类型与函数返回类型不符
        Type declared = currentFunctionType;
        if (actual != null && declared != null
                && actual.isBasic() && declared.isBasic() && !actual.equals(declared)) {
            errors.add(ErrorType.MismatchedParameterType, stmt.getReturnToken().location());
        }
    }

    /** Stmt -> 'printf' '(' StringConst { ',' Exp } ')' ';' */
    private void analyzePrintf(PrintfStmt stmt) {
        int expected = countFormatSpecifiers(stmt.getFormat());
        List<Exp> args = stmt.getArgs() == null ? List.of() : stmt.getArgs();
        // 错误 l：格式符个数与实参个数不符
        if (expected != args.size()) {
            errors.add(ErrorType.MismatchedFormatArgument, stmt.getPrintfToken().location());
        }
        for (Exp arg : args) {
            analyzeExp(arg);
        }
    }

    /**
     * 数出格式串里的格式符个数。
     *
     * <p>{@code %%} 是转义，不消耗实参。{@code \n} 之类的转义在词法阶段已经解码成真实字符，
     * 不会干扰计数。
     */
    private int countFormatSpecifiers(Token format) {
        if (format == null) {
            return 0;
        }
        String text = format.value();
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '%') {
                continue;
            }
            i++;
            if (i >= text.length()) {
                break;
            }
            if (text.charAt(i) == '%') {
                continue;       // %% 不消耗实参
            }
            count++;
        }
        return count;
    }

    // ================================================================== 表达式

    private Type analyzeExp(Exp exp) {
        if (exp == null) {
            return null;
        }
        Type cached = types.get(exp);
        if (cached != null) {
            return cached;
        }
        Type type = analyzeAddExp(exp.getAddExp());
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
        Type type = analyzeAddExp(exp.getAddExp());
        put(exp, type);
        return type;
    }

    private Type analyzeCond(Cond cond) {
        if (cond == null) {
            return null;
        }
        Type type = analyzeLOr(cond.getLOrExp());
        put(cond, type);
        return type;
    }

    private Type analyzeLOr(ASTNode node) {
        return analyzeBinary(node, true);
    }

    private Type analyzeLAnd(ASTNode node) {
        return analyzeBinary(node, true);
    }

    private Type analyzeEq(ASTNode node) {
        return analyzeBinary(node, false);
    }

    private Type analyzeRel(ASTNode node) {
        return analyzeBinary(node, false);
    }

    private Type analyzeAddExp(ASTNode node) {
        return analyzeBinary(node, false);
    }

    private Type analyzeMulExp(ASTNode node) {
        return analyzeBinary(node, false);
    }

    /**
     * 二元表达式的类型。
     *
     * @param node 任意一层二元结点
     * @param resultIsInt 本层的运算结果是否恒为 {@code int}（逻辑与/或，文档明确要求）
     */
    private Type analyzeBinary(ASTNode node, boolean resultIsInt) {
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
        Type left = analyzeOperand(binary.getLeft());
        if (binary.isLeaf()) {
            put(node, left);
            return left;
        }
        Type right = analyzeOperand(binary.getRight());

        Type result;
        if (resultIsInt) {
            // 逻辑运算的结果恒为 int，但两个操作数仍必须是同一种基本类型
            checkSameType(left, right, binary.getOp());
            result = Type.Int;
        } else {
            result = checkSameType(left, right, binary.getOp());
        }
        put(node, result);
        return result;
    }

    /** 递归分析某个操作数（它可能是下一层的二元结点、UnaryExp 或 Cond）。 */
    private Type analyzeOperand(ASTNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof BinaryExp) {
            // 先看缓存，避免重复分析
            Type cached = types.get(node);
            if (cached != null) {
                return cached;
            }
            if (node instanceof frontend.ast.expression.LOrExp) {
                return analyzeLOr(node);
            }
            if (node instanceof frontend.ast.expression.LAndExp) {
                return analyzeLAnd(node);
            }
            if (node instanceof frontend.ast.expression.EqExp) {
                return analyzeEq(node);
            }
            if (node instanceof frontend.ast.expression.RelExp) {
                return analyzeRel(node);
            }
            if (node instanceof frontend.ast.expression.AddExp) {
                return analyzeAddExp(node);
            }
            if (node instanceof frontend.ast.expression.MulExp) {
                return analyzeMulExp(node);
            }
            return null;
        }
        if (node instanceof UnaryExp unary) {
            return analyzeUnaryExp(unary);
        }
        return null;
    }

    /**
     * 错误 e：两个操作数必须是同一种基本类型。
     *
     * @return 两侧一致时返回该类型，否则返回 {@code null}
     */
    private Type checkSameType(Type left, Type right, Token op) {
        if (left == null || right == null) {
            return null;
        }
        if (left.equals(right) && left.isBasic()) {
            return left;
        }
        // 数组、void 参与运算，或者 int 与 char 混用，都报 e
        errors.add(ErrorType.MismatchedParameterType,
                op == null ? DeclarationFallback : op.location());
        return null;
    }

    private Type analyzeUnaryExp(UnaryExp exp) {
        if (exp == null) {
            return null;
        }
        Type cached = types.get(exp);
        if (cached != null) {
            return cached;
        }
        Type type = switch (exp.getKind()) {
            case Primary -> analyzePrimaryExp(exp.getPrimary());
            case Cast -> analyzeCast(exp);
            case Unary -> analyzeUnaryOp(exp);
            case Call -> analyzeCall(exp);
        };
        put(exp, type);
        return type;
    }

    private Type analyzeCast(UnaryExp exp) {
        analyzeUnaryExp(exp.getOperand());
        return exp.getCastType().is(TokenType.Char) ? Type.Char : Type.Int;
    }

    private Type analyzeUnaryOp(UnaryExp exp) {
        Type operand = analyzeUnaryExp(exp.getOperand());
        if (operand != null && !operand.isBasic()) {
            // 错误 e：一元运算符只能作用于基本类型
            errors.add(ErrorType.MismatchedParameterType,
                    exp.getOp().getToken().location());
            return null;
        }
        return operand;
    }

    /** UnaryExp -> Ident '(' [FuncRParams] ')' */
    private Type analyzeCall(UnaryExp exp) {
        Token ident = exp.getIdent();
        List<Exp> args = exp.getParams() == null
                ? List.of() : exp.getParams().getParams();

        // 实参无论如何都要分析，否则实参里的未定义引用会漏报
        List<Type> argTypes = new ArrayList<>();
        for (Exp arg : args) {
            argTypes.add(analyzeExp(arg));
        }

        SymbolFunction function = symbols.getFunction(ident.value());
        if (function == null) {
            // 错误 c：也可能是把变量当函数调用，两种都报 c
            errors.add(ErrorType.UndefinedReference, ident.location());
            return null;
        }
        List<SymbolVariable> params = function.getParameters();
        if (args.size() != params.size()) {
            // 错误 d：个数不符就不再查类型
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
                // 错误 e：每个调用点最多报一次
                errors.add(ErrorType.MismatchedParameterType, ident.location());
                break;
            }
        }
        return (Type) function.getType();
    }

    private Type analyzePrimaryExp(PrimaryExp exp) {
        if (exp == null) {
            return null;
        }
        Type cached = types.get(exp);
        if (cached != null) {
            return cached;
        }
        Type type = switch (exp.getKind()) {
            case Paren -> analyzeExp(exp.getExp());
            case LVal -> analyzeLVal(exp.getLVal());
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

    /**
     * LVal 的类型。
     *
     * <p>无下标时是变量本身的类型（数组整体）；有下标时是元素类型，同时检查被下标的必须
     * 是数组、下标必须是 {@code int}。
     */
    private Type analyzeLVal(LVal lval) {
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
            // 错误 c
            errors.add(ErrorType.UndefinedReference, ident.location());
            return null;
        }
        lval.setSymbol(symbol);
        Type type = (Type) symbol.getType();

        if (!lval.isArrayElement()) {
            put(lval, type);
            return type;
        }
        // 有下标：必须作用在数组上
        if (!type.isArray()) {
            errors.add(ErrorType.MismatchedParameterType, ident.location());
            return null;
        }
        Type index = analyzeExp(lval.getIndex());
        if (index != null && !index.isInt()) {
            // 错误 e：下标必须是 int
            errors.add(ErrorType.MismatchedParameterType, ident.location());
        }
        Type element = type.getElementType();
        put(lval, element);
        return element;
    }

    // ================================================================== 工具

    private void put(ASTNode node, Type type) {
        if (node != null && type != null) {
            types.put(node, type);
        }
    }

    /**
     * 赋值、传参、返回的类型兼容性。
     *
     * <p>标量必须严格相等（2026 不允许 int 与 char 混用）；数组之间看元素类型，
     * 长度不参与比较（{@code int t[5]} 可以传给 {@code int a[]}）。
     */
    private boolean compatible(Type formal, Type actual) {
        if (formal == null || actual == null) {
            return true;
        }
        if (formal.isArray() || actual.isArray()) {
            return formal.isPassableTo(actual) || actual.isPassableTo(formal);
        }
        return formal.equals(actual);
    }

    /** @return 结点所在行，用于把错误定位到正确的行号。 */
    private Navigation lineOf(ASTNode node) {
        if (node instanceof LVal lval && lval.getIdent() != null) {
            return lval.getIdent().location();
        }
        if (node instanceof Number number && number.getToken() != null) {
            return number.getToken().location();
        }
        if (node instanceof BinaryExp binary && binary.getOp() != null) {
            return binary.getOp().location();
        }
        if (node instanceof UnaryExp unary) {
            if (unary.getIdent() != null) {
                return unary.getIdent().location();
            }
            if (unary.getCastType() != null) {
                return unary.getCastType().location();
            }
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

    /** 定位不到具体记号时使用的兜底位置（第 0 行不会与真实诊断冲突）。 */
    private static final Navigation DeclarationFallback =
            new Navigation(new utils.Pair<>(0, 0), new utils.Pair<>(0, 0));
}
