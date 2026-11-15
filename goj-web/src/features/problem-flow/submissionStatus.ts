export const activeSubmissionStatuses = new Set([
  "in queue",
  "wait",
  "compiling",
  "running",
]);

export const submissionStatusOptions = [
  "in queue",
  "wait",
  "compiling",
  "running",
  "Compile Error",
  "Wrong Answer",
  "Accepted",
  "Time Limit Exceeded",
  "Memory Limit Exceeded",
  "Runtime Error",
  "System Error",
];

export function isActiveSubmissionStatus(status?: string | null) {
  return Boolean(status && activeSubmissionStatuses.has(status.trim().toLowerCase()));
}

export function statusClassName(status?: string | null) {
  const normalized = status?.trim().toLowerCase() ?? "";
  if (normalized === "accepted") return "is-accepted";
  if (activeSubmissionStatuses.has(normalized)) return "is-running";
  if (!normalized) return "";
  return "is-failed";
}
