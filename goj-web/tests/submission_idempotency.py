"""浏览器幂等流程回归：使用真实前端和可控 API 响应，不依赖业务数据库。"""
import json
import os
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect


def main():
    """模拟响应丢失、刷新恢复和业务拒绝，核对发出的 UUID 与代码。"""
    with sync_playwright() as playwright:
        options = {"headless": True}
        if os.environ.get("GOJ_TEST_BROWSER"):
            options["executable_path"] = os.environ["GOJ_TEST_BROWSER"]
        browser = playwright.chromium.launch(**options)
        page = browser.new_page()
        page.add_init_script("localStorage.setItem('goj.token', 'test-token')")
        requests = []
        state = {"mode": "lost"}

        def api(route):
            """用固定受理编号模拟同一个请求的回放。"""
            path = route.request.url.split("/api", 1)[1]
            if path == "/user/me":
                data = {"userId": 7, "username": "tester", "permissions": []}
            elif path == "/common/supported-language":
                data = [{"code": "py3", "enabled": True, "displayName": "Python"}]
            elif path == "/problem/1":
                data = {"id": 1, "problemName": "幂等测试题", "tags": [], "status": 1}
            elif route.request.method == "POST" and (path == "/submission/submit" or path.startswith("/contest/")):
                requests.append({"path": path, "payload": route.request.post_data_json})
                if state["mode"] == "lost":
                    route.abort("failed")
                    return
                if state["mode"] == "rejected":
                    route.fulfill(status=422, json={"code": 422, "msg": "重复提交相同代码频率过高", "data": None})
                    return
                data = {"submissionType": "CONTEST" if path.startswith("/contest/") else "REGULAR", "submissionId": 100}
            else:
                data = {"current": 1, "size": 10, "total": 0, "pages": 0, "records": []}
            route.fulfill(json={"code": 200, "msg": "操作成功", "data": data})

        page.route(re.compile(r"^http://[^/]+/api/"), api)
        page.goto(os.environ.get("GOJ_TEST_WEB_URL", "http://localhost:5179") + "/problems/1/submit")
        page.wait_for_load_state("networkidle")
        print("按钮:", page.get_by_role("button").all_text_contents())
        editor = page.locator(".cm-content")
        page.get_by_role("combobox", name="Language").select_option("py3")
        editor.fill("print(1)")
        page.get_by_role("button", name="Submit", exact=True).click()
        expect(page.get_by_role("button", name="确认原提交", exact=True)).to_be_enabled()
        first = requests[0]["payload"]
        assert first["sourceCode"] == "print(1)" and first["requestId"]

        # 刷新恢复待确认快照，即使草稿变化，重试仍发送第一次的原内容。
        page.reload()
        page.wait_for_load_state("networkidle")
        expect(page.get_by_role("button", name="确认原提交", exact=True)).to_be_enabled()
        page.get_by_role("combobox", name="Language").select_option("py3")
        editor.fill("print(2)")
        state["mode"] = "success"
        page.get_by_role("button", name="确认原提交", exact=True).click()
        expect(page.get_by_text("原提交已受理，提交编号：100。当前草稿可以作为新提交发送。", exact=True)).to_be_visible()
        assert requests[1]["payload"] == first
        expect(editor).to_have_text("print(2)")
        assert page.evaluate("sessionStorage.getItem('goj.pending-submission.7')") is None

        # 明确业务拒绝结束当前尝试；新提交拥有不同 UUID。
        state["mode"] = "rejected"
        page.get_by_role("button", name="Submit", exact=True).click()
        expect(page.get_by_text("重复提交相同代码频率过高", exact=True)).to_be_visible()
        assert requests[2]["payload"]["requestId"] != first["requestId"]
        assert requests[2]["payload"]["sourceCode"] == "print(2)"
        assert page.evaluate("sessionStorage.getItem('goj.pending-submission.7')") is None

        # 直接验证共用存储层的用户隔离、迟到清理、比赛快照和损坏保护。
        checks = page.evaluate("""async () => {
          const m = await import('/src/features/problem-flow/pendingSubmission.ts');
          const draft = {kind: 'CONTEST', contestId: 10, problemCode: 'A', payload: {language: 'py3', sourceCode: 'print(3)'}};
          const pending = m.preparePendingSubmission(7, draft);
          const same = m.preparePendingSubmission(7, {...draft, problemCode: 'B'});
          const isolated = m.readPendingSubmission(8) === null;
          m.clearPendingSubmission(7, 'old-response');
          const retained = m.readPendingSubmission(7).payload.requestId === pending.payload.requestId;
          return {same: same.payload.requestId === pending.payload.requestId, isolated, retained};
        }""")
        assert all(checks.values()), checks
        state["mode"] = "success"
        page.reload()
        page.wait_for_load_state("networkidle")
        page.get_by_role("button", name="确认原提交", exact=True).click()
        expect(page.get_by_text("原提交已受理，提交编号：100。当前草稿可以作为新提交发送。", exact=True)).to_be_visible()
        assert requests[-1]["path"] == "/contest/10/problem/A"
        assert requests[-1]["payload"]["sourceCode"] == "print(3)"

        page.evaluate("sessionStorage.setItem('goj.pending-submission.7', 'broken')")
        page.reload()
        page.wait_for_load_state("networkidle")
        expect(page.get_by_text("待确认提交记录无法读取，请先在提交记录中确认上次提交情况", exact=True)).to_be_visible()
        expect(page.get_by_role("button", name="Submit", exact=True)).to_be_disabled()
        page.evaluate("sessionStorage.removeItem('goj.pending-submission.7')")
        page.reload()
        page.wait_for_load_state("networkidle")
        screenshot = Path(__file__).parents[1] / "test-results" / "submission-idempotency.png"
        screenshot.parent.mkdir(exist_ok=True)
        page.screenshot(path=str(screenshot), full_page=True)
        print(json.dumps({"result": "passed", "requests": len(requests), "storage_checks": checks}, ensure_ascii=False))
        browser.close()


if __name__ == "__main__":
    main()
