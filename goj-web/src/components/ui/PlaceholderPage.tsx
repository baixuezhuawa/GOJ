import { AppShell } from "../layout/AppShell";

interface PlaceholderPageProps {
  title: string;
  message: string;
}

function PlaceholderSidebar() {
  return (
    <section className="sidebar-panel placeholder-sidebar">
      <span className="eyebrow">GOJ</span>
      <h2>Reserved</h2>
      <p>该区域将在对应页面需求确认后接入真实业务。</p>
    </section>
  );
}

export function PlaceholderPage({ title, message }: PlaceholderPageProps) {
  return (
    <AppShell sidebar={<PlaceholderSidebar />} sidebarLabel="页面信息">
      <section className="content-panel placeholder-page">
        <span className="eyebrow">Coming soon</span>
        <h1>{title}</h1>
        <p>{message}</p>
      </section>
    </AppShell>
  );
}
