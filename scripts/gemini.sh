#!/usr/bin/env bash
# Gemini API 便捷启动脚本
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 如果存在 .env 文件，导出变量
if [ -f "$ROOT/.env" ]; then
  # 避免 set -e 导致空变量出错
  set -a
  source "$ROOT/.env"
  set +a
fi

exec python3 "$ROOT/scripts/gemini_api.py" "$@"
