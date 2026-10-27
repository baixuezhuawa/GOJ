import { Link, useParams } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { AppShell } from "../../components/layout/AppShell";
import { CatLoader } from "../../components/ui/CatLoader";
import { parseSubmissionId } from "../problem-flow/problemRoute";
import { SubmissionDetailContent } from "../problem-flow/SubmissionDetailContent";
import { useContestSubmissionDetail } from "./useContestSubmissionDetail";

/** 比赛提交详情页，复用现有结果展示并保留比赛访问入口。 */
export function ContestSubmissionDetailPage() {
  const params = useParams();
  const contestId = parseSubmissionId(params.contestId);
  const submissionId = parseSubmissionId(params.submissionId);
  const auth = useAuth();
  const detail = useContestSubmissionDetail(contestId, submissionId,
    auth.status === "authenticated" ? auth.token : null);

  return (
    <AppShell sidebar={
      <section className="sidebar-panel submission-detail-sidebar">
        <h2>比赛提交 #{submissionId ?? "--"}</h2>
        <p>{detail.submission?.contestTitle ?? `比赛 #${contestId ?? "--"}`}</p>
        <Link to="/contests">返回比赛列表</Link>
      </section>
    } sidebarLabel="比赛提交信息">
      <section className="content-panel submission-detail-panel">
        {auth.status === "loading" ? <CatLoader label="正在确认登录状态..." /> :
          auth.status !== "authenticated" ? (
            <div className="request-state">
              <h1>请先登录</h1>
              <p>登录后可以查看比赛提交和判题结果。</p>
              <Link className="primary-button" to="/login">前往登录</Link>
            </div>
          ) : (
            <SubmissionDetailContent {...detail}
              onRetry={detail.retry}
              problemHref={null}
            />
          )}
      </section>
    </AppShell>
  );
}
