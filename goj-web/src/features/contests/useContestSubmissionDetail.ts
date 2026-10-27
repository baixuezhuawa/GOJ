import { useEffect, useState } from "react";
import { getContestSubmissionDetail, getContestSubmissionStatus } from "../../api/contest";
import type { ContestSubmissionDetail } from "../../types/api";
import { isActiveSubmissionStatus } from "../problem-flow/submissionStatus";

/** 比赛详情首次完整加载，后续只轮询判题摘要。 */
export function useContestSubmissionDetail(contestId: number | null, submissionId: number | null, token: string | null) {
  const [submission, setSubmission] = useState<ContestSubmissionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    setSubmission(null);
    setError(null);
    if (contestId === null || submissionId === null || !token) {
      setLoading(false);
      if (token) setError("比赛或提交 ID 无效");
      return;
    }
    const validContestId = contestId;
    const validSubmissionId = submissionId;
    const validToken = token;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    setLoading(true);

    async function poll() {
      try {
        const result = await getContestSubmissionStatus(validContestId, validSubmissionId, validToken, controller.signal);
        if (controller.signal.aborted) return;
        setSubmission((current) => current ? { ...current, ...result } : current);
        if (isActiveSubmissionStatus(result.status)) timer = setTimeout(() => void poll(), 2500);
      } catch (requestError) {
        if (controller.signal.aborted) return;
        setError(requestError instanceof Error ? requestError.message : "判题状态加载失败");
      }
    }

    async function load() {
      try {
        const detail = await getContestSubmissionDetail(validContestId, validSubmissionId, validToken, controller.signal);
        if (controller.signal.aborted) return;
        setSubmission(detail);
        setLoading(false);
        if (isActiveSubmissionStatus(detail.status)) timer = setTimeout(() => void poll(), 2500);
      } catch (requestError) {
        if (controller.signal.aborted) return;
        setLoading(false);
        setError(requestError instanceof Error ? requestError.message : "比赛提交详情加载失败");
      }
    }

    void load();
    return () => {
      controller.abort();
      if (timer) clearTimeout(timer);
    };
  }, [contestId, submissionId, token, revision]);

  return { submission, loading, error, retry: () => setRevision((value) => value + 1) };
}
