import { apiRequest } from "./client";
import type {
  CurrentUser,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
} from "../types/api";

export function loginUser(payload: LoginRequest) {
  return apiRequest<LoginResponse>("/user/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function registerUser(payload: RegisterRequest) {
  return apiRequest<null>("/user/register", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function getCurrentUser(token: string, signal?: AbortSignal) {
  return apiRequest<CurrentUser>("/user/me", {
    method: "GET",
    token,
    signal,
  });
}

export function logoutCurrentUser(token: string) {
  return apiRequest<null>("/user/logout", {
    method: "POST",
    token,
  });
}
