type PaginationToken = number | "ellipsis-left" | "ellipsis-right";

interface CompactPaginationProps {
  current: number;
  pages: number;
  onChange: (page: number) => void;
  disabled?: boolean;
  ariaLabel?: string;
}

function buildPageTokens(current: number, pages: number): PaginationToken[] {
  if (pages <= 7) {
    return Array.from({ length: pages }, (_, index) => index + 1);
  }

  const visible = new Set([1, pages, current - 1, current, current + 1]);
  const pageNumbers = [...visible]
    .filter((page) => page >= 1 && page <= pages)
    .sort((left, right) => left - right);

  const tokens: PaginationToken[] = [];
  pageNumbers.forEach((page, index) => {
    const previous = pageNumbers[index - 1];
    if (previous && page - previous > 1) {
      tokens.push(previous === 1 ? "ellipsis-left" : "ellipsis-right");
    }
    tokens.push(page);
  });
  return tokens;
}

export function CompactPagination({
  current,
  pages,
  onChange,
  disabled = false,
  ariaLabel = "列表分页",
}: CompactPaginationProps) {
  if (pages <= 1) return null;
  const tokens = buildPageTokens(current, pages);

  return (
    <nav className="compact-pagination" aria-label={ariaLabel}>
      <button
        type="button"
        disabled={disabled || current <= 1}
        onClick={() => onChange(current - 1)}
        aria-label="上一页"
      >
        ‹
      </button>

      {tokens.map((token) =>
        typeof token === "number" ? (
          <button
            key={token}
            className={token === current ? "is-current" : ""}
            type="button"
            disabled={disabled}
            aria-current={token === current ? "page" : undefined}
            onClick={() => onChange(token)}
          >
            {token}
          </button>
        ) : (
          <span key={token} aria-hidden="true">
            …
          </span>
        ),
      )}

      <button
        type="button"
        disabled={disabled || current >= pages}
        onClick={() => onChange(current + 1)}
        aria-label="下一页"
      >
        ›
      </button>
    </nav>
  );
}
