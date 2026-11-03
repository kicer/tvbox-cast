#!/bin/sh
# 没有 Android SDK 时的离线自检：
#   用 .check/stubs 里的最小 android stub 编译一次 app 源码，
#   只验证 Java 语法与类型，不产出 APK（真正出包走 GitHub Actions）。
#
# 用法：sh check.sh
set -e

cd "$(dirname "$0")" || exit 1

# 找一个能用的 JDK（javac 需要真实 JDK，不是 /usr/bin/javac 占位符）
if [ -z "$JAVA_HOME" ]; then
  for c in \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" \
    "/opt/homebrew/opt/openjdk@17"
  do
    if [ -x "$c/bin/javac" ]; then
      JAVA_HOME="$c"
      break
    fi
  done
fi

if [ -z "$JAVA_HOME" ]; then
  echo "找不到 JDK，请设置 JAVA_HOME" >&2
  exit 1
fi

export JAVA_HOME

mkdir -p .check/out
rm -rf .check/out/*
find .check/stubs app/src/main/java -name '*.java' > .check/files.txt

javac -source 8 -target 8 -nowarn -d .check/out @.check/files.txt
echo "OK: Java 语法/类型自检通过（未产出 APK）"
