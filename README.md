# BUAA Compile — SysY 编译器

北京航空航天大学《编译原理》课程设计。用 Java 实现一个 SysY 语言编译器，
目前阶段：
- 词法分析 ✅
- 语法分析 ✅
- 语义分析 ✅
- 中间代码生成
- 代码优化
- 目标代码生成

---

## 一、编译器架构

### 1.1 阶段划分

编译器按"每个阶段可独立验收"的方式组织，阶段开关在 `src/utils/HomeworkConfig.java`，
驱动入口是 `src/utils/Controller.java`。

| 阶段 | 主要实现 | 产物 | 状态 |
| --- | --- | --- | --- |
| 1 词法分析 | `frontend/Lexer.java`、`frontend/token/` | `lexer.txt`（token 序列） | ✅ 完成 |
| 2 语法分析 | `frontend/Parser.java`、`frontend/ast/` | `parser.txt`（token + 语法树先序） | ✅ 完成 |
| 3 语义分析 | `frontend/Traverser.java`、`frontend/symbol/` | `symbol.txt`（符号表） | ✅ 完成 |
| 4 中间代码生成 | 待实现 | `llvm_ir.txt` | ⬜ 未开始 |
| 5 代码优化 | 待实现 | — | ⬜ 未开始 |
| 6 目标代码生成 | 待实现 | `mips.txt` | ⬜ 未开始 |

出错时错误信息写入 `error.txt`（`src/utils/Configure.java` 里配置），
格式为 `行号 错误码`，如 `5 i`。

### 1.2 数据流

```
源文件 testfile.txt
      │
      ▼
  ┌─────────┐   TokenStream   ┌──────────┐   CompileUnit   ┌────────────┐
  │  Lexer  │ ──────────────▶ │  Parser  │ ──────────────▶ │ Traverser  │
  │ 词法分析 │                 │ 语法分析  │   (AST 根)      │  语义分析   │
  └─────────┘                 └──────────┘                 └────────────┘
       │                           │                             │
       ▼                           ▼                             ▼
  lexer.txt                   parser.txt                    symbol.txt
       └───────────────────────────┴─────────────────────────────┘
                          ErrorTable → error.txt
```

三个阶段共用两个全局引擎（`utils/Controller` 持有静态实例）：

- **`error.ErrorTable`** —— 错误收集表，实现 `BackTrace.Traceable`
- **`frontend.symbol.SymbolTable`** —— 符号表，作用域栈 + 输出列表

### 1.3 关键设计

**回溯（`utils/BackTrace`）** —— 语法分析需要在不改变文法（不消除左递归、不提取左公因子）的前提下做
预测分析，因此实现了 `Traceable` 快照机制。`Parser` 用一个 `BackTrace` 同时握住
`ErrorTable`、`TokenStream`、`ParserWatcher` 三者，投机解析后一并回滚
（典型用例：`isAssignStmt()` 区分 `LVal '=' Exp` 与 `[Exp] ';'`）。

**错误恢复** —— 缺 `;` / `)` / `]` 属于可恢复错误：记录对应错误码后把当前 token
留在流里继续解析，因此一次编译可以报出多个错误，而不是遇到第一个错误就退出。
错误码覆盖 `a` ~ `n` 共 14 种（见 `error/ErrorType.java`）。

**AST 分包** —— `frontend/ast/` 按文法结构分为
`declaration` / `function` / `statement` / `expression` / `block`，
共 43 个节点类（另有 `ASTNode` 基类），每个节点负责把自己的名字 `watch()` 进输出流，
因此 `parser.txt` 是解析过程的直接记录，不需要额外的序列化代码。

**常量折叠** —— `utils/ConstFolder`、`utils/ConstValue` 提供编译期求值，
`ASTNode.calculateConst()` 是统一入口；数组长度、`const` 初值、`case` 标签、
下标上限都依赖它。

### 1.4 目录结构

```
src/
├── Compiler.java              入口（main）
├── config.json                课程要求的配置
├── utils/                     Controller 阶段调度、Configure 全局配置、
│                              HomeworkConfig 阶段开关、BackTrace 回溯快照、
│                              ConstValue/ConstFolder 常量折叠、Pair 二元组
├── error/                     ErrorType 错误码、ErrorEntry 单条错误、
│                              ErrorTable 收集与排序输出
├── frontend/
│   ├── Lexer.java             词法分析
│   ├── Parser.java            递归下降 + 回溯语法分析
│   ├── ParserWatcher.java     语法树输出流（受 displayTokensWithAst 控制）
│   ├── Traverser.java         语义分析（类型检查、符号表填充、常量求值）
│   ├── Navigation.java        源码位置（起止行列）
│   ├── token/                 词法单元：TokenType 45 种 + EOF、Token、TokenStream、
│   │                          Keywords / Symbols / Escapes 三张识别表
│   ├── ast/                   抽象语法树，按文法分包
│   └── symbol/                符号表与符号种类（变量/函数/数组）、类型系统
└── ...

ui/                             图形前端外壳（不参与评测）
├── compilerui/model/           纯数据
├── compilerui/pipeline/        Pipeline 接口层 + 文法速查 + 语法高亮
└── compilerui/gui/             Swing 界面

docs/编译器设计报告.md            设计报告
build.sh / run-ui.sh            构建与启动脚本
testfile*.sysy                  测试用例
```

---

## 二、目前所处开发阶段

**第 3 阶段（语义分析）刚完成，正在进入第 4 阶段（中间代码生成）之前。**

已完成：

- 词法分析：45 种 token（另有 EOF），注释、字符/字符串转义、非法 token（错误 `a`）全部处理
- 语法分析：覆盖 2026 版全部文法，AST 共 43 个节点类，带错误恢复与回溯
- 语义分析：符号表（作用域编号、7 个运行时库函数预注册、`main` 为保留字不进符号表）、
  类型检查、`b` ~ `n` 全部 13 种语义错误、常量折叠
- 图形前端外壳：传代码 / 选阶段 / 选导出目录，含行号、语法高亮、错误单击跳转、
  文法速查、近似 AST 视图、阶段耗时与规模统计

---

## 三、构建与运行

### 3.1 环境要求

- **JDK 17 或更高**

### 3.2 命令行方式

在项目根目录：

```bash
# 1) 准备输入：把待编译源码放到项目根的 testfile.txt
#    阶段开关改 src/utils/HomeworkConfig.java 里的 hw 字段
#      public static Hw hw = Hw.Semantic;   // Lexer / Syntax / Semantic

# 2) 编译
javac -encoding UTF-8 -d out/production/BUAA_Compile $(find src -name '*.java')

# 3) 运行
java -cp out/production/BUAA_Compile Compiler
```

产物落在项目根，按阶段不同：`lexer.txt` / `parser.txt` / `symbol.txt`；
有错时写 `error.txt`。

IDEA 里直接运行 `src/Compiler.java` 的 `main` 即可。

### 3.3 图形前端方式

前端外壳独立在 `ui/`，两条路可以并用。

```bash
./build.sh      # 编译 src/ + ui/ → build/classes
./run-ui.sh     # 打开窗口
```

界面页签：

- **输出 → 编译结果 / 近似 AST**：该阶段产物；近似 AST 是按先序文本分组的折叠视图。
- **错误**：可读错误表，格式 `line行号：错误码——错误类型英文名`
  （如 `line5：i——ExpectedSemicolon`），**点一行即跳到源码对应行**。
- **阶段对比**：各阶段耗时、产物行数、token/节点/符号数，以及错误数演变。
- **文法速查**：关键字 / 运算符 / 转义字符，数据直接读 `src/` 的三张表
- **日志**：stdout/stderr 捕获。