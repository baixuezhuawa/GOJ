import { useCallback, useEffect, useMemo, useState } from "react";
import { AppShell } from "../../components/layout/AppShell";
import { CompactPagination } from "../../components/ui/CompactPagination";
import { ApiError } from "../../api/client";
import { getProblemList, getProblemTags } from "../../api/problem";
import { useAuth } from "../../auth/AuthContext";
import type { PageResult, ProblemListItem, ProblemTag } from "../../types/api";
import { ProblemFilterPanel } from "./ProblemFilterPanel";
import { ProblemTable } from "./ProblemTable";
import { useSearchParams } from "react-router-dom";
import {
  emptyProblemFilters,
  toProblemQuery,
  type ProblemFilterDraft,
} from "./problemFilters";

const emptyPage: PageResult<ProblemListItem> = {
  current: 1,
  size: 30,
  total: 0,
  pages: 0,
  records: [],
};

export function ProblemListPage() {
  const { token, currentUser, clearSession } = useAuth();
  const authenticated = currentUser !== null;
  const [searchParams] = useSearchParams();
  const initialSolveStatus = searchParams.get("solveStatus");
  const initialProgress = initialSolveStatus === "SOLVED" || initialSolveStatus === "ATTEMPTED"
    ? initialSolveStatus
    : "";
  const [draftFilters, setDraftFilters] = useState<ProblemFilterDraft>(() => ({
    ...emptyProblemFilters,
    solveStatus: initialProgress,
  }));
  const [appliedFilters, setAppliedFilters] = useState<ProblemFilterDraft>(() => ({
    ...emptyProblemFilters,
    solveStatus: initialProgress,
  }));
  const [page, setPage] = useState(1);
  const [requestRevision, setRequestRevision] = useState(0);
  const [result, setResult] = useState<PageResult<ProblemListItem>>(emptyPage);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [tags, setTags] = useState<ProblemTag[]>([]);
  const [tagsLoading, setTagsLoading] = useState(true);
  const [tagsError, setTagsError] = useState<string | null>(null);
  const [tagsRevision, setTagsRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setTagsLoading(true);
    setTagsError(null);
    getProblemTags(controller.signal)
      .then(setTags)
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
        setTagsError(
          requestError instanceof Error ? requestError.message : "标签加载失败",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setTagsLoading(false);
      });
    return () => controller.abort();
  }, [tagsRevision]);

  useEffect(() => {
    if (authenticated || !appliedFilters.solveStatus) return;
    setAppliedFilters((filters) => ({ ...filters, solveStatus: "" }));
    setDraftFilters((filters) => ({ ...filters, solveStatus: "" }));
    setPage(1);
  }, [appliedFilters.solveStatus, authenticated]);

  const query = useMemo(
    () => toProblemQuery(appliedFilters, page, authenticated),
    [appliedFilters, authenticated, page],
  );

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);

    getProblemList(query, token, controller.signal)
      .then(setResult)
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
        if (requestError instanceof ApiError && requestError.status === 401) {
          clearSession();
          return;
        }
        setError(
          requestError instanceof Error ? requestError.message : "题目列表加载失败",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [clearSession, query, requestRevision, token]);

  const applyFilters = useCallback(() => {
    setAppliedFilters({ ...draftFilters, tagIds: [...draftFilters.tagIds] });
    setPage(1);
    setRequestRevision((revision) => revision + 1);
  }, [draftFilters]);

  const clearFilters = useCallback(() => {
    setDraftFilters(emptyProblemFilters);
    setAppliedFilters(emptyProblemFilters);
    setPage(1);
    setRequestRevision((revision) => revision + 1);
  }, []);

  const sidebar = (
    <ProblemFilterPanel
      filters={draftFilters}
      tags={tags}
      tagsLoading={tagsLoading}
      tagsError={tagsError}
      authenticated={authenticated}
      requestLoading={loading}
      onChange={setDraftFilters}
      onApply={applyFilters}
      onClear={clearFilters}
      onRetryTags={() => setTagsRevision((revision) => revision + 1)}
    />
  );

  return (
    <AppShell
      sidebar={sidebar}
      sidebarLabel="Filter"
    >
      <section className="content-panel problem-list-panel">
        <header className="problem-list-panel__header">
          <h1>Problem</h1>
          <span className="list-mark" aria-hidden="true">
            <i />
            <i />
            <i />
          </span>
        </header>

        <ProblemTable
          problems={result.records}
          authenticated={authenticated}
          loading={loading}
          error={error}
          onRetry={() => setRequestRevision((revision) => revision + 1)}
          onClearFilters={clearFilters}
        />

        <footer className="problem-list-panel__footer">
          <span>{error ? "数据暂不可用" : `共 ${result.total} 道题`}</span>
          <CompactPagination
            current={result.current || page}
            pages={result.pages}
            disabled={loading}
            onChange={setPage}
          />
        </footer>
      </section>
    </AppShell>
  );
}
