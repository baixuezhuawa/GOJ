import loadingCat from "../../assets/loading-cat.png";

interface CatLoaderProps {
  label?: string;
}

export function CatLoader({ label = "正在寻找题目…" }: CatLoaderProps) {
  return (
    <div className="cat-loader" role="status" aria-live="polite">
      <div className="cat-loader__stage" aria-hidden="true">
        <img className="cat-loader__image" src={loadingCat} alt="" />
        <span className="cat-loader__mouth" />
        <span className="cat-loader__voice cat-loader__voice--one">喵</span>
        <span className="cat-loader__voice cat-loader__voice--two">喵</span>
        <span className="cat-loader__voice cat-loader__voice--three">喵</span>
      </div>
      <span className="cat-loader__label">{label}</span>
    </div>
  );
}
