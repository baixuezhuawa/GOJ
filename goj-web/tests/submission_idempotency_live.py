"""真实环境提交幂等回归；创建带 idem_e2e 标识的测试账号、比赛和提交，保留证据。"""
import concurrent.futures
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import uuid

import requests


ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "goj-server/target/idempotency-live-report.json"
BASE = os.environ.get("GOJ_TEST_API_URL", "http://localhost:8080")


def sql(statement):
    """仅使用测试生成的数字、UUID 等参数；数据库密码通过环境传递，不写日志。"""
    env = dict(os.environ, MYSQL_PWD=os.environ["GOJ_MYSQL_PASSWORD"])
    process = subprocess.run(["mysql", "-h", "localhost", "-u", "root", "goj", "--batch",
                              "--skip-column-names", "--default-character-set=utf8mb4", "-e", statement],
                             env=env, capture_output=True, encoding="utf-8", check=True)
    return process.stdout.strip()


def call(path, payload=None, token=None):
    """调用真实 API，不输出密码或令牌。"""
    response = requests.request("POST" if payload is not None else "GET", BASE + path,
                                json=payload, headers={"Authorization": "Bearer " + token} if token else {}, timeout=25)
    return response.status_code, response.json()


def main():
    """验证真实 API、MySQL、RocketMQ、Judge 与沙箱的完整提交链路。"""
    run = uuid.uuid4().hex[:10]
    report = {"run": run, "users": [], "checks": [], "submissions": []}
    tokens = []

    def record(name, details):
        report["checks"].append({"name": name, "details": details})
        REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(name, details, flush=True)

    def accepted(path, payload, token):
        status, body = call(path, payload, token)
        assert status == 200 and body["code"] == 200, (status, body)
        return body["data"]

    def wait_for_judging(ids):
        limit = time.monotonic() + 55
        while time.monotonic() < limit:
            rows = sql("SELECT s.id,s.status,j.status,j.attempt_count FROM submission s JOIN judge_task j "
                       "ON j.business_id=s.id WHERE s.id IN (" + ",".join(map(str, ids)) + ") ORDER BY s.id")
            parsed = [line.split("\t") for line in rows.splitlines()]
            if len(parsed) == len(ids) and all(row[2] in ("SUCCEEDED", "DEAD") for row in parsed):
                assert all(row[1] == "Accepted" and row[2] == "SUCCEEDED" for row in parsed), parsed
                return parsed
            time.sleep(1)
        raise AssertionError("评测未在期限内完成: " + rows)

    try:
        for index in range(2):
            username = f"idem_e2e_{run}_{index}"
            password = secrets.token_urlsafe(24)
            status, body = call("/user/register", {"username": username, "password": password,
                                                  "confirmPassword": password})
            assert status == 200 and body["code"] == 200, (status, body)
            status, body = call("/user/login", {"username": username, "password": password})
            assert status == 200 and body["code"] == 200, (status, body)
            token = body["data"]["token"]
            tokens.append(token)
            status, body = call("/user/me", token=token)
            report["users"].append({"id": body["data"]["userId"], "username": username})
        user = report["users"][0]["id"]
        path = "/submission/submit"
        code = f"# idem_e2e_{run}\nimport sys\na=list(map(int,sys.stdin.read().split()))\nprint(sum(a))\n"
        payload = {"requestId": str(uuid.uuid4()), "problemId": 1, "language": "py3", "sourceCode": code}

        for value in (None, "bad-uuid"):
            invalid = dict(payload)
            if value is None:
                invalid.pop("requestId")
            else:
                invalid["requestId"] = value
            status, body = call(path, invalid, tokens[0])
            assert status == 400, (status, body)
        record("普通入口拒绝缺失或非法 UUID", "HTTP 400")

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            results = list(executor.map(lambda _: accepted(path, payload, tokens[0]), range(8)))
        assert all(result == results[0] for result in results), results
        regular_id = results[0]["submissionId"]
        report["submissions"].append(regular_id)
        record("普通入口 8 路并发回放同一提交", results[0])
        assert accepted(path, payload, tokens[0]) == results[0]
        assert accepted(path, dict(payload, requestId=payload["requestId"].upper()), tokens[0]) == results[0]
        record("普通入口重复及大写 UUID 回放", regular_id)
        status, body = call(path, dict(payload, sourceCode=code + "# changed\n"), tokens[0])
        assert status == 409, (status, body)
        status, body = call(path, dict(payload, requestId=str(uuid.uuid4())), tokens[0])
        assert status == 422, (status, body)
        record("内容冲突及不同 UUID 频率限制", "409 / 422")
        other = accepted(path, payload, tokens[1])
        assert other["submissionId"] != regular_id
        report["submissions"].append(other["submissionId"])
        record("同 UUID 跨用户隔离", other)

        contest_id = int(sql("INSERT INTO contest(title,status,register_start_time,register_end_time,start_time,end_time,remark) "
                             f"VALUES('idem_e2e_{run}','RUNNING',NOW()-INTERVAL 2 HOUR,NOW()-INTERVAL 1 HOUR,"
                             f"NOW()-INTERVAL 30 MINUTE,NOW()+INTERVAL 1 DAY,'idem_e2e_{run}'); SELECT LAST_INSERT_ID();"))
        report["contestId"] = contest_id
        sql("INSERT INTO contest_problem(contest_id,problem_id,display_name,display_code,sort_order,test_data_id,release_status) "
            f"VALUES({contest_id},1,'A+B idempotency test','A',1,1,'OPENING'); "
            f"INSERT INTO contest_participate(user_id,contest_id,participate_type) VALUES({user},{contest_id},'REAL');")
        contest_path = f"/contest/{contest_id}/problem/A"
        contest_payload = {"requestId": str(uuid.uuid4()), "language": "py3", "sourceCode": code}
        missing = dict(contest_payload)
        missing.pop("requestId")
        assert call(contest_path, missing, tokens[0])[0] == 400
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            results = list(executor.map(lambda _: accepted(contest_path, contest_payload, tokens[0]), range(8)))
        assert all(result == results[0] for result in results)
        contest_result = results[0]
        assert contest_result["submissionType"] == "CONTEST"
        report["submissions"].append(contest_result["submissionId"])
        record("比赛入口 8 路并发及必传 UUID", contest_result)
        record("真实沙箱评测及任务终态", wait_for_judging(report["submissions"]))

        # 仅修改本脚本新建的测试比赛，验证阶段拒绝后同 UUID 重试。
        sql(f"UPDATE contest SET status='DRAFT' WHERE id={contest_id}")
        retry_payload = dict(contest_payload, requestId=str(uuid.uuid4()), sourceCode=code + "# retry after rejection\n")
        status, body = call(contest_path, retry_payload, tokens[0])
        assert status == 422, (status, body)
        assert sql(f"SELECT COUNT(*) FROM submission WHERE user_id={user} AND request_id='{retry_payload['requestId']}'") == "0"
        assert accepted(contest_path, contest_payload, tokens[0]) == contest_result
        sql(f"UPDATE contest SET status='RUNNING' WHERE id={contest_id}")
        retried = accepted(contest_path, retry_payload, tokens[0])
        report["submissions"].append(retried["submissionId"])
        record("比赛阶段变化回放及失败后同 UUID 重试", retried)
        wait_for_judging([retried["submissionId"]])

        sql(f"UPDATE contest SET status='FINISH',end_time=NOW() WHERE id={contest_id}; "
            f"UPDATE contest_problem SET release_status='PUBLISH' WHERE contest_id={contest_id};")
        assert accepted(contest_path, contest_payload, tokens[0]) == contest_result
        practice_payload = dict(contest_payload, requestId=str(uuid.uuid4()), sourceCode=code + "# after contest\n")
        practice = accepted(contest_path, practice_payload, tokens[0])
        assert practice["submissionType"] == "REGULAR"
        assert accepted(contest_path, practice_payload, tokens[0]) == practice
        report["submissions"].append(practice["submissionId"])
        record("赛后旧请求保持比赛类型，新请求转普通并可回放", practice)
        record("所有提交最终状态", wait_for_judging(report["submissions"]))
        rows = sql("SELECT s.id,s.request_id,COUNT(DISTINCT j.id) FROM submission s LEFT JOIN judge_task j ON j.business_id=s.id "
                   "WHERE s.id IN (" + ",".join(map(str, report["submissions"])) + ") GROUP BY s.id,s.request_id")
        assert all(row.split("\t")[2] == "1" for row in rows.splitlines()), rows
        record("每条提交只有一个初始评测任务", rows.splitlines())
        report["result"] = "passed"
    except Exception as error:
        report["result"] = "failed"
        report["error"] = str(error)
        raise
    finally:
        for token in tokens:
            try:
                call("/user/logout", {}, token)
            except Exception:
                pass
        REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print("报告:", REPORT, flush=True)


if __name__ == "__main__":
    main()
