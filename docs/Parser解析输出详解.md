# Parser 解析输出详解：优先级、函数调用与 if/else

本文使用当前项目实际执行得到的 Parser 输出，不涉及尚未落地的 elif、FuncStmt 等新增文法。源码未改动。

## 1. 示例程序

```c
int main() {
    int a = 1;
    if (a < 3)
        a = a + 2 * get_int();
    else
        a = -a;
    return a;
}
```

程序同时包含变量初始化、关系条件、赋值、加法与乘法、无参函数调用、单目负号和 return。这里只进行编译前端分析，不执行 get_int()，也不读取输入。

## 2. 完整实际输出

共 97 行；下面是原始输出，不加入行号或解释性注释：

```text
INTTK int
MAINTK main
LPARENT (
RPARENT )
LBRACE {
INTTK int
IDENFR a
ASSIGN =
INTCON 1
<Number>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
<Exp>
<InitVal>
<VarDef>
SEMICN ;
<VarDecl>
IFTK if
LPARENT (
IDENFR a
<LVal>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
<RelExp>
LSS <
INTCON 3
<Number>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
<RelExp>
<EqExp>
<LAndExp>
<LOrExp>
<Cond>
RPARENT )
IDENFR a
<LVal>
ASSIGN =
IDENFR a
<LVal>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
PLUS +
INTCON 2
<Number>
<PrimaryExp>
<UnaryExp>
<MulExp>
MULT *
IDENFR get_int
LPARENT (
RPARENT )
<UnaryExp>
<MulExp>
<AddExp>
<Exp>
SEMICN ;
<Stmt>
ELSETK else
IDENFR a
<LVal>
ASSIGN =
MINU -
<UnaryOp>
IDENFR a
<LVal>
<PrimaryExp>
<UnaryExp>
<UnaryExp>
<MulExp>
<AddExp>
<Exp>
SEMICN ;
<Stmt>
<Stmt>
RETURNTK return
IDENFR a
<LVal>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
<Exp>
SEMICN ;
<Stmt>
RBRACE }
<Block>
<MainFuncDef>
<CompUnit>
```

## 3. 输出的两个入口

- `consume()`：成功消费一个 token 后，将 token.toString() 加入 ParserWatcher。
- `watch(node)`：将 node.toString() 加入 ParserWatcher；ASTNode.toString() 返回 getName()，如 `<AddExp>`，然后 watch 返回原节点。

因此 token 按源码顺序出现；某个语法节点的标签在该节点的子项解析完成后出现。输出在递归下降过程中逐步记录，并非单纯的 token 列表，也不能根据同名标签出现多次就判断重复打印。

`Configure.debug.displayTokensWithAst` 必须在创建 ParserWatcher 之前为 true。语法阶段的 Controller 已经按这个顺序设置；否则 watcher 不保存这些行。

## 4. 分段对照

这里的行号指第 2 节原始输出从 1 开始计数的行号。

| 输出行 | 对应源码 | 完成的语法成分 |
| --- | --- | --- |
| 1～5 | `int main() {` | 主函数头和块左花括号 |
| 6～19 | `int a = 1;` | 数值到初值，再到变量定义、变量声明 |
| 20～41 | `if (a < 3)` | 完整 Cond 和右括号 |
| 42～66 | `a = a + 2 * get_int();` | then 赋值语句 |
| 67～83 | `else a = -a;` | else 赋值语句，随后完成整个 if |
| 84～93 | `return a;` | return 语句 |
| 94～97 | `}` | Block、MainFuncDef、CompUnit 依次完成 |

### 4.1 主函数头：为什么没有 FuncType

`parseMainFuncDef()` 直接 consume(Int)、consume(Main)、consume(LParen)、consume(RParen)，然后调用 parseBlock()。它没有调用 parseFuncType()，所以不会输出 `<FuncType>`。

parseBlock() 先消费 `{`，此时块内内容尚未解析完，所以第 5 行仅输出 `LBRACE {`，还不输出 `<Block>`。

### 4.2 声明：为什么 VarDef 在分号之前

声明规则为：

```text
VarDecl → BType VarDef ';'
VarDef  → Ident '=' InitVal
InitVal → Exp
Exp → AddExp → MulExp → UnaryExp → PrimaryExp → Number
```

读到数值 `1` 后，按方法返回顺序输出 `<Number>`、`<PrimaryExp>`、`<UnaryExp>`、`<MulExp>`、`<AddExp>`、`<Exp>`。初值完成输出 `<InitVal>`，变量定义完成输出 `<VarDef>`。

分号由外层 parseVarDecl() 消费，因此顺序必须是：

```text
<InitVal>
<VarDef>
SEMICN ;
<VarDecl>
```

当前项目按既定输出规则省略 `<BType>`、`<Decl>`、`<BlockItem>`，不要为这些包装额外输出标签。

### 4.3 条件：为什么有 RelExp，却没有 Exp

`parseIfStmt()` 调用 parseCond()，条件规则是：

```text
Cond → LOrExp → LAndExp → EqExp → RelExp → AddExp → ...
```

这里不经过 parseExp()，所以 `a < 3` 的整个条件中不会出现 `<Exp>`。

parseRelExp() 先解析左边的 a，并构造叶子形式 `RelExp → AddExp`，因此在 `<` 前已经输出一次 `<RelExp>`。读到 `<` 后，再解析右边的 3，构造组合形式 `RelExp → RelExp '<' AddExp`，再输出一次 `<RelExp>`。

接下来 EqExp、LAndExp、LOrExp 均完成各自的单项形式，依次输出对应标签，最后输出 `<Cond>`。这几个标签依然需要输出，即使源码没有 `==`、`&&` 或 `||`。

### 4.4 then 赋值：左边为什么没有 Exp

`parseStmt()` 在确定是赋值后，直接调用 parseLVal() 解析左边的 a，因此赋值开头是：

```text
IDENFR a
<LVal>
ASSIGN =
```

右侧调用 parseExp()，相同的 a 就会继续包装成 PrimaryExp、UnaryExp、MulExp、AddExp。左右两处相同标识符的输出层级不同，来自它们在产生式中承担的角色不同。

赋值之前的 isAssignStmt() 虽然会试解析一次 LVal，但 finally 中 trace.restore() 同时恢复 token、错误表和 watcher，试解析记录不会保留到最终输出。因此左边的 a 不会被打印两次。

### 4.5 加乘优先级：为什么运算符前已经有 AddExp/MulExp

右侧的结构是：

```text
a + (2 * get_int())
```

parseAddExp() 的关键逻辑是：

```java
AddExp exp = watch(new AddExp(parseMulExp()));
while (among(TokenType.Plus, TokenType.Minus)) {
    Token op = consumeAny(TokenType.Plus, TokenType.Minus);
    exp = watch(new AddExp(exp, op, parseMulExp()));
}
```

第一条语句先完成 a 的 MulExp 和 AddExp，所以第 49～50 行已经输出 `<MulExp>`、`<AddExp>`；接下来才在第 51 行消费 PLUS。

右操作数调用 parseMulExp()，先将 2 包装成一个 MulExp，在第 56 行输出；然后才消费 MULT，并解析 get_int()。整个乘积完成后在第 62 行输出新的 `<MulExp>`，再返回外层完成加法，在第 63 行输出新的 `<AddExp>`。

这些标签各自代表叶子形式和组合形式。循环是左递归文法的实现方式，必须保留每次完成一个对应节点时的输出。

### 4.6 无参调用：为什么没有 PrimaryExp 和 FuncRParams

调用匹配：

```text
UnaryExp → Ident '(' [FuncRParams] ')'
```

因此 `get_int()` 对应的输出只有：

```text
IDENFR get_int
LPARENT (
RPARENT )
<UnaryExp>
```

函数名在这里不是变量引用，不输出 `<LVal>`；这个候选式也不经过 PrimaryExp，因此不输出 `<PrimaryExp>`。括号内没有实参，没有调用 parseFuncRParams()，所以不输出 `<FuncRParams>`。

### 4.7 单目负号：为什么 UnaryExp 连续出现两次

`-a` 匹配：

```text
UnaryExp → UnaryOp UnaryExp
UnaryOp  → '-'
```

先消费 MINU 并完成 UnaryOp，然后解析操作数 a。a 完成内层 `UnaryExp → PrimaryExp` 后输出第一个 `<UnaryExp>`；随后外层 `UnaryExp → UnaryOp UnaryExp` 完成，再输出第二个 `<UnaryExp>`。

因此第 76～77 行的连续标签是两个不同节点，不是重复输出错误。

### 4.8 else 末尾：为什么 Stmt 连续出现两次

整个 if 产生式为：

```text
Stmt → 'if' '(' Cond ')' Stmt ['else' Stmt]
```

then 的赋值完成后输出一次 `<Stmt>`，对应第 66 行。else 的赋值完成后输出一次 `<Stmt>`，对应第 82 行。此时整个 if 才解析完，外层 IfStmt 再输出一次 `<Stmt>`，对应第 83 行。

所以这个程序共有四个 `<Stmt>`：then 赋值、else 赋值、整个 if、return。声明不算 Stmt。

### 4.9 函数结束：为什么没有额外 Stmt

return a 的表达式、分号和 Stmt 在第 84～93 行完成。消费最后的 `}` 后，按调用层级返回：

```text
RBRACE }
<Block>
<MainFuncDef>
<CompUnit>
```

主函数体由 parseMainFuncDef() 直接调用 parseBlock()，没有经过 `Stmt → Block`，因此这里只输出 `<Block>`，没有额外的 `<Stmt>`。如果是 if 分支中的显式 `{ ... }` 作为一条语句，则会再有 BlockStmt 对应的 `<Stmt>`。

## 5. 核对结果

使用 JDK 21 编译当前 `src/` 的临时副本，并实际执行词法、语法、语义分析，均无错误；Parser 输出确为上面的 97 行。输入函数没有实际执行。临时副本代码未写回项目，保留了用户已有的 Parser 修改。

## 6. 补充：参数表后必须接右括号，为何恢复判断允许看到左花括号

正常文法仍然是：

```text
FuncDef → FuncType Ident '(' [FuncFParams] ')' Block
```

右括号必须存在。下面的检查识别的是“正常结束或可以恢复的缺符号位置”，没有把左花括号作为右括号的合法替代：

```java
if (!among(TokenType.RParen, TokenType.LBrace)) {
    throw new IllegalStateException("函数参数表后的内容无法按缺右括号恢复");
}
consume(TokenType.RParen);
return watch(new FuncDef(funcType, ident, params, parseBlock()));
```

| 当前 token | 检查后的实际处理 |
| --- | --- |
| `)` | consume(RParen) 正常消费，无错误 |
| `{` | consume(RParen) 报缺右括号 j，不消费 `{`；接着 parseBlock() 消费真实 `{` |
| 其他 token | 无法在这里按“仅缺右括号”恢复，抛出异常 |

例如 `int f(int x { return x; }` 中，`{` 是函数体的开头，参数表后实际缺少 `)`。consume(RParen) 的失败会在 x 所在行记录 j，并保留 `{`。错误程序能继续建树，但并没有被当成无错误的合法程序；最终语法阶段会输出错误表。

因此这段边界检查不能理解成 `')' | '{'` 的新产生式。若在调用 consume(RParen) 之前就对 `{` 直接抛异常，反而会失去题目要求的 j 诊断和继续解析函数体的能力。
