export function ContestFilterPanel() {
  return (
    <section className="sidebar-panel filter-panel contest-filter-panel">
      <div className="filter-panel__heading">
        <span className="eyebrow">Contest</span>
        <h2>Filter</h2>
      </div>

      <p className="contest-filter-panel__notice">
        当前比赛列表接口仅支持分页，筛选条件将在后端提供后启用。
      </p>

      <label className="form-field">
        <span>Contest name</span>
        <input type="search" placeholder="Name" disabled />
      </label>

      <fieldset className="form-field difficulty-field" disabled>
        <legend>Problem number</legend>
        <div className="difficulty-field__inputs">
          <input type="number" placeholder="Min" aria-label="最少题目数量" />
          <span aria-hidden="true">—</span>
          <input type="number" placeholder="Max" aria-label="最多题目数量" />
        </div>
      </fieldset>

      <label className="form-field">
        <span>Contest type</span>
        <select disabled defaultValue="">
          <option value="">Unavailable</option>
        </select>
      </label>

      <div className="filter-panel__actions contest-filter-panel__actions">
        <button className="primary-button" type="button" disabled>
          <span aria-hidden="true">⌕</span>
          Filter unavailable
        </button>
      </div>
    </section>
  );
}
