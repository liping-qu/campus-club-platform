#!/usr/bin/env bash
# P5 缓存端到端验证
# 证据有两类：① 应用日志里 SQL 的实际执行次数；② Redis 里 key 的存在与版本号
# 前置：Redis 在 6380；后端以 dev profile 启动（默认），SQL 打到 app-p5.log
B=http://127.0.0.1:8090
LOG=/d/app-kaifa/backend/app-p5.log
BODY="D:/app-kaifa/backend/.verify-body.json"
PASS=0; FAIL=0

chk() { if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf "  [PASS] %-40s %s\n" "$1" "$3"
        else FAIL=$((FAIL+1)); printf "  [FAIL] %-40s 期望 %s 实际 %s\n" "$1" "$2" "$3"; fi; }

# 详情语句独有 LEFT JOIN sys_user；列表语句用分页插件的 COUNT 当标记
# （count 为 0 时 MP 不会再发主查询，所以不能用 ORDER BY 那行做标记）
detailSql() { grep -ac "LEFT JOIN sys_user u ON u.id = a.created_by" "$LOG" 2>/dev/null || true; }
listSql()   { grep -ac "SELECT COUNT(\*) AS total FROM activity" "$LOG" 2>/dev/null || true; }
field()     { grep -o "\"$1\":[0-9]*" "$BODY" | head -1 | cut -d: -f2; }

# 通过 /dev/tcp 直接说 Redis 协议（内联命令形式），避免依赖 redis-cli
redisDo() {
  local out=""
  exec 3<>/dev/tcp/127.0.0.1/6380 2>/dev/null || { printf "(redis 不可达)"; return 1; }
  printf '%s\r\n' "$1" >&3
  while IFS= read -r -t 1 line <&3; do out+="$line"$'\n'; done
  exec 3<&-; exec 3>&-
  printf '%s' "$out"
}

# 整数回复（:N）
rInt() { redisDo "$1" | tr -d '\r\n:' ; }
# 批量字符串回复（$len\r\nvalue\r\n），取最后一个非空行
rStr() { redisDo "$1" | tr -d '\r' | grep -v '^\$' | grep -v '^\*' | tail -1 | tr -d '\n'; }

echo "== 0. 准备：清空缓存，确认服务可用 =="
redisDo "FLUSHALL" >/dev/null
echo "  Redis keys=$(rInt 'DBSIZE')"
for i in $(seq 1 30); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "$B/actuator/health")" = "200" ] && { echo "  服务 UP"; break; }
  sleep 2
done

echo "== 1. 详情：第一次回源，第二次命中 =="
a1=$(detailSql); curl -s -o "$BODY" "$B/api/activities/1"; a2=$(detailSql)
chk "第一次查库" "$((a1+1))" "$a2"
j1=$(field joinedCount); echo "  joinedCount=$j1"
echo "  Redis 中的详情 key: $(redisDo 'KEYS club:activity:detail:*' | tr -d '\r' | grep -c 'club:activity:detail' ) 个"
curl -s -o "$BODY" "$B/api/activities/1"; a3=$(detailSql)
chk "第二次不查库" "$a2" "$a3"
chk "两次返回一致" "$j1" "$(field joinedCount)"
echo "  详情 key 剩余 TTL: $(rInt 'TTL club:activity:detail:1') 秒（基础 600 ± 抖动 30）"

echo "== 2. 列表：第二次命中 =="
b1=$(listSql); curl -s -o /dev/null "$B/api/activities?page=1&size=5"; b2=$(listSql)
curl -s -o /dev/null "$B/api/activities?page=1&size=5"; b3=$(listSql)
echo "  第一次 +$((b2-b1)) 条，第二次 +$((b3-b2)) 条"
chk "列表第二次不查库" "$b2" "$b3"

echo "== 3. 换查询条件：指纹不同，必须重新查库 =="
# ASCII 关键词：Git Bash 下 curl 不接受未编码的中文 URL，那是脚本限制不是缓存问题
curl -s -o /dev/null "$B/api/activities?page=1&size=5&keyword=zzz"; b4=$(listSql)
chk "新条件重新查库" "$((b3+1))" "$b4"
curl -s -o /dev/null "$B/api/activities?page=1&size=5&keyword=zzz"; b5=$(listSql)
chk "同条件再来一次命中" "$b4" "$b5"
echo "  列表 key 数: $(redisDo 'KEYS club:activity:list:v*' | tr -d '\r' | grep -c 'club:activity:list:v')"

echo "== 4. 报名后缓存被清掉（最关键的一条）=="
curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
     -d '{"username":"student1","password":"123456"}' -o "$BODY"
T=$(grep -o '"token":"[^"]*"' "$BODY" | cut -d'"' -f4)

# 先取消一次，保证脚本可重复执行（本来就没报名时返回业务错误，忽略）
curl -s -o /dev/null -X DELETE "$B/api/activities/3/registrations/me" -H "Authorization: Bearer $T"

curl -s -o "$BODY" "$B/api/activities/3"; c1=$(detailSql); jb=$(field joinedCount)
echo "  报名前 joinedCount=$jb"
chk "详情已缓存" "1" "$(rInt 'EXISTS club:activity:detail:3')"
ver1=$(rStr 'GET club:activity:list:ver')

reg=$(curl -s -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/3/registrations" \
      -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{}')
echo "  报名 HTTP $reg code=$(grep -o '\"code\":[0-9]*' "$BODY" | head -1 | cut -d: -f2)"
chk "报名成功" 200 "$reg"
sleep 1   # 等事务提交后的失效回调

chk "详情 key 已被删除" "0" "$(rInt 'EXISTS club:activity:detail:3')"
ver2=$(rStr 'GET club:activity:list:ver')
echo "  列表版本号: ${ver1:-1} -> ${ver2:-1}"

curl -s -o "$BODY" "$B/api/activities/3"; c2=$(detailSql)
chk "下次读取重新查库" "$((c1+1))" "$c2"
ja=$(field joinedCount); echo "  报名后 joinedCount=$ja"
chk "名额已增加" "$((jb+1))" "$ja"

echo "== 5. 不存在的 id 只穿透一次 =="
d1=$(detailSql); curl -s -o /dev/null "$B/api/activities/999999"; d2=$(detailSql)
curl -s -o /dev/null "$B/api/activities/999999"; d3=$(detailSql)
echo "  第一次 +$((d2-d1))，第二次 +$((d3-d2))"
chk "第二次被空值标记挡住" "$d2" "$d3"

echo
echo "结果: PASS=$PASS FAIL=$FAIL"
