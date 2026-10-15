#!/usr/bin/env bash
# 启动编译器前端界面。工作目录固定为项目根，方便「载入示例」和相对路径导出。
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -d build/classes ]]; then
  echo "尚未编译，先执行 ./build.sh" >&2
  exit 1
fi

exec java -Dfile.encoding=UTF-8 -cp build/classes compilerui.gui.CompilerUi "$@"
