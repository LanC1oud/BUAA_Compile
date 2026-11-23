# LLVM IR 阶段 TODO

依据 2026 年课程要求、两个代码生成专题报告，以及当前仓库和本地 `BUAA_SysY_Compiler_2024` 参考实现整理。核对日期：2026-10-30。

目标：在现有词法、语法、语义分析之后生成符合 **LLVM 12.0.0** 的 `llvm_ir.txt`，覆盖本项目的 2026 版 SysY 文法，并让同一份结构化 IR 后续可以直接交给 MIPS 后端。

实施状态（2026-11-02）：P0～P11 已实现并通过 LLVM 12.0.0 验收；MIPS.1～MIPS.5 的 IR 接口约束已落实，汇编生成留待下一阶段。逐项代码位置与验证记录见 [LLVM IR阶段任务完成对应表](LLVM%20IR阶段任务完成对应表.md)。下文保留实施前的分析与参考定位，实际类名以对应表为准。

每个待办下新增“参考定位”，分为三类：**已有对应实现**表示可以沿该代码路径学习，**部分可借鉴**表示只有部分机制或采用了不同设计，**无直接实现**表示需要在本项目新增。前两类也须结合文法差异适配。链接包含已核对的文件行号，可直接打开本机参考代码；`frontend/Traverser` 是语义分析器，`llvm/Traverser` 才是 IR 翻译器。

## 1. 依据与范围

| 标记 | 材料                                                                                                  | 本清单使用的部分                                                                                                 |
| ---- | ----------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| G    | [2026 编译技术实验文法定义及相关说明](C:/Users/96330/Downloads/2026编译技术实验文法定义及相关说明.pdf) | 第 3～4 页文法，第 7～11 页运行时、static、类型和控制流语义，第 12～17 页错误处理                                |
| J    | [希冀平台“2026 代码生成一”要求](C:/Users/96330/Downloads/希冀：计算机专业课程一体化平台.pdf)         | 第 1 页输入输出，第 2 页综合样例，第 3 页库函数声明、LLVM 版本与提交要求，第 4 页配置                            |
| IR   | [LLVM IR 专题报告](C:/Users/96330/Downloads/专题报告-代码生成（LLVM+IR）.pptx)                         | 第 8～17 页基础指令，第 19～33 页 Value、基本块和 SSA，第 34～46 页类型与 GEP，第 47～54 页递归翻译和短路        |
| M    | [MIPS 专题报告](C:/Users/96330/Downloads/专题报告-代码生成（MIPS）.pptx)                               | 第 9～16 页中后端分工、存储、数组、调用，第 20 页参考实现的适用范围                                              |
| R    | [2024 参考代码目录](C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024)                               | `src/top/swkfk/compiler/llvm/`、`helpers/Compatibility.java`、`helpers/LoopStorage.java` 和 `arch/mips/` |

**规范取舍：以 G、J 为准，专题报告用于理解设计，R 用于参考实现方法。** 报告中“文法只有 int”、隐式混合运算等表述和示例带有旧版本背景，不能覆盖 2026 版要求。参考仓库的实际名称是 `BUAA_SysY_Compiler_2024`，对应请求中提到的参考代码。

J 第 3 页列出代码生成一的六类单元测试：常量、变量、读入赋值、算术表达式、函数、数组。可以先按这些内容跑通基本闭环；本清单的完整验收范围还包括 `while`、`switch`、`static` 和 A 级短路求值。J 第 2 页综合样例本身也包含循环和 switch。

## 2. 实施前的项目起点（2026-10-30 快照）

| 当前已有                                                                                                   | IR 阶段需要衔接的内容                                                                                |
| ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `src/frontend/Traverser.java` 完成类型分析，私有 `IdentityHashMap<ASTNode, Type> types` 保存表达式类型 | 对外提供本次分析的类型信息，避免 IR 遍历重新实现类型检查                                             |
| `LVal.getSymbol()` 保存已经解析的变量符号                                                                | `ConstDef`、`VarDef`、`FuncDef`、`FuncFormalParam`、函数调用还缺少可供 IR 使用的统一符号绑定 |
| `SymbolVariable` 已有 `isStatic()`、`isFromParam()`、常量值/数组初值                                 | 补充符号到 IR 存储地址的映射，并区分“只读常量”和“可变对象的初值”                                 |
| `Type` 覆盖 `int/char/void` 和一维数组                                                                 | 增加 IR 层的 `i1`、指针、函数类型，以及前端类型到 IR 类型的映射                                    |
| `ConstFolder`、`FixedArray`、`calculateConst()`，以及逐步处理 char 回绕的标记                        | 复用编译期计算结果，确保运行时代码与常量求值一致                                                     |
| `WhileStmt`、`SwitchStmt`、`CaseStmt`、`UnaryExp.Kind.Cast` 已经存在                               | 为这些 2026 语法新增翻译，参考代码中没有对应的完整路径                                               |
| 命令行和 UI 都完成到语义分析                                                                               | 两条管线都要增加 IR 阶段，成功时输出 IR，有错误时停止在语义阶段                                      |
| `src/config.json` 当前为 `"objectCode": "MIPS"`                                                        | LLVM 阶段提交时改成 `"LLVM"`，后续 MIPS 提交再切换                                                 |

当前项目的代码路径相对仓库根目录；参考实现链接使用本机绝对路径和行号。下文会指出需要修改的现有文件。

## 3. 与 2024 参考代码的差异清单

| 项目           | R 中的实现                                                       | 本项目应该实现                                                             |
| -------------- | ---------------------------------------------------------------- | -------------------------------------------------------------------------- |
| 循环           | `StmtFor`，有 init/cond/update，continue 去 update             | `WhileStmt`，continue 回到条件入口                                       |
| switch         | IR 遍历未提供 switch 分支                                        | 支持 int/char 选择值、case/default、贯穿执行和嵌套跳转                     |
| 静态局部变量   | IR 声明路径没有对应处理                                          | 提升为具有唯一名称的模块级存储，初始化一次，保留局部可见性                 |
| 显式转换       | `visitUnaryExpr()` 没有 Cast 分支                              | 翻译 `(int)`、`(char)`，保留源语言严格的类型匹配要求                   |
| 算术类型       | `Compatibility.unityIntoInteger()` 通常先统一成 i32            | char 算术每一步按 8 位无符号规则执行并回绕，不能只在赋值时截断             |
| 除模和大小比较 | `BinaryOp` 使用 `sdiv/srem`、有符号比较                      | 区分 int 的有符号语义和 char 的无符号语义                                  |
| 输入           | `StmtGetInt/StmtGetChar` 专用语句，`getint/getchar` 返回 i32 | `get_int/get_char/get_string` 走普通函数调用表达式，`get_char` 返回 i8 |
| 输出           | `putint/putch/putstr`，printf 只识别 `%d/%c`                 | `put_int/put_char/put_string`，支持 `%d/%c/%s`，`put_char` 参数是 i8 |
| 短路           | `visitCondAnd/Or()` 生成条件值并用 `IPhi` 合流               | 建议先用真假目标块直接翻译条件，基础版本可不产生 phi                       |
| AST 形状       | Add/Mul 节点保存左项、运算符列表和右项列表                       | 本项目 `BinaryExp` 保存 left/op/right，按当前树形结构递归翻译            |
| 符号和类型     | 声明节点提供符号，符号可保存 Value，类型中含 IR 类型             | 当前项目缺少部分绑定且前端类型独立，需要先完成接口衔接                     |

## 4. 建议实现结构

建议保留前端 `Type` 的含义，在 `src/llvm/` 建立独立的 IR 模型，通过转换函数衔接。这样 `char[]` 的源语言类型、`[N x i8]` 的存储类型和 `i8*` 的传参类型不会混淆。

```text
src/llvm/
  IrGenerator.java       AST 翻译：声明、表达式、语句、函数
  IrBuilder.java         创建对象、维护当前函数和插入块、检查终结指令
  IrModule.java          全局变量、字符串、外部声明、用户函数
  IrPrinter.java         统一输出 LLVM 12 文本
  type/                 整数、void、label、数组、指针、函数类型
  value/                Value、Constant、GlobalVariable、Function、Argument、BasicBlock
  instruction/          内存、算术、比较、转换、调用、跳转、返回
  verify/               模型的结构与类型检查
  pass/                 可选优化的统一入口，初期可为空
```

建议前端返回一个 `SemanticInfo`，保存节点类型和符号绑定；具体放在 `frontend/` 还是其子包由实现决定。建议用 `Map<SymbolVariable, Value>` 保存 IR 地址，以符号对象身份区分同名变量。

`IrGenerator` 负责访问 AST，`IrBuilder` 只负责构建 IR。R 的 `IrBuilder.java` 自己也注释了全局声明遍历和函数遍历职责交错的问题，可以在此处避免继续沿用。

## 5. 按依赖顺序执行的 TODO

### P0：先固定评测和本地运行环境

- [x] **P0.1 固定工具版本。** 准备同一套 LLVM 12 的 `llvm-as`、`opt`、`llvm-link`、`lli`，记录位置和版本；生成 `i8*`、`i32*` 等带类型指针。验收：手写最小 main 能被 LLVM 12 解析并执行。

  **参考定位（部分可借鉴）：** [Compiler.java · LLVM 工具使用注释（L18）][ref-entry-tools]列出 clang、llvm-link、lli 的手工命令；参考仓库没有固定 LLVM 12 版本或安装/自动校验工具链的实现，需要自己补充。
- [x] **P0.2 解决本地运行时版本冲突。** 本次检查发现 `C:/Users/96330/Downloads/libsysy/libsysy.ll` 使用不透明指针 `ptr`，文件末尾标记为 `clang version 22.1.8`。先取得兼容 LLVM 12 的运行时，或在匹配的执行环境中用 Clang 12 从 `libsysy.c` 重新生成测试副本；验证其六个接口后使用，保留下载的原文件。此项是实测到的兼容性差异，不能通过把编译器输出也改成 `ptr` 来适配。

  **参考定位（无直接实现）：** 参考仓库未附兼容运行时生成器，也不处理本机下载的 Clang 22 运行时。这是本年度环境准备任务，按 P0.2 单独解决。
- [x] **P0.3 跑通链接。** 将最小手写 IR 与兼容的 `libsysy.ll` 链接，至少验证一次 `get_int` 和 `put_int`。本地执行环境的 target triple、运行时 C 库与 `lli` 要匹配，暂不写入 MIPS 的 target triple。

  **参考定位（部分可借鉴）：** [Compiler.java · LLVM 工具使用注释（L18）][ref-entry-tools]中的 `llvm-link -S ... lib.ll ...`、`lli ...` 展示链接执行顺序；没有自动执行这两步的 Java 方法，库名和接口需换成本年度版本。
- [x] **P0.4 明确两种输入的归属。** 编译器仅读取 `testfile.txt` 并输出 `llvm_ir.txt`；`input.txt` 只交给生成程序执行时的标准输入。验收：有 `get_int()` 的源程序在没有运行时输入时也能立即编译完成。

  **参考定位（已有对应实现）：** [Controller.run() · 源文件输入（L39）][ref-controller-input]读取 `Configure.source`，[Compiler.main()（L13）][ref-entry-main]只解析编译参数并运行编译器；运行时输入在[Compiler.java · LLVM 工具使用注释（L18）][ref-entry-tools]所示的 lli 阶段提供。

### P1：把语义分析结果交给 IR 阶段

主要文件：`src/frontend/Traverser.java`、`src/frontend/ast/declaration/`、`src/frontend/ast/function/`、`src/frontend/ast/expression/UnaryExp.java`。

- [x] **P1.1 提供表达式类型查询。** 将本次遍历的节点类型表保存在可传递的 `SemanticInfo` 中，提供 `typeOf(ASTNode)`。不要依赖重新做常量求值来猜测运行时类型，也不要只暴露 `charResult` 布尔标志。

  **参考定位（部分可借鉴）：** [Expr.calculateType()（L26）][ref-expr-type]逐层调用[ExprAdd.calculateType()（L46）][ref-expr-add-type]等节点方法推导类型。参考代码没有 `SemanticInfo/typeOf` 类型快照；学习信息传递目的即可，本项目沿用已计算的类型表更合适。
- [x] **P1.2 保存定义绑定。** 记录 `ConstDef/VarDef → SymbolVariable`、`FuncFormalParam → SymbolVariable`、`FuncDef → SymbolFunction`。可保存在 `SemanticInfo` 的 identity map 中，或为节点增加字段，统一采用一种主路径。

  **参考定位（已有对应实现）：** [frontend/Traverser.spawn()（L77）][ref-semantic-spawn]给函数与形参调用 `setSymbol()`，[frontend/Traverser.registerDecl()（L129）][ref-semantic-decl]给 ConstDef/VarDef 绑定 SymbolVariable。学习从“建立符号”到“挂回 AST”的完整路径。
- [x] **P1.3 保存引用绑定。** 复用 `LVal.getSymbol()`，为 `UnaryExp.Kind.Call` 保存已解析的 `SymbolFunction`，包括运行时函数。验收：IR 阶段拿到符号后不再按名称重新解析局部作用域。

  **参考定位（已有对应实现）：** [frontend/Traverser.visitLeftValue()（L316）][ref-semantic-lval]绑定左值，[frontend/Traverser.visitUnaryExpr() · Call（L373）][ref-semantic-call]的 Call 分支绑定被调函数，节点字段见[ExprUnaryCall.setSymbol()（L25）][ref-call-symbol]。本年度内置函数也应走这条绑定链。
- [x] **P1.4 正确处理 main。** 当前 main 不进入可输出的符号表，IR 仍要独立创建 `i32 @main()`；不要为了复用普通函数路径而改变现有 `symbol.txt` 输出。

  **参考定位（已有对应实现）：** [MainFuncDef.into()（L27）][ref-main-into]临时构造 main 的 FuncDef/SymbolFunction，[IrBuilder.buildFunctions()（L88）][ref-builder-functions]调用 `into()` 后复用函数生成。可学习其复用方式，无需把 main 加入前端输出符号表。
- [x] **P1.5 区分初值与常量。** 全局变量和 static 变量也可能有 `hasConstantValue()` 或 `getConstantArray()`，这表示可用初值，并不说明对象不可变。只有满足源语言只读语义的对象才允许当常量替换；可变对象被写入后必须读取当前值。

  **参考定位（部分可借鉴）：** [frontend/Traverser.registerDecl()（L129）][ref-semantic-decl]记录全局变量的编译期初值，[SymbolVariable · 初值记录（L30）][ref-symbol-constant]保存它，[GlobalVariable.from()（L49）][ref-global-from]用它构造全局存储。参考中的“constantValue”也承载可变全局初值，不能按字段名称直接判断对象只读。
- [x] **P1.6 固定编译任务的状态边界。** 每次编译重新建立语义结果、IR 模块、命名计数器、字符串池和跳转上下文。当前语义遍历结束会弹出局部作用域，不能靠遍历结束后的 `SymbolTable.getVariable(name)` 找回所有局部定义。

  **参考定位（部分可借鉴）：** [IrBuilder.build()（L58）][ref-builder-build]生成新的模块容器，[llvm/Traverser.visitFunction()（L105）][ref-ir-function]末尾重置 Value 计数器，计数器实现见[GlobalCounter.reset()（L16）][ref-counter]。参考入口按单次进程使用静态符号表，没有完整的同 JVM 多次编译复位机制。

验收：同名全局/局部/形参/嵌套块变量绑定准确；同一 JVM 连续编译两个程序，第二次结果不受第一次影响。

### P2：建立可输出、可遍历的 IR 模型

参考：R 的 `llvm/value/`、`llvm/value/instruction/`、`llvm/IrModule.java`，以及 IR 第 19～46 页。

- [x] **P2.1 类型模型。** 实现 `i1/i8/i32/void/label`、数组、指针和函数签名；实现前端类型转换。数组对象转换为 `[N x T]`，数组形参转换为 `T*`，取得对象地址时再增加一层指针。

  **参考定位（部分可借鉴）：** [Ty · I1/I8/I32/Void（L6）][ref-ty]定义常用类型，[TyArray.from()（L43）][ref-ty-array]把数组形参转换成指针，[TyPtr（L3）][ref-ty-ptr]保留被指向类型。它把前端和 IR 类型放在一起，没有清单建议的独立函数类型/label 类型模型。
- [x] **P2.2 Value 和容器。** 实现常量、全局对象、函数、参数、基本块、指令；保留父函数/父块、操作码、操作数对象和结果类型，MIPS 后端能够直接遍历。

  **参考定位（已有对应实现）：** 按[IrModule（L15）][ref-module]、[Function（L16）][ref-function]、[BasicBlock（L7）][ref-block]、[Value（L13）][ref-value]阅读容器层次，指令继承 User。形参由[Function.addParam()（L71）][ref-function-param]创建为普通 Value，未单设 Argument 类。
- [x] **P2.3 最小指令集合。** 实现 `alloca/load/store`、`add/sub/mul/sdiv/srem/udiv/urem`、`icmp`、`zext/trunc`、`getelementptr`、`call`、条件/无条件 `br`、`ret`。基础版本 switch 使用比较与跳转链即可，暂不强制增加 LLVM `switch` 指令。

  **参考定位（部分可借鉴）：** 入口看[IAllocate（L11）][ref-instruction-alloca]和[IBinary（L18）][ref-instruction-binary]，再读同目录 ILoad/IStore/IComparator/IConvert/IGep/ICall/IBranch/IReturn 的构造器与 `toLLVM()`。[BinaryOp（L3）][ref-instruction-opcode]缺少 `udiv/urem` 和无符号大小比较，需补齐。各指令的可点击位置集中列在第 9 节。
- [x] **P2.4 构建约束。** `IrBuilder` 提供当前函数、当前插入块、建块、插入指令、`isTerminated()`、`branchIfOpen()` 等操作；禁止在 terminator 后继续插入普通指令或第二个 terminator。将“创建块”和“是否切换插入点”明确区分。

  **参考定位（部分可借鉴）：** [IrBuilder.createBlock()（L142）][ref-builder-create]建块并切换插入点，[IrBuilder.insertInstruction(BasicBlock, User)（L162）][ref-builder-insert]检查旧块是否已终结。注意后者在块已终结时直接返回一个没有插入的 instruction，清单建议显式拒绝/处理这种状态，避免误认为指令已经入块。
- [x] **P2.5 命名和打印。** 优先使用 `%v0`、`%arg0`、`bb0` 等具名标识，避免纯数字参数、块与临时值共享编号时出错；保证函数内名称不冲突、模块级名称不冲突、输出顺序稳定。`store`、`br`、`ret`、void call 不打印结果赋值。

  **参考定位（部分可借鉴）：** [IrModule.toString()（L30）][ref-module-print]输出模块，Function/BasicBlock 输出函数与块，指令各自 `toLLVM()`。[VariableRename.run(Function)（L37）][ref-rename]实现纯数字重新编号，[ICall.toLLVM()（L27）][ref-instruction-call]示范 void call 不打印结果；参考没有独立 IrPrinter，且模块头包含时间戳。
- [x] **P2.6 操作数维护接口。** 至少统一 `getOperands()`、`replaceOperand()`；若同步实现 `Use/User`，验证使用关系注册在被使用的 Value 上，替换和删除会更新双方。完整的 use-def 优化设施可以后补，但不要让后端依赖解析字符串。

  **参考定位（部分可借鉴）：** [User.addOperand()（L21）][ref-user-operand]、[User.replaceOperand()/dropOperand()（L38）][ref-user-replace]和[Use（L9）][ref-use]展示操作数维护。必须结合[Value · uses 的设计说明（L16）][ref-value-uses]阅读：参考把 uses 登记在使用者一侧，作者也注明设计特殊；清单采用“被使用值保存其使用者”的约定时，要一起调整登记、替换和删除，不能只复制类名。

验收：直接用模型构造 `int add(int x,int y)` 和 main，打印后通过 `llvm-as`，各指令的结果类型、指针类型和参数类型一致。

### P3：跑通最小端到端闭环

主要文件：`src/utils/Controller.java`、`src/utils/HomeworkConfig.java`、`src/utils/Configure.java`、`src/config.json`。

- [x] **P3.1 增加 LLVM 阶段开关和输出文件名。** 例如增加 `Hw.Llvm`，使 `getTarget()` 返回 `llvm_ir.txt`；保留前三个阶段的独立验收能力。

  **参考定位（已有对应实现）：** [HomeworkConfig.Hw（L7）][ref-home-stage]包含 CodegenI/CodegenII，[HomeworkConfig.getTarget()（L18）][ref-home-target]分别返回 llvm_ir.txt/mips.txt。照此为当前三阶段枚举增加 LLVM 阶段。
- [x] **P3.2 在语义分析之后接入生成器。** 取得 AST 和 `SemanticInfo`，调用生成器返回 `IrModule`，再由打印器输出。LLVM 阶段遇到任何已有错误时输出 `error.txt` 并返回，不进入 IR 构建。

  **参考定位（已有对应实现）：** 先读[Controller.run() · 错误截断（L76）][ref-controller-error]，再读[Controller.run() · IR 生成入口（L85）][ref-controller-ir]，最后读[Controller.run() · LLVM 输出（L123）][ref-controller-output]。这里串起语义完成、错误截断、IrBuilder.build().emit() 和文本输出。
- [x] **P3.3 支持 `int main(){return 0;}`。** 不依赖输入输出库即可生成合法 main；为正常结束的 void 函数补 `ret void`。

  **参考定位（已有对应实现）：** [IrBuilder.buildFunctions()（L88）][ref-builder-functions]接入 main，[llvm/Traverser.visitFunction()（L105）][ref-ir-function]为未终结的 void 函数补 IReturn，[IReturn.toLLVM()（L18）][ref-instruction-return]输出 `ret void` 或带类型的返回值。
- [x] **P3.4 固定输出与提交配置。** 显式使用 UTF-8 输出，LLVM 提交配置设为 `{"programmingLanguage":"Java","objectCode":"LLVM"}`；编译器不执行生成的程序，不把调试日志混入 IR 文件。

  **参考定位（部分可借鉴）：** [Controller.run() · LLVM 输出（L123）][ref-controller-output]展示文件输出，[Archiver · 旧版配置模板（L29）][ref-archiver-config]展示随阶段生成配置的思路。旧模板用 `programming language/object code` 和小写 `java/llvm`，与 2026 格式不同；参考的 FileWriter 也未显式指定 UTF-8。

**里程碑 M0：** 从 `testfile.txt` 到 `llvm_ir.txt`，再到 `lli`，最小程序成功返回；错误程序只走错误处理。

### P4：标量声明、左值、表达式与赋值

主要节点：`ConstDecl/ConstDef/VarDecl/VarDef`、`LVal`、`Number`、`PrimaryExp`、`UnaryExp`、`BinaryExp`。

- [x] **P4.1 全局标量。** 生成 int/char 全局变量和常量，初值在编译期计算；没有显式初值的全局变量置零。源语言可变的全局对象使用 `global`，不因初值可计算就变成 `constant`。

  **参考定位（部分可借鉴）：** [frontend/Traverser.registerDecl()（L129）][ref-semantic-decl]计算全局初值，[IrBuilder.buildGlobalVariables()（L65）][ref-builder-global]调用[GlobalVariable.from()（L49）][ref-global-from]构造对象。[GlobalVariable.toString()（L89）][ref-global-print]统一输出 `global`，没有按 const 输出 `constant`，只读存储策略需自行明确。
- [x] **P4.2 普通局部标量。** 使用 `alloca + load/store`。建议将固定大小 alloca 集中到函数 entry，初始化 store 仍在源声明的位置执行，尤其不能把循环内初始化移出循环；未初始化的普通局部变量无需补零。

  **参考定位（部分可借鉴）：** [llvm/Traverser.visitDecl()（L127）][ref-ir-decl]为局部 const/var 分配 IAllocate 并写初值，无初值变量直接跳过初始化；参数槽位见[IrBuilder.registerFunction()（L100）][ref-builder-register]。普通局部 alloca 原地插入，不具备清单建议的 entry 集中分配策略。
- [x] **P4.3 分离地址与值。** 建立 `emitAddress(LVal)` 和 `emitValue(...)`：赋值左边取地址，表达式中的标量取值；数组整体求值在 P7 中返回首元素地址。

  **参考定位（部分可借鉴）：** 对照[llvm/Traverser.visitPrimaryExpr()（L269）][ref-ir-primary]的 LVal 分支和[llvm/Traverser.performAssign()（L607）][ref-ir-assign]：前者取地址后 load，后者计算地址后 store。参考没有独立 `emitAddress()`，可将两处重复的寻址逻辑整理成该接口。
- [x] **P4.4 表达式递归翻译。** 按本项目 left/op/right 树结构支持常量、括号、左结合加减乘除模和一元正负；保留调用等副作用。常量折叠作为可选简化，基础正确性不依赖优化。

  **参考定位（已有对应实现）：** [llvm/Traverser.visitAddExpr()（L226）][ref-ir-add]、[llvm/Traverser.visitMulExpr()（L238）][ref-ir-mul]、[llvm/Traverser.visitUnaryExpr()（L251）][ref-ir-unary]与[llvm/Traverser.visitPrimaryExpr()（L269）][ref-ir-primary]构成表达式递归翻译链。学习逐项累积运算结果的顺序，改写为本项目 left/op/right 树；char 和 Cast 另按 P5 适配。
- [x] **P4.5 赋值和表达式语句。** 计算右值后 store；空语句不产生指令，普通表达式语句计算后丢弃结果，不能连同有副作用的调用一起省略。

  **参考定位（已有对应实现）：** [llvm/Traverser.performAssign()（L607）][ref-ir-assign]最终生成 IStore，[llvm/Traverser.visitStmt() · Expr（L595）][ref-ir-expr-stmt]只在表达式存在时访问表达式，[llvm/Traverser.visitBlock()/visitBlockItem()（L336）][ref-ir-block]顺序访问块项。

验收：全局与局部读写、变量遮蔽、`1-2-3`、`8/2/2`、负 int 的除模，以及带有副作用的表达式语句正确。

### P5：落实 2026 版 char 和显式转换

G 第 8 页是此部分的主要依据。报告 IR 第 35 页的隐式混合运算示例不能直接复用。

- [x] **P5.1 固定数值表示。** int 用 i32，char 用 i8，字符常量按词法阶段已解码的字节值构造常量。LLVM 的 i8 本身不区分符号，选择有符号/无符号操作由生成器负责。

  **参考定位（部分可借鉴）：** [Ty · I1/I8/I32/Void（L6）][ref-ty]提供 i8/i32，[ConstInteger（L9）][ref-const-int]保存带类型字面量，[llvm/Traverser.visitPrimaryExpr()（L269）][ref-ir-primary]把字符常量构造为 i8。类型位宽可参考，但 unsigned 的每步运算规则还需 P5.3/P5.4 补齐。
- [x] **P5.2 显式转换。** `(int)charValue` 使用 `zext i8 ... to i32`；`(char)intValue` 使用 `trunc i32 ... to i8`；同类型转换复用原值。不要用 `sext` 扩展 128～255 的 char。

  **参考定位（部分可借鉴）：** [IConvert.toLLVM()（L22）][ref-instruction-convert]输出 trunc/zext，[Compatibility.unityIntoInteger(target, ...)（L56）][ref-compat-int]在类型不同时插入 IConvert。参考[llvm/Traverser.visitUnaryExpr()（L251）][ref-ir-unary]没有 Cast 语法分支，需要为本项目 `UnaryExp.Kind.Cast` 接上这些指令。
- [x] **P5.3 逐步回绕。** char 的加、减、乘和一元负保持 i8 结果，逐步执行模 256；若内部选择先扩展到 i32 运算，每个 char 运算节点结束都必须截断，不能等到赋值或返回时才截断。char 算术不添加会把合法回绕变成 poison 的 `nuw/nsw` 标记。

  **参考定位（无直接实现）：** [llvm/Traverser.visitAddExpr()（L226）][ref-ir-add]、[llvm/Traverser.visitMulExpr()（L238）][ref-ir-mul]会经 Compatibility 把算术值统一为 i32，未逐个 char 节点回绕。将这些方法作为需要修改的位置，按清单重写，不能直接沿用其算术生成结果。
- [x] **P5.4 除法、取模和比较。** int 使用 `sdiv/srem` 与有符号大小比较；char 使用 `udiv/urem` 与无符号大小比较，或先零扩展再进行等价计算；相等比较使用 `eq/ne`。

  **参考定位（部分可借鉴）：** [BinaryOp（L3）][ref-instruction-opcode]定义 sdiv/srem 和有符号比较，[llvm/Traverser.visitCondRel()（L348）][ref-ir-rel]统一操作数后生成比较。新增 unsigned 指令/判定选项，并在生成处根据本年度源类型选择。
- [x] **P5.5 区分布尔内部表示与语言类型。** `icmp` 产生 i1，条件分支使用 i1；`!` 作为后续算术操作数时扩展为 i32 的 0/1。关系/相等比较的判定结果也要按比较表达式语义规范化为 0/1；不能因为输入是 char 就对比较结果套用普通 char 算术规则。核对 `SemanticInfo` 中相应类型与运行时计算的一致性。

  **参考定位（部分可借鉴）：** [IComparator（L7）][ref-instruction-compare]产生 i1，[Compatibility.unityIntoBoolean()（L31）][ref-compat-bool]把整数转为判定，[Compatibility.unityIntoInteger(target, ...)（L56）][ref-compat-int]把判定扩展为整数；[llvm/Traverser.visitUnaryExpr()（L251）][ref-ir-unary]的 Not 分支可沿着阅读。参考侧重内部转换，本年度逻辑表达式的 int 类型需另行落实。
- [x] **P5.6 保留严格匹配。** 合法的类型变化来自显式 Cast 或内部布尔表示转换。不要照搬 R 的通用自动转换，把本应报错的 int/char 混合赋值、传参或返回静默修正。

  **参考定位（无直接实现）：** 本年度严格匹配规则没有可直接移植的实现。对照[SymbolType.compatible()（L48）][ref-ty-compatible]、[frontend/Traverser.visitUnaryExpr() · Call（L373）][ref-semantic-call]和[Compatibility.unityIntoInteger(target, ...)（L56）][ref-compat-int]理解旧版检查与自动适配的边界，保留当前项目的严格语义检查。

关键验收：运行时与 `calculateConst()` 对同一算术表达式给出相同结果；`(int)(((char)255 + (char)1) / (char)2)` 得到 `0`，`(int)((char)255 / (char)2)` 得到 `127`，`(int)(char)255` 得到 `255`。

### P6：用户函数、参数与六个运行时接口

- [x] **P6.1 函数定义和调用。** 支持 int/char/void 返回值、无参/多参函数、递归和嵌套调用。先登记正在定义的函数再生成函数体，使自递归可解析；IR 预建函数对象不改变前端“先声明再使用”的限制。

  **参考定位（已有对应实现）：** [llvm/Traverser.visitFunction()（L105）][ref-ir-function]先注册函数并加入 functionMap，再访问函数体；[IrBuilder.registerFunction()（L100）][ref-builder-register]构建参数和入口块；[llvm/Traverser.visitCall()（L328）][ref-ir-call]创建 ICall。这是自递归能找到自身的关键顺序。
- [x] **P6.2 标量形参。** 为实参和形参保持准确类型与位置；基础实现可在 entry 为标量形参 alloca，再 store 输入值，后续赋值只修改该次调用的参数副本。

  **参考定位（已有对应实现）：** [IrBuilder.registerFunction()（L100）][ref-builder-register]先 `function.addParam()`，再创建 entry，并为各形参 alloca/store，最后把符号的 Value 改成槽位地址。参数值本体见[Function.addParam()（L71）][ref-function-param]。
- [x] **P6.3 call 与 return。** void call 无结果，非 void call 可作为表达式；返回值匹配函数返回类型。`return` 终结当前基本块，后续语句不能继续添加到该块。

  **参考定位（部分可借鉴）：** [llvm/Traverser.visitCall()（L328）][ref-ir-call]区分 void 返回，[ICall.toLLVM()（L27）][ref-instruction-call]打印 call，[llvm/Traverser.visitStmt() · Return（L548）][ref-ir-return]生成 IReturn 并经[IrBuilder.insertInstruction(BasicBlock, User)（L162）][ref-builder-insert]限制终结后插入。Return 分支中的自动 IConvert 属于旧版适配，本年度不得用它放过源语言返回类型错误。
- [x] **P6.4 运行时签名集中登记。** 将下列六个函数建为外部函数对象；使用过的函数在头部声明一次，全部声明也符合 J 要求。`printf` 只作为前端语法存在，不生成外部 printf 调用。

  **参考定位（部分可借鉴）：** [IrBuilder.externalFunctions（L38）][ref-builder-runtime]集中创建旧版外部函数，使用[Function.external()（L54）][ref-function-external]记录签名，由[IrModule.toString()（L30）][ref-module-print]输出 declare。替换函数名与 char 签名，并新增 get_string；参考不是本年度六接口表。
- [x] **P6.5 普通调用路径。** 使用与用户函数相同的调用生成逻辑处理 `get_int/get_char/put_int/put_char`，P7 完成后加入 `get_string/put_string` 的数组实参。不要增加旧版 `StmtGetInt` 等 AST，也不要生成旧版函数名称。

  **参考定位（部分可借鉴）：** [llvm/Traverser.visitCall()（L328）][ref-ir-call]展示普通函数调用，[llvm/Traverser.visitStmt() · GetInt/GetChar（L587）][ref-ir-get]展示把旧输入语句降低为 ICall。参考 AST 层仍把输入特殊化；本年度应把外部函数也交给普通调用生成路径。

```llvm
declare i32 @get_int()
declare i8 @get_char()
declare void @get_string(i8*, i32)
declare void @put_int(i32)
declare void @put_char(i8)
declare void @put_string(i8*)
```

验收：有返回值调用参与表达式，void 调用单独成句，递归调用正确，函数调用写入的全局变量能被后续 load 观察到。

**里程碑 M1：** 常量、变量、读入赋值、算术、标量函数五类基础测试可以编译并链接执行。

### P7：数组、初始化与字符串存储

- [x] **P7.1 全局/局部一维数组。** 生成 `[N x i32]`、`[N x i8]`；全局和常量初值使用 `ConstFolder/FixedArray`，局部运行时初值逐项求值并 store，保留函数调用等副作用。

  **参考定位（已有对应实现）：** 全局路径为[IrBuilder.buildGlobalVariables()（L65）][ref-builder-global]与[GlobalVariable.from()（L49）][ref-global-from]；局部路径为[llvm/Traverser.visitDecl()（L127）][ref-ir-decl]的 Const/Var 初始化列表分支。后者逐个访问 Exp 再生成 store，可学习如何保留运行时初值。
- [x] **P7.2 初值归一化。** 覆盖 `{}`、部分元素、完整元素和未显式初始化等情形；常量数组、全局数组、static 数组的应补零元素必须补齐。对带初始化列表的普通局部数组按课程采用的数组初始化规则补齐剩余元素；与完全没有初始化的普通局部数组区别处理。

  **参考定位（已有对应实现）：** [FixedArray.from()（L48）][ref-fixed-array]用稀疏元素表记录常量数组，[llvm/Traverser.visitDecl()（L127）][ref-ir-decl]的列表初始化循环将缺少的项补零；全局输出另经 ArrayInitialString。static 数组没有现成路径，需要接入相同初值归一化逻辑。
- [x] **P7.3 区分两种 GEP。** 全局/局部数组对象访问使用 `getelementptr [N x T], [N x T]* %a, i32 0, i32 %i`；数组参数访问使用 `getelementptr T, T* %p, i32 %i`。GEP 只返回地址，读元素还需 load，写元素使用 store。

  **参考定位（已有对应实现）：** [IrBuilder.getGep()（L184）][ref-builder-gep]按 `isFromParam()` 决定是否先 load 指针，[IGep.toLLVM()（L54）][ref-instruction-gep]按来源决定是否输出额外的 `i32 0`。逐个对照数组对象指针与数组形参指针的类型变化。
- [x] **P7.4 数组传参。** 数组整体表达式衰变为 `T*`，不 load 整个数组，不复制数组内容。数组形参可直接使用传入指针；若将其存入槽位，先从 `T**` 槽位 load 出 `T*` 再 GEP。覆盖形参继续传给另一函数的情况。

  **参考定位（已有对应实现）：** [TyArray.from()（L43）][ref-ty-array]把省略首维的参数变为 TyPtr，[IrBuilder.registerFunction()（L100）][ref-builder-register]存入参数槽位，[llvm/Traverser.visitPrimaryExpr()（L269）][ref-ir-primary]的无下标数组分支返回 GEP 地址，再由[llvm/Traverser.visitCall()（L328）][ref-ir-call]传递。数组形参转传也沿着这条链阅读。
- [x] **P7.5 字符串初始化。** 使用词法阶段已经完成转义处理的字符串，按字节复制；长度不足补零，长度相等不额外追加终止符，数组更短时只保留前 N 字节。`char a[2]="abc"` 合法，不能越界写第三字节；不拿未终止的数组做 `%s` 正确性测试。

  **参考定位（部分可借鉴）：** [llvm/Traverser.fillStringInitializer()（L209）][ref-ir-string]按声明长度循环，超出字符串的部分补零，长度更短时自然截断；全局常量路径见[FixedArray.from()（L48）][ref-fixed-array]。后者会在稀疏表额外记录 NUL，需确保最终输出仍限制在声明长度内。
- [x] **P7.6 独立的输出字符串池。** 为 printf 的普通文本片段建立带 `\00` 的模块级只读字节数组；空串、换行、反斜杠等按 LLVM 字符串规则编码，数组长度按实际字节计算。字符串池属于输出常量，不能替代可写的源程序 char 数组对象。

  **参考定位（无直接实现）：** [llvm/Traverser.visitStmt() · Printf（L565）][ref-ir-printf]把普通文本逐字符转为 putch；参考 LLVM 层没有输出字符串池。可借[GlobalVariable（L17）][ref-global]实现字节数组容器，再自行增加去重、字节转义、终止符和长度计算。MIPS 层的字符合并不等于 LLVM 字符串池。
- [x] **P7.7 字符串输入输出。** `get_string(a,n)` 和 `put_string(a)` 接收 `i8*`，遵循运行时原有行为，尤其是换行保留、NUL 结尾和 `max_len <= 0` 不修改缓冲区；这些行为通过调用已提供的库实现。

  **参考定位（部分可借鉴）：** [IrBuilder.externalFunctions（L38）][ref-builder-runtime]含旧版 putstr(i8*) 的声明方式，[ICall.toLLVM()（L27）][ref-instruction-call]可表达指针实参；参考没有 get_string，也没有完整的本年度字符串运行时调用路径，需要扩展。

验收：全局/局部/形参数组、动态下标、数组参数写入反映到调用者、数组转传、char 与 int 元素不同类型，以及字符串三种长度关系均正确。

### P8：printf 降低为 put_* 调用

- [x] **P8.1 先求值全部参数。** 先遍历并保存全部参数的 Value，再依次输出文本与参数；不能先输出前缀再计算后面的参数。保存的是当时计算出的值或指针，不在输出时重新生成参数表达式。

  **参考定位（已有对应实现）：** [Printf · 预先收集实参 Value（L567）][ref-ir-printf-eval]通过 `.stream().map(this::visitExpr).toList()` 先收集全部参数，之后才遍历格式串。再读[Readme.md · printf 求值顺序教训（L506）][ref-readme-debug]了解作者为何从惰性迭代器改成 List。
- [x] **P8.2 三种格式。** `%d → put_int(i32)`，`%c → put_char(i8)`，`%s → put_string(i8*)`；按已通过语义检查的类型调用，普通文本可合并为字符串池条目，也可逐字符输出。

  **参考定位（部分可借鉴）：** [llvm/Traverser.visitStmt() · Printf（L565）][ref-ir-printf]识别 `%d/%c` 并生成 putint/putch，普通字符逐个输出。扩展 `%s`、更新库名，并将 `%c` 参数从旧 i32 接口改为本年度 i8 接口。
- [x] **P8.3 转义和边界。** 复用已解码的格式串，正确处理空串、连续占位符、开头/结尾占位符、只有普通文本和换行；不要对同一段 `\n` 二次解码。

  **参考定位（部分可借鉴）：** [Lexer.scanString()（L252）][ref-lexer-string]在词法阶段解码字符串，[llvm/Traverser.visitStmt() · Printf（L565）][ref-ir-printf]扫描已经解码的内容。可学习“只解码一次”；字符串池方案的 LLVM 十六进制字节编码需自行增加。

关键验收：若 `f()` 会先输出 `X` 并返回 `1`，则 `printf("v=%d",f());` 输出 `Xv=1`；无参数 printf 不需要构造无用临时值。

**里程碑 M2：** 六类基础测试全部跑通，并具备数组、char、字符串和完整 printf 支持。

### P9：条件、if、while 和短路求值

- [x] **P9.1 条件接口。** 建立 `emitCond(node, trueBlock, falseBlock)`，将普通 int/char 条件与同类型零比较，生成分支。`&&` 左侧为真时才进入右侧块，`||` 左侧为假时才进入右侧块；不要用先计算两侧再 `and/or` 代替短路。

  **参考定位（部分可借鉴）：** [llvm/Traverser.visitCondAnd()（L385）][ref-ir-and]与[llvm/Traverser.visitCondOr()（L418）][ref-ir-or]展示右操作数的条件执行和短路块；它们通过 IPhi 合成结果值。清单建议的 `emitCond(node,T,F)` 直接跳转接口未在参考中实现，需要保留控制逻辑并改写结果传递方式。
- [x] **P9.2 比较表达式。** 支持关系、相等、不等及连续比较的左结合求值；将前一比较产生的 0/1 适配为下一次比较需要的类型，避免直接比较 i1 和 i32。按源语言类型规则处理 char 比较。

  **参考定位（已有对应实现）：** [llvm/Traverser.visitCondRel()（L348）][ref-ir-rel]和[llvm/Traverser.visitCondEqu()（L362）][ref-ir-equ]逐项计算，比较结果在下一次比较前经 Compatibility 适配。可学习左结合及 i1/i32 衔接；char 比较需结合 P5 的无符号规则。
- [x] **P9.3 if/else。** 建立 then、可选 else 和必要的 merge；只为未终结的分支补跳转。两侧均已返回时，不制造可达的伪后继。

  **参考定位（已有对应实现）：** [llvm/Traverser.visitStmt() · If（L456）][ref-ir-if]创建 then/else/merge，保存各分支翻译后的末尾块，再回填跳转；[IrBuilder.insertInstruction(BasicBlock, User)（L162）][ref-builder-insert]防止向已终结的末尾继续插入。学习时尤其关注复杂条件会改变插入点。
- [x] **P9.4 while。** 建立 condition/body/exit；从条件入口重新计算条件，body 正常结束回 condition，continue 也回 condition。短路条件可能包含多个基本块，continue 目标始终是条件入口。

  **参考定位（部分可借鉴）：** [llvm/Traverser.visitStmt() · For（L490）][ref-ir-for]提供 cond/body/update/exit 的循环模板。本年度只需要 cond/body/exit，删除 init/update 专用路径，把 continue 从 update 改到 cond 的入口。参考没有 While 分支。
- [x] **P9.5 单独维护跳转上下文。** 记录 break 的最近循环或 switch 退出目标，以及 continue 的最近循环条件目标。可用统一结构栈并跳过没有 continue 目标的 switch，也可分别维护两类目标栈。

  **参考定位（部分可借鉴）：** [LoopStorage · 跳转回填（L12）][ref-loop-storage]保存待回填的 break/continue，[llvm/Traverser.visitStmt() · For（L490）][ref-ir-for]入栈并在循环结束回填。参考 localLoops 只处理循环，需扩展以区分 switch 的 break 目标和最近循环的 continue 目标。
- [x] **P9.6 终结后的语句。** 对 `return/break/continue` 后的源码选择明确策略：停止生成当前不可达尾部，或放入单独且合法终结的不可达块。新建 IR 块不代表新建源语言作用域，短路块也不应改变符号绑定。

  **参考定位（部分可借鉴）：** [IrBuilder.insertInstruction(BasicBlock, User)（L162）][ref-builder-insert]对终结块停止插入；[llvm/Traverser.visitStmt() · Break（L535）][ref-ir-break]与[llvm/Traverser.visitStmt() · Continue（L542）][ref-ir-continue]在跳转后创建 Dummy Block，[llvm/Traverser.visitStmt() · Return（L548）][ref-ir-return]直接插入返回。清单仍要求自己明确不可达块的保留与终结策略。

验收：`0 && f()`、`1 || f()` 不调用 f；混合 `a || b && c`、嵌套 if/while、空循环体、continue、break、分支内 return 正确。

### P10：2026 新增的 switch 和 static

- [x] **P10.1 switch 分派只计算选择值一次。** 预建每个 case/default 的块和 exit，使用 `icmp eq + br` 比较链分派；没有匹配且无 default 时到 exit，空 switch 仍保留选择表达式的副作用。

  **参考定位（无直接实现）：** 参考没有 Switch/Case 的 IR 翻译。可用[IComparator（L7）][ref-instruction-compare]和[IBranch.toLLVM()（L59）][ref-instruction-branch]作为比较链构件，选择值只调用一次表达式生成器，标签块与分派过程自行实现。
- [x] **P10.2 按源顺序贯穿。** 分派可直接进入匹配的 case，但各 case 的正常末尾跳到源码中的下一个标签块；default 也可以位于中间。空 case 可贯穿到下一标签，不能自动在每个 case 末尾补 break。

  **参考定位（无直接实现）：** 参考没有 case/default 顺序贯穿逻辑；需要按当前 SwitchStmt.getCases() 的源码顺序连接各 case 块。不能照搬 If 分支“全部跳 merge”的连接方式。
- [x] **P10.3 嵌套控制目标。** switch 内 break 跳到该 switch 的 exit；循环中的 switch 内 continue 跳到外层 while 条件；switch 内新建 while 后，break 应退出内层 while。case 值与选择值使用一致的 i32/i8 类型。

  **参考定位（部分可借鉴）：** [LoopStorage · 跳转回填（L12）][ref-loop-storage]和[llvm/Traverser.visitStmt() · Break（L535）][ref-ir-break]、[llvm/Traverser.visitStmt() · Continue（L542）][ref-ir-continue]提供回填素材，但没有 switch 混合嵌套语义。需要扩展控制上下文，不能原样对 localLoops.lastElement() 操作。
- [x] **P10.4 static 存储提升。** 将每个静态局部变量/数组变为模块级 `internal global`，按函数和符号唯一标识生成名称；词法可见性仍来自前端绑定。同名但不同函数或作用域的 static 不能共享存储。

  **参考定位（无直接实现）：** 参考没有 static 局部变量提升。借鉴[GlobalVariable（L17）][ref-global]、[IrBuilder.buildGlobalVariables()（L65）][ref-builder-global]和[SymbolVariable.setValue()/getValue()（L46）][ref-symbol-value]的全局地址绑定，自己增加唯一名称、internal 链接和局部符号到全局存储的映射。
- [x] **P10.5 static 初始化只做一次。** 初值直接写入模块级对象，缺省补零；函数入口、循环或声明执行位置都不能重复 store 初值。覆盖 int、char、数组和递归调用，static 读写与普通全局存储走同一套 load/store 路径。

  **参考定位（无直接实现）：** 可借[GlobalVariable.from()（L49）][ref-global-from]学习把编译期初值放进模块级对象；初始化一次的 static 语义及其在声明遍历中的分流需自行实现，不能继续走[llvm/Traverser.visitDecl()（L127）][ref-ir-decl]的局部 store 初值路径。

验收：default 在前/中/后、无 default、空标签、多层嵌套都正确；计数函数的 static 连续两次返回 `1`、`2`，同名独立 static 互不影响。

**里程碑 M3：** 完整 2026 文法可执行，达到本清单的功能目标。

### P11：结构验证、回归、UI 和提交收尾

- [x] **P11.1 生成结束执行 IR 结构检查。** 所有输出块恰有一个末尾 terminator，跳转目标属于本函数，指令类型一致，call/store/ret 类型准确，定义支配其使用；每个块与函数的父级正确，entry 没有回边。

  **参考定位（部分可借鉴）：** [BasicBlock.getSuccessors()（L46）][ref-block-successors]在缺少 terminator 时抛错，[IBinary（L18）][ref-instruction-binary]等构造器有局部 assert。参考没有覆盖清单所有条件的统一 IR verifier，也没有自动调用 LLVM verify 的驱动；这些检查需要补充。
- [x] **P11.2 加入独立的 IR 执行回归。** 每个用例先确认前端无错误，再进行 LLVM 解析/验证、运行时链接和实际执行，比对标准输出与退出状态，并设置执行超时。覆盖下一节的测试矩阵；不以“IR 文本大致相似”代替执行正确性。

  **参考定位（无直接实现）：** [Compiler.java · LLVM 工具使用注释（L18）][ref-entry-tools]只有手工调试命令，参考仓库中未找到解析、链接、执行、超时和比对输出一体化的测试脚本。自动回归需在本项目新增，不把这些注释当成现成测试框架。
- [x] **P11.3 保护既有阶段。** 运行已有 `tests/run-semantic-tests.ps1` 等前端回归；加入 LLVM 模式下错误程序不生成有效 IR 的检查。连续运行时避免把上一次的 `llvm_ir.txt` 当作本次错误程序的产物。

  **参考定位（部分可借鉴）：** [Controller.run() · 错误截断（L76）][ref-controller-error]在前端错误时截断代码生成。前端回归、先成功后失败的旧文件处理、同 JVM 状态隔离等没有现成套件，使用本项目已有测试继续扩展。
- [x] **P11.4 接入 UI。** 更新 `ui/compilerui/model/Stage.java`、`ui/compilerui/pipeline/Pipeline.java` 及相关阶段枚举使用点；传递同一份 `SemanticInfo`，记录 IR 文本和耗时，支持导出 `llvm_ir.txt`。先完成命令行闭环再处理展示。

  **参考定位（无直接实现）：** 参考仓库是命令行编译器，没有本项目的 Swing UI/Stage/Pipeline。此项直接修改本项目 ui/，复用已经实现的 IR 入口。
- [x] **P11.5 验证打包。** 使用 JDK 17 可用的语言特性和标准库；检查 `tools/pack.ps1` 打包后 `Compiler.java` 与 `config.json` 位于根目录，LLVM 配置正确。`tools/`、`tests/` 当前被 Git 忽略，新增测试或脚本若要留在版本库，需要显式处理其跟踪状态。

  **参考定位（部分可借鉴）：** [Archiver.main()（L92）][ref-archiver-main]遍历 Java 源文件、去掉最外层 src、写 zip 并加入配置。旧配置见[Archiver · 旧版配置模板（L29）][ref-archiver-config]，不能照搬字段或大小写；JDK 17 构建验证与本项目忽略规则检查需自行完成。
- [x] **P11.6 更新项目说明。** 同步 `README.md` 的阶段状态、执行方法，以及 `docs/编译器设计报告.md` 的 IR 模型、翻译规则和验证方式；把实际采用的类名和本清单对应起来。

  **参考定位（已有对应实现）：** [Readme.md · 中间代码生成（L363）][ref-readme-ir]介绍 IR 层次、翻译与调试，适合学习设计报告如何对应具体实现。更新时写本项目实际采用的结构和本年度语义。

## 6. 验收测试矩阵

这些是需要实现的行为测试，不要求 IR 的临时变量名与参考输出一致。下表的算术片段需放入完整合法程序，输出数值时使用 `%d` 和显式 `(int)` 转换。

| 类别            | 至少覆盖的用例                                                           | 预期或关注点                              |
| --------------- | ------------------------------------------------------------------------ | ----------------------------------------- |
| 基础入口        | `int main(){return 0;}`，空 void 函数                                  | LLVM 12 可解析，正确 return               |
| 作用域          | 同名全局、形参、嵌套块局部；不同函数同名局部                             | 取值和写入命中各自符号                    |
| int 算术        | 左结合减除、负数除模、一元正负                                           | `-7/3 == -2`，`-7%3 == -1` 的数值结果 |
| char 转换       | `(char)256`、`(char)-1`、`(int)(char)255`                          | 分别为 `0`、`255`、`255`            |
| char 中间回绕   | `((char)255+(char)1)/(char)2`                                          | 数值为 `0`，不能得到 `128`            |
| char 无符号运算 | `(char)255/(char)2`、`(char)255%(char)2`、`(char)255 > (char)1`    | 分别为 `127`、`1`、真                 |
| 常量/运行时一致 | 常量表达式与从 get_int 读入后显式转 char 的同式计算                      | 不能仅常量路径正确                        |
| 可变初值        | `int g=1;` 后在函数内改写，下一次读取                                  | 读取新值，不能折叠为初值 1                |
| 数组            | 全局、局部、const、动态索引、部分初始化、空初始化                        | 类型、偏移、补零和读写正确                |
| 数组传参        | 函数改写调用者数组；数组形参转传；数组元素按值传参                       | 基地址不多 load、不多一层 GEP             |
| 字符串初值      | `char a[4]="ab"`、`char b[2]="ab"`、`char c[2]="abc"`              | a 尾部补零，b/c 不额外写终止符            |
| 运行时          | 六个接口；get_string 的 0/1/正常容量、换行                               | 不提前读 stdin，接口与库一致              |
| printf          | `%d/%c/%s` 混合、无参、空串、参数函数自带输出                          | 所有参数先求值，再输出格式内容            |
| 函数            | int/char/void、递归、多参数、嵌套调用、形参被赋值                        | 返回值、值传递和副作用正确                |
| 条件            | 所有比较符、逻辑非、`if(!'a'+1)`、连续比较                             | i1 和源语言 0/1 数值正确衔接              |
| 短路            | `0&&f()`、`1\|\|f()`、`a\|\|b&&c`、循环短路条件                        | 侧效应次数和控制路径正确                  |
| 循环            | 空 body、嵌套 while、break/continue、body 内 return                      | continue 每次回到条件入口                 |
| switch          | int/char、空 switch、default 任意位置、无 default、空 case、fall-through | 选择值一次求值，按源码顺序贯穿            |
| 嵌套跳转        | while 内 switch 的 continue；switch 内 while 的 break                    | 找到正确的外围结构                        |
| static          | 缺省初值、重复调用、递归、同名不同作用域、static 数组                    | 初值一次建立，各对象独立                  |
| 错误与状态      | a～n 错误用例、同一 JVM 多次编译、先成功后失败                           | 只报告错误，无旧 IR/符号/字符串污染       |
| 官方综合样例    | J 第 2 页输入 `Az9!bB2?xy7#`                                           | 输出与官方五行结果逐字一致                |

官方综合样例的输出应为：

```text
raw=Az9!bB2?xy7#
upper=AZ9!BB2?XY7#
U=2 L=4 D=3 O=3
score=29
reverse=#7YX?2BB!9ZA
```

普通 C 程序可作为部分测试的对照，但本课程规定 char 每一步运算仍为 char；C 的整型提升可能让中间结果不同。因此 char 回绕测试使用人工推导或遵循课程规则的独立期望值，不能直接把未经改写的 gcc/clang 输出当作全部 SysY 语义的标准答案。

## 7. 本阶段为 MIPS 预留的接口

以下是 LLVM 阶段的设计约束，具体汇编实现留到下一阶段。

- [x] **MIPS.1 结构化输入。** 未来提供类似 `MipsGenerator.generate(IrModule)` 的入口，直接读取对象，不重新解析 `llvm_ir.txt`，也不重新遍历 AST 生成另一套程序语义。

  **参考定位（已有对应实现）：** [MipsModule.runParseIr()（L35）][ref-mips-parse]直接接收 IrModule，[MipsModule.parseFunction()（L54）][ref-mips-function]逐函数取基本块，再交给[MipsGenerator.run()（L205）][ref-mips-run]逐条翻译指令，无需重新解析 IR 文本。
- [x] **MIPS.2 类型和布局分离。** IR 保留 i8/i32、数组元素类型和 GEP 索引；MIPS 后端独立定义目标的指针大小、对齐与栈布局。不要把前端长度未知的数组参数按长度 0 的实体数组分配空间。

  **参考定位（部分可借鉴）：** [MipsGenerator.run() · IGep（L482）][ref-mips-gep]读取元素类型并按 sizeof 计算偏移；[MipsModule.parseFunction()（L54）][ref-mips-function]管理函数入口/出口与栈大小。[TyPtr.sizeof()（L21）][ref-ty-ptr-size]却把指针大小固定为 4，清单建议将目标布局单独抽出。
- [x] **MIPS.3 保留无符号 char 语义。** MIPS 阶段为 char 考虑 `lbu/sb`、无符号比较和截断/零扩展；M 第 14 页的 `lb` 示例不能直接作为本课程 unsigned char 的读取规则。

  **参考定位（已有对应实现）：** [MipsGenerator.run() · lbu（L385）][ref-mips-load]已按 unsigned char 使用 lbu，[MipsGenerator.run() · 按宽度 store（L409）][ref-mips-store]按值宽度选择 sb，[MipsGenerator.run() · IConvert（L447）][ref-mips-convert]对截断生成掩码。这些可学习，但不能据此认为旧版 LLVM 算术已经符合本年度逐步回绕语义。
- [x] **MIPS.4 运行时函数可识别。** 将六个外部调用保留为独立函数对象或带稳定标识的调用，便于后端映射到 MARS syscall：输入整数 5、输入字符 12、输入字符串 8、输出整数 1、输出字符 11、输出字符串 4。

  **参考定位（部分可借鉴）：** [MipsGenerator.run() · 旧库调用（L306）][ref-mips-call]按 ICall 的函数名识别旧版 getint/getchar/putint/putch 并生成 syscall。需要改为六个新版名称，补 get_string/put_string 的完整映射，特别是输入字符串的边界行为。
- [x] **MIPS.5 优化开关。** 预留 `IrModule → 可选 Pass → IR/MIPS` 的管线和优化前/后文本导出；默认关闭优化时同样正确。先生成基于内存的合法 SSA 临时值，再考虑 mem2reg。

  **参考定位（已有对应实现）：** [Configure.optimize（L47）][ref-config]控制优化，[Controller.run() · 可选优化（L87）][ref-controller-opt]组织 pass，[IrModule.runPass()（L79）][ref-module-pass]调用 pass 并可导出过程 IR，[Controller.run() · LLVM 输出（L123）][ref-controller-output]输出最终 LLVM。学习管线接口即可，现阶段不必移植全部优化。

本阶段可暂缓：支配树、完整活跃变量分析、mem2reg、phi 构建/消除、GVN/LVN、循环优化、图着色/线性扫描、MIPS 栈帧与寄存器分配。若选择直接移植 R 的 phi 短路方案，则 phi 的合法输出和未来 MIPS 降低就成为额外必需工作；本清单推荐的直接分支方案可延后这些内容。

## 8. 完成标准与参考阅读顺序

**完成标准：P0～P11 的任务完成，测试矩阵通过，LLVM 12 验证和执行成功，前端既有输出不回归，提交包能独立生成 `llvm_ir.txt`。** 没有优化不会阻止本阶段完成。

建议参考代码按以下顺序阅读，每次只借鉴当前要实现的部分：

1. 先读 [IrModule][ref-module]、[Value][ref-value]、[Function][ref-function]、[BasicBlock][ref-block]，理解容器、结果值和基本块，再从 [Controller 的 IR 入口][ref-controller-ir]跟到 [IrBuilder.build()][ref-builder-build]。
2. 按第 9 节读指令：先看 IAllocate/ILoad/IStore/IBinary/ICall/IReturn，再看 IGep/IComparator/IConvert/IBranch。每个类都先读构造器的类型和操作数，再读文本输出。
3. 回到 [visitDecl()][ref-ir-decl]、[visitPrimaryExpr()][ref-ir-primary]、[visitAddExpr()][ref-ir-add]、[visitCall()][ref-ir-call] 和 [Printf 分支][ref-ir-printf]，追踪一个变量从声明、取值、运算到输出的过程；完成后再读 [If][ref-ir-if]、[For][ref-ir-for]、[visitCondAnd()][ref-ir-and] 和 [visitCondOr()][ref-ir-or]。
4. 配合 [Compatibility][ref-compat-int] 理解内部类型适配，配合 [LoopStorage][ref-loop-storage] 理解跳转回填，再按本年度严格类型规则与 while/switch 嵌套语义改写。前端如何给 AST 绑定符号，从 [frontend/Traverser.spawn()][ref-semantic-spawn] 和 [registerDecl()][ref-semantic-decl] 开始读。
5. MIPS 阶段再从 [MipsModule.runParseIr()][ref-mips-parse]跟到 [MipsGenerator.run()][ref-mips-run]，之后看寄存器分配；本阶段只检查 IR 是否保留它们需要的信息。

LLVM 指令细节可查 [LLVM 12 Language Reference](https://releases.llvm.org/12.0.0/docs/LangRef.html)，尤其是 `icmp`、`zext/trunc` 和有/无符号除模的区别。课程源语言的规则仍以 G 为准。

## 9. 基础指令源码速查

下面均为 R 的实现位置。完整路径位于 `src/top/swkfk/compiler/llvm/value/instruction/`；点击链接可定位源码，构造器通常就在 `toLLVM()` 上方。

| LLVM 指令                     | 参考代码位置                                                                | 阅读重点                                                      |
| ----------------------------- | --------------------------------------------------------------------------- | ------------------------------------------------------------- |
| `alloca`                    | [IAllocate][ref-instruction-alloca]                                         | 结果是指针，输出的是其指向的分配类型                          |
| `load`                      | [ILoad.toLLVM()（L20）][ref-instruction-load]                               | 操作数是地址，结果类型取指针的 base                           |
| `store`                     | [IStore.toLLVM()（L26）][ref-instruction-store]                             | 先值后地址，无结果名，检查被指向类型                          |
| `add/sub/mul/sdiv/srem`     | [IBinary][ref-instruction-binary]、[BinaryOp][ref-instruction-opcode]       | 指令保存 opcode 与两个操作数，enum 决定文本拼写               |
| `udiv/urem`、无符号大小比较 | [BinaryOp 中需要扩展的位置][ref-instruction-opcode]                         | 参考未实现这些选项，本年度按 char 语义补充                    |
| `icmp`                      | [IComparator][ref-instruction-compare]、[BinaryOp][ref-instruction-opcode]  | 结果类型与被比较操作数的类型不同，结果为 i1                   |
| `trunc/zext`                | [IConvert.toLLVM()][ref-instruction-convert]                                | 由源类型、目标类型决定转换；本项目转换判断要依据位宽          |
| `getelementptr`             | [IGep.toLLVM()][ref-instruction-gep]、[IrBuilder.getGep()][ref-builder-gep] | 参数指针是否需要先 load，数组对象是否需要额外的首个 0         |
| `call`                      | [ICall.toLLVM()][ref-instruction-call]、[visitCall()][ref-ir-call]          | 被调函数、实参 Value 列表、void 与非 void 的输出差别          |
| `br`                        | [IBranch.toLLVM()][ref-instruction-branch]                                  | 条件跳转与无条件跳转的操作数，结合本类 fillNullBlock() 看回填 |
| `ret`                       | [IReturn.toLLVM()][ref-instruction-return]、[Return 分支][ref-ir-return]    | 有值/无值返回，作为基本块 terminator 的处理                   |

<!-- 参考代码链接：对应 2026-10-30 核对的本机版本，行号随参考仓库修改可能变化。 -->

[ref-archiver-config]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/Archiver.java:29
[ref-archiver-main]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/Archiver.java:92
[ref-block]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/BasicBlock.java:7
[ref-block-successors]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/BasicBlock.java:46
[ref-builder-build]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrBuilder.java:58
[ref-builder-create]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrBuilder.java:142
[ref-builder-functions]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrBuilder.java:88
[ref-builder-gep]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrBuilder.java:184
[ref-builder-global]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrBuilder.java:65
[ref-builder-insert]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrBuilder.java:162
[ref-builder-register]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrBuilder.java:100
[ref-builder-runtime]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrBuilder.java:38
[ref-call-symbol]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/ast/expression/ExprUnaryCall.java:25
[ref-compat-bool]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/helpers/Compatibility.java:31
[ref-compat-int]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/helpers/Compatibility.java:56
[ref-config]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/Configure.java:47
[ref-const-int]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/constants/ConstInteger.java:9
[ref-controller-error]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/Controller.java:76
[ref-controller-input]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/Controller.java:39
[ref-controller-ir]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/Controller.java:85
[ref-controller-opt]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/Controller.java:87
[ref-controller-output]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/Controller.java:123
[ref-counter]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/helpers/GlobalCounter.java:16
[ref-entry-main]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/Compiler.java:13
[ref-entry-tools]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/Compiler.java:18
[ref-expr-add-type]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/ast/expression/ExprAdd.java:46
[ref-expr-type]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/ast/expression/Expr.java:26
[ref-fixed-array]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/symbol/FixedArray.java:48
[ref-function]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/Function.java:16
[ref-function-external]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/Function.java:54
[ref-function-param]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/Function.java:71
[ref-global]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/GlobalVariable.java:17
[ref-global-from]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/GlobalVariable.java:49
[ref-global-print]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/GlobalVariable.java:89
[ref-home-stage]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/HomeworkConfig.java:7
[ref-home-target]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/HomeworkConfig.java:18
[ref-instruction-alloca]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/IAllocate.java:11
[ref-instruction-binary]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/IBinary.java:18
[ref-instruction-branch]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/IBranch.java:59
[ref-instruction-call]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/ICall.java:27
[ref-instruction-compare]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/IComparator.java:7
[ref-instruction-convert]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/IConvert.java:22
[ref-instruction-gep]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/IGep.java:54
[ref-instruction-load]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/ILoad.java:20
[ref-instruction-store]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/IStore.java:26
[ref-instruction-opcode]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/BinaryOp.java:3
[ref-instruction-return]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/instruction/IReturn.java:18
[ref-ir-add]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:226
[ref-ir-and]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:385
[ref-ir-assign]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:607
[ref-ir-block]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:336
[ref-ir-break]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:535
[ref-ir-call]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:328
[ref-ir-continue]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:542
[ref-ir-decl]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:127
[ref-ir-equ]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:362
[ref-ir-expr-stmt]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:595
[ref-ir-for]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:490
[ref-ir-function]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:105
[ref-ir-get]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:587
[ref-ir-if]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:456
[ref-ir-mul]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:238
[ref-ir-or]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:418
[ref-ir-primary]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:269
[ref-ir-printf]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:565
[ref-ir-printf-eval]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:567
[ref-ir-rel]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:348
[ref-ir-return]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:548
[ref-ir-string]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:209
[ref-ir-unary]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Traverser.java:251
[ref-lexer-string]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/Lexer.java:252
[ref-loop-storage]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/helpers/LoopStorage.java:12
[ref-main-into]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/ast/declaration/function/MainFuncDef.java:27
[ref-mips-call]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/arch/mips/process/MipsGenerator.java:306
[ref-mips-convert]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/arch/mips/process/MipsGenerator.java:447
[ref-mips-function]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/arch/mips/MipsModule.java:54
[ref-mips-gep]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/arch/mips/process/MipsGenerator.java:482
[ref-mips-load]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/arch/mips/process/MipsGenerator.java:385
[ref-mips-parse]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/arch/mips/MipsModule.java:35
[ref-mips-run]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/arch/mips/process/MipsGenerator.java:205
[ref-mips-store]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/arch/mips/process/MipsGenerator.java:409
[ref-module]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrModule.java:15
[ref-module-pass]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrModule.java:79
[ref-module-print]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/IrModule.java:30
[ref-readme-debug]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/Readme.md:506
[ref-readme-ir]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/Readme.md:363
[ref-rename]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/transforms/VariableRename.java:37
[ref-semantic-call]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/Traverser.java:373
[ref-semantic-decl]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/Traverser.java:129
[ref-semantic-lval]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/Traverser.java:316
[ref-semantic-spawn]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/Traverser.java:77
[ref-symbol-constant]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/symbol/SymbolVariable.java:30
[ref-symbol-value]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/symbol/SymbolVariable.java:46
[ref-ty]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/symbol/type/Ty.java:6
[ref-ty-array]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/symbol/type/TyArray.java:43
[ref-ty-compatible]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/symbol/type/SymbolType.java:48
[ref-ty-ptr]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/symbol/type/TyPtr.java:3
[ref-ty-ptr-size]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/frontend/symbol/type/TyPtr.java:21
[ref-use]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/Use.java:9
[ref-user-operand]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/User.java:21
[ref-user-replace]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/User.java:38
[ref-value]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/Value.java:13
[ref-value-uses]: C:/Users/96330/IdeaProjects/BUAA_SysY_Compiler_2024/src/top/swkfk/compiler/llvm/value/Value.java:16
