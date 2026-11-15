export interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}

export interface PageResult<T> {
  current: number;
  size: number;
  total: number;
  pages: number;
  records: T[];
}

export interface CurrentUser {
  userId: number;
  username: string;
  permissions: string[];
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  confirmPassword: string;
  email?: string;
}

export interface SupportedLanguage {
  code: string;
  enabled: boolean;
  displayName: string;
}

export interface ProblemDetail {
  id: number;
  problemName: string;
  timeLimit: number | null;
  memoryLimit: number | null;
  description: string | null;
  inputDescription: string | null;
  outPutDescription: string | null;
  inputExample: string | null;
  outPutExample: string | null;
  exampleNote: string | null;
  difficulty: number | null;
  authorId: number | null;
  authorName: string | null;
  contestName?: string | null;
  tags: string[];
  status: number | null;
}

export interface SubmissionRequest {
  problemId: number;
  language: string;
  sourceCode: string;
}

export interface SubmissionResult {
  submissionType: string;
  submissionId: number;
}

export interface SubmissionListItem {
  id: number;
  username: string;
  problemName: string;
  problemId: number;
  language: string;
  timeMs: number | null;
  memoryKb: number | null;
  status: string;
  submissionTime: string | null;
}

export interface SubmissionSearchQuery {
  page: number;
  size: number;
  problemId?: number;
  language?: string;
  status?: string;
}

export interface SubmissionDetail {
  id: number;
  username: string;
  language: string;
  problemName: string;
  problemId: number;
  sourceCode: string;
  status: string;
  timeMs: number | null;
  memoryKb: number | null;
  submissionTime: string | null;
  judgeStartTime: string | null;
  judgeEndTime: string | null;
  judgeMsg: string | null;
  compilerMsg: string | null;
}

export type ProblemProgressStatus =
  | "UNATTEMPTED"
  | "ATTEMPTED"
  | "SOLVED";

export interface ProblemListItem {
  problemId: number;
  problemName: string;
  difficulty: number | null;
  tags: string[];
  status: ProblemProgressStatus | string;
}

export interface ProblemTag {
  tagId: number;
  tagName: string;
}

export interface ProblemQuery {
  page: number;
  size: number;
  keyword?: string;
  difficultyMin?: number;
  difficultyMax?: number;
  tagIds?: number[];
  solveStatus?: ProblemProgressStatus;
}

export interface ContestListItem {
  contestId: number;
  title: string;
  registerStartTime: string;
  registerEndTime: string;
  startTime: string;
  endTime: string;
  participateNumber: number;
  isRegister: boolean;
}

export interface UserProfile {
  email: string | null;
  gender: number | null;
  phoneNumber: string | null;
  avatar: string | null;
  birthdate: string | null;
}

export interface ProfileStatistics {
  solveNumber: number;
  unSolveNumber: number;
  submitCount: number;
  solveLastMonth: number;
  solveLastYear: number;
  longestConsecutiveDays: number;
}

export interface SubmissionStatusStat {
  status: string;
  submissionCount: number;
}

export interface LanguageStat {
  language: string;
  displayName: string | null;
  submissionCount: number;
  acceptedCount: number;
}
