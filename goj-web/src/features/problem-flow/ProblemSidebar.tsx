import { Link } from "react-router-dom";
import type { ProblemDetail } from "../../types/api";

interface ProblemSidebarProps {
  problem: ProblemDetail | null;
  loading?: boolean;
}

export function ProblemSidebar({ problem, loading = false }: ProblemSidebarProps) {
  return (
    <section className="sidebar-panel problem-context">
      <span className="eyebrow">Problem context</span>
      <div className="problem-context__contest">
        <small>Contest name</small>
        <strong>{loading ? "..." : "--"}</strong>
        <p>普通题目暂不提供比赛归属。</p>
      </div>

      <div className="problem-context__section">
        <small>Author</small>
        {problem?.authorName ? (
          <Link to={`/users/${problem.authorName}`}>{problem.authorName}</Link>
        ) : (
          <strong>{loading ? "..." : "--"}</strong>
        )}
      </div>

      <div className="problem-context__section">
        <small>Difficulty</small>
        <strong>{problem?.difficulty ?? (loading ? "..." : "--")}</strong>
      </div>

      <div className="problem-context__section">
        <small>Tags</small>
        <div className="problem-context__tags">
          {problem?.tags?.length ? (
            problem.tags.map((tag) => <span key={tag}>{tag}</span>)
          ) : (
            <strong>{loading ? "..." : "--"}</strong>
          )}
        </div>
      </div>
    </section>
  );
}
