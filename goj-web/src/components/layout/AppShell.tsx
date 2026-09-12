import { useEffect, useState, type ReactNode } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { BrandLogo } from "./BrandLogo";

interface AppShellProps {
  children: ReactNode;
  sidebar: ReactNode;
  sidebarLabel?: string;
}

const navigation = [
  { label: "Home", to: "/" },
  { label: "Problem", to: "/problems" },
  { label: "Contest", to: "/contests" },
];

export function AppShell({
  children,
  sidebar,
  sidebarLabel = "侧边栏",
}: AppShellProps) {
  const { currentUser, status, logout } = useAuth();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  useEffect(() => {
    if (!sidebarOpen) return;
    const closeWithEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setSidebarOpen(false);
    };
    document.addEventListener("keydown", closeWithEscape);
    return () => document.removeEventListener("keydown", closeWithEscape);
  }, [sidebarOpen]);

  return (
    <div className="app-shell">
      <header className="global-header">
        <div className="identity-row">
          <BrandLogo />

          <div className="account-area">
            {status === "loading" ? (
              <span className="account-area__loading" aria-label="正在恢复登录状态" />
            ) : currentUser ? (
              <>
                <button
                  className="icon-placeholder"
                  type="button"
                  disabled
                  aria-label="通知功能暂未开放"
                  title="通知功能暂未开放"
                >
                  🔔
                </button>
                <Link className="header-button" to="/me">
                  {currentUser.username}
                </Link>
                <button className="header-button" type="button" onClick={() => void logout()}>
                  Logout
                </button>
              </>
            ) : (
              <>
                <Link
                  className="header-button"
                  to="/login"
                  state={{ from: `${location.pathname}${location.search}` }}
                >
                  Login
                </Link>
                <Link
                  className="header-button"
                  to="/register"
                  state={{ from: `${location.pathname}${location.search}` }}
                >
                  Register
                </Link>
              </>
            )}
          </div>
        </div>

        <div className="navigation-row">
          <button
            className="navigation-toggle"
            type="button"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <span aria-hidden="true">☰</span>
            Menu
          </button>

          <nav className={`primary-navigation ${menuOpen ? "is-open" : ""}`}>
            {navigation.map((item) => (
              <NavLink
                key={item.to}
                className={({ isActive }) =>
                  `navigation-item ${isActive ? "is-active" : ""}`
                }
                to={item.to}
                onClick={() => setMenuOpen(false)}
              >
                {item.label}
              </NavLink>
            ))}
            <button className="navigation-item is-disabled" type="button" disabled>
              Rank
            </button>
            <button className="navigation-item is-disabled" type="button" disabled>
              Team
            </button>
          </nav>

          <span className="navigation-cloud" aria-hidden="true" />
        </div>
      </header>

      <div className="page-layout">
        <main className="main-content">
          <button
            className="mobile-sidebar-trigger"
            type="button"
            onClick={() => setSidebarOpen(true)}
          >
            <span aria-hidden="true">☷</span>
            {sidebarLabel}
          </button>
          {children}
        </main>

        <aside className="right-sidebar" aria-label={sidebarLabel}>
          {sidebar}
        </aside>
      </div>

      <div
        className={`sidebar-backdrop ${sidebarOpen ? "is-open" : ""}`}
        onClick={() => setSidebarOpen(false)}
        aria-hidden="true"
      />
      <aside
        className={`sidebar-drawer ${sidebarOpen ? "is-open" : ""}`}
        aria-label={`${sidebarLabel}抽屉`}
        aria-hidden={!sidebarOpen}
      >
        <div className="sidebar-drawer__header">
          <strong>{sidebarLabel}</strong>
          <button type="button" onClick={() => setSidebarOpen(false)}>
            关闭
          </button>
        </div>
        {sidebar}
      </aside>
    </div>
  );
}
