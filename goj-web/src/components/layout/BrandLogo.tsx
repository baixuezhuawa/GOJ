import { Link } from "react-router-dom";
import loadingCat from "../../assets/loading-cat.png";

export function BrandLogo() {
  return (
    <Link className="brand-logo" to="/" aria-label="返回 GOJ 首页">
      <span className="brand-logo__mascot" aria-hidden="true">
        <img src={loadingCat} alt="" />
      </span>
      <span className="brand-logo__text">GOJ</span>
    </Link>
  );
}
