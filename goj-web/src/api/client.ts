import type { ApiResult } from "../types/api";

export const TOKEN_STORAGE_KEY = "goj.token";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

export class ApiError extends Error {
  readonly status: number;
  readonly code?: number;

  constructor(message: string, status: number, code?: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

interface RequestOptions extends RequestInit {
  token?: string | null;
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { token, headers, ...requestInit } = options;
  const requestHeaders = new Headers(headers);

  if (requestInit.body && !requestHeaders.has("Content-Type")) {
    requestHeaders.set("Content-Type", "application/json");
  }
  if (token) {
    requestHeaders.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestInit,
    headers: requestHeaders,
  });

  const payload = (await response.json().catch(() => null)) as ApiResult<T> | null;
  if (!response.ok) {
    throw new ApiError(
      payload?.msg ?? `请求失败（${response.status}）`,
      response.status,
      payload?.code,
    );
  }
  if (!payload) {
    throw new ApiError("服务器返回了无法识别的数据", response.status);
  }
  if (payload.code !== 200) {
    throw new ApiError(payload.msg || "操作失败", response.status, payload.code);
  }

  return payload.data;
}
