# 异步通知：为什么现在用 `@Async`，以及什么时候必须换成 MQ

> 结论先放前面：**本项目不引入 RabbitMQ**，用「事务提交后 + 进程内有界线程池」实现站内通知。
> 下面把判断依据、当前的可靠性边界、以及真要上 MQ 时的改造清单写清楚，
> 而不是留一句「以后可以换 MQ」。

## 1. 当前实现

```
POST /api/activities/{id}/registrations
  └─ register()  ── @Transactional
       ├─ 条件更新占位 → 写报名记录 → 回读计数
       ├─ publishEvent(RegistrationSucceededEvent)   ← 只「登记」，不做事
       └─ commit
             └─ TransactionSynchronization.afterCommit
                  └─ RegistrationNotificationListener.onRegistrationSucceeded
                       └─ notificationService.sendRegistrationNotice()  ── @Async("notificationExecutor")
                            └─ notify-* 线程：INSERT notification
```

四个关键点：

| 决定 | 原因 |
| --- | --- |
| `@TransactionalEventListener(AFTER_COMMIT)` | 事务回滚时监听器根本不触发。用普通 `@EventListener` 会出现「收到报名成功通知但数据库里没有这条报名」—— 日志里还留着一条「已发送通知」，排查极痛苦 |
| 事件里带字段快照 | 异步线程没有事务上下文，回查会拿到**新**数据（活动可能已改标题/下架），和用户当初报的那个活动对不上；也白白多一次数据库往返 |
| 有界队列 + `AbortPolicy` | 不用 `CallerRuns`：队列满时让 Tomcat 线程自己跑 INSERT，异步立刻退化成同步，正是要避免的。宁可丢一条通知 |
| `TaskDecorator` 复制 MDC | 否则异步线程日志全是 `traceId=no-trace`，P6 建好的链路在这里断掉，而且不报错、纯静默 |

## 2. 可靠性边界（诚实版）

当前实现是 **at-most-once**：通知可能一条都到不了，但不会重复。

| 丢失路径 | 触发条件 | 是否有痕迹 |
| --- | --- | --- |
| 队列拒绝 | 突发流量打满 2000 队列 + 8 线程 | `app.notification.dispatch{result="rejected"}` + 一条 WARN |
| 进程崩溃 | 提交后、INSERT 前进程被杀 | 无（事件在内存里） |
| 写库失败 | 数据库不可用、用户被并发删除（外键） | `app.notification.dispatch{result="failed"}` + WARN |

对「站内信」这个场景，这三条都可以接受：用户本来就能在「我的报名」里看到结果，
通知只是让他少点一次。**如果通知本身是业务凭证（比如缴费成功回执），上面三条一条都不能接受，
那时必须换成持久化投递。**

## 3. 方案横评

| 方案 | 投递保证 | 额外组件 | 重复消费 | 适用规模 |
| --- | --- | --- | --- | --- |
| `@Async` + `AFTER_COMMIT`（当前） | at-most-once | 无 | 不会 | 单实例、通知可丢 |
| 本地 outbox 表 + 定时扫描 | at-least-once | 无（复用 MySQL） | 会，需幂等键 | 单库、可接受秒级延迟 |
| RabbitMQ | at-least-once（需 publisher confirm + 手动 ack） | 是 | 会，需幂等 | 多实例、多消费者、需要削峰 |
| Redis Stream | at-least-once | 是（已有 Redis） | 会 | 中等规模，想少引一个组件 |
| Kafka | at-least-once / 分区有序 | 是（重） | 会 | 高吞吐、需要回放 |

## 4. 什么时候必须换成 MQ

按顺序自查，**任意一条命中**就该换：

1. **实例数 > 1 且通知不能丢**。现在的线程池在每个 JVM 里各管各的，
   没有重投、没有死信、进程重启后内存里的事件全没。
2. **需要重试与死信**。第三方推送（短信/微信模板消息）失败要退避重试，
   重试若干次仍失败要进死信队列人工排查 —— 这些能力不该自己写。
3. **消费方要独立扩容或由别的团队维护**。同一份事件要被多个下游消费时，
   进程内事件模型根本表达不了「扇出」。
4. **需要削峰**。报名瞬时 5k QPS 时，MQ 能把写入摊到消费者那边，
   而进程内队列满了就是拒绝。

反过来，只满足「想让接口快一点」这一条，**不需要 MQ** —— 那是本方案要解决的问题，
引入 broker 只是把复杂度从一个线程池换成一个需要运维的中间件。

## 5. 真要上 RabbitMQ：改造清单

事件体不用动（`RegistrationSucceededEvent` 已经是自包含的快照），只需要换投递通道：

1. `pom.xml` 加 `spring-boot-starter-amqp`；
2. `application.yml` 配 `spring.rabbitmq.*`，交换机 `campus.notification`（topic）、
   队列 `notification.registration`、路由键 `registration.succeeded`；
3. 发消息改成**在事务内写 outbox 表**，由 `@TransactionalEventListener(AFTER_COMMIT)` 投递
   （或直接用 Spring AMQP 的事务性 channel）。**不能**在业务事务里直接发消息 ——
   消息发出去了而事务回滚，消费者就会为一次不存在的报名发通知；
4. 消费端 `@RabbitListener` 取代 `NotificationServiceImpl.sendRegistrationNotice`，
   加**幂等键**（`activityId + userId + type` 上的唯一索引），
   因为 at-least-once 必然重复投递；
5. 配 `publisher-confirm-type: correlated` + 手动 ack + 死信队列，
   否则「消息丢了」和「消息重复了」两件事你都发现不了。

本地起 broker（端口避开已占用的 5672）：

```yaml
  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: campus-club-rabbitmq
    ports: ["5673:5672", "15673:15672"]
    environment:
      RABBITMQ_DEFAULT_USER: campus
      RABBITMQ_DEFAULT_PASS: campus123456
```

## 6. 为什么这次没有实测 RabbitMQ

本机 Docker Desktop 未运行，也无法从当前 shell 拉起（安全策略拦截），
拿不到一个可连的 broker。**没有真实 broker 就不写「已接入 RabbitMQ」** ——
把 concurrency/吞吐写成编出来的数字，比不做更有害。

如果环境里能起 broker，验证顺序应该是：

1. `docker compose up -d rabbitmq` 后确认 `5673` 可连；
2. 报名一次，确认 `notification.registration` 队列 depth 从 0→1→0；
3. **停掉 broker 再报名**：业务必须照常成功（消息进不了队列不能拖垮报名），
   恢复后补偿由 outbox 扫描补发 —— 这一步才真正验证了「异步与业务解耦」；
4. 重复投递同一条消息，确认幂等键让第二条被拒而不是产生两条通知。

## 7. 验证入口

- 集成测试：`NotificationFlowIntegrationTest`
  - 报名提交后通知真的落库（轮询等异步结果，不靠 sleep 拍脑袋）；
  - **事务回滚后一条通知都没有**（`AFTER_COMMIT` 的存在理由）；
  - 越权标记别人的通知返回 404，对方仍是未读；
  - 全部已读幂等。
- 线程/MDC：`AsyncConfigTest`（traceId 跨线程传递、任务结束后清理、无上下文时不凭空造）。
- 端到端：`verify-p8.sh`（10 组真实 HTTP 校验，含异步线程名与指标下钻）。
