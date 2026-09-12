import { apiRequest } from "./client";
import type {
  LanguageStat,
  PageResult,
  ProblemListItem,
  ProfileStatistics,
  SubmissionListItem,
  SubmissionStatusStat,
  UserProfile,
} from "../types/api";

export function getMyProfile(token: string, signal?: AbortSignal) {
  return apiRequest<UserProfile>("/me/profile", { method: "GET", token, signal });
}

export function updateMyProfile(profile: UserProfile, token: string) {
  return apiRequest<void>("/me/profile", {
    method: "PUT",
    token,
    body: JSON.stringify(profile),
  });
}

export function getProfileStatistics(token: string, signal?: AbortSignal) {
  return apiRequest<ProfileStatistics>("/me/profile-statistics", {
    method: "GET",
    token,
    signal,
  });
}

export function getSubmissionStatusStats(token: string, signal?: AbortSignal) {
  return apiRequest<SubmissionStatusStat[]>("/me/submission-status-stat", {
    method: "GET",
    token,
    signal,
  });
}

export function getLanguageStats(token: string, signal?: AbortSignal) {
  return apiRequest<LanguageStat[]>("/me/language-stat", {
    method: "GET",
    token,
    signal,
  });
}

export function getRecentSubmissions(token: string, signal?: AbortSignal) {
  return apiRequest<PageResult<SubmissionListItem>>("/me/submission-recent", {
    method: "GET",
    token,
    signal,
  });
}

function getProblemProgressList(path: string, token: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: "1", size: "30" });
  return apiRequest<PageResult<ProblemListItem>>(`${path}?${params.toString()}`, {
    method: "GET",
    token,
    signal,
  });
}

export function getSolvedProblems(token: string, signal?: AbortSignal) {
  return getProblemProgressList("/me/solve-problem", token, signal);
}

export function getAttemptedUnsolvedProblems(token: string, signal?: AbortSignal) {
  return getProblemProgressList("/me/attempted-problem", token, signal);
}
