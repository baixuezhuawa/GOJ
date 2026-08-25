import { Link } from "react-router-dom";
import { CatLoader } from "../../components/ui/CatLoader";
import type { SubmissionDetail } from "../../types/api";
import { CodeEditor } from "./CodeEditor";
import { statusClassName } from "./submissionStatus";

interface SubmissionDetailContentProps {
  submission: SubmissionDetail | null;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  editorHeight?: string;
}

export function SubmissionDetailContent({
  submission,
  loading,
  error,
  onRetry,
  editorHeight = "470px",
}: SubmissionDetailContentProps) {
  if (loading && !submission) {
    return <CatLoader label="正在等待判题消息..." />;
  }

  if (error && !submission) {
    return (
      <div className="request-state request-state--error" role="alert">
        <span className="request-state__icon">!</span>
        <strong>提交详情加载失败</strong>
        <p>{error}</p>
        <button className="primary-button" type="button" onClick={onRetry}>
          Retry
        </button>
      </div>
    );
  }

  if (!submission) return null;

  return (
    <>
      <header className="submission-detail-header">
        <div>
          <span className="eyebrow">Submission #{submission.id}</span>
          <h1>{submission.problemName}</h1>
        </div>
        <span className={`submission-status submission-status--large ${statusClassName(submission.status)}`}>
          {submission.status}
        </span>
      </header>

      {error && (
        <div className="polling-error" role="alert">
          <span>{error}</span>
          <button type="button" onClick={onRetry}>Retry</button>
        </div>
      )}

      <dl className="submission-meta-grid">
        <div><dt>Author</dt><dd>{submission.username}</dd></div>
        <div><dt>Submit time</dt><dd>{submission.submissionTime ?? "--"}</dd></div>
        <div><dt>Time</dt><dd>{submission.timeMs == null ? "--" : `${submission.timeMs} ms`}</dd></div>
        <div><dt>Memory</dt><dd>{submission.memoryKb == null ? "--" : `${submission.memoryKb} KB`}</dd></div>
        <div><dt>Begin time</dt><dd>{submission.judgeStartTime ?? "--"}</dd></div>
        <div><dt>End time</dt><dd>{submission.judgeEndTime ?? "--"}</dd></div>
        <div><dt>Language</dt><dd>{submission.language}</dd></div>
        <div>
          <dt>Problem</dt>
          <dd><Link to={`/problems/${submission.problemId}`}>{`#${submission.problemId} ${submission.problemName}`}</Link></dd>
        </div>
      </dl>

      <section className="submission-code-section">
        <h2>Source code</h2>
        <CodeEditor value={submission.sourceCode ?? ""} language={submission.language} readOnly height={editorHeight} />
      </section>

      <div className="submission-message-grid">
        <section>
          <h2>Compiler message</h2>
          <pre>{submission.compilerMsg?.trim() || "--"}</pre>
        </section>
        <section>
          <h2>Judge message</h2>
          <pre>{submission.judgeMsg?.trim() || "--"}</pre>
        </section>
      </div>
    </>
  );
}
