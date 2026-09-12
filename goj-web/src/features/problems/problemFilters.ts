import type { ProblemProgressStatus, ProblemQuery } from "../../types/api";

export interface ProblemFilterDraft {
  keyword: string;
  difficultyMin: string;
  difficultyMax: string;
  tagIds: number[];
  solveStatus: "" | ProblemProgressStatus;
}

export const emptyProblemFilters: ProblemFilterDraft = {
  keyword: "",
  difficultyMin: "",
  difficultyMax: "",
  tagIds: [],
  solveStatus: "",
};

function parseOptionalNumber(value: string) {
  if (!value.trim()) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function toProblemQuery(
  filters: ProblemFilterDraft,
  page: number,
  authenticated: boolean,
): ProblemQuery {
  return {
    page,
    size: 30,
    keyword: filters.keyword.trim() || undefined,
    difficultyMin: parseOptionalNumber(filters.difficultyMin),
    difficultyMax: parseOptionalNumber(filters.difficultyMax),
    tagIds: filters.tagIds.length > 0 ? filters.tagIds : undefined,
    solveStatus: authenticated && filters.solveStatus
      ? filters.solveStatus
      : undefined,
  };
}
