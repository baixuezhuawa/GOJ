import { useEffect, useState } from "react";
import { getProblemDetail } from "../../api/problem";
import type { ProblemDetail } from "../../types/api";

export function useProblemDetail(problemId: number | null) {
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    if (problemId === null) {
      setProblem(null);
      setLoading(false);
      setError("题目 ID 无效");
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setError(null);
    getProblemDetail(problemId, controller.signal)
      .then(setProblem)
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
        setError(requestError instanceof Error ? requestError.message : "题目加载失败");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [problemId, revision]);

  return {
    problem,
    loading,
    error,
    retry: () => setRevision((value) => value + 1),
  };
}
