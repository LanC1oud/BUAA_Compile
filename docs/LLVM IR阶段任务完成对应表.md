# LLVM IR 阶段任务与代码对应记录

对应清单：[LLVM IR阶段TODO](LLVM%20IR阶段TODO.md)。记录日期：2026-11-02。

逐项覆盖清单的 **68 项**：P0～P11 为已实现及验证的 LLVM 阶段任务；MIPS.1～MIPS.5 为已落实的 IR 接口约束，MIPS 汇编生成留待下一阶段。下面的行号由 `tools/write-llvm-report.py` 从实际源码生成。

## 备份与回滚

开始修改前的工作区快照为 `backups/before-llvm-20261101-234349/workspace.zip`（162 个文件），同时保存 SHA-256 清单、原 git HEAD 与未提交状态。备份保留当时未提交内容；原有文件删除状态也保持，不创建或重置 Git 提交。

备份排除 `.git`、`.idea`、构建目录 `build/out`、备份目录及 Python 缓存。所有源码、UI、文档、配置和测试均包含。先运行下面命令查看回滚提示；加 `--restore` 才真正回滚。回滚会移除源码等受管目录中新增加的文件，再还原快照。

```powershell
python backups/before-llvm-20261101-234349/restore.py
python backups/before-llvm-20261101-234349/restore.py --restore
```

## 实现选择

新增 `src/llvm/` 独立类型和值对象模型。`SemanticInfo` 保存前端类型和定义/调用绑定，IR 地址映射按符号身份组织。局部对象使用 entry alloca 与声明位置 store，数组实参传 T*；static 提升为模块存储。char 算术每步在 i8 中回绕，条件短路直接构造分支。LLVM 文本使用带类型指针，字符串常量使用显式 i8 聚合。

关系/相等比较遵从现有前端的同类型规则，先得到 i1，再将 0/1 扩展为 SemanticInfo 规定的源类型；逻辑非始终产生 i32，条件分支始终用 i1。现有前端允许非 void 函数使用裸 return，IR 对该情况返回同类型 0；此次没有改变其既有诊断规则。

## P0

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P0.1 固定工具版本。 | 固定 LLVM/Clang 12.0.0；仅安装至 build/llvm12，四工具版本在回归入口强制检查。 | [`tools/setup-llvm12.py:11`](../tools/setup-llvm12.py#L11)<br>[`tests/run-llvm-tests.py:33`](../tests/run-llvm-tests.py#L33) |
| P0.2 解决本地运行时版本冲突。 | 保留课程运行时原始 C/H 副本；用 Clang 12 生成 typed-pointer IR，记录命令、源文件校验值。 | [`tools/build-runtime.py:17`](../tools/build-runtime.py#L17)<br>[`runtime/llvm12/build.json:27`](../runtime/llvm12/build.json#L27) |
| P0.3 跑通链接。 | 每个用例经汇编、opt 验证、运行时链接和 lli 执行；six-runtime 覆盖六接口。 | [`tests/run-llvm-tests.py:53`](../tests/run-llvm-tests.py#L53)<br>[`tests/llvm_cases.py:22`](../tests/llvm_cases.py#L22) |
| P0.4 明确两种输入的归属。 | 编译器只词法读取源文件；测试运行器仅在 lli 阶段传入运行时 stdin。 | [`src/utils/Controller.java:32`](../src/utils/Controller.java#L32)<br>[`tests/run-llvm-tests.py:54`](../tests/run-llvm-tests.py#L54) |

## P1

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P1.1 提供表达式类型查询。 | SemanticInfo.typeOf 查询本次语义分析的类型；数字、表达式与条件类型由 Traverser.put 保存。 | [`src/frontend/SemanticInfo.java:25`](../src/frontend/SemanticInfo.java#L25)<br>[`src/frontend/Traverser.java:880`](../src/frontend/Traverser.java#L880) |
| P1.2 保存定义绑定。 | 定义及形参统一写入 identity map，作用域退出后仍保留绑定。 | [`src/frontend/Traverser.java:94`](../src/frontend/Traverser.java#L94)<br>[`src/frontend/Traverser.java:109`](../src/frontend/Traverser.java#L109)<br>[`src/frontend/Traverser.java:173`](../src/frontend/Traverser.java#L173) |
| P1.3 保存引用绑定。 | 函数调用保存 SymbolFunction；LVal 沿用前端已绑定的符号对象。 | [`src/frontend/Traverser.java:784`](../src/frontend/Traverser.java#L784)<br>[`src/llvm/IrGenerator.java:269`](../src/llvm/IrGenerator.java#L269)<br>[`src/llvm/IrGenerator.java:187`](../src/llvm/IrGenerator.java#L187) |
| P1.4 正确处理 main。 | IR 独立注册 i32 main，不改变前端符号表输出。 | [`src/llvm/IrGenerator.java:59`](../src/llvm/IrGenerator.java#L59) |
| P1.5 区分初值与常量。 | 常量值字段仅供初始化使用；可变对象每次取值通过地址 load。 | [`src/llvm/IrGenerator.java:174`](../src/llvm/IrGenerator.java#L174)<br>[`src/llvm/IrGenerator.java:212`](../src/llvm/IrGenerator.java#L212) |
| P1.6 固定编译任务的状态边界。 | 每次 CLI/UI 编译清空前端状态，每次新建 IrGenerator，其模块、映射、字符串池、控制栈和计数器均独立。 | [`src/utils/Controller.java:24`](../src/utils/Controller.java#L24)<br>[`ui/compilerui/pipeline/Pipeline.java:126`](../ui/compilerui/pipeline/Pipeline.java#L126)<br>[`src/llvm/IrGenerator.java:24`](../src/llvm/IrGenerator.java#L24) |

## P2

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P2.1 类型模型。 | 独立 IR 类型包含整数、void、label、数组、带类型指针和函数签名；区分对象与形参数组。 | [`src/llvm/type/IrType.java:9`](../src/llvm/type/IrType.java#L9)<br>[`src/llvm/type/IrType.java:38`](../src/llvm/type/IrType.java#L38) |
| P2.2 Value 和容器。 | Value/Constant/GlobalVariable/Function/Argument/BasicBlock/Instruction 保留结构、类型及父容器。 | [`src/llvm/value/Value.java:9`](../src/llvm/value/Value.java#L9)<br>[`src/llvm/value/Function.java:8`](../src/llvm/value/Function.java#L8)<br>[`src/llvm/instruction/Instruction.java:33`](../src/llvm/instruction/Instruction.java#L33) |
| P2.3 最小指令集合。 | 实现清单全部基础指令；额外提供 unreachable 合法终结无后继路径。 | [`src/llvm/instruction/Instruction.java:11`](../src/llvm/instruction/Instruction.java#L11)<br>[`src/llvm/IrBuilder.java:86`](../src/llvm/IrBuilder.java#L86) |
| P2.4 构建约束。 | Builder 管理函数和插入点；块拒绝 terminator 后追加，alloca 单独插入 entry 前部。 | [`src/llvm/IrBuilder.java:22`](../src/llvm/IrBuilder.java#L22)<br>[`src/llvm/IrBuilder.java:104`](../src/llvm/IrBuilder.java#L104)<br>[`src/llvm/value/BasicBlock.java:30`](../src/llvm/value/BasicBlock.java#L30) |
| P2.5 命名和打印。 | 采用 argN/vN/bbN 命名；统一 printer 输出，无结果指令不打印赋值左侧。 | [`src/llvm/value/Function.java:37`](../src/llvm/value/Function.java#L37)<br>[`src/llvm/IrPrinter.java:64`](../src/llvm/IrPrinter.java#L64) |
| P2.6 操作数维护接口。 | 操作数均为 Value 对象；双向 Use 记录支持替换、全量替换及删除时解绑。 | [`src/llvm/value/Value.java:43`](../src/llvm/value/Value.java#L43)<br>[`src/llvm/instruction/Instruction.java:64`](../src/llvm/instruction/Instruction.java#L64)<br>[`src/llvm/value/BasicBlock.java:47`](../src/llvm/value/BasicBlock.java#L47) |

## P3

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P3.1 增加 LLVM 阶段开关和输出文件名。 | 新增 Hw.Llvm，默认到 LLVM 阶段，目标为 llvm_ir.txt。 | [`src/utils/HomeworkConfig.java:5`](../src/utils/HomeworkConfig.java#L5) |
| P3.2 在语义分析之后接入生成器。 | 前端无错后才建立模块并写 IR；任何已记录错误输出 error.txt 并返回。 | [`src/utils/Controller.java:88`](../src/utils/Controller.java#L88)<br>[`src/utils/Controller.java:92`](../src/utils/Controller.java#L92) |
| P3.3 支持 `int main(){return 0;}`。 | main 生成 i32 返回；正常落空的 void 函数补 ret void。 | [`src/llvm/IrGenerator.java:106`](../src/llvm/IrGenerator.java#L106)<br>[`tests/llvm_cases.py:4`](../tests/llvm_cases.py#L4) |
| P3.4 固定输出与提交配置。 | 提交配置 LLVM，CLI 明确 UTF-8；源语言输入与目标程序执行完全分开。 | [`src/config.json:3`](../src/config.json#L3)<br>[`src/utils/Controller.java:39`](../src/utils/Controller.java#L39) |

## P4

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P4.1 全局标量。 | 全局对象使用前端常量初值，缺省零值沿语义结果；仅 const 对象打印 constant。 | [`src/llvm/IrGenerator.java:174`](../src/llvm/IrGenerator.java#L174)<br>[`src/llvm/IrGenerator.java:132`](../src/llvm/IrGenerator.java#L132)<br>[`src/llvm/IrPrinter.java:18`](../src/llvm/IrPrinter.java#L18) |
| P4.2 普通局部标量。 | 固定大小 alloca 放 entry；初始化 store 留在声明位置，未初始化自动对象不补零。 | [`src/llvm/IrBuilder.java:54`](../src/llvm/IrBuilder.java#L54)<br>[`src/llvm/IrGenerator.java:140`](../src/llvm/IrGenerator.java#L140)<br>[`src/llvm/IrGenerator.java:160`](../src/llvm/IrGenerator.java#L160) |
| P4.3 分离地址与值。 | emitAddress 独立计算地址，emitValue 按类型 load 或数组衰减。 | [`src/llvm/IrGenerator.java:187`](../src/llvm/IrGenerator.java#L187)<br>[`src/llvm/IrGenerator.java:205`](../src/llvm/IrGenerator.java#L205) |
| P4.4 表达式递归翻译。 | 按现有 left/op/right AST 递归发射指令，覆盖括号、算术、一元、调用；数字保留完整 i32 位模式以支持 INT_MIN。 | [`src/llvm/IrGenerator.java:198`](../src/llvm/IrGenerator.java#L198)<br>[`src/frontend/ast/expression/Number.java:29`](../src/frontend/ast/expression/Number.java#L29) |
| P4.5 赋值和表达式语句。 | 赋值保存到地址，空语句不发指令，表达式语句保留全部求值副作用。 | [`src/llvm/IrGenerator.java:292`](../src/llvm/IrGenerator.java#L292) |

## P5

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P5.1 固定数值表示。 | int→i32，char→i8；常量 i8 归一化到 0..255。 | [`src/llvm/type/IrType.java:42`](../src/llvm/type/IrType.java#L42)<br>[`src/llvm/value/Constant.java:17`](../src/llvm/value/Constant.java#L17) |
| P5.2 显式转换。 | 按位宽用 zext/trunc；同类型转换直接返回原值。 | [`src/llvm/IrBuilder.java:78`](../src/llvm/IrBuilder.java#L78)<br>[`src/llvm/IrGenerator.java:224`](../src/llvm/IrGenerator.java#L224) |
| P5.3 逐步回绕。 | char 的每个算术节点直接产生 i8 结果，不使用 nsw/nuw；一元负号同样在 i8 计算。 | [`src/llvm/IrGenerator.java:263`](../src/llvm/IrGenerator.java#L263)<br>[`src/llvm/IrGenerator.java:230`](../src/llvm/IrGenerator.java#L230) |
| P5.4 除法、取模和比较。 | char 选 udiv/urem/无符号比较，int 选有符号指令；相等比较 eq/ne。 | [`src/llvm/IrGenerator.java:241`](../src/llvm/IrGenerator.java#L241)<br>[`src/llvm/IrGenerator.java:243`](../src/llvm/IrGenerator.java#L243)<br>[`src/llvm/IrGenerator.java:259`](../src/llvm/IrGenerator.java#L259) |
| P5.5 区分布尔内部表示与语言类型。 | icmp 保留 i1 内部结果；! 扩展为 i32；比较的 0/1 值转回 SemanticInfo 指定类型以支持连续比较。 | [`src/llvm/IrGenerator.java:231`](../src/llvm/IrGenerator.java#L231)<br>[`src/llvm/IrGenerator.java:252`](../src/llvm/IrGenerator.java#L252)<br>[`src/llvm/IrGenerator.java:357`](../src/llvm/IrGenerator.java#L357) |
| P5.6 保留严格匹配。 | 不在赋值、call、return 处添加隐式类型转换；前端严格检查，IR 验证器再次核对类型。 | [`src/frontend/Traverser.java:684`](../src/frontend/Traverser.java#L684)<br>[`src/llvm/verify/IrVerifier.java:92`](../src/llvm/verify/IrVerifier.java#L92)<br>[`src/llvm/verify/IrVerifier.java:120`](../src/llvm/verify/IrVerifier.java#L120) |

## P6

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P6.1 函数定义和调用。 | 先登记用户函数对象，再翻译函数体；实际可见性仍由前端判定。 | [`src/llvm/IrGenerator.java:51`](../src/llvm/IrGenerator.java#L51)<br>[`src/llvm/IrGenerator.java:87`](../src/llvm/IrGenerator.java#L87) |
| P6.2 标量形参。 | 标量形参保存到独立 entry 槽；实参与形参保持次序和类型。 | [`src/llvm/IrGenerator.java:91`](../src/llvm/IrGenerator.java#L91)<br>[`src/llvm/IrBuilder.java:93`](../src/llvm/IrBuilder.java#L93) |
| P6.3 call 与 return。 | void call 无结果；return 终结块；后续源语句停止翻译。 | [`src/llvm/IrBuilder.java:93`](../src/llvm/IrBuilder.java#L93)<br>[`src/llvm/IrGenerator.java:292`](../src/llvm/IrGenerator.java#L292)<br>[`src/llvm/IrGenerator.java:283`](../src/llvm/IrGenerator.java#L283) |
| P6.4 运行时签名集中登记。 | 集中声明六个 2026 运行时函数；printf 不作为外部函数。 | [`src/llvm/IrGenerator.java:74`](../src/llvm/IrGenerator.java#L74) |
| P6.5 普通调用路径。 | 运行时与用户函数共享 emitCall/builder.call，精确保留 i8 和指针签名。 | [`src/llvm/IrGenerator.java:269`](../src/llvm/IrGenerator.java#L269)<br>[`src/llvm/IrGenerator.java:74`](../src/llvm/IrGenerator.java#L74) |

## P7

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P7.1 全局/局部一维数组。 | 全局与局部数组用 [N x T]；全局常量初值和局部逐元素动态求值分别处理。 | [`src/llvm/IrGenerator.java:124`](../src/llvm/IrGenerator.java#L124)<br>[`src/llvm/IrGenerator.java:174`](../src/llvm/IrGenerator.java#L174) |
| P7.2 初值归一化。 | 带初始化器的数组未显式列出元素补零；普通局部数组无初值不写入；static/全局由零初值表处理。 | [`src/llvm/IrGenerator.java:161`](../src/llvm/IrGenerator.java#L161)<br>[`src/llvm/IrGenerator.java:174`](../src/llvm/IrGenerator.java#L174) |
| P7.3 区分两种 GEP。 | 对象数组 GEP 使用 0,index；形参指针 GEP 仅 index；结果为地址，读取再 load。 | [`src/llvm/IrBuilder.java:86`](../src/llvm/IrBuilder.java#L86)<br>[`src/llvm/IrGenerator.java:187`](../src/llvm/IrGenerator.java#L187) |
| P7.4 数组传参。 | 数组对象取首元素地址，数组形参直接复用 T*，转传无需额外 load。 | [`src/llvm/IrGenerator.java:94`](../src/llvm/IrGenerator.java#L94)<br>[`src/llvm/IrGenerator.java:208`](../src/llvm/IrGenerator.java#L208) |
| P7.5 字符串初始化。 | 使用词法已解码字符串，按 N 字节写入，短串补零，等长/超长串不额外写终止符。 | [`src/llvm/IrGenerator.java:165`](../src/llvm/IrGenerator.java#L165)<br>[`src/frontend/symbol/FixedArray.java:82`](../src/frontend/symbol/FixedArray.java#L82) |
| P7.6 独立的输出字符串池。 | 独立字符串池生成只读模块数组，追加 NUL、按内容去重；printer 用显式 i8 数组避免文本转义歧义。 | [`src/llvm/IrModule.java:41`](../src/llvm/IrModule.java#L41)<br>[`src/llvm/IrPrinter.java:50`](../src/llvm/IrPrinter.java#L50) |
| P7.7 字符串输入输出。 | get_string/put_string 使用 i8*，换行/容量/NUL/EOF 语义由未修改的课程运行时执行。 | [`src/llvm/IrGenerator.java:77`](../src/llvm/IrGenerator.java#L77)<br>[`runtime/libsysy.c:66`](../runtime/libsysy.c#L66) |

## P8

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P8.1 先求值全部参数。 | printf 在任何格式输出前从左到右求完所有参数，保存 SSA Value。 | [`src/llvm/IrGenerator.java:325`](../src/llvm/IrGenerator.java#L325)<br>[`tests/llvm_cases.py:20`](../tests/llvm_cases.py#L20) |
| P8.2 三种格式。 | %d/%c/%s 分别调用 put_int/put_char/put_string，普通文本进入字符串池。 | [`src/llvm/IrGenerator.java:338`](../src/llvm/IrGenerator.java#L338)<br>[`src/llvm/IrGenerator.java:350`](../src/llvm/IrGenerator.java#L350) |
| P8.3 转义和边界。 | 直接使用词法解码格式串；空串、连续占位、前后占位、换行、反斜杠均有执行用例。 | [`src/llvm/IrGenerator.java:330`](../src/llvm/IrGenerator.java#L330)<br>[`tests/llvm_cases.py:21`](../tests/llvm_cases.py#L21) |

## P9

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P9.1 条件接口。 | emitCond 传入真/假块，&&/|| 只在所需分支执行 RHS。 | [`src/llvm/IrGenerator.java:357`](../src/llvm/IrGenerator.java#L357) |
| P9.2 比较表达式。 | 比较发射 icmp，再将判定值扩展到前端规定的源类型；连续 char/int 比较均有回归。 | [`src/llvm/IrGenerator.java:252`](../src/llvm/IrGenerator.java#L252)<br>[`tests/llvm_cases.py:27`](../tests/llvm_cases.py#L27) |
| P9.3 if/else。 | if 构造分支，仅未终结分支接 merge；双方返回时不建立伪后继。 | [`src/llvm/IrGenerator.java:382`](../src/llvm/IrGenerator.java#L382) |
| P9.4 while。 | while 独立条件入口、body、exit；continue 始终回条件入口，entry 不接回边。 | [`src/llvm/IrGenerator.java:407`](../src/llvm/IrGenerator.java#L407) |
| P9.5 单独维护跳转上下文。 | break 与 continue 独立栈；switch 只压 break 栈，保持外层 while 的 continue。 | [`src/llvm/IrGenerator.java:28`](../src/llvm/IrGenerator.java#L28)<br>[`src/llvm/IrGenerator.java:422`](../src/llvm/IrGenerator.java#L422) |
| P9.6 终结后的语句。 | 块/语句遍历遇终结即停止，IR 基本块与前端词法作用域分离。 | [`src/llvm/IrGenerator.java:281`](../src/llvm/IrGenerator.java#L281)<br>[`src/llvm/IrGenerator.java:293`](../src/llvm/IrGenerator.java#L293) |

## P10

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P10.1 switch 分派只计算选择值一次。 | switch selector 求值一次，比较链引用同一个 Value；无匹配跳 default/exit。 | [`src/llvm/IrGenerator.java:422`](../src/llvm/IrGenerator.java#L422) |
| P10.2 按源顺序贯穿。 | 预建标签块；未终结的 case 按源码标签顺序贯穿，default 可位于任意位置。 | [`src/llvm/IrGenerator.java:444`](../src/llvm/IrGenerator.java#L444)<br>[`src/llvm/IrGenerator.java:448`](../src/llvm/IrGenerator.java#L448) |
| P10.3 嵌套控制目标。 | case 常量按选择值的 i8/i32 类型构建；两套栈正确处理 switch/while 嵌套。 | [`src/llvm/IrGenerator.java:437`](../src/llvm/IrGenerator.java#L437)<br>[`src/llvm/IrGenerator.java:412`](../src/llvm/IrGenerator.java#L412) |
| P10.4 static 存储提升。 | static 提升为 .static.函数.序号.原名 的 internal global；地址以符号身份映射。 | [`src/llvm/IrGenerator.java:129`](../src/llvm/IrGenerator.java#L129)<br>[`src/llvm/IrGenerator.java:132`](../src/llvm/IrGenerator.java#L132) |
| P10.5 static 初始化只做一次。 | static 初值只放全局初始化器，不在函数体重复 store；之后与普通全局共用 load/store。 | [`src/llvm/IrGenerator.java:128`](../src/llvm/IrGenerator.java#L128)<br>[`tests/llvm_cases.py:41`](../tests/llvm_cases.py#L41) |

## P11

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| P11.1 生成结束执行 IR 结构检查。 | 生成后验证名字、父容器、terminator、操作数类型、调用/返回/跳转及 use 双向关系；完整 LLVM 验证另由 opt 执行。 | [`src/llvm/verify/IrVerifier.java:17`](../src/llvm/verify/IrVerifier.java#L17)<br>[`src/llvm/verify/IrVerifier.java:82`](../src/llvm/verify/IrVerifier.java#L82) |
| P11.2 加入独立的 IR 执行回归。 | 39 项执行矩阵：前端无错、汇编、验证、链接、超时控制、stdout 和退出状态比对。 | [`tests/run-llvm-tests.py:45`](../tests/run-llvm-tests.py#L45)<br>[`tests/official-llvm.sysy:1`](../tests/official-llvm.sysy#L1) |
| P11.3 保护既有阶段。 | 74 项语义回归；备份与新代码各前端产物逐字节比较；所有错误样例检查无残留 IR。 | [`tests/SemanticRegression.java:33`](../tests/SemanticRegression.java#L33)<br>[`tests/check-frontend-snapshot.py:26`](../tests/check-frontend-snapshot.py#L26)<br>[`src/utils/Controller.java:26`](../src/utils/Controller.java#L26) |
| P11.4 接入 UI。 | UI 新增 LLVM 枚举、计时/指令统计和导出；语义报错不生成 IR，导出错误文件。 | [`ui/compilerui/pipeline/Pipeline.java:220`](../ui/compilerui/pipeline/Pipeline.java#L220)<br>[`ui/compilerui/model/Stage.java:7`](../ui/compilerui/model/Stage.java#L7)<br>[`ui/compilerui/gui/CompilerUi.java:561`](../ui/compilerui/gui/CompilerUi.java#L561)<br>[`ui/compilerui/pipeline/BatchRunner.java:201`](../ui/compilerui/pipeline/BatchRunner.java#L201) |
| P11.5 验证打包。 | 提交仅含 src 下 Java/JSON，解包后 --release 17 编译验证；测试及工具不再整体忽略。 | [`tools/pack.ps1:12`](../tools/pack.ps1#L12)<br>[`src/config.json:3`](../src/config.json#L3)<br>[`.gitignore:35`](../.gitignore#L35) |
| P11.6 更新项目说明。 | README、设计报告、运行时说明和本对应文档同步更新。 | [`README.md:152`](../README.md#L152)<br>[`docs/编译器设计报告.md:1276`](../docs/编译器设计报告.md#L1276)<br>[`runtime/README.md:1`](../runtime/README.md#L1) |

## MIPS

| 任务 | 完成内容 | 实际代码位置 |
| --- | --- | --- |
| MIPS.1 结构化输入。 | 本阶段已提供 IrGenerator.generate → IrModule；未来 MIPS 接收模块对象，汇编生成尚未实现。 | [`src/llvm/IrGenerator.java:47`](../src/llvm/IrGenerator.java#L47)<br>[`src/llvm/IrModule.java:18`](../src/llvm/IrModule.java#L18) |
| MIPS.2 类型和布局分离。 | 类型无目标指针大小/对齐假设；数组参数直接用 T*，不分配零长度对象。 | [`src/llvm/type/IrType.java:7`](../src/llvm/type/IrType.java#L7)<br>[`src/llvm/IrGenerator.java:94`](../src/llvm/IrGenerator.java#L94) |
| MIPS.3 保留无符号 char 语义。 | 保留 i8、UDIV/UREM、无符号比较、ZEXT/TRUNC，供未来选择 lbu/sb 等 MIPS 指令。 | [`src/llvm/instruction/Instruction.java:11`](../src/llvm/instruction/Instruction.java#L11)<br>[`src/llvm/IrGenerator.java:241`](../src/llvm/IrGenerator.java#L241) |
| MIPS.4 运行时函数可识别。 | 六运行时均为稳定名称的 Function 对象，call 操作数引用对象，供未来 syscall 映射。 | [`src/llvm/IrGenerator.java:74`](../src/llvm/IrGenerator.java#L74)<br>[`src/llvm/IrBuilder.java:93`](../src/llvm/IrBuilder.java#L93) |
| MIPS.5 优化开关。 | 默认关闭优化；预留可注入 pass 列表和优化前文本导出，最终输出为处理后的 IR。 | [`src/utils/Configure.java:9`](../src/utils/Configure.java#L9)<br>[`src/llvm/IrGenerator.java:43`](../src/llvm/IrGenerator.java#L43)<br>[`src/utils/Controller.java:94`](../src/utils/Controller.java#L94)<br>[`src/llvm/IrModule.java:54`](../src/llvm/IrModule.java#L54) |

## 验证记录

- `pwsh -NoProfile -File tests/run-semantic-tests.ps1`：74 项语义回归通过。
- `python tests/run-llvm-tests.py`：39 项实际执行通过；全部经过 LLVM 12.0.0 汇编、opt 验证、课程运行时链接，比较输出与退出状态；单次执行限时 5 秒。
- 同一 JVM 连续编译 A/B/A、失败后再次成功、名称/字符串/static 状态隔离、Use 替换、terminator 后追加拒绝及手工 IR 模型均通过。
- UI 管线无头自检分别使用官方样例与前端错误样例，四阶段结果、计时/指令规模、可复现性和错误格式检查通过。
- `pwsh -NoProfile -File tools/pack.ps1`：91 个 Java/JSON 文件打包成功，根目录含 Compiler.java/config.json，解包后 Java 17 编译通过。

- 与修改前快照比较：92 项输入的词法/语法/符号/诊断产物逐字节一致；26 项合法输入额外通过 LLVM 验证；66 项错误输入均不残留 IR，覆盖错误码 a, b, c, d, e, f, g, h, i, j, k, l, m, n。

旧 `tests/cases.psd1` 有 2 项预期在修改前快照中就已不一致；上述兼容性判断使用真实快照产物，未修改旧预期掩盖差异。74 项 SemanticRegression 与执行回归独立比较其明确预期。

官方综合样例输入 `Az9!bB2?xy7#` 的实际输出：

```text
raw=Az9!bB2?xy7#
upper=AZ9!BB2?XY7#
U=2 L=4 D=3 O=3
score=29
reverse=#7YX?2BB!9ZA
```

工具及报告位置：`build/llvm12/Library/bin/`、`build/llvm12/package.json`、`runtime/llvm12/build.json`、`build/llvm-tests/results.json`、`build/frontend-snapshot/results.json`。工具链安装仅供本地验收，评测提交包无需 LLVM 或 Python。

## 本阶段边界

未实现 MIPS 汇编、mem2reg、phi、支配树和寄存器分配；这些属于后续阶段。Java IR 验证器检查结构及指令类型，完整 LLVM 合法性（包括 SSA 支配关系）由测试中的 llvm-as/opt 检查。兼容运行时为 Windows x86_64/MSVC；其他系统应使用匹配平台的 Clang 12 从同一 C 源码重新生成，编译器本身不写目标 triple。
