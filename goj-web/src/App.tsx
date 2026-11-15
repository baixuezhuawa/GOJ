import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { PlaceholderPage } from "./components/ui/PlaceholderPage";
import { CatLoader } from "./components/ui/CatLoader";
import { ProblemListPage } from "./features/problems/ProblemListPage";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./features/auth/LoginPage";
import { RegisterPage } from "./features/auth/RegisterPage";

const ProblemDetailPage = lazy(() =>
  import("./features/problem-flow/ProblemDetailPage").then((module) => ({
    default: module.ProblemDetailPage,
  })),
);
const ProblemRankPage = lazy(() =>
  import("./features/problem-flow/ProblemRankPage").then((module) => ({ default: module.ProblemRankPage })),
);
const ProblemSubmitPage = lazy(() =>
  import("./features/problem-flow/ProblemSubmitPage").then((module) => ({
    default: module.ProblemSubmitPage,
  })),
);
const SubmissionDetailPage = lazy(() =>
  import("./features/problem-flow/SubmissionDetailPage").then((module) => ({
    default: module.SubmissionDetailPage,
  })),
);
const SubmissionListPage = lazy(() =>
  import("./features/problem-flow/SubmissionListPage").then((module) => ({
    default: module.SubmissionListPage,
  })),
);
const ContestListPage = lazy(() =>
  import("./features/contests/ContestListPage").then((module) => ({
    default: module.ContestListPage,
  })),
);
const ProfilePage = lazy(() =>
  import("./features/profile/ProfilePage").then((module) => ({
    default: module.ProfilePage,
  })),
);

export default function App() {
  return (
    <Suspense
      fallback={
        <div className="route-loading">
          <CatLoader label="页面正在展开..." />
        </div>
      }
    >
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/problems" element={<ProblemListPage />} />
        <Route path="/problems/:problemId" element={<ProblemDetailPage />} />
        <Route path="/problems/:problemId/submit" element={<ProblemSubmitPage />} />
        <Route
          path="/problems/:problemId/submissions"
          element={<SubmissionListPage />}
        />
        <Route path="/problems/:problemId/rank" element={<ProblemRankPage />} />
        <Route path="/submissions" element={<SubmissionListPage />} />
        <Route path="/submissions/:submissionId" element={<SubmissionDetailPage />} />
        <Route path="/contests" element={<ContestListPage />} />
        <Route
          path="/contests/:contestId"
          element={
            <PlaceholderPage
              title="Contest detail"
              message="比赛详情页将在下一轮需求确认后接入真实接口。"
            />
          }
        />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/me" element={<ProfilePage />} />
        <Route
          path="/users/:username"
          element={
            <PlaceholderPage
              title="User profile"
              message="他人主页当前仅保留静态路由占位。"
            />
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
