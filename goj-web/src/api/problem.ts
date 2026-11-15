import { apiRequest } from "./client";
import type {
  PageResult,
  ProblemDetail,
  ProblemListItem,
  ProblemQuery,
  ProblemTag,
} from "../types/api";

export function getProblemDetail(problemId: number, signal?: AbortSignal) {
  return apiRequest<ProblemDetail>(`/problem/${problemId}`, {
    method: "GET",
    signal,
  });
}

export function getProblemList(
  query: ProblemQuery,
  token: string | null,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });

  if (query.keyword) params.set("keyword", query.keyword);
  if (query.difficultyMin !== undefined) {
    params.set("difficultyMin", String(query.difficultyMin));
  }
  if (query.difficultyMax !== undefined) {
    params.set("difficultyMax", String(query.difficultyMax));
  }
  query.tagIds?.forEach((tagId) => params.append("tagId", String(tagId)));
  if (query.solveStatus) params.set("solveStatus", query.solveStatus);

  return apiRequest<PageResult<ProblemListItem>>(
    `/problem/list?${params.toString()}`,
    {
      method: "GET",
      token,
      signal,
    },
  );
}

export function getProblemTags(signal?: AbortSignal) {
  return apiRequest<ProblemTag[]>("/common/problem-all-tags", {
    method: "GET",
    signal,
  });
}
