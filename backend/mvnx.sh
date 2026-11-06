#!/bin/sh
# 本地开发用的 maven 启动器。
#
# 背景：这台机器上没有全局 mvn，maven 只存在于 ~/.m2/wrapper/dists 下；
# 而 Git Bash 无法直接执行 mvn.cmd（那是 cmd 脚本），
# 所以这里用 java 直接拉起 maven 的 classworlds 启动器，效果与 mvn 完全等价。
#
# 用法：./mvnx.sh -q compile
#      ./mvnx.sh test
#      ./mvnx.sh spring-boot:run
set -e

if [ -z "$MAVEN_HOME" ]; then
  conf=$(ls -d "$HOME"/.m2/wrapper/dists/*/*/apache-maven-*/bin/m2.conf 2>/dev/null | head -1)
  if [ -z "$conf" ]; then
    echo "找不到 maven：请设置 MAVEN_HOME，或先执行一次 mvnw 让它下载 wrapper 发行包" >&2
    exit 1
  fi
  MAVEN_HOME=$(cd "$(dirname "$conf")/.." && pwd)
fi

# java 是 Windows 可执行文件，路径必须转成 Windows 形式，否则它看不懂 /c/Users/...
case "$MAVEN_HOME" in
  /?/*) MAVEN_HOME="$(cygpath -w "$MAVEN_HOME")" ;;
esac
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
case "$PROJECT_DIR" in
  /?/*) PROJECT_DIR="$(cygpath -w "$PROJECT_DIR")" ;;
esac

exec java \
  -classpath "$MAVEN_HOME\\boot\\plexus-classworlds-2.6.0.jar" \
  "-Dclassworlds.conf=$MAVEN_HOME\\bin\\m2.conf" \
  "-Dmaven.home=$MAVEN_HOME" \
  "-Dmaven.multiModuleProjectDirectory=$PROJECT_DIR" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
