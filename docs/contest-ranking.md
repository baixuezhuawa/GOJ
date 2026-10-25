# 比赛排行榜实现说明

## 源码阅读顺序

1. `goj-server/.../contest/application/model/LiveRankings.java`：单场榜单结构。
2. `goj-server/.../contest/application/ranking/RankingManager.java`：内存管理、锁、幂等、乱序和重测。
3. `goj-server/.../contest/domain/ranking/RankCalculator.java`：共用 ACM 规则。
4. `goj-server/.../contest/application/ranking/RankingArchiveService.java`：归档和完整快照。
5. `goj-server/.../contest/application/ranking/VpRankingService.java`：VP 回放入口。
6. `goj-server/.../contest/application/scheduling/ContestTask.java`：比赛阶段编排。

排行榜属于 `contest` 子模块，没有独立进程，也没有策略工厂或工作流框架。
原空的 `ContestRankManager` 迁移为 `application/ranking/RankingManager`。

## 实时榜

`ContestTask.beginner` 在开赛前 60 秒初始化正式参赛者、题目和零成绩行。
服务重启后，尚未进入 SYNC 的比赛首次收到消息或查询时从正式提交恢复一次，之后消息更新不查询提交表。
报名成功后新增行；赛前取消报名移除行，赛中不能取消。

调用链：

```text
Judge 提交结果事务提交
  → ContestJudgeResultPublisher
  → GOJ_CONTEST_RESULT
  → ContestJudgeResultConsumer
  → RankingManager.applyResult
  → RankCalculator.calculateProblem
  → LiveRankings.updateProblemState
```

消息字段为 `contestId / problemId / userId / submissionId / submissionTime / version / status`。
Judge 只发送当前提交。Manager 在单场比赛锁内维护用户、题目、提交 ID 三层索引，提交按 ID 升序存入 TreeMap。
相同提交只有更高版本才能替换记录；只扫描对应用户的对应题目，按差额更新总通过数和罚时。

`LiveRankings` 不处理版本、锁或数据库；题目下标直接采用创建比赛时约束好的 `sortOrder`（0 起、连续）。
`SingleFlight` 从结构对象移除：它不提供成绩修改互斥，也不缓存顺序执行的查询结果。
Manager 每场使用 `ReentrantReadWriteLock`：初始化、消息更新、报名变更和关闭使用写锁，查询检查版本和复制成绩使用读锁。

`rankingVersion` 记录榜单展示变化，`cachedVersion` 记录查询缓存版本，与单份提交的重测版本无关。
题格或参赛者变化才递增榜单版本；重复、旧消息和不改变展示的结果不使缓存失效，但仍保留有效提交明细供重测使用。
查询版本一致时，只在读锁内深拷贝当前页。版本不一致时，由单场 `rebuildLock` 串行生成缓存：读锁内复制整榜并记录版本，锁外排序，写锁内检查关闭状态并发布。
排序期间允许消息继续更新；缓存始终标记复制时的版本，本次返回该副本，下次查询再检查，不循环追赶新版本。
缓存发布后不再修改其行或题格，只替换列表引用；分页返回深拷贝，不暴露缓存对象。

## 成绩模型

```text
UserRankRow：rank / userId / username / penalty / acceptedCount / states
ProblemState：attempt / accepted / acceptedMinute
```

`attempt` 为首次 AC 前计罚的错误次数；系统错误不计罚。首次 AC 按提交 ID 确定。
罚时以分钟计，未 AC 不贡献总罚时；通过数降序、罚时升序，并列排名为 1、1、3。
同成绩按用户 ID 保持稳定显示，但不会因此拆开并列排名。

原 `ContestRankService` 的批量查询、用户二分定位和全量计算能力保留，字段统一到上述模型，计分及排序调用共用计算器。
旧 Redis 缓存前缀改为 `contest:rank:snapshot:v2:`，避免把旧字段缓存读入新模型。
旧 VO 类没有继续参与新榜单计算，暂时保留以免删除用户原有代码。

## 比赛阶段与归档

```text
RUNNING → WAITING → SYNC → FINISH
```

- WAITING：等待正式比赛时间范围内的提交全部到达终态，不等待 MQ 消息全部消费。
- SYNC：成功进入该阶段后关闭实时榜，同步历史提交，再从历史表顺序计算完整榜单快照。
- FINISH：归档成功后公开题目并完成比赛。未来 Hack、系统重测插在 SYNC 前。

正式提交事务锁定比赛行，阶段更新也修改同一行；进入 WAITING 前已经受理的提交必须先完成入库。
正式提交入口按比赛起止时间及阶段限制，不能在 WAITING/SYNC 期间产生新的正式提交。

`RankingManager.close` 与消息更新使用同一上下文的写锁，先标记关闭，再释放实时榜、提交索引和查询缓存，最后从 Map 移除上下文。
已持有旧引用的请求在锁内检查关闭状态；迟到消息在上下文缺失时读取比赛阶段，遇到 SYNC 或 FINISH 正常结束消费，不加载题目、选手和提交。
SYNC 扫描在归档前重复关闭，补偿阶段更新成功但尚未清理的情况；归档失败不恢复内存榜。

历史表 `contest_submission_history`：

- 正式提交按原 `Submission.id` 升序离散化为连续 `sequence_no`。
- 保存原提交 ID、用户、题目、相对毫秒时间和最终判题结果，不保存判题中间过程。

快照表 `contest_rank_snapshot`：

- 保存完整 `UserRankRow` 列表 JSON、截止序号、相对时间。
- 第 0 份为初始榜单；每 100 份保存一次；末尾不足 100 份也保存最终快照。
- 内部的提交版本索引不进入快照。

整场归档在一个事务中删除旧归档并重新生成；失败回滚，停留 SYNC 等待重试。
VP 仅允许读取 FINISH 比赛，避免读取半成品。当前实现一次加载整场数据，没有分批归档和大赛压测。

## VP 计算入口

本次按用户确认，仅提供 Java 回放入口，不增加 VP 场次表、开始接口、提交接口或 VP REST 查询。

```java
vpRankingService.replay(
    contestId, userId, username,
    vpStart, now, vpSubmissions
);
```

调用方提供本次 VP 开始时间和经过场次筛选的提交集合；`now` 应由服务端在每次刷新时获取。
截止进度不超过原比赛时长。历史提交按“相对时间 <= 当前进度”确定截止序号，加载截止序号之前最近的快照。
恢复的是独立副本。当前用户行清零，历史增量跳过当前用户，再应用该用户本次 VP 的提交。
刷新即推进历史进度，不要求用户先有新的提交。
结果返回完整已排名行，分页由未来的 VP 查询入口处理。

## 正式榜 REST

`GET /contest/{contestId}/ranking?page=1&size=20`

- 进行中、WAITING 使用实时内存榜的版本缓存并分页。
- SYNC 返回业务提示“排行榜正在整理”，不返回榜单。
- FINISH 使用数据库最终快照。
- 实时查询遇到关闭的上下文会重新读取比赛阶段，返回整理中或最终榜；关闭前已复制完成的查询可以返回已有副本。
- 返回 `contestId / contestStatus / contestProblems / contestRankingRows`。
- `contestRankingRows.records` 使用新的 `UserRankRow` 字段。

## 数据库及启动

已有数据库使用 `sql/migrations/20261021_contest_ranking.sql` 增量创建两张表；它不清空现有业务数据。
`sql/schema.sql` 已同步新增表，但这是全量重建脚本，会删除原数据，不能用于保留数据的升级。
本次没有执行 SQL，没有修改运行中的数据库。

已处于 FINISH 的老比赛不会自动回填归档；若没有快照，查询会明确提示尚无排行榜归档。
本次没有自动改动旧比赛阶段或批量回填旧比赛。

Judge 与 Server 的 YAML 已配置同一结果 topic。必须先创建归档表，再启动更新后的服务。
本次未改前端页面；已有客户端若使用旧 `solveCount / penaltyMinutes / problemResults` 字段，需要按新模型对齐。

## 当前验证与边界

初版定向 Maven 测试覆盖 31 项：Server 排行榜 11 项，Judge 消息发布 2 项、结果写回 6 项及原判题测试 12 项。
本次内存清理和查询缓存调整单独运行 `RankingTests`，19 项全部通过，覆盖原有计分、归档、VP 回放，以及缓存复用、分页副本隔离、并发重建、排序期间更新或关闭、清理等待正在执行的更新、迟到消息、阶段切换失败和查询切换等边界。
事务事件测试使用 Spring 事务同步回调和 Mock MQ；归档 Mapper 使用 Mock，不代表真实数据库回滚或 MQ 联调通过。

当前明确边界：

- 单实例 Server；进入 SYNC 后自动释放实时榜、提交明细和查询缓存。
- 事务提交后 MQ 发送失败仅记录日志，没有持久化发送补偿；实时榜可能暂缺结果，重启恢复和最终归档会读取数据库最终结果。
- 归档完成后的重测不会自动重建历史与快照，后续若开放此能力需明确重新归档流程。
- 实时更新会扫描该用户该题的提交明细；不是整行重算，也不是常数时间更新。
- 查询缓存过期后仍需复制和排序整榜，未限制重建频率；复制期间持有读锁，消息更新需要等待复制完成。
- 尚未执行真实数据库/MQ/REST 联调，也未进行负载测试。
