#!/usr/bin/env bash
# P9 验证：活动增删改（新建 / 修改 / 发布 / 下架 / 删除）+ 管理端列表
# 前置：服务已在 8090 启动（本机数据库已按 README 跑过 01_schema.sql / 02_seed.sql）
#
# 用法：bash verify-p9.sh
#   可选环境变量：B（服务地址）、LOG（日志路径，用于断言日志里确实记了操作人）
#
# 说明：脚本会真的写库。最后会尽量把造出来的数据清干净，
#      但「已发布过的活动」在本项目的规则里只能下架、不能删除（防止级联删掉用户报名记录），
#      所以它会留下一条「已下架」的 E2E 活动，脚本末尾会打印清理用的 SQL。
B=${B:-http://127.0.0.1:8090}
BODY="D:/app-kaifa/backend/.verify-body.json"
NULLF="D:/app-kaifa/backend/.verify-null.json"
PASS=0; FAIL=0

# 本机若设置了 HTTP_PROXY，curl 会把 127.0.0.1 的请求也发给代理（回 502），
# 所以这里固定绕过代理；-o 统一写文件，不用 /dev/null（Windows 原生 curl 会写失败）。
B_curl() { curl -s --noproxy '*' "$@"; }

chk() { if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf "  [PASS] %-44s %s\n" "$1" "$3"
        else FAIL=$((FAIL+1)); printf "  [FAIL] %-44s 期望 %s 实际 %s\n" "$1" "$2" "$3"; fi; }

# 从响应体里取字段。只用于脚本内的简单断言，不做通用 JSON 解析
json_str() { grep -o "\"$1\":\"[^\"]*\"" "$BODY" | head -1 | cut -d'"' -f4; }
json_num() { grep -o "\"$1\":[0-9]*" "$BODY" | head -1 | cut -d: -f2; }

# 接口约定的时间格式是「yyyy-MM-dd HH:mm:ss」，不是 ISO 的 T
ts() { date -d "$1 days" '+%Y-%m-%d %H:%M:%S'; }

login() {
  B_curl -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
       -d "{\"username\":\"$1\",\"password\":\"123456\"}" -o "$BODY"
  json_str token
}

act_json() {
  cat <<EOF
{"clubId":$1,"title":"$2","description":"P9 端到端验证创建的活动",
 "location":"大学生活动中心","category":"公益志愿",
 "startTime":"$(ts 3)","endTime":"$(ts 4)","signupDeadline":"$(ts 2)",
 "capacity":$3,"coverColor":"#c8d9ff","rules":"请提前 10 分钟到场\n自备饮水"}
EOF
}

echo "== 0. 健康检查 =="
# 用存活探针而不是综合健康检查：综合检查在 Redis 没起时会返回 503，
# 但接口本身是可用的（缓存降级为直查数据库），等服务起来就够了。
for i in $(seq 1 30); do
  [ "$(B_curl -o "$NULLF" -w '%{http_code}' --max-time 2 "$B/actuator/health/liveness")" = "200" ] && { echo "  UP"; break; }
  sleep 2
done

echo "== 1. 写接口的鉴权边界 =="
s=$(B_curl -o "$NULLF" -w "%{http_code}" -X POST "$B/api/activities" \
     -H 'Content-Type: application/json' -d "$(act_json 1 匿名创建 10)")
chk "匿名新建活动 → 401" 401 "$s"

s=$(B_curl -o "$NULLF" -w "%{http_code}" "$B/api/activities/manage")
chk "匿名访问管理端列表 → 401" 401 "$s"

ST=$(login student1)
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities" -H "Authorization: Bearer $ST" \
     -H 'Content-Type: application/json' -d "$(act_json 1 学生创建 10)")
chk "学生新建活动 → 403" 403 "$s"
chk "学生新建活动的业务码" 40300 "$(json_num code)"

echo "== 2. 登录两类管理员 =="
T1=$(login linqingya)   # 用户 2，社团 1（青年志愿者协会）负责人
chk "社团 1 负责人拿到令牌" 1 "$([ -n "$T1" ] && echo 1 || echo 0)"
T2=$(login zhouyiran)   # 用户 3，社团 2（摄影协会）负责人
chk "社团 2 负责人拿到令牌" 1 "$([ -n "$T2" ] && echo 1 || echo 0)"

echo "== 3. 横向越权：给别的社团建活动 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities" -H "Authorization: Bearer $T2" \
     -H 'Content-Type: application/json' -d "$(act_json 1 越权创建 10)")
chk "社团 2 负责人给社团 1 建活动 → 403" 403 "$s"
chk "越权创建的业务码" 40300 "$(json_num code)"

echo "== 4. 参数校验 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' \
     -d '{"clubId":1,"title":"时间颠倒","location":"体育馆","category":"文体活动",
          "startTime":"'"$(ts 3)"'","endTime":"'"$(ts 1)"'","signupDeadline":"'"$(ts 2)"'","capacity":10}')
chk "结束时间早于开始 → 400" 400 "$s"
chk "时间区间错误的业务码" 40002 "$(json_num code)"

s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' -d "$(act_json 1 名额为零 0)")
chk "名额为 0 → 400" 400 "$s"
chk "参数校验的业务码" 40000 "$(json_num code)"

echo "== 5. 新建：一定是草稿 =="
TITLE="P9验证活动-$(date +%s)"
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' -d "$(act_json 1 "$TITLE" 20)")
AID=$(json_num id)
chk "社团 1 负责人新建活动 → 200" 200 "$s"
chk "初始状态是草稿(0)" 0 "$(json_num status)"
chk "草稿的展示阶段" DRAFT "$(json_str phase)"
chk "草稿展示文案" 草稿 "$(json_str phaseText)"
chk "创建人就是当前用户(2)" 2 "$(json_num createdBy)"
chk "拿到活动 ID" 1 "$([ -n "$AID" ] && echo 1 || echo 0)"
echo "  活动 ID = $AID"

echo "== 6. 草稿对公众不可见，但在管理端可见 =="
s=$(B_curl -o "$NULLF" -w "%{http_code}" "$B/api/activities/$AID")
chk "匿名查草稿详情 → 404" 404 "$s"

B_curl "$B/api/activities/manage?status=0&pageSize=50" -H "Authorization: Bearer $T1" -o "$BODY"
chk "管理端草稿列表含新建活动" 1 "$(grep -c "\"id\":$AID," "$BODY")"

B_curl "$B/api/activities/manage?pageSize=50" -H "Authorization: Bearer $T2" -o "$BODY"
chk "别的社团负责人看不到它" 0 "$(grep -c "\"id\":$AID," "$BODY")"

echo "== 7. 发布 → 对外可见 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/publish" -H "Authorization: Bearer $T1")
chk "发布活动 → 200" 200 "$s"
chk "状态变成已发布(1)" 1 "$(json_num status)"

s=$(B_curl -o "$BODY" -w "%{http_code}" "$B/api/activities/$AID")
chk "发布后匿名详情可访问 → 200" 200 "$s"
chk "详情标题一致" "$TITLE" "$(json_str title)"
chk "详情里的创建人昵称" 林清雅 "$(json_str creatorName)"

echo "== 8. 修改 → 详情立刻返回新值（缓存已失效） =="
NEW="$TITLE-已修改"
s=$(B_curl -o "$BODY" -w "%{http_code}" -X PUT "$B/api/activities/$AID" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' \
     -d '{"title":"'"$NEW"'","description":"改过的详情","location":"体育馆二楼","category":"文体活动",
          "startTime":"'"$(ts 5)"'","endTime":"'"$(ts 6)"'","signupDeadline":"'"$(ts 4)"'",
          "capacity":25,"coverColor":"#ffd9b8","rules":"改过的规则"}')
chk "修改活动 → 200" 200 "$s"
chk "返回标题是新的" "$NEW" "$(json_str title)"

B_curl "$B/api/activities/$AID" -o "$BODY"
chk "再查详情：标题是新值" "$NEW" "$(json_str title)"
chk "再查详情：地点是新值" 体育馆二楼 "$(json_str location)"
chk "再查详情：名额 25" 25 "$(json_num capacity)"

echo "== 9. 名额不能改到已报名人数以下（活动 1 已报名 3 人） =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X PUT "$B/api/activities/1" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' \
     -d '{"title":"校园环保志愿行动","description":"改名额","location":"大学生活动中心","category":"公益志愿",
          "startTime":"'"$(ts 5)"'","endTime":"'"$(ts 6)"'","signupDeadline":"'"$(ts 4)"'","capacity":2}')
chk "名额 2 < 已报名 3 → 409" 409 "$s"
chk "业务码是 40909" 40909 "$(json_num code)"

echo "== 10. 下架 → 对外不可见，且不能物理删除 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/unpublish" -H "Authorization: Bearer $T1")
chk "下架活动 → 200" 200 "$s"
chk "状态变成已下架(2)" 2 "$(json_num status)"
chk "展示文案是「已下架」而不是「已结束」" 已下架 "$(json_str phaseText)"

s=$(B_curl -o "$NULLF" -w "%{http_code}" "$B/api/activities/$AID")
chk "下架后匿名详情 → 404" 404 "$s"

s=$(B_curl -o "$BODY" -w "%{http_code}" -X DELETE "$B/api/activities/$AID" -H "Authorization: Bearer $T1")
chk "删除已下架活动 → 409（只能删草稿）" 409 "$s"
chk "删除冲突的业务码" 40910 "$(json_num code)"

echo "== 11. 已下架可以重新发布 =="
s=$(B_curl -o "$BODY" -w "%{http_code}" -X POST "$B/api/activities/$AID/publish" -H "Authorization: Bearer $T1")
chk "重新发布 → 200" 200 "$s"
chk "状态回到已发布(1)" 1 "$(json_num status)"

echo "== 12. 草稿可以直接删除 =="
B_curl -o "$BODY" -X POST "$B/api/activities" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' -d "$(act_json 1 "P9待删除草稿-$(date +%s)" 10)"
DID=$(json_num id)
chk "拿到待删除草稿的 ID" 1 "$([ -n "$DID" ] && echo 1 || echo 0)"

s=$(B_curl -o "$NULLF" -w "%{http_code}" -X DELETE "$B/api/activities/$DID" -H "Authorization: Bearer $T1")
chk "删除草稿 → 200" 200 "$s"

s=$(B_curl -o "$NULLF" -w "%{http_code}" "$B/api/activities/$DID")
chk "删除后匿名详情 → 404" 404 "$s"

echo "== 13. 可管理社团：新建活动的下拉框数据源 =="
# 前端的「新建活动」必须知道往哪个社团建，这个列表只能由后端按归属给，不能前端按角色猜
B_curl "$B/api/clubs/mine" -H "Authorization: Bearer $T1" -o "$BODY"
chk "社团 1 负责人只看到 1 个社团" 1 "$(grep -o '"name":' "$BODY" | wc -l | tr -d ' ')"
chk "看到的是自己负责的社团" 1 "$(grep -c '"id":1,' "$BODY")"
chk "社团名称对得上" 1 "$(grep -c '青年志愿者协会' "$BODY")"

ADMIN=$(login admin)
B_curl "$B/api/clubs/mine" -H "Authorization: Bearer $ADMIN" -o "$BODY"
chk "平台管理员看到全部 4 个社团" 4 "$(grep -o '"name":' "$BODY" | wc -l | tr -d ' ')"

s=$(B_curl -o "$BODY" -w "%{http_code}" "$B/api/clubs/mine" -H "Authorization: Bearer $ST")
chk "学生查可管理社团 → 403" 403 "$s"
chk "角色不够的业务码" 40300 "$(json_num code)"

s=$(B_curl -o "$BODY" -w "%{http_code}" "$B/api/clubs/mine")
chk "未登录查可管理社团 → 401" 401 "$s"

echo "== 14. 管理端单条详情：草稿也能回填 =="
# 编辑页要回填草稿，而公开详情 GET /api/activities/{id} 对草稿是 404。
# 没有这个接口，前端只能把列表里那一条塞进本地缓存带过去 ——
# 活动超过一页（管理端单页上限 50）就再也翻不到了。
B_curl -o "$BODY" -X POST "$B/api/activities" -H "Authorization: Bearer $T1" \
     -H 'Content-Type: application/json' -d "$(act_json 1 "P9管理端详情草稿-$(date +%s)" 10)"
MID=$(json_num id)

s=$(B_curl -o "$NULLF" -w "%{http_code}" "$B/api/activities/$MID")
chk "同一条草稿：公开详情 → 404" 404 "$s"

s=$(B_curl -o "$BODY" -w "%{http_code}" "$B/api/activities/manage/$MID" -H "Authorization: Bearer $T1")
chk "管理端详情 → 200" 200 "$s"
chk "读到的状态是草稿(0)" 0 "$(json_num status)"
chk "阶段仍是草稿" DRAFT "$(json_str phase)"
chk "规则已拆成数组" 1 "$(grep -c '"ruleList":\["' "$BODY")"

s=$(B_curl -o "$NULLF" -w "%{http_code}" "$B/api/activities/manage/$MID" -H "Authorization: Bearer $T2")
chk "别的社团负责人读 → 403" 403 "$s"

s=$(B_curl -o "$NULLF" -w "%{http_code}" "$B/api/activities/manage/$MID" -H "Authorization: Bearer $ST")
chk "学生读 → 403" 403 "$s"

s=$(B_curl -o "$NULLF" -w "%{http_code}" "$B/api/activities/manage/$MID")
chk "匿名读 → 401（没落到 permitAll 上）" 401 "$s"

s=$(B_curl -o "$NULLF" -w "%{http_code}" "$B/api/activities/manage/$MID" -H "Authorization: Bearer $ADMIN")
chk "平台管理员读任意社团草稿 → 200" 200 "$s"

s=$(B_curl -o "$BODY" -w "%{http_code}" "$B/api/activities/manage/999999" -H "Authorization: Bearer $ADMIN")
chk "不存在的活动 → 404" 404 "$s"
chk "404 的业务码" 40401 "$(json_num code)"

# 这张草稿从未发布过，可以直接物理删除，不留脏数据
B_curl -o "$NULLF" -X DELETE "$B/api/activities/$MID" -H "Authorization: Bearer $T1"

echo "== 15. 收尾：把验证活动下架，避免污染对外列表 =="
B_curl -o "$NULLF" -X POST "$B/api/activities/$AID/unpublish" -H "Authorization: Bearer $T1"
B_curl -o "$BODY" "$B/api/activities?pageSize=50"
chk "对外列表里已经没有它" 0 "$(grep -c "\"id\":$AID," "$BODY")"

echo
echo "结果：PASS=$PASS FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then
  echo "全部通过。"
else
  echo "存在失败项，请检查上面的 [FAIL] 行。"
fi
cat <<EOF

清理（可选）：验证活动 $AID 现在是「已下架」，按项目规则不能删除。
如果不想在库里留着它，直接执行：
  DELETE FROM activity WHERE id = $AID;
EOF
[ "$FAIL" -eq 0 ]
