import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../api/client";
import {
  getAttemptedUnsolvedProblems,
  getLanguageStats,
  getMyProfile,
  getProfileStatistics,
  getRecentSubmissions,
  getSolvedProblems,
  getSubmissionStatusStats,
  updateMyProfile,
} from "../../api/profile";
import { useAuth } from "../../auth/AuthContext";
import { AppShell } from "../../components/layout/AppShell";
import { CatLoader } from "../../components/ui/CatLoader";
import { EmptyState } from "../../components/ui/EmptyState";
import type {
  LanguageStat,
  PageResult,
  ProblemListItem,
  ProfileStatistics,
  SubmissionListItem,
  SubmissionStatusStat,
  UserProfile,
} from "../../types/api";

const emptyProfile: UserProfile = {
  email: null,
  gender: null,
  phoneNumber: null,
  avatar: null,
  birthdate: null,
};

const emptyStatistics: ProfileStatistics = {
  solveNumber: 0,
  unSolveNumber: 0,
  submitCount: 0,
  solveLastMonth: 0,
  solveLastYear: 0,
  longestConsecutiveDays: 0,
};

const emptyRecent: PageResult<SubmissionListItem> = {
  current: 1,
  size: 7,
  total: 0,
  pages: 0,
  records: [],
};

interface ProfileDraft {
  email: string;
  gender: string;
  phoneNumber: string;
  avatar: string;
  birthdate: string;
}

interface ProfileData {
  profile: UserProfile;
  statistics: ProfileStatistics;
  submissionStatus: SubmissionStatusStat[];
  languageStats: LanguageStat[];
  recentSubmissions: PageResult<SubmissionListItem>;
}

const heatmapPattern = [
  [0, 1, 0, 2, 1, 0, 0, 3, 2, 1, 0, 0, 1, 2, 3, 1, 0, 0, 2, 1, 0, 0, 1, 3, 2, 0],
  [1, 2, 1, 0, 0, 1, 3, 2, 1, 0, 0, 2, 1, 3, 2, 1, 0, 1, 2, 3, 1, 0, 0, 2, 1, 0],
  [0, 0, 1, 2, 3, 1, 0, 1, 2, 0, 1, 2, 3, 1, 0, 0, 1, 2, 1, 0, 2, 3, 1, 0, 0, 1],
  [2, 1, 0, 0, 1, 3, 2, 1, 0, 1, 2, 3, 1, 0, 1, 2, 3, 2, 1, 0, 0, 1, 2, 3, 1, 0],
  [0, 1, 2, 1, 0, 0, 2, 3, 1, 0, 1, 2, 0, 1, 2, 1, 0, 0, 3, 2, 1, 0, 1, 2, 3, 1],
  [1, 0, 0, 1, 2, 3, 1, 0, 0, 1, 2, 1, 0, 2, 3, 1, 0, 1, 2, 0, 1, 3, 2, 1, 0, 0],
  [0, 2, 3, 1, 0, 1, 2, 0, 1, 3, 2, 1, 0, 0, 1, 2, 3, 1, 0, 0, 2, 1, 0, 1, 2, 3],
];

function toDraft(profile: UserProfile): ProfileDraft {
  return {
    email: profile.email ?? "",
    gender: profile.gender == null ? "" : String(profile.gender),
    phoneNumber: profile.phoneNumber ?? "",
    avatar: profile.avatar ?? "",
    birthdate: profile.birthdate?.slice(0, 10) ?? "",
  };
}

function toProfilePayload(draft: ProfileDraft): UserProfile {
  return {
    email: draft.email.trim() || null,
    gender: draft.gender.trim() ? Number(draft.gender) : null,
    phoneNumber: draft.phoneNumber.trim() || null,
    avatar: draft.avatar.trim() || null,
    birthdate: draft.birthdate ? `${draft.birthdate}T00:00:00` : null,
  };
}

function displayStatus(status: string) {
  return status.replaceAll("_", " ");
}

function displayDate(value: string | null) {
  return value?.replace("T", " ") || "";
}

function ProfileSidebar({
  submissionStatus,
  languageStats,
}: Pick<ProfileData, "submissionStatus" | "languageStats">) {
  const maxStatusCount = Math.max(
    1,
    ...submissionStatus.map((item) => item.submissionCount),
  );

  return (
    <div className="profile-sidebar-stack">
      <section className="sidebar-panel profile-stats-sidebar">
        <div className="filter-panel__heading">
          <span className="eyebrow">Activity</span>
          <h2>Submission status</h2>
        </div>
        {submissionStatus.length === 0 ? (
          <EmptyState title="暂无提交统计" />
        ) : (
          <div className="profile-status-list">
            {submissionStatus.map((item) => (
              <div className="profile-status-row" key={item.status}>
                <span>{displayStatus(item.status)}</span>
                <div className="profile-status-row__bar">
                  <i style={{ width: `${(item.submissionCount / maxStatusCount) * 100}%` }} />
                </div>
                <strong>{item.submissionCount}</strong>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="sidebar-panel profile-language-sidebar">
        <div className="filter-panel__heading">
          <span className="eyebrow">Code practice</span>
          <h2>Languages</h2>
        </div>
        {languageStats.length === 0 ? (
          <EmptyState title="暂无语言统计" />
        ) : (
          <div className="profile-language-list">
            {languageStats.map((item) => (
              <div className="profile-language-row" key={item.language}>
                <div>
                  <strong>{item.displayName || item.language}</strong>
                  <small>{item.language}</small>
                </div>
                <span>{item.acceptedCount} / {item.submissionCount} AC</span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function ProfileIdentityCard({
  username,
  profile,
  statistics,
  editing,
  draft,
  saving,
  saveError,
  onDraftChange,
  onStartEditing,
  onCancelEditing,
  onSubmit,
}: {
  username: string;
  profile: UserProfile;
  statistics: ProfileStatistics;
  editing: boolean;
  draft: ProfileDraft;
  saving: boolean;
  saveError: string | null;
  onDraftChange: (draft: ProfileDraft) => void;
  onStartEditing: () => void;
  onCancelEditing: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const updateDraft = <K extends keyof ProfileDraft>(field: K, value: ProfileDraft[K]) => {
    onDraftChange({ ...draft, [field]: value });
  };

  return (
    <section className={`profile-identity-card ${profile.avatar ? "has-avatar" : ""}`}>
      {profile.avatar && !editing && (
        <div className="profile-avatar-wrap">
          <img className="profile-avatar" src={profile.avatar} alt={`${username} 的头像`} />
        </div>
      )}

      <div className="profile-identity-main">
        <span className="eyebrow">Profile</span>
        <h1>{username}</h1>
        <div className="profile-summary-grid">
          <div><small>Rating</small><strong>--</strong></div>
          <Link to="/problems?solveStatus=SOLVED"><small>Solved</small><strong>{statistics.solveNumber}</strong></Link>
          <Link to="/problems?solveStatus=ATTEMPTED"><small>Unsolved</small><strong>{statistics.unSolveNumber}</strong></Link>
          <Link to="/submissions"><small>Submissions</small><strong>{statistics.submitCount}</strong></Link>
        </div>
      </div>

      <div className="profile-description-card">
        <span className="eyebrow">Description</span>
        <p>个人描述暂未提供。</p>
      </div>

      {!editing ? (
        <div className="profile-details-list">
          {profile.email && <div><small>Email</small><strong>{profile.email}</strong></div>}
          {profile.phoneNumber && <div><small>Phone</small><strong>{profile.phoneNumber}</strong></div>}
          {profile.gender != null && <div><small>Gender</small><strong>{profile.gender}</strong></div>}
          {profile.birthdate && <div><small>Birthday</small><strong>{displayDate(profile.birthdate)}</strong></div>}
        </div>
      ) : (
        <form className="profile-edit-form" onSubmit={onSubmit}>
          <label><span>Email</span><input type="email" value={draft.email} onChange={(event) => updateDraft("email", event.target.value)} /></label>
          <label><span>Phone</span><input value={draft.phoneNumber} onChange={(event) => updateDraft("phoneNumber", event.target.value)} /></label>
          <label><span>Gender</span><input inputMode="numeric" value={draft.gender} onChange={(event) => updateDraft("gender", event.target.value)} /></label>
          <label><span>Birthday</span><input type="date" value={draft.birthdate} onChange={(event) => updateDraft("birthdate", event.target.value)} /></label>
          <label className="profile-edit-form__wide"><span>Avatar URL</span><input value={draft.avatar} onChange={(event) => updateDraft("avatar", event.target.value)} /></label>
          <p className="profile-edit-form__hint">暂不支持头像文件上传；空值不会清除已有后端字段。</p>
          <div className="profile-edit-form__feedback" role="alert">{saveError}</div>
          <div className="profile-edit-form__actions">
            <button className="secondary-button" type="button" disabled={saving} onClick={onCancelEditing}>Cancel</button>
            <button className="primary-button" type="submit" disabled={saving}>{saving ? "Saving..." : "Save"}</button>
          </div>
        </form>
      )}

      {!editing && (
        <button className="secondary-button profile-settings-button" type="button" onClick={onStartEditing}>
          Settings
        </button>
      )}
    </section>
  );
}

function StaticHeatmap() {
  return (
    <section className="profile-panel profile-heatmap-panel">
      <div className="profile-panel__header">
        <div>
          <span className="eyebrow">Practice rhythm</span>
          <h2>Problem-solving heat map</h2>
        </div>
        <span className="profile-placeholder-label">静态演示</span>
      </div>
      <div className="profile-heatmap" aria-label="静态热力图占位">
        {heatmapPattern.flatMap((row, rowIndex) => row.map((level, columnIndex) => (
          <i key={`${rowIndex}-${columnIndex}`} className={`heat-level-${level}`} />
        )))}
      </div>
      <p className="profile-panel__note">热力图接口暂未提供，当前仅保留静态布局演示。</p>
    </section>
  );
}

function RecentSubmissions({ recentSubmissions }: { recentSubmissions: PageResult<SubmissionListItem> }) {
  return (
    <section className="profile-panel profile-recent-panel">
      <div className="profile-panel__header">
        <div>
          <span className="eyebrow">Latest activity</span>
          <h2>Recent submissions</h2>
        </div>
        <Link to="/submissions">View all</Link>
      </div>
      {recentSubmissions.records.length === 0 ? (
        <EmptyState title="暂无最近提交" />
      ) : (
        <div className="profile-recent-list">
          {recentSubmissions.records.map((submission) => (
            <Link className="profile-recent-row" to={`/submissions/${submission.id}`} key={submission.id}>
              <span>#{submission.id}</span>
              <strong>{submission.problemName}</strong>
              <code>{submission.language}</code>
              <span>{submission.status}</span>
              <small>{submission.submissionTime || "--"}</small>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}

function ProfileStatsStrip({ statistics }: { statistics: ProfileStatistics }) {
  return (
    <section className="profile-stats-strip" aria-label="做题统计">
      <div><small>Last month solved</small><strong>{statistics.solveLastMonth}</strong></div>
      <div><small>Last year solved</small><strong>{statistics.solveLastYear}</strong></div>
      <div><small>Consecutive days</small><strong>{statistics.longestConsecutiveDays}</strong></div>
    </section>
  );
}

export function ProfilePage() {
  const { token, status, currentUser, clearSession } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [data, setData] = useState<ProfileData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<ProfileDraft>(toDraft(emptyProfile));
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    if (status !== "authenticated" || !token) return;
    const controller = new AbortController();
    setLoading(true);
    setError(null);

    Promise.all([
      getMyProfile(token, controller.signal),
      getProfileStatistics(token, controller.signal),
      getSubmissionStatusStats(token, controller.signal),
      getLanguageStats(token, controller.signal),
      getRecentSubmissions(token, controller.signal),
    ])
      .then(([profile, statistics, submissionStatus, languageStats, recentSubmissions]) => {
        setData({ profile, statistics, submissionStatus, languageStats, recentSubmissions });
        setDraft(toDraft(profile));
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
        if (requestError instanceof ApiError && requestError.status === 401) {
          clearSession();
          return;
        }
        setError(requestError instanceof Error ? requestError.message : "个人主页加载失败");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [clearSession, status, token]);

  const sidebar = data ? (
    <ProfileSidebar submissionStatus={data.submissionStatus} languageStats={data.languageStats} />
  ) : (
    <section className="sidebar-panel profile-sidebar-loading"><CatLoader label="正在整理统计..." /></section>
  );

  if (status === "guest") {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  async function submitProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || !data) return;
    setSaving(true);
    setSaveError(null);
    try {
      const payload = toProfilePayload(draft);
      await updateMyProfile(payload, token);
      const profile = await getMyProfile(token);
      setData((current) => current ? { ...current, profile } : current);
      setDraft(toDraft(profile));
      setEditing(false);
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        clearSession();
        navigate("/login", { replace: true, state: { from: location.pathname } });
        return;
      }
      setSaveError(requestError instanceof Error ? requestError.message : "个人信息保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <AppShell sidebar={sidebar} sidebarLabel="Profile statistics">
      <section className="content-panel profile-page-panel">
        {loading ? (
          <CatLoader label="正在打开个人主页..." />
        ) : error || !data || !currentUser ? (
          <div className="request-state request-state--error" role="alert">
            <span className="request-state__icon">!</span>
            <strong>个人主页加载失败</strong>
            <p>{error || "暂时无法读取个人信息"}</p>
          </div>
        ) : (
          <>
            <ProfileIdentityCard
              username={currentUser.username}
              profile={data.profile}
              statistics={data.statistics}
              editing={editing}
              draft={draft}
              saving={saving}
              saveError={saveError}
              onDraftChange={setDraft}
              onStartEditing={() => { setSaveError(null); setEditing(true); }}
              onCancelEditing={() => { setSaveError(null); setDraft(toDraft(data.profile)); setEditing(false); }}
              onSubmit={submitProfile}
            />
            <ProfileStatsStrip statistics={data.statistics} />
            <StaticHeatmap />
            <RecentSubmissions recentSubmissions={data.recentSubmissions} />
          </>
        )}
      </section>
    </AppShell>
  );
}
