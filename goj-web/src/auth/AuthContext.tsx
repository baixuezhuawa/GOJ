import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
} from "react";
import { ApiError, TOKEN_STORAGE_KEY } from "../api/client";
import { getCurrentUser, loginUser, logoutCurrentUser } from "../api/user";
import type { CurrentUser } from "../types/api";

type AuthStatus = "loading" | "guest" | "authenticated";

interface AuthContextValue {
  token: string | null;
  currentUser: CurrentUser | null;
  status: AuthStatus;
  login: (username: string, password: string) => Promise<void>;
  clearSession: () => void;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const verifiedTokenRef = useRef<string | null>(null);
  const [token, setToken] = useState<string | null>(() =>
    localStorage.getItem(TOKEN_STORAGE_KEY),
  );
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [status, setStatus] = useState<AuthStatus>(token ? "loading" : "guest");

  const clearSession = useCallback(() => {
    verifiedTokenRef.current = null;
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setToken(null);
    setCurrentUser(null);
    setStatus("guest");
  }, []);

  useEffect(() => {
    if (!token) {
      setCurrentUser(null);
      setStatus("guest");
      return;
    }
    if (verifiedTokenRef.current === token) {
      verifiedTokenRef.current = null;
      return;
    }

    const controller = new AbortController();
    setStatus("loading");
    getCurrentUser(token, controller.signal)
      .then((user) => {
        setCurrentUser(user);
        setStatus("authenticated");
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        if (error instanceof ApiError && error.status === 401) {
          clearSession();
          return;
        }
        setCurrentUser(null);
        setStatus("guest");
      });

    return () => controller.abort();
  }, [clearSession, token]);

  const login = useCallback(async (username: string, password: string) => {
    const result = await loginUser({ username, password });
    const user = await getCurrentUser(result.token);

    verifiedTokenRef.current = result.token;
    localStorage.setItem(TOKEN_STORAGE_KEY, result.token);
    setToken(result.token);
    setCurrentUser(user);
    setStatus("authenticated");
  }, []);

  const logout = useCallback(async () => {
    if (token) {
      try {
        await logoutCurrentUser(token);
      } finally {
        clearSession();
      }
      return;
    }
    clearSession();
  }, [clearSession, token]);

  const value = useMemo(
    () => ({ token, currentUser, status, login, clearSession, logout }),
    [clearSession, currentUser, login, logout, status, token],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth 必须在 AuthProvider 内使用");
  }
  return context;
}
