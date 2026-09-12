import { apiRequest } from "./client";
import type { ContestListItem, PageResult } from "../types/api";

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
