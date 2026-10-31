# 2022 编译实验期中：repeat…until 与十六进制整数

状态：仅提供修改方案，不修改工程源码。根据用户提供的《2022编译实验期中考试题目.md》和当前仓库接口整理。返回[文法扩展示例集](README.md)。下文文件路径均相对于项目根目录。

## 1. 先明确考试要求

本题新增两项能力：

```text
Stmt -> 'repeat' Stmt 'until' '(' Cond ')' ';'
Number -> IntConst | HexConst
```

| 新单词 | 输出类别 | 示例 |
| --- | --- | --- |
| `repeat` | `REPEATTK` | `REPEATTK repeat` |
| `until` | `UNTILTK` | `UNTILTK until` |
| 十六进制整数 | `HEXCON` | `HEXCON 0X86ab` |

考试只要求**词法和语法分析**，不要求语义错误处理或执行程序。结果是 token 和语法成分交错的解析轨迹，统一写入 `output.txt`，不能只输出纯 token，也不能输出符号表。

题面排除了 `<BlockItem>`、`<Decl>`、`<BType>`，其余语法成分在分析完成时输出。十六进制数仍通过 `<Number>`，循环仍通过 `<Stmt>`；不额外输出 `<RepeatUntilStmt>`、`<HexConst>`、前缀或单个十六进制数字的标签。

### 1.1 题面转录问题与本文命名

题面中的 `HecadecimalConst`、`HexdecimalConst`、`HexadecimalConst` 及前缀 / 数字拼写不一致，实际指同一种十六进制常量。本文统一使用 Java 枚举名 `HexConst`，**输出名严格保留 `HEXCON`**。其词法结构为：

```text
HexConst -> ('0x' | '0X') HexDigit {HexDigit}
HexDigit -> '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9'
          | 'a' | 'b' | 'c' | 'd' | 'e' | 'f'
          | 'A' | 'B' | 'C' | 'D' | 'E' | 'F'
```

这是词法结构说明，整个数只生成一个 token。前缀后至少一个数字；正负号仍属于表达式运算符，例如 `-0x2A` 是 `MINU -` 和 `HEXCON 0x2A`。

提供的 Markdown 将输入文件名写成了 `testfile.tt`，可能有转录问题。本文沿用当前工程实际的 `testfile.txt`；如果评测平台明确要求其它文件名，只调整 `Configure.source`。输出文件名按题目明确要求使用 `output.txt`。

### 1.2 与当前 2026 工程的关系

本方案在当前工程上增加上述两项能力，保留已有 char、static、switch、显式转换等规则。`Number` 实际改成：

```text
Number -> IntConst | HexConst | CharConst
```

第 2～7 节完成本题**新增部分**的词法、语法及输出适配；第 8 节是接入本项目语义阶段的可选改动。题面完整的 2022 基础文法还含多维数组、递归初始化和专用 getint 语句，与当前工程不完全相同，迁移范围见第 10 节。不能将新增部分通过样例理解为所有 2022 基础规则都已适配。

## 2. 修改文件清单

| 文件 | 是否必需 | 修改内容 |
| --- | --- | --- |
| `src/frontend/token/TokenType.java` | 必需 | 增加 Repeat、Until、HexConst |
| `src/frontend/token/Keywords.java` | 必需 | 增加 repeat / until 的关键字映射 |
| `src/frontend/Lexer.java` | 必需 | `readNumber()` 识别 0x / 0X，保留原始数值文本 |
| `src/frontend/ast/statement/RepeatUntilStmt.java` | 新增，必需 | 保存循环体与条件，继承 `<Stmt>` 标签 |
| `src/frontend/Parser.java` | 必需 | repeat 入口、完整循环解析、Number 和表达式起始集合 |
| `src/utils/HomeworkConfig.java` | 考试模式必需 | 切到 Syntax，目标文件改为 output.txt |
| `src/frontend/ast/expression/Number.java` | 仅语义 / 常量求值时必需 | 按 16 进制解释 HexConst，token 文本不变 |
| `src/frontend/Traverser.java` | 仅语义阶段必需 | 遍历新循环，并维护 break / continue 上下文 |
| `ui/compilerui/pipeline/SyntaxHighlighter.java` | UI 可选 | 数字着色扫描支持十六进制 |
| `ui/compilerui/pipeline/SyntaxReference.java` | UI 可选 | 新关键字说明和十六进制常量说明 |
| `testgen/grammar.py`、`testgen/oracle.py`、边界集 | 扩展生成器时需要 | 增加真正的十六进制词法类别及新语句语义，见第 11 节 |

通常无需修改 `Symbols`、`Token`、`TokenStream`、`ParserWatcher`、`ConstFolder`、`BinaryExp` 或错误枚举。以下代码保留未展示的原有成员和 import；新语句在 Parser 中由已有 `frontend.ast.statement.*` 导入。

## 3. 新 token 与关键字

### 3.1 `TokenType.java`：新增三个枚举项

```java
Repeat("REPEATTK"),
Until("UNTILTK"),
HexConst("HEXCON"),
```

它们可以分别放在 `While` 和 `IntConst` 附近，注意枚举项逗号。

### 3.2 `Keywords.java`：新增两个映射

在 `Keywords.TABLE = Map.ofEntries(...)` 中增加：

```java
entry("repeat", TokenType.Repeat),
entry("until", TokenType.Until),
```

当前文件已静态导入 `Map.entry`。`readIdentifier()` 自动查表，无需添加两份专用扫描函数；`repeatCount`、`until1` 仍应识别为 `IDENFR`。

## 4. Lexer：完整替换数字扫描方法

修改 `src/frontend/Lexer.java`。主循环已经把以 ASCII 数字开始的串交给 `readNumber()`，不必改 `lex()` 的分派。

### 4.1 替换完整的 `readNumber()`

```java
private void readNumber() throws IOException {
    int first = reader.read();
    Pair<Integer, Integer> start = reader.location();
    StringBuilder number = new StringBuilder();
    number.append((char) first);

    int next = reader.read();
    if (first == '0' && (next == 'x' || next == 'X')) {
        number.append((char) next);
        int digit = reader.read();
        if (!isHexDigit(digit)) {
            // 题目只要求处理合法输入；这里快速拒绝缺少数字的前缀。
            throw new IllegalStateException("十六进制前缀后缺少数字");
        }
        do {
            number.append((char) digit);
            digit = reader.read();
        } while (isHexDigit(digit));
        reader.unread(digit);
        addToken(TokenType.HexConst, number.toString(), start);
        return;
    }

    while (isAsciiDigit(next)) {
        number.append((char) next);
        next = reader.read();
    }
    reader.unread(next);
    addToken(TokenType.IntConst, number.toString(), start);
}
```

### 4.2 在同一类中增加辅助方法

```java
private static boolean isHexDigit(int c) {
    return isAsciiDigit(c)
            || (c >= 'a' && c <= 'f')
            || (c >= 'A' && c <= 'F');
}
```

现有 `isAsciiDigit()` 可直接复用。使用 int 接收 `reader.read()`，使 EOF 的 -1 不会被误当数字。现有 PushbackReader 只有一个字符的回退容量，上述写法每次最多回退一个字符，不会连续回退 0 和 x。

**不能先扫描成十进制 0，再由 Parser 拼接 xfff。** 必须在数字扫描入口判断前缀，否则会输出 `INTCON 0` 和 `IDENFR xfff`，与题目要求不同。

**不能把词法文本提前改成十进制。** `0xfff` 必须输出 `HEXCON 0xfff`，不能输出 `HEXCON 4095`；大小写、前缀和前导零都要保留。考试词法阶段也不必为了输出 token 就把字面量转换成 Java int。

| 输入 | 词法结果 |
| --- | --- |
| `0` / `123` | `INTCON 0` / `INTCON 123` |
| `0x0` / `0X0` | `HEXCON 0x0` / `HEXCON 0X0` |
| `0xfff` / `0X86ab` | 每个串各一条 HEXCON，文本原样保留 |
| `0x00Af+1` | HEXCON、PLUS、INTCON |
| `-0x2A` | MINU、HEXCON，不合并符号 |
| `0 /*comment*/ + 0x2` | INTCON、PLUS、HEXCON，不跨空白 / 注释拼接前缀 |

`0x`、`0X` 不是合法整数；本方案的异常只用于快速结束，不定义考试之外的新错误码。`0x1g` 会扫描成 HEXCON 与后续标识符，不能据此声称实现了完整的非法数值诊断。

## 5. repeat…until：AST 和完整解析方法

### 5.1 新增 `RepeatUntilStmt.java`

```java
package frontend.ast.statement;

import frontend.ast.expression.Cond;

public class RepeatUntilStmt extends Stmt {
    private final Stmt body;
    private final Cond cond;

    public RepeatUntilStmt(Stmt body, Cond cond) {
        this.body = body;
        this.cond = cond;
    }

    public Stmt getBody() {
        return body;
    }

    public Cond getCond() {
        return cond;
    }
}
```

继承 `Stmt.getName()` 就会输出 `<Stmt>`，不要覆盖成 `<RepeatUntilStmt>`。即使考试只输出语法轨迹，当前 Parser 也需要返回一个实际的 Stmt 节点，不能返回 null 来替代整个循环。

### 5.2 `Parser.parseStmt()` 增加分派

在已有 while 分支附近、赋值 / 表达式兜底分支之前加入：

```java
if (among(TokenType.Repeat)) {
    return parseRepeatUntilStmt();
}
```

### 5.3 同一类中新增完整解析方法

```java
private Stmt parseRepeatUntilStmt() {
    consume(TokenType.Repeat);
    Stmt body = parseStmt();
    consume(TokenType.Until);
    consume(TokenType.LParen);
    Cond cond = parseCond();
    consume(TokenType.RParen);
    consume(TokenType.Semicolon);
    return watch(new RepeatUntilStmt(body, cond));
}
```

逐项对应 `'repeat' Stmt 'until' '(' Cond ')' ';'`，循环体是一条任意 Stmt，而非固定的 Block。因此下面三种形式都能复用同一个方法：

```c
repeat i = i + 1; until (i > 3);
repeat { i = i + 1; } until (i > 3);
repeat ; until (1);
```

`parseStmt()` 自身负责空语句、块、if / else 及嵌套循环，不能只调用 `parseBlock()`。循环体是赋值语句时，其分号与循环尾部分号分别属于不同产生式，都要消费。

`Cond` 必须调用 `parseCond()`，不能改成 `parseExp()`，否则 `until (i > 3 && j != 0)` 解析不完整，也不会输出正确的条件层标签。`until` 不是独立语句的起始符，不给它额外增加 `parseStmt()` 分支。

## 6. Parser：十六进制数的两个入口

修改 `src/frontend/Parser.java`，保留原有 char 支持。

### 6.1 完整替换 `parseNumber()`

```java
private Number parseNumber() {
    return watch(new Number(consumeAny(
            TokenType.IntConst, TokenType.HexConst, TokenType.CharConst)));
}
```

### 6.2 完整替换 `atExpressionStart()`

```java
private boolean atExpressionStart() {
    return among(TokenType.Plus, TokenType.Minus, TokenType.Not,
            TokenType.LParen, TokenType.Ident,
            TokenType.IntConst, TokenType.HexConst, TokenType.CharConst);
}
```

第二处不可遗漏：原方法用于 `return` 的可选表达式、函数调用首个实参及部分数组长度入口。若只改 `parseNumber()`，题目样例中的 `return 0X86ab;` 仍可能被当作不带表达式的 return，随后在 HEXCON 处误报缺分号。

当前 `parsePrimaryExp()` 在不是括号 / 标识符时直接调用 `parseNumber()`，因此不用再复制一份十六进制分支。已启用 `++` / `~` 时，在表达式起始集合中同时保留对应 token。

## 7. 考试输出模式

### 7.1 `HomeworkConfig.java` 的两处修改

将阶段字段设为：

```java
public static Hw hw = Hw.Syntax;
```

将 `getTarget()` 中 Syntax 分支改为：

```java
case Syntax -> "output.txt";
```

其余 Lexer / Semantic 分支保留。`Configure.target` 会通过 `HomeworkConfig.getTarget()` 获取目标文件；通常无需另外修改 Configure。

当前 `Controller` 在 Syntax 模式会先开启 `displayTokensWithAst`，然后创建 ParserWatcher，最后输出 watcher 并在进入 Traverser 前结束。保留这个顺序，否则可能得到空文件或误输出 `symbol.txt`。也不能在词法扫描时先写出全部 token 再追加所有语法标签，那样会破坏交错顺序。

### 7.2 `watch()` 和预读输出

由 `consume()` 输出真实消费的 token，由各 parse 方法最后的 `watch()` 输出语法成分。`among()` 仅预读，不输出。`isAssignStmt()` 的试探必须继续保存 / 恢复 token、错误和 watcher，避免赋值左值被重复输出。

`0xfff` 的局部轨迹应为：

```text
HEXCON 0xfff
<Number>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
```

其后按所在位置输出 `<Exp>` 或 `<ConstExp>` 等。`repeat ; until (0x1);` 的关键顺序为 repeat → 空语句的分号 / `<Stmt>` → until → 条件各层 → 右括号 → 循环尾分号 / `<Stmt>`。块体会另外输出 `<Block>` 和表示块语句的 `<Stmt>`，这些是不同语法成分，不能合并删除。

### 7.3 实施后的运行方法

在项目根目录保存输入为 `testfile.txt`，应用上述考试模式配置后运行：

```powershell
.\run.bat --build-only
& 'C:\Users\96330\.jdks\openjdk-24.0.2+12-54\bin\java.exe' -cp build/classes Compiler
```

Java 路径按本机 JDK 17+ 安装位置调整；若 PATH 已是 JDK 17+，也可使用 `java -cp build/classes Compiler`。本机 PATH 中曾优先出现 Java 8，不应把它当成工程可用的编译 / 运行版本。不要用默认启动 GUI 代替命令行考试入口。

## 8. 可选：继续支持当前语义阶段

第 7 节 Syntax 模式不会执行本节。以下内容用于日后恢复 Semantic 模式，使新 AST 能参与本项目类型检查和常量计算；不是 2022 题目新增的考核要求。

### 8.1 `Number.java`：完整替换 `valueOf()`

```java
public static int valueOf(Token token) {
    if (token.is(TokenType.IntConst)) {
        return Integer.parseInt(token.value());
    }
    if (token.is(TokenType.HexConst)) {
        return Integer.parseUnsignedInt(token.value().substring(2), 16);
    }
    if (token.is(TokenType.CharConst)) {
        return token.value().isEmpty() ? 0 : token.value().charAt(0);
    }
    throw new IllegalArgumentException(
            "期望 IntConst、HexConst 或 CharConst，实际是 " + token);
}
```

只在求值时去掉前缀，原 token 文本仍用于输出。`0xfff` 得到 4095，`0X86ab` 得到 34475。现有 `analyzeNumber()` 仅将 CharConst 判为 Char，其余返回 Int，所以 HexConst 无需额外类型分支。

本文可选实现采用 32 位位模式：`0xffffffff` 转为 int 的 -1，超过 32 位数值范围则抛出解析异常。这是与当前 Java int 求值体系的对接选择，**题面并未要求用它限制词法接受范围**。因此不能在 Lexer 中用此函数筛掉本可正确输出的 HEXCON；如评测另有数值范围约定，应据其调整。

此改动使 `const int n = 0x2 + 1; int a[n];` 能正常折叠为长度 3；`FixedValue`、`FixedArray` 和 `ConstFolder` 不必新增十六进制存储结构，保存数值后已不需要关心其进制写法。

### 8.2 `Traverser.java`：加入循环分派和完整方法

在文件顶部导入新类：

```java
import frontend.ast.statement.RepeatUntilStmt;
```

在 `visitStmt()` 中与 WhileStmt 并列加入分支：

```java
} else if (stmt instanceof RepeatUntilStmt repeatStmt) {
    analyzeRepeatUntil(repeatStmt);
```

在同一类增加：

```java
private void analyzeRepeatUntil(RepeatUntilStmt stmt) {
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

两个深度分别判断 continue、break 的合法性；不能只增加其中一个。块体的作用域由现有 `visitStmt(BlockStmt)` 管理，块内声明退出后不能泄漏到尾部条件。条件即使恒真，也要检查循环体中的名字和类型。

### 8.3 未来执行语义：until 为真时退出

```text
body:
    执行循环体
condition:
    计算 Cond
    Cond == 0 时跳回 body
    Cond != 0 时跳到 exit
exit:
```

至少执行一次循环体。continue 应跳到 condition，break 跳到相应出口。与 `do…while` 相比，尾部条件的分支方向相反，不能只替换关键字后原样复用后端逻辑。

当前工程没有本题的执行器；考试输出中的条件仍然是原始 Cond 的解析轨迹，**不要为了模拟 until 就在 Parser 中合成一个 `!` 节点**，否则会多出 token / 标签，导致输出不符。

## 9. 官方样例与验收

### 9.1 题目样例输入

```c
const int CONV = 0xfff;

int func() {
   return 0X86ab;
}

int main()
{
    int PI = CONV + func();

    repeat{
        PI = PI + 1;

    }until(PI > 100);
    return 0;
}
```

### 9.2 题目样例完整输出

```text
CONSTTK const
INTTK int
IDENFR CONV
ASSIGN =
HEXCON 0xfff
<Number>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
<ConstExp>
<ConstInitVal>
<ConstDef>
SEMICN ;
<ConstDecl>
INTTK int
<FuncType>
IDENFR func
LPARENT (
RPARENT )
LBRACE {
RETURNTK return
HEXCON 0X86ab
<Number>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
<Exp>
SEMICN ;
<Stmt>
RBRACE }
<Block>
<FuncDef>
INTTK int
MAINTK main
LPARENT (
RPARENT )
LBRACE {
INTTK int
IDENFR PI
ASSIGN =
IDENFR CONV
<LVal>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
PLUS +
IDENFR func
LPARENT (
RPARENT )
<UnaryExp>
<MulExp>
<AddExp>
<Exp>
<InitVal>
<VarDef>
SEMICN ;
<VarDecl>
REPEATTK repeat
LBRACE {
IDENFR PI
<LVal>
ASSIGN =
IDENFR PI
<LVal>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
PLUS +
INTCON 1
<Number>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
<Exp>
SEMICN ;
<Stmt>
RBRACE }
<Block>
<Stmt>
UNTILTK until
LPARENT (
IDENFR PI
<LVal>
<PrimaryExp>
<UnaryExp>
<MulExp>
<AddExp>
<RelExp>
GRE >
INTCON 100
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
SEMICN ;
<Stmt>
RETURNTK return
INTCON 0
<Number>
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

实施后应逐行比较上面的输出，尤其检查 HEXCON 的文本、条件各层、块体后的 `<Stmt>`，以及最后的 `<CompUnit>`。这里展示的是题目给出的参考答案，不表示当前源码已具备新功能。

### 9.3 新增边界用例

| 场景 | 输入片段 / 核查点 |
| --- | --- |
| 前缀和数字大小写 | `0x0`、`0X0`、`0xaBcDeF`、`0XABCDEF`，均各生成一个 HEXCON |
| 原始写法保留 | `0x000A` 不能规范化成 10 或 0xa |
| 十六进制在各表达式入口 | `return 0x1;`、`f(0X2);`、`int a[0x3];`、`a[0x0] = 0x4;` |
| 带符号和混合算术 | `-0x1 + 2 * 0X3`，符号独立且乘加优先级保持 |
| 紧邻分隔符与文件末尾 | `0x1;`、`f(0x2)`、词法输入末尾仅为 `0Xf`；不得丢最后一个数字 |
| 字符串和注释中的伪 token | `"0xff repeat until"`、`/* repeat 0x1 */` 不拆成新单词 |
| 完整关键字识别 | `repeatCount`、`until1` 为标识符，只有完整单词成为保留字 |
| 循环体形式 | 块体、普通赋值、空语句 `repeat ; until (1);`，不强制加大括号 |
| 条件层级 | `until (i > 0x3 && j != 0)`，覆盖比较、相等、逻辑与及 `<Cond>` |
| 嵌套 repeat | `repeat repeat ; until (1); until (0x1);`，两条循环各输出外层 `<Stmt>` |
| 与 if / else 交互 | `repeat if (x) x = 0; else x = 1; until (x == 0);`，else 由最近 if 解析 |
| 与已有 while / 赋值回溯交互 | 体内嵌套 while、数组元素赋值；预读不多输出 `<LVal>` |
| 非法形状的稳健性 | 缺数字的 `0x`、缺 until、空条件；题目无错误输出要求，不能伪造新错误码 |
| 可选语义回归 | 常量十六进制初值、常量数组元素、数组长度、repeat 内 break / continue、退出循环后的非法跳转 |

新增的独立小程序可用来锁定局部输出，比直接调试长样例更容易定位问题：

```c
int main() {
    int i = 0;
    repeat i = i + 0x1; until (i >= 0X3);
    return 0x0;
}
```

若未来实现执行，i 应依次成为 1、2、3，条件为真时退出；本题只比较语法轨迹，不输出这些变量值。

## 10. 若要兼容完整 2022 基础文法

本题是在当年的已有词法、语法作业上增加两个特性。当前仓库是另一版基础文法，直接应用本文不能自动补齐以下旧能力：

| 2022 基础规则 | 当前差异 | 迁移需要核查的文件 / 方法 |
| --- | --- | --- |
| `ConstDef/VarDef` 的多次 `[...]` | 当前只保存一维长度 | 声明 AST 的维度列表、`parseConstDef()`、`parseVarDef()` |
| `LVal` 的多次 `[...]` | 当前只保存一个下标 | LVal 下标列表、`parseLVal()`；若运行语义阶段还需逐维类型检查 |
| 函数数组形参的后续维度 | 当前仅支持可选的第一对空方括号 | FuncFormalParam 与 `parseFuncFParam()` |
| ConstInitVal / InitVal 的递归花括号 | 当前主要是平铺表达式列表 | 初始化 AST、`parseConstInitVal()`、`parseInitVal()` 递归结构 |
| `LVal '=' 'getint' '(' ')' ';'` | 当前库函数是 `get_int()`，语法形状不同 | 专用 GetInt token / 关键字、赋值语句分支及对应 AST |

尤其不能将题面的 getint 直接重命名成当前的 `get_int` 并走普通函数表达式：题目专用语句不要求那套 `<UnaryExp>/<Exp>` 标签，这样会改变参考输出。已有[定义时 getint 的扩展文档](../文法扩展-getint输入与bitand按位与.md)讨论的是 `VarDef` 中的输入，**不等于本题赋值语句中的 getint**。

若考核数据包含这些基础形状，应先迁移相应 AST / Parser，再做整份 2022 文法回归。反之，本题官方展示的样例使用的基础结构已在当前工程中具备，可以直接用于验证本次两个新增点。新增 char / static / switch 的保留不会在该样例中多输出标签。

## 11. 测试生成器衔接与实施顺序

### 11.1 生成器不能只加一个 HEXCON 名称

当前 `testgen/grammar.py` 的内置词法类别只有 Ident、IntConst、CharConst、StringConst，`testgen/oracle.py` 的数字扫描也以十进制为基础。若要随机生成十六进制数，需同步：

1. 增加 HexConst 类别及 0x / 0X、大小写、前导零等生成策略。
2. 在独立 tokenizer 中将完整十六进制串归为 HexConst，并映射到 HEXCON。
3. 给 Number 增加候选式，为 repeat 增加 Stmt 候选式；输出只出现 `<Number>` / `<Stmt>`。
4. 若生成完整合法程序及符号表，给 oracle 增加十六进制的 Int 类型规则和 repeat 循环上下文；若测常量值，还需独立值求解。

把每个十六进制字符写成普通 EBNF 终结符，会导致多个 token 及多余的语法标签，不能替代单个 HEXCON 的词法类别。仅配置 `--tokens` 也不能让当前扫描器自动识别新数字格式。

### 11.2 建议顺序

1. 增加三个 token、两个关键字，先验证数值扫描与原文输出。
2. 增加 RepeatUntilStmt 及解析入口，验证空体 / 赋值体 / 块体。
3. 更新 Number 和表达式起始集合，覆盖 return、首实参及数组长度入口。
4. 切换 Syntax / output.txt，逐行比对题目完整样例；保持预读和回溯不产生重复输出。
5. 根据实际评测范围核查第 10 节基础文法差异。
6. 若保留 Semantic 模式，另外完成第 8 节并验证常量值与跳转上下文；需要 UI 或生成器时再同步对应适配。

本方案不为题目增加语义错误码，不新增中间表达式标签，也不修改原文法中乘加、条件及赋值的优先级。

## 12. 本文参考代码的验证记录

在 `build` 下创建临时源码副本，提取本文代码应用到副本，使用本机 JDK 24 编译全部核心源码并运行验证；结束后移除临时副本，原工程 `src` 文件的哈希保持不变。

- 考试模式实际生成 `output.txt`，与题目提供的 **122 行输出逐行一致**。
- 验证十六进制原始文本、大小写、前导零、正负号拆分、EOF、关键字边界及字符串 / 注释不误识别。
- 验证空体、单语句体、块体、复合条件、repeat 嵌套、if / else 和 while 嵌套的解析。
- 可选语义代码验证了常量值 3 / 数组长度 3、32 位十六进制值 -1、循环内跳转合法性、循环退出后的 m 及块变量不可见时的 c。
- 缺数字的 `0x` / `0X` 能快速拒绝；没有为它们定义考试要求之外的诊断协议。

这些验证针对本文的两个新增点及选定组合，没有验证第 10 节尚未迁移的全部 2022 基础文法，也不代表原工程源码已实施本方案。
