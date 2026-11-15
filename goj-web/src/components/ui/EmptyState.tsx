import restingCat from "../../assets/resting-cat.png";
import type { ReactNode } from "react";

interface EmptyStateProps {
  title: string;
  description?: string;
  children?: ReactNode;
}

/**
 * 统一展示列表没有内容时的休息小猫状态。
 */
export function EmptyState({ title, description, children }: EmptyStateProps) {
  return (
    <div className="request-state request-state--empty">
      <img className="request-state__cat" src={restingCat} alt="休息中的小猫" />
      <strong>{title}</strong>
      {description && <p>{description}</p>}
      {children}
    </div>
  );
}
