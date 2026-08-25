import { useParams } from "react-router-dom";
import { AppShell } from "../../components/layout/AppShell";
import { MarkdownBlock } from "./MarkdownBlock";
import { ProblemRequestState } from "./ProblemRequestState";
import { ProblemSidebar } from "./ProblemSidebar";
import { ProblemTabs } from "./ProblemTabs";
import { parseProblemId } from "./problemRoute";
import { useProblemDetail } from "./useProblemDetail";

function ExampleBlock({ value }: { value: string | null }) {
  return <pre className="problem-example"><code>{value?.trim() || "--"}</code></pre>;
}

export function ProblemDetailPage() {
  const { problemId: routeProblemId } = useParams();
  const problemId = parseProblemId(routeProblemId);
  const { problem, loading, error, retry } = useProblemDetail(problemId);

  return (
    <AppShell
      sidebar={<ProblemSidebar problem={problem} loading={loading} />}
      sidebarLabel="Problem info"
    >
      {problemId ? <ProblemTabs problemId={problemId} /> : null}
      <article className="content-panel problem-detail-panel">
        {loading || error || !problem ? (
          <ProblemRequestState loading={loading} error={error} onRetry={retry} />
        ) : (
          <>
            <header className="problem-detail-header">
              <span className="eyebrow">Problem #{problem.id}</span>
              <h1>{problem.problemName}</h1>
              <dl>
                <div><dt>Time limit</dt><dd>{problem.timeLimit ?? "--"} ms</dd></div>
                <div><dt>Memory limit</dt><dd>{problem.memoryLimit ?? "--"} KB</dd></div>
              </dl>
            </header>

            <section className="problem-statement-section">
              <h2>Description</h2>
              <MarkdownBlock>{problem.description}</MarkdownBlock>
            </section>
            <section className="problem-statement-section">
              <h2>Input</h2>
              <MarkdownBlock>{problem.inputDescription}</MarkdownBlock>
            </section>
            <section className="problem-statement-section">
              <h2>Output</h2>
              <MarkdownBlock>{problem.outPutDescription}</MarkdownBlock>
            </section>
            <section className="problem-statement-section">
              <h2>Example input</h2>
              <ExampleBlock value={problem.inputExample} />
            </section>
            <section className="problem-statement-section">
              <h2>Example output</h2>
              <ExampleBlock value={problem.outPutExample} />
            </section>
            <section className="problem-statement-section">
              <h2>Example note</h2>
              <MarkdownBlock>{problem.exampleNote}</MarkdownBlock>
            </section>
          </>
        )}
      </article>
    </AppShell>
  );
}
