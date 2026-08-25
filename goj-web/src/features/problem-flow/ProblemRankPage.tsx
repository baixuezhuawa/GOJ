import { useParams } from "react-router-dom";
import { AppShell } from "../../components/layout/AppShell";
import { ProblemSidebar } from "./ProblemSidebar";
import { ProblemTabs } from "./ProblemTabs";
import { parseProblemId } from "./problemRoute";
import { useProblemDetail } from "./useProblemDetail";

export function ProblemRankPage() {
  const { problemId: routeProblemId } = useParams();
  const problemId = parseProblemId(routeProblemId);
  const { problem, loading } = useProblemDetail(problemId);

  return (
    <AppShell sidebar={<ProblemSidebar problem={problem} loading={loading} />} sidebarLabel="Problem info">
      {problemId ? <ProblemTabs problemId={problemId} /> : null}
      <section className="content-panel problem-rank-placeholder">
        <span className="request-state__icon" aria-hidden="true">♜</span>
        <h1>排行榜暂未开放</h1>
        <p>当前后端没有题目排行榜接口，本页只保留入口位置。</p>
      </section>
    </AppShell>
  );
}
