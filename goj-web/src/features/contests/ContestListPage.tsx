import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  cancelContestRegistration,
  getFinishedContests,
  getUnfinishedContests,
  registerContest,
} from "../../api/contest";
import { ApiError } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";
import { AppShell } from "../../components/layout/AppShell";
import { CatLoader } from "../../components/ui/CatLoader";
import { CompactPagination } from "../../components/ui/CompactPagination";
import { EmptyState } from "../../components/ui/EmptyState";
import type { ContestListItem, PageResult } from "../../types/api";
import { ContestFilterPanel } from "./ContestFilterPanel";

const pageSize = 30;

const emptyContestPage: PageResult<ContestListItem> = {
  current: 1,
  size: pageSize,
  total: 0,
  pages: 0,
  records: [],
};

type ContestGroup = "unfinished" | "finished";

interface ContestPageState {
  result: PageResult<ContestListItem>;
  loading: boolean;
  error: string | null;
}

interface ContestSectionProps {
  group: ContestGroup;
  title: string;
  state: ContestPageState;
  page: number;
  now: number;
  actionContestId: number | null;
  onPageChange: (page: number) => void;
  onRetry: () => void;
  onRegistrationAction: (contest: ContestListItem) => void;
}

function parseDate(value: string) {
  return new Date(value.replace(" ", "T"));
}

function formatDate(value: string) {
  return value?.replace("T", " ") || "--";
}

function formatDuration(startTime: string, endTime: string) {
  const milliseconds = parseDate(endTime).getTime() - parseDate(startTime).getTime();
  if (!Number.isFinite(milliseconds) || milliseconds <= 0) return "--";

  const totalMinutes = Math.round(milliseconds / 60_000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

function formatBeforeStart(startTime: string, now: number) {
  const milliseconds = parseDate(startTime).getTime() - now;
  if (!Number.isFinite(milliseconds)) return "--";
  if (milliseconds <= 0) return "Running";

  const totalMinutes = Math.ceil(milliseconds / 60_000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  if (days > 0) return `${days}d ${hours}h before start`;
  if (hours > 0) return `${hours}h ${minutes}m before start`;
  return `${minutes}m before start`;
}

function registrationState(contest: ContestListItem, now: number) {
  const registerStart = parseDate(contest.registerStartTime).getTime();
  const registerEnd = parseDate(contest.registerEndTime).getTime();
  const open = Number.isFinite(registerStart)
    && Number.isFinite(registerEnd)
    && now >= registerStart
    && now <= registerEnd;

  if (contest.isRegister) {
    return { open, label: open ? "Cancel" : "Registered" };
  }
  if (now < registerStart) return { open: false, label: "Not open" };
  if (now > registerEnd) return { open: false, label: "Closed" };
  return { open, label: "Register" };
}

function ContestRow({
  contest,
  group,
  now,
  actionContestId,
  onRegistrationAction,
}: {
  contest: ContestListItem;
  group: ContestGroup;
  now: number;
  actionContestId: number | null;
  onRegistrationAction: (contest: ContestListItem) => void;
}) {
  const registration = registrationState(contest, now);
  const started = parseDate(contest.startTime).getTime() <= now;
  const actionLoading = actionContestId === contest.contestId;

  return (
    <article className="contest-row">
      <div className="contest-row__identity">
        <span className="contest-row__id">#{contest.contestId}</span>
        <h3>{contest.title}</h3>
      </div>

      <dl className="contest-row__facts">
        <div>
          <dt>Type</dt>
          <dd>--</dd>
        </div>
        <div>
          <dt>Author</dt>
          <dd>--</dd>
        </div>
        <div>
          <dt>Length</dt>
          <dd>{formatDuration(contest.startTime, contest.endTime)}</dd>
        </div>
        <div>
          <dt>Start time</dt>
          <dd>{formatDate(contest.startTime)}</dd>
        </div>
        <div>
          <dt>Participants</dt>
          <dd>{contest.participateNumber ?? 0}</dd>
        </div>
        <div>
          <dt>Problems</dt>
          <dd>--</dd>
        </div>
        <div>
          <dt>{group === "finished" ? "Solved" : "Status"}</dt>
          <dd>
            {group === "finished"
              ? "--"
              : formatBeforeStart(contest.startTime, now)}
          </dd>
        </div>
        {group === "finished" && (
          <div>
            <dt>End time</dt>
            <dd>{formatDate(contest.endTime)}</dd>
          </div>
        )}
      </dl>

      <div className="contest-row__actions">
        {group === "unfinished" ? (
          <>
            {started && (
              <Link className="secondary-button contest-enter-button" to={`/contests/${contest.contestId}`}>
                Enter
              </Link>
            )}
            <button
              className={contest.isRegister ? "secondary-button" : "primary-button"}
              type="button"
              disabled={!registration.open || actionLoading}
              onClick={() => onRegistrationAction(contest)}
            >
              {actionLoading ? "Working..." : registration.label}
            </button>
          </>
        ) : (
          <Link className="secondary-button contest-enter-button" to={`/contests/${contest.contestId}`}>
            Enter
          </Link>
        )}
      </div>
    </article>
  );
}

function ContestSection({
  group,
  title,
  state,
  page,
  now,
  actionContestId,
  onPageChange,
  onRetry,
  onRegistrationAction,
}: ContestSectionProps) {
  return (
    <section className="contest-section" aria-labelledby={`contest-${group}-title`}>
      <div className="contest-section__heading">
        <h2 id={`contest-${group}-title`}>{title}</h2>
        <span>{state.error ? "数据暂不可用" : `${state.result.total} contests`}</span>
      </div>

      <div className="contest-section__body">
        {state.loading ? (
          <CatLoader label={group === "unfinished" ? "正在寻找即将开始的比赛..." : "正在翻阅历史比赛..."} />
        ) : state.error ? (
          <div className="request-state request-state--error" role="alert">
            <span className="request-state__icon">!</span>
            <strong>比赛列表加载失败</strong>
            <p>{state.error}</p>
            <button className="primary-button" type="button" onClick={onRetry}>Retry</button>
          </div>
        ) : state.result.records.length === 0 ? (
          <EmptyState
            title={group === "unfinished" ? "暂时没有即将开始的比赛" : "还没有已结束的比赛"}
          />
        ) : (
          <div className="contest-rows">
            {state.result.records.map((contest) => (
              <ContestRow
                key={contest.contestId}
                contest={contest}
                group={group}
                now={now}
                actionContestId={actionContestId}
                onRegistrationAction={onRegistrationAction}
              />
            ))}
          </div>
        )}
      </div>

      <footer className="contest-section__footer">
        <span>Page {state.result.current || page}</span>
        <CompactPagination
          current={state.result.current || page}
          pages={state.result.pages}
          disabled={state.loading}
          ariaLabel={`${title}比赛分页`}
          onChange={onPageChange}
        />
      </footer>
    </section>
  );
}

export function ContestListPage() {
  const { token, status, clearSession } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [now, setNow] = useState(() => Date.now());
  const [unfinishedPage, setUnfinishedPage] = useState(1);
  const [finishedPage, setFinishedPage] = useState(1);
  const [unfinishedRevision, setUnfinishedRevision] = useState(0);
  const [finishedRevision, setFinishedRevision] = useState(0);
  const [unfinished, setUnfinished] = useState<ContestPageState>({
    result: emptyContestPage,
    loading: true,
    error: null,
  });
  const [finished, setFinished] = useState<ContestPageState>({
    result: emptyContestPage,
    loading: true,
    error: null,
  });
  const [actionContestId, setActionContestId] = useState<number | null>(null);
  const [actionFeedback, setActionFeedback] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setUnfinished((current) => ({ ...current, loading: true, error: null }));
    getUnfinishedContests(unfinishedPage, pageSize, token, controller.signal)
      .then((result) => setUnfinished({ result, loading: false, error: null }))
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
        if (requestError instanceof ApiError && requestError.status === 401) {
          clearSession();
          return;
        }
        setUnfinished((current) => ({
          ...current,
          loading: false,
          error: requestError instanceof Error ? requestError.message : "未结束比赛加载失败",
        }));
      });
    return () => controller.abort();
  }, [clearSession, token, unfinishedPage, unfinishedRevision]);

  useEffect(() => {
    const controller = new AbortController();
    setFinished((current) => ({ ...current, loading: true, error: null }));
    getFinishedContests(finishedPage, pageSize, token, controller.signal)
      .then((result) => setFinished({ result, loading: false, error: null }))
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
        if (requestError instanceof ApiError && requestError.status === 401) {
          clearSession();
          return;
        }
        setFinished((current) => ({
          ...current,
          loading: false,
          error: requestError instanceof Error ? requestError.message : "已结束比赛加载失败",
        }));
      });
    return () => controller.abort();
  }, [clearSession, finishedPage, finishedRevision, token]);

  const totalContests = useMemo(
    () => unfinished.result.total + finished.result.total,
    [finished.result.total, unfinished.result.total],
  );

  async function handleRegistrationAction(contest: ContestListItem) {
    if (status !== "authenticated" || !token) {
      navigate("/login", {
        state: { from: `${location.pathname}${location.search}` },
      });
      return;
    }

    setActionContestId(contest.contestId);
    setActionFeedback(null);
    try {
      if (contest.isRegister) {
        await cancelContestRegistration(contest.contestId, token);
        setActionFeedback(`已取消报名：${contest.title}`);
      } else {
        await registerContest(contest.contestId, token);
        setActionFeedback(`报名成功：${contest.title}`);
      }
      setUnfinishedRevision((revision) => revision + 1);
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        clearSession();
        navigate("/login", {
          replace: true,
          state: { from: `${location.pathname}${location.search}` },
        });
        return;
      }
      setActionFeedback(requestError instanceof Error ? requestError.message : "报名操作失败");
    } finally {
      setActionContestId(null);
    }
  }

  return (
    <AppShell
      sidebar={<ContestFilterPanel />}
      sidebarLabel="Filter"
    >
      <section className="content-panel contest-list-panel">
        <header className="contest-list-panel__header">
          <div>
            <span className="eyebrow">Online judge</span>
            <h1>Contest</h1>
          </div>
          <div className="contest-list-panel__summary">
            <span>{totalContests} contests</span>
            <span className="list-mark" aria-hidden="true"><i /><i /><i /></span>
          </div>
        </header>

        {actionFeedback && (
          <div className="contest-action-feedback" role="status">
            <span>{actionFeedback}</span>
            <button type="button" onClick={() => setActionFeedback(null)} aria-label="关闭提示">×</button>
          </div>
        )}

        <div className="contest-list-panel__sections">
          <ContestSection
            group="unfinished"
            title="Will start"
            state={unfinished}
            page={unfinishedPage}
            now={now}
            actionContestId={actionContestId}
            onPageChange={setUnfinishedPage}
            onRetry={() => setUnfinishedRevision((revision) => revision + 1)}
            onRegistrationAction={(contest) => void handleRegistrationAction(contest)}
          />
          <ContestSection
            group="finished"
            title="Past"
            state={finished}
            page={finishedPage}
            now={now}
            actionContestId={actionContestId}
            onPageChange={setFinishedPage}
            onRetry={() => setFinishedRevision((revision) => revision + 1)}
            onRegistrationAction={(contest) => void handleRegistrationAction(contest)}
          />
        </div>
      </section>
    </AppShell>
  );
}
