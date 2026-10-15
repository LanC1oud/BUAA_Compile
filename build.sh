#!/usr/bin/env bash
# 编译「编译器源码 (src/) + 前端界面源码 (ui/)」到 build/classes。
# 只读 src/，不会修改它；产物全部落在 build/ 下。
set -euo pipefail

cd "$(dirname "$0")"

OUT_DIR="build/classes"
mkdir -p "$OUT_DIR"

# 用 while-read 而不是 mapfile：macOS 自带 bash 3.2 没有 mapfile。
SOURCES=()
while IFS= read -r file; do
  SOURCES+=("$file")
done < <(find src ui -name '*.java' | sort)

echo "==> javac ${#SOURCES[@]} 个文件 → $OUT_DIR"
javac -encoding UTF-8 -d "$OUT_DIR" "${SOURCES[@]}"
echo "==> 编译完成"
