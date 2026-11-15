import { useMemo, useState } from "react";
import type { ProblemTag } from "../../types/api";
import type { ProblemFilterDraft } from "./problemFilters";

interface ProblemFilterPanelProps {
  filters: ProblemFilterDraft;
  tags: ProblemTag[];
  tagsLoading: boolean;
  tagsError: string | null;
  authenticated: boolean;
  requestLoading: boolean;
  onChange: (filters: ProblemFilterDraft) => void;
  onApply: () => void;
  onClear: () => void;
  onRetryTags: () => void;
}

export function ProblemFilterPanel({
  filters,
  tags,
  tagsLoading,
  tagsError,
  authenticated,
  requestLoading,
  onChange,
  onApply,
  onClear,
  onRetryTags,
}: ProblemFilterPanelProps) {
  const [tagPickerOpen, setTagPickerOpen] = useState(false);
  const selectedTags = useMemo(
    () => tags.filter((tag) => filters.tagIds.includes(tag.tagId)),
    [filters.tagIds, tags],
  );

  const updateField = <K extends keyof ProblemFilterDraft>(
    field: K,
    value: ProblemFilterDraft[K],
  ) => onChange({ ...filters, [field]: value });

  const addTag = (tagId: number) => {
    if (filters.tagIds.includes(tagId)) return;
    updateField("tagIds", [...filters.tagIds, tagId]);
  };

  const removeTag = (tagId: number) => {
    updateField(
      "tagIds",
      filters.tagIds.filter((selectedId) => selectedId !== tagId),
    );
  };

  return (
    <section className="sidebar-panel filter-panel">
      <div className="filter-panel__heading">
        <h2>Filter</h2>
      </div>

      <label className="form-field">
        <span>题目名称或 ID</span>
        <input
          type="search"
          value={filters.keyword}
          placeholder="Search problem"
          onChange={(event) => updateField("keyword", event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") onApply();
          }}
        />
      </label>

      <fieldset className="form-field difficulty-field">
        <legend>Difficulty</legend>
        <div className="difficulty-field__inputs">
          <input
            type="number"
            value={filters.difficultyMin}
            placeholder="Min"
            aria-label="最低难度"
            onChange={(event) => updateField("difficultyMin", event.target.value)}
          />
          <span aria-hidden="true">—</span>
          <input
            type="number"
            value={filters.difficultyMax}
            placeholder="Max"
            aria-label="最高难度"
            onChange={(event) => updateField("difficultyMax", event.target.value)}
          />
        </div>
      </fieldset>

      <div className="form-field tag-filter">
        <span>Tags</span>
        <button
          className="add-tag-button"
          type="button"
          aria-expanded={tagPickerOpen}
          onClick={() => setTagPickerOpen((open) => !open)}
        >
          <span>Add Tag</span>
          <span aria-hidden="true">▤</span>
        </button>

        {tagPickerOpen && (
          <div className="tag-picker">
            {tagsLoading ? (
              <p>正在加载标签…</p>
            ) : tagsError ? (
              <div className="tag-picker__error">
                <p>{tagsError}</p>
                <button type="button" onClick={onRetryTags}>
                  重试
                </button>
              </div>
            ) : tags.length === 0 ? (
              <p>暂无标签</p>
            ) : (
              tags.map((tag) => (
                <button
                  key={tag.tagId}
                  className={filters.tagIds.includes(tag.tagId) ? "is-selected" : ""}
                  type="button"
                  disabled={filters.tagIds.includes(tag.tagId)}
                  onClick={() => addTag(tag.tagId)}
                >
                  {tag.tagName}
                </button>
              ))
            )}
          </div>
        )}

        <div className="selected-tags" aria-label="已选择标签">
          {selectedTags.map((tag) => (
            <button
              key={tag.tagId}
              type="button"
              onClick={() => removeTag(tag.tagId)}
              title={`移除 ${tag.tagName}`}
            >
              {tag.tagName}
              <span aria-hidden="true">×</span>
            </button>
          ))}
        </div>
      </div>

      {authenticated && (
        <label className="form-field">
          <span>Progress</span>
          <select
            value={filters.solveStatus}
            onChange={(event) =>
              updateField(
                "solveStatus",
                event.target.value as ProblemFilterDraft["solveStatus"],
              )
            }
          >
            <option value="">All</option>
            <option value="UNATTEMPTED">Unattempted</option>
            <option value="ATTEMPTED">Attempted</option>
            <option value="SOLVED">Solved</option>
          </select>
        </label>
      )}

      <div className="filter-panel__actions">
        <button className="secondary-button" type="button" onClick={onClear}>
          Clear
        </button>
        <button
          className="primary-button"
          type="button"
          disabled={requestLoading}
          onClick={onApply}
        >
          <span aria-hidden="true">⌕</span>
          Filter
        </button>
      </div>
    </section>
  );
}
