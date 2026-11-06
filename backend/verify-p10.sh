#!/usr/bin/env bash
# P10 验证：签到（本人签到 / 签到状态 / 负责人代签 / 志愿时长 / 签到名单 / 签到与取消报名的互斥）
# 前置：服务已在 8090 启动（本机数据库已按 README 跑过 01_schema.sql / 02_seed.sql）
#
# 用法：bash verify-p10.sh
#   可选环境变量：B（服务地址）
#
# 设计说明：
#   1. 时间窗是相对「现在」算的，所以成功签到必须现场造一条「已经开始、还没结束」的活动；
#      「太早」用种子活动 2（2026-11-15），「已结束」用种子活动 1（2026-10-12）——
#      这两条都是真实数据、且签到会被拒绝，不会写脏库。
#   2. 造出来的活动会真的写库（签到记录 + 志愿时长）。脚本末尾打印清理 SQL：
#      按项目规则，发布过的活动只能下架不能物理删除（防止级联删掉报名记录）。
#   3. 「我的签到」累计值用「前后差值」断言，而不是断言一个绝对值 ——
#      学生 1 在种子数据里已有 1.5 小时（活动 1），绝对值会随环境变化，差值不会。
B=${B:-http://127.0.0.1:8090}
BODY="D:/app-kaifa/backend/.verify-body.json"
NULLF="D:/app-kaifa/backend/.verify-null.json"
PASS=0; FAIL=0

# 本机若设置了 HTTP_PROXY，curl 会把 127.0.0.1 的请求也发给代理（回 502），固定绕过；
# -o 统一写文件，不用 /dev/null（Windows 原生 curl 会写失败）。
B_curl() { curl -s --noproxy '*' "$@"; }

chk() { if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf "  [PASS] %-46s %s\n" "$1" "$3"
        else FAIL=$((FAIL+1)); printf "  [FAIL] %-46s 期望 %s 实际 %s\n" "$1" "$2" "$3"; fi; }

# 小数比较：$1 op $2（awk 做浮点，脚本里没有 bc）
num_eq() { awk -v a="$1" -v b="$2" 'BEGIN { d = a - b; if (d < 0) d = -d; exit !(d < 0.001) }'; }

json_str() { grep -o "\"$1\":\"[^\"]*\"" "$BODY" | head -1 | cut -d'"' -f4; }
json_num() { grep -o "\"$1\":[0-9]*" "$BODY" | head -1 | cut -d: -f2; }
json_dec() { grep -o "\"$1\":[0-9][0-9.]*" "$BODY" | head -1 | cut -d: -f2; }
json_bool() { grep -o "\"$1\":\(true\|false\)" "$BODY" | head -1 | cut -d: -f2; }

# 接口约定的时间格式是「yyyy-MM-dd HH:mm:ss」，不是 ISO 的 T
ts_min() { date -d "$1 minutes" '+%Y-%m-%d %H:%M:%S'; }

login() {
  B_curl -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
       -d "{\"username\":\"$1\",\"password\":\"123456\"}" -o "$BODY"
  json_str token
}

act_json() {
  cat <<EOF
{"clubId":1,"title":"$1","description":"P10 端到端验证创建的活动",
 "location":"大学生活动中心","category":"公益志愿",
 "startTime":"$2","endTime":"$3","signupDeadline":"$4",
 "capacity":20,"coverColor":"#c8d9ff","rules":"请提前 10 分钟到场签到"}
EOF
}

echo "== 0. 健康检查 =="
for i in $(seq 1 30); do
  [ "$(B_curl -o "$NULLF" -w '%{http_code}' --max-time 2 "$B/actuator/health/liveness")" = "200" ] && { echo "  UP"; break; }
  sleep 2
done

echo "== 1. 登录 =="
ST1=$(login student1)   # 用户 6
ST2=$(login student2)   # 用户 7
ST3=$(login student3)   # 用户 8
T1=$(login linqingya)   # 用户 2，社团 1 负责人
T2=$(login zhouyiran)   # 用户 3，社团 2 负责人
chk "三个学生 + 两个负责人拿到令牌" 1 \
    "$([ -n "$ST1" ] && [ -n "$ST2" ] && [ -n "$ST3" ] && [ -n "$T1" ] && [ -n "$T2" ] && echo 1 || echo 0)"

echo "== 2. 匿名访问签到接口 → 401（不能靠 @PreAuthorize 兜成 403） =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/2/check-in")
chk "匿名签到 → 401" 401 "$s"
chk "匿名签到的业务码" 40100 "$(json_num code)"

s=$(B_curl -o "$BODY" -w "%{http_code}" "$B/api/activities/2/check-in/me")
chk "匿名查签到状态 → 401" 401 "$s"

s=$(B_curl -o "$BODY" -w "%{http_code}" "$B/api/activities/2/attendances")
chk "匿名查签到名单 → 401" 401 "$s"

s=$(B_curl -o "$BODY" -w "%{http_code}" "$B/api/attendances/me")
chk "匿名查我的签到记录 → 401" 401 "$s"

echo "== 3. 造一条「已经开始、还没结束」的活动并发布 =="
TITLE="P10签到验证-$(date +%s)"
START=$(ts_min 10); END=$(ts_min 120); DEADLINE=$(ts_min 5)
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' -d "$(act_json "$TITLE" "$START" "$END" "$DEADLINE")")
AID=$(json_num id)
chk "负责人新建活动 → 200" 200 "$s"
chk "拿到活动 ID" 1 "$([ -n "$AID" ] && echo 1 || echo 0)"
echo "  活动 ID = $AID，开始 $START，结束 $END"

s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/publish" -H "Authorization: Bearer $T1")
chk "发布活动 → 200" 200 "$s"

s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/registrations" -H "Authorization: Bearer $ST1")
chk "学生 1 报名 → 200" 200 "$s"
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/registrations" -H "Authorization: Bearer $ST2")
chk "学生 2 报名 → 200" 200 "$s"

echo "== 4. 未签到时的状态：checkedIn=false，但 checkInOpen=true =="
B_curl "$B/api/activities/$AID/check-in/me" -H "Authorization: Bearer $ST1" -o "$BODY"
chk "未签到返回 false 而不是 404" false "$(json_bool checkedIn)"
chk "窗口内 checkInOpen" true "$(json_bool checkInOpen)"

echo "== 5. 没报名的人签到 → 409/40903 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/check-in" -H "Authorization: Bearer $ST3")
chk "学生 3 未报名签到 → 409" 409 "$s"
chk "未报名的业务码" 40903 "$(json_num code)"

echo "== 6. 窗口内本人签到 → 200，记 MANUAL、时长 0 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/check-in" -H "Authorization: Bearer $ST1")
chk "学生 1 签到 → 200" 200 "$s"
chk "签到后 checkedIn" true "$(json_bool checkedIn)"
chk "签到方式是 MANUAL" MANUAL "$(json_str method)"
chk "自签时长从 0 起" 0 "$(json_dec volunteerHours)"

B_curl "$B/api/activities/$AID/check-in/me" -H "Authorization: Bearer $ST1" -o "$BODY"
chk "再查状态：已签到" true "$(json_bool checkedIn)"
chk "再查状态：时间是签到时间而不是空" 1 "$([ -n "$(json_str checkedInAt)" ] && echo 1 || echo 0)"

echo "== 7. 重复签到 → 409/40906 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/check-in" -H "Authorization: Bearer $ST1")
chk "重复签到 → 409" 409 "$s"
chk "重复签到的业务码" 40906 "$(json_num code)"

echo "== 8. 太早签到：种子活动 2 还有十几天才开始 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/2/check-in" -H "Authorization: Bearer $ST1")
chk "种子活动 2 签到 → 409" 409 "$s"
chk "尚未开始的业务码" 40911 "$(json_num code)"

echo "== 9. 已结束签到：种子活动 1 已于 2026-10-12 结束（学生 3 已报名、未签到） =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/1/check-in" -H "Authorization: Bearer $ST3")
chk "种子活动 1 签到 → 409" 409 "$s"
chk "已结束的业务码" 40912 "$(json_num code)"

echo "== 10. 学生查签到名单 → 403（角色不够） =="
s=$(B_curl -o "$BODY" -w "%{http_code}" "$B/api/activities/$AID/attendances" -H "Authorization: Bearer $ST1")
chk "学生查签到名单 → 403" 403 "$s"
chk "角色不够的业务码" 40300 "$(json_num code)"

echo "== 11. 横向越权：别家负责人代签 / 查名单 → 403 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/attendances" -H "Authorization: Bearer $T2" \
     -H 'Content-Type: application/json' -d '{"userId":7,"volunteerHours":1.0}')
chk "社团 2 负责人给社团 1 的活动代签 → 403" 403 "$s"
chk "越权代签的业务码" 40300 "$(json_num code)"

s=$(B_curl -o "$BODY" -w "%{http_code}" "$B/api/activities/$AID/attendances" -H "Authorization: Bearer $T2")
chk "社团 2 负责人查社团 1 的签到名单 → 403" 403 "$s"

echo "== 12. 负责人代签 → 200，记 ADMIN 与指定时长 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/attendances" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' -d '{"userId":7,"volunteerHours":2.5}')
chk "负责人代签学生 2 → 200" 200 "$s"
chk "代签方式是 ADMIN" ADMIN "$(json_str method)"
chk "代签时长 2.5" 2.5 "$(json_dec volunteerHours)"

s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/attendances" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' -d '{"userId":7}')
chk "重复代签 → 409" 409 "$s"
chk "重复代签的业务码" 40906 "$(json_num code)"

echo "== 13. 代签没报名的人 → 409/40903 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/attendances" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' -d '{"userId":8}')
chk "代签未报名用户 → 409" 409 "$s"
chk "代签未报名的业务码" 40903 "$(json_num code)"

echo "== 14. 参数校验：userId=0 / 时长超 DECIMAL(4,1) → 400/40000 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/attendances" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' -d '{"userId":0}')
chk "userId=0 → 400" 400 "$s"
chk "参数校验的业务码" 40000 "$(json_num code)"

s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/attendances" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' -d '{"userId":7,"volunteerHours":1000}')
chk "时长 1000 → 400" 400 "$s"

echo "== 15. 签到名单：两种方式都能看出来 =="
B_curl "$B/api/activities/$AID/attendances" -H "Authorization: Bearer $T1" -o "$BODY"
chk "名单里有 2 条记录" 2 "$(grep -o '"userId":' "$BODY" | wc -l | tr -d ' ')"
chk "一条自签（MANUAL）" 1 "$(grep -c '"method":"MANUAL"' "$BODY")"
chk "一条代签（ADMIN）" 1 "$(grep -c '"method":"ADMIN"' "$BODY")"
chk "名单带昵称，便于现场核对" 1 "$(grep -c '"nickname":"张同学"' "$BODY")"

echo "== 16. 设置志愿时长：累计值按差值变化 =="
# 此时学生 1 已经签过到（第 6 步），本次只改时长、不新增记录，
# 所以断言是「总时长 +3.5」且「记录数不变」—— 这正是「改时长」和「又签了一次」的区别。
B_curl "$B/api/attendances/me" -H "Authorization: Bearer $ST1" -o "$BODY"
BEFORE_TOTAL=$(json_dec totalHours); BEFORE_COUNT=$(json_num count)

s=$(B_curl -o "$BODY" -w "%{http_code}" -X PUT "$B/api/activities/$AID/attendances/6/hours" \
     -H "Authorization: Bearer $T1" -H 'Content-Type: application/json' -d '{"volunteerHours":3.5}')
chk "学生 1 的时长改成 3.5 → 200" 200 "$s"

B_curl "$B/api/attendances/me" -H "Authorization: Bearer $ST1" -o "$BODY"
chk "累计时长 = 之前 + 3.5" 1 "$(num_eq "$(json_dec totalHours)" "$(awk -v a="$BEFORE_TOTAL" 'BEGIN{print a+3.5}')" && echo 1 || echo 0)"
chk "记录数不变（改时长不是再签一次）" "$BEFORE_COUNT" "$(json_num count)"

s=$(B_curl -o "$BODY" -w "%{http_code}" -X PUT "$B/api/activities/$AID/attendances/8/hours" \
     -H "Authorization: Bearer $T1" -H 'Content-Type: application/json' -d '{"volunteerHours":1.0}')
chk "给没签到的人设时长 → 404" 404 "$s"
chk "签到记录不存在的业务码" 40405 "$(json_num code)"

s=$(B_curl -o "$BODY" -w "%{http_code}" -X PUT "$B/api/activities/$AID/attendances/6/hours" \
     -H "Authorization: Bearer $T1" -H 'Content-Type: application/json' -d '{"volunteerHours":1.25}')
chk "时长两位小数 → 400（表是 DECIMAL(4,1)）" 400 "$s"

echo "== 17. 签到后不能取消报名（否则签到记录成孤儿） =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X DELETE "$B/api/activities/$AID/registrations/me" -H "Authorization: Bearer $ST1")
chk "已签到后取消报名 → 409" 409 "$s"
chk "已签到不能取消的业务码" 40913 "$(json_num code)"

B_curl "$B/api/activities/$AID/registrations/me" -H "Authorization: Bearer $ST1" -o "$BODY"
chk "报名状态仍然是 REGISTERED" REGISTERED "$(json_str status)"

echo
echo "结果：PASS=$PASS FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then
  echo "全部通过。"
else
  echo "存在失败项，请检查上面的 [FAIL] 行。"
fi
cat <<EOF

清理（可选）：验证活动 $AID 是「已发布」，按项目规则不能物理删除。
执行下面两条即可同时清掉活动、报名和签到记录（外键级联）：
  DELETE FROM activity WHERE id = $AID;
EOF
[ "$FAIL" -eq 0 ]
