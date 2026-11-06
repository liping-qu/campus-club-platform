# 校园社团管理平台

## 一、项目简介

面向校园场景的社团与活动管理平台：学生端完成浏览社团、报名活动、签到与查看志愿时长；
社团负责人完成活动的新建、编辑、发布、下架与签到管理；平台管理员可管理全部社团。

后端为独立 Spring Boot 服务（Java 17 + MyBatis-Plus + MySQL 8 + Redis 7），
前端为 uni-app（Vue 3 + TypeScript + Pinia），一套代码构建 H5 与微信小程序。

**已实现能力**：活动查询、并发安全的报名/取消报名、JWT 登录与「角色 + 数据归属」两层权限、
活动读缓存、traceId 贯穿的日志与指标、事务提交后投递的异步站内通知、活动增删改与状态机、
签到 / 代签 / 志愿时长统计。目前 **219 个自动化测试全部通过**（含 40 线程真实并发用例、
真 Redis 缓存用例），并有真实压测数据，见 [`backend/PERFORMANCE.md`](backend/PERFORMANCE.md)。

完整接口清单与逐项完成度见下文表格（以表格为准）。

---

## 二、技术栈

前端侧选型与用途：

| 技术 | 用途 |
| --- | --- |
| uni-app | 一套代码构建 H5 与微信小程序，两端仍需分别调试 |
| Vue 3 | 页面渲染与状态响应：列表、报名/收藏按钮状态 |
| TypeScript | 类型定义：接口出入参、资料字段、请求封装 |
| uni-ui / UniIcons | 图标与少量基础组件 |
| SCSS | 主题、卡片与跨端样式差异处理 |
| 本地存储 | 保存收藏、草稿等客户端状态（业务数据走后端接口） |
| Pinia | 全局状态管理，入口已注册 |
| Node.js / npm | 依赖安装与构建工具链 |
| Vite | 开发服务器与构建打包 |
| Git / GitHub | 版本管理与代码托管 |

**已接入：** Java 17 + Spring Boot 3.2 + MySQL 8（活动查询、报名、登录）+ Spring Security / JWT（认证与权限）+ Redis（活动读缓存），均有真实接口与自动化测试。**尚未接入：** RabbitMQ、uniCloud（方案评估见 `backend/docs/async-notification.md`）。

### 相关文档

| 文档 | 内容 |
| --- | --- |
| [`backend/PERFORMANCE.md`](backend/PERFORMANCE.md) | 压测方法、真实数据与限制说明 |
| [`backend/docs/async-notification.md`](backend/docs/async-notification.md) | 异步通知的取舍，以及改造为 MQ 的清单 |

### 前端代码位置

| 文件 | 作用 |
| --- | --- |
| `src/pages/activity-list/index.vue` | 活动列表、关键词与分类筛选 |
| `src/pages/activity-detail/index.vue` | 活动详情、报名 / 取消报名、签到入口 |
| `src/pages/attendance/index.vue` | 我的签到记录与累计志愿时长 |
| `src/pages/activity-manage/index.vue` | 管理端活动列表（按状态分 Tab，行内发布 / 下架 / 删除） |
| `src/pages/activity-edit/index.vue` | 新建 / 编辑活动表单 |
| `src/pages/registrations/index.vue` | 我的报名 |
| `src/utils/request.ts` | 统一请求封装、令牌注入、错误码映射 |
| `src/api/` | 按业务分组的接口定义 |
| `src/utils/safe-area.ts` | 按设备信息计算顶部安全区 |
| `src/styles/theme.scss` | 共享样式与跨端效果处理 |
| `src/pages.json` | 页面注册与导航配置 |


### 本地运行

#### 环境要求

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 17 | 后端运行 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 18+ | 前端构建 |
| Docker Desktop | 24+ | 启动 MySQL 8 与 Redis 7 |

#### 目录结构

```text
.
├── src/                       # uni-app 前端源码
├── backend/                   # Java 后端（独立 Maven 工程）
│   ├── docker-compose.yml
│   ├── pom.xml
│   ├── bench/                 # 压测脚本
│   ├── docs/                  # 设计说明（异步通知等）
│   ├── verify-p*.sh           # 各阶段的端到端验证脚本
│   └── src/main/
│       ├── java/com/campus/club/
│       └── resources/
│           ├── application.yml
│           ├── mapper/ActivityMapper.xml
│           └── db/init/       # 建表脚本与种子数据
└── ...
```

#### 第一步：启动数据库与缓存

```bash
cd backend
docker compose up -d
```

| 服务 | 宿主机端口 | 说明 |
| --- | --- | --- |
| MySQL 8.4 | 3316 | 库名 `campus_club`，账号 `campus` / `campus123456` |
| Redis 7.4 | 6380 | 活动读缓存，已接入；连不上会自动降级为直查数据库 |

端口刻意避开 8080 / 8081 / 6379 —— 本机上这些已被其它项目占用。

> **没有 Redis 也能跑**：缓存连不上时应用照常启动，只是每次读都查库（启动日志里能看到 `Cannot reconnect ... 6380`）。
> 想彻底关掉缓存（比如对比压测）用 `APP_CACHE_ENABLED=false`。

首次启动时容器会自动执行 `src/main/resources/db/init/` 下的脚本建表并写入种子数据。检查：

```bash
docker compose ps
docker exec campus-club-mysql mysql -ucampus -pcampus123456 campus_club -e "SELECT COUNT(*) FROM activity;"
```

#### 第二步：启动后端

```bash
cd backend
mvn spring-boot:run
```

如果本机存在 `SERVER_PORT` 环境变量覆盖了端口，用命令行参数强制指定（命令行优先级高于配置文件）：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8090
```

验证：

```bash
curl http://127.0.0.1:8090/actuator/health
curl "http://127.0.0.1:8090/api/activities?page=1&size=5"
```

接口文档（Swagger UI）：`http://127.0.0.1:8090/swagger-ui.html`

**Windows 上务必加 UTF-8 输出参数**（打包后 `java -jar` 启动时）：

```bash
java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 \
     -jar target/campus-club-backend-0.1.0-SNAPSHOT.jar --server.port=8090
```

不加这三个参数的后果不是乱码那么轻：JDK 17 在 Windows 上把 `System.out` 按控制台代码页（GBK）编码，
日志文件里就是 GBK 字节流。服务本身没问题，但**用 `grep` 搜中文日志会一条都搜不到**（
P8 的验证脚本第一版就栽在这里：`grep "站内通知已投递"` 返回 0，而日志里明明有这行）。

健康检查分三档，含义不同：

```bash
curl http://127.0.0.1:8090/actuator/health            # 综合：任一依赖挂就 503
curl http://127.0.0.1:8090/actuator/health/liveness   # 存活：进程 + 数据库，不含 Redis
curl http://127.0.0.1:8090/actuator/health/readiness  # 就绪：依赖齐了才接流量
```

#### 第三步：启动前端

```bash
npm install
npm run dev:h5
```

H5 开发服务器通过 Vite 代理把 `/api` 转发到 `127.0.0.1:8090`，前端代码里用同源路径即可，不需要处理跨域。

小程序端把 `src/utils/request.ts` 里的 `BASE_URL` 指向本机后端，并在微信开发者工具中勾选「不校验合法域名」。

构建命令：

```bash
npm run build:h5
npm run build:mp-weixin
```

微信开发者工具导入 `dist/build/mp-weixin`，填写自己的 AppID 后编译、预览。改代码要改 `src`，不要直接改 `dist`：后者是构建结果，下次构建可能被覆盖。

#### 运行测试

```bash
cd backend
mvn test
```

本机没有全局 `mvn` 时，用仓库里的 `mvnx.sh`（它用 `~/.m2/wrapper` 里的 maven 直接拉起，
效果与 `mvn` 一致，省去安装步骤）：

```bash
cd backend
sh ./mvnx.sh test
```

当前 219 个测试全部通过：

| 测试类 | 数量 | 类型 | 连数据库 |
| --- | --- | --- | --- |
| `ActivityServiceImplTest` | 9 | 单元测试（Mockito） | 否 |
| `ActivityServiceCacheTest` | 9 | 缓存编排（Mockito） | 否 |
| `ActivityAdminServiceTest` | 28 | **活动增删改的单元测试**：越权、状态机、不变量、管理端详情 | 否 |
| `ActivityPhaseTest` | 6 | 展示阶段推导顺序（草稿/下架/满员/结束谁优先） | 否 |
| `ActivityApiIntegrationTest` | 9 | 接口测试（MockMvc） | 是 |
| `ActivityAdminApiIntegrationTest` | 14 | **活动管理接口**：完整生命周期、横向越权、缓存失效、管理端详情 | 是 |
| `RegistrationServiceImplTest` | 21 | 单元测试（Mockito），含「已签到不能取消报名」 | 否 |
| `RegistrationApiIntegrationTest` | 8 | 接口测试（MockMvc） | 是 |
| `RegistrationConcurrencyTest` | 8 | **真实并发测试（40 线程）** | 是 |
| `AuthApiIntegrationTest` | 11 | 认证测试（登录、令牌过期/篡改、账号禁用） | 是 |
| `RegistrationPermissionApiIntegrationTest` | 6 | 权限测试（角色、横向越权） | 是 |
| `AttendanceServiceImplTest` | 20 | **签到分支**：窗口边界、报名前置、代签、时长回写 | 否 |
| `AttendanceApiIntegrationTest` | 21 | **签到接口**：真落库、唯一键、时间窗、代签、签到与取消报名互斥 | 是 |
| `ClubOwnershipGuardTest` | 6 | 归属校验单点（平台管理员 / 本社团 / 别家 / 不存在 / 无负责人 / 未登录） | 否 |
| `ClubApiIntegrationTest` | 5 | **可管理社团**：负责人只看自己的、平台管理员看全部、已解散被过滤、角色/登录边界 | 是 |
| `ActivityCacheTest` | 15 | **缓存行为测试（内嵌真 Redis）**，含命中率指标 | 否（用 Redis） |
| `CacheStampedeTest` | 2 | **并发击穿/穿透测试**：30 线程撞冷 key，断言只回源 1 次 | 否（用 Redis） |
| `AfterCommitTest` | 4 | 事务提交后回调的时机 | 否 |
| `TraceIdFilterTest` | 4 | traceId 生成、沿用、拒绝非法值、MDC 清理 | 否 |
| `AccessLogFilterTest` | 5 | 访问日志内容与级别（挂 Logback appender 直接看输出） | 否 |
| `AsyncConfigTest` | 3 | MDC 跨线程传递、任务结束后清理、无线程上下文时不凭空造 | 否 |
| `NotificationFlowIntegrationTest` | 5 | **异步通知**：提交后落库、**回滚后一条都没有**、越权改不到 | 是 |

只跑其中一组：

```bash
cd backend
mvn test -Dtest=RegistrationConcurrencyTest
mvn test -Dtest=AuthApiIntegrationTest
```

**连的库不是 Docker 那个怎么办**：连数据库的测试会读 `application.yml`（默认 `127.0.0.1:3316`，即 docker-compose 映射出来的端口）。
如果你本机另有 MySQL，用 Spring Boot 原生环境变量覆盖即可，**不要改配置文件**：

```bash
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/campus_club?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true" \
mvn test
```

前提是这个库里已经跑过 `db/init/01_schema.sql` 和 `02_seed.sql`，并且账号密码与配置一致。

---

## 后端服务与接口

### 技术栈（实际使用）

| 技术 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 17 | 运行环境 |
| Spring Boot | 3.2.5 | Web 框架 |
| Spring Security | 6.2.4 | 认证与授权（过滤器链、方法级鉴权、BCrypt） |
| jjwt | 0.12.5 | JWT 签发与校验 |
| MyBatis-Plus | 3.5.5 | 数据访问 + 分页插件 |
| MySQL | 8.4 | 业务数据 |
| Spring Data Redis + Lettuce | Boot 管理版本 | 活动读缓存（真实 Redis 7.4） |
| Micrometer + Actuator | Boot 管理版本 | 缓存命中率等运行时指标 |
| Spring Validation | jakarta.validation | 参数校验 |
| springdoc-openapi | 2.3.0 | 接口文档 |
| JUnit 5 + Mockito | Spring Boot 管理版本 | 单元与集成测试 |
| embedded-redis | 1.4.3（test） | 内嵌 Redis，缓存测试不依赖本机环境 |
| Spring `@Async` + 自建线程池 | Boot 管理版本 | 站内信异步投递（有界队列 + Abort 策略 + MDC 传递） |
| Node 22（仅压测） | 22.x | `bench/loadtest.js` 压测客户端，只用标准库，无第三方依赖 |
| Maven | 3.8.5 | 构建 |

Redis 客户端（`spring-boot-starter-data-redis`）随缓存阶段接入；异步通知没有引入任何新依赖。
**尚未引入：RabbitMQ** —— 是否引入的评估见 `docs/async-notification.md`。

### 分层结构

```text
controller  → 只做参数接收、校验触发、响应包装
service     → 业务规则、事务边界、派生字段计算
mapper      → 只负责 SQL 与结果映射
entity      → 数据库实体（不直接对外暴露）
dto / vo    → 入参与出参，与实体解耦
config      → MyBatis-Plus 分页、Jackson 时间格式
exception   → 全局异常处理与统一错误码
```

### 已实现接口

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | 登录，返回 Bearer 令牌 | 否 |
| GET | `/api/auth/me` | 当前登录用户的最新资料 | 是 |
| GET | `/api/activities` | 分页查询，支持 keyword / category / phase | 否 |
| GET | `/api/activities/{id}` | 活动详情 | 否 |
| POST | `/api/activities/{id}/registrations` | 报名活动 | 是 |
| DELETE | `/api/activities/{id}/registrations/me` | 取消报名 | 是 |
| GET | `/api/activities/{id}/registrations/me` | 我在该活动的报名状态 | 是 |
| GET | `/api/activities/{id}/registrations` | 活动报名名单 | 是（社团负责人 / 平台管理员） |
| POST | `/api/activities` | 新建活动，初始为草稿 | 是（社团负责人 / 平台管理员） |
| PUT | `/api/activities/{id}` | 修改活动（整体覆盖可编辑字段） | 是（本社团负责人 / 平台管理员） |
| POST | `/api/activities/{id}/publish` | 发布草稿/已下架的活动 | 是（本社团负责人 / 平台管理员） |
| POST | `/api/activities/{id}/unpublish` | 下架已发布的活动 | 是（本社团负责人 / 平台管理员） |
| DELETE | `/api/activities/{id}` | 删除**草稿**；已发布的只能下架 | 是（本社团负责人 / 平台管理员） |
| GET | `/api/activities/manage` | 管理端列表（含草稿/已下架），按归属过滤 | 是（社团负责人 / 平台管理员） |
| GET | `/api/activities/manage/{id}` | 管理端单条详情（草稿/已下架也能读，供编辑页回填） | 是（本社团负责人 / 平台管理员） |
| GET | `/api/clubs/mine` | 我能管理的社团（新建活动的下拉框数据源） | 是（社团负责人 / 平台管理员） |
| POST | `/api/activities/{id}/check-in` | 本人签到（需已报名，窗口为开始前 30 分钟至结束） | 是 |
| GET | `/api/activities/{id}/check-in/me` | 我的签到状态（未签到返回 `checkedIn=false`，不是 404） | 是 |
| POST | `/api/activities/{id}/attendances` | 负责人代签 / 补录，不受时间窗限制 | 是（本社团负责人 / 平台管理员） |
| GET | `/api/activities/{id}/attendances` | 活动签到名单（含签到方式与时长） | 是（本社团负责人 / 平台管理员） |
| PUT | `/api/activities/{id}/attendances/{userId}/hours` | 修正某人某次活动计入的志愿时长 | 是（本社团负责人 / 平台管理员） |
| GET | `/api/attendances/me` | 我的签到记录 + 累计志愿时长 | 是 |
| GET | `/api/registrations/me` | 我的报名列表 | 是 |
| GET | `/api/notifications` | 我的通知列表（分页，pageSize ≤ 50） | 是 |
| GET | `/api/notifications/unread-count` | 未读数（红点） | 是 |
| POST | `/api/notifications/{id}/read` | 标记单条已读；越权/重复标记返回 404 | 是 |
| POST | `/api/notifications/read-all` | 全部标记已读，返回本次更新条数 | 是 |
| GET | `/actuator/health` | 健康检查（含 Redis 状态） | 否 |
| GET | `/actuator/health/liveness` | 存活探针（不含 Redis） | 否 |
| GET | `/actuator/health/readiness` | 就绪探针（含 Redis） | 否 |
| GET | `/actuator/metrics` | 指标明细，如 `app.cache.requests`、`http.server.requests` | 是（平台管理员） |
| GET | `/actuator/metrics/{name}` | 单个指标的取值与 tag 下钻 | 是（平台管理员） |

「认证」为「是」的接口需要在请求头带上 `Authorization: Bearer <token>`。

```bash
# 登录拿令牌
curl -X POST http://127.0.0.1:8090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"student1","password":"123456"}'

# 带令牌访问
curl http://127.0.0.1:8090/api/registrations/me -H "Authorization: Bearer <token>"
```

演示账号（`02_seed.sql` 里密码统一为 `123456`）：

| 用户名 | 角色 | 用途 |
| --- | --- | --- |
| `student1` | STUDENT | 报名、取消报名 |
| `linqingya` | CLUB_ADMIN | 社团 1（青年志愿者协会）负责人，可看该社团活动的报名名单 |
| `zhouyiran` | CLUB_ADMIN | 社团 2（摄影协会）负责人，访问社团 1 的名单会被 403 |
| `admin` | PLATFORM_ADMIN | 平台管理员，可看所有社团 |

`phase` 取值：`ALL`、`OPEN`（报名中）、`UPCOMING`（即将开始）、`FULL`（已满员）、`FINISHED`（已结束）。

请求示例：

```bash
curl "http://127.0.0.1:8090/api/activities?keyword=%E6%91%84%E5%BD%B1&phase=OPEN&page=1&size=10"
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "records": [
      {
        "id": 2,
        "title": "秋日校园摄影采风",
        "clubId": 2,
        "clubName": "摄影协会",
        "location": "南校区林荫道",
        "category": "兴趣艺术",
        "startTime": "2026-11-15 14:00:00",
        "endTime": "2026-11-15 17:00:00",
        "signupDeadline": "2026-11-14 20:00:00",
        "capacity": 30,
        "joinedCount": 2,
        "remaining": 28,
        "status": 1,
        "phase": "OPEN",
        "phaseText": "报名中",
        "coverColor": "#ffd9b8"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10,
    "pages": 1
  }
}
```

错误响应（活动不存在，HTTP 404）：

```json
{ "code": 40401, "message": "活动不存在" }
```

错误响应（参数越界，HTTP 400）：

```json
{ "code": 40000, "message": "size 每页条数不能超过 100" }
```

### 报名并发控制：这一节是全项目的核心

活动报名是典型的「秒杀」式写场景：**一条记录被大量请求同时争抢**。这里不放任何编造的性能数字，只写清楚矛盾在哪、用什么手段解决、怎么证明它解决了。

#### 一、直觉写法为什么会超卖

```java
Activity a = mapper.selectById(id);                  // 两个线程都读到 joined = 9, capacity = 10
if (a.getJoinedCount() < a.getCapacity()) {          // 两个线程都判断「还有名额」
    mapper.updateJoinedCount(id, a.getJoinedCount() + 1);  // 两个线程都写 10
}
```

结果是 **2 人报名成功，但只占用了 1 个名额**。数据库里 10 条报名记录，`joined_count` 却是 10 —— 看起来没问题，实际有 11 个人以为报上了。

常见的三种补丁，各有各的问题：

| 补丁 | 问题 |
| --- | --- |
| `synchronized` / 锁方法 | 只锁得住单个 JVM。多实例部署、或者哪天加了负载均衡，立刻失效 |
| 把隔离级别提到 `SERIALIZABLE` | 全表级别的串行化，代价远大于收益，还容易死锁 |
| 引入 Redis / ZooKeeper 分布式锁 | 能解决，但为了一个自增引入新中间件，可用性和运维成本都被放大 |

#### 二、实际采用：条件更新 + 判断受影响行数

```sql
UPDATE activity
SET joined_count = joined_count + 1
WHERE id = #{activityId}
  AND status = 1
  AND signup_deadline > NOW()
  AND joined_count < capacity
```

关键点：**「还有没有名额」的判断和「加一」被压进了同一条 SQL**。InnoDB 对同一条记录的更新加行锁，并发请求在这里排队，第二个线程执行时读到的一定是最新值，`WHERE` 自然不成立，返回受影响行数 `0`。

业务层必须检查这个返回值：

```java
int occupied = activityMapper.occupySlot(activityId);
if (occupied == 0) {
    throw explainOccupyFailure(activityId);   // 抢不到就失败，不能默认成功
}
```

「看到 0 还继续往下写」就是超卖的起点。

#### 三、三层防线（不是一层）

| 层 | 手段 | 挡住什么 |
| --- | --- | --- |
| 1 | 条件 `UPDATE` + 检查受影响行数 | 正常并发下的超卖 |
| 2 | `UNIQUE (activity_id, user_id)` | 同一用户重复报名（业务判断写错也拦得住） |
| 3 | `CHECK (joined_count BETWEEN 0 AND capacity)` | 任何绕过前两层把计数改坏的路径 |

第 3 层不是摆设，下面有实测。

#### 四、事务边界与加锁顺序

报名在**一个事务**里做三件事：占位 → 写报名记录 → 回读计数。任何一步失败，前面的自增一起回滚，不会出现「计数涨了但人没报名」。

取消报名同理：释放名额 → 改状态。**报名和取消必须都用相同的加锁顺序：先 `activity` 行，再 `activity_registration` 行。** 如果取消反过来先锁报名行，两个方向的并发请求就会互相等待对方持有的锁，形成死锁。

#### 五、错误码语义

| 场景 | HTTP | code | 说明 |
| --- | --- | --- | --- |
| 名额已满 | 409 | 40901 | `ACTIVITY_FULL` |
| 重复报名 | 409 | 40902 | `ALREADY_REGISTERED` |
| 未报名却取消 | 409 | 40903 | `NOT_REGISTERED` |
| 活动已下架 | 409 | 40904 | `ACTIVITY_CLOSED` |
| 报名已截止 | 409 | 40905 | `SIGNUP_CLOSED` |
| 条件满足却更新 0 行 | 409 | 40908 | `REGISTER_CONFLICT`，可重试的竞态 |

最后一个 `40908` 值得单独说：占位失败后代码会用 `SELECT ... FOR UPDATE` 回读最新状态来解释原因。如果回读发现「名额还有、截止时间没到、活动也正常」，就说明判断与更新之间状态被并发改了 —— 这是可重试的竞态。**不把它伪装成「名额已满」**，用户刷新一下就能成功，报「已满」反而是误导。

> 为什么解释原因要用 `FOR UPDATE`：在 MySQL 默认的 REPEATABLE READ 下，同一事务内普通 `SELECT` 读的是事务开始时的快照，拿旧数据解释失败原因，很容易把「名额已满」和「报名已截止」说反。

#### 六、怎么证明它真的不超卖

`RegistrationConcurrencyTest` 连的是真实 MySQL，用 `CountDownLatch` 让 32~40 个线程在同一时刻放行，抢占同一条记录：

```java
assertEquals(capacity, successCount);                                  // 成功人数恰好等于名额
assertEquals(capacity, joinedCountFromDb);                             // 冗余计数没有漂移
assertEquals(capacity, countOfRegistrationRowsWhereStatusIs1);         // 真实报名行数对得上
assertEquals(threads - capacity, countOf(ACTIVITY_FULL));              // 失败原因必须精确
```

三条断言缺一不可：只看「成功数」会漏掉「成功数与计数一致、但与报名记录不一致」这种更隐蔽的错。

**破坏性验证（一次真实的实验记录）**：为了确认这组测试不是「怎么写都能过」，把 `occupySlot` 里的 `AND joined_count < capacity` 临时删掉再跑一次：

```text
结果分布 = {ERROR=35, OK=5}
错误信息 = Check constraint 'ck_activity_joined' is violated.
```

5 个人正常报上（名额上限），另外 35 个请求被数据库的 CHECK 约束拦下并返回 500。也就是说：

- 测试**确实能发现**条件被删掉（它不会静默通过）；
- 第 3 层防线**真的在工作** —— 应用层写错时，数据仍然没有被写坏，退化成接口报错而不是脏数据。

这个实验做完了就把条件加回来了，`mvn test` 48 项全绿。

### 认证与权限：401 和 403 为什么要分开

#### 一、登录做了什么

```text
用户名密码 → 按唯一索引查用户 → BCrypt 校验 → 账号状态检查 → 签发 JWT
```

三个容易写错的细节：

1. **用户不存在和密码错误返回同一个码**（40101）。分开提示等于给攻击者一个账号存在性探测器。
2. **用户不存在时也跑一次 BCrypt**。否则「不存在」立刻返回、「密码错」要等 100ms 左右的计算，用响应时间就能批量判断哪些用户名真实存在。
3. **先校验密码，再校验账号状态**。反过来先返回「账号已禁用」，同样泄漏了账号存在性。

#### 二、JWT 里放了什么

```json
{ "iss": "campus-club", "sub": "6", "username": "student1", "role": "STUDENT",
  "iat": 1761800000, "exp": 1761807200 }
```

只放 **ID / 用户名 / 角色**。昵称、学院、手机号这些会变的信息不放 —— 令牌是签发那一刻的快照，放进去就会和数据库不一致。需要最新资料时用 `/api/auth/me` 查库。

> JWT 的 payload 是 **Base64 编码，不是加密**，任何人解开都能看到内容。因此绝不能在 payload 里放密码、手机号这类数据。签名只保证「没被篡改」，不保证「看不见」。

#### 三、认证在过滤器，授权分两层

```text
JwtAuthenticationFilter   解析令牌 → 写入 SecurityContext + CurrentUserContext
URL 规则（SecurityConfig） /api/** 需要登录；活动查询、登录、健康检查放行
@PreAuthorize              方法级角色校验，例如名单接口只允许 CLUB_ADMIN / PLATFORM_ADMIN
Service 内的归属校验        社团负责人只能看自己社团的活动
```

为什么不能只用 URL 规则：URL 规则表达不了「A 社团的负责人不能看 B 社团的名单」这种**数据级**条件。角色对、数据不对，就是横向越权。

这就是权限测试里那两条用例的意义：

| 场景 | 期望 |
| --- | --- |
| 社团 2 的负责人看社团 1 的活动名单 | 403（角色对，数据不对） |
| 普通学生看名单 | 403（角色不对） |

#### 四、状态码与业务码

| 场景 | HTTP | code | 前端该做什么 |
| --- | --- | --- | --- |
| 没带令牌 | 401 | 40100 | 跳登录页 |
| 令牌过期 | 401 | 40102 | 静默重新登录 |
| 令牌被篡改 / 签名不对 | 401 | 40103 | 清本地令牌，跳登录页 |
| 密码错误 | 401 | 40101 | 提示「用户名或密码错误」 |
| 账号被禁用 | 403 | 40301 | 提示联系管理员 |
| 登录了但没权限 | 403 | 40300 | 提示「无权操作」，不要跳登录页 |

**401 和 403 不能混**：401 是「你是谁我不知道」，403 是「我知道你是谁，但你不能干这个」。混成一个码，前端只能一律弹「操作失败」，用户既不知道该去登录，也不知道该找管理员。

#### 五、踩过的三个坑

1. **`@PreAuthorize` 抛出的异常会被全局异常处理的 catch-all 吞成 500**。
   方法级鉴权的异常在 DispatcherServlet 内部就冒出来了，走不到 Security 的 `AccessDeniedHandler`。
   必须在 `@RestControllerAdvice` 里显式处理 `AccessDeniedException`，否则「没权限」会变成「服务器错误」。

2. **Security 过滤器在 DispatcherServlet 之前，拿不到 `@RestControllerAdvice`**。
   所以 401/403 的响应体只能在 `AuthenticationEntryPoint` / `AccessDeniedHandler` 里手写，
   并且必须和 `ApiResponse` 格式完全一致，否则前端要写两套错误解析。

3. **通配符放行会把子资源一起放行**（这条是测试抓出来的）。
   原来写了 `GET /api/activities/**` 免登录，本意是「活动列表和详情公开」，
   结果把 `/api/activities/{id}/registrations`（社团内部名单）也一起放行了 ——
   匿名请求一路穿到 `@PreAuthorize` 才被拒，于是「没登录」被报成「没权限」（403 而不是 401）。
   两个修正缺一不可：
   - 在通配符**之前**补一条更具体的 `.requestMatchers(GET, "/api/activities/*/registrations").authenticated()`（匹配按顺序取第一条）；
   - 在 `AccessDeniedException` 的处理里区分匿名/已登录：匿名回 401，已登录回 403。
     一旦 advice 接住了这个异常，`ExceptionTranslationFilter` 原本「匿名 → 401」的翻译逻辑就失效了，必须自己补。

> 附带一个容易误判的现象：在合法 token 的**尾部追加字符**仍然能验签通过。
> 因为签名是 48 字节（HS384）= base64url 64 字符且无 padding，尾部多出的 6 bit 不足一个字节，解码器直接丢弃，
> 解出的签名和原签名完全相同。这不是漏洞（攻击者改不了解码后的字节），但写测试时要篡改签名里的某一位，而不是往后拼接。


#### 六、当前的取舍

- **令牌无法主动吊销**。JWT 无状态，签发后在有效期内一直可用；要支持「强制下线」需要引入令牌黑名单（Redis，按 `jti` 记录），属于用状态换能力，等真有这个需求再做。当前有效期 2 小时，风险可接受。
- **没有刷新令牌**。过期就重新登录。校园场景一天登录一两次，收益不足以抵消复杂度。
- **密钥有开发默认值**。`app.jwt.secret` 未配置时用内置值并打警告，生产必须通过环境变量 `APP_JWT_SECRET` 覆盖（Spring 松散绑定把 `app.jwt.secret` 映射成 `APP_JWT_SECRET`）。
- **签名算法由密钥长度决定**。jjwt 的 `Keys.hmacShaKeyFor` 会按长度自动选 HS256/384/512（32/48/64 字节），当前密钥 48 字节即 HS384。换个长度不同的密钥会静默换算法，旧令牌全部失效，所以生产要固定密钥、轮换时准备过渡期。
- **按角色授权，不是按权限点**。三种角色够用；真要做细粒度 RBAC（角色-权限-资源三张表）是另一个量级，当前业务撑不起这个复杂度。

### 缓存：只缓存数据，不缓存结论

活动是整个平台读得最多的地方：首页一进就是列表，点进去就是详情，报名前后都会刷新。所以 Redis 只做一件事 —— 把活动读模型挡在数据库前面。

#### 一、缓存了什么，存多久

| 数据 | key | TTL | 为什么是这个值 |
| --- | --- | --- | --- |
| 活动详情 | `club:activity:detail:{id}` | 10 分钟 ± 30 秒 | 标题、地点、规则几乎不变，改一次等 10 分钟不影响业务 |
| 活动列表 | `club:activity:list:v{版本}:{指纹}` | 60 秒 ± 30 秒 | 每次报名都会改 `joined_count`，只能短命 |
| 不存在标记 | 同上（值为 `__NULL__`） | 60 秒 ± 30 秒 | 它只用来挡穿透，不需要长命 |

列表的 `{指纹}` 是查询条件的 SHA-256 前 20 位（页码、条数、关键词、分类、阶段）。
不用 `String.hashCode()`：只有 32 位，在缓存键这个量级上碰撞概率不可忽略，一旦碰撞就会把 A 条件的列表返回给 B 条件。

#### 二、三个经典问题，对应三种做法

| 问题 | 现象 | 这里怎么解决 |
| --- | --- | --- |
| **穿透** | 一直请求不存在的 id，缓存永远不命中，每次都打库 | 查不到就写一个 `__NULL__` 标记并给短 TTL，下次直接判定「不存在」 |
| **击穿** | 热点 key 刚过期，几百个线程同时回源同一条数据 | `SETNX` 抢回填锁，只有抢到的线程查库；其余线程等 200ms 读缓存，等不到就自己查（宁可多查一次，也不让用户卡住） |
| **雪崩** | 大批 key 在同一秒失效，流量整批压到数据库 | TTL 加随机抖动，把失效时间摊开（测试里断言 30 个 key 的 TTL 不全相同） |

#### 三、缓存的是「数据」，不是「结论」

`phase`（报名中 / 即将开始 / 已结束）、`remaining`（剩余名额）都是拿**当前时间**算出来的。
如果把算好的结果一起缓存，一条 TTL 10 分钟的详情会在报名截止之后仍然显示「报名中」—— 缓存把时间冻住了。

所以写入缓存的是数据库原始快照（不含推导字段），**每次读出来都按当前时间重算**。
测试里专门钉了这条：缓存里放一条 `phase=OPEN` 的旧快照，读出来必须是 `UPCOMING`。
换句话说，缓存可以「旧」，但不能「错」。

#### 四、失效必须在事务提交之后

直觉写法是在报名成功后顺手 `DEL` 掉缓存。这会出问题：

```text
T1 报名成功 → 事务里删掉缓存（还没提交）
T2 读详情   → 未命中 → 回源 → 读不到 T1 未提交的 joined_count
            → 把【旧值】写回缓存
T1 提交
结果：缓存里是旧值，而且会一直新鲜到 TTL 到期
```

正确做法是 `AfterCommit.run(...)` 把失效推迟到提交之后（见 `common/AfterCommit.java`）。
删晚一点最多多一次回源，不会写入脏数据；事务回滚时回调根本不执行，因为本来就没有变化需要失效。

列表的失效不用删键，而是把 `club:activity:list:ver` 这个计数器 `+1`：
活动一多，列表 key 就是几十上百个，`KEYS` / `SCAN` 批量删在线上是禁止操作；版本号推进之后旧 key 自然读不到，等 TTL 到期自己消失。
（这里有个坑：计数器不存在时 `INCR` 得到 1，正好等于默认版本号，等于没失效。所以失效前先 `SETNX ... 1` 把初始值补上。）

#### 五、缓存挂了，业务不能挂

所有 Redis 调用都包在容错壳里：失败就当作未命中，让调用方查库；失败之后进入 10 秒静默期，期间不再尝试连接。

实测（把 Redis 进程 kill 掉，服务不重启）：

```text
第 1 次请求: HTTP 200, 1.219s   ← 付了一次命令超时（1s）
第 2 次请求: HTTP 200, 0.011s   ← 降级期内直接查库
第 3 次请求: HTTP 200, 0.011s
降级日志：只有 1 条（不是每个请求刷一条）
Redis 恢复后：缓存自动重新生效，第 2 次请求又不查库了
```

#### 六、实测证据

`backend/verify-p5.sh` 用「日志里 SQL 的实际执行次数 + Redis 里 key 的状态」当证据，12 项全过：

```text
详情第一次查库（+1 条 SQL）→ 第二次不查库（+0）
列表第一次查库 → 第二次不查库
换关键词 → 重新查库，且 Redis 里多了一个独立 key（不会串用）
报名成功 → 详情 key 被删（EXISTS=0）、列表版本号 2→3、下次读取重新查库、joinedCount 0→1
不存在的 id → 第一次 +1 条 SQL，第二次 +0（被空值标记挡住）
```

#### 七、当前的取舍

- **TTL 就是数据最大陈旧时间**。Redis 挂掉期间发生的写，失效动作可能没执行成功；恢复后最坏情况要等一个 TTL。这是「不引入双写一致性协议」的代价。
- **只有一级缓存**。没有做「本地 Caffeine + Redis」两级，因为多实例下本地缓存的失效广播是另一套复杂度，当前 QPS 撑得起单级 Redis。
- **单实例 Redis**。没有哨兵/集群，也没有做热点 key 探测；真上量要先看 `redis-cli --hotkeys` 的数据再决定。
- **降级期每 10 秒会有一个请求付 1 秒超时**。用固定静默期换「不拿每个请求去撞挂掉的 Redis」，比指数退避简单；如果 Redis 长时间不可用，这一秒的抖动需要改成退避 + 熔断。

### 日志与可观测性：一个 traceId 串起一次请求

线上排查最怕的不是没日志，而是日志有几十万行、不知道哪几行属于同一次失败。

#### 一、traceId：每个请求一个，贯穿所有日志

```text
2026-10-30 19:22:51.060 WARN  [48674a92e4314380] ... AccessLogFilter  - slow request method=GET uri=/api/activities/1 cost=119ms
2026-10-30 19:22:51.592 WARN  [1ebb469e5cba4ee0] ... SlowSqlInterceptor - slow sql statement=...selectActivityPage_mpCount cost=74ms
2026-10-30 19:22:51.610 WARN  [1ebb469e5cba4ee0] ... SlowSqlInterceptor - slow sql statement=...selectActivityPage cost=14ms
```

第二、三行同一个 traceId，一眼能看出：一次 269ms 的列表请求，74ms 花在分页的 count 上、14ms 花在主查询上。
没有 traceId 的话，这三行会混在别人的日志中间，只能靠时间戳猜。

实现就是 `TraceIdFilter`：进来时 `MDC.put("traceId", id)`，logback 用 `%X{traceId}` 打在固定位置，`finally` 里清掉。

**两个容易错的细节：**

1. **必须放在最外层**（`HIGHEST_PRECEDENCE`，比 Security 的过滤器链 -100 更早）。
   否则 401/403 这些由 Security 直接返回的响应里没有 traceId —— 而权限问题恰恰最需要日志。
2. **不能无条件相信请求头里的 traceId**。允许调用方传 `X-Request-Id` 方便把链路串到网关，
   但这个值会写进日志：如果原样接受，`X-Request-Id: xxx 换行 伪造的日志行` 就能伪造日志（日志注入），
   超长值还能把日志系统当存储用。所以只接受 `[A-Za-z0-9_-]{8,64}`，不合规就重新生成。
   同一个 traceId 还会回写到响应头 `X-Trace-Id`，用户报障时贴过来就能直接定位。

#### 二、访问日志：记请求行，不记请求体

```text
request method=POST uri=/api/activities/3/registrations status=200 cost=19ms user=6
```

只记方法、路径、状态码、耗时、用户 ID。**不记请求体和响应体**：
登录接口的请求体里有密码，报名接口的响应里可能带手机号，打印全量 body 等于把敏感数据复制进日志系统，
风险远大于收益。测试里专门钉了这条：带了 `{"password":"123456"}` 的请求，日志里不能出现 `123456`。

超过阈值（默认 500ms）的请求降级为 `WARN` 并带上查询串；健康检查、接口文档、OPTIONS 不记，
否则每隔几秒一次的探活会把有用的日志挤掉。

`userId` 从请求属性里读，不是从 `CurrentUserContext` —— 认证在内层 Security 链里，
等外层过滤器写日志时线程上下文已经被清理了。

#### 三、慢 SQL：把「接口慢」和「SQL 慢」分开

访问日志只说「这个接口 269ms」，说不出慢在哪。所以再在 MyBatis 的 `Executor` 上挂一个插件计时：

```text
slow sql statement=com.campus.club.activity.mapper.ActivityMapper.selectActivityPage cost=14ms
```

**只记语句 ID，不记 SQL 和参数**：参数里可能有密码哈希；日志长期留存、多人可查，写进去就收不回来了。
语句 ID 已经够定位，具体参数按同一个 traceId 去查业务日志。

顺带一个发现：分页会先跑 `selectActivityPage_mpCount` 再跑主查询，两条都各自计时，
所以「分页插件让接口变慢」这种问题能直接看出来是 count 贵还是主查询贵。

#### 四、指标：缓存到底有没有用

`ActivityCache` 每次访问都打点，指标名 `app.cache.requests`，两个标签：

| 标签 | 取值 | 含义 |
| --- | --- | --- |
| `cache` | `activity-detail` / `activity-list` | 哪个缓存 |
| `result` | `hit` / `miss` / `null-marker` / `bypass` / `error` | 命中、未命中、被空值标记挡住、没走缓存、脏数据 |

`bypass` 是刻意加的：缓存被关掉或处于降级期时，这段流量根本没碰缓存。
如果不单独计数，降级期间会显示成「命中率 100%」—— 指标说假话比没指标更糟。

查看方式（需要平台管理员令牌，匿名访问返回 401）：

```bash
curl "http://127.0.0.1:8090/actuator/metrics/app.cache.requests?tag=cache:activity-detail&tag=result:hit" \
     -H "Authorization: Bearer <admin-token>"
```

`/actuator/metrics` 只给 `PLATFORM_ADMIN`：缓存命中率、JVM 指标这些信息对外公开，等于免费给攻击者做侦察。
`/actuator/health` 仍然匿名可访问，给探活用。

#### 五、实测

`backend/verify-p6.sh` 13 项全过：响应头带 16 位 traceId、合规的 `X-Request-Id` 被沿用、
含空格和超长值被拒、同一 traceId 能 grep 出访问日志 + 业务日志、访问日志字段完整、
登录后带 `user=1`、`/actuator/metrics` 匿名 401 / 管理员 200、缓存指标可按下钻标签查询。

#### 六、当前的取舍

- **没有接 ELK / Loki / Prometheus**。当前是单机单实例，`grep traceId` 就够用；真要多实例再谈采集，否则只是把复杂度提前。
- **没有分布式追踪（Sleuth / Micrometer Tracing）**。跨服务调用才需要 span 树，本项目一个进程，traceId 已经够。
- **日志只输出到控制台**，没有滚动文件。容器化部署本来就应该由平台收集 stdout，本地开发看控制台更直接。
- **指标是进程内的**。`/actuator/metrics` 每次重启清零，要长期趋势需要外部拉取（Prometheus 的 `/actuator/prometheus` 端点已经能用，只是没配）。

### 异步通知：为什么不能在报名事务里直接发

报名成功后给用户写一条站内信。听起来是「加一行 insert」，但直接写会引出两个问题：
接口多一次数据库往返；更要命的是——**事务回滚了，通知却已经发出去了**。

#### 一、投递时机：`AFTER_COMMIT` 而不是 `@EventListener`

```text
register()  ── @Transactional
  ├─ 条件更新占位 → 写报名记录 → 回读计数
  ├─ publishEvent(RegistrationSucceededEvent)   ← 只「登记」，不做任何 IO
  └─ commit
        └─ RegistrationNotificationListener（AFTER_COMMIT 才被唤起）
             └─ notificationService.sendRegistrationNotice()  ── @Async
                  └─ notify-* 线程：INSERT notification
```

普通 `@EventListener` 在 `publishEvent` 那一刻就执行，此时事务还没提交。
后面任何一步失败（唯一键冲突、约束不满足、连接断），用户就会收到「报名成功」，
而数据库里根本没有这条报名 —— 日志里还留着一条「已发送通知」，排查起来极其痛苦。

`AFTER_COMMIT` 把执行挪到提交之后，回滚则监听器根本不触发。
`NotificationFlowIntegrationTest` 里专门有一条用例：在一个会被回滚的外层事务里调用报名，
断言**一条通知都不存在**。这条用例用普通 `@EventListener` 是过不了的。

#### 二、事件里带快照，异步线程不回查

`RegistrationSucceededEvent` 带的是 `activityId / userId / 标题 / 地点 / 开始时间 / 剩余名额`。
只传 activityId 让异步方自己查，会踩两个坑：多一次数据库往返；
更糟的是查到的**是新数据** —— 活动可能刚被改标题或下架，用户收到的通知就和他当初报的不是同一个活动。

#### 三、线程池：有界队列 + Abort，不用 CallerRuns

`@Async` 找不到 executor 时会退回 `SimpleAsyncTaskExecutor`：**每个任务新建一个线程，无上限**。
通知高峰时它会一直开线程直到 OOM —— 一个「优化响应时间」的功能把服务拖垮。

所以显式配一个有界池（核心 2 / 最大 8 / 队列 2000）。队列满时用 `AbortPolicy` 快速失败，
**不用 `CallerRunsPolicy`**：后者会让提交任务的 Tomcat 线程自己跑 INSERT，
队列一满异步立刻退化成同步，正是要避免的事。这里的选择是
**宁可丢一条通知，不拖慢一次报名**；丢弃会被 `app.notification.dispatch{result="rejected"}` 计数 + 一条 WARN 记录下来。

#### 四、traceId 要手动带过去

`MDC` 是 `ThreadLocal`，跨线程不会自动传递。不做处理的话，异步线程的日志全是 `traceId=no-trace`，
P6 建好的链路在这里静默断掉（不报错，只是搜不到）。`AsyncConfig.MdcPropagatingDecorator`
在提交时抓一份快照、执行时换上、结束后清掉 —— 最后这步不能省，线程是复用的。

实测同一条链路能串起来（`verify-p8.sh` 第 8 项）：

```text
[p8-register-1793363627] [http-nio-8090-exec-10] RegistrationServiceImpl - 报名成功 activityId=3 userId=6
[p8-register-1793363627] [http-nio-8090-exec-10] AccessLogFilter - request method=POST ... status=200 cost=55ms
[p8-register-1793363627] [notify-1]              NotificationServiceImpl - 站内通知已投递 userId=6 ...
```

#### 五、通知接口的越权处理

路径里没有 `userId` 参数，一律从令牌解析 —— 一旦允许传，就得在每个方法里记得校验，
漏一次就是越权读别人通知。标记已读的 `UPDATE` 把 `user_id` 写进 `WHERE`：

```sql
UPDATE notification SET is_read = 1, read_at = NOW()
WHERE id = ? AND user_id = ? AND is_read = 0
```

影响 0 行就返回 404。**不区分「不存在」和「不属于你」**，否则等于提供了一个探测他人通知 id 的接口。
重复标记也返回 404（而不是「成功」），因为 `is_read = 0` 让它第二次必然影响 0 行。

#### 六、为什么没上 RabbitMQ

单实例、通知可丢、没有跨服务消费者 —— 这三条都不满足「必须用 MQ」的门槛，
引入 broker 只是把复杂度从一个线程池换成一个需要运维的中间件。
判断依据、四种方案的横评、以及真要上 MQ 时的 5 步改造清单写在
[`backend/docs/async-notification.md`](backend/docs/async-notification.md)。

**本机 Docker 起不来，拿不到可连的 broker，所以没有写「已接入 RabbitMQ」** ——
没有实测过的东西不写进文档。

### 活动增删改：状态机、数据归属，和一条「删不掉」的规则

写接口比读接口多出的三件事：谁能改、改了不冲突、改完别让缓存变成旧数据。
这一节按这三条讲。全部行为由 28 个单元用例 + 14 个 MockMvc 用例 + 63 项端到端断言
（`backend/verify-p9.sh`，含「可管理社团」下拉框数据源与「管理端单条详情」）钉住。

前端对应两个页面：`pages/activity-manage`（管理端列表，按状态分 Tab，行内发布/下架/删除）
和 `pages/activity-edit`（新建/编辑表单）。新建出来是草稿，发布是列表里单独一次点击 ——
界面不把「保存」和「发布」合成一个按钮，因为状态机里那本来就是两条边。

#### 一、状态机：只留两条合法的边

```text
新建                发布 publish          下架 unpublish
  │                    │                     ▲
  ▼                    ▼                     │
DRAFT(0) ──publish──► PUBLISHED(1) ──unpublish──► CANCELLED(2)
                        │  ▲                                    │
                        └──┼────── publish（重新上架）◄──────────┘
                           │
                       FINISHED(3)：不落库，由 end_time < 当前时间推导
```

- **`FINISHED` 不是一个可写入的状态**：它由时间推导（见 `ActivityPhase.resolve`），
  任何人调用任何接口都写不进去「已结束」，也就不存在「把活动提前标成结束」这种操作。
- **发布前要过两道门**：报名截止时间已过 → `409/40905`（先改时间再发布）；
  结束时间已过 → `400/40002`。不拦的话，发布出来一个「报名已截止」的活动，
  前端只能显示一个永远点不动的报名按钮。
- **状态变更是条件更新**，不是「读出来、判断、再写回」：

```sql
UPDATE activity SET status = ? WHERE id = ? AND status = ?
```

两个管理员同时点「发布」和「下架」，输的那次影响 0 行 → 返回 `409/40910`「请刷新重试」，
而不是出现「先删了又被改成已发布」的中间态。

#### 二、请求体里没有的东西，才是设计

`ActivitySaveRequest`（新建/修改共用）刻意**不接收**三个字段：

| 字段 | 为什么不收 | 收了会发生什么 |
| --- | --- | --- |
| `status` | 状态只能走 publish / unpublish 两个动作 | 调用方直接把草稿改成「已发布」，绕过发布校验 |
| `joinedCount` | 冗余计数只由报名事务维护 | 「把已报名人数改成 0」，计数等式（`joined_count = 有效报名数`）被破坏 |
| `clubId`（修改时） | 归属在创建时固定 | 活动挪到别的社团，已有报名记录的归属和名单权限全部错位 |

修改用**整体覆盖**（PUT）而不是「传哪个改哪个」（PATCH）：活动表单是一个整页提交的东西，
PATCH 语义下「字段没传」和「字段被清空」无法区分，前端一次误操作就能把地点清成 null。
为了让「传 null = 清空」真正生效，`updateEditable` 不用 MyBatis-Plus 的 `updateById`
（它按「非空才更新」处理），而是显式列出每个可写列 —— 顺带的收益是
`status`、`joined_count`、`club_id`、`created_by` 根本不在这条 SQL 里，脏数据写不进去。

这条 UPDATE 还带两个并发防护：`WHERE status IN (0,1)`（改到一半被下架就作废）、
`WHERE joined_count <= #{capacity}`（改小名额的同时有人报名，直接放弃本次修改，
而不是让数据库 CHECK 约束抛一个用户看不懂的异常）。影响 0 行时 Service 会**重读一次**，
把真实原因（名额被顶掉了？活动被下架了？）说给用户，而不是笼统报「失败」。

#### 三、权限分两层，少一层都出事故

```java
@PreAuthorize("hasAnyRole('CLUB_ADMIN', 'PLATFORM_ADMIN')")   // 角色：是不是管理员
requireManageableClub(existing.getClubId(), principal)          // 数据：是不是这个社团的
```

只做第一层，社团 2 的负责人就能改社团 1 的活动 —— 这是横向越权，比「没登录」更难发现。
归属校验放 Service 而不是 Controller，因为同一个判断要被改、发、下架、删、管理列表五处复用，
放 Controller 就得抄五遍。

管理端列表的归属过滤写进 SQL 的 `WHERE`（`c.owner_user_id = ?`），不是查出来在 Java 里过滤：
后者一旦漏写一行 `if`，就是把**别人社团的草稿**整个吐出去。草稿和已下架活动只出现在
`/api/activities/manage`（需登录），对外 `/api/activities` 硬编码 `status = 1`。

#### 四、只有草稿能删，已发布的只能下架

`activity_registration` 对 `activity` 是 `ON DELETE CASCADE`：删掉一个已发布的活动，
等于顺手删掉所有用户的报名历史。所以物理删除被限制在草稿：

```sql
DELETE FROM activity WHERE id = ? AND status = 0
```

已发布的想撤下来 → 下架（保留报名记录）；下架的还能重新发布（撤回不等于报废）。
真要删一个发布过的活动，得先把报名记录归档 —— 那是另一个需求，见「已知限制」。

#### 五、写完之后：缓存的三个失效时机

| 操作 | 失效动作 | 少做会怎样 |
| --- | --- | --- |
| 新建 | `evictList`（只推进列表版本号） | 列表缓存里没有新活动，运营以为「创建成功但没生效」 |
| 修改 / 发布 / 下架 / 删除 | `evict`（删详情 + 推进列表版本号） | 详情页最长一个 TTL 显示旧标题/旧名额 |
| **发布**（尤其） | 同上 | 发布前这个 id 大概率被缓存成「**不存在**」（详情查询只认已发布）—— 不失效，发布后 60 秒内仍然 404 |

所有失效都挂在 `AfterCommit`（事务提交后执行）。在事务里删缓存，删早了；
并发读会把旧数据重新写回缓存，比不删更糟。

#### 六、编辑页要回填草稿，所以「详情」得有两个

公开详情接口 `GET /api/activities/{id}` 的 SQL 里硬编码了 `status = 1` —— 这是对的，
草稿不该被任何人匿名读到。但编辑页要回填的**正好是草稿与已下架**，走公开详情只会拿到 404。

最早的做法是把管理端列表里的那一条塞进本地缓存带过去。它有个藏得很深的边界：
**活动超过一页（50 条）之后就翻不到了**，而「超过 50 个活动的社团」在大半年后一定会出现。

正解是补一个 `GET /api/activities/manage/{id}`：

| | 公开详情 | 管理端详情 |
| --- | --- | --- |
| 状态过滤 | `AND a.status = 1` | 无 |
| 权限 | 匿名可读 | `@PreAuthorize` + 社团归属校验 |
| 缓存 | 走 Redis 详情缓存（含「不存在」占位） | **不缓存**：管理端要立刻看到刚改完的值 |
| SQL 列 | 共用 `<sql id="detailColumns">` 片段 | 同左 |

两条 SQL 共用同一份列清单不是洁癖：详情有 18 列，抄两遍必然漂移，而「少一列」在前端
表现为「编辑页某一项永远是空的」，排查成本远高于维护一个 `<sql>` 片段。

权限顺序沿用 `ClubOwnershipGuard` 的约定：**先判存在（404）再判归属（403）**。
反过来的话，拿 id 挨个试就能靠 403/404 反推出「哪些活动存在」，等于白送一个探测接口。

#### 七、这轮顺手修掉的两个「显示错但不报错」的 bug

都是纯推导顺序问题，没有任何异常：

1. **草稿被显示成「报名中」**：草稿的报名截止时间通常在未来，`resolve` 先判报名再判状态的话，
   草稿一路走到底被判成 OPEN。修法：草稿最先判。
2. **已下架被显示成「已结束」**：下架的活动可能时间在未来。修法：新增 `CANCELLED(已下架)` 阶段。

这种 bug 单测一写就现形（`ActivityPhaseTest` 6 个用例按优先级排列），靠肉眼 review 很难发现。

### 签到：一道时间窗、一个「代签」后门，和一条不能断的不变量

签到看着比报名简单（不外乎插一行记录），但它决定的是**志愿时长**——要写进评奖材料的东西。
所以这里的判断标准不是「能不能签上」，而是「谁不能签、为什么不能签、签错了能不能查出来」。

#### 一、两条路径，两种规则

| 谁在签 | 前提 | 时间限制 | 落库的 `method` |
| --- | --- | --- | --- |
| 学生本人 | 有一条 **有效报名**（`status=1`） | 活动开始前 30 分钟 ~ 活动结束 | `MANUAL` |
| 负责人代签 | 目标用户有有效报名 | **没有**（补录就是要事后做） | `ADMIN` |
| 扫码 | 预留 | —— | `QR_CODE`（种子数据里已有两条） |

代签不是后门，是必需品：现场没网、手机没电、老年机扫不了码。但**代签必须留痕**——
名单上能一眼看出「这条不是本人签的」，这就是 `method` 字段存在的唯一理由。

时间窗只在本人签到这条路径上：`checkInForUser` 整条链路**根本不读活动起止时间**，
单测里直接断言 `verify(activityMapper, never()).selectById(...)`。
「不受限制」最好的证明不是「结束后也能签」，而是代码里压根没有那个判断。

#### 二、窗口只有一处定义

```java
private boolean isInCheckInWindow(Activity activity, LocalDateTime now) {
    return start != null && end != null
            && !now.isBefore(start.minus(CHECKIN_OPEN_BEFORE_START))   // 开始前 30 分钟
            && !now.isAfter(end);                                      // 结束时间（含）
}
```

`assertInCheckInWindow` 不重复写边界，而是复用这个布尔结果，只负责把「为什么不在窗口里」翻译成
40911（还没开始）/ 40912（已结束，提示去找负责人补录）。

两处各写一遍边界比较是这类代码最典型的漂移：改了常量忘了改另一处，
于是「能不能签」和「为什么不能签」互相矛盾，前端按钮和接口各说各话。

#### 三、签到表与报名表之间的不变量

> 到场的人一定是报名的人。

只写签到表、不校验报名，任何人凭一个 `activityId` 就能给自己刷志愿时长。
所以签到的第一件事是查报名，而且查的是**有效报名**：取消过的人来签到，
等于给一个已经退出的人记时长。

反方向也要堵：**已签到的人不能取消报名**。否则签到记录指向一条「已取消」的报名，
名单里就会出现一个「签到了但没报名」的人。`cancelRegistration` 的 SQL 直接带上
`AND NOT EXISTS (SELECT 1 FROM activity_attendance ...)`，受影响行数为 0 时**再查一次**
签到表：有签到记录就是「签到抢先」→ 40913，没有就是「本来就没报名」→ 40903。
把判断放进 `WHERE` 而不是先查后写，是为了让并发下的裁决权留在数据库；
再查一次只是为了让报错说对话 —— 否则用户会一直重试一个永远不会成功的操作。

#### 四、重复签到交给唯一键

先查「有没有签过」只是快速失败，真正的裁决是 `UNIQUE(activity_id, user_id)`。
两个并发请求都可能通过查询，所以 `insert` 必须捕获 `DuplicateKeyException` 转成
40906，而不是让它变成 500 —— 否则用户在弱网下连点两下，看到的就是「系统繁忙」。

同样的理由，代签也走这一条：被拒绝时**不覆盖**原来的时长（测试里断言自签的 0 小时仍然是 0）。

#### 五、时长为什么用 `BigDecimal`

`volunteer_hours DECIMAL(4,1)`：一位小数、最大 999.9。用 `double` 存 0.1 小时会在
汇总时出现 `1.5000000000000002`，而这是要写进评奖材料的数字。
入参用 `@Digits(integer = 3, fraction = 1)` 在边界拦住，不让 MySQL 去截断。

「我的签到」把 `totalHours` 和 `count` 一起返回，`SUM` 外面套了 `COALESCE(...,0)`：
没有记录时返回 `0.0` 而不是 `null`，前端不会渲染成「null 小时」。
总数由后端算，是因为将来口径会变成「按学年」「只算审核通过」——
规则一旦落到前端，就会和别处的统计分叉。

#### 六、签到**不**失效活动缓存

活动详情/列表里没有任何签到字段，`joined_count` 也不因签到变化。
每次写都顺手 `evict` 一次看着「保险」，实际只是让详情缓存白白穿透一次。
会读签到数据的只有报名名单和签到名单，它们都直接查库。

#### 七、怎么证明

`backend/verify-p10.sh` 用真实 HTTP 打了 55 项断言，全部通过，关键是这几组：

- 窗口内自签 200 → 再查 `checkedIn=true`、`method=MANUAL`、时长 0；重复签到 40906；
- 种子活动 2（还有十几天）签到 40911，种子活动 1（已结束）签到 40912 —— 用真实数据，不造将来时间；
- 代签在**已结束**的活动上成功，`method=ADMIN`、时长精确存下 2.5；
- 别家负责人代签 / 查名单 403，学生查名单 403，匿名一律 401（不是 403）；
- 签到后取消报名 40913，报名状态仍是 `REGISTERED`；
- 设置时长后累计值按差值变化（学生 1 种子里已有 1.5 小时，断言的是「+3.5」而不是绝对值）。

单元测试 20 个（`AttendanceServiceImplTest`）+ 集成测试 21 个（`AttendanceApiIntegrationTest`，真连 MySQL）。

### 性能：压测数据，以及两个被压出来的问题

完整数据与方法见 [`backend/PERFORMANCE.md`](backend/PERFORMANCE.md)，脚本在 `backend/bench/`。

#### 一、缓存值多少（同一份代码，只切开关）

| 场景 | 缓存开（中位） | 缓存关（中位） | 变化 |
| --- | --- | --- | --- |
| 活动详情 QPS | 5095 | 2765 | **+84%** |
| 活动详情 P95 | 9.76ms | 18.62ms | **−48%** |
| 活动列表 QPS | 5488 | 2231 | **+146%** |
| 活动列表 P95 | 9.50ms | 20.69ms | **−54%** |
| 报名/取消 QPS | 382 | 263 | 区间完全重叠，不下结论 |

比中位数更硬的证据是**区间不重叠**：缓存开最差一轮（4231 QPS）仍高于缓存关最好一轮（4101 QPS）。
所以结论不是靠挑数据挑出来的。

写路径两组重叠（294–468 vs 230–404），统计上分不出快慢 —— 但代码事实很清楚：
开缓存后每次写要多付两次 Redis 往返（`DEL detail` + `INCR list:ver`），这是纯成本。
只有「读远多于写」时这笔账才划算，本项目是「一次报名、几十次浏览」，成立。

#### 二、Redis 挂了会怎样（真杀进程）

| 阶段 | 失败数 | P50 | P95 | P99 |
| --- | --- | --- | --- | --- |
| 故障瞬间（无预热） | 0 | 28.75ms | 86.83ms | **1133.60ms** |
| 10s 静默期内 | 0 | 13.34ms | 24.04ms | 33.27ms |

P99 的 1.1 秒正好等于配置里的 `spring.data.redis.timeout: 1s`：
并发的那批请求各撞一次命令超时，之后静默期生效、直接查库。
**上限是被配置钉死的** —— 没有这个超时，线程会跟着挂住的连接一直等下去。
整个故障窗口里「进入降级期」的 WARN 只打了 1 条，不会把日志刷爆。

#### 三、压出来的问题一：JIT 预热不足会让结论反向

同一份代码，冷启动第一轮 1487 QPS，预热后 5612 QPS，差 3.7 倍。
如果只跑一轮就下结论，会算出「缓存只提升 13%」（1487 vs 1315）这种完全错误的判断。
所以压测脚本把预热和「丢弃第一轮」设成必选，不是可选项。

#### 四、压出来的问题二：Redis 一挂，健康检查把实例判死

```text
Redis 正常：/actuator/health -> 200 {"status":"UP"}
Redis 挂掉：/actuator/health -> 503 {"status":"DOWN"}   ← 但业务接口仍然 200
```

业务明明降级后还能跑，健康检查却判死。K8s 若拿它当 **liveness** 探针，
结果就是 Redis 抖一下 → 全部实例被重启 → 冷启动 → 更久连不上 Redis → 雪崩。

修法是把探针拆开，并让它们匿名可访问（探针没有令牌，第一版只放行 `/actuator/health`，
两个子路径全被 401 拦住，探针永远失败）：

| 地址 | Redis 挂掉时 | 含义 |
| --- | --- | --- |
| `/actuator/health` | 503 DOWN | 综合状态，如实反映 |
| `/actuator/health/liveness` | 200 UP | 进程 + 数据库，不含 Redis |
| `/actuator/health/readiness` | 503 DOWN | 依赖不齐，先别接流量 |

Redis 恢复后三者全部回到 200 UP，已实测。

#### 五、这些数字不能证明什么

单机、客户端与服务同机、Windows 调度、无网络延迟 —— **不能当生产容量**。
写路径只有 3 并发（受种子数据限制），写入的并发正确性由 JUnit 的 40 线程用例证明。
也没有做长稳压测，GC 长尾、内存增长、连接泄漏都还没观察。

### 数据库设计要点

8 张表：`sys_user`、`club`、`club_membership`、`activity`、`activity_registration`、`activity_attendance`、`activity_favorite`、`notification`。

三条关键约束：

1. `activity_registration` 上的 `UNIQUE (activity_id, user_id)` —— 一个用户在一个活动永远只有一行；取消报名改 `status`，重新报名复用该行。防重复报名是结构保证，不依赖业务代码判断。
2. `activity_attendance` 上的 `UNIQUE (activity_id, user_id)` —— 防重复签到。
3. `activity` 上的 `CHECK (joined_count >= 0 AND joined_count <= capacity)` —— 即使应用层逻辑写错，数据库也不允许超卖。

`activity.joined_count` 是冗余计数，与真实报名数应满足：

```sql
-- 应返回 0 行
SELECT a.id, a.joined_count,
       (SELECT COUNT(*) FROM activity_registration r
        WHERE r.activity_id = a.id AND r.status = 1) AS real_count
FROM activity a
HAVING a.joined_count <> real_count;
```

选择冗余计数的理由与代价：

- 列表页要显示「已报名 / 容量」。若每行都用子查询聚合，5 条数据就要额外 5 次聚合；冗余计数把读放大降为一次主表扫描。
- 代价是写路径必须保证计数与记录一致：报名和计数更新在同一事务内完成，并检查 `UPDATE` 的受影响行数。这部分已实现，见上文「报名并发控制」。

### 初始化与重置

- 建表脚本：`backend/src/main/resources/db/init/01_schema.sql`
- 种子数据：`backend/src/main/resources/db/init/02_seed.sql`

重置（会删除全部本地数据）：

```bash
cd backend
docker compose down -v
docker compose up -d
```

### 当前完成度

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 活动列表 / 详情接口 | ✅ 已完成 | 真实 MySQL，有集成测试 |
| 活动数据来自数据库 | ✅ 已完成 | 前端不再读 mock 活动数据 |
| 统一响应 / 全局异常 / 参数校验 | ✅ 已完成 | 错误码集中在 `ErrorCode` |
| 建表脚本 + 种子数据 | ✅ 已完成 | 含唯一约束与 CHECK 约束 |
| 计数一致性校验 SQL | ✅ 已完成 | 见上方 SQL |
| 测试体系 | ✅ 已完成 | 219 个测试通过，含 8 个真实并发用例、17 个真 Redis 缓存用例、5 个异步通知用例、48 个活动管理用例、41 个签到用例 |
| 报名 / 取消报名接口 | ✅ 已完成 | 条件更新防超卖，前端已接入 |
| 注册 / 登录 / JWT | ✅ 已完成 | 登录 + 令牌校验 + 401/403 分离 |
| 权限控制 | ✅ 已完成 | URL 规则 + 角色校验 + 数据归属校验 |
| Redis 缓存 | ✅ 已完成 | 详情/列表缓存、防穿透击穿雪崩、事务后失效、故障降级 |
| 日志与可观测性 | ✅ 已完成 | traceId 贯穿、访问日志、慢请求/慢 SQL、缓存命中率指标 |
| 异步通知 | ✅ 已完成 | 报名后 `AFTER_COMMIT` + 有界线程池写站内信，含未读/已读接口 |
| 压测与性能数据 | ✅ 已完成 | `bench/` 脚本 + `PERFORMANCE.md`，真数字非估算 |
| 健康检查探针 | ✅ 已完成 | 综合 / liveness / readiness 三档分离 |
| 签到接口 | ✅ 已完成 | 本人签到（30 分钟窗口）/ 负责人代签 / 签到名单 / 志愿时长，41 个用例；前端签到页已接 |
| 活动增删改接口 | ✅ 已完成 | 新建/修改/发布/下架/删除 + 管理端列表，状态机与归属校验有 41 个用例；前端管理页已接 |
| 可管理社团列表 | ✅ 已完成 | `GET /api/clubs/mine`，给新建活动的社团下拉框提供唯一合法来源 |
| 管理端单条详情 | ✅ 已完成 | `GET /api/activities/manage/{id}`，草稿/已下架可回填，编辑页不再依赖本地缓存快照 |
| 消息推送到微信 / 短信 | ❌ 未实现 | 站内信已通，外部通道需要 MQ 与第三方资质 |
| RabbitMQ | ❌ 未引入 | 已做方案评估与改造清单，单实例场景下判定为不需要，详见 `docs/async-notification.md` |

### 已知限制

- **签到窗口是固定 30 分钟**：`CHECKIN_OPEN_BEFORE_START` 写死在代码里，不随活动类型变化（讲座与大运会显然不是一个提前量）。要按活动配置就得在 `activity` 表加字段，目前没做。
- **扫码签到只有字段没有流程**：`method` 支持 `QR_CODE`，但既没有二维码生成，也没有扫码校验（签名、时效、一次一码都没有）。现在只有种子数据里两条历史记录用到了这个值。
- **志愿时长没有审核**：负责人填多少就是多少，没有学生确认、没有导出、没有按学年结算。真要做需要一条「申报 → 审核 → 归档」的状态流。
- **「我的签到」不分页**：一个人一年几十条，一次全返回。数据量再大就要改成按学年分页。
- **已发布/已下架的活动无法删除**：报名表对活动是 `ON DELETE CASCADE`，允许删除等于允许抹掉用户报名历史。想删得先做报名记录归档，目前未实现。
- **活动管理页没有权限分级之外的审计**：谁改了标题、谁下架了活动，目前只有服务端 info 日志，没有面向管理员的变更记录。
- **修改是整体覆盖（PUT）**：调用方必须每次传全量字段。改成部分更新（PATCH）需要先明确「字段没传」与「字段清空」的区分语义，暂时不做。
- **缓存会让数据「旧」，不会让它「错」**：读到的派生状态（报名中/已结束/剩余名额）永远按当前时间重算，但 `joined_count` 最坏情况下会旧一个 TTL（Redis 故障期间发生的写）。
- **「我的报名」只返回未取消的记录**：取消过的报名在列表里看不到，历史记录能力需要单独的接口与分页。
- **候补报名仍是前端演示**：名额满时按钮叫「候补报名」，但只弹提示，没有排队表，也没有自动补位。真要做需要一张 waitlist 表和取消时触发的补位逻辑。
- **搜索使用 `LIKE '%keyword%'`**：无法命中索引，数据量大时会全表扫描。当前数据量下可接受，后续可改全文索引或搜索引擎。
- **种子数据的成员数、报名数偏小**：只插入了可核对的真实记录，没有为了好看编造人数。
- **签到页只过了类型检查与 H5 构建**：`vue-tsc --noEmit` 与 `uni build -p h5` 通过，接口形状也用 curl 经 Vite 代理核对过，但没有在微信开发者工具/真机上点过一遍。真机联调需要把 `BASE_URL` 改成局域网 IP。
- **站内信是「尽力投递」，会丢**：队列满被拒、进程在提交后崩溃、写库失败，三条路径都会丢通知且不重试（at-most-once）。
  每条都有指标可查，但要让通知变成业务凭证就得换 outbox 或 MQ，见 `docs/async-notification.md`。
- **压测只覆盖单机短跑**：所有场景 5 秒内结束，没有观察 GC 长尾、内存增长、连接泄漏；写路径只有 3 并发。数字只能横向比，不能当容量结论。

---

## 三、功能清单

### 当前可演示的流程

| 模块 | 当前能演示的内容 |
| --- | --- |
| 社团 | 搜索、分类、详情、关联活动、提交申请并保存审核中状态 |
| 活动 | 搜索、筛选、详情、报名、取消报名、查看报名记录 |
| 活动管理 | 负责人/管理员：管理端列表（按状态筛选）、新建草稿、编辑、发布、下架、删除草稿 |
| 收藏 | 收藏、取消收藏，收藏列表与详情页联动 |
| 签到 | 报名后在活动详情签到（真实接口，窗口由后端判定）、志愿时长与签到记录（后端统计） |
| 消息 | 本地生成报名/申请/签到通知，筛选未读、标记已读 |
| 个人资料 | 编辑昵称、学院、年级等，保存后个人中心回显 |

**仍是前端模拟、未接后端的部分**：社团搜索 / 详情 / 加入申请（后端只有「我能管理哪些社团」一个接口）、
收藏与站内信、候补提示、个人中心的统计数字。真实审批、扫码签到校验、微信/短信推送均未实现，
详见上文「已知限制」。

