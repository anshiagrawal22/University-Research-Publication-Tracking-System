import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  cleanDoi,
  cleanOrcid,
  createPublication,
  DEPARTMENTS,
  fetchOrcidWorks,
  fetchPublicationByDoi,
  getErrorMessage,
  isValidDoi,
  isValidOrcid,
  validatePublicationFields,
} from '../services/publicationApi';
import { useToast } from '../components/Toast';

const EMPTY = {
  title: '',
  authors: '',
  doi: '',
  year: '',
  department: '',
  journal: '',
};

export default function SubmitPublication() {
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('mode') === 'orcid' ? 'orcid' : 'manual';

  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [source, setSource] = useState('FORM');
  const [fetching, setFetching] = useState(false);
  const [saving, setSaving] = useState(false);

  const [orcidInput, setOrcidInput] = useState('');
  const [orcidError, setOrcidError] = useState('');
  const [orcidLoading, setOrcidLoading] = useState(false);
  const [orcidWorks, setOrcidWorks] = useState([]);
  const [orcidSearched, setOrcidSearched] = useState(false);
  const [resolvedOrcidId, setResolvedOrcidId] = useState('');
  const [batchDepartment, setBatchDepartment] = useState('');
  const [importing, setImporting] = useState(false);
  const [importSummary, setImportSummary] = useState(null);

  function switchTab(mode) {
    setSearchParams(mode === 'orcid' ? { mode: 'orcid' } : {});
  }

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  // Fetch publication details using DOI and populate form fields.
  async function handleDoiLookup() {
    const doi = cleanDoi(form.doi);
    if (!isValidDoi(doi)) {
      setErrors((current) => ({ ...current, doi: 'Enter a DOI such as 10.1038/nature12373' }));
      return;
    }
    setFetching(true);
    try {
      const data = await fetchPublicationByDoi(doi);
      setForm((current) => ({
        ...current,
        doi: data.doi || doi,
        title: data.title || current.title,
        authors: data.authors?.length ? data.authors.join(', ') : current.authors,
        journal: data.journal || current.journal,
        year: data.year ? String(data.year) : current.year,
      }));
      setSource('CROSSREF');
      setErrors((current) => ({ ...current, doi: undefined, title: undefined, authors: undefined, year: undefined }));
      const missing = [];
      if (!data.title) missing.push('title');
      if (!data.authors?.length) missing.push('authors');
      if (!data.year) missing.push('year');
      toast.push(
        'success',
        missing.length > 0
          ? `Crossref details loaded (fill in missing ${missing.join(', ')} and department).`
          : 'Details filled from Crossref. Choose a department, then submit.'
      );
    } catch (error) {
      toast.push('error', getErrorMessage(error, 'Crossref could not be reached. You can still enter the details.'));
    } finally {
      setFetching(false);
    }
  }

  async function onSubmit(event) {
    event.preventDefault();
    const { errors: next, authors, doi } = validatePublicationFields(form);
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setSaving(true);
    try {
      const created = await createPublication({
        title: form.title.trim(),
        authors,
        doi: doi || null,
        year: Number(form.year),
        department: form.department,
        journal: form.journal.trim() || null,
        source,
      });
      if (created.status === 'REJECTED') {
        toast.push('error', 'Submitted, but blocked by the pre-publish Security Gate. Inspect findings in the register.');
      } else {
        toast.push('success', 'Submitted to Security Gate review. Clear and publish it from the register to go live.');
      }
      navigate('/publications');
    } catch (error) {
      const data = error?.response?.data;
      if (data?.fieldErrors?.length) {
        const mapped = {};
        data.fieldErrors.forEach((item) => {
          mapped[item.field] = item.message;
        });
        setErrors((current) => ({ ...current, ...mapped }));
      }
      toast.push('error', getErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  // Retrieve public works for the entered ORCID iD.
  async function handleOrcidSearch(event) {
    event.preventDefault();
    const normalized = cleanOrcid(orcidInput);
    if (!isValidOrcid(normalized)) {
      setOrcidError('Enter an ORCID iD such as 0000-0002-1825-0097');
      return;
    }

    setOrcidError('');
    setImportSummary(null);
    setOrcidLoading(true);
    setOrcidSearched(true);

    try {
      const data = await fetchOrcidWorks(normalized);
      setResolvedOrcidId(normalized);
      setOrcidWorks(
        (data || []).map((work, index) => ({
          key: `${work.doi || 'no-doi'}-${index}`,
          title: work.title || '',
          authorsText: work.authors?.length ? work.authors.join(', ') : '',
          journal: work.journal || '',
          year: work.year ? String(work.year) : '',
          doi: work.doi || '',
          department: batchDepartment,
          enrichedFromCrossref: Boolean(work.enrichedFromCrossref),
          alreadyExists: Boolean(work.alreadyExists),
          imported: false,
          selected: !work.alreadyExists,
          rowError: '',
        }))
      );
    } catch (error) {
      setOrcidWorks([]);
      setOrcidError(getErrorMessage(error, 'ORCID works could not be retrieved.'));
    } finally {
      setOrcidLoading(false);
    }
  }

  function applyBatchDepartment(dept) {
    setBatchDepartment(dept);
    setOrcidWorks((current) =>
      current.map((item) => (item.imported || item.alreadyExists ? item : { ...item, department: dept, rowError: '' }))
    );
  }

  function updateOrcidWork(key, patch) {
    setOrcidWorks((current) =>
      current.map((item) => (item.key === key ? { ...item, ...patch, rowError: '' } : item))
    );
  }

  function selectAllImportable(selected) {
    setOrcidWorks((current) =>
      current.map((item) => (item.alreadyExists || item.imported ? item : { ...item, selected }))
    );
  }

  // Save each selected ORCID publication through the shared backend create endpoint.
  async function importSelectedWorks() {
    const selectedItems = orcidWorks.filter((item) => item.selected && !item.alreadyExists && !item.imported);
    if (selectedItems.length === 0) {
      toast.push('error', 'Select at least one new publication to import.');
      return;
    }

    let invalidCount = 0;
    const validatedByKey = new Map();
    selectedItems.forEach((item) => {
      const { errors: rowErrors, authors, doi, yearNum } = validatePublicationFields({
        title: item.title,
        authors: item.authorsText,
        year: item.year,
        department: item.department,
        doi: item.doi,
      });

      const issueFields = Object.keys(rowErrors);
      if (issueFields.length > 0) {
        invalidCount++;
        validatedByKey.set(item.key, { error: `Missing or invalid: ${issueFields.join(', ')}` });
      } else {
        validatedByKey.set(item.key, {
          payload: {
            title: item.title.trim(),
            authors,
            doi: doi || null,
            year: yearNum,
            department: item.department,
            journal: item.journal.trim() || null,
            orcidId: resolvedOrcidId,
            source: 'ORCID',
          },
        });
      }
    });

    if (invalidCount > 0) {
      setOrcidWorks((current) =>
        current.map((item) => {
          const check = validatedByKey.get(item.key);
          return check?.error ? { ...item, rowError: check.error } : item;
        })
      );
      toast.push('error', `Complete required fields on ${invalidCount} selected publication(s) before importing.`);
      return;
    }

    setImporting(true);
    let importedCount = 0;
    let duplicateCount = 0;
    let failedCount = 0;
    const outcomes = new Map();

    for (const item of selectedItems) {
      const { payload } = validatedByKey.get(item.key);
      try {
        await createPublication(payload);
        importedCount++;
        outcomes.set(item.key, { imported: true, alreadyExists: true, selected: false, rowError: '' });
      } catch (error) {
        if (error?.response?.status === 409) {
          duplicateCount++;
          outcomes.set(item.key, { alreadyExists: true, selected: false, rowError: 'Already exists in register (duplicate DOI)' });
        } else {
          failedCount++;
          outcomes.set(item.key, { rowError: getErrorMessage(error, 'Failed to save publication.') });
        }
      }
    }

    setOrcidWorks((current) =>
      current.map((item) => (outcomes.has(item.key) ? { ...item, ...outcomes.get(item.key) } : item))
    );
    setImportSummary({ importedCount, duplicateCount, failedCount });

    if (importedCount > 0 && failedCount === 0 && duplicateCount === 0) {
      toast.push('success', `Imported ${importedCount} publication(s) into Security Gate review.`);
    } else if (importedCount > 0) {
      toast.push('success', `Imported ${importedCount}; ${duplicateCount} duplicate(s), ${failedCount} failed.`);
    } else {
      toast.push('error', 'No publications were imported. Check the row messages below.');
    }
    setImporting(false);
  }

  const selectedCount = orcidWorks.filter((item) => item.selected && !item.alreadyExists && !item.imported).length;
  const importableCount = orcidWorks.filter((item) => !item.alreadyExists && !item.imported).length;

  return (
    <div className={activeTab === 'orcid' ? 'submit-wide' : 'narrow'}>
      <p className="eyebrow">Register</p>
      <h1>Submit a publication</h1>
      <p className="lede">
        Enter a record manually, fetch metadata from Crossref with a DOI, or import publicly available works from an ORCID profile.
      </p>

      <div className="tab-bar" role="tablist" aria-label="Submission method">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'manual'}
          className={activeTab === 'manual' ? 'tab-button active' : 'tab-button'}
          onClick={() => switchTab('manual')}
        >
          Manual &amp; DOI Lookup
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'orcid'}
          className={activeTab === 'orcid' ? 'tab-button active' : 'tab-button'}
          onClick={() => switchTab('orcid')}
        >
          Import from ORCID
        </button>
      </div>

      {activeTab === 'manual' ? (
        <form className="form-card" onSubmit={onSubmit} noValidate>
          <div className="doi-row">
            <label>
              DOI (optional — fetch metadata automatically)
              <input
                value={form.doi}
                onChange={(event) => {
                  update('doi', event.target.value);
                  setSource('FORM');
                }}
                placeholder="10.1038/nature12373 or https://doi.org/..."
                aria-invalid={Boolean(errors.doi)}
              />
              {errors.doi && <span className="field-error">{errors.doi}</span>}
            </label>
            <button type="button" className="button button-secondary" onClick={handleDoiLookup} disabled={fetching}>
              {fetching ? 'Fetching…' : 'Fetch from DOI'}
            </button>
          </div>
          <label>
            Title
            <input
              value={form.title}
              onChange={(event) => update('title', event.target.value)}
              aria-invalid={Boolean(errors.title)}
              maxLength={500}
            />
            {errors.title && <span className="field-error">{errors.title}</span>}
          </label>
          <label>
            Authors
            <input
              value={form.authors}
              onChange={(event) => update('authors', event.target.value)}
              placeholder="Separate names with commas"
              aria-invalid={Boolean(errors.authors)}
            />
            <span className="hint">Example: Ada Lovelace, Alan Turing</span>
            {errors.authors && <span className="field-error">{errors.authors}</span>}
          </label>
          <div className="split">
            <label>
              Year
              <input
                type="number"
                min="1900"
                max="2100"
                step="1"
                value={form.year}
                onChange={(event) => update('year', event.target.value)}
                aria-invalid={Boolean(errors.year)}
              />
              {errors.year && <span className="field-error">{errors.year}</span>}
            </label>
            <label>
              Department
              <select
                value={form.department}
                onChange={(event) => update('department', event.target.value)}
                aria-invalid={Boolean(errors.department)}
              >
                <option value="">Select a department</option>
                {DEPARTMENTS.map((department) => (
                  <option key={department} value={department}>{department}</option>
                ))}
              </select>
              {errors.department && <span className="field-error">{errors.department}</span>}
            </label>
          </div>
          <label>
            Journal
            <input
              value={form.journal}
              onChange={(event) => update('journal', event.target.value)}
              maxLength={300}
            />
          </label>
          <div className="form-actions">
            <button type="submit" className="button button-primary" disabled={saving}>
              {saving ? 'Saving…' : 'Submit publication'}
            </button>
          </div>
        </form>
      ) : (
        <div className="orcid-stack">
          <form className="form-card" onSubmit={handleOrcidSearch} noValidate>
            <div className="doi-row">
              <label>
                ORCID iD
                <input
                  value={orcidInput}
                  onChange={(event) => {
                    setOrcidInput(event.target.value);
                    setOrcidError('');
                  }}
                  placeholder="0000-0002-1825-0097 or https://orcid.org/..."
                  aria-invalid={Boolean(orcidError)}
                />
                <span className="hint">Public works are retrieved from ORCID and enriched via Crossref when a DOI is present.</span>
                {orcidError && <span className="field-error">{orcidError}</span>}
              </label>
              <button type="submit" className="button button-primary" disabled={orcidLoading}>
                {orcidLoading ? 'Fetching works…' : 'Fetch ORCID Works'}
              </button>
            </div>
          </form>

          {importSummary && (
            <div className="banner banner-success" role="status">
              <p>
                Import complete: <strong>{importSummary.importedCount}</strong> added
                {importSummary.duplicateCount > 0 ? `, ${importSummary.duplicateCount} skipped (duplicate DOI)` : ''}
                {importSummary.failedCount > 0 ? `, ${importSummary.failedCount} failed` : ''}.
              </p>
              <Link to="/publications" className="button button-secondary">
                View in Register
              </Link>
            </div>
          )}

          {orcidLoading && (
            <div className="panel">
              <div className="skeleton-stack">
                <div className="skeleton skeleton-row" />
                <div className="skeleton skeleton-row" />
                <div className="skeleton skeleton-row" />
              </div>
            </div>
          )}

          {!orcidLoading && orcidSearched && !orcidError && orcidWorks.length === 0 && (
            <div className="empty card-empty">
              <h2>No public works found</h2>
              <p>This ORCID profile has no publicly visible works available to import.</p>
            </div>
          )}

          {!orcidLoading && orcidWorks.length > 0 && (
            <div className="panel orcid-results">
              <div className="orcid-toolbar">
                <label>
                  Default department for import
                  <select
                    value={batchDepartment}
                    onChange={(event) => applyBatchDepartment(event.target.value)}
                  >
                    <option value="">Select a department</option>
                    {DEPARTMENTS.map((department) => (
                      <option key={department} value={department}>{department}</option>
                    ))}
                  </select>
                </label>
                <div className="orcid-toolbar-actions">
                  <button
                    type="button"
                    className="button button-secondary"
                    disabled={importableCount === 0 || importing}
                    onClick={() => selectAllImportable(selectedCount < importableCount)}
                  >
                    {selectedCount < importableCount ? 'Select all new' : 'Deselect all'}
                  </button>
                  <button
                    type="button"
                    className="button button-primary"
                    disabled={selectedCount === 0 || importing}
                    onClick={importSelectedWorks}
                  >
                    {importing ? 'Importing…' : `Import Selected (${selectedCount})`}
                  </button>
                </div>
              </div>

              <div className="orcid-list">
                {orcidWorks.map((item) => {
                  const disabled = item.alreadyExists || item.imported;
                  return (
                    <article
                      key={item.key}
                      className={disabled ? 'orcid-item is-disabled' : 'orcid-item'}
                    >
                      <div className="orcid-item-check">
                        <input
                          type="checkbox"
                          checked={item.selected}
                          disabled={disabled || importing}
                          aria-label={`Select ${item.title || 'work'}`}
                          onChange={(event) => updateOrcidWork(item.key, { selected: event.target.checked })}
                        />
                      </div>
                      <div className="orcid-item-body">
                        <div className="orcid-item-badges">
                          {item.imported ? (
                            <span className="chip chip-verified">Imported</span>
                          ) : item.alreadyExists ? (
                            <span className="chip chip-in_review">Already in register</span>
                          ) : null}
                          <span className="tag">
                            {item.enrichedFromCrossref ? 'Crossref enriched' : 'ORCID metadata'}
                          </span>
                          {item.doi && <span className="cell-sub">DOI {item.doi}</span>}
                        </div>
                        <h3>{item.title || 'Untitled work'}</h3>
                        <div className="orcid-item-fields">
                          <label>
                            Authors
                            <input
                              value={item.authorsText}
                              disabled={disabled || importing}
                              onChange={(event) => updateOrcidWork(item.key, { authorsText: event.target.value })}
                              placeholder="Comma-separated authors"
                            />
                          </label>
                          <label>
                            Journal
                            <input
                              value={item.journal}
                              disabled={disabled || importing}
                              onChange={(event) => updateOrcidWork(item.key, { journal: event.target.value })}
                              placeholder="Journal name"
                            />
                          </label>
                          <label>
                            Year
                            <input
                              type="number"
                              min="1900"
                              max="2100"
                              value={item.year}
                              disabled={disabled || importing}
                              onChange={(event) => updateOrcidWork(item.key, { year: event.target.value })}
                            />
                          </label>
                          <label>
                            Department
                            <select
                              value={item.department}
                              disabled={disabled || importing}
                              onChange={(event) => updateOrcidWork(item.key, { department: event.target.value })}
                            >
                              <option value="">Select department</option>
                              {DEPARTMENTS.map((department) => (
                                <option key={department} value={department}>{department}</option>
                              ))}
                            </select>
                          </label>
                        </div>
                        {item.rowError && <p className="field-error">{item.rowError}</p>}
                      </div>
                    </article>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
