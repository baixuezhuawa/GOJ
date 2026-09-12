import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../api/client";
import { registerUser } from "../../api/user";
import { useAuth } from "../../auth/AuthContext";
import { AuthShell } from "./AuthShell";
import type { AuthLocationState } from "./authNavigation";

export function RegisterPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { status } = useAuth();
  const locationState = location.state as AuthLocationState | null;
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  if (status === "authenticated") return <Navigate to="/" replace />;

  async function submitRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");

    const cleanUsername = username.trim();
    if (!cleanUsername || !password || !confirmPassword) {
      setError("账号、密码和确认密码不能为空");
      return;
    }
    if (password !== confirmPassword) {
      setError("两次输入的密码不一致");
      return;
    }

    setPending(true);
    try {
      await registerUser({
        username: cleanUsername,
        password,
        confirmPassword,
        email: email.trim() || undefined,
      });
      navigate("/login", {
        replace: true,
        state: { from: locationState?.from, username: cleanUsername },
      });
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : "注册失败，请稍后重试");
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthShell pageLabel="sign up" pending={pending}>
      <form className="auth-form auth-form--register" onSubmit={submitRegister} noValidate>
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
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={pending || status === "loading"}
          />
        </label>

        <label className="auth-field">
          <span>confirm password</span>
          <input
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            disabled={pending || status === "loading"}
          />
        </label>

        <label className="auth-field">
          <span>email</span>
          <input
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            disabled={pending || status === "loading"}
          />
        </label>

        <div className="auth-field auth-field--code">
          <span>email code</span>
          <input disabled aria-label="邮箱验证码功能暂未开放" placeholder="暂不支持" />
          <button type="button" disabled title="邮箱验证码功能暂未开放">
            Send
          </button>
        </div>

        <div className="auth-feedback" role="alert" aria-live="polite">
          {error}
        </div>

        <button className="auth-submit" type="submit" disabled={pending || status === "loading"}>
          {pending ? "喵..." : "go!"}
        </button>

        <p className="auth-switch">
          已经有账号？
          <Link to="/login" state={{ from: locationState?.from }}>
            login
          </Link>
        </p>
      </form>
    </AuthShell>
  );
}
