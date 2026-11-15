import { Link } from "react-router-dom";
import { AppShell } from "../components/layout/AppShell";

function RatingPlaceholder() {
  return (
    <section className="sidebar-panel rating-placeholder">
      <span className="eyebrow">Community</span>
      <h2>Rating</h2>
      <ol>
        {["--", "--", "--"].map((value, index) => (
          <li key={index}>
            <span>{index + 1}</span>
            <i />
            <strong>{value}</strong>
          </li>
        ))}
      </ol>
      <p>排行榜将在后续阶段接入。</p>
    </section>
  );
}

export function HomePage() {
  return (
    <AppShell sidebar={<RatingPlaceholder />} sidebarLabel="Rating">
      <section className="content-panel home-placeholder">
        <div className="home-placeholder__copy">
          <span className="eyebrow">Welcome to GOJ</span>
          <h1>把每一次提交，变成下一步。</h1>
          <p>首页业务将在后续需求阶段继续完善。本阶段可以从题库开始体验真实接口。</p>
          <Link className="primary-button" to="/problems">
            Browse problems
          </Link>
        </div>
        <div className="home-placeholder__map" aria-hidden="true">
          <span className="map-river" />
          <span className="map-path map-path--one" />
          <span className="map-path map-path--two" />
          <i className="map-tree map-tree--one" />
          <i className="map-tree map-tree--two" />
          <i className="map-tree map-tree--three" />
        </div>
      </section>
    </AppShell>
  );
}
