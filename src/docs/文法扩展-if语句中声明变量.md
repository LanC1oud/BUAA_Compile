# 文法扩展方案：在 `if` 条件中声明变量

本文依据题图和当前工作区代码编写，给出涉及文件、实施顺序以及全部需要新增或替换的 Java 代码。**本次只生成本文档，不修改项目源码。** 代码块是后续实施方案，不代表仓库已经支持此功能。

## 1. 明确文法和语义

题图新增：

```text
Stmt → ... | 'if' '(' BType Ident '=' VarInit ')' Stmt ['else' Stmt]
```

保留原有的普通条件形式，合并后为：

```text
Stmt → ...
     | 'if' '(' Cond ')' Stmt ['else' Stmt]
     | 'if' '(' BType Ident '=' VarInit ')' Stmt ['else' Stmt]
BType → 'int' | 'char'
VarInit → Exp
```

题图没有展开 `VarInit`，仓库也没有名为 `VarInit` 的节点。本文根据项目根目录《2026编译技术实验文法定义及相关说明》中“对于单个的常量或变量，初始值也必须是单个的表达式”这一约束，将其落实为 `Exp`，内部用已有 `InitVal.exp(...)` 保存。这是本文对未展开部分的对接约定；如果后续题目另行定义 `VarInit`，需要按其定义调整解析入口。

具体边界：

- 只声明一个普通标量变量，类型为 `int` 或 `char`，必须有 `=` 和初值。
- 不接收 `const`、`static`、数组声明、逗号分隔的多个声明；不增加 `if (int a = 1; a > 0)` 这种形式。
- 初值按已有 `Exp` 解析，允许变量引用、算术表达式、函数调用和显式转换；不能直接把 `Cond` 当作初值，也不接收列表或字符串初值。
- 先初始化变量，再读取**这个变量**判定条件，值非零执行 then，值为零执行 else（若有）。不能只执行声明，也不能把初值当作整个条件节点。
- 新变量不是常量，两个分支内都可以读取或赋值。
- 初始化只求值一次；离开整个 if 后，新变量失效。
- 初值类型沿用现有严格匹配：`int` 与 `char` 不自动混用，需要时显式转换。

依据题图的等价关系，有 else 的形式也应理解为：

```c
if (int a = b) {
    printf("%d", a);
} else {
    a = 2;
}
```

等价于：

```c
{
    int a = b;
    if (a) {
        printf("%d", a);
    } else {
        a = 2;
    }
}
```

因此作用域包住“声明、条件、then、else”四部分；then 和 else 自己的 `{ ... }` 仍各自创建子作用域。普通 `if (Cond)` 不额外创建这个作用域。

## 2. 涉及文件总表

以下路径相对项目根目录；第 3～5 节的代码组成一套完整实现，按顺序一起应用。

| 文件 | 操作 | 原因 |
| --- | --- | --- |
| `src/frontend/ast/statement/IfStmt.java` | 用第 3 节代码替换整个文件 | 保存可选声明，同时保留普通 if 的构造接口 |
| `src/frontend/Parser.java` | 替换 `parseIfStmt()`，增加两个辅助方法 | 区分声明和普通条件；构造声明及读取变量的条件 AST |
| `src/frontend/Traverser.java` | 替换 `visitStmt()` 中的 if 分支，增加 `analyzeIf()` | 为声明形式创建作用域，复用现有声明、条件和分支分析 |

以下文件已检查，无须为本方案增加代码：

| 文件或模块 | 复用方式 |
| --- | --- |
| `src/frontend/Lexer.java`、`src/frontend/token/*` | 所有关键字、标识符、括号、赋值号均已有 token |
| `src/frontend/ast/declaration/{VarDecl,VarDef,InitVal}.java` | 直接构造一个非 static、非数组、有表达式初值的声明 |
| `src/frontend/ast/expression/*` | 使用已有构造器构建条件 `a` 的表达式链 |
| `src/frontend/symbol/SymbolTable.java` | 已有 `newScope()`、`exitScope()`、变量注册和逐层查找 |
| `src/error/*` | 已有 `b/c/d/e/j` 等诊断可复用，不引入新错误码 |
| `src/frontend/ParserWatcher.java` | 已有 `add()`，可记录新非终结符 `<VarInit>` |
| `ui/compilerui/gui/AstTreePanel.java` | 根据 `<...>` 文本分组，无须增加节点枚举 |
| `ui/compilerui/pipeline/{SyntaxReference,SyntaxHighlighter}.java` | 本扩展没有新关键字或运算符；速查是 token 表，无须增加文法分支 |
| `src/utils/Controller.java` | 前端调用顺序不变；当前没有 IR 或目标代码生成阶段实现 |

不要直接调用 `parseVarDecl()`：它会消费末尾分号，并允许数组、多个变量等不符合新产生式的形式。也不要直接调用 `parseVarDef()`，因为它允许缺少初值或带数组长度。

## 3. AST：完整替换 `IfStmt.java`

在同一个 `IfStmt` 中增加可选 `conditionDecl`。无论哪种 if，`getCond()` 都返回可分析的 `Cond`，避免到处新增另一种 if 节点分派。

下面是该文件的完整内容，包括所需 import、构造器和 getter，没有省略方法体：

```java
package frontend.ast.statement;

import frontend.ast.declaration.VarDecl;
import frontend.ast.expression.Cond;

// Stmt -> 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
//       | 'if' '(' BType Ident '=' VarInit ')' Stmt [ 'else' Stmt ]
public class IfStmt extends Stmt {
    // null 表示普通 if；非 null 表示在条件中声明了一个变量。
    private final VarDecl conditionDecl;
    private final Cond cond;
    private final Stmt thenStmt;
    private final Stmt elseStmt;

    // 保留旧接口，普通 if 的已有调用仍然有效。
    public IfStmt(Cond cond, Stmt thenStmt, Stmt elseStmt) {
        this(null, cond, thenStmt, elseStmt);
    }

    public IfStmt(VarDecl conditionDecl, Cond cond,
                  Stmt thenStmt, Stmt elseStmt) {
        this.conditionDecl = conditionDecl;
        this.cond = cond;
        this.thenStmt = thenStmt;
        this.elseStmt = elseStmt;
    }

    public boolean hasConditionDecl() {
        return conditionDecl != null;
    }

    public VarDecl getConditionDecl() {
        return conditionDecl;
    }

    public Cond getCond() {
        return cond;
    }

    public Stmt getThenStmt() {
        return thenStmt;
    }

    public Stmt getElseStmt() {
        return elseStmt;
    }

    public boolean hasElse() {
        return elseStmt != null;
    }
}
```

`getName()` 继续继承 `Stmt`，输出仍为 `<Stmt>`。

## 4. 语法分析：修改 `Parser.java`

当前文件已通过通配符导入声明、表达式、语句节点，也已导入 `Token`、`TokenType` 和 `java.util.List`，**本节不需要增加 import**。

### 4.1 完整替换 `parseIfStmt()`

找到原来的 `private Stmt parseIfStmt()`，用下面整个方法替换。它仍由现有 `parseStmt()` 的 `TokenType.If` 分支调用，因此不用改 `parseStmt()`。

```java
// Stmt -> 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
//       | 'if' '(' BType Ident '=' VarInit ')' Stmt [ 'else' Stmt ]
private Stmt parseIfStmt() {
    consume(TokenType.If);
    consume(TokenType.LParen);

    VarDecl conditionDecl = null;
    Cond cond;
    if (among(TokenType.Int, TokenType.Char)) {
        Token btype = parseBType();
        Token ident = consume(TokenType.Ident);
        consume(TokenType.Assign);
        InitVal initVal = parseVarInit();

        // 这些是供语义阶段复用的内部包装，不调用 watch()。
        VarDef def = new VarDef(ident, null, initVal);
        conditionDecl = new VarDecl(null, btype, List.of(def));
        cond = conditionOfVariable(ident);
    } else {
        cond = parseCond();
    }

    consume(TokenType.RParen);
    Stmt thenStmt = parseStmt();
    Stmt elseStmt = null;
    if (among(TokenType.Else)) {
        consume(TokenType.Else);
        elseStmt = parseStmt();
    }
    return watch(new IfStmt(conditionDecl, cond, thenStmt, elseStmt));
}
```

`(` 后直接出现 `int` 或 `char`，才走声明形式。`if ((int)c)` 的下一个 token 是 `(`，仍走原有 `Cond` 分支，不会把强制转换误当成声明。新的分支不消费分号。

### 4.2 新增 `parseVarInit()`

将下面方法放在 `parseIfStmt()` 后面、类体内部。它只解析标量初值，内部复用 `InitVal`，语法输出采用题图中的非终结符名 `<VarInit>`。

```java
// VarInit -> Exp；内部复用 InitVal 的表达式形态。
private InitVal parseVarInit() {
    InitVal initVal = InitVal.exp(parseExp());
    __watcher.add("<VarInit>");
    return initVal;
}
```

这里不调用 `watch(initVal)`，否则会输出 `<InitVal>`，与本文选择的新增产生式名不同。题图未规定具体评测输出格式；本文显式采用 `<VarInit>`，若实际评测另有约定，应同步该输出名称。

### 4.3 新增 `conditionOfVariable()`

紧接上一个方法加入以下完整方法。它把条件构造成 `a`，不是把初始化表达式再放入条件中。

```java
// 构建 Cond -> ... -> LVal，读取刚声明的变量。
// 这是内部合成节点，不对应额外源码，不调用 consume() 或 watch()。
private Cond conditionOfVariable(Token ident) {
    LVal lval = new LVal(ident, null);
    PrimaryExp primary = PrimaryExp.lval(lval);
    UnaryExp unary = UnaryExp.primary(primary);
    MulExp mul = new MulExp(unary);
    AddExp add = new AddExp(mul);
    RelExp rel = new RelExp(add);
    EqExp eq = new EqExp(rel);
    LAndExp and = new LAndExp(eq);
    LOrExp or = new LOrExp(and);
    return new Cond(or);
}
```

内部关系相当于：

```text
IfStmt
├─ conditionDecl = VarDecl(null, BType, [VarDef(Ident, null, InitVal.exp(Exp))])
├─ cond          = 对 Ident 的普通条件读取
├─ thenStmt
└─ elseStmt      = 可为 null
```

声明包装中的两个 `null` 分别表示“不是 static”和“没有数组长度”。初值 AST 与条件中的 `LVal` 是两个不同节点，函数调用等初值副作用不会因为这个构造方式被复制到条件中。

### 4.4 语法输出、else 绑定和错误恢复

以 `if (int a = 1) ;` 为例，输出包含实际的 `if ( int a = 1 ) ;` token、初值的表达式节点、`<VarInit>`、空语句的 `<Stmt>`，最后是 if 的 `<Stmt>`。

不要为内部合成的 `VarDef`、`VarDecl` 或条件链调用 `watch()`：源码中没有对应的完整声明语句、分号或条件表达式 `a`，输出这些包装会凭空增加非终结符。也不要伪造 token，声明和合成条件直接共用原来的标识符 token，保留错误位置。

原来的递归结构不变，else 仍绑定到最近的未匹配 if：

```c
if (int a = 1)
    if (int b = 0)
        printf("%d", b);
    else
        printf("%d", a);
```

这里 else 属于内层 if，并且能使用外层 `a`。两个声明分别有自己的作用域。

缺少 `)` 继续使用 `consume(TokenType.RParen)` 的现有恢复，报告 `j`。不要把完整声明末尾的 `consume(TokenType.Semicolon)` 搬进来，否则正常的新语法会被误报 `i`。

缺少类型后的标识符、`=`、合法初值，以及出现数组、多个声明等不符合本产生式的情况，当前框架没有通用语法错误恢复保证，可能抛出解析异常。本文不为这些形式虚构课程错误码，也不将“未产生错误条目”当作解析成功。

## 5. 语义分析：修改 `Traverser.java`

本节所有类型均已导入或属于已有方法参数类型，**无须增加 import**。

### 5.1 替换 `visitStmt()` 中的 if 分支

原分支为：

```java
} else if (stmt instanceof IfStmt ifStmt) {
    analyzeCond(ifStmt.getCond());
    visitStmt(ifStmt.getThenStmt());
    visitStmt(ifStmt.getElseStmt());
```

仅将这段替换为：

```java
} else if (stmt instanceof IfStmt ifStmt) {
    analyzeIf(ifStmt);
```

后面现有的 `} else if (stmt instanceof WhileStmt whileStmt) {` 及其他分支保持原样。不需要整体重写 `visitStmt()`。

### 5.2 新增完整的 `analyzeIf()`

把下面方法加入 `Traverser` 类，例如放在 `visitStmt()` 后、`analyzeSwitch()` 前。

```java
private void analyzeIf(IfStmt stmt) {
    boolean hasDeclaration = stmt.hasConditionDecl();
    if (hasDeclaration) {
        symbols.newScope();
    }
    try {
        if (hasDeclaration) {
            analyzeVarDecl(stmt.getConditionDecl());
        }
        analyzeCond(stmt.getCond());
        visitStmt(stmt.getThenStmt());
        visitStmt(stmt.getElseStmt());
    } finally {
        if (hasDeclaration) {
            symbols.exitScope();
        }
    }
}
```

执行顺序：

1. 仅为声明形式创建作用域。
2. `analyzeVarDecl()` 在新作用域内注册标量，并分析初值。
3. `analyzeCond()` 解析合成条件中的 `LVal`，绑定到刚注册的变量。
4. 依次检查 then 和 else；这里是静态检查，即使初值是常量，也要分析两个分支。
5. 最后退出新增作用域；普通 if 沿用原来的作用域行为。

具体复用关系：

- `analyzeVarDecl()` → `symbols.addVariable()`：注册变量，保留已有重名和保留名检查。
- `VarDef.getLength() == null`：得到 `int` 或 `char` 标量类型。
- `analyzeInitVal()` → `analyzeExp()`、`checkInitializerType()`：检查未定义名称、函数实参、初值类型等。
- `analyzeCond()` → `analyzeLVal()`：为条件变量设置符号绑定和表达式类型。
- `visitStmt(BlockStmt)`：then、else 有花括号时各自继续开子作用域。
- 现有 `loopDepth`、`breakDepth` 不在 `analyzeIf()` 中增加，if 自己不是循环或 switch。

不要把初值传给 `analyzeCond()`：它是普通局部变量初值，遵循 `Exp` 的既有规则，例如项目对仅允许出现在条件中的 `!` 仍按原规则处理。

也不要在 then 结束时就退出新增作用域，否则 else 访问变量会错误地报 `c`；不要给普通 if 无条件开作用域，否则会改变原有程序的符号表编号。

## 6. 作用域、初始化与错误处理细节

### 6.1 外层同名变量和内层遮蔽

```c
int main() {
    int a = 9;
    if (int a = 0) {
        int a = 3;
        printf("%d", a);
    } else {
        printf("%d", a);
    }
    printf("%d", a);
    return 0;
}
```

三个 `a` 属于不同作用域，合法。else 中绑定 if 声明的 `a`，if 后绑定函数块中的 `a`。未来后端实现后，本例应输出 `09`。

这里允许 then 的 `{ ... }` 内再次声明 `a`，是严格按照题图额外套一层块的等价关系及本项目嵌套块遮蔽规则推导的，不能直接套用其他语言的 if 声明规则。

### 6.2 初值中的同名引用

现有 `analyzeVarDecl()` 先注册变量，再分析初值，本方案完全复用这一顺序：

```c
int a = 9;
if (int a = a) {
    printf("%d", a);
}
```

其中右侧 `a` 会解析到新声明的局部 `a`，不能据此认为初值为外层的 `9`。项目当前没有未初始化读取检查，不应把此例作为具有确定运行结果的测试。若扩展另行要求右侧优先查找外层变量，需要额外修改声明时机；题图没有给出这种要求。

### 6.3 现有错误码的复用

| 场景 | 预期处理 |
| --- | --- |
| 初值引用不存在的名称 | `c`，由已有表达式分析报告 |
| 初值函数实参数量错误 | `d` |
| 初值类型与 `BType` 不同，或初值是数组/void 值 | `e` |
| 分支块内同层重复声明变量 | `b` |
| if 后使用已失效变量，且外层没有同名变量 | `c` |
| 声明形式缺少 `)` | `j`，位置沿用前一个 token 的位置 |
| 新变量在分支中被赋值 | 合法，不应报常量修改 `h` |
| 非循环、非 switch 环境中在 if 内执行 `break` | `m`，if 不提供 break 目标 |

每行最终最多输出一条错误，沿用 `ErrorTable.add()` 的优先级规则；测试时应将不同错误安排在不同行或不同程序中。

符号输出中的变量记录不会在 `exitScope()` 时删除，这是现有符号表保留声明输出的设计。判断变量是否泄漏应看 `getVariable()` 的活动作用域查找结果，不能仅凭 `symbol.txt` 仍列出变量就判断作用域退出失败。

## 7. 代码生成阶段的后续要求

当前 `src/utils/Controller.java` 只调用词法、语法和语义阶段，仓库没有可直接修改的 IR 或目标代码生成器。因此本次方案的完整 Java 实现范围是前端；以下是后续后端的行为要求，不是当前可执行的生成器代码。

```text
进入整个声明 if 的局部作用域
分配变量 a 的存储
求初值表达式一次，并将结果写入 a
读取 a，按是否为 0 跳转到 then 或 else/end
生成 then 与 else（若有），在必要处跳至汇合点
到达汇合点，结束 a 的作用域
```

例如 `if (int a = get_int())` 只能读取一次输入。条件节点必须读取 `a`，不能再次调用 `get_int()`。声明 if 出现在 while 循环体时，每次执行到它都要重新初始化，不能因复用 `VarDecl` 包装就把它当作全局变量或 static 变量处理。

## 8. 实施后的验收用例

以下片段放在合法函数中独立验证，必要变量、函数声明见表。当前仓库源码尚未应用本方案，不能直接用它验收新增语法。

| 编号 | 场景与片段 | 前端预期 |
| --- | --- | --- |
| T01 | `if (int a = 1) printf("%d", a);` | 成功；if AST 带一个普通标量声明 |
| T02 | `if (int a = 0) ; else printf("%d", a);` | 成功；else 能解析到 a |
| T03 | `if (char c = 'A') printf("%c", c);` | 成功；变量类型保持 Char |
| T04 | `int b = 2; if (int a = b + 1) a = 4;` | 成功；初值允许局部变量，新变量可修改 |
| T05 | `if (int a = get_int()) printf("%d", a);` | 成功；初值只有一个调用节点，条件只有 a 的读取 |
| T06 | `int a = 9; if (int a = 1) ; printf("%d", a);` | 成功；if 后绑定外层 a |
| T07 | `if (int a = 1) { int a = 2; }` | 成功；显式块是新增作用域的子作用域 |
| T08 | `if (int a = 1) ;` 后在下一行 `a = 2;` | 下一行 `c` |
| T09 | `if (int a = missing) ;` | `c` |
| T10 | `if (int a = 'A') ;` 或 `if (char c = 1) ;` | 分别 `e` |
| T11 | `if (char c = (char)1) ;` | 成功；显式转换保持有效 |
| T12 | `int arr[2]; if (int a = arr) ;` | `e`；标量不能用整个数组初始化 |
| T13 | `if (int a = 1) { int x; int x; }` | 重复 x 处 `b` |
| T14 | `if (int a = 1) break;`（无外层循环或 switch） | `m` |
| T15 | `while (1) { if (int a = 1) continue; break; }` | 成功；原有循环深度有效 |
| T16 | 两个连续的 `if (int a = 1) ;` | 成功；两次声明所属作用域不同 |
| T17 | `if (int a = 1) if (int b = 0) ; else printf("%d", a);` | 成功；else 属于内层，外层 a 仍可见 |
| T18 | `if (1) ;`、`char c = 'A'; if ((int)c) ;` | 成功；普通条件和强制转换回归 |
| T19 | 独立一行 `if (int a = 1`，下一行 `{ a = 2; }` | 第一行 `j`，仍能恢复并解析后面的块 |
| T20 | `if (int a[2] = 1) ;`、`if (const int a = 1) ;`、`if (int a = 1, b = 2) ;` | 不属于新文法；解析不得作为成功验收，不要求通用结构化错误恢复 |

还应检查符号作用域编号：对下面程序，main 的作用域为 2，if 声明作用域为 3，then 块为 4，else 块为 5；普通 `if (1) ;` 本身不占新编号。

```c
int main() {
    if (1) ;
    if (int a = 1) {
        int x;
    } else {
        int y;
    }
    return 0;
}
```

在清空符号表后独立编译，预期符号输出为：

```text
3 a Int
4 x Int
5 y Int
```

后端实现后，再验证第 6.1 节的输出和下例：

```c
int main() {
    if (int a = get_int()) {
        printf("%d\n", a);
    } else {
        printf("%d\n", a);
    }
    return 0;
}
```

输入 `0` 输出 `0`，输入 `7` 输出 `7`；两者都只消耗一个输入整数。当前前端不能作为这些运行结果已经通过验证的证据。

## 9. 推荐修改流程与完成标准

1. 按第 1 节固定标量初值与作用域规则，确认 `VarInit → Exp` 的对接约定。
2. 按第 3 节替换 `IfStmt.java`，保留普通 if 的三参数构造器。
3. 按第 4 节替换解析方法并新增两个辅助方法；只输出实际文法节点，内部合成包装不写入 watcher。
4. 按第 5 节替换语义分支并新增 `analyzeIf()`；声明先于条件，新增作用域覆盖两个分支。
5. 用 JDK 17+ 执行 `./run.sh --build-only`，同时编译核心和 UI。
6. 分别检查 token、语法输出、真实 AST 字段、符号绑定与错误表，执行第 8 节用例，回归原有普通 if 和最近 else 绑定。
7. 后端出现后，再实现并验证一次初始化、读取变量判定和零/非零分支行为。

核心前端完成标准：三个文件按本文修改后能够编译；声明 if 与题图的显式块改写具有一致的作用域和类型检查结果；普通 if 的语法输出及作用域编号不变。

## 10. 本文交付与校验范围

本次交付文件仅为 `docs/文法扩展-if语句中声明变量.md`。项目中已有的源码修改不属于本次工作，本文以当前工作区实际接口为依据，不要求覆盖或回退这些修改。

已将第 3～5 节 Java 代码块直接提取到临时源码副本中，使用 JDK 21 编译全部 `src/`、`ui/` 源文件，并通过 45 项断言检查，覆盖第 8 节各类场景及以下细节：

- then/else 可见性、if 后变量失效、外层同名变量与子块遮蔽。
- 合成条件绑定新变量、初始化调用仅在初值位置出现、内部包装未污染语法输出。
- 最近 else 绑定、连续声明 if、普通 if 不新增作用域编号。
- 初值类型、数组/void 初值、函数参数数量、未定义引用及缺少右括号恢复。
- 非法声明形式拒绝，以及初值中同名引用绑定新声明的现有行为。

校验前后对仓库已有跟踪文件进行内容哈希比对，内容一致；临时副本的修改没有写回项目。以上校验仅证明文档方案在当前前端上的可编译性和已覆盖行为，运行时数值输出仍需等待后端实现后验证。
