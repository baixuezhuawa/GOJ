import { Link } from "react-router-dom";
import { CatLoader } from "../../components/ui/CatLoader";
import { EmptyState } from "../../components/ui/EmptyState";
import type { ProblemListItem } from "../../types/api";

interface ProblemTableProps {
  problems: ProblemListItem[];
  authenticated: boolean;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  onClearFilters: () => void;
}

function PaperPlaneIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 11.2 20.5 3l-6.8 18-3.2-7.1L3 11.2Zm7.8.7 2 4.5 3.6-9.5-5.6 5Z" />
    </svg>
  );
}

export function ProblemTable({
  problems,
  authenticated,
  loading,
  error,
  onRetry,
  onClearFilters,
}: ProblemTableProps) {
  const stateClass = (status: string) => {
    if (!authenticated) return "";
    if (status === "SOLVED") return "problem-row--solved";
    if (status === "ATTEMPTED") return "problem-row--attempted";
    return "";
  };

  return (
    <div className="problem-table-scroll">
      <table className="problem-table">
        <thead>
          <tr>
            <th className="sticky-column sticky-column--id">ID</th>
            <th className="sticky-column sticky-column--name">Problem</th>
            <th>Tags</th>
            <th className="icon-column" aria-label="功能占位" />
            <th className="icon-column" aria-label="收藏占位" />
            <th>Difficulty</th>
            <th>AC</th>
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td className="table-state" colSpan={7}>
                <CatLoader />
              </td>
            </tr>
          ) : error ? (
            <tr>
              <td className="table-state" colSpan={7}>
                <div className="request-state request-state--error" role="alert">
                  <span className="request-state__icon" aria-hidden="true">!</span>
                  <strong>题目列表加载失败</strong>
                  <p>{error}</p>
                  <button className="primary-button" type="button" onClick={onRetry}>
                    Retry
                  </button>
                </div>
              </td>
            </tr>
          ) : problems.length === 0 ? (
            <tr>
              <td className="table-state" colSpan={7}>
                <EmptyState
                  title="没有找到符合条件的题目"
                  description="可以清除筛选条件后重新查看题库。"
                >
                  <button className="secondary-button" type="button" onClick={onClearFilters}>
                    Clear filters
                  </button>
                </EmptyState>
              </td>
            </tr>
          ) : (
            problems.map((problem) => (
              <tr
                key={problem.problemId}
                className={stateClass(problem.status)}
              >
                <td className="sticky-column sticky-column--id">
                  <Link to={`/problems/${problem.problemId}`}>
                    {problem.problemId}
                  </Link>
                </td>
                <td className="sticky-column sticky-column--name">
                  <Link to={`/problems/${problem.problemId}`}>
                    {problem.problemName}
                  </Link>
                </td>
                <td>
                  <div className="problem-tags">
                    {problem.tags?.length > 0 ? (
                      problem.tags.map((tag) => <span key={tag}>{tag}</span>)
                    ) : (
                      <span className="problem-tags__empty">--</span>
                    )}
                  </div>
                </td>
                <td className="icon-column placeholder-icon" title="功能暂未开放">
                  <PaperPlaneIcon />
                </td>
                <td className="icon-column placeholder-icon placeholder-icon--heart" title="收藏暂未开放">
                  ♡
                </td>
                <td>
                  <span className="difficulty-value">
                    {problem.difficulty ?? "--"}
                  </span>
                </td>
                <td>
                  <span className="accepted-placeholder">--</span>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
