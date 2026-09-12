import { useEffect, useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../api/client";
import standingLeft from "../../assets/auth-art/standing-left.png";
import { useAuth } from "../../auth/AuthContext";
import { AuthShell } from "./AuthShell";
import { safeReturnPath, type AuthLocationState } from "./authNavigation";

export function LoginPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { status, login } = useAuth();
  const locationState = location.state as AuthLocationState | null;
  const [username, setUsername] = useState(locationState?.username ?? "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (locationState?.username) setUsername(locationState.username);
  }, [locationState?.username]);

  if (status === "authenticated") return <Navigate to="/" replace />;

  async function submitLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");

    const cleanUsername = username.trim();
    if (!cleanUsername || !password) {
      setError("账号和密码不能为空");
      return;
    }

    setPending(true);
    try {
      await login(cleanUsername, password);
      navigate(safeReturnPath(locationState?.from), { replace: true });
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "登录失败，请稍后重试");
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthShell pageLabel="login" pending={pending}>
      <img className="auth-form-guide" src={standingLeft} alt="等待出发的冒险者" />
      <form className="auth-form auth-form--login" onSubmit={submitLogin} noValidate>
        <label className="auth-field">
          <span>username</span>
          <input
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            disabled={pending || status === "loading"}
          />
        </label>

        <label className="auth-field">
          <span>password</span>
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={pending || status === "loading"}
          />
        </label>

        <div className="auth-feedback" role="alert" aria-live="polite">
          {error}
        </div>

        <button className="auth-submit" type="submit" disabled={pending || status === "loading"}>
          {pending ? "喵..." : "go!"}
        </button>

        <p className="auth-switch">
          还没有账号？
          <Link to="/register" state={{ from: locationState?.from }}>
            sign up
          </Link>
        </p>
      </form>
    </AuthShell>
  );
}
