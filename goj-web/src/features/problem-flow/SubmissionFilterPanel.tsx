import type { SupportedLanguage } from "../../types/api";
import { submissionStatusOptions } from "./submissionStatus";

export interface SubmissionFilterDraft {
  problemId: string;
  language: string;
  status: string;
}

interface SubmissionFilterPanelProps {
  filters: SubmissionFilterDraft;
  languages: SupportedLanguage[];
  fixedProblemId?: number;
  loading: boolean;
  onChange: (value: SubmissionFilterDraft) => void;
  onApply: () => void;
  onClear: () => void;
}

export function SubmissionFilterPanel({
  filters,
  languages,
  fixedProblemId,
  loading,
  onChange,
  onApply,
  onClear,
}: SubmissionFilterPanelProps) {
  return (
    <section className="sidebar-panel submission-filter-panel">
      <div className="filter-panel__heading">
        <span className="eyebrow">My submissions</span>
        <h2>Filter</h2>
      </div>

      <label className="form-field">
        <span>Problem ID</span>
        <input
          inputMode="numeric"
          value={fixedProblemId ?? filters.problemId}
          disabled={fixedProblemId !== undefined}
          onChange={(event) => onChange({ ...filters, problemId: event.target.value })}
        />
      </label>

      <label className="form-field">
        <span>Language</span>
        <select
          value={filters.language}
          onChange={(event) => onChange({ ...filters, language: event.target.value })}
        >
          <option value="">All</option>
          {languages.map((item) => (
            <option key={item.code} value={item.code}>{item.displayName}</option>
          ))}
        </select>
      </label>

      <label className="form-field">
        <span>Status</span>
        <select
          value={filters.status}
          onChange={(event) => onChange({ ...filters, status: event.target.value })}
        >
          <option value="">All</option>
          {submissionStatusOptions.map((status) => (
            <option key={status} value={status}>{status}</option>
          ))}
        </select>
      </label>

      <div className="filter-panel__actions">
        <button className="secondary-button" type="button" onClick={onClear} disabled={loading}>
          Clear
        </button>
        <button className="primary-button" type="button" onClick={onApply} disabled={loading}>
          Filter
        </button>
      </div>
    </section>
  );
}
