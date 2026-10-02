#!/usr/bin/env bash
# 构建 merge/dm 下的全部 dm 插件为 tgz（df 插件商店可装的本地包）。
# 用法: scripts/build-merge-plugins.sh [outdir]
# 默认输出到 out/merge-plugins/。
#
# 要点：
# - 插件间存在 file: 相对路径依赖（如 dsh-android-file-open -> ../dsh-android-bridge、
#   dsh-android-linux-env -> ../../dsh-shell-termux）。被依赖的包必须先 build 出 lib，
#   否则引用方 npm ci 链接到的是个空壳。构建顺序因此固定为：被依赖者在前。
# - 单个包失败不中断整体，最后汇总；任一包出 tgz 失败会以非零退出。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-$ROOT/out/merge-plugins}"
mkdir -p "$OUT"
# 转成绝对路径：子 shell 会 cd 进各包目录，相对 OUT 会飘到包里
OUT="$(cd "$OUT" && pwd)"

# 顺序即依赖序（被依赖者在前）
PACKAGES=(
  "$ROOT/merge/dm/dsh-shell-termux"
  "$ROOT/merge/dm/plugins/dsh-android-bridge"
  "$ROOT/merge/dm/plugins/dsh-android-browser"
  "$ROOT/merge/dm/plugins/dsh-android-manage"
  "$ROOT/merge/dm/plugins/dsh-android-vdisplay"
  "$ROOT/merge/dm/plugins/dsh-model-capability"
  "$ROOT/merge/dm/plugins/dsh-android-file-open"
  "$ROOT/merge/dm/plugins/dsh-android-linux-env"
  "$ROOT/merge/dm/vendor/dsh-undo-savepoint"
  "$ROOT/merge/dm/vendor/dshmarketplace-plugin"
  "$ROOT/merge/dm/dsh-client-ui-responsive"
  "$ROOT/merge/dm/dsh-host-web-compat"
)

built=0
failed=0

for pkg in "${PACKAGES[@]}"; do
  if [ ! -f "$pkg/package.json" ]; then
    echo "[skip] 无 package.json: $pkg"
    continue
  fi
  name="$(node -e "console.log(require(process.argv[1]).name)" "$pkg/package.json" 2>/dev/null || echo "$(basename "$pkg")")"
  echo "==> build $name"
  (
    cd "$pkg"
    if [ -f package-lock.json ]; then
      npm ci --no-audit --no-fund --ignore-scripts || echo "    [warn] npm ci failed, continue"
    fi
    if node -e "const p=require(process.argv[1]); process.exit(p.scripts && p.scripts.build ? 0 : 1)" ./package.json; then
      npm run build || echo "    [warn] npm run build failed"
    fi
    tgz="$(npm pack --silent 2>/dev/null | tail -1)"
    if [ -n "$tgz" ] && [ -f "$tgz" ]; then
      mv -f "$tgz" "$OUT/"
      echo "    -> $tgz"
      exit 0
    fi
    echo "    [warn] no tgz produced"
    exit 1
  ) || failed=$((failed + 1))
  built=$((built + 1))
done

echo
echo "完成: $built 个包, $((built - failed)) 个成功, $failed 个失败。产物在 $OUT"
ls -la "$OUT"
[ "$failed" -eq 0 ]
