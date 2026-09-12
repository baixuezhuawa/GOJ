import { apiRequest } from "./client";
import type { SupportedLanguage } from "../types/api";

export function getSupportedLanguages(signal?: AbortSignal) {
  return apiRequest<SupportedLanguage[]>("/common/supported-language", {
    method: "GET",
    signal,
  });
}
