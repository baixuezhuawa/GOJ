import { Link, useParams } from "react-router-dom";
import { AppShell } from "../../components/layout/AppShell";
import type { SubmissionDetail } from "../../types/api";
import { parseSubmissionId } from "./problemRoute";
import { statusClassName } from "./submissionStatus";
import { SubmissionDetailContent } from "./SubmissionDetailContent";
import { useSubmissionDetail } from "./useSubmissionDetail";

function DetailSidebar({ submission }: { submission: SubmissionDetail | null }) {
  return (
    <section className="sidebar-panel submission-detail-sidebar">
      <span className="eyebrow">Submission</span>
      <h2>#{submission?.id ?? "--"}</h2>
      <div>
        <small>Status</small>
        <span className={`submission-status ${statusClassName(submission?.status)}`}>
          {submission?.status ?? "--"}
        </span>
      </div>
      <div>
        <small>Problem</small>
        {submission ? (
          <Link to={`/problems/${submission.problemId}`}>{submission.problemName}</Link>
        ) : <strong>--</strong>}
      </div>
      <div><small>Author</small><strong>{submission?.username ?? "--"}</strong></div>
      <div><small>Language</small><code>{submission?.language ?? "--"}</code></div>
    </section>
  );
}

export function SubmissionDetailPage() {
  const { submissionId: routeSubmissionId } = useParams();
  const submissionId = parseSubmissionId(routeSubmissionId);
  const { submission, loading, error, retry } = useSubmissionDetail(submissionId);

  return (
    <AppShell sidebar={<DetailSidebar submission={submission} />} sidebarLabel="Submission info">
      <section className="content-panel submission-detail-panel">
        <SubmissionDetailContent
          submission={submission}
          loading={loading}
          error={error}
          onRetry={retry}
        />
      </section>
    </AppShell>
  );
}
