#!/usr/bin/env bash
# P4 认证与权限 —— 真实 HTTP 验证脚本
# 用法：先启动后端（8090），再 bash verify-p4.sh
B=http://127.0.0.1:8090
BODY="D:/app-kaifa/backend/.verify-body.json"   # 必须用 Windows 盘符路径：curl 是原生 exe，不认 /d/...
PASS=0; FAIL=0

chk() { # $1=描述 $2=期望 $3=实际
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf "  [PASS] %-40s %s\n" "$1" "$3"
  else FAIL=$((FAIL+1)); printf "  [FAIL] %-40s 期望 %s 实际 %s\n" "$1" "$2" "$3"; fi
}
code() { curl -s -o "$BODY" -w "%{http_code}" "$@"; }
login() { code -X POST "$B/api/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}"; }
tok() { grep -o '"token":"[^"]*"' "$BODY" | cut -d'"' -f4; }
ecode() { grep -o '"code":[0-9]*' "$BODY" | head -1; }

echo "== 1. 登录 =="
s=$(login admin 123456); chk "平台管理员登录" 200 "$s"
ADMIN=$(tok)
if grep -q passwordHash "$BODY"; then chk "响应不含 passwordHash" absent present; else chk "响应不含 passwordHash" absent absent; fi

login linqingya 123456 >/dev/null; CLUB1=$(tok)
login zhouyiran 123456 >/dev/null; CLUB2=$(tok)
login student1 123456 >/dev/null; STUD=$(tok)
echo "  token 长度: admin=${#ADMIN} club1=${#CLUB1} club2=${#CLUB2} student=${#STUD}"

echo "== 2. 口令错误 / 账号不存在（防枚举）=="
s=$(login admin wrong-pass); chk "口令错误" 401 "$s"; c1=$(ecode)
s=$(login no_such_user_zzz 123456); chk "账号不存在" 401 "$s"; c2=$(ecode)
chk "两者错误码一致(防枚举)" "$c1" "$c2"

echo "== 3. /api/auth/me =="
s=$(code "$B/api/auth/me" -H "Authorization: Bearer $STUD"); chk "带 token 取当前用户" 200 "$s"
echo "  body: $(head -c 220 "$BODY")"

echo "== 4. 无 token / 坏 token / 旧方案 =="
s=$(code "$B/api/auth/me"); chk "无 token" 401 "$s"
# 注意：只在尾部追加字符是「无效篡改」—— HS384 签名是 48 字节 = base64url 64 字符（无 padding），
# 尾部多出的 6 bit 不足一个字节，base64 解码器会直接丢弃，解出的签名与原签名完全一致。
# 真正有意义的篡改是改动签名里的某一位。
LASTC="${ADMIN: -1}"; [ "$LASTC" = "A" ] && REPL="B" || REPL="A"
TAMPERED="${ADMIN:0:${#ADMIN}-1}$REPL"
s=$(code "$B/api/auth/me" -H "Authorization: Bearer $TAMPERED"); chk "篡改签名末位" 401 "$s"; echo "  篡改后错误码: $(ecode)"
s=$(code "$B/api/auth/me" -H "X-User-Id: 1"); chk "只带 X-User-Id（旧方案已废）" 401 "$s"

echo "== 5. 公开接口仍公开 =="
s=$(code "$B/api/activities?page=1&pageSize=5"); chk "活动列表免登录" 200 "$s"
s=$(code "$B/api/activities/1"); chk "活动详情免登录" 200 "$s"

echo "== 6. 报名名单：URL 级 + 数据级双层鉴权 =="
s=$(code "$B/api/activities/1/registrations"); chk "未登录" 401 "$s"
s=$(code "$B/api/activities/1/registrations" -H "Authorization: Bearer $STUD"); chk "普通学生" 403 "$s"
s=$(code "$B/api/activities/1/registrations" -H "Authorization: Bearer $CLUB1"); chk "活动1所属社团负责人" 200 "$s"
echo "  名单首条: $(head -c 200 "$BODY")"
s=$(code "$B/api/activities/1/registrations" -H "Authorization: Bearer $CLUB2"); chk "其他社团负责人(横向越权)" 403 "$s"
s=$(code "$B/api/activities/1/registrations" -H "Authorization: Bearer $ADMIN"); chk "平台管理员" 200 "$s"
s=$(code "$B/api/activities/99999/registrations" -H "Authorization: Bearer $ADMIN"); chk "活动不存在" 404 "$s"

echo "== 7. 写接口仍要登录 =="
s=$(code -X POST "$B/api/activities/1/registrations" -H 'Content-Type: application/json' -d '{}'); chk "未登录报名" 401 "$s"

echo
echo "结果: PASS=$PASS FAIL=$FAIL"
