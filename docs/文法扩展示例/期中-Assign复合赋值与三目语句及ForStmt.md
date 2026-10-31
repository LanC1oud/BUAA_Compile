# 期中扩展：Assign、多种赋值右侧与 ForStmt

状态：仅提供修改方案，不修改工程源码。按用户截图及当前仓库接口整理，包含词法、AST、解析、语义和验证代码。返回[文法扩展示例集](README.md)。文件路径均相对于项目根目录。

## 1. 先准确理解图片中的规则

截图新增：

```text
Assign -> '+=' | '-=' | '*=' | '/=' | '%='
Stmt -> LVal Assign Cond '?' Exp ':' Exp ';'
```

截图同时修改：

```text
Stmt -> LVal Assign Exp ';'
Stmt -> LVal Assign 'getint' '(' ')' ';'
Stmt -> LVal Assign 'getchar' '(' ')' ';'
ForStmt -> LVal Assign Exp
```

需要特别区分：

1. **图片中的 Assign 没有列 `'='`。**本文默认严格按图，仅接受五种复合赋值；`x = 1;` 不再属于上面的赋值语句。声明 `int x = 1;` 中的 `=` 仍使用原有 TokenType.Assign，不受影响。若完整题目另要求保留普通赋值，按第 5.4 节扩充一次集合即可。
2. **三目形式属于 Stmt，不属于 Exp。**支持 `x += c > 0 ? a : b;`，不因此支持 `return c ? a : b;`、`f(c ? a : b)`、`int x = c ? a : b;` 或三目嵌套。
3. **getint / getchar 是专用右侧形式。**支持 `x += getint();`，不把 getint 任意开放为普通函数表达式；`x += getint() + 1;` 不在图示规则中。当前库函数 `get_int()` / `get_char()` 与这里的源码关键字不同。
4. **ForStmt 末尾没有分号。**分号由外层 for 头消费；且 ForStmt 只允许 Exp 右侧，不自动支持三目、getint / getchar。
5. 图片没有给出词法类别码、输出文件名和完整 for 语句规则。下文类别码是与本项目对接的建议；`<Assign>` 默认按新增非终结符输出，若完整题目明确要求省略则按要求调整。第 7 节的完整 for 是配套基线，不冒充截图新增规则。

### 1.1 本文采用的语义约定

五种运算分别是把目标旧值与右侧值相加、相减、相乘、相除、取模后存回。条件赋值先选择一个分支值，再用 Assign 指定的运算更新目标；例如 `x *= c ? a : b;` 为 `x *= (c ? a : b)`，不是先执行 `x *= c` 再选择分支。

沿用当前项目严格类型规则：目标和右侧必须是相同基本类型，char 运算按 `& 0xFF` 截断；getint 产生 Int，getchar 产生 Char。因此 `char c; c += getchar();` 合法，`int x; x += getchar();` 报 e。截图未规定类型提升，不能自行套用其它语言的规则。

`LVal` 必须可修改，常量报 h，整个数组或错误类型报 e，未定义名称报 c。三目的两条分支都要做静态检查，但未来执行只计算被选择的一条。本文不新增除零、未初始化变量或运行时溢出的专用诊断。

## 2. 修改文件与实施顺序

| 文件 | 修改内容 |
| --- | --- |
| `src/frontend/token/TokenType.java` | 五种复合赋值 token、问号、输入关键字；使用配套 for 时再加 For |
| `src/frontend/token/Keywords.java` | getint / getchar 映射；配套 for 映射 |
| `src/frontend/token/Symbols.java` | 增加 `?`，保留已有 `:` 和 `=` |
| `src/frontend/Lexer.java` | `readOp()` 四个新分支、公共扫描函数；单独修改 `readSlash()` 以识别 `/=` 并保留注释 |
| `src/frontend/ast/statement/AssignOp.java` | 新增 Assign 非终结符节点，输出 `<Assign>` |
| `src/frontend/ast/statement/AssignStmt.java` | 完整替换，保存表达式 / 条件 / 输入三种右侧 |
| `src/frontend/Parser.java` | Assign 集合及解析、赋值判别、条件右侧试探、完整语句右侧解析、ForStmt |
| `src/frontend/ast/statement/ForStmt.java` | 新增无尾分号的 for 头赋值，输出 `<ForStmt>` |
| `src/frontend/ast/statement/ForLoopStmt.java` | 当前没有 for，采用第 7 节配套基线时新增 |
| `src/frontend/Traverser.java` | 替换赋值分析；配套 for 的上下文遍历 |
| `ui/compilerui/pipeline/SyntaxHighlighter.java`、`SyntaxReference.java` | 可选：新运算符、关键字及文法说明 |
| `testgen` 中的文法、token 配置、oracle、边界目录 | 独立适配新的结构与语义，不直接沿用旧赋值契约 |

不必修改 Exp / Cond / BinaryExp、常量求值器或错误枚举，也不新增通用 ConditionalExp。已有 `+=` 单独扩展方案没有 `<Assign>` 层，不能直接混用其旧参考输出；本题以本文统一的 AssignStmt 方案为准。

## 3. 词法修改

### 3.1 token、关键字和单字符表

在 `TokenType` 中新增：

```java
PlusAssign("PLUSEQ"),
MinusAssign("MINUEQ"),
MultAssign("MULTEQ"),
DivAssign("DIVEQ"),
ModAssign("MODEQ"),
Question("QUESTION"),
GetInt("GETINTTK"),
GetChar("GETCHARTK"),
```

在 `Keywords.TABLE` 的初始化项中增加：

```java
entry("getint", TokenType.GetInt),
entry("getchar", TokenType.GetChar),
```

在 `Symbols.TABLE` 增加：

```java
entry('?', TokenType.Question),
```

已有 `':' -> Colon` 保留。双字符运算符不能放进 Character 键的 Symbols.TABLE。以上类别码不是截图给出的标准码，拿到完整输出要求后应同步 token 和参考答案。

### 3.2 `Lexer`：增加公共辅助方法

```java
private void readAssignOrSingle(char first, TokenType single, TokenType compound,
                                Pair<Integer, Integer> start) throws IOException {
    int next = reader.read();
    if (next == '=') {
        addToken(compound, String.valueOf(first) + "=", start);
    } else {
        reader.unread(next);
        addToken(single, String.valueOf(first), start);
    }
}
```

在 `readOp()` 的 `switch (c)` 内、default 前增加四个分支：

```java
case '+' -> readAssignOrSingle('+', TokenType.Plus, TokenType.PlusAssign, start);
case '-' -> readAssignOrSingle('-', TokenType.Minus, TokenType.MinusAssign, start);
case '*' -> readAssignOrSingle('*', TokenType.Mult, TokenType.MultAssign, start);
case '%' -> readAssignOrSingle('%', TokenType.Mod, TokenType.ModAssign, start);
```

已有 `= / ==`、`! / !=`、关系与逻辑运算符分支不变。若已实现 `++` 或 `**`，合并其同字符分支，不能加入重复的 case。只相邻的字符才能组成 token，`+ =`、`+/*comment*/=` 都不是 `+=`。

### 3.3 必须单独替换 `readSlash()`

当前 Lexer 主循环把 `/` 专门交给 `readSlash()`，所以仅在 `readOp()` 增加 `/` 分支**不会识别 `/=`**。完整替换如下：

```java
private void readSlash() throws IOException {
    reader.read();
    Pair<Integer, Integer> start = reader.location();
    int next = reader.read();
    if (next == '/') {
        int c;
        do {
            c = reader.read();
        } while (c != -1 && c != '\n');
    } else if (next == '*') {
        int previous = -1;
        int c;
        while ((c = reader.read()) != -1) {
            if (previous == '*' && c == '/') {
                return;
            }
            previous = c;
        }
    } else if (next == '=') {
        addToken(TokenType.DivAssign, "/=", start);
    } else {
        reader.unread(next);
        addToken(TokenType.Div, "/", start);
    }
}
```

回归 `/`、`/=`、`//...`、`/*...*/` 以及 EOF，保持当前不支持嵌套块注释的规则。这里没有额外定义未闭合注释的新诊断。

## 4. AST：保存 Assign 和三种右侧

### 4.1 新增完整的 `AssignOp.java`

Java 类叫 AssignOp，文法标签叫 `<Assign>`，避免与 TokenType.Assign 的普通等号混淆。

```java
package frontend.ast.statement;

import frontend.ast.ASTNode;
import frontend.token.Token;

public class AssignOp extends ASTNode {
    private final Token token;

    public AssignOp(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }

    @Override
    protected String getName() {
        return "<Assign>";
    }
}
```

### 4.2 完整替换 `AssignStmt.java`

三个工厂分别对应普通 Exp、Cond 三目、输入关键字。保留 `getLVal()` / `getExp()` 接口，避免现有调用点编译失败；条件形态的 exp 保存真分支，elseExp 保存假分支。

```java
package frontend.ast.statement;

import frontend.ast.expression.Cond;
import frontend.ast.expression.Exp;
import frontend.ast.expression.LVal;
import frontend.token.Token;

public class AssignStmt extends Stmt {
    public enum Kind { Expression, Conditional, Input }

    private final Kind kind;
    private final LVal lval;
    private final AssignOp assign;
    private final Exp exp;
    private final Cond cond;
    private final Exp elseExp;
    private final Token inputToken;

    private AssignStmt(Kind kind, LVal lval, AssignOp assign, Exp exp,
                       Cond cond, Exp elseExp, Token inputToken) {
        this.kind = kind;
        this.lval = lval;
        this.assign = assign;
        this.exp = exp;
        this.cond = cond;
        this.elseExp = elseExp;
        this.inputToken = inputToken;
    }

    public static AssignStmt expression(LVal lval, AssignOp assign, Exp exp) {
        return new AssignStmt(Kind.Expression, lval, assign, exp, null, null, null);
    }

    public static AssignStmt conditional(LVal lval, AssignOp assign, Cond cond,
                                         Exp thenExp, Exp elseExp) {
        return new AssignStmt(Kind.Conditional, lval, assign,
                thenExp, cond, elseExp, null);
    }

    public static AssignStmt input(LVal lval, AssignOp assign, Token inputToken) {
        return new AssignStmt(Kind.Input, lval, assign, null, null, null, inputToken);
    }

    public Kind getKind() { return kind; }
    public LVal getLVal() { return lval; }
    public AssignOp getAssign() { return assign; }
    public Exp getExp() { return exp; }
    public Cond getCond() { return cond; }
    public Exp getElseExp() { return elseExp; }
    public Token getInputToken() { return inputToken; }
}
```

保持继承的 `<Stmt>` 标签。旧的 `new AssignStmt(lval, exp)` 构造调用要按第 5 节一起替换，不保留一个会丢失运算符的旧构造器。输入形态不合成 UnaryExp.call，三目形态不合成 IfStmt，否则语法输出会多出未要求的标签。

## 5. Parser：完整的共享赋值处理

### 5.1 新增集合和 `parseAssign()`

在 Parser 类字段区增加：

```java
private static final TokenType[] ASSIGN_OPERATORS = {
        TokenType.PlusAssign, TokenType.MinusAssign, TokenType.MultAssign,
        TokenType.DivAssign, TokenType.ModAssign
};
```

在类中增加：

```java
private AssignOp parseAssign() {
    return watch(new AssignOp(consumeAny(ASSIGN_OPERATORS)));
}
```

消耗复合赋值 token 后立即输出 `<Assign>`；不要手动在 Lexer 输出该标签。

### 5.2 完整替换 `isAssignStmt()`

```java
private boolean isAssignStmt() {
    if (!among(TokenType.Ident)) {
        return false;
    }
    trace.save();
    boolean assign = false;
    try {
        parseLVal();
        assign = among(ASSIGN_OPERATORS);
    } catch (RuntimeException e) {
        assign = false;
    } finally {
        trace.restore();
    }
    return assign;
}
```

先读取完整 LVal，才能识别 `arr[f()] *= value;`。恢复 token、错误和 watcher，避免试探时重复输出左值。

### 5.3 新增三目右侧试探与完整语句方法

普通 Exp 和 Cond 共享起始部分，但三目规则要求输出 `<Cond>`，普通赋值只要求 `<Exp>`。因此先试探 Cond 是否后接问号，再恢复并按真正产生式解析：

```java
private boolean isConditionalAssignRhs() {
    trace.save();
    try {
        parseCond();
        return among(TokenType.Question);
    } catch (RuntimeException e) {
        return false;
    } finally {
        trace.restore();
    }
}
```

```java
private Stmt parseAssignedStmt() {
    LVal target = parseLVal();
    AssignOp assign = parseAssign();

    if (among(TokenType.GetInt, TokenType.GetChar)) {
        Token input = consumeAny(TokenType.GetInt, TokenType.GetChar);
        consume(TokenType.LParen);
        consume(TokenType.RParen);
        consume(TokenType.Semicolon);
        return watch(AssignStmt.input(target, assign, input));
    }

    if (isConditionalAssignRhs()) {
        Cond cond = parseCond();
        consume(TokenType.Question);
        Exp thenExp = parseExp();
        consume(TokenType.Colon);
        Exp elseExp = parseExp();
        consume(TokenType.Semicolon);
        return watch(AssignStmt.conditional(target, assign, cond, thenExp, elseExp));
    }

    Exp exp = parseExp();
    consume(TokenType.Semicolon);
    return watch(AssignStmt.expression(target, assign, exp));
}
```

在 `parseStmt()` 中把原来的整个 `if (isAssignStmt()) { ... }` 分支替换为：

```java
if (isAssignStmt()) {
    return parseAssignedStmt();
}
```

其余块、if、while、switch、返回及普通表达式语句分支保留。试探会额外解析一次右侧前缀，但不做语义分析或执行副作用，恢复后只输出正式轨迹。不能先输出完整 Cond 再在没有问号时直接当 Exp 返回，否则普通复合赋值会多出 RelExp / EqExp / LAndExp / LOrExp / Cond 标签。

也不能只扫描“本行是否有问号”：括号、数组下标、函数调用和换行都不按源代码行划分语句。截图的 Exp 不是递归三目表达式，`thenExp` / `elseExp` 中遇到另一个问号不能继续当通用三目解析。

### 5.4 如果完整题目要求保留普通 `=`

仅向 `ASSIGN_OPERATORS` 增加 `TokenType.Assign`，并将文法写成：

```text
Assign -> '=' | '+=' | '-=' | '*=' | '/=' | '%='
```

这样普通赋值也会输出 `<Assign>`，与旧版直接消费 `=` 的轨迹不同。第 8 节类型检查可共用，但未来执行 `=` 时只求右侧并存回，不读取目标旧值；不能将它当成某种复合算术。此变体必须明确得到完整题面的依据，不作为截图默认规则。

## 6. ForStmt：独立的头部赋值

### 6.1 新增完整的 `ForStmt.java`

复用 AssignStmt 的 Expression 形态保存目标、操作符和 Exp，但不 watch 这个内部赋值节点；对外输出的仍是 `<ForStmt>`。

```java
package frontend.ast.statement;

import frontend.ast.ASTNode;
import frontend.ast.expression.Exp;
import frontend.ast.expression.LVal;

public class ForStmt extends ASTNode {
    private final AssignStmt assignment;

    public ForStmt(LVal target, AssignOp assign, Exp exp) {
        this.assignment = AssignStmt.expression(target, assign, exp);
    }

    public AssignStmt getAssignment() {
        return assignment;
    }

    @Override
    protected String getName() {
        return "<ForStmt>";
    }
}
```

### 6.2 Parser 新增完整方法

```java
private ForStmt parseForStmt() {
    LVal target = parseLVal();
    AssignOp assign = parseAssign();
    Exp exp = parseExp();
    return watch(new ForStmt(target, assign, exp));
}
```

末尾没有 `consume(Semicolon)`。例如 `i += 1` 的局部轨迹为 LVal → 复合 token / Assign → Exp → ForStmt，不出现 `<Stmt>`。不要直接调用 `parseAssignedStmt()`，否则会吃掉外层分号，且错误放开输入和三目右侧。

## 7. 当前缺少 for 的配套基线

当前工程没有 For token、完整 for 语句或其 AST，单独加入 `parseForStmt()` 不会让程序能解析 for 循环。以下明确采用常见的配套基线：

```text
Stmt -> 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt
```

它不是截图中重新给出的完整题面；若你的真实基础文法不同，保留其原有 for 头结构，仅将里面的赋值操作符换成 `parseAssign()`。

### 7.1 新 token、关键字和完整 AST

TokenType 新增 `For("FORTK")`，Keywords 初始化增加 `entry("for", TokenType.For)`。新增 `src/frontend/ast/statement/ForLoopStmt.java`：

```java
package frontend.ast.statement;

import frontend.ast.expression.Cond;

public class ForLoopStmt extends Stmt {
    private final ForStmt init;
    private final Cond cond;
    private final ForStmt update;
    private final Stmt body;

    public ForLoopStmt(ForStmt init, Cond cond, ForStmt update, Stmt body) {
        this.init = init;
        this.cond = cond;
        this.update = update;
        this.body = body;
    }

    public ForStmt getInit() { return init; }
    public Cond getCond() { return cond; }
    public ForStmt getUpdate() { return update; }
    public Stmt getBody() { return body; }
}
```

### 7.2 完整的循环解析方法与入口

```java
private Stmt parseForLoopStmt() {
    consume(TokenType.For);
    consume(TokenType.LParen);
    ForStmt init = among(TokenType.Semicolon) ? null : parseForStmt();
    consume(TokenType.Semicolon);
    Cond cond = among(TokenType.Semicolon) ? null : parseCond();
    consume(TokenType.Semicolon);
    ForStmt update = among(TokenType.RParen) ? null : parseForStmt();
    consume(TokenType.RParen);
    Stmt body = parseStmt();
    return watch(new ForLoopStmt(init, cond, update, body));
}
```

在 `parseStmt()` 中与 While 分支并列、兜底表达式分支之前增加：

```java
if (among(TokenType.For)) {
    return parseForLoopStmt();
}
```

这份配套基线允许三个头部部分分别为空；不允许初始化段直接声明变量，也不允许逗号分隔多条更新。严格按截图的 Assign 集合时，应写 `int i = 0; for (i += 0; i < 3; i += 1) ...`，而不是在头部使用未列出的普通 `=`。

## 8. 当前语义阶段的完整改法

如果只考语法输出，可切换到 Syntax 阶段验证第 3～7 节；若继续运行当前默认 Semantic 阶段，必须同步本节，否则旧 `analyzeAssign()` 只看 `getExp()`，会漏掉条件、假分支与输入类型。

### 8.1 完整替换 `Traverser.analyzeAssign()`

```java
private void analyzeAssign(AssignStmt stmt) {
    LVal target = stmt.getLVal();
    Type left = analyzeLVal(target, false);
    SymbolVariable symbol = target == null ? null : target.getSymbol();
    if (symbol != null && symbol.isConst()) {
        errors.add(ErrorType.AssignToConstant, target.getIdent().location());
    }

    Type right = switch (stmt.getKind()) {
        case Expression -> analyzeExp(stmt.getExp());
        case Input -> stmt.getInputToken().is(TokenType.GetInt) ? Type.Int : Type.Char;
        case Conditional -> {
            analyzeCond(stmt.getCond());
            Type thenType = analyzeExp(stmt.getExp());
            Type elseType = analyzeExp(stmt.getElseExp());
            yield checkSameType(thenType, elseType, stmt.getAssign().getToken());
        }
    };

    if (left != null && right != null && (!left.isBasic() || !left.equals(right))) {
        errors.add(ErrorType.MismatchedParameterType, stmt.getAssign().getToken().location());
    }
}
```

`analyzeCond()` 会设置已有条件上下文，因此 `!` 可出现在三目条件中；分支 Exp 的上下文恢复为普通表达式。即使条件是字面量 1，假分支里的未定义名称、函数参数或类型错误也要检查。

此处复用 `checkSameType()` 同时拒绝分支类型不一致及非基本类型；不根据条件常量来跳过某条分支的静态分析。输入 token 直接决定类型，无需向 SymbolTable 注册名为 getint / getchar 的普通函数。

### 8.2 配套 for 的分析方法

使用第 7 节时，在 Traverser 顶部增加：

```java
import frontend.ast.statement.ForLoopStmt;
```

在 `visitStmt()` 中与 WhileStmt 并列加入：

```java
} else if (stmt instanceof ForLoopStmt forStmt) {
    analyzeForLoop(forStmt);
```

新增完整方法：

```java
private void analyzeForLoop(ForLoopStmt stmt) {
    if (stmt.getInit() != null) {
        analyzeAssign(stmt.getInit().getAssignment());
    }
    analyzeCond(stmt.getCond());
    loopDepth++;
    breakDepth++;
    try {
        visitStmt(stmt.getBody());
        if (stmt.getUpdate() != null) {
            analyzeAssign(stmt.getUpdate().getAssignment());
        }
    } finally {
        breakDepth--;
        loopDepth--;
    }
}
```

没有声明型初始化段，for 头本身不新增作用域；块体仍由既有 BlockStmt 管理作用域。初始化、条件、更新和体中的名字都要检查，不因为常量假条件跳过。

普通 Exp / 三目 / 输入 / ForStmt 都复用相同的目标检查，不能漏掉 for 更新里的常量修改。这里不执行输入或复合赋值，也不更新 FixedValue / FixedArray。

## 9. 未来执行时必须保留的信息

对严格的五种复合赋值，本文约定如下执行顺序：

```text
address = 计算目标地址一次（包含下标求值）
old = load(address)
rhs = 根据 RHS Kind 计算一个右侧值
    Expression：计算 Exp
    Conditional：先计算 Cond，然后只计算真 / 假分支中的一条
    Input：调用一次对应输入操作
result = 用 Assign 对应的运算（+、-、*、/、%）合并 old 和 rhs
store(address, 按目标类型处理后的 result)
```

例如 `a[index()] += c ? f() : g();`，index 只调用一次，f / g 只调用被选中的一个。不能改写成两份 LVal 的 `a[index()] = a[index()] + ...`，也不能先计算两条分支再选择结果。

复合输入是先取得一个输入值再做指定运算；`x *= getint();` 不是把输入直接赋给 x。涉及 `/=` 和 `%=` 时，运行时零除行为需沿用真实语言规范，本文不凭空增加错误码。

配套 for 的执行顺序是 init → Cond → body → update → Cond；缺 Cond 表示持续循环。continue 应跳到 update，break 跳到出口，不能照搬 while 而让 continue 跳过更新。这是后端要求，当前前端深度计数只能判断跳转是否允许。

## 10. 输出与完整样例

图片未给输出文件名。词法 / 语法 / 语义分别沿用当前 lexer.txt / parser.txt / symbol.txt；若实际考核要求 output.txt，在 HomeworkConfig 中切到 Hw.Syntax 并将 Syntax 目标改为 output.txt。语法模式要在创建 ParserWatcher 前启用轨迹记录，当前 Controller 已处理。

### 10.1 覆盖全部截图形式的完整程序

```c
int main() {
    int x = 10;
    int y = 2;
    int i = 0;
    char c = 'a';
    x += y;
    x -= 1;
    x *= 2;
    x /= 2;
    x %= 7;
    x += y > 0 ? 3 : 4;
    x *= getint();
    c += getchar();
    for (i += 0; i < 3; i += 1) {
        x += i;
    }
    return 0;
}
```

该程序包含第 7 节的配套 for。实施完整方案后，应无语义错误；symbol.txt 为：

```text
2 x Int
2 y Int
2 i Int
2 c Char
```

程序含输入调用，本题没有提供输入数据，因此不编造运行输出。当前前端也不会真正读取输入。

`x += y;` 的局部语法输出应为：

```text
IDENFR x
<LVal>
PLUSEQ +=
<Assign>
IDENFR y
<LVal>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
<Exp>
SEMICN ;
<Stmt>
```

输入形式的关键轨迹不经过 Exp 或函数调用节点：

```text
IDENFR x
<LVal>
MULTEQ *=
<Assign>
GETINTTK getint
LPARENT (
RPARENT )
SEMICN ;
<Stmt>
```

三目形式在问号前输出 `<Cond>`，问号后分别输出两个 `<Exp>`，最后输出一次 `<Stmt>`；不输出 `<ConditionalExp>`。ForStmt 则在右侧 Exp 后输出 `<ForStmt>`，由外层 for 消费分号 / 右括号。

### 10.2 必测边界

| 场景 | 验收要求 |
| --- | --- |
| 五种复合操作符 | 每种单独及紧贴标识符测试，不能漏掉 `/=` 的专用扫描路径 |
| `/` 与注释 | `/=`、普通除法、`//`、`/*...*/`、`/**/` 及 EOF 互不破坏 |
| 关系 / 逻辑条件 | `x += a < b && c != 0 ? 1 : 2;`，问号前必须是完整 Cond |
| 普通 Exp 右侧 | 不出现试探产生的 Cond / 关系 / 逻辑标签 |
| 复杂左值 | `a[f(1)] %= y + 1;`，回溯不重复输出 LVal / 函数调用 |
| 常量目标 | `k += 1;`、`constArray[0] *= 2;` 以及 for 更新，独立报 h |
| 类型检查 | int / char 混用、数组整体目标、非 int 下标、三目分支类型不同，独立报 e |
| 两分支静态检查 | `x += 1 ? 2 : unknown;` 即使假分支不执行也报 c |
| 条件上下文 | `x += !y ? 1 : 2;` 合法；分支中的 ! 仍受现有条件位置限制 |
| 输入类型 | Int 目标配 getint、Char 目标配 getchar；反向类型组合报 e |
| ForStmt 分隔符 | 初始化 / 更新不消费尾分号，不输出 `<Stmt>` |
| 配套 for 空头 | 三部分分别省略及 `for (;;) ;`，保持各分隔符和外层 Stmt |
| for 控制上下文 | body 内 continue / break 合法；循环后的独立跳转仍报 m |
| 明确不支持 | 三目作为 return / 初值 / 实参、三目嵌套、ForStmt 的三目或输入、输入后追加算术、链式复合赋值 |
| 严格 Assign 集合 | `int x = 1;` 声明仍合法，`x = 1;` 语句不在截图集合中 |
| 边界格式 | 运算符不能跨空白 / 注释合并，缺分号 / 括号沿用既有恢复，截断文件不能死循环 |

## 11. 测试生成器与实施核对

文法应增加 Assign 非终结符，并替换旧的赋值 Stmt 候选式，不能仅合并后仍保留旧 `LVal '=' Exp ';'`，却声称生成器测试了严格截图规则。保留其它旧 Stmt 产生式；只有采用第 7 节配套基线时才加入完整 for。

同步 token 配置可写为：

```json
{"+=":"PLUSEQ","-=":"MINUEQ","*=":"MULTEQ","/=":"DIVEQ","%=":"MODEQ","?":"QUESTION","getint":"GETINTTK","getchar":"GETCHARTK","for":"FORTK"}
```

纯语法参考解析器可按有效文法生成 / 读取片段并输出新的 `<Assign>`；完整程序模式仍需扩展 `SemanticModel.statement()`、for 上下文和错误目录。不能直接把所有以 LVal 开头的语句当旧赋值，也不能把专用输入当普通函数。三目 oracle 必须静态检查两分支，未来运行答案则只执行一条分支。

推荐实施顺序：词法及注释回归 → AssignOp / AssignStmt → 共享 Assign 集合 → 右侧试探与解析 → ForStmt / 配套 for → 统一语义检查 → 输出轨迹与非法形状回归。若保留旧的 `+=` 专用 AST，明确迁移到本文方案，避免两个分支分别处理同一个 token。

## 12. 本文参考代码的验证记录

在 build 下创建临时源码副本，从本文提取代码应用到副本，使用本机 JDK 24 编译全部核心源码；结束后移除临时目录，原 src 文件哈希保持不变，包括用户已有改动。

- 8 组词法对照覆盖五种复合操作符、`/=` 与两种注释、单字符运算符、空白分隔、字符串、关键字边界和 EOF。
- 23 组完整程序的 token / 语法交错轨迹对照，与按本文有效文法建立的独立参考解析器逐行一致；包括全部右侧形式、复杂左值、三目逻辑条件、for 头的 8 种可选组合与循环嵌套。
- 单独断言普通 Exp 赋值中不泄漏试探产生的 `<Cond>`，输入赋值不额外输出函数调用 / Exp 标签。
- 验证完整样例符号表、常量修改 h、输入 / 分支类型 e、未选中分支的未定义引用 c、for 更新的常量修改及退出循环后的跳转 m。
- 9 种超出文法的形状未被当作合法程序接受，包括普通 `=` 语句、通用 / 嵌套三目、输入追加算术、链式复合赋值及非法 for 头。

截图没有提供官方类别码、完整 for 基线或标准输出，以上验证的是本文明确写出的对接契约；不据此宣称通过未知的官方评测。当前工作区源码与 testgen 均未实施这些扩展，后端求值次数和分支执行仍是待实现的运行要求。
