#!/usr/bin/env bash
# 一键启动本地教学环境（用户态，无需 root / docker）。
# 前置：node 20+；若尚未安装用户态 JDK/Maven/PostgreSQL，本脚本会自动下载到 $HOME/tools、$HOME/pglocal。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HOME/tools"; PGLOCAL="$HOME/pglocal"; PGDATA="$HOME/pgdata"
JDK_DIR="$TOOLS/jdk-17.0.20.1+1"
MVN_DIR="$TOOLS/apache-maven-3.9.9"
PGBIN="$PGLOCAL/usr/lib/postgresql/15/bin"
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$MVN_DIR/bin:$PATH"
export LD_LIBRARY_PATH="$PGLOCAL/usr/lib/aarch64-linux-gnu:${LD_LIBRARY_PATH:-}"

echo "==> [1/4] 启动 PostgreSQL（端口 5432，库 matchingdb，用户 match，trust）"
if [ ! -x "$PGBIN/postgres" ]; then
  echo "未找到用户态 PostgreSQL，请先按 README 安装（需要 Debian/Ubuntu 环境）。"; exit 1
fi
if ! "$PGBIN/pg_ctl" -D "$PGDATA" status >/dev/null 2>&1; then
  "$PGBIN/pg_ctl" -D "$PGDATA" -l "$PGDATA/server.log" start
  sleep 2
fi
"$PGBIN/psql" -h /tmp -U match -d matchingdb -tAc "SELECT 1" >/dev/null 2>&1 || \
  "$PGBIN/psql" -h /tmp -U match -d postgres -c "CREATE DATABASE matchingdb;"

echo "==> [2/4] 构建并启动 Spring Boot（8080）"
( cd "$ROOT/backend" && mvn -q -DskipTests package )
nohup java -jar "$ROOT/backend/target/limit-matching-1.0.0.jar" > /tmp/matching-backend.log 2>&1 &
echo $! > /tmp/matching-backend.pid
sleep 1
echo "后端日志：/tmp/matching-backend.log  PID：$(cat /tmp/matching-backend.pid)"

echo "==> [3/4] 安装前端依赖并启动 Vite（5173，/api 代理到 8080）"
( cd "$ROOT/frontend" && [ -d node_modules ] || npm install --no-audit --no-fund )
( cd "$ROOT/frontend" && nohup npm run dev > /tmp/matching-frontend.log 2>&1 & echo $! > /tmp/matching-frontend.pid )
sleep 3

echo "==> [4/4] 健康检查"
curl -s http://localhost:8080/api/settings && echo
echo
echo "完成："
echo "  前端页面  http://localhost:5173"
echo "  后端 API  http://localhost:8080/api"
echo "  录制脚本  cd frontend && node replay.mjs --out recording.json"
echo "停止：kill \$(cat /tmp/matching-backend.pid) \$(cat /tmp/matching-frontend.pid)"
