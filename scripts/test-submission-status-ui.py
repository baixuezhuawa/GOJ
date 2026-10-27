"""使用模拟接口验证详情页轮询，浏览器截图保存在忽略的 test-results 中。"""
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE = os.environ.get("GOJ_UI_TEST_URL", "http://127.0.0.1:5179")
OUT = Path(__file__).resolve().parents[1] / "goj-web" / "test-results" / "submission-status"
OUT.mkdir(parents=True, exist_ok=True)


def check(browser, contest, mobile=False):
    context = browser.new_context(viewport={"width": 390 if mobile else 1440, "height": 844 if mobile else 960})
    if contest:
        context.add_init_script("localStorage.setItem('goj.token', 'test-token')")
    page = context.new_page()
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    counts = {"detail": 0, "status": 0}
    prefix = "/contest/8/submission/123" if contest else "/submission/123"
    detail = dict(id=123, version=1, score=0, username="测试选手", language="py3",
                  problemName="两数之和", problemId=1, sourceCode="print(1 + 2)", status="running",
                  timeMs=None, memoryKb=None, submissionTime="2026-10-26 12:00:00",
                  judgeStartTime="2026-10-26 12:00:01", judgeEndTime=None,
                  judgeMsg=None, compilerMsg=None, contestId=8, contestTitle="周末练习赛", problemCode="A")

    def route(request):
        path = request.request.url.split("/api", 1)[-1]
        data = {}
        if path == prefix:
            counts["detail"] += 1
            data = detail
        elif path == prefix + "/status":
            counts["status"] += 1
            if contest:
                assert request.request.headers.get("authorization") == "Bearer test-token"
            data = {key: detail[key] for key in ["id", "version", "score", "status", "timeMs", "memoryKb",
                                               "compilerMsg", "judgeMsg", "judgeStartTime", "judgeEndTime"]}
            data.update(status="Accepted", score=100, timeMs=12, memoryKb=2048, judgeEndTime="2026-10-26 12:00:03")
        elif path == "/user/me":
            data = dict(userId=1, username="测试选手", nickname="测试选手", roles=["USER"])
        request.fulfill(status=200, content_type="application/json", body=json.dumps(dict(code=200, msg="操作成功", data=data)))

    page.route(BASE + "/api/**", route)
    path = "/contests/8/submissions/123" if contest else "/submissions/123"
    page.goto(BASE + path)
    page.wait_for_load_state("networkidle")
    page.get_by_role("heading", name="两数之和").wait_for()
    page.get_by_text("Accepted", exact=True).first.wait_for(timeout=10000)
    page.wait_for_timeout(3000)
    assert counts == {"detail": 1, "status": 1}, counts
    assert not errors, errors
    assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "页面横向溢出"
    page.screenshot(path=str(OUT / (("contest" if contest else "regular") + ("-mobile" if mobile else "-desktop") + ".png")), full_page=True)
    # 切换页面必须取消轮询定时器。
    page.goto(BASE + path)
    page.get_by_role("heading", name="两数之和").wait_for()
    page.goto(BASE + "/")
    previous = counts["status"]
    page.wait_for_timeout(3000)
    assert counts["status"] == previous
    context.close()


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel="msedge", headless=True)
    try:
        for contest, mobile in [(False, False), (True, False), (True, True)]:
            check(browser, contest, mobile)
        guest = browser.new_page()
        guest.route(BASE + "/api/**", lambda route: route.fulfill(status=200, content_type="application/json", body='{"code":200,"data":{}}'))
        guest.goto(BASE + "/contests/8/submissions/123")
        guest.get_by_role("heading", name="请先登录").wait_for()
        guest.close()
        print("PASS: 普通/比赛详情只加载一次，终态停止轮询，离开取消，比赛登录限制，桌面/手机布局")
    finally:
        browser.close()
