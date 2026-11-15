import { useEffect, type ReactNode } from "react";
import { Link } from "react-router-dom";
import fire from "../../assets/auth-art/fire.png";
import makingPoint from "../../assets/auth-art/making-a-point.png";
import personLaptop from "../../assets/auth-art/person-laptop.png";
import shark from "../../assets/auth-art/shark.png";
import superhero from "../../assets/auth-art/superhero.png";
import wizard from "../../assets/auth-art/wizard.png";
import loadingCat from "../../assets/loading-cat.png";

interface AuthShellProps {
  pageLabel: "login" | "sign up";
  pending?: boolean;
  children: ReactNode;
}

const journeyCharacters = [
  { src: superhero, alt: "披着斗篷的冒险者" },
  { src: shark, alt: "背着长剑的冒险者" },
  { src: wizard, alt: "拿着法杖的魔法师" },
  { src: makingPoint, alt: "正在指路的冒险者" },
  { src: personLaptop, alt: "正在使用电脑的冒险者" },
];

export function AuthShell({ pageLabel, pending = false, children }: AuthShellProps) {
  useEffect(() => {
    document.body.classList.add("auth-page-active");
    return () => document.body.classList.remove("auth-page-active");
  }, []);

  return (
    <div className="auth-viewport">
      <div className="auth-canvas">
        <header className="auth-journey" aria-label="GOJ 冒险者横幅">
          <Link className="auth-brand" to="/" aria-label="返回 GOJ 首页">
            <img src={fire} alt="" />
            <strong>GOJ</strong>
          </Link>

          <div className="auth-journey__characters">
            {journeyCharacters.map((character) => (
              <img key={character.src} src={character.src} alt={character.alt} />
            ))}
          </div>

          <span className="auth-user-mark" aria-hidden="true">
            <i />
          </span>
        </header>

        <main className="auth-card">
          <span className="auth-card__label">{pageLabel}</span>
          <section className="auth-form-area">{children}</section>
          <aside className={`auth-mascot ${pending ? "is-pending" : ""}`} aria-hidden="true">
            <img src={loadingCat} alt="" />
            {pending && <span>喵...</span>}
          </aside>
        </main>
      </div>
    </div>
  );
}
