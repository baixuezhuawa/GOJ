# Judge 精简第一版实施与验证

日期：2026-10-18。用户已授权先实现，再根据阅读效果动态调整。

## 实际调用链

```text
RocketMQ / Redis 消费者
  → JudgeTaskProcessor.process（原领取逻辑）
  → JudgeServiceImpl.judge
      → 按提交类型准备材料和 JudgeSession，直接绑定结果服务的更新方法
  → JudgeEngine.judge(session)
      → 编译选手程序
      → normalJudge / specialJudge
      → 共用测试点循环、运行、校验
      → session.push → 结果服务 → 主表及已有终态统计
      → finally 清理编译产物
  → JudgeTaskStateService.markSucceeded / handleFailure
```

交互题由 Engine 明确提示未支持。恢复定时器直接调用 JudgeTaskStateService.recover。

## 减法范围

- 删除 HandlerRegistry、SubmissionJudgeHandler、三个 Handler、SubmissionJudgeContext、LoadedSubmissionJudgeContext。
- 删除 JudgeStrategy、NormalJudgeStrategy、SpecialJudgeStrategy，重复测试点循环合为一个 Engine 方法。
- 删除 JudgeContext，运行期语言配置及编译产物放入 JudgeSession；保留 JudgeExecutionRequest 存放准备好的输入材料。
- 删除无引用的 OutputCheckerRegistry、ExecutionMode。按题目类型直接分派，保留已有 Compiler、Runner、语言适配器及文本/特殊校验器。
- 原 FailureService、RecoveryService 合并为 JudgeTaskStateService，成功更新只保留一处，系统错误的业务回写移入现有结果服务。
- 生产 Java 类型净减少 14 个，生产代码相对本轮基线净减少约 640 行；测试代码另计。

## 回写与事务

Session 只调用一个业务 writer，结果服务内部维护主表及关联表。现有普通提交统计更新器仍保留，没有额外引入回调层。

任务 DEAD 与提交 SYSTEM_ERROR 通过状态服务和结果服务的 Spring 事务共同提交；正常业务终态与任务 SUCCEEDED 仍是两个写入步骤，恢复逻辑补齐后者。本轮没有增加执行代次或 fencing token。

更新影响 0 行时读取当前记录：已经终态可视为幂等完成，记录不存在或仍非终态则返回失败。管理员验题补齐终态条件保护。Session 在成功回写后才标记完成。

当前比赛结果服务仅更新比赛提交记录，关联统计仍未实现；此前讨论中关于已有榜单更新的表述不适用于这次读取的源码。此次没有新增榜单功能。

## 验证

- `mvn -pl goj-judge -am clean test -q`：通过。
- `mvn -pl goj-server,goj-judge -am test -q`：通过；common 7、Judge 41、server 15，共 63 个常规测试。
- 新增显式执行的 `JudgeLiveIT`，默认常规测试不会运行。RocketMQ 和 Redis 各执行一次同样的 10 场景，均通过。
- 真实 Python 普通题：AC、WA、RE、TLE；真实 Java 11：CE、AC；特殊题验题：AC、WA；比赛普通提交：AC；校验程序异常：第一次 RETRY_WAIT，第二次 DEAD，提交 system error。
- 检查数据库终态、judge_end_time、任务状态、attempt_count；验证普通提交统计累计为 6 次，重复投递不增加执行次数和统计。
- 模拟业务已终态而任务 PROCESSING 且租约过期，调用真实恢复服务确认补齐 SUCCEEDED，统计不重复累计。
- RocketMQ 初始化阶段发现测试环境不提供 TBW102 路由且新消费组需显式创建；测试工具建立独立 topic/group 后通过，无需修改正式消费者。

真实测试使用临时数据库（只从 goj 复制表结构）、JUnit 临时文件目录、独立 MQ topic/group，以及运行前确认空的 Redis 15 号库。数据库、MQ 测试资源已清理，测试进程已退出；失败初始化轮次遗留的测试 topic 也已单独清理。

## 重跑入口与证据边界

```powershell
mvn -pl goj-judge -am '-Dtest=JudgeLiveIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl goj-judge -am '-Dtest=JudgeLiveIT' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dlive.redis=true' test
```

需要 GOJ_MYSQL_PASSWORD 及当前开发环境中的 MySQL、Redis、RocketMQ 和 go-judge。测试通过真实消费者、沙箱、MyBatis 和结果服务；任务记录由测试创建，首次投递及重试补发由测试发送，没有覆盖登录、提交 HTTP API、server 定时投递器或真实进程崩溃。本轮未实测 MLE 和基础设施断网故障，相关状态边界由常规测试覆盖。

本地运行日志：`goj-judge/target/live-it.log`、`goj-judge/target/live-redis-it.log`；target 在 clean 后会被清理。

## 目录精简

同日按用户要求清理空目录，并去掉 application、domain、infrastructure、interfaces 中间包。保留 task 与 judging 两个职责分区，包内直接按 engine、result、compiler、runner、mapper 等实际用途组织。共移动 72 个生产 Java 文件，同步修改测试 import、Mapper XML namespace、Feign/Mapper 扫描、日志和接口文档包配置；此次不改变方法实现。

原先 6 个空目录及迁移后留下的 25 个空目录均已删除。当前结构和阅读顺序见 [Judge 源码入口](../../../goj-judge/README.md)。

迁移后重新执行 clean test：common 7、Judge 41 个常规测试通过；真实 RocketMQ 链路的 10 个判题场景及重复投递、终态恢复检查再次通过，临时数据库和 MQ 资源清理完成。Redis 的真实验证为上一次重构结果，本次目录迁移后未重复运行。
