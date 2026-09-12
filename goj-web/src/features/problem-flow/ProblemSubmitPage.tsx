import { useEffect, useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getSupportedLanguages } from "../../api/common";
import { submitCode } from "../../api/submission";
import { useAuth } from "../../auth/AuthContext";
import { AppShell } from "../../components/layout/AppShell";
import type { SupportedLanguage } from "../../types/api";
import { CodeEditor } from "./CodeEditor";
import { ProblemRequestState } from "./ProblemRequestState";
import { ProblemSidebar } from "./ProblemSidebar";
import { ProblemTabs } from "./ProblemTabs";
import { parseProblemId } from "./problemRoute";
import { useProblemDetail } from "./useProblemDetail";

export function ProblemSubmitPage() {
  const { problemId: routeProblemId } = useParams();
  const problemId = parseProblemId(routeProblemId);
  const location = useLocation();
  const navigate = useNavigate();
  const { token, status, clearSession } = useAuth();
  const { problem, loading, error, retry } = useProblemDetail(problemId);
  const [languages, setLanguages] = useState<SupportedLanguage[]>([]);
  const [language, setLanguage] = useState("");
  const [languageError, setLanguageError] = useState<string | null>(null);
  const [sourceCode, setSourceCode] = useState("");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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
    if (!problemId || !token) return;
    if (!language) {
      setSubmitError("请选择代码语言");
      return;
    }
    if (!sourceCode.trim()) {
      setSubmitError("代码不能为空");
      return;
    }

    setSubmitting(true);
    try {
      const result = await submitCode({ problemId, language, sourceCode }, token);
      navigate("/submissions", {
        replace: true,
        state: { highlightSubmissionId: result.submissionId },
      });
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        clearSession();
        navigate("/login", { replace: true, state: { from: location.pathname } });
        return;
      }
      setSubmitError(requestError instanceof Error ? requestError.message : "提交失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AppShell sidebar={<ProblemSidebar problem={problem} loading={loading} />} sidebarLabel="Problem info">
      {problemId ? <ProblemTabs problemId={problemId} /> : null}
      <section className="content-panel problem-submit-panel">
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

            <div className="auth-feedback submission-feedback" role="alert" aria-live="polite">
              {submitError}
            </div>
            <button
              className="primary-button submission-submit-button"
              type="submit"
              disabled={submitting || status === "loading" || Boolean(languageError)}
            >
              {submitting ? "Submitting..." : "Submit"}
            </button>
          </form>
        )}
      </section>
    </AppShell>
  );
}
