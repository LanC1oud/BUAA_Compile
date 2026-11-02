# BUAA Compile — SysY 编译器

北京航空航天大学《编译原理》课程设计。用 Java 实现一个 SysY 语言编译器，
目前阶段：
- 词法分析 ✅
- 语法分析 ✅
- 语义分析 ✅
- LLVM IR 生成 ✅
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
| 4 中间代码生成 | `llvm/`、`frontend/SemanticInfo.java` | `llvm_ir.txt` | ✅ 完成（LLVM 12） |
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
run.sh / run.bat               一键入口：需要时先编译，再启动界面（bash / Windows 原生）
testfile*.sysy                  测试用例
```

---

## 二、目前所处开发阶段

**第 4 阶段（LLVM IR 生成）已完成。** 支持 2026 SysY 的 int/char、函数、数组、显式转换、短路、while、switch、static 及六个运行时接口；命令行和 UI 默认生成 LLVM IR。

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
#      public static Hw hw = Hw.Llvm;   // Lexer / Syntax / Semantic / Llvm

# 2) 编译
javac -encoding UTF-8 -d out/production/BUAA_Compile $(find src -name '*.java')

# 3) 运行
java -cp out/production/BUAA_Compile Compiler
```

产物落在项目根，按阶段不同：`lexer.txt` / `parser.txt` / `symbol.txt` / `llvm_ir.txt`；
有错时写 `error.txt`。

IDEA 里直接运行 `src/Compiler.java` 的 `main` 即可。

### LLVM IR 阶段

默认读取 `testfile.txt` 并生成 `llvm_ir.txt`，编译过程不读取程序运行时的标准输入。可通过参数保留前面阶段或指定文件：

```powershell
java -cp build/llvm-tests/classes Compiler --stage Llvm --source tests/official-llvm.sysy --output build/program.ll
java -cp build/llvm-tests/classes Compiler --stage Semantic
python tools/setup-llvm12.py
python tests/run-llvm-tests.py
pwsh -NoProfile -File tests/run-semantic-tests.ps1
pwsh -NoProfile -File tools/pack.ps1
```

`tests/run-llvm-tests.py` 会先用 JDK 17 兼容选项编译项目，再使用 LLVM 12.0.0 验证并执行全部用例。工具链只安装到 `build/llvm12/`；课程运行时的兼容副本及重新生成方法见 [运行时说明](runtime/README.md)。提交包在 `out/Compiler-jdk17.zip`，只依赖 JDK 17 标准库。

逐条任务的实现、函数和源码行号见 [LLVM IR 阶段任务完成对应表](docs/LLVM%20IR阶段任务完成对应表.md)。修改前快照及回滚脚本在 `backups/before-llvm-20261101-234349/`，包含当时未提交的代码与文档。

### 3.3 图形前端方式

前端外壳独立在 `ui/`。每平台一个入口脚本，**需要时先编译，再启动界面**：

| 平台 | 命令 |
| --- | --- |
| Windows | `run.bat`（双击亦可） |
| macOS / Linux | `./run.sh` |

```bat
run.bat                :: 需要时先编译，然后开界面
run.bat --build-only   :: 只编译，不开界面
run.bat --force        :: 强制重编（源码没改也编）
run.bat --help
```

```bash
./run.sh --build-only  # 只编译
./run.sh --force       # 强制重编
./run.sh --help
```

界面页签：

- **输出 → 编译结果 / 近似 AST**：该阶段产物；近似 AST 是按先序文本分组的折叠视图。
- **错误**：可读错误表，格式 `line行号：错误码——错误类型英文名`
  （如 `line5：i——ExpectedSemicolon`），**点一行即跳到源码对应行**。
- **阶段对比**：各阶段耗时、产物行数、token/节点/符号数，以及错误数演变。
- **文法速查**：关键字 / 运算符 / 转义字符，数据直接读 `src/` 的三张表
- **日志**：stdout/stderr 捕获。

---

## 四、文法驱动的测试生成

独立命令行工具 `testgen/` 可根据已有文法和新增 EBNF 规则，批量生成可复现的 `testfile.txt` 及独立的词法、语法、符号表、错误预期。

```powershell
python -m testgen --count 20 --seed 42 --out build/generated
python -m testgen --rule "UnaryExp -> '++' LVal | LVal '++'" --count 30 --out build/generated-inc
python -m testgen --suite boundary --out build/generated-boundary
python -m testgen --suite mixed --rules testgen/examples/inc.ebnf --count 30 --scale 2 --out build/generated-inc-extremes
```

仅生成需要 Python 3.10+，无需第三方包；增加 `--check` 可与当前 Java 编译器逐个比较，需要 JDK 17+。完整程序采用受控语义模型；任意新文法可先用 `--mode syntax` 生成片段，复杂新语义需要补充契约。详见 [文法测试生成器](docs/文法测试生成器.md)。

边界套件覆盖 `a`～`n`、类型/作用域组合、错误恢复及四档压力规模；`++` 有独立边界扩展。`--official-zip` 可重复指定官方包，保留原答案并生成重命名/空行变体，提供来源和实际覆盖统计。范围与验证结果见 [极端测试覆盖与官方样例分析](docs/极端测试覆盖与官方样例分析.md)。

新增运算符、表达式、语句、声明或类型时，可按 [文法增添通用指南](docs/文法增添通用指南.md) 梳理规则契约、修改文件、分层实现与验证，并复用其中的具体扩展文档模板。具体方案统一收录于 [文法扩展示例集](docs/文法扩展示例/README.md)，目前包含 `++`、`~`、`**`、`+=` 和 `do…while`。
