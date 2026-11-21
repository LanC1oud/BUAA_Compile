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

### 在界面运行 LLVM IR

打开「LLVM IR 运行」，粘贴包含 `main` 的 IR，在「标准输入」填写程序要读取的文本，点击「运行 IR」。如需自动核对，勾选「对比预期输出」并填入期望文本。比对时兼容 Windows 和 Unix 换行，空格和末尾换行均计入。

运行需要 PATH 中可用的 `clang` 和本机 C 编译、链接环境，也可以通过环境变量 `CLANG` 指定 clang 的绝对路径。界面会链接项目的 `runtime/libsysy.c`。粘贴的 IR 会作为本机程序执行，请使用可信代码。程序的标准输出、诊断和退出码分别展示，运行限时 5 秒。

---

### SysY IDE（实验）

第三个标签页提供带行号和语法高亮的 SysY 编辑器。点击「运行」后，在下方终端输入一行并按 Enter，程序的输出会随运行显示。「停止」可以结束正在编译、计算或等待输入的程序。初始示例输入 `41` 后会显示 `n + 1 = 42`。此页使用与 LLVM IR 运行页相同的 Clang 环境。

---

## 文法驱动的测试生成

独立命令行工具 `testgen/` 可根据已有文法和新增 EBNF 规则，批量生成可复现的 `testfile.txt` 及独立的词法、语法、符号表、错误预期。

```powershell
python -m testgen --count 20 --seed 42 --out build/generated
python -m testgen --rule "UnaryExp -> '++' LVal | LVal '++'" --count 30 --out build/generated-inc
python -m testgen --suite boundary --out build/generated-boundary
python -m testgen --suite mixed --rules testgen/examples/inc.ebnf --count 30 --scale 2 --out build/generated-inc-extremes
```
