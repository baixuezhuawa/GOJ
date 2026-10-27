import { apiRequest } from "./client";
import type {
  PageResult,
  SubmissionDetail,
  SubmissionStatus,
  SubmissionListItem,
  SubmissionRequest,
  SubmissionResult,
  SubmissionSearchQuery,
} from "../types/api";

export function submitCode(payload: SubmissionRequest, token: string) {
  return apiRequest<SubmissionResult>("/submission/submit", {
    method: "POST",
    token,
    body: JSON.stringify(payload),
  });
}

export function getSubmissionHistory(
  query: SubmissionSearchQuery,
  token: string,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  if (query.problemId !== undefined) params.set("problemId", String(query.problemId));
  if (query.language) params.set("language", query.language);
  if (query.status) params.set("status", query.status);

  return apiRequest<PageResult<SubmissionListItem>>(
    `/me/submission-history?${params.toString()}`,
    { method: "GET", token, signal },
  );
}

export function getSubmissionDetail(submissionId: number, signal?: AbortSignal) {
  return apiRequest<SubmissionDetail>(`/submission/${submissionId}`, {
    method: "GET",
    signal,
  });
}

/** 轮询只请求结果摘要，不重复加载源码和题目信息。 */
export function getSubmissionStatus(submissionId: number, signal?: AbortSignal) {
  return apiRequest<SubmissionStatus>(`/submission/${submissionId}/status`, {
    method: "GET", signal,
  });
}
