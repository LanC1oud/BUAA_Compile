# 文法扩展方案：`do…while` 循环

状态：修改建议，尚未实施。本例展示如何添加关键字、语句 AST、循环上下文与定向恢复测试。返回[示例目录](README.md)。

## 1. 规则与语义

在已有 `Stmt` 中增加一个候选式：

```text
Stmt -> 'do' Stmt 'while' '(' Cond ')' ';'
```

采用后测试循环规则：

- 先执行一次循环体，再计算条件；条件非零继续，零则退出。
- 循环体是一条 `Stmt`，允许普通语句、空语句、块、条件及嵌套循环；不要求一定使用 `{}`。
- 末尾分号是本语句的一部分，不能省略；不能复用 `while` 分支而漏掉这个分号。
- `continue` 跳到本层循环的条件检测位置，`break` 跳到对应的循环 / switch 出口。
- `do` 本身不创建新作用域，循环体若为 `BlockStmt`，按现有规则创建块作用域。体内块的局部变量在尾部条件处不可见。
- 条件使用现有 `Cond`，沿用当前条件类型和 `!` 的上下文规则。
- 不新增全路径返回分析。当前函数结尾检查仍要求末尾块项是 `ReturnStmt`，不能仅凭循环体中有 `return` 就假定函数满足返回要求。

## 2. 文件清单

| 文件 | 修改内容 |
| --- | --- |
| `src/frontend/token/TokenType.java` | 增加 `Do("DOTK")` |
| `src/frontend/token/Keywords.java` | 增加 `do` 关键字映射；复用原有 `While` |
| `src/frontend/ast/statement/DoWhileStmt.java` | 新增体、条件与关键字位置结构 |
| `src/frontend/Parser.java` | 语句入口分派和完整的 `parseDoWhileStmt()` |
| `src/frontend/Traverser.java` | 分派新语句，并正确维护循环、break 上下文 |
| `ui/compilerui/pipeline/SyntaxReference.java`、`SyntaxHighlighter.java` | 新关键字与语句文法说明 |
| 扩展文法文件 / `testgen/sysy.ebnf`、token 配置 | 新候选式及 `do -> DOTK` |
| `testgen/oracle.py`、`testgen/boundaries.py` 及相应测试 | 新语句的循环上下文、作用域、跳转合法性及恢复边界 |

`Lexer.readIdentifier()` 已通过 `Keywords.TABLE` 判断关键字，通常不改扫描控制流；`Symbols`、表达式 AST、错误枚举也无需新增条目。

## 3. token 与关键字

在 `TokenType` 枚举增加：

```java
Do("DOTK"),
```

在 `Keywords.TABLE` 初始化列表增加：

```java
Map.entry("do", TokenType.Do)
```

沿用文件实际使用的 `Map.entry` / 静态导入写法。只有完整单词 `do` 成为关键字；`done`、`do1`、`_do` 仍是标识符。扩展会使原先命名为 `do` 的变量不再合法，这是词法兼容性变化。

## 4. AST 与完整解析方法

### 4.1 新增 `DoWhileStmt.java`

```java
package frontend.ast.statement;

import frontend.ast.expression.Cond;
import frontend.token.Token;

public class DoWhileStmt extends Stmt {
    private final Token doToken;
    private final Stmt body;
    private final Cond cond;

    public DoWhileStmt(Token doToken, Stmt body, Cond cond) {
        this.doToken = doToken;
        this.body = body;
        this.cond = cond;
    }

    public Token getDoToken() {
        return doToken;
    }

    public Stmt getBody() {
        return body;
    }

    public Cond getCond() {
        return cond;
    }
}
```

保留 `doToken` 供诊断或未来后端定位使用。继承的语法标签仍是 `<Stmt>`，不新增 `<DoWhileStmt>`。

### 4.2 `Parser.parseStmt()` 增加入口

在普通表达式 / 赋值兜底之前，与已有 `While` 分支并列添加：

```java
if (among(TokenType.Do)) {
    return parseDoWhileStmt();
}
```

### 4.3 完整的 `parseDoWhileStmt()`

```java
private Stmt parseDoWhileStmt() {
    Token doToken = consume(TokenType.Do);
    Stmt body = parseStmt();
    consume(TokenType.While);
    consume(TokenType.LParen);
    Cond cond = parseCond();
    consume(TokenType.RParen);
    consume(TokenType.Semicolon);
    return watch(new DoWhileStmt(doToken, body, cond));
}
```

在 `Parser` 中引入新类。递归 `parseStmt()` 自然支持 `do if (...) ... else ... while (...);` 和嵌套 `do`，最近匹配的 `else` 仍由已有 `parseIfStmt()` 处理。

`consume(RParen)` / `consume(Semicolon)` 复用 `j/i` 缺符号恢复，位置按上一 token 计算。缺 `while`、缺左括号、缺整个条件等情况没有现成专用码，不要自动映射为 `i/j`。这些情况应拒绝解析，若希望进一步恢复，需要另行定义同步策略并保证 token 前进。

语法轨迹顺序：先 `DOTK do`，再循环体及其 `<Stmt>`，然后 `WHILETK while`、条件、右括号、分号，最后外层 `<Stmt>`。

## 5. 完整语义分析方法

在 `Traverser.visitStmt()` 的 `WhileStmt` 附近增加分支，并导入 `DoWhileStmt`：

```java
} else if (stmt instanceof DoWhileStmt doWhileStmt) {
    analyzeDoWhile(doWhileStmt);
```

新增方法：

```java
private void analyzeDoWhile(DoWhileStmt stmt) {
    loopDepth++;
    breakDepth++;
    try {
        visitStmt(stmt.getBody());
        analyzeCond(stmt.getCond());
    } finally {
        breakDepth--;
        loopDepth--;
    }
}
```

`loopDepth` 控制 `continue` 合法性，`breakDepth` 控制 `break` 合法性，两个都要维护。退出时必须恢复计数，否则循环后的非法 `break/continue` 可能被误判为合法。

这里先访问循环体，再检查条件；块作用域在 `visitStmt(BlockStmt)` 内进入并退出，因此不会泄漏到条件。例如没有外层 `i` 时，`do { int i = 0; } while (i);` 在尾部 `i` 报 `c`。若存在同名外层变量，则尾部条件引用外层变量。

这是静态遍历，遇到 `while (0)` 也要检查循环体内的名字和类型；遇到 `break` 也不因此停止后面语句的静态分析。

## 6. 后端需要保留的控制流

未来降低为如下结构：

```text
body:
    执行循环体
    continue 的目标是 condition
condition:
    计算条件；非零跳回 body，否则跳到 exit
exit:
```

不能直接照搬前测试 `while`，把入口和 `continue` 都放在体前：这会改变首次执行和 `continue` 行为。嵌套循环 / switch 时需要控制流目标栈；语义分析中的深度计数只能判断跳转是否允许，不能决定后端跳到哪一个标签。

## 7. 完整样例与阶段预期

```c
int main() {
    int i = 0;
    do {
        i = i + 1;
    } while (i < 3);
    printf("%d", i);
    return 0;
}
```

实施后 `lexer.txt` 中 `do` 输出 `DOTK do`，`while` 仍是 `WHILETK while`；`parser.txt` 复用 `<Stmt>` 和 `<Cond>`；`error.txt` 为空。`symbol.txt` 只应包含：

```text
2 i Int
```

循环体创建一个空的符号作用域，但没有声明，不输出额外符号行。未来运行输出为 `3`。

独立作用域错误用例：

```c
int main() {
    do {
        int i = 0;
    } while (i);
    return 0;
}
```

预期 `error.txt` 为 `4 c`，不能因为先遍历过循环体就让局部 `i` 泄漏到条件。

独立缺分号用例：

```c
int main() {
    do ; while (0)
    return 0;
}
```

预期 `error.txt` 为 `2 i`，并继续解析下一行的 `return`。`do ; while (0);` 则完全合法。

## 8. 定向边界

| 场景 | 预期 / 核查点 |
| --- | --- |
| `do x = x + 1; while (0);` | 无大括号，首次仍执行一次 |
| `do ; while (0);` | 空语句体，不额外要求块 |
| `do { ... continue; ... } while (cond);` | continue 必须经过尾部条件，不直接重进循环体 |
| `do { break; } while (1);` | 合法，break 退出该循环 |
| do 体内 switch 的 `break` / `continue` | break 退出 switch；continue 转至外围最近循环的条件 |
| do 外只有 switch 的 `continue` | 仍报 `m`，不能因为 switch 允许 break 就允许 continue |
| do 之后独立的 `break;` / `continue;` | `m`，检查深度恢复 |
| 多层 `while` / `do` 混合，含 if/else | 最近跳转目标、悬空 else、语法输出嵌套 |
| `do { int i; } while (i);`、内外同名 i | 前者未定义 `c`；后者正确绑定外层 |
| 条件里未定义名称、整个数组、`!x` | 分别覆盖 `c`、非基本类型 `e`、已有条件上下文 |
| `do ; while (0;` | 缺右括号 `j`，仍应消费分号继续解析 |
| 缺 while / 缺条件 / 缺循环体 / 截断文件 | 解析拒绝、无死循环，不假造统一错误码 |
| `int f(){ do { return 1; } while(0); }` | 沿用当前末尾检查，仍报函数末尾 `g` |
| 深层 do 嵌套、连续大量短 do | 递归深度、输出数量、上下文恢复 |

后端验收 `continue` 时可用：`int x = 0; do { x = x + 1; continue; } while (x < 1);`，正确结果为 `x == 1`；若错误地绕过条件，该程序可能无法退出。执行测试必须设置超时。当前阶段可通过 AST 与控制流设计审查这个要求，不能声称前端结果已经证明运行行为。

## 9. 测试生成器适配

扩展规则文件写 `Stmt -> 'do' Stmt 'while' '(' Cond ')' ';'`，token 配置写 `{"do":"DOTK"}`，默认合并已有 `Stmt` 即可。语法片段模式可以先验证 lexer / parser 预期，完整程序的可靠预期还需修改 `testgen/oracle.py`：

1. 在 `SemanticModel.statement()` 显式增加 `do` 处理，找到循环体与尾部 `Cond`，不要仅按首关键字是否为 `while` 判断循环上下文。
2. 处理循环体时增加 loop / break 上下文，块内符号按已有作用域规则进出；条件在块退出后检查。
3. 给“循环外控制语句”“do 内 switch 的 continue”“退出 do 后的控制语句”各建独立预期。
4. 将首次执行、空体、缺尾分号、缺右括号及多层嵌套加入 `testgen/boundaries.py`，补独立测试。

默认随机语句包装在外层 `while (0)` 中，可能掩盖“生成器忘了给 do 增加循环上下文”的缺陷：即使内层处理错误，break / continue 也会因外层循环而通过。因此这些控制上下文用例应使用表中的独立完整程序，不能只测默认随机包装。

单个新规则和 token 配置无法自动生成运行时预期；当前工具目标仍是前端四类输出。未来要验证控制流执行，应另建带超时的执行器和独立的运行答案。
