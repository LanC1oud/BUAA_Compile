# 增量开发题：elif、FuncStmt 与函数声明/定义混排

本次只生成文档，不修改项目源码。本文依据当前工作区给出涉及文件、全部新增或替换代码、错误恢复流程和验收样例。

## 一、题目要求与实现范围

题图明确要求：

```text
新增词法单词：elif，类别码 ELIFTK

Stmt → ...
     | 'if' '(' Cond ')' Stmt
       { 'elif' '(' Cond ')' Stmt }
       [ 'else' Stmt ]

FuncStmt → FuncType Ident '(' [FuncFParams] ')' ';'

CompUnit → {Decl} {FuncDef | FuncStmt} MainFuncDef
```

- elif 可以出现零次、一次或多次，else 仍可选且放在整个分支链末尾。elif 的右小括号可能缺失，报 j。
- FuncStmt 是没有函数体的顶层函数声明，支持原有 FuncType（int/char/void）和有名字的 FuncFParams。它的右小括号可能缺失，报 j；末尾分号可能缺失，报 i。
- 函数定义和声明在全局 Decl 后任意混排，主函数仍在最后；不能只在原有函数定义循环前增加一个声明循环。
- 本题给出的新增要求是词法、语法及 i/j 处理。题图没有规定重复声明、签名冲突、声明后定义和前向调用的语义规则。本文核心方案完成这些明确要求，不擅自套用 C 函数原型规则；第七节单独说明语义边界。
- 这是独立题目的方案，按当前尚未实施前几篇扩展的源码编写，不默认叠加 if 条件中声明变量、getint 或 bitand 的方案。

## 二、涉及文件

| 文件 | 必须/建议 | 改动 |
| --- | --- | --- |
| `src/frontend/token/TokenType.java` | 必须 | 新增 `Elif("ELIFTK")` |
| `src/frontend/token/Keywords.java` | 必须 | 关键字 elif 映射到 TokenType.Elif |
| `src/frontend/ast/function/FuncStmt.java` | 必须新增 | 保存函数类型、名字、形参，输出 `<FuncStmt>` |
| `src/frontend/ast/CompileUnit.java` | 必须 | 存储声明、定义和混排顺序，保留旧 getter |
| `src/frontend/Parser.java` | 必须 | 改写顶层和函数项解析、增加 elif 分支 |
| `ui/compilerui/pipeline/SyntaxReference.java` | 建议 | 补 elif 的中文说明 |

不必为本题修改 Lexer、IfStmt、FuncDef、ParserWatcher、TokenStream、ErrorType、ErrorTable、语法高亮或 AST 面板。也不需要为 elif 新增 AST 类。Traverser 对 elif 可复用已有嵌套 IfStmt 分支，但函数声明的语义未扩展，具体限制见第七节。

## 三、完整代码与放置位置

以下代码是一套可共同应用的语法实现；未列出的方法及 import 保持原样。Parser 原有方法前的旧文法注释同步改为第一节的新产生式，避免说明与方法体不一致。

### 1. `src/frontend/token/TokenType.java`：增加枚举项

在 `Else("ELSETK"),` 附近增加以下一行。Java 枚举项沿用当前项目的驼峰命名，输出名称严格使用题目要求的 `ELIFTK`。

```java
Elif("ELIFTK"),
```

### 2. `src/frontend/token/Keywords.java`：登记关键字

在 `Map.ofEntries(...)` 中增加以下条目。`Lexer.readIdentifier()` 本来就会完整读取单词后查表，无须修改 Lexer。`elifValue` 等完整单词仍是普通标识符。

```java
entry("elif", TokenType.Elif),
```

### 3. 新增 `src/frontend/ast/function/FuncStmt.java`：完整文件

这里的 FuncStmt 是顶层函数声明，不是函数体内的 Stmt。继承 ASTNode，不能继承 Stmt 后再接入 parseStmt()，否则会额外开放块内函数声明。该节点没有 Block，不应伪造空函数体。

```java
package frontend.ast.function;

import frontend.ast.ASTNode;
import frontend.token.Token;

// FuncStmt -> FuncType Ident '(' [FuncFParams] ')' ';'
public class FuncStmt extends ASTNode {
    private final FuncType funcType;
    private final Token ident;
    private final FuncFormalParams params;

    public FuncStmt(FuncType funcType, Token ident, FuncFormalParams params) {
        this.funcType = funcType;
        this.ident = ident;
        this.params = params;
    }

    public FuncType getFuncType() {
        return funcType;
    }

    public Token getIdent() {
        return ident;
    }

    // 无形参时为 null，约定与已有 FuncDef 一致。
    public FuncFormalParams getParams() {
        return params;
    }

    @Override
    protected String getName() {
        return "<FuncStmt>";
    }
}
```

### 4. `src/frontend/ast/CompileUnit.java`：完整替换

新增函数声明列表，以及保留声明/定义混排顺序的 functionItems。旧 getFunctions() 仍只返回定义，不能把 FuncStmt 强转为 FuncDef；否则现有语义阶段会尝试分析不存在的函数体。Parser 统一调用 addFunctionItem()，不要同时再次调用 addFunction()，以免重复入表。

```java
package frontend.ast;

import frontend.ast.declaration.Decl;
import frontend.ast.function.FuncDef;
import frontend.ast.function.FuncStmt;
import frontend.ast.function.MainFuncDef;
import java.util.LinkedList;
import java.util.List;

public class CompileUnit extends ASTNode {
    private final List<Decl> declarations = new LinkedList<>();
    private final List<FuncDef> functions = new LinkedList<>();
    private final List<FuncStmt> functionStatements = new LinkedList<>();
    // 按源码顺序保存函数定义和声明，供之后的处理使用。
    private final List<ASTNode> functionItems = new LinkedList<>();
    private final MainFuncDef[] mainFunc = new MainFuncDef[1];

    public CompileUnit() {
    }

    public void addDeclaration(Decl decl) {
        declarations.add(decl);
    }

    public void addFunction(FuncDef func) {
        functions.add(func);
        functionItems.add(func);
    }

    public void addFunctionStatement(FuncStmt stmt) {
        functionStatements.add(stmt);
        functionItems.add(stmt);
    }

    public void addFunctionItem(ASTNode item) {
        if (item instanceof FuncDef func) {
            addFunction(func);
        } else if (item instanceof FuncStmt stmt) {
            addFunctionStatement(stmt);
        } else {
            throw new IllegalArgumentException("函数项必须是 FuncDef 或 FuncStmt");
        }
    }

    public void setMainFunc(MainFuncDef func) {
        mainFunc[0] = func;
    }

    public List<Decl> getDeclarations() {
        return declarations;
    }

    // 保留旧接口，只返回定义，避免破坏现有 Traverser。
    public List<FuncDef> getFunctions() {
        return functions;
    }

    public List<FuncStmt> getFunctionStatements() {
        return functionStatements;
    }

    public List<ASTNode> getFunctionItems() {
        return functionItems;
    }

    public MainFuncDef getMainFunc() {
        return mainFunc[0];
    }

    @Override
    protected String getName() {
        return "<CompUnit>";
    }
}
```

### 5. `src/frontend/Parser.java`：补充 import

其余类型通过已有 `frontend.ast.function.*`、`frontend.ast.statement.*` 和 `java.util.List`、`LinkedList` 导入，无须重复添加。

```java
import frontend.ast.ASTNode;
```

### 6. 同文件：完整替换 `parse()`

按题图的三个阶段解析：先全局 Decl，再任意次 FuncDef/FuncStmt，最后一个 MainFuncDef。函数头的判断直接写在 parse() 中，不增加专门的函数起始判断方法。不要分成“先所有声明，再所有定义”两个循环，因为题目允许两者混排。主函数继续使用原来的 parseMainFuncDef()。本文按指定产生式检查 main 之后不能再有内容；题外的非法顶层结构抛异常，不自造课程错误码。

```java
public Parser parse() {
    // CompUnit -> {Decl} {FuncDef | FuncStmt} MainFuncDef
    while (among(TokenType.Const, TokenType.Static)
            || (among(TokenType.Int, TokenType.Char)
                && !among(1, TokenType.Main)
                && !(among(1, TokenType.Ident) && among(2, TokenType.LParen)))) {
        ast.addDeclaration(parseDecl());
    }

    while (among(TokenType.Int, TokenType.Char, TokenType.Void)
            && among(1, TokenType.Ident)
            && among(2, TokenType.LParen)) {
        ast.addFunctionItem(parseFunctionItem());
    }

    ast.setMainFunc(parseMainFuncDef());
    if (!eof()) {
        throw new IllegalStateException("MainFuncDef 后不能再出现顶层内容");
    }
    watch(ast);
    return this;
}
```

第一个循环在 int/char 开头时排除 main 和“Ident 后接左括号”的函数头；第二个循环直接判断 Int/Char/Void + Ident + 左括号。Main 是独立 token，不会进入普通函数项。两个循环的判断都在每次迭代时重新计算，缺分号恢复后可以继续识别下一条函数。

### 7. 同文件：将旧 `parseFuncDef()` 整体替换为 `parseFunctionItem()`

删除旧方法，用下面完整方法替代；原来只解析定义的入口已被第 6 节替换。本方法只解析一次共同的 FuncType/Ident/参数表，然后看到左花括号就构造 FuncDef，其余情况尝试消费分号并构造 FuncStmt。缺右括号通过现有 consume(RParen) 报 j。不要保留旧方法中意外 token 时直接 __tokens.next() 的分支，它可能吞掉声明分号或下一函数的开头。

```java
private ASTNode parseFunctionItem() {
    FuncType funcType = parseFuncType();
    Token ident = consume(TokenType.Ident);
    consume(TokenType.LParen);
    FuncFormalParams params = null;
    if (among(TokenType.Int, TokenType.Char)
            && among(1, TokenType.Ident)
            && !among(2, TokenType.LParen)) {
        params = parseFuncFParams();
    }
    consume(TokenType.RParen);

    if (among(TokenType.LBrace)) {
        return watch(new FuncDef(funcType, ident, params, parseBlock()));
    }
    // 不要求必须预先看见分号：缺分号时由 consume() 报 i 并保持当前位置。
    consume(TokenType.Semicolon);
    return watch(new FuncStmt(funcType, ident, params));
}
```

形参入口判断直接写在本方法中，不新增辅助方法。`Int/Char + Ident` 是形参的开头；再排除其后紧接左小括号的情况，是为了防止缺少 `)` 和 `;` 时把下一条 `int g(...)` 误读为当前函数的形参。合法 FuncFParam 的标识符后不可能紧接左小括号，因此这项检查不会排除合法形参。

### 8. 同文件：完整替换 `parseIfStmt()`

`{...}` 表示允许零个或多个 elif，所以必须使用 while。每个 elif 的条件独立调用 consume(RParen)，支持缺右括号 j。最终把分支从后向前接成已有 IfStmt 的 else 链，复用现有 Traverser 对嵌套 if 的分析；原有 IfStmt.java 不需要新增字段。只对实际完整的 if 语句调用一次 watch()。

```java
private Stmt parseIfStmt() {
    consume(TokenType.If);
    consume(TokenType.LParen);
    Cond cond = parseCond();
    consume(TokenType.RParen);
    Stmt thenStmt = parseStmt();

    List<Cond> elifConds = new LinkedList<>();
    List<Stmt> elifStmts = new LinkedList<>();
    while (among(TokenType.Elif)) {
        consume(TokenType.Elif);
        consume(TokenType.LParen);
        Cond elifCond = parseCond();
        consume(TokenType.RParen);
        elifConds.add(elifCond);
        elifStmts.add(parseStmt());
    }

    Stmt elseStmt = null;
    if (among(TokenType.Else)) {
        consume(TokenType.Else);
        elseStmt = parseStmt();
    }

    // 从后向前组装 else-if 链。内部合成 IfStmt 不单独 watch()。
    for (int i = elifConds.size() - 1; i >= 0; i--) {
        elseStmt = new IfStmt(elifConds.get(i), elifStmts.get(i), elseStmt);
    }
    return watch(new IfStmt(cond, thenStmt, elseStmt));
}
```

### 9. UI 建议项：`ui/compilerui/pipeline/SyntaxReference.java`

在 KEYWORD_NOTES 的 Map.ofEntries(...) 中增加以下一项即可。高亮代码已自动查 Keywords.TABLE，AstTreePanel 也自动识别 `<FuncStmt>`，不需要修改其类或增加专用枚举。

```java
Map.entry("elif", "前面的条件不成立时继续检查的分支"),
```

## 四、为什么这样解析 elif

例如：

```c
if (a) S1
elif (b) S2
elif (c) S3
else S4
```

内部结构等价于：

```text
IfStmt(a, S1,
    IfStmt(b, S2,
        IfStmt(c, S3, S4)))
```

原 Traverser 的 visitStmt() 已经递归检查 then 和 else，能自然检查所有条件和语句；无需再新增 elif 分派。if 本身也不新增作用域，带花括号的分支仍由原有 BlockStmt 创建作用域。

**内部 AST 的等价改写不能改变语法输出。** 题目产生式中每个 elif 后只有 Cond 和 Stmt，没有嵌套的源码 if。因此内部合成的 IfStmt 不调用 watch()，也不生成额外 IFTK。实际消费的是 `ELIFTK elif`，每个条件和分支语句照常输出，最后仅输出这个完整 if 构造对应的一次 `<Stmt>`。

对无花括号的嵌套 if，沿用当前递归下降对 else 的就近绑定方式，并将它应用于 elif：

```c
if (a)
    if (b) S1
    elif (c) S2
    else S3
```

elif 和 else 归属内层 if。需要归属外层时，用花括号先结束内层语句。这是本文沿用现有解析器结构的消歧约定。

原有 `else if (...)` 也仍然合法：else 后的 Stmt 可以是一个真正的 if。这种源码中的第二个 if 会正常输出自己的 `<Stmt>`，与合成的 elif 分支不同。

## 五、函数声明的判定与错误恢复

### 1. 共享函数头，而不是试解析整棵函数

函数定义和函数声明共有：

```text
FuncType Ident '(' [FuncFParams] ')'
```

所以先完成这部分，再按当前位置分流：

| 当前位置 | 操作 |
| --- | --- |
| 左花括号 `{` | parseBlock()，构造 FuncDef |
| 分号 `;` | consume(Semicolon)，构造 FuncStmt |
| 下一条函数头或主函数开头 | 视为声明缺分号，consume(Semicolon) 报 i，但不消费当前位置 |

不要在解析前扫描到分号才决定是不是声明：题目允许缺分号，这会扫描进下一条函数。也不要把“不是分号”直接当成定义，否则缺分号的声明会错误进入 parseBlock()。

### 2. 复用 consume() 的插入恢复

现有 consume(RParen/Semicolon) 在不匹配时记录 j/i，并保持 token 位置，相当于插入缺失符号。本文不改这个机制，也不向 watcher 写入虚构的右括号或分号。

```c
int f(int x;
int g() { return 0; }
int main() { return 0; }
```

解析 f 的参数后看到分号：consume(RParen) 在 x 所在行报 j，分号保留；随后消费真实分号构造 FuncStmt，下一轮仍能完整解析 g。

```c
int f(int x)
int g(int y) { return y; }
int main() { return 0; }
```

解析 f 的右括号后看到下一条函数的 int：它不是左花括号，因此消费分号失败，在 f 的右括号所在行报 i；该 int 留给下一轮解析 g。

```c
void f(
int g() { return 0; }
int main() { return 0; }
```

这是额外的边界检查：即使同时缺少 `)` 和 `;`，内联的形参入口判断也不会把 int g 误读为 f 的参数。两个错误位置相同，现有 ErrorTable 每行保留一条、等优先级保留先记录项，因此最终为 j，不保证同一行同时输出 i 和 j。

### 3. 保持声明和定义的节点区别

```c
int f();
int g() { return 0; }
int h(int x);
int main() { return 0; }
```

functionItems 按顺序为 FuncStmt(f)、FuncDef(g)、FuncStmt(h)；getFunctions() 只包含 g。语法输出的函数项末尾分别是 `<FuncStmt>`、`<FuncDef>`、`<FuncStmt>`。声明不生成 `<Block>`、`<FuncDef>` 或 `<Stmt>`。

## 六、可直接使用的验收样例

### A. 多个 elif，正常输出 ELIFTK

```c
int main() {
    int x = 2;
    if (x == 0) x = 10;
    elif (x == 1) x = 20;
    elif (x == 2) x = 30;
    else x = 40;
    return 0;
}
```

词法结果含两次 `ELIFTK elif`。AST 是三层 IfStmt；源码有四个赋值分支及一个 if 构造，所以这部分语法输出共有五个 `<Stmt>`，不能因为内部合成两个 IfStmt 而多输出两次。不要把函数末尾 return 的 `<Stmt>` 也算入这五个。

### B. elif 缺右小括号

```c
int main() {
    int x = 2;
    if (x == 0) x = 10;
    elif (x == 1
    ) x = 20;
    return 0;
}
```

上例只是换行，仍有右括号，应当无错误。真正的缺括号样例是：

```c
int main() {
    int x = 2;
    if (x == 0) x = 10;
    elif (x == 1
        x = 20;
    else x = 30;
    return 0;
}
```

预期错误为 `4 j`，仍恢复并保留后面的分支。位置取条件表达式最后一个 token 的行号。

### C. 函数声明的两种缺符号

```c
int f(int x;
char g(char c)
int h() { return 0; }
int main() { return 0; }
```

预期语法错误：

```text
1 j
2 i
```

f、g 都构造为 FuncStmt；h 完整构造为 FuncDef，主函数存在。

### D. 需要覆盖的其余场景

| 场景 | 预期 |
| --- | --- |
| 普通 if，无 elif、有/无 else | 原有语法输出不变 |
| 一个或多个 elif，无 else | 合法，最后一个 elseStmt 为 null |
| 各 elif 分别缺右括号，错误分布不同行 | 各自在条件末尾报 j，并继续解析 |
| elif 条件含函数调用、括号表达式 | 沿用 parseCond() 及各层既有恢复 |
| elif 分支块引用未定义变量 | 如运行语义阶段，原有 Traverser 能检查合成分支中的 c |
| `int f();`、`char g(char c);`、`void h(int a[]);` | 合法 FuncStmt，保留返回类型和完整形参 |
| 声明、定义、声明交错出现 | functionItems 与源码顺序相同 |
| `int f(;` 后紧接另一条函数定义 | f 的左括号所在行 j；真实分号和下一条函数都保留 |
| `int f(int x { return x; }` | 报 j，仍为 FuncDef，不能误判为 FuncStmt |
| `int f(int x)` 后下一行 `int g(int y);` | f 行 i；g 不被吞掉 |
| `int f();` 后最后为 main | main 按原有专用产生式解析 |
| `int elifValue = 1;` | 标识符，不切分出 ELIFTK |
| 孤立 elif、else 后再接 elif、块内函数声明、main 后再有声明 | 不属于新文法，不承诺通用错误恢复 |

测试必须确认没有解析异常、完整 AST 和 `<CompUnit>` 已生成，不能仅凭错误表为空就认为解析成功。已有 UI Pipeline 可用 crashMessage() 区分异常与正常错误恢复。

## 七、语义阶段的边界

本题未要求设计函数原型语义。本文保留 Traverser.spawn() 的现有循环，只分析 getFunctions() 返回的函数定义，跳过 FuncStmt，因此：

- 新语法可以输出 `<FuncStmt>`，也可以保留其类型和参数信息，但不等于已经把它注册为可调用函数。
- 若函数只有声明没有定义，调用它仍可能被原有语义阶段报 c。若声明在前、定义在后，而中间函数调用它，也不保证解决前向调用。
- 不会因 FuncStmt 没有函数体而对它检查缺少 return 的 g，也不会把声明参数注册到活动变量作用域。
- 题目没有规定的重复声明、声明/定义签名冲突、只有声明的函数是否需要实现等行为，本文未作新增语义判断。

如果后续要求完整原型语义，应再根据给定规则按 functionItems 顺序注册签名，区分已声明/已定义状态，比较返回类型及参数类型，并避免声明参数污染定义函数体的局部符号。不能直接对声明和定义分别调用现有 symbols.addFunction()：后者会把合法的声明后定义误报为重名 b。

本次方案以题图明确的词法/语法评测为验收目标，没有新增 IR 或目标代码生成器。elif 的运行时链式分支含义为：依次检查，首次条件成立后只执行该分支；真正的执行输出留待后端实现。

## 八、推荐流程与校验记录

1. 加入 ELIFTK 的枚举值和关键字映射。
2. 新增 FuncStmt，扩展 CompileUnit 的函数项存储。
3. 加 Parser import，替换 parse()，将函数头判断直接内联。
4. 用 parseFunctionItem() 替换原 parseFuncDef()，形参入口判断也直接内联，不新增这两个判断的辅助方法。
5. 替换 parseIfStmt()，确认内部合成节点不额外输出。
6. 使用 JDK 17+ 运行 `./run.sh --build-only`，再分词法与语法阶段检查上述用例。
7. 将没有新语法的合法程序与原解析器输出逐字比较，检查普通 if、else if、函数定义和 main 的回归。

已在临时源码副本中应用本文代码，使用 JDK 21 编译全部 `src/` 与 `ui/` Java 文件，并通过 93 项断言，覆盖：

- ELIFTK 词法类别、完整单词匹配、多个 elif 的内部链结构、零 elif 回归、就近绑定和实际语法节点数量。
- elif 缺右括号后的恢复，以及现有 Traverser 可以检查合成分支中的未定义引用。
- FuncStmt 的返回类型和形参保存、定义/声明源码顺序、声明不生成函数体或 FuncDef 输出。
- 空形参或非空形参缺右括号、声明缺分号、同时缺符号时保留下一条函数或 main，以及缺右括号的函数定义仍正确分流。
- 对题外非法形式的拒绝，以及当前函数声明未注册到语义符号表的明确边界。

另外，对 6 个没有新增语法的合法程序，将修改后与原解析器的语法输出及错误文本逐字比较，结果一致，包含普通 if、else if、嵌套 if、函数定义、数组形参、while 和 switch。

校验前后对仓库已有跟踪文件进行内容哈希比较，内容一致。本次交付只有此 Markdown 文档，临时副本代码未写回项目；上述结果不代表函数原型语义或后端执行已经实现。
