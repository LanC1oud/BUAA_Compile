# 文法扩展：定义时 getint() 输入与 bitand 按位与

状态：仅提供修改方案；本次不修改项目源码。本文按当前工作区实际接口编写，列出全部核心代码，并将多维数组访问作为明确标注的配套扩展。

## 一、先读清这两条文法

```text
VarDef → Ident { '[' ConstExp ']' }
       | Ident { '[' ConstExp ']' } '=' InitVal
       | Ident '=' 'getint' '(' ')'
MulExp → UnaryExp | MulExp ('*' | '/' | '%' | 'bitand') UnaryExp
```

第一条实际包含两项变化：允许零个或多个数组维度，以及标量专用的输入定义。当前工程只存储一个维度，输入库函数名为 `get_int`，因此不能只修改一处 while 或将新单词当普通函数使用。

- `int i = getint();` 的运行语义是 `int i; i = getint();`，读一次整数后写入 i，不创建额外作用域，不把初值当编译期常量。
- `getint` 是本新增产生式的终结符，仅允许专用定义分支。此次不新增 `i = getint();`、`return getint();`、`int i = getint() + 1;` 等源码形式；题图的等价写法说明执行效果，不自动增加这些产生式。现有 `get_int()` 通用库函数继续可用。
- getint 返回 Int。按本项目严格类型规则，`char c = getint();` 报 e；若题目允许隐式窄化，需另行改变此规则，不能自行假定。
- 题图未定义全局或 static 定义时运行输入的行为。本文采用现有工程“global/static 初值必须为编译期常量”的规则，拒绝这两处 getint；沿用现有常量上下文函数调用的 c 诊断。该范围和诊断是本文的对接约定。
- bitand 是整数按位与：`6 bitand 3` 为 2；逻辑与 `6 && 3` 为 1，二者不能混用。
- bitand 与 `* / %` 同优先级且左结合：`6 bitand 3 * 2` 为 `(6 & 3) * 2 = 4`；`6 * 3 bitand 5` 为 `(6 * 3) & 5 = 0`。它们都比加减优先。
- 图中未说明 char 的 bitand 规则。本文沿用现有乘除模：两边必须是相同基本类型，Int/Int 得 Int，Char/Char 得 Char，混合、数组或 void 报 e。现有 char 结果截断机制保持不变；若评测只允许 Int，应另加该限制。
- `{ '[' ConstExp ']' }` 是多维声明。InitVal 本身未变，本文保留现有平铺列表，约定多维列表按行优先排列；不增加嵌套花括号初值。ConstDef 和 FuncFParam 未在题图中修改，继续保留原有一维限制。

## 二、文件与实施顺序

| 层次 | 文件 | 改动 |
| --- | --- | --- |
| 词法 | `src/frontend/token/TokenType.java`、`Keywords.java` | 新增 GetInt 和 BitAnd；Lexer 自动查表 |
| 声明 AST | `src/frontend/ast/declaration/VarDef.java` | 用列表保存所有维度，输入复用已有初值节点 |
| 解析 | `src/frontend/Parser.java` | 定义输入分支、合成输入初值、bitand 同级左结合 |
| 类型 | `src/frontend/symbol/type/Type.java` | 嵌套数组构造及结构比较 |
| 语义 | `src/frontend/Traverser.java` | 全部维度、输入返回类型、常量上下文、平铺数组初值 |
| 常量计算 | `src/utils/ConstValue.java` | `left & right` |
| AST 注释 | `src/frontend/ast/expression/MulExp.java` | 注释中的运算符列表加 bitand；类结构不变 |
| 多下标访问（配套） | `src/frontend/ast/expression/LVal.java`、Parser、Traverser | 新增第三条 LVal 文法的完整实现，见第 19～21 小节 |
| UI 说明（建议） | `ui/compilerui/pipeline/SyntaxReference.java` | 新保留字的中文说明 |

无需新增 `BitAndExp`，也无需修改 BinaryExp、MulExp 构造器、ConstFolder、SymbolTable 或注册 getint 库函数。MulExp 类的注释同步改成 `// MulExp -> UnaryExp | MulExp ('*' | '/' | '%' | 'bitand') UnaryExp` 即可。Parser 对应方法前的旧注释也同步更新，运行逻辑以本文方法体为准。

## 三、完整新增或替换代码

以下代码共同组成实现方案。代码块明确标明是完整文件、完整方法还是插入片段，不含省略的方法体。保持其余原有方法与 import 不变。

### 1. `src/frontend/token/TokenType.java`

在枚举常量列表中加入以下两项。`GetInt` 与旧库函数 `get_int` 不同，`BitAnd` 与逻辑与 `And` 不同。输出名称 `GETINTTK`、`BITAND` 是本文约定，如评测指定名称，应按评测调整。

```java
GetInt("GETINTTK"),
BitAnd("BITAND"),
```

### 2. `src/frontend/token/Keywords.java`

在 `Map.ofEntries(...)` 中增加以下条目。`Lexer.readIdentifier()` 会完整读取单词后查表，所以不必修改 Lexer：`bitand` 是运算符 token，`bitandValue` 仍是标识符。原有 `&`、`&&` 的规则不变。

```java
entry("getint", TokenType.GetInt),
entry("bitand", TokenType.BitAnd),
```

### 3. `src/frontend/ast/declaration/VarDef.java`：完整替换

图中的 `{ ... }` 是零次或多次，不是可选一次，因此维度需要改成列表。保留三参数旧构造器，列表构造采用工厂，避免 `new VarDef(ident, null, init)` 在重载时产生歧义。输入初值复用 `InitVal`，不另加输入标志字段。

```java
package frontend.ast.declaration;

import frontend.ast.ASTNode;
import frontend.ast.expression.ConstExp;
import frontend.token.Token;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// VarDef -> Ident { '[' ConstExp ']' } [ '=' InitVal ]
//         | Ident '=' 'getint' '(' ')'
public class VarDef extends ASTNode {
    private final Token ident;
    private final List<ConstExp> lengths;
    private final InitVal initVal;

    // 保留旧接口，避免现有标量、一维数组的构造代码失效。
    public VarDef(Token ident, ConstExp length, InitVal initVal) {
        this(ident, length == null ? List.of() : List.of(length), initVal, true);
    }

    private VarDef(Token ident, List<ConstExp> lengths,
                   InitVal initVal, boolean internal) {
        this.ident = ident;
        // 允许 null 维度占位，供缺少长度或右括号时的原有恢复机制使用。
        this.lengths = Collections.unmodifiableList(new ArrayList<>(lengths));
        this.initVal = initVal;
    }

    public static VarDef ofDimensions(Token ident, List<ConstExp> lengths,
                                      InitVal initVal) {
        return new VarDef(ident, lengths, initVal, true);
    }

    public Token getIdent() {
        return ident;
    }

    public List<ConstExp> getLengths() {
        return lengths;
    }

    // 兼容旧调用；新语义分析必须通过 getLengths() 处理所有维度。
    public ConstExp getLength() {
        return lengths.isEmpty() ? null : lengths.get(0);
    }

    public boolean isArray() {
        return !lengths.isEmpty();
    }

    public InitVal getInitVal() {
        return initVal;
    }

    public boolean hasInitVal() {
        return initVal != null;
    }

    @Override
    protected String getName() {
        return "<VarDef>";
    }
}
```

### 4. `src/frontend/Parser.java`：替换 `parseVarDef()`

本文件已有全部所需 import。分号仍由 `parseVarDecl()` 消费；逗号定义也沿用外层逻辑。读完维度后，只允许无维度变量使用 `getint()` 专用分支，不把它开放为普通表达式。

```java
private VarDef parseVarDef() {
    Token ident = consume(TokenType.Ident);
    List<ConstExp> lengths = new LinkedList<>();
    while (among(TokenType.LBracket)) {
        consume(TokenType.LBracket);
        lengths.add(atExpressionStart() ? parseConstExp() : null);
        consume(TokenType.RBracket);
    }

    InitVal initVal = null;
    if (among(TokenType.Assign)) {
        consume(TokenType.Assign);
        if (among(TokenType.GetInt)) {
            if (!lengths.isEmpty()) {
                throw new IllegalStateException("getint() 专用定义只能用于标量");
            }
            Token getInt = consume(TokenType.GetInt);
            consume(TokenType.LParen);
            consume(TokenType.RParen);
            initVal = inputInitializer(getInt);
        } else {
            initVal = parseInitVal();
        }
    }
    return watch(VarDef.ofDimensions(ident, lengths, initVal));
}
```

### 5. `src/frontend/Parser.java`：新增 `inputInitializer()`

放在类体内，例如 `parseVarDef()` 之后。把专用输入形式降为内部“调用 → 表达式初值”，让已有声明类型检查继续工作。该链是合成节点，不能调用 `watch()`：新产生式在 `getint()` 后直接结束，不经过源码中的 `InitVal`、`Exp`、`UnaryExp` 等非终结符。也不要将源码 token 文本篡改为 `get_int`。

```java
private InitVal inputInitializer(Token getInt) {
    // 保留 GETINTTK 和源码位置，由语义分析识别专用输入节点。
    UnaryExp call = UnaryExp.call(getInt, null);
    MulExp mul = new MulExp(call);
    AddExp add = new AddExp(mul);
    return InitVal.exp(new Exp(add));
}
```

### 6. `src/frontend/Parser.java`：替换 `parseMulExp()`

循环判断和消费列表必须同时加入 `BitAnd`。保持现有左折叠，因此四种运算同优先级、从左向右结合。不要新建比乘法更低的一层，不要把它并入 `LAndExp`。

```java
private MulExp parseMulExp() {
    MulExp exp = watch(new MulExp(parseUnaryExp()));
    while (among(TokenType.Mult, TokenType.Div, TokenType.Mod, TokenType.BitAnd)) {
        Token op = consumeAny(TokenType.Mult, TokenType.Div,
                TokenType.Mod, TokenType.BitAnd);
        exp = watch(new MulExp(exp, op, parseUnaryExp()));
    }
    return exp;
}
```

### 7. `src/utils/ConstValue.java`：常量运算分支

在 `calculate()` 的 `switch`、`case Mod` 附近加入以下分支。`&` 是 Java 整数按位与；不能写成逻辑与，也不能先将操作数转成布尔值。`BinaryExp.calculateConst()` 和 `ConstFolder` 将自动复用它。

```java
case BitAnd -> left & right;
```

### 8. `src/frontend/symbol/type/Type.java`：替换标量长度版 `ofArray()`

从最内层向外层嵌套构造数组。`int a[2][3]` 对应 `[2 x [3 x int]]`。原来的实现只允许元素为 int 或 char，会拒绝数组的数组。

```java
public static Type ofArray(Type elementType, int length) {
    if (length < 0) {
        throw new IllegalArgumentException("数组长度不能为负：" + length);
    }
    Type scalar = elementType;
    while (scalar.isArray()) {
        scalar = scalar.getElementType();
    }
    if (!scalar.isBasic()) {
        throw new IllegalArgumentException("数组基本元素必须是 int 或 char");
    }
    if (elementType == Int && length == 0) return IntArray;
    if (elementType == Char && length == 0) return CharArray;
    Kind kind = scalar.isChar() ? Kind.CharArray : Kind.IntArray;
    return new Type(kind, elementType, length);
}
```

### 9. 同文件：替换列表版 `ofArray()`

这个旧重载只读取第一维，也应同步。正式语义流程使用后文的 `declaredArrayType()`，先分析、再安全折叠维度；不要直接在未绑定符号的 AST 上调用这个便捷重载。

```java
public static Type ofArray(Type elementType, List<ConstExp> lengths) {
    if (lengths == null || lengths.isEmpty()) {
        return ofArray(elementType, 0);
    }
    Type result = elementType;
    for (int i = lengths.size() - 1; i >= 0; i--) {
        ConstExp length = lengths.get(i);
        result = ofArray(result, length == null ? 0 : length.calculate());
    }
    return result;
}
```

### 10. 同文件：替换 `equals()`

保留方法前已有的 `@Override`。多维类型比较不能只比较最外层长度和 IntArray/CharArray 类别。

```java
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Type type) || kind != type.kind) return false;
    return !isArray() || (length == type.length
            && elementType.equals(type.elementType));
}
```

### 11. 同文件：替换 `hashCode()`

保留已有 `@Override`，哈希计算与新的结构比较保持一致。

```java
public int hashCode() {
    return isArray() ? (kind.hashCode() * 31 + length) * 31
            + elementType.hashCode() : kind.hashCode();
}
```

### 12. 同文件：替换 `isPassableTo()`

嵌套数组的元素类型需要结构比较。现有 `sizeof()`、`toString()` 已经递归调用元素方法，可直接复用。符号表仍沿用课程的 IntArray/CharArray 展示名，具体形状由 `Type.toString()` 表达。

```java
public boolean isPassableTo(Type parameter) {
    if (this == parameter) return true;
    if (!isArray() || !parameter.isArray()) return false;
    // 延续现有函数数组形参忽略最外层长度的约定；内部结构必须一致。
    return elementType.equals(parameter.elementType);
}
```

### 13. `src/frontend/Traverser.java`：替换 `analyzeVarDecl()`

本文件已导入 `List`、`ArrayList`、`Token`、`Type`、`ConstExp` 等所需类型。改用全部维度，并使 global/static 初值进入常量语义上下文。后者用于阻止运行时输入流入编译期初值，普通局部初值仍使用 false。

```java
private void analyzeVarDecl(VarDecl decl) {
    Type base = decl.getBType().is(TokenType.Char) ? Type.Char : Type.Int;
    for (VarDef def : decl.getDefs()) {
        Type declared = declaredArrayType(def.getLengths(), base, def.getIdent());
        SymbolVariable symbol = symbols.addVariable(def.getIdent().value(), declared);
        if (symbol == null) {
            errors.add(ErrorType.DuplicatedDeclaration, def.getIdent().location());
            continue;
        }
        if (decl.isStatic()) {
            symbol.setStatic();
        }
        boolean constContext = symbol.isGlobal() || symbol.isStatic();
        analyzeInitVal(def.getInitVal(), declared, constContext);
        if (constContext) {
            recordVariableValue(symbol, declared, def.getInitVal());
        }
    }
}
```

### 14. 同文件：新增 `declaredArrayType()`

放在类体内。按源码顺序检查全部维度，再逆序构造类型。保留已有 `declaredType(ConstExp, Type, Token)` 给一维 `ConstDef` 使用。长度不能求值或非正时沿用当前实现的未知长度 0；总元素数溢出时本文约定报 e，避免扁平存储容量溢出。

```java
private Type declaredArrayType(List<ConstExp> lengths, Type base, Token ident) {
    List<Integer> sizes = new ArrayList<>();
    for (ConstExp length : lengths) {
        if (length == null) {
            sizes.add(0);
            continue;
        }
        Type actual = analyzeConstExp(length);
        if (actual != null && !actual.isInt()) {
            errors.add(ErrorType.MismatchedParameterType, lineOf(length));
        }
        Integer size = ConstFolder.fold(length);
        sizes.add(size == null || size <= 0 ? 0 : size);
    }
    Type declared = base;
    for (int i = sizes.size() - 1; i >= 0; i--) {
        declared = Type.ofArray(declared, sizes.get(i));
    }
    try {
        scalarCount(declared);
    } catch (ArithmeticException e) {
        errors.add(ErrorType.MismatchedParameterType, ident.location());
        return Type.ofArray(base, 0);
    }
    return declared;
}
```

### 15. 同文件：新增 `scalarCount()`

计算数组包含的基本元素总数；标量为 1，未知长度为 0。用它核对平铺初值的个数并分配已有 `FixedArray`。

```java
private int scalarCount(Type type) {
    int count = 1;
    while (type.isArray()) {
        if (type.getLength() == 0) return 0;
        count = Math.multiplyExact(count, type.getLength());
        type = type.getElementType();
    }
    return count;
}
```

### 16. 同文件：替换 `analyzeInitVal()`

InitVal 文法未改变，仍是单表达式、平铺表达式列表或字符串。本文约定多维数组的平铺列表按行优先排列，数量与所有维度乘积比较；不增加 `{{...},{...}}` 递归初始化语法。标量继续使用原来的类型检查，因而 `char c = getint()` 会报 e。

```java
private void analyzeInitVal(InitVal init, Type declared, boolean constContext) {
    if (init == null) return;
    if (!declared.isArray()) {
        if (init.getKind() == InitVal.Kind.Exp) {
            Type actual = analyzeAddExp(init.getExp().getAddExp(), constContext);
            checkInitializerType(actual, declared, init.getExp());
        }
        return;
    }
    if (init.getKind() == InitVal.Kind.Str) {
        // 现有规则只允许一维 char 数组接受字符串，不隐式填充多维数组。
        checkStringInitializer(init.getString(), declared);
        return;
    }
    if (init.getKind() == InitVal.Kind.Exp) {
        analyzeAddExp(init.getExp().getAddExp(), constContext);
        errors.add(ErrorType.MismatchedParameterType, lineOf(init.getExp()));
        return;
    }
    Type scalar = declared;
    while (scalar.isArray()) scalar = scalar.getElementType();
    List<Exp> elements = init.getElements();
    if (elements.size() > scalarCount(declared)) {
        errors.add(ErrorType.MismatchedParameterType, initTokenLine(init));
    }
    for (Exp element : elements) {
        Type actual = analyzeAddExp(element.getAddExp(), constContext);
        checkInitializerType(actual, scalar, element);
    }
}
```

### 17. 同文件：替换 `recordVariableValue()`

global/static 数组使用基本元素总数建立一维底层元素表。`FixedArray.from()` 原本就按列表顺序填充，未填部分为 0，无须改它。普通局部变量仍不记录编译期初值。

```java
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
    int count = scalarCount(declared);
    if (count <= 0) return;
    if (init == null) {
        symbol.setConstantValue(new frontend.symbol.FixedArray(count));
        return;
    }
    symbol.setConstantValue(frontend.symbol.FixedArray.from(count, init));
}
```

### 18. 同文件：在 `analyzeCall()` 开头增加专用输入分支

紧接 `Token ident = exp.getIdent();` 后、原有实参及符号查找之前加入。getint 是 Parser 合成的零实参调用，返回 Int；不去符号表查找名为 getint 的普通函数，也不增加库函数别名。全局/static 输入约定在常量上下文按现有函数调用规则报 c；题图没有指定这些错误码，这只是本项目实现沿用，不是新的课程规范。

```java
if (ident.is(TokenType.GetInt)) {
    if (constContext) {
        // 沿用现有函数调用在常量上下文中的诊断方式。
        errors.add(ErrorType.UndefinedReference, ident.location());
        return null;
    }
    return Type.Int;
}
```

### 19. 配套第三条文法：完整替换 `src/frontend/ast/expression/LVal.java`

**本节及接下来两个小节属于额外配套扩展，并不是题图明示的两条产生式。** 题图只改变 VarDef；要在表达式和赋值中写 `a[i][j]`，还必须把 LVal 从可选一个下标改成下标列表。若严格只改两条且不需要多下标访问，可不应用第 19～21 小节，此时仍能声明多维变量，但不能写多重下标。本文临时副本校验会同时应用这三节。

```java
package frontend.ast.expression;

import frontend.ast.ASTNode;
import frontend.symbol.SymbolVariable;
import frontend.symbol.type.Type;
import frontend.token.Token;
import java.util.List;

// 配套扩展：LVal -> Ident { '[' Exp ']' }
public class LVal extends ASTNode {
    private final Token ident;
    private final List<Exp> indices;
    private SymbolVariable symbol;

    public LVal(Token ident, Exp index) {
        this(ident, index == null ? List.of() : List.of(index), true);
    }

    private LVal(Token ident, List<Exp> indices, boolean internal) {
        this.ident = ident;
        this.indices = List.copyOf(indices);
    }

    public static LVal ofIndices(Token ident, List<Exp> indices) {
        return new LVal(ident, indices, true);
    }

    public Token getIdent() { return ident; }
    public List<Exp> getIndices() { return indices; }
    public Exp getIndex() { return indices.isEmpty() ? null : indices.get(0); }
    public boolean isArrayElement() { return !indices.isEmpty(); }
    public SymbolVariable getSymbol() { return symbol; }
    public void setSymbol(SymbolVariable symbol) { this.symbol = symbol; }

    @Override
    public int calculateConst() {
        if (symbol == null) {
            throw new UnsupportedOperationException("符号尚未解析：" + ident.value());
        }
        Type type = (Type) symbol.getType();
        int offset = 0;
        for (Exp index : indices) {
            if (!type.isArray()) {
                throw new UnsupportedOperationException("下标数量超过数组维数");
            }
            int value = index.calculateConst();
            if (value < 0 || value >= type.getLength()) {
                throw new IndexOutOfBoundsException("常量数组下标越界");
            }
            offset = Math.addExact(Math.multiplyExact(offset, type.getLength()), value);
            type = type.getElementType();
        }
        if (type.isArray()) {
            throw new UnsupportedOperationException("整个数组或子数组没有标量值");
        }
        if (symbol.getConstantArray() != null) {
            return symbol.getConstantArray().get(offset);
        }
        return symbol.getConstantValue().getValue();
    }

    @Override
    protected String getName() { return "<LVal>"; }
}
```

### 20. 配套修改：替换 `Parser.parseLVal()`

这是上节额外 LVal 产生式对应的解析入口。保留旧的 `<LVal>` 输出机制，所有实际下标 token 均正常输出。

```java
private LVal parseLVal() {
    Token ident = consume(TokenType.Ident);
    List<Exp> indices = new LinkedList<>();
    while (among(TokenType.LBracket)) {
        consume(TokenType.LBracket);
        indices.add(parseExp());
        consume(TokenType.RBracket);
    }
    return watch(LVal.ofIndices(ident, indices));
}
```

### 21. 配套修改：替换 `Traverser.analyzeLVal()`

每消费一个下标，就检查它是 Int，并剥去一层数组类型。`a[i]` 可以得到子数组，`a[i][j]` 得到标量，过多下标报 e。原有 `analyzeAssign()` 已拒绝对整个数组/子数组赋值。函数形参文法保持一维，所以整个二维数组不能传给 `int x[]`，其一行 `a[i]` 可以。

```java
private Type analyzeLVal(LVal lval, boolean constContext) {
    if (lval == null) return null;
    Type cached = types.get(lval);
    if (cached != null) return cached;
    Token ident = lval.getIdent();
    SymbolVariable symbol = symbols.getVariable(ident.value());
    if (symbol == null) {
        errors.add(symbols.getFunction(ident.value()) == null
                ? ErrorType.UndefinedReference : ErrorType.MismatchedParameterType,
                ident.location());
        return null;
    }
    if (constContext && !symbol.isConst()) {
        errors.add(ErrorType.UndefinedReference, ident.location());
        return null;
    }
    lval.setSymbol(symbol);
    Type type = (Type) symbol.getType();
    for (Exp index : lval.getIndices()) {
        Type indexType = analyzeAddExp(index.getAddExp(), constContext);
        if (indexType != null && !indexType.isInt()) {
            errors.add(ErrorType.MismatchedParameterType, lineOf(index));
        }
        if (!type.isArray()) {
            errors.add(ErrorType.MismatchedParameterType, ident.location());
            return null;
        }
        type = type.getElementType();
    }
    put(lval, type);
    return type;
}
```

### 22. `ui/compilerui/pipeline/SyntaxReference.java`：同步说明

建议在 `KEYWORD_NOTES` 的 `Map.ofEntries(...)` 中加入以下两项。词法扫描和 UI 高亮会自动读取 Keywords.TABLE，不需要再手写一份匹配规则。bitand 在语义上是运算符，但因其由保留字表识别，当前 UI 会按保留字着色；这里只补文字说明。

```java
Map.entry("getint", "变量定义中的整数输入：int i = getint();"),
Map.entry("bitand", "按位与，与乘除模同优先级，左结合"),
```

## 四、关键实现说明

### getint 的语法输出和内部 AST

`int i = getint();` 的新分支消费 `INTTK IDENFR ASSIGN GETINTTK LPARENT RPARENT`，并输出 `<VarDef>`，外层消费分号后输出 `<VarDecl>`。专用分支没有源码中的 InitVal，因此不输出合成的 `<InitVal>` 或表达式链。

内部却可保存 `InitVal.exp(Exp(AddExp(MulExp(UnaryExp.call(GETINTTK)))))`，供类型检查复用。不能调用 `parseInitVal()` 来消费 GETINTTK，也不能把 GetInt 加到 `atExpressionStart()` 或普通调用分支中，否则会超出题图新增的文法。

`getint` 既然变成关键字，就不能再作为变量名或用户函数名；`getintValue`、`bitandValue` 仍是普通标识符。相邻字母数字组成完整单词，所以 `abitandb` 是单个标识符，按位与应写作 `a bitand b`。

### bitand 的类型与常量求值

`parseMulExp()` 已将 BitAnd 存入 BinaryExp.op。现有 `analyzeMulExp()` → `analyzeBinary(..., false, constContext)` → `checkSameType()` 正好符合本文约定，无须另外增加语义分支。`put()` 会继续设置 charResult；`BinaryExp.calculateConst()` 调用新加的 `ConstValue.calculate()` 分支。

因此 `const int n = 6 bitand 3;` 和 `int a[6 bitand 3];` 都需要常量运算分支，单改 Parser 会让这些场景无法正确求值。也不能把 BitAnd 写进逻辑运算的 resultIsInt 分支：它不是短路运算，两个操作数都必须求值。

### 多维存储与下标

类型必须保留每一维；直接把 `[2][3]` 变成 `[6]` 会丢失下标和参数匹配信息。本文仅在底层 `FixedArray` 中平铺保存元素。

对 `a[D0][D1]...[Dn]` 的完整下标，行优先元素偏移为 `i0*(D1*...*Dn) + i1*(D2*...*Dn) + ... + in`；字节偏移再乘基本类型大小。部分下标表示子数组，在语义阶段保留剩余的数组类型。

第 19～21 小节是额外第三条文法 `LVal → Ident { '[' Exp ']' }`。严格只添加题图两条时不能声称已经支持 `a[i][j]`；如同时实现这些配套代码，则可检查多维读写，而 ConstDef、函数形参和递归初始化文法仍不扩展。

### 错误处理

继续复用缺分号 i、缺右小括号 j、缺右中括号 k、重名 b、未定义 c、类型不匹配 e。专用分支 `getint()` 不接受实参、不接受数组目标；这些不属于文法的输入可能触发解析异常，不假装已有完整通用语法恢复。缺右小括号仍经现有 `consume(RParen)` 报 j 并恢复。

非正或不能折叠的维度保留原有未知长度行为，本方案没有新增完整的“非法数组长度”诊断规范。代码中的总元素数溢出 e 是本文额外约定。

## 五、实施后的验收

各程序放入合法函数中独立测试，除特别说明外末尾补 `return 0;`。

| 场景 | 输入片段 | 预期 |
| --- | --- | --- |
| 标量输入 | `int i = getint();` | 成功，变量为 Int；初值含一次专用输入调用 |
| 逗号定义 | `int i = getint(), j = 6 bitand 3;` | 成功，分号只由外层 VarDecl 消费 |
| 字符输入 | `char c = getint();` | e，依本文严格匹配约定 |
| 全局或 static 输入 | `int i = getint();`（全局）或 `static int i = getint();` | c，依本文常量上下文约定 |
| 专用输入位置 | `int i = getint() + 1;`、`i = getint();`、`getint(1)`、数组目标输入 | 不属于新产生式，不作为解析成功验收 |
| 缺右括号 | `int i = getint(;` | j，原有恢复机制 |
| 普通运行库回归 | `int i = get_int();` | 原有合法调用保持有效 |
| 按位运算 | `int i = 6 bitand 3;` | 成功，常量折叠为 2 |
| 左结合 | `int i = 6 bitand 3 * 2;` | 4 |
| 同级混合 | `int i = 6 * 3 bitand 5;` | 0 |
| 加减优先级 | `int i = 1 + 6 bitand 3;` | `1 + (6 & 3)` 为 3 |
| 常量初值 | `const int n = 6 bitand 3;` | n 的编译期值为 2 |
| 数组长度 | `int a[6 bitand 3];` | 数组长度为 2 |
| char 按位与 | `char c = 'A' bitand (char)15;` | 成功，结果为 Char、值为 1，依本文约定 |
| 混合类型 | `int i = 1 bitand 'A';` | e |
| 逻辑与回归 | `if (6 && 3) ;` | 仍使用原有 AND，与 BitAnd 不同 |
| 完整单词 | `int bitandValue = 1;` | 合法 Ident，不切分为 BitAnd |
| 多维声明 | `int a[2][3];` | 类型 `[2 x [3 x int]]` |
| 多维列表 | `int a[2][3] = {1,2,3,4,5,6};` | 成功；按本文行优先约定 |
| 全局多维存储 | 全局 `int a[2][3] = {1,2,3,4,5,6};` | 元素表长度 6，最后元素为 6 |
| 元素过多 | `int a[2][3] = {1,2,3,4,5,6,7};` | e |
| 多下标读写（配套） | `int a[2][3]; a[1][2] = 7;` | 第 19～21 节应用后成功，元素类型 Int |
| 过多下标（配套） | `int a[2][3]; a[0][0][0] = 1;` | e |
| 函数参数（配套） | 已有 `int sum(int a[])`，传二维变量的一行 `sum(a[1])` | 成功；传整个 a 则 e |

用 JDK 17+ 执行 `./run.sh --build-only` 编译核心和 UI，再检查真实 AST、符号类型、诊断及常量值。**当前工程尚无 IR 或目标代码生成器，不能把前端通过说成运行输入或运行输出已验证。**

未来后端必须对专用 GetInt 调用产生一次整数读取，并写入已经定义的变量；可映射到现有运行库 `get_int`。bitand 要产生按位与指令，不能生成逻辑短路分支。多维寻址应使用上述行优先偏移。

## 六、本次校验记录

已在临时源码副本中应用本文代码（包括第 19～21 小节多下标配套扩展），使用 JDK 21 编译全部 `src/` 与 `ui/` Java 文件，并通过 70 项断言检查，覆盖：

- 专用 getint 定义、逗号声明、原有 get_int 调用、输入节点不可常量折叠，以及语法输出没有合成表达式节点。
- char/global/static 输入限制、缺分号和右括号恢复、非法输入位置拒绝。
- bitand 常量值、同级左结合、与加减及括号的优先级、负数、char 类型以及混合类型诊断。
- 多维类型保存、结构比较、平铺初值容量、全局数组补零、二维和三维行优先偏移。
- 多下标读写、过多下标、子数组赋值拒绝、一维形参接收行数组，以及原有常量数组求值回归。

校验前后对仓库已有跟踪文件进行了内容哈希比较，内容一致。本次交付只有此 Markdown 文档，所有方案代码均未写入项目源码；输入执行及目标程序输出尚未进行后端运行验证。
