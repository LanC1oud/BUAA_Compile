# LLVM 12 本地测试运行时

`libsysy.c`、`libsysy.h` 原样复制自课程下载目录 `C:/Users/96330/Downloads/libsysy/`，未修改实现；原下载文件保留。`llvm12/libsysy.ll` 用 Clang 12.0.0 为本机 Windows x86_64/MSVC 重新生成，采用 i8*/i32* 等带类型指针，避免原下载 IR 使用 Clang 22 不透明指针造成版本冲突。

生成信息、完整命令和 C/H 的 SHA-256 在 [build.json](llvm12/build.json)。六个接口为 `get_int():i32`、`get_char():i8`、`get_string(i8*,i32):void`、`put_int(i32):void`、`put_char(i8):void` 和 `put_string(i8*):void`。

本机重建和验收：

```powershell
python tools/setup-llvm12.py
python tools/build-runtime.py
python tests/run-llvm-tests.py
```

安装脚本从 conda-forge 下载固定版本包，仅写入项目 build 目录，校验包元数据摘要，不修改 PATH。运行时构建脚本直接调用 clang -cc1，以避免旧 Clang 驱动探测 Visual Studio 2026 时崩溃，显式传入本机 MSVC/UCRT 头文件。该脚本记录了本机 SDK 路径；其他环境应调整 include 路径，或在配套 Clang 12 开发环境运行：

```text
clang-12 -S -emit-llvm -O0 -funsigned-char runtime/libsysy.c -o runtime/llvm12/libsysy.ll
```

LLVM 工具位于 `build/llvm12/Library/bin/`。运行时与 lli 必须匹配平台；Linux/macOS 上重新编译 C 文件即可。编译器输出不含主机 target triple，也不依赖这份本地运行时即可完成代码生成；评测提交包不携带 runtime 或 LLVM 二进制。

执行回归完整覆盖六接口，包括 get_string 的 0/1/正常容量、截断、CRLF、换行与 NUL 行为。运行时输入只在 lli 执行时提供，Java 编译过程无需 input.txt。
