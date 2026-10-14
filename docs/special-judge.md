# 答案不唯一题判题

当前实现沿用批量测试点策略。题目类型为 `0`（普通）、`1`（答案不唯一）、`2`（交互，暂不支持执行）。

## 校验程序约定

执行链路如下：

```text
input.txt → 选手程序 → 选手标准输出
                          ↓ 作为标准输入
                       校验程序 → 校验程序标准输出 → 与 output.txt 比对
```

校验程序输出的是用于最终比对的文本。它只从标准输入接收选手输出，不额外接收原始测试输入或参考答案。本协议适用于能把合法输出转换成固定比对结果的题目；依赖原始输入进行验证的其他题型需要另行扩展输入协议。

比对沿用 `ExactOutputChecker` 的规则：统一 CRLF 为 LF，移除文本末尾空白后比较。校验程序编译失败、异常退出或超时属于系统故障，由现有任务失败处理流程接管。选手输出格式不合法时，校验程序应正常退出并输出与参考答案不匹配的文本，从而得到 WA。

校验程序复用已配置的语言适配器，当前配置包括 `java11`、`py3`。引擎在一次判题中准备编译产物，测试点共用，任务结束后清理。运行时间和内存限制沿用题目限制；选手成绩中的耗时和内存仅统计选手程序。

## 存储和代码入口

- `problem.problem_type` 保存题目类型，原有题目默认为普通题。
- `problem_checker` 按 `problem_id` 唯一保存当前语言、源码及审计字段。加载请求时读取当前代码，无历史版本。
- `JudgeRequestConfigurer` 供普通提交、比赛提交、管理员验题三个入口共用。
- `JudgeEngine` 复用 `Compiler` 准备选手及校验程序；`SpecialOutputChecker` 复用 `Runner` 和 `ExactOutputChecker`。
- `OutputChecker.check(OutputCheckContext)` 使用参数对象，后续扩充字段不改变方法签名。

管理员创建、编辑题目的 DTO 支持 `problemType`。创建时省略默认普通题；编辑时省略保持原值。校验程序本期通过 SQL 维护。

## 安装示例

SQL 目录只保留 `schema.sql`（完整表结构）和 `data.sql`（开发环境初始化数据）。全新数据库按此顺序执行；`schema.sql` 包含删表操作，不用于保留已有数据的升级。

`data.sql` 已包含第 7 题“三个整数中的最大值”、第 8 题“任意排列（答案不唯一）”、第 8 题校验代码及两题的测试数据记录。第 7 题保留草稿状态，两个数据集分别有 12 和 4 个测试点。

生成示例数据：

```powershell
& D:\Work\Project\GOJ\docs\examples\special-checker\generate-test-data.ps1
```

默认生成至：

```text
D:\ProgramData\goj-data\testData\p8\v1\
  test1\input.txt、output.txt    n = 1
  test2\input.txt、output.txt    n = 3
  test3\input.txt、output.txt    n = 5
  test4\input.txt、output.txt    n = 1000
```

初始化数据中的示例题 ID 固定为 8，`storage_path` 为 `testData/p8/v1`。正常创建的新题仍使用数据库自增 ID。第 7 题的本地测试数据位于 `testData/p7/v1`；SQL 仅保存元信息，换到新机器时需要一并准备数据文件。

`problem_test_data` 原有的 `version = 1` 表示测试数据版本，与校验程序无版本的设计无关。示例使用本地目录，不要求上传 ZIP。

## 验证示例

1. 提交 `docs/examples/special-checker/accepted.py`，语言选择 `py3`。它输出倒序排列，应为 AC；其输出与升序 `output.txt` 不同，可以确认特殊校验确实生效。
2. 提交同目录的 `wrong_answer.py`。它重复输出 1，应在第二个测试点得到 WA。
3. 提交只输出 `abc` 的程序，应为 WA，不能因选手输出格式错误导致系统故障。
4. 校验程序源码见同目录的 `checker.py`，与 `sql/data.sql` 中的源码一致。

例如 `n = 3`：选手输出 `3 2 1`，校验程序输出 `1 2 3`，与 `output.txt` 匹配。输出 `1 1 3` 时排序后仍与参考答案不符。
