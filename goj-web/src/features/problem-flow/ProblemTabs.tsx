import { Link, NavLink, useLocation } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

interface ProblemTabsProps {
  problemId: number;
}

export function ProblemTabs({ problemId }: ProblemTabsProps) {
  const location = useLocation();
  const { currentUser } = useAuth();
  const submitPath = `/problems/${problemId}/submit`;

  return (
    <nav className="problem-tabs" aria-label="题目页面导航">
      <NavLink end to={`/problems/${problemId}`}>
        Problem
      </NavLink>
      {currentUser ? (
        <NavLink to={submitPath}>Submit</NavLink>
      ) : (
        <Link
          to="/login"
          state={{ from: submitPath, source: `${location.pathname}${location.search}` }}
        >
          Submit
        </Link>
      )}
      <NavLink to={`/problems/${problemId}/submissions`}>Status</NavLink>
      <NavLink to={`/problems/${problemId}/rank`}>Rank</NavLink>
    </nav>
  );
}
