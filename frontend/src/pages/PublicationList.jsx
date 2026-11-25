import { useEffect, useState } from 'react';
import { CheckCircle2, Pencil, Search, ShieldAlert, ShieldCheck, Trash2, X } from 'lucide-react';
import {
  deletePublication,
  DEPARTMENTS,
  fetchFilterOptions,
  fetchPublications,
  fetchSecurityGateReport,
  formatAuthors,
  getErrorMessage,
  publishPublication,
  updatePublication,
  validatePublicationFields,
} from '../services/publicationApi';
import { useToast } from '../components/Toast';
import Select from '../components/Select';

const STATUSES = [
  { value: '', label: 'All statuses' },
  { value: 'VERIFIED', label: 'Verified' },
  { value: 'IN_REVIEW', label: 'In review' },
  { value: 'REJECTED', label: 'Rejected' },
];

const STATUS_LABELS = {
  VERIFIED: 'Verified',
  IN_REVIEW: 'In review',
  REJECTED: 'Rejected',
};

const GATE_LABELS = {
  PASSED: 'Gate passed',
  WARN: 'Gate warning',
  FAILED: 'Gate blocked',
};

const GATE_CHIP_CLASS = {
  PASSED: 'chip-verified',
  WARN: 'chip-in_review',
  FAILED: 'chip-rejected',
};

export default function PublicationList() {
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [debounced, setDebounced] = useState('');
  const [department, setDepartment] = useState('');
  const [faculty, setFaculty] = useState('');
  const [year, setYear] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [filterOptions, setFilterOptions] = useState({
    departments: DEPARTMENTS,
    years: [],
    faculties: [],
  });

  const [editing, setEditing] = useState(null);
  const [editErrors, setEditErrors] = useState({});
  const [savingEdit, setSavingEdit] = useState(false);

  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);

  const [gateModal, setGateModal] = useState(null);
  const [gateLoading, setGateLoading] = useState(false);
  const [publishingGate, setPublishingGate] = useState(false);

  // Debounce free-text search to avoid querying the API on every keystroke.
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    setPage(0);
  }, [debounced, department, faculty, year, status]);

  useEffect(() => {
    let active = true;
    fetchFilterOptions()
      .then((options) => {
        if (active) setFilterOptions(options);
      })
      .catch(() => {
        // Keep default department options if filter metadata fails
      });
    return () => {
      active = false;
    };
  }, [reload]);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      setLoading(true);
      setError('');
      try {
        const params = { page, size: 8 };
        if (debounced) params.search = debounced;
        if (department) params.department = department;
        if (faculty) params.faculty = faculty;
        if (year) params.year = year;
        if (status) params.status = status;
        setData(await fetchPublications(params, controller.signal));
      } catch (err) {
        if (err.code !== 'ERR_CANCELED') {
          setError(getErrorMessage(err, 'The register could not be loaded.'));
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [debounced, department, faculty, year, status, page, reload]);

  const rows = data?.content || [];
  const total = data?.totalElements || 0;
  const from = total === 0 ? 0 : page * (data?.size || 8) + 1;
  const to = Math.min(total, (page + 1) * (data?.size || 8));
  const hasFilters = Boolean(search || department || faculty || year || status);

  function resetFilters() {
    setSearch('');
    setDebounced('');
    setDepartment('');
    setFaculty('');
    setYear('');
    setStatus('');
  }

  function startEdit(row) {
    setConfirmDeleteId(null);
    setEditErrors({});
    setEditing({
      id: row.id,
      title: row.title || '',
      authors: (row.authors || []).join(', '),
      doi: row.doi || '',
      year: row.year ? String(row.year) : '',
      department: row.department || '',
      journal: row.journal || '',
      orcidId: row.orcidId || '',
      status: row.status || 'VERIFIED',
    });
  }

  async function submitEdit(event) {
    event.preventDefault();
    if (!editing) return;

    const { errors: nextErrors, authors, doi, yearNum } = validatePublicationFields(editing);
    setEditErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setSavingEdit(true);
    try {
      await updatePublication(editing.id, {
        title: editing.title.trim(),
        authors,
        doi: doi || null,
        year: yearNum,
        department: editing.department,
        journal: editing.journal.trim() || null,
        orcidId: editing.orcidId.trim() || null,
        status: editing.status,
      });
      toast.push('success', 'Publication updated.');
      setEditing(null);
      setReload((current) => current + 1);
    } catch (err) {
      const resp = err?.response?.data;
      if (resp?.fieldErrors?.length) {
        const mapped = {};
        resp.fieldErrors.forEach((item) => {
          mapped[item.field] = item.message;
        });
        setEditErrors((current) => ({ ...current, ...mapped }));
      }
      toast.push('error', getErrorMessage(err, 'Could not update publication.'));
    } finally {
      setSavingEdit(false);
    }
  }

  async function confirmDelete(id) {
    setDeletingId(id);
    try {
      await deletePublication(id);
      toast.push('success', 'Publication removed from the register.');
      setConfirmDeleteId(null);
      setReload((current) => current + 1);
    } catch (err) {
      toast.push('error', getErrorMessage(err, 'Could not delete publication.'));
    } finally {
      setDeletingId(null);
    }
  }

  async function openSecurityGate(row) {
    setConfirmDeleteId(null);
    setGateModal({ publication: row, report: null, error: '' });
    setGateLoading(true);
    try {
      const report = await fetchSecurityGateReport(row.id);
      setGateModal({ publication: row, report, error: '' });
      setReload((current) => current + 1);
    } catch (err) {
      setGateModal({
        publication: row,
        report: null,
        error: getErrorMessage(err, 'Could not run security gate checks.'),
      });
    } finally {
      setGateLoading(false);
    }
  }

  async function handlePublishFromGate() {
    if (!gateModal?.publication) return;
    setPublishingGate(true);
    try {
      const updated = await publishPublication(gateModal.publication.id);
      toast.push('success', 'Security gates cleared. Publication is now live on the website.');
      setGateModal(null);
      setReload((current) => current + 1);
      return updated;
    } catch (err) {
      toast.push('error', getErrorMessage(err, 'Security gate blocked publication from going live.'));
      try {
        const refreshed = await fetchSecurityGateReport(gateModal.publication.id);
        setGateModal((curr) => (curr ? { ...curr, report: refreshed } : curr));
        setReload((current) => current + 1);
      } catch {
        // Keep existing modal error context
      }
    } finally {
      setPublishingGate(false);
    }
  }

  return (
    <div>
      <p className="eyebrow">Register</p>
      <h1>Publications</h1>
      <p className="lede">Every submitted record is listed here, including those still in review or rejected.</p>

      <div className="filter-bar pubs-filters">
        <label className="search-field">
          <span>Search</span>
          <span className="search-input-wrap">
            <Search size={16} strokeWidth={1.5} aria-hidden="true" />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Title, author, or DOI"
            />
          </span>
        </label>
        <label>
          <span>Department</span>
          <Select
            value={department}
            onChange={setDepartment}
            options={[{ value: '', label: 'All departments' }, ...filterOptions.departments.map((item) => ({ value: item, label: item }))]}
          />
        </label>
        <label>
          <span>Faculty</span>
          <Select
            value={faculty}
            onChange={setFaculty}
            options={[{ value: '', label: 'All faculty' }, ...filterOptions.faculties.map((item) => ({ value: item, label: item }))]}
          />
        </label>
        <label>
          <span>Year</span>
          <Select
            value={year}
            onChange={setYear}
            options={[{ value: '', label: 'All years' }, ...filterOptions.years.map((item) => ({ value: String(item), label: String(item) }))]}
          />
        </label>
        <label>
          <span>Status</span>
          <Select value={status} onChange={setStatus} options={STATUSES} />
        </label>
        <div className="filter-reset">
          <button type="button" className="button button-ghost" onClick={resetFilters} disabled={!hasFilters}>
            Clear filters
          </button>
        </div>
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          <p>{error}</p>
          <button type="button" className="button button-secondary" onClick={() => setReload((current) => current + 1)}>Try again</button>
        </div>
      )}

      <div className="table-card" aria-busy={loading}>
        {loading && rows.length === 0 ? (
          <div className="skeleton-stack">
            <div className="skeleton skeleton-row" />
            <div className="skeleton skeleton-row" />
            <div className="skeleton skeleton-row" />
          </div>
        ) : rows.length === 0 ? (
          <div className="empty">
            <h2>No publications match</h2>
            <p>Try a broader search, or reset the active filters.</p>
          </div>
        ) : (
          <table>
            <caption className="sr-only">Publication register</caption>
            <thead>
              <tr>
                <th>Title</th>
                <th>Authors</th>
                <th>Department</th>
                <th>Year</th>
                <th>Status</th>
                <th className="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const statusKey = row.status || 'IN_REVIEW';
                return (
                  <tr key={row.id}>
                    <td data-label="Title">
                      <strong>{row.title}</strong>
                      {row.journal && <span className="cell-sub">{row.journal}</span>}
                      {row.doi && <span className="cell-sub">DOI {row.doi}</span>}
                    </td>
                    <td data-label="Authors" title={row.authors.length > 4 ? `${row.authors.length.toLocaleString()} authors` : undefined}>{formatAuthors(row.authors)}</td>
                    <td data-label="Department">{row.department}</td>
                    <td data-label="Year">{row.year}</td>
                    <td data-label="Status">
                      <div className="status-stack">
                        <span className={`chip chip-${statusKey.toLowerCase()}`}>
                          {STATUS_LABELS[statusKey] || statusKey}
                        </span>
                        {row.gateStatus && (
                          <span className="gate-sub">
                            {GATE_LABELS[row.gateStatus] || row.gateStatus}
                          </span>
                        )}
                      </div>
                    </td>
                    <td data-label="Actions" className="actions-cell">
                      {confirmDeleteId === row.id ? (
                        <div className="row-actions">
                          <button
                            type="button"
                            className="button button-danger button-sm"
                            disabled={deletingId === row.id}
                            onClick={() => confirmDelete(row.id)}
                          >
                            {deletingId === row.id ? 'Deleting…' : 'Confirm'}
                          </button>
                          <button
                            type="button"
                            className="button button-secondary button-sm"
                            disabled={deletingId === row.id}
                            onClick={() => setConfirmDeleteId(null)}
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <div className="row-actions">
                          <button
                            type="button"
                            className="icon-button icon-button-gate"
                            aria-label={`Security gate for ${row.title}`}
                            title="Security gate & publish"
                            onClick={() => openSecurityGate(row)}
                          >
                            {row.gateStatus === 'FAILED' ? (
                              <ShieldAlert size={15} strokeWidth={1.6} aria-hidden="true" />
                            ) : (
                              <ShieldCheck size={15} strokeWidth={1.6} aria-hidden="true" />
                            )}
                          </button>
                          <button
                            type="button"
                            className="icon-button"
                            aria-label={`Edit ${row.title}`}
                            title="Edit"
                            onClick={() => startEdit(row)}
                          >
                            <Pencil size={15} strokeWidth={1.6} aria-hidden="true" />
                          </button>
                          <button
                            type="button"
                            className="icon-button icon-button-danger"
                            aria-label={`Delete ${row.title}`}
                            title="Delete"
                            onClick={() => setConfirmDeleteId(row.id)}
                          >
                            <Trash2 size={15} strokeWidth={1.6} aria-hidden="true" />
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      <div className="pager">
        <p>{loading && !data ? 'Loading records' : total === 0 ? '0 records' : `${from}–${to} of ${total}`}</p>
        <div>
          <button type="button" className="button button-secondary" disabled={page === 0 || loading} onClick={() => setPage((current) => current - 1)}>
            Previous
          </button>
          <button
            type="button"
            className="button button-secondary"
            disabled={loading || !data || page + 1 >= data.totalPages}
            onClick={() => setPage((current) => current + 1)}
          >
            Next
          </button>
        </div>
      </div>

      {editing && (
        <div className="modal-backdrop" role="presentation" onClick={() => !savingEdit && setEditing(null)}>
          <div
            className="modal-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="edit-pub-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <h2 id="edit-pub-title">Edit publication</h2>
              <button
                type="button"
                className="icon-button"
                aria-label="Close dialog"
                disabled={savingEdit}
                onClick={() => setEditing(null)}
              >
                <X size={18} strokeWidth={1.6} />
              </button>
            </div>
            <form className="modal-form" onSubmit={submitEdit} noValidate>
              <label>
                Title
                <input
                  value={editing.title}
                  onChange={(event) => setEditing((curr) => ({ ...curr, title: event.target.value }))}
                  aria-invalid={Boolean(editErrors.title)}
                  maxLength={500}
                />
                {editErrors.title && <span className="field-error">{editErrors.title}</span>}
              </label>
              <label>
                Authors
                <input
                  value={editing.authors}
                  onChange={(event) => setEditing((curr) => ({ ...curr, authors: event.target.value }))}
                  placeholder="Separate names with commas"
                  aria-invalid={Boolean(editErrors.authors)}
                />
                {editErrors.authors && <span className="field-error">{editErrors.authors}</span>}
              </label>
              <div className="split">
                <label>
                  Year
                  <input
                    type="number"
                    min="1900"
                    max="2100"
                    step="1"
                    value={editing.year}
                    onChange={(event) => setEditing((curr) => ({ ...curr, year: event.target.value }))}
                    aria-invalid={Boolean(editErrors.year)}
                  />
                  {editErrors.year && <span className="field-error">{editErrors.year}</span>}
                </label>
                <label>
                  Department
                  <Select
                    value={editing.department}
                    onChange={(next) => setEditing((curr) => ({ ...curr, department: next }))}
                    aria-invalid={Boolean(editErrors.department)}
                    placeholder="Select a department"
                    options={filterOptions.departments.map((dept) => ({ value: dept, label: dept }))}
                  />
                  {editErrors.department && <span className="field-error">{editErrors.department}</span>}
                </label>
              </div>
              <div className="split">
                <label>
                  DOI
                  <input
                    value={editing.doi}
                    onChange={(event) => setEditing((curr) => ({ ...curr, doi: event.target.value }))}
                    aria-invalid={Boolean(editErrors.doi)}
                  />
                  {editErrors.doi && <span className="field-error">{editErrors.doi}</span>}
                </label>
                <label>
                  Status
                  <Select
                    value={editing.status}
                    onChange={(next) => setEditing((curr) => ({ ...curr, status: next }))}
                    options={STATUSES.filter((item) => item.value)}
                  />
                </label>
              </div>
              <label>
                Journal
                <input
                  value={editing.journal}
                  onChange={(event) => setEditing((curr) => ({ ...curr, journal: event.target.value }))}
                  maxLength={300}
                />
              </label>
              <div className="modal-actions">
                <button
                  type="button"
                  className="button button-secondary"
                  disabled={savingEdit}
                  onClick={() => setEditing(null)}
                >
                  Cancel
                </button>
                <button type="submit" className="button button-primary" disabled={savingEdit}>
                  {savingEdit ? 'Saving…' : 'Save changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {gateModal && (
        <div className="modal-backdrop" role="presentation" onClick={() => !publishingGate && setGateModal(null)}>
          <div
            className="modal-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="gate-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <h2 id="gate-modal-title">Pre-publish Security Gate</h2>
              <button
                type="button"
                className="icon-button"
                aria-label="Close dialog"
                disabled={publishingGate}
                onClick={() => setGateModal(null)}
              >
                <X size={18} strokeWidth={1.6} />
              </button>
            </div>

            <div className="gate-modal-meta">
              <strong>{gateModal.publication.title}</strong>
              <p className="cell-sub">
                Current status: {STATUS_LABELS[gateModal.publication.status] || gateModal.publication.status}
              </p>
            </div>

            {gateLoading ? (
              <div className="skeleton-stack">
                <div className="skeleton skeleton-row" />
                <div className="skeleton skeleton-row" />
              </div>
            ) : gateModal.error ? (
              <div className="banner banner-error" role="alert">
                <p>{gateModal.error}</p>
              </div>
            ) : gateModal.report ? (
              <>
                <div
                  className={`banner ${gateModal.report.publishAllowed ? 'banner-success' : 'banner-error'}`}
                  role="status"
                >
                  <p>
                    <strong>{GATE_LABELS[gateModal.report.overallStatus] || gateModal.report.overallStatus}:</strong>{' '}
                    {gateModal.report.summary}
                  </p>
                </div>

                <ul className="gate-list">
                  {gateModal.report.checks.map((check) => (
                    <li key={check.gate} className="gate-item">
                      <div className="gate-item-head">
                        <strong>{check.label}</strong>
                        <span className={`chip ${GATE_CHIP_CLASS[check.status] || 'chip-in_review'}`}>
                          {check.status}
                        </span>
                      </div>
                      <p>{check.message}</p>
                    </li>
                  ))}
                </ul>
              </>
            ) : null}

            <div className="modal-actions">
              <button
                type="button"
                className="button button-secondary"
                disabled={gateLoading || publishingGate}
                onClick={() => openSecurityGate(gateModal.publication)}
              >
                Re-run checks
              </button>
              {gateModal.publication.status !== 'VERIFIED' && (
                <button
                  type="button"
                  className="button button-primary"
                  disabled={gateLoading || publishingGate || !gateModal.report?.publishAllowed}
                  onClick={handlePublishFromGate}
                >
                  <CheckCircle2 size={15} strokeWidth={1.8} style={{ marginRight: 6, verticalAlign: '-2px' }} />
                  {publishingGate ? 'Publishing…' : 'Approve & Publish Live'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
