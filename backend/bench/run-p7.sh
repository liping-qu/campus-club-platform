#!/usr/bin/env bash
# P7 性能场景编排：读路径（缓存开/关）、穿透场景、写路径（同一条活动行的锁竞争）
#
# 用法：
#   bash bench/run-p7.sh cache-on     # 服务以 app.cache.enabled=true 启动
#   bash bench/run-p7.sh cache-off    # 服务以 app.cache.enabled=false 启动
#
# 只负责发包和收集数字，结论写在 PERFORMANCE.md 里。

B=http://127.0.0.1:8090
BODY="D:/app-kaifa/backend/.bench-body.json"
NODE="C:/Users/屈丽萍/.workbuddy/binaries/node/versions/22.22.2-3/node.exe"
LABEL=${1:-cache-on}

login() {
  curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
       -d "{\"username\":\"$1\",\"password\":\"123456\"}" -o "$BODY"
  grep -o '"token":"[^"]*"' "$BODY" | cut -d'"' -f4
}

echo "=== 场景组：$LABEL ==="
T1=$(login student1)
T2=$(login student2)
T3=$(login student3)
if [ -z "$T1" ] || [ -z "$T2" ] || [ -z "$T3" ]; then
  echo "登录失败，服务是否已在 8090 启动？"
  exit 1
fi

# 写入场景前把这三个人的报名状态清干净，避免一上来就撞 409
for T in "$T1" "$T2" "$T3"; do
  curl -s -o /dev/null -X DELETE "$B/api/activities/3/registrations/me" -H "Authorization: Bearer $T"
done

"$NODE" bench/loadtest.js --mode=get \
  --url="$B/api/activities/1" --token="$T1" \
  --concurrency=32 --requests=6000 --warmup=1200 --label="[$LABEL] 活动详情 GET /api/activities/1"

"$NODE" bench/loadtest.js --mode=get \
  --url="$B/api/activities?pageNo=1&pageSize=10" --token="$T1" \
  --concurrency=32 --requests=6000 --warmup=1200 --label="[$LABEL] 活动列表 GET /api/activities"

"$NODE" bench/loadtest.js --mode=get \
  --url="$B/api/activities/999999" --token="$T1" --expect=404 \
  --concurrency=32 --requests=3000 --warmup=1200 --label="[$LABEL] 不存在的 id（空值标记）"

"$NODE" bench/loadtest.js --mode=cycle \
  --url="$B/api/activities/3/registrations" --tokens="$T1,$T2,$T3" \
  --concurrency=3 --requests=1200 --warmup=120 --label="[$LABEL] 报名/取消循环（同一条活动行）"

# 收尾：确保没有残留报名，避免影响其它脚本的活动计数断言
for T in "$T1" "$T2" "$T3"; do
  curl -s -o /dev/null -X DELETE "$B/api/activities/3/registrations/me" -H "Authorization: Bearer $T"
done
