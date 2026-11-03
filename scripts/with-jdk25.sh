#!/usr/bin/env bash
# 用项目内 .jdk/temurin-25 运行 Gradle（Minecraft 26.3 需要 Java 25）。
# 用法：bash scripts/with-jdk25.sh build      或   bash scripts/with-jdk25.sh :fabric:test
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JDK_HOME="$ROOT/.jdk/temurin-25/Contents/Home"

if [[ ! -x "$JDK_HOME/bin/java" ]]; then
  echo "未找到 JDK 25：$JDK_HOME" >&2
  echo "请先下载 Temurin 25 到 $ROOT/.jdk/temurin-25（见 PLAN.md 的 M0 步骤）" >&2
  exit 1
fi

export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

# 沙箱限制：Gradle 用户目录放在项目内（~/.gradle 不可写）
export GRADLE_USER_HOME="$ROOT/.gradle-home"

exec "$ROOT/gradlew" -p "$ROOT" "$@"
