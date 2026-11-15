import { useEffect, useMemo, useState } from "react";
import { Link, Navigate, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getSupportedLanguages } from "../../api/common";
import { getSubmissionHistory } from "../../api/submission";
import { useAuth } from "../../auth/AuthContext";
import { AppShell } from "../../components/layout/AppShell";
import { CatLoader } from "../../components/ui/CatLoader";
import { CompactPagination } from "../../components/ui/CompactPagination";
import { EmptyState } from "../../components/ui/EmptyState";
import type { PageResult, SubmissionListItem, SupportedLanguage } from "../../types/api";
import { ProblemTabs } from "./ProblemTabs";
import { parseProblemId, parseSubmissionId } from "./problemRoute";
import {
  SubmissionFilterPanel,
  type SubmissionFilterDraft,
} from "./SubmissionFilterPanel";
import { statusClassName } from "./submissionStatus";
import { SubmissionDetailModal } from "./SubmissionDetailModal";

const emptyPage: PageResult<SubmissionListItem> = {
  current: 1,
  size: 30,
  total: 0,
  pages: 0,
  records: [],
};

const emptyFilters: SubmissionFilterDraft = { problemId: "", language: "", status: "" };

interface SubmissionLocationState {
  highlightSubmissionId?: number;
}

export function SubmissionListPage() {
  const { problemId: routeProblemId } = useParams();
  const fixedProblemId = routeProblemId ? parseProblemId(routeProblemId) : undefined;
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const modalSubmissionId = parseSubmissionId(searchParams.get("submission") ?? undefined);
  const locationState = location.state as SubmissionLocationState | null;
  const { token, status, clearSession } = useAuth();
  const [draft, setDraft] = useState<SubmissionFilterDraft>(emptyFilters);
  const [applied, setApplied] = useState<SubmissionFilterDraft>(emptyFilters);
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<PageResult<SubmissionListItem>>(emptyPage);
  const [languages, setLanguages] = useState<SupportedLanguage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getSupportedLanguages(controller.signal)
      .then((items) => setLanguages(items.filter((item) => item.enabled)))
      .catch(() => setLanguages([]));
    return () => controller.abort();
  }, []);

  const query = useMemo(() => {
    const parsedProblemId = fixedProblemId ?? (
      applied.problemId && Number.isSafeInteger(Number(applied.problemId))
        ? Number(applied.problemId)
        : undefined
    );
    return {
      page,
      size: 30,
      problemId: parsedProblemId ?? undefined,
      language: applied.language || undefined,
      status: applied.status || undefined,
    };
  }, [applied, fixedProblemId, page]);

  useEffect(() => {
    if (!token || status !== "authenticated") return;
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    getSubmissionHistory(query, token, controller.signal)
      .then(setResult)
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
        if (requestError instanceof ApiError && requestError.status === 401) {
          clearSession();
          return;
        }
        setError(requestError instanceof Error ? requestError.message : "提交列表加载失败");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [clearSession, query, revision, status, token]);

  if (routeProblemId && fixedProblemId === null) {
    return <Navigate to="/problems" replace />;
  }

  if (status === "guest") {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }

  const openSubmissionModal = (submissionId: number) => {
    const nextSearchParams = new URLSearchParams(location.search);
    nextSearchParams.set("submission", String(submissionId));
    navigate(
      { pathname: location.pathname, search: `?${nextSearchParams.toString()}` },
      { state: { ...location.state, submissionModal: true } },
    );
  };

  const closeSubmissionModal = () => {
    if (location.state && typeof location.state === "object" && "submissionModal" in location.state) {
      navigate(-1);
      return;
    }
    const nextSearchParams = new URLSearchParams(location.search);
    nextSearchParams.delete("submission");
    navigate(
      { pathname: location.pathname, search: nextSearchParams.toString() ? `?${nextSearchParams}` : "" },
      { replace: true },
    );
  };

  const sidebar = (
    <SubmissionFilterPanel
      filters={draft}
      languages={languages}
      fixedProblemId={fixedProblemId ?? undefined}
      loading={loading}
      onChange={setDraft}
      onApply={() => {
        setApplied({ ...draft });
        setPage(1);
        setRevision((value) => value + 1);
      }}
      onClear={() => {
        setDraft(emptyFilters);
        setApplied(emptyFilters);
        setPage(1);
        setRevision((value) => value + 1);
      }}
    />
  );

  return (
    <AppShell sidebar={sidebar} sidebarLabel="Submission filter">
      {fixedProblemId ? <ProblemTabs problemId={fixedProblemId} /> : null}
      <section className="content-panel submission-list-panel">
        <header className="submission-list-header">
          <div>
            <span className="eyebrow">Online judge</span>
            <h1>{fixedProblemId ? `Problem #${fixedProblemId} status` : "My submissions"}</h1>
          </div>
          <span>{error ? "数据暂不可用" : `${result.total} submissions`}</span>
        </header>

        <div className="submission-table-scroll">
          <table className="submission-table">
            <thead>
              <tr>
                <th>Submission ID</th>
                <th>Submit time</th>
                <th>Author</th>
                <th>Problem</th>
                <th>Language</th>
                <th>Status</th>
                <th>Time</th>
                <th>Memory</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td className="table-state" colSpan={8}><CatLoader label="正在翻找提交记录..." /></td></tr>
              ) : error ? (
                <tr>
                  <td className="table-state" colSpan={8}>
                    <div className="request-state request-state--error" role="alert">
                      <span className="request-state__icon">!</span>
                      <strong>提交列表加载失败</strong>
                      <p>{error}</p>
                      <button className="primary-button" type="button" onClick={() => setRevision((value) => value + 1)}>Retry</button>
                    </div>
                  </td>
                </tr>
              ) : result.records.length === 0 ? (
                <tr>
                  <td className="table-state" colSpan={8}>
                    <EmptyState
                      title="还没有符合条件的提交"
                      description="提交一份代码后，记录会出现在这里。"
                    />
                  </td>
                </tr>
              ) : (
                result.records.map((submission) => (
                  <tr
                    key={submission.id}
                    className={locationState?.highlightSubmissionId === submission.id ? "is-highlighted" : ""}
                  >
                    <td>
                      <button
                        className="submission-id-button"
                        type="button"
                        onClick={() => openSubmissionModal(submission.id)}
                      >
                        {submission.id}
                      </button>
                    </td>
                    <td>{submission.submissionTime ?? "--"}</td>
                    <td>{submission.username || "--"}</td>
                    <td>
                      <Link to={`/problems/${submission.problemId}`}>
                        #{submission.problemId} {submission.problemName}
                      </Link>
                    </td>
                    <td><code>{submission.language || "--"}</code></td>
                    <td><span className={`submission-status ${statusClassName(submission.status)}`}>{submission.status || "--"}</span></td>
                    <td>{submission.timeMs == null ? "--" : `${submission.timeMs} ms`}</td>
                    <td>{submission.memoryKb == null ? "--" : `${submission.memoryKb} KB`}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <footer className="submission-list-footer">
          <span>Page {result.current || page}</span>
          <CompactPagination
            current={result.current || page}
            pages={result.pages}
            disabled={loading}
            onChange={setPage}
          />
        </footer>
      </section>
      {modalSubmissionId !== null && (
        <SubmissionDetailModal
          submissionId={modalSubmissionId}
          onClose={closeSubmissionModal}
        />
      )}
    </AppShell>
  );
}
