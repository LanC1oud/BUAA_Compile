#!/usr/bin/env bash
# ============================================================
#  run.sh —— 一键入口：需要时先编译 src/ + ui/，然后启动前端界面。
#
#    ./run.sh              需要时先编译，然后打开界面
#    ./run.sh --build-only 需要时先编译，不启动界面
#    ./run.sh --force      强制重新编译，然后打开界面
#    ./run.sh --help       显示帮助
#
#  「需要编译」= 产物或标记文件缺失，或有 *.java 比标记文件新（即上次成功
#  编译之后源码改过）。标记文件只在 javac 成功后写入，且每次编译前先删除，
#  所以编译失败后下次一定会重试，不会拿旧产物冒充最新。
#
#  需要 JDK 17+：优先 $JAVA_HOME，其次 PATH 上的 javac，最后用 macOS 的
#  /usr/libexec/java_home 兜底；版本不够会直接报错并提示怎么办。
#
#  保持 bash 3.2 兼容（macOS 自带 bash 没有 mapfile；空数组要防 set -u）。
# ============================================================
set -euo pipefail

cd "$(dirname "$0")"

OUT_DIR="build/classes"
STAMP="$OUT_DIR/.stamp"
MAIN_CLASS="compilerui.gui.CompilerUi"
MAIN_CLASS_FILE="$OUT_DIR/${MAIN_CLASS//.//}.class"
MIN_MAJOR=17

FORCE=0
BUILD_ONLY=0
GUI_ARGS=()

usage() {
  cat <<'EOF'
用法: ./run.sh [选项]

  (无选项)      需要时先编译，然后打开界面
  --build-only  需要时先编译，不启动界面
  --force       强制重新编译，然后打开界面
  -h, --help    显示本帮助

需要 JDK 17+：优先 $JAVA_HOME，其次 PATH 上的 javac。
EOF
}

for arg in "$@"; do
  case "$arg" in
    --build-only) BUILD_ONLY=1 ;;
    --force|--rebuild) FORCE=1 ;;
    -h|--help)
      usage
      exit 0
      ;;
    *) GUI_ARGS+=("$arg") ;;
  esac
done

javac_major() { # $1 = javac 路径；输出主版本号，失败则退出非零
  local out
  out="$("$1" -version 2>&1)" || return 1
  printf '%s\n' "$out" | sed -n 's/^[^0-9]*\([0-9][0-9]*\)\..*/\1/p' | head -n 1
}

find_javac() {
  local home
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    printf '%s\n' "$JAVA_HOME/bin/javac"
    return 0
  fi
  if command -v javac >/dev/null 2>&1; then
    command -v javac
    return 0
  fi
  if [ -x /usr/libexec/java_home ]; then
    home="$(/usr/libexec/java_home 2>/dev/null || true)"
    if [ -n "$home" ] && [ -x "$home/bin/javac" ]; then
      printf '%s\n' "$home/bin/javac"
      return 0
    fi
  fi
  return 1
}

JAVAC="$(find_javac || true)"
if [ -z "$JAVAC" ]; then
  echo "[run] 找不到 javac。请安装 JDK $MIN_MAJOR+，或把 JAVA_HOME 指向它。" >&2
  exit 1
fi

MAJOR="$(javac_major "$JAVAC" || true)"
case "$MAJOR" in
  ''|*[!0-9]*)
    echo "[run] 无法识别 javac 版本：$JAVAC" >&2
    exit 1
    ;;
esac
if [ "$MAJOR" -lt "$MIN_MAJOR" ]; then
  echo "[run] $JAVAC 是 JDK $MAJOR，界面需要 $MIN_MAJOR+。" >&2
  echo "[run] 请把 JAVA_HOME 指向一个 JDK $MIN_MAJOR+ 后重试。" >&2
  exit 1
fi
JAVA="$(dirname "$JAVAC")/java"

# ---------- 判断是否需要编译 ----------
needs_build() {
  local newer
  if [ "$FORCE" -eq 1 ]; then
    return 0
  fi
  if [ ! -d "$OUT_DIR" ] || [ ! -f "$STAMP" ]; then
    return 0
  fi
  # 有 .java 比标记文件新 -> 需要重编；find 出错时也重编（宁可多编一次）。
  newer="$(find src ui -name '*.java' -newer "$STAMP" -print -quit 2>/dev/null)" || return 0
  [ -n "$newer" ]
}

do_build() {
  local sources=() file
  while IFS= read -r file; do
    sources+=("$file")
  done < <(find src ui -name '*.java' | sort)

  if [ "${#sources[@]}" -eq 0 ]; then
    echo "[run] 没在 src/ 或 ui/ 下找到 .java 源文件（当前目录：$PWD）" >&2
    return 1
  fi

  mkdir -p "$OUT_DIR"
  rm -f "$STAMP"

  echo "[run] javac ${#sources[@]} 个文件 → $OUT_DIR  ($("$JAVAC" -version 2>&1))"
  if ! "$JAVAC" -encoding UTF-8 -d "$OUT_DIR" "${sources[@]}"; then
    return 1
  fi

  : >"$STAMP"
  echo "[run] 编译完成"
}

if needs_build; then
  do_build || {
    echo "[run] 编译失败，见上方 javac 输出。" >&2
    exit 1
  }
else
  echo "[run] 产物已是最新，跳过编译。"
fi

if [ "$BUILD_ONLY" -eq 1 ]; then
  echo "[run] 只编译，不启动界面。产物：$OUT_DIR"
  exit 0
fi

if [ ! -f "$MAIN_CLASS_FILE" ]; then
  echo "[run] 找不到 $MAIN_CLASS_FILE，产物不完整。" >&2
  echo "[run] 试试 ./run.sh --force" >&2
  exit 1
fi

echo "[run] 启动 $MAIN_CLASS ..."
exec "$JAVA" -Dfile.encoding=UTF-8 -cp "$OUT_DIR" "$MAIN_CLASS" ${GUI_ARGS[@]+"${GUI_ARGS[@]}"}
