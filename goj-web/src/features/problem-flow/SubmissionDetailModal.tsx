import { useEffect } from "react";
import { SubmissionDetailContent } from "./SubmissionDetailContent";
import { useSubmissionDetail } from "./useSubmissionDetail";

interface SubmissionDetailModalProps {
  submissionId: number;
  onClose: () => void;
}

export function SubmissionDetailModal({ submissionId, onClose }: SubmissionDetailModalProps) {
  const detail = useSubmissionDetail(submissionId);

  useEffect(() => {
    const closeWithEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", closeWithEscape);
    document.body.classList.add("submission-modal-open");
    return () => {
      document.removeEventListener("keydown", closeWithEscape);
      document.body.classList.remove("submission-modal-open");
    };
  }, [onClose]);

  return (
    <div
      className="submission-modal"
      role="presentation"
      onClick={onClose}
    >
      <section
        className="submission-modal__card"
        role="dialog"
        aria-modal="true"
        aria-label={`提交 #${submissionId} 详情`}
        onClick={(event) => event.stopPropagation()}
      >
        <button
          className="submission-modal__close"
          type="button"
          aria-label="关闭提交详情"
          onClick={onClose}
        >
          ×
        </button>
        <div className="submission-modal__content submission-detail-panel">
          <SubmissionDetailContent
            submission={detail.submission}
            loading={detail.loading}
            error={detail.error}
            onRetry={detail.retry}
            editorHeight="420px"
          />
        </div>
      </section>
    </div>
  );
}
