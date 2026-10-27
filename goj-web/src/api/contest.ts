import { apiRequest } from "./client";
import type { ContestListItem, ContestSubmissionRequest, PageResult, SubmissionResult,
  ContestSubmissionDetail, SubmissionStatus } from "../types/api";

/** 比赛详情与状态查询保持登录认证。 */
export function getContestSubmissionDetail(contestId: number, submissionId: number, token: string, signal?: AbortSignal) {
  return apiRequest<ContestSubmissionDetail>(`/contest/${contestId}/submission/${submissionId}`, {
    method: "GET", token, signal,
  });
}

export function getContestSubmissionStatus(contestId: number, submissionId: number, token: string, signal?: AbortSignal) {
  return apiRequest<SubmissionStatus>(`/contest/${contestId}/submission/${submissionId}/status`, {
    method: "GET", token, signal,
  });
}

/** 比赛提交重试时必须复用原 requestId 和请求内容。 */
export function submitContestCode(contestId: number, problemCode: string,
  payload: ContestSubmissionRequest, token: string) {
  return apiRequest<SubmissionResult>(`/contest/${contestId}/problem/${encodeURIComponent(problemCode)}`, {
    method: "POST",
    token,
    body: JSON.stringify(payload),
  });
}

function getContestPage(
  path: string,
  page: number,
  size: number,
  token: string | null,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  return apiRequest<PageResult<ContestListItem>>(`${path}?${params.toString()}`, {
    method: "GET",
    token,
    signal,
  });
}

export function getUnfinishedContests(
  page: number,
  size: number,
  token: string | null,
  signal?: AbortSignal,
) {
  return getContestPage("/contest/unfinish-list", page, size, token, signal);
}

export function getFinishedContests(
  page: number,
  size: number,
  token: string | null,
  signal?: AbortSignal,
) {
  return getContestPage("/contest/finish-list", page, size, token, signal);
}

export function registerContest(contestId: number, token: string) {
  return apiRequest<void>(`/contest/register-contest/${contestId}`, {
    method: "POST",
    token,
  });
}

export function cancelContestRegistration(contestId: number, token: string) {
  return apiRequest<void>(`/contest/logout-contest/${contestId}`, {
    method: "DELETE",
    token,
  });
}
