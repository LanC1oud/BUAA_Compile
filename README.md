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

## 编译器架构

### 阶段划分

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

### 数据流

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

### 目录结构

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

## 构建与运行

### 环境要求

- **JDK 17 或更高**

### 命令行方式

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

`Compiler` 保持原始入口，直接运行其 `main` 即可。阶段只通过 `src/utils/HomeworkConfig.java` 中的 `hw` 字段手动切换；输入、输出路径在 `src/utils/Configure.java` 中配置，不使用命令行参数。

当前配置为 `Hw.Llvm`，读取 `testfile.txt` 并生成 `llvm_ir.txt`。如需语义分析，将 `hw` 改为 `Hw.Semantic` 后重新编译运行，输出自动切换为 `symbol.txt`。编译过程不读取程序运行时的标准输入。

```powershell
python tools/setup-llvm12.py
python tests/run-llvm-tests.py
pwsh -NoProfile -File tests/run-semantic-tests.ps1
pwsh -NoProfile -File tools/pack.ps1
```

`tests/run-llvm-tests.py` 会先用 JDK 17 兼容选项编译项目，再使用 LLVM 12.0.0 验证并执行全部用例。工具链只安装到 `build/llvm12/`；课程运行时的兼容副本及重新生成方法见 [运行时说明](runtime/README.md)。提交包在 `out/Compiler-jdk17.zip`，只依赖 JDK 17 标准库。

逐条任务的实现、函数和源码行号见 [LLVM IR 阶段任务完成对应表](docs/LLVM%20IR阶段任务完成对应表.md)。修改前快照及回滚脚本在 `backups/before-llvm-20261101-234349/`，包含当时未提交的代码与文档。

### 图形前端方式

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

---

## 文法驱动的测试生成

独立命令行工具 `testgen/` 可根据已有文法和新增 EBNF 规则，批量生成可复现的 `testfile.txt` 及独立的词法、语法、符号表、错误预期。

```powershell
python -m testgen --count 20 --seed 42 --out build/generated
python -m testgen --rule "UnaryExp -> '++' LVal | LVal '++'" --count 30 --out build/generated-inc
python -m testgen --suite boundary --out build/generated-boundary
python -m testgen --suite mixed --rules testgen/examples/inc.ebnf --count 30 --scale 2 --out build/generated-inc-extremes
```
