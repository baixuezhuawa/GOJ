export interface AuthLocationState {
  from?: string;
  username?: string;
}

export function safeReturnPath(from?: string) {
  if (!from || !from.startsWith("/") || from.startsWith("//")) return "/";
  if (from.startsWith("/login") || from.startsWith("/register")) return "/";
  return from;
}
