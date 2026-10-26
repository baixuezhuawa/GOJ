import { ApiError } from "../../api/client";
import { submitContestCode } from "../../api/contest";
import { submitCode } from "../../api/submission";
import type { ContestSubmissionRequest, SubmissionRequest } from "../../types/api";

export type SubmissionDraft =
  | { kind: "REGULAR"; payload: Omit<SubmissionRequest, "requestId"> }
  | { kind: "CONTEST"; contestId: number; problemCode: string; payload: Omit<ContestSubmissionRequest, "requestId"> };

export type PendingSubmission =
  | { kind: "REGULAR"; payload: SubmissionRequest }
  | { kind: "CONTEST"; contestId: number; problemCode: string; payload: ContestSubmissionRequest };

const storageKey = (userId: number) => `goj.pending-submission.${userId}`;
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** 读取当前用户的待确认快照；记录损坏时阻止覆盖，避免静默生成新请求。 */
export function readPendingSubmission(userId: number): PendingSubmission | null {
  const raw = sessionStorage.getItem(storageKey(userId));
  if (!raw) return null;
  try {
    const value = JSON.parse(raw);
    const payload = value?.payload;
    if (typeof payload?.requestId !== "string" || !uuidPattern.test(payload.requestId)
      || typeof payload.language !== "string" || typeof payload.sourceCode !== "string") {
      throw new Error();
    }
    if (value.kind === "REGULAR" && Number.isSafeInteger(payload.problemId) && payload.problemId > 0) return value;
    if (value.kind === "CONTEST" && Number.isSafeInteger(value.contestId) && value.contestId > 0
      && typeof value.problemCode === "string" && value.problemCode.length > 0) return value;
  } catch {
    throw new Error("待确认提交记录无法读取，请先在提交记录中确认上次提交情况");
  }
  throw new Error("待确认提交记录格式不正确，请先确认上次提交情况");
}

/** 一次只保留一个待确认提交，先持久化成功，再允许发出请求。 */
export function preparePendingSubmission(userId: number, draft: SubmissionDraft): PendingSubmission {
  const existing = readPendingSubmission(userId);
  if (existing) return existing;
  const requestId = crypto.randomUUID();
  const pending: PendingSubmission = draft.kind === "REGULAR"
    ? { kind: "REGULAR", payload: { ...draft.payload, requestId } }
    : { kind: "CONTEST", contestId: draft.contestId, problemCode: draft.problemCode,
      payload: { ...draft.payload, requestId } };
  sessionStorage.setItem(storageKey(userId), JSON.stringify(pending));
  return pending;
}

/** 迟到响应只能清除自己的 UUID，不能影响后续请求。 */
export function clearPendingSubmission(userId: number, requestId: string) {
  if (readPendingSubmission(userId)?.payload.requestId === requestId) {
    sessionStorage.removeItem(storageKey(userId));
  }
}

/** 发送原快照；只有成功或明确业务拒绝才结束尝试，其他错误保留原 UUID。 */
export async function sendPendingSubmission(userId: number, pending: PendingSubmission, token: string) {
  let result;
  try {
    result = pending.kind === "REGULAR"
      ? await submitCode(pending.payload, token)
      : await submitContestCode(pending.contestId, pending.problemCode, pending.payload, token);
  } catch (error) {
    if (error instanceof ApiError && error.status === 422 && error.code === 422) {
      clearPendingSubmission(userId, pending.payload.requestId);
    }
    throw error;
  }
  // 成功包也必须含有受理凭据，不能因空响应而丢失待确认记录。
  if (!result || !Number.isSafeInteger(result.submissionId) || result.submissionId <= 0
    || !["REGULAR", "CONTEST"].includes(result.submissionType)) {
    throw new Error("未收到有效的提交受理结果，请重试确认原提交");
  }
  clearPendingSubmission(userId, pending.payload.requestId);
  return result;
}
