#!/usr/bin/env bash
# P8 异步通知验证：通知真的落库、越权改不到、异步线程真的独立、指标可查
# 前置：服务已在 8090 启动，日志写到 app-p8.log
B=http://127.0.0.1:8090
# 日志路径可用环境变量覆盖，方便把服务输出到别的文件时复用本脚本
LOG=${LOG:-/d/app-kaifa/backend/app-p8.log}
BODY="D:/app-kaifa/backend/.verify-body.json"
PASS=0; FAIL=0

chk() { if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf "  [PASS] %-46s %s\n" "$1" "$3"
        else FAIL=$((FAIL+1)); printf "  [FAIL] %-46s 期望 %s 实际 %s\n" "$1" "$2" "$3"; fi; }

json_num() { grep -o "\"$1\":[0-9]*" "$BODY" | head -1 | cut -d: -f2; }

echo "== 0. 健康检查 =="
for i in $(seq 1 30); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "$B/actuator/health")" = "200" ] && { echo "  UP"; break; }
  sleep 2
done

echo "== 1. 通知接口必须登录 =="
s=$(curl -s -o /dev/null -w "%{http_code}" "$B/api/notifications")
chk "匿名访问 /api/notifications" 401 "$s"
s=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$B/api/notifications/1/read")
chk "匿名标记已读" 401 "$s"

echo "== 2. 登录并记录基线 =="
curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
     -d '{"username":"student1","password":"123456"}' -o "$BODY"
ST=$(grep -o '"token":"[^"]*"' "$BODY" | cut -d'"' -f4)
chk "student1 拿到令牌" 1 "$([ -n "$ST" ] && echo 1 || echo 0)"
curl -s "$B/api/notifications/unread-count" -H "Authorization: Bearer $ST" -o "$BODY"
BEFORE=$(json_num unread)
echo "  未读基线: $BEFORE"

curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"123456"}' -o "$BODY"
AT=$(grep -o '"token":"[^"]*"' "$BODY" | cut -d'"' -f4)

echo "== 3. 报名 → 通知异步落库 =="
# 先取消再报名，保证这次一定是「新报名」，状态确定
curl -s -o /dev/null -X DELETE "$B/api/activities/3/registrations/me" -H "Authorization: Bearer $ST"
sleep 0.3
RID="p8-register-$(date +%s)"
OUT=$(curl -s -o "$BODY" -w '%{http_code} %{time_total}' -X POST "$B/api/activities/3/registrations" \
     -H "Authorization: Bearer $ST" -H "X-Request-Id: $RID" -H 'Content-Type: application/json' -d '{}')
RSTATUS=${OUT%% *}
COST=${OUT##* }
echo "  报名接口耗时: ${COST}s"
chk "报名成功" 200 "$RSTATUS"

# 轮询等异步结果，最多 5 秒。报名接口已经返回了，通知才出现在这里
N=0
for i in $(seq 1 25); do
  curl -s "$B/api/notifications?pageNo=1&pageSize=5" -H "Authorization: Bearer $ST" -o "$BODY"
  N=$(grep -o '"title":"报名成功"' "$BODY" | wc -l)
  [ "$N" -ge 1 ] && break
  sleep 0.2
done
chk "通知列表里出现「报名成功」" 1 "$([ "$N" -ge 1 ] && echo 1 || echo 0)"

echo "== 4. 通知内容是事务内快照，不是回查出来的 =="
grep -q '"type":"REGISTRATION"' "$BODY" && chk "type=REGISTRATION" ok ok || chk "type=REGISTRATION" ok missing
grep -q '"read":false' "$BODY" && chk "新通知为未读" ok ok || chk "新通知为未读" ok missing
TITLE=$(grep -o '"content":"[^"]*"' "$BODY" | head -1 | cut -d'"' -f4)
echo "  正文: $TITLE"
# 拿活动真实标题来比对，而不是断言正文里出现「活动」两个字
ACT_TITLE=$(curl -s "$B/api/activities/3" -H "Authorization: Bearer $ST" | grep -o '"title":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "  活动标题: $ACT_TITLE"
case "$TITLE" in
  *"$ACT_TITLE"*) chk "正文含活动真实标题" ok ok ;;
  *) chk "正文含活动真实标题" "含 $ACT_TITLE" "missing" ;;
esac

echo "== 5. 未读数随通知 +1 =="
curl -s "$B/api/notifications/unread-count" -H "Authorization: Bearer $ST" -o "$BODY"
AFTER=$(json_num unread)
chk "未读数 +1" "$((BEFORE+1))" "$AFTER"

echo "== 6. 标记已读（含幂等与越权）=="
NID=$(grep -o '"id":[0-9]*' "$BODY" | head -1 | cut -d: -f2)
curl -s "$B/api/notifications?pageNo=1&pageSize=5" -H "Authorization: Bearer $ST" -o "$BODY"
NID=$(grep -o '"id":[0-9]*' "$BODY" | head -1 | cut -d: -f2)
echo "  通知 ID: $NID"
s=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$B/api/notifications/$NID/read" -H "Authorization: Bearer $ST")
chk "本人标记已读" 200 "$s"
s=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$B/api/notifications/$NID/read" -H "Authorization: Bearer $ST")
chk "重复标记返回 404（不是假装成功）" 404 "$s"
s=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$B/api/notifications/$NID/read" -H "Authorization: Bearer $AT")
chk "管理员改别人的通知也 404" 404 "$s"
curl -s "$B/api/notifications/unread-count" -H "Authorization: Bearer $ST" -o "$BODY"
chk "已读后未读回到基线" "$BEFORE" "$(json_num unread)"

echo "== 7. 全部已读幂等 =="
curl -s -o /dev/null -X POST "$B/api/notifications/read-all" -H "Authorization: Bearer $ST"
curl -s -X POST "$B/api/notifications/read-all" -H "Authorization: Bearer $ST" -o "$BODY"
UPD=$(json_num updated)
chk "重复全部已读 updated=0" 0 "$UPD"

echo "== 8. 异步证据：通知在 notify- 线程里写 =="
sleep 0.5
# 按「同一个 traceId + notify 线程」定位，不依赖消息正文（日志编码因平台而异）
tline=$(grep -a "$RID" "$LOG" | grep -a "\[notify-" | head -1)
echo "  $tline"
if [ -n "$tline" ]; then chk "同一 traceId 下能追到通知投递" ok ok; else chk "同一 traceId 下能追到通知投递" ok missing; fi
pl=$(grep -a "$RID" "$LOG" | grep -a "\[http-nio" | head -1 | grep -o "\[http-nio[^]]*\]")
echo "  报名请求所在线程: $pl（与上面的 notify- 线程不同 → 真的异步）"
grep -aq "\[notify-" "$LOG" && chk "日志出现 notify- 线程" ok ok || chk "日志出现 notify- 线程" ok missing
echo "  --- 同一条链路（报名请求线程 → 通知线程）---"
grep -a "$RID" "$LOG" | sed 's/^/  /' | head -6

echo "== 9. 通知指标可查 =="
s=$(curl -s "$B/actuator/metrics/app.notification.dispatch?tag=result:success" -H "Authorization: Bearer $AT" -o "$BODY" -w "%{http_code}")
chk "app.notification.dispatch 存在" 200 "$s"
echo "  投递成功计数: $(grep -o '"value":[0-9.]*' "$BODY" | head -1 | cut -d: -f2)"
s=$(curl -s "$B/actuator/metrics/app.notification.queue.size" -H "Authorization: Bearer $AT" -o /dev/null -w "%{http_code}")
chk "队列积压指标存在" 200 "$s"

echo "== 10. 清理：取消报名，不留脏数据 =="
s=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$B/api/activities/3/registrations/me" -H "Authorization: Bearer $ST")
chk "取消报名" 200 "$s"

echo
echo "结果: PASS=$PASS FAIL=$FAIL"
