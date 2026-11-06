# 交接文档：分支、发布、验证与环境坑

> 面向：接手这个仓库的人（或隔三个月回来的自己）。
> 目标：**15 分钟内把项目跑起来**，并且知道「改完之后怎么安全地发出去」。
>
> 最后更新：2026-11-06（分支治理当天）
>
> 相关文档：
> - 项目介绍、接口清单、完成度、已知限制 → [`README.md`](../README.md)
> - 压测方法与真实数据 → [`backend/PERFORMANCE.md`](../backend/PERFORMANCE.md)
> - 异步通知的取舍与改造清单 → [`backend/docs/async-notification.md`](../backend/docs/async-notification.md)

---

## 0. 三十秒现状

| 项 | 值 |
| --- | --- |
| 仓库 | `https://github.com/liping-qu/campus-club-platform` |
| 默认分支 | `main`（发布分支，也是 GitHub 的默认展示分支） |
| 工作分支 | `backend-refactor`（保留，作为对照/回滚备份） |
| 最新提交 | 两端一致，均指向同一条提交（见 `git log --oneline -1`） |
| 后端测试基线 | **219 个自动化测试全绿**（连真 MySQL；Redis 用例用内嵌实例） |
| 端到端断言 | `verify-p9.sh` 63 项、`verify-p10.sh` 55 项，其余 `verify-p*.sh` 按阶段覆盖 |
| 前端验证 | `vue-tsc --noEmit` 0 错；H5 与 mp-weixin 均可构建 |
| 尚未完成 | 见 README「已知限制」：扫码签到只有字段没有流程、志愿时长无审核、已发布活动不可删除等 |

**一句话**：功能是完整的、测试是真的、数字是实测的；剩下的是「没做的功能」而不是「没验证的代码」。

---

## 1. 分支模型

### 1.1 为什么只有两个分支

不是标准的 Git Flow，也不需要。这个仓库只有一个人推进，分支的唯一作用是**把「实验中的重构」和「别人 clone 下来能跑的东西」分开**：

| 分支 | 定位 | 谁在用 |
| --- | --- | --- |
| `main` | 发布分支。clone 默认落到这里，必须永远可启动、测试全绿 | 看项目的人、面试官、CI |
| `backend-refactor` | 工作分支。历史上一路演进过来的那条线，保留作对照 | 开发者本人 |

当前两者**指向同一条提交**，内容完全一致：

```bash
git diff --stat backend-refactor main    # 空输出 = 一致
```

### 1.2 先判断「有没有分叉」，再决定怎么合

这一步不能跳。两种情况的处理方式完全不同：

```bash
# main 是不是 backend-refactor 的祖先？
git merge-base --is-ancestor main backend-refactor && echo "可直接快进" || echo "分叉了，需要合并"

# 两边各领先多少
git rev-list --count main..backend-refactor
git rev-list --count backend-refactor..main
```

- **落后 N、领先 0** → 快进即可（最理想，不动历史）。
- **两边都非 0** → 真分叉，见 1.5。

### 1.3 发布流程（照抄即可）

```bash
# 1) 确认工作区干净。有改动先提交，不要带着未提交改动切分支
git status --short          # 期望：空

# 2) 切到 main 并只允许快进。--ff-only 不是洁癖：它会拒绝产生合并提交，
#    也就拒绝了「悄悄改变历史形状」这件事
git checkout main
git merge --ff-only backend-refactor

# 3) 在 main 上重新验证（不要拿分支上的成绩冒充，见第 3 节）
mvn test

# 4) 推送（快进推送，禁止 -f）
git push origin main
```

合并完成后两端应当指向同一提交：

```bash
git rev-parse main origin/main backend-refactor origin/backend-refactor
# 四行输出完全相同才算发完了
```

### 1.4 明令禁止的四件事

| 禁止 | 原因 |
| --- | --- |
| `git push -f` / `--force-with-lease` 到 `main` | 公开仓库的 main 被别人 clone 过，强推会让对方的历史对不上 |
| `git reset --hard` 到已推送的提交 | 同上，等于改写公开历史 |
| `git branch -D` 删分支「清理一下」 | 分支是唯一的回滚点，删了就只能靠 reflog（默认 90 天） |
| 把 `.workbuddy/`、`.env` 提交上去 | 里面有本机口令与临时脚本，`.gitignore` 已经挡住，不要用 `-f` 绕过 |

### 1.5 万一真的分叉了

```bash
# 方案 A（推荐）：把 main 的新提交合进来，保留两边历史
git checkout backend-refactor
git merge main

# 方案 B：把工作分支 rebase 到 main 上，历史更直，但会改写 backend-refactor 的提交号
git rebase main
```

选 A 还是 B，取决于「`backend-refactor` 有没有被别人基于它开分支」。本项目没有，所以两者都安全；**但只要分支已经推到远端并被别人使用，就不要 rebase**。

---

## 2. 接手第一天：把项目跑起来

### 2.1 依赖

| 组件 | 版本 | 是否必须 |
| --- | --- | --- |
| JDK | 17 | 必须 |
| Maven | 3.8+ | 必须 |
| Node.js | 18+（本机验证用 22） | 前端必须 |
| MySQL | 8.x | 必须 |
| Redis | 7.x | 可选，连不上自动降级为直查数据库 |
| Docker Desktop | 24+ | 可选，只是用来起 MySQL/Redis 更省事 |

> `backend/mvnx.sh` 不是通用脚本：它是「本机没装全局 `mvn`」时的启动器（用 java 拉起 `~/.m2/wrapper/dists` 里的 Maven）。装了 mvn 就直接用 `mvn`。

### 2.2 起数据库（两条路，选一条）

**路线 A：用 docker-compose（有 Docker 时）**

```bash
cd backend
docker compose up -d
```

| 服务 | 宿主机端口 | 账号 |
| --- | --- | --- |
| MySQL 8.4 | **3316** | `campus` / `campus123456` |
| Redis 7.4 | **6380** | 无密码 |

端口是刻意错开的（8080/8081/6379 常被别的项目占用）。首次启动会自动执行 `db/init/` 下的建表与种子脚本。

**路线 B：用本机已装的 MySQL（没有 Docker 时）**

不要改 `application.yml`。用 Spring Boot 原生环境变量覆盖，改完即走：

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/campus_club?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export SPRING_DATASOURCE_USERNAME=campus
export SPRING_DATASOURCE_PASSWORD=campus123456
```

库要先建好并灌入种子数据：

```bash
mysql -ucampus -pcampus123456 campus_club < backend/src/main/resources/db/init/01_schema.sql
mysql -ucampus -pcampus123456 campus_club < backend/src/main/resources/db/init/02_seed.sql
```

### 2.3 起后端

```bash
cd backend
mvn spring-boot:run
# 或先打包再跑
mvn -DskipTests package
java -jar target/campus-club-backend-0.1.0-SNAPSHOT.jar --server.port=8090
```

验证它真的起来了（**用 liveness 而不是综合 health**：Redis 没起时综合检查会 503，但接口本身可用）：

```bash
curl -s http://127.0.0.1:8090/actuator/health/liveness      # {"status":"UP"}
curl -s "http://127.0.0.1:8090/api/activities?pageSize=5"    # total = 5
```

### 2.4 起前端

```bash
npm install
npm run dev:h5          # H5，浏览器打开终端里给的地址
npm run dev:mp-weixin   # 小程序：产物在 dist/dev/mp-weixin，用微信开发者工具导入
```

前端默认请求同源 `/api`，H5 由 Vite 代理到 8090（`vite.config.ts` 的 `server.proxy`）。非 H5 端（小程序 / App）走 `src/utils/request.ts` 里的 `BASE_URL`，默认 `http://127.0.0.1:8090`；**真机调试必须改成电脑的局域网 IP**，否则手机上的 `127.0.0.1` 是手机自己。

### 2.5 种子数据（断言都基于它）

| 表 | 条数 |
| --- | --- |
| `activity` | 5 |
| `club` | 4 |
| `sys_user` | 8 |
| `activity_registration` | 8 |
| `activity_attendance` | 2 |
| `notification` | 5 |

演示账号密码统一 `123456`：

| 用户名 | 角色 | 用途 |
| --- | --- | --- |
| `student1` | STUDENT | 报名、取消、签到 |
| `linqingya` | CLUB_ADMIN | 社团 1 负责人（能看本社团名单、能代签） |
| `zhouyiran` | CLUB_ADMIN | 社团 2 负责人（访问社团 1 的数据应当 403） |
| `admin` | PLATFORM_ADMIN | 平台管理员（看全部） |

> 跑完 `verify-*.sh` 会往库里写数据。这些脚本的收尾只能把活动「下架」，不能删除（设计如此，见 README「只有草稿能删」）。想彻底复原就重跑 `01_schema.sql` + `02_seed.sql`。

---

## 3. 提交前必跑的验证清单

**顺序有意义**：单测最快暴露逻辑错误，端到端最后确认「真的能跑」。

> 连库的测试读的是 `application.yml`（默认 `127.0.0.1:3316`，即 docker-compose 的端口）。
> 如果你走的是路线 B（本机 3306），**先把 2.2 的三个 `SPRING_DATASOURCE_*` 环境变量 export 出来再跑**，
> 否则测试会连不上库并报一串 `Communications link failure`。

| # | 命令 | 期望 | 大致耗时 |
| --- | --- | --- | --- |
| 1 | `cd backend && mvn test` | `Tests run: 219, Failures: 0, Errors: 0` | 约 1 分钟 |
| 2 | `npx vue-tsc --noEmit` | 无输出 | 约 20 秒 |
| 3 | `npm run build:h5` | `DONE Build complete.` | 约 20 秒 |
| 4 | `npm run build:mp-weixin` | `DONE Build complete.` | 约 20 秒 |
| 5 | 起服务后 `bash verify-p9.sh` | `PASS=63 FAIL=0` | 约 30 秒 |
| 6 | `bash verify-p10.sh` | `PASS=55 FAIL=0` | 约 30 秒 |

端到端脚本按阶段划分，改到哪块就跑哪个：

| 脚本 | 覆盖 |
| --- | --- |
| `verify-p4.sh` | 认证与权限（401/403 分离、令牌过期/篡改） |
| `verify-p5.sh` | 缓存（命中、失效、故障降级） |
| `verify-p6.sh` | 可观测性（traceId、访问日志、慢请求） |
| `verify-p8.sh` | 异步通知（提交后落库、回滚后为零） |
| `verify-p9.sh` | 活动增删改、状态机、管理端列表与单条详情、可管理社团 |
| `verify-p10.sh` | 签到、代签、志愿时长 |
| `backend/bench/run-p7.sh` | 压测（不是断言脚本，产出数字写进 `PERFORMANCE.md`） |

改前端时补一条**手动**验证：H5 里把登录 → 报名 → 我的报名 → 签到 → 活动管理这条链路点一遍。仓库里没有浏览器自动化，这一步没人替你做。

---

## 4. 仓库地图

```text
.
├── README.md                  # 项目主文档：功能、接口、设计取舍、已知限制
├── docs/HANDOVER.md           # 本文件：分支、发布、验证、环境坑
├── src/                       # uni-app 前端（Vue 3 + TS + Pinia）
│   ├── api/                   # 按后端资源拆分的请求层，错误码映射在 utils/request.ts
│   ├── pages/                 # 页面：列表/详情/登录/我的报名/签到/活动管理/编辑
│   └── utils/                 # request 封装、格式化、安全区
└── backend/                   # Spring Boot 3.2 + MyBatis-Plus
    ├── src/main/java/com/campus/club/
    │   ├── activity/          # 活动：读模型、写模型、状态机、缓存
    │   ├── registration/      # 报名：并发占位、取消
    │   ├── attendance/        # 签到：时间窗、代签、志愿时长
    │   ├── auth/  user/       # JWT 与登录
    │   ├── club/              # 社团与归属校验（ClubOwnershipGuard）
    │   ├── cache/  notification/  web/  config/
    │   └── common/            # ApiResponse / ErrorCode / BusinessException
    ├── src/main/resources/
    │   ├── application.yml    # 端口、数据源、缓存 TTL、线程池
    │   ├── mapper/*.xml       # 手写 SQL（并发控制都在这里）
    │   └── db/init/           # 建表 + 种子数据
    ├── verify-p*.sh           # 真实 HTTP 端到端断言
    └── bench/                 # 压测脚本
```

要改功能，先看这四个文件（顺序即依赖方向）：

1. `backend/src/main/java/com/campus/club/common/ErrorCode.java` —— 所有错误码，前端按它提示
2. `backend/src/main/java/com/campus/club/activity/service/impl/ActivityServiceImpl.java` —— 读缓存 + 写状态机
3. `backend/src/main/resources/mapper/ActivityMapper.xml` —— 并发控制写在这条 SQL 里
4. `src/utils/request.ts` —— 令牌注入、401 处理、错误码到文案的映射

---

## 5. 敏感信息纪律

### 5.1 仓库里现有的「口令」是什么性质

| 位置 | 内容 | 判断 |
| --- | --- | --- |
| `application.yml` | `campus` / `campus123456` | 本地 docker-compose 演示值，**不是生产凭据** |
| `docker-compose.yml` | `root123456`（可用环境变量覆盖） | 同上，仅本地容器 |
| `db/init/02_seed.sql` | 演示账号密码统一 `123456`（库存 BCrypt 哈希） | 演示数据 |
| `auth/JwtProperties.java` | `DEV_SECRET` 内置开发密钥 | 启动会打警告；**生产必须覆盖** |
| 种子手机号 / 邮箱 | `138000000xx`、`@campus.edu` | 合成数据，非真实个人信息 |

结论：这些可以公开。真正的纪律是下面两条。

### 5.2 绝不能进仓库的

- `.env` / `.env.local` / `backend/.env` —— `.gitignore` 已挡住
- `.workbuddy/` —— 本地会话数据与临时脚本（里面可能有真实口令），整体忽略
- 任何真实的 GitHub Token、云厂商 AK/SK、私钥
- 面试/作业场景下的他人真实数据

提交前自查（应全部无输出）：

```bash
git grep -nIE "ghp_[A-Za-z0-9]{20,}|github_pat_|AKIA[0-9A-Z]{16}|BEGIN (RSA|OPENSSH) PRIVATE KEY"
git status --ignored --short | grep -E "\.env|\.workbuddy" | head
```

### 5.3 上线前必须覆盖的配置

| 配置 | 环境变量 | 说明 |
| --- | --- | --- |
| JWT 密钥 | `APP_JWT_SECRET` | 对应 `app.jwt.secret`（Spring 松散绑定：点换下划线、全大写）。不覆盖就会用内置开发密钥，任何人可伪造令牌 |
| 数据库 | `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | 不要改 yml 里的默认值 |
| 缓存开关 | `APP_CACHE_ENABLED` | 多实例部署时缓存一致性要重新评估 |
| 端口 | `SERVER_PORT` | 默认 8090 |

> `APP_JWT_SECRET` 这个名字是实测过的（不是照抄）：设置它启动后不再出现
> `正在使用内置的开发用 JWT 密钥` 警告；不设置则每次启动都会 WARN 一行。
> 想自己确认，用 `grep "内置的开发用" 启动日志` 看有没有。

---

## 6. 已知限制

不在这里重复，见 [`README.md` → 后端服务与接口 → 已知限制](../README.md)。交接时**先读那一节**：里面每条都写了「为什么不做」和「要做需要什么」，比看代码猜快得多。

---

## 7. Windows + Git Bash 环境坑

这一节记录的是**实际踩过的**，不是理论上的可能性。用 macOS/Linux 的可以跳过 7.1–7.6，但 7.7 对谁都成立。

### 7.1 切分支可能要 30 秒，前台执行会被打断

两个分支之间有 129 个文件的差异，`git checkout` 一次约 30 秒。如果命令有超时限制，会被中途杀掉，留下**半删的工作区**和 `.git/index.lock`。

恢复顺序（前提：改动都已提交，所以一定救得回来）：

```bash
rm -f .git/index.lock          # 先确认没有 git 进程在跑
git restore --worktree -- .    # 从 HEAD 全量还原工作区
git status --short             # 期望：空
```

然后**换到后台/加长超时**重试。

### 7.2 推送在受限网络里会断

若本机走代理或处于受限沙箱，`git push` 常见两种报错：

- `schannel: server closed abruptly (missing close_notify)`
- `Empty reply from server` / `Recv failure: Connection was reset`

而同一时刻 `git ls-remote` 是正常的 —— 很容易误判成「网络没问题」。原因通常是**上传的数据流被中途切断**，拉取（GET）却没事。

处理顺序：

1. `git -c http.version=HTTP/1.1 push origin main`（排除 HTTP/2 问题）
2. `git -c http.proxy= -c https.proxy= push origin main`（绕开代理直连）
3. 仍然失败 → 在**不受限的终端**里手动执行同一条 `git push`（这是最可靠的一步）

### 7.3 `git fetch` / `update-ref` 可能「返回成功但没写引用」

症状：`git status` 显示 `## main...origin/main [gone]`，`git branch -vv` 也显示 `[xxx: gone]`，但远端分支明明存在。

原因：`.git/refs/remotes/origin/` 目录不存在时，某些环境下写引用会静默失败。手工补：

```bash
mkdir -p .git/refs/remotes/origin
printf '%s\n' "$(git rev-parse main)" > .git/refs/remotes/origin/main
git status --short --branch    # 期望：## main...origin/main（没有 gone）
```

### 7.4 curl 的两个细节

- 本机若设了 `HTTP_PROXY`，访问 `127.0.0.1` 也会被代理，返回 502 → 加 `--noproxy '*'`
- Windows 原生 curl 的 `-o` 不认 `/dev/null` 与 MSYS 路径 `/d/...` → 用 `NUL` 或 `D:/...` 盘符路径

### 7.5 控制台编码是 GBK

中文在 Git Bash 里常显示成乱码（`闄堝瓙鏄`），但**文件本身是 UTF-8**，不要因此去改文件编码。读日志用 `grep -a`，否则含中文的行会被当成二进制吞掉。跑 jar 时加：

```bash
java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar ...
```

### 7.6 jar 被占用时打包会失败

服务还在跑的时候 `mvn package` 会因 Windows 文件锁失败。先停服务再打包。停服务：

```bash
netstat -ano | grep ':8090.*LISTENING'      # 拿最后一列的 PID
MSYS_NO_PATHCONV=1 taskkill /PID <pid> /F
```

（Git Bash 的 `kill` 杀不掉 Windows 进程；`taskkill //PID` 会被路径转换弄坏，所以要 `MSYS_NO_PATHCONV`。）

### 7.7 用 AI 代理继续开发时

- 长命令（打包、全量测试、`git checkout`）**放后台跑**，别让它卡在前台超时
- 起服务必须用后台方式，`nohup ... &` 在某些沙箱里会被回收
- 需要联网写操作（`git push`）时，若沙箱代理不透传上传流，要显式申请跳出沙箱执行
- 每次改完仍按第 3 节跑验证；**AI 说「已完成」不等于验证过**

---

## 8. 交接检查清单

新接手的人照这个清单走一遍，能覆盖 90% 的坑：

- [ ] `git log --oneline -3` 看到的是最新提交，`git status` 干净
- [ ] `git merge-base --is-ancestor main backend-refactor` 返回成功（说明没分叉）
- [ ] 数据库起来了，`SELECT COUNT(*) FROM activity` = 5
- [ ] 后端能启动，`/actuator/health/liveness` 返回 UP
- [ ] `GET /api/activities` 返回 total = 5
- [ ] 用 `student1/123456` 能登录并拿到令牌
- [ ] `mvn test` 219 全绿
- [ ] `npx vue-tsc --noEmit` 无输出
- [ ] `npm run build:h5` 成功
- [ ] 读一遍 README 的「已知限制」，知道哪些是没做的、哪些是不打算做的

---

## 9. 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-11-06 | 分支治理：`main` 从最初 MVP 快进到 `backend-refactor` 的最新提交（14 个提交，零分叉、零强推、零删除）；推送前清掉 `bench/run-p7.sh` 里写死的本机绝对路径；新增本文件 |
