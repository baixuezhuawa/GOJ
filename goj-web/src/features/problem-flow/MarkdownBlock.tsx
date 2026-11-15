import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

interface MarkdownBlockProps {
  children: string | null | undefined;
}

export function MarkdownBlock({ children }: MarkdownBlockProps) {
  if (!children?.trim()) return <p className="markdown-empty">--</p>;
  return (
    <div className="markdown-content">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
    </div>
  );
}
