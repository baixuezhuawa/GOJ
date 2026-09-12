import { useEffect, useState } from "react";
import { getSubmissionDetail } from "../../api/submission";
import type { SubmissionDetail } from "../../types/api";
import { isActiveSubmissionStatus } from "./submissionStatus";

const pollingIntervalMs = 2500;

export function useSubmissionDetail(submissionId: number | null) {
  const [submission, setSubmission] = useState<SubmissionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    if (submissionId === null) {
      setSubmission(null);
      setLoading(false);
      setError("提交 ID 无效");
      return;
    }

    const validSubmissionId = submissionId;

    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;

    setSubmission(null);
    setLoading(true);
    setError(null);

    async function load() {
      try {
        const detail = await getSubmissionDetail(validSubmissionId, controller.signal);
        if (controller.signal.aborted) return;
        setSubmission(detail);
        setLoading(false);
        if (isActiveSubmissionStatus(detail.status)) {
          timer = setTimeout(() => void load(), pollingIntervalMs);
        }
      } catch (requestError) {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
        setLoading(false);
        setError(requestError instanceof Error ? requestError.message : "提交详情加载失败");
      }
    }

    void load();
    return () => {
      controller.abort();
      if (timer) clearTimeout(timer);
    };
  }, [revision, submissionId]);

  return {
    submission,
    loading,
    error,
    retry: () => setRevision((value) => value + 1),
  };
}
