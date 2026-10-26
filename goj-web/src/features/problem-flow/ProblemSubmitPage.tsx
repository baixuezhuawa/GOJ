import { useEffect, useRef, useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getSupportedLanguages } from "../../api/common";
import { useAuth } from "../../auth/AuthContext";
import { AppShell } from "../../components/layout/AppShell";
import type { SupportedLanguage } from "../../types/api";
import { CodeEditor } from "./CodeEditor";
import { ProblemRequestState } from "./ProblemRequestState";
import { ProblemSidebar } from "./ProblemSidebar";
import { ProblemTabs } from "./ProblemTabs";
import { parseProblemId } from "./problemRoute";
import { useProblemDetail } from "./useProblemDetail";
import { preparePendingSubmission, readPendingSubmission, sendPendingSubmission,
  type PendingSubmission } from "./pendingSubmission";

export function ProblemSubmitPage() {
  const { problemId: routeProblemId } = useParams();
  const problemId = parseProblemId(routeProblemId);
  const location = useLocation();
  const navigate = useNavigate();
  const { token, status, currentUser, clearSession } = useAuth();
  const { problem, loading, error, retry } = useProblemDetail(problemId);
  const [languages, setLanguages] = useState<SupportedLanguage[]>([]);
  const [language, setLanguage] = useState("");
  const [languageError, setLanguageError] = useState<string | null>(null);
  const [sourceCode, setSourceCode] = useState("");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pending, setPending] = useState<PendingSubmission | null>(null);
  const [storageError, setStorageError] = useState<string | null>(null);
  const sendingRef = useRef(false);
  const userId = currentUser?.userId;
  const latestRef = useRef({ userId, language, sourceCode, problemId });
  latestRef.current = { userId, language, sourceCode, problemId };

  useEffect(() => {
    setPending(null);
    setStorageError(null);
    if (!userId) return;
    try {
      setPending(readPendingSubmission(userId));
    } catch (error) {
      setStorageError(error instanceof Error ? error.message : "无法读取待确认提交");
    }
  }, [userId]);

  useEffect(() => {
    const controller = new AbortController();
    setLanguageError(null);
    getSupportedLanguages(controller.signal)
      .then((items) => {
        const enabled = items.filter((item) => item.enabled);
        setLanguages(enabled);
        setLanguage((current) => {
          if (current && enabled.some((item) => item.code === current)) return current;
          return "";
        });
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
        setLanguageError(requestError instanceof Error ? requestError.message : "语言列表加载失败");
      });
    return () => controller.abort();
  }, []);

  if (status === "guest") {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    if (!problemId || !token || !userId || sendingRef.current || storageError) return;
    if (!language) {
      setSubmitError("请选择代码语言");
      return;
    }
    if (!sourceCode.trim()) {
      setSubmitError("代码不能为空");
      return;
    }

    try {
      const snapshot = preparePendingSubmission(userId, {
        kind: "REGULAR", payload: { problemId, language, sourceCode },
      });
      setPending(snapshot);
      await send(snapshot, false);
    } catch (requestError) {
      setSubmitError(requestError instanceof Error ? requestError.message : "无法保存待确认提交");
    }
  }

  async function send(snapshot: PendingSubmission, confirming: boolean) {
    if (!token || !userId || sendingRef.current) return;
    // 同步标记拦住 React 尚未重绘时的连续点击。
    sendingRef.current = true;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const result = await sendPendingSubmission(userId, snapshot, token);
      if (latestRef.current.userId !== userId) return;
      setPending(readPendingSubmission(userId));
      if (confirming || latestRef.current.sourceCode !== snapshot.payload.sourceCode
        || latestRef.current.language !== snapshot.payload.language || latestRef.current.problemId !== problemId) {
        setSubmitError(`原提交已受理，提交编号：${result.submissionId}。当前草稿可以作为新提交发送。`);
      } else {
        navigate("/submissions", {
          replace: true,
          state: { highlightSubmissionId: result.submissionId },
        });
      }
    } catch (requestError) {
      if (latestRef.current.userId !== userId) return;
      if (requestError instanceof ApiError && requestError.status === 401) {
        clearSession();
        navigate("/login", { replace: true, state: { from: location.pathname } });
        return;
      }
      setSubmitError(requestError instanceof Error ? requestError.message : "提交失败，请稍后重试");
      try {
        setPending(readPendingSubmission(userId));
      } catch (error) {
        setStorageError(error instanceof Error ? error.message : "无法读取待确认提交");
      }
    } finally {
      sendingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <AppShell sidebar={<ProblemSidebar problem={problem} loading={loading} />} sidebarLabel="Problem info">
      {problemId ? <ProblemTabs problemId={problemId} /> : null}
      <section className="content-panel problem-submit-panel">
        {storageError && <div className="inline-error" role="alert">{storageError}</div>}
        {pending && (
          <div role="status">
            <p>有一份待确认的{pending.kind === "REGULAR" ? `题目 ${pending.payload.problemId}`
              : `比赛 ${pending.contestId} 的 ${pending.problemCode} 题`}提交。请先确认原提交，再发送当前草稿。</p>
            <button type="button" disabled={submitting || status !== "authenticated" || Boolean(storageError)}
              onClick={() => void send(pending, true)}>
              {submitting ? "正在确认…" : "确认原提交"}
            </button>
          </div>
        )}
        <div className="auth-feedback submission-feedback" role="alert" aria-live="polite">{submitError}</div>
        {loading || error || !problem ? (
          <ProblemRequestState loading={loading} error={error} onRetry={retry} />
        ) : (
          <form onSubmit={submit}>
            <header className="problem-submit-header">
              <h1>{problem.problemName}</h1>
              {problem.contestName?.trim() && (
                <p className="problem-submit-contest">{problem.contestName}</p>
              )}
            </header>

            <div className="code-toolbar">
              <label className="language-picker">
                <select
                  aria-label="Language"
                  value={language}
                  onChange={(event) => setLanguage(event.target.value)}
                  disabled={submitting || languages.length === 0}
                >
                  <option value="" disabled>Choose language</option>
                  {languages.map((item) => (
                    <option key={item.code} value={item.code}>{item.displayName}</option>
                  ))}
                </select>
                <span className="language-picker__icon" aria-hidden="true">≡</span>
              </label>
            </div>

            {languageError && <div className="inline-error" role="alert">{languageError}</div>}
            <CodeEditor
              value={sourceCode}
              language={language || "java11"}
              onChange={setSourceCode}
            />
            <div className="code-editor-footer">
              <span>{sourceCode.length} characters</span>
              <span>自定义样例运行暂不提供</span>
            </div>

            <button
              className="primary-button submission-submit-button"
              type="submit"
              disabled={submitting || status !== "authenticated" || Boolean(languageError) || Boolean(pending) || Boolean(storageError)}
            >
              {submitting ? "Submitting..." : "Submit"}
            </button>
          </form>
        )}
      </section>
    </AppShell>
  );
}
