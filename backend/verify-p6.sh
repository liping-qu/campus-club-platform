#!/usr/bin/env bash
# P6 可观测性验证：traceId 贯穿、访问日志、缓存指标、actuator 权限
B=http://127.0.0.1:8090
LOG=/d/app-kaifa/backend/app-p6.log
BODY="D:/app-kaifa/backend/.verify-body.json"
PASS=0; FAIL=0

chk() { if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf "  [PASS] %-42s %s\n" "$1" "$3"
        else FAIL=$((FAIL+1)); printf "  [FAIL] %-42s 期望 %s 实际 %s\n" "$1" "$2" "$3"; fi; }

echo "== 0. 健康检查 =="
for i in $(seq 1 30); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "$B/actuator/health")" = "200" ] && { echo "  UP"; break; }
  sleep 2
done

echo "== 1. 每个响应都有 traceId =="
TID=$(curl -s -D - -o /dev/null "$B/api/activities/1" | tr -d '\r' | grep -i "^X-Trace-Id:" | cut -d' ' -f2)
chk "响应头带 X-Trace-Id" "16" "${#TID}"
echo "  traceId=$TID"

echo "== 2. 调用方传的 traceId 被沿用（能串到网关/前端）=="
CUSTOM="trace-from-gateway-0001"
EID=$(curl -s -D - -o /dev/null -H "X-Request-Id: $CUSTOM" "$B/api/activities/2" | tr -d '\r' | grep -i "^X-Trace-Id:" | cut -d' ' -f2)
chk "沿用请求头里的 traceId" "$CUSTOM" "$EID"

echo "== 3. 不合规的 traceId 被丢弃（防日志注入 / 防刷爆日志）=="
# 含空格：合法的 HTTP 头值，但不合我们的白名单
EID2=$(curl -s -D - -o /dev/null -H "X-Request-Id: bad value with spaces" "$B/api/activities/3" | tr -d '\r' | grep -i "^X-Trace-Id:" | cut -d' ' -f2)
chk "含空格的被拒，改用新生成的" "16" "${#EID2}"
# 超长：没有长度上限就能拿日志系统当存储用
LONG=$(printf 'a%.0s' $(seq 1 200))
EID3=$(curl -s -D - -o /dev/null -H "X-Request-Id: $LONG" "$B/api/activities/3" | tr -d '\r' | grep -i "^X-Trace-Id:" | cut -d' ' -f2)
chk "超长的被拒" "16" "${#EID3}"

echo "== 4. 一个 traceId 能 grep 出这次请求的全部日志 =="
# 用报名流程：它既有访问日志，也有业务日志（报名成功）
curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
     -d '{"username":"student1","password":"123456"}' -o "$BODY"
ST=$(grep -o '"token":"[^"]*"' "$BODY" | cut -d'"' -f4)
curl -s -o /dev/null -X DELETE "$B/api/activities/3/registrations/me" -H "Authorization: Bearer $ST"

RID="trace-register-0001"
curl -s -o /dev/null -X POST "$B/api/activities/3/registrations" \
     -H "Authorization: Bearer $ST" -H "X-Request-Id: $RID" \
     -H 'Content-Type: application/json' -d '{}'
sleep 0.5
lines=$(grep -ac "$RID" "$LOG")
echo "  日志中 traceId=$RID 的行数: $lines"
[ "$lines" -ge 2 ] && chk "访问日志 + 业务日志可串起来" ok ok || chk "访问日志 + 业务日志可串起来" ">=2 行" "$lines 行"
echo "  --- 摘录（同一 traceId 的完整过程）---"
grep -a "$RID" "$LOG" | head -4 | sed 's/^/  /'

echo "== 5. 访问日志字段完整 =="
last=$(grep -a "request method=" "$LOG" | tail -1)
echo "  $last"
echo "$last" | grep -qE "method=(GET|POST|DELETE|PUT)" && chk "含 method" ok ok || chk "含 method" ok missing
echo "$last" | grep -q "status=200" && chk "含 status" ok ok || chk "含 status" ok missing
echo "$last" | grep -q "cost=" && chk "含 cost" ok ok || chk "含 cost" ok missing

echo "== 6. 登录后访问日志带 userId =="
curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"123456"}' -o "$BODY"
AT=$(grep -o '"token":"[^"]*"' "$BODY" | cut -d'"' -f4)
curl -s -o /dev/null "$B/api/auth/me" -H "Authorization: Bearer $AT"
sleep 0.3
ulog=$(grep -a "request method=GET uri=/api/auth/me" "$LOG" | tail -1)
echo "  $ulog"
echo "$ulog" | grep -q "user=1" && chk "带 userId" ok ok || chk "带 userId" "user=1" "miss"

echo "== 7. /actuator/metrics 需要平台管理员 =="
s1=$(curl -s -o /dev/null -w "%{http_code}" "$B/actuator/metrics")
chk "匿名访问被拒" 401 "$s1"
s2=$(curl -s -o /dev/null -w "%{http_code}" "$B/actuator/metrics" -H "Authorization: Bearer $AT")
chk "管理员可访问" 200 "$s2"

echo "== 8. 缓存指标已上报 =="
curl -s -o /dev/null "$B/api/activities/1"
sleep 0.3
m=$(curl -s "$B/actuator/metrics/app.cache.requests" -H "Authorization: Bearer $AT" -o "$BODY" -w "%{http_code}")
chk "指标存在" 200 "$m"
echo "  详情命中次数: $(grep -o '"value":[0-9.]*' "$BODY" | head -1 | cut -d: -f2)"
curl -s "$B/actuator/metrics/app.cache.requests?tag=cache:activity-detail&tag=result:hit" -H "Authorization: Bearer $AT" -o "$BODY"
echo "  activity-detail/hit = $(grep -o '"value":[0-9.]*' "$BODY" | head -1 | cut -d: -f2)"
grep -q '"value"' "$BODY" && chk "可以按标签下钻" ok ok || chk "可以按标签下钻" ok missing

echo
echo "结果: PASS=$PASS FAIL=$FAIL"
