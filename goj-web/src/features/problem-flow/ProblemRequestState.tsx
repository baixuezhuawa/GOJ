import { CatLoader } from "../../components/ui/CatLoader";

interface ProblemRequestStateProps {
  loading: boolean;
  error: string | null;
  onRetry: () => void;
}

export function ProblemRequestState({ loading, error, onRetry }: ProblemRequestStateProps) {
  if (loading) return <CatLoader label="题面正在赶来的路上..." />;
  if (!error) return null;
  return (
    <div className="request-state request-state--error" role="alert">
      <span className="request-state__icon" aria-hidden="true">!</span>
      <strong>题目加载失败</strong>
      <p>{error}</p>
      <button className="primary-button" type="button" onClick={onRetry}>
        Retry
      </button>
    </div>
  );
}
