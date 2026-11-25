import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  CircleCheck,
  CircleDashed,
  CircleX,
  IdCard,
  Link2,
  LoaderCircle,
  PenLine,
  TriangleAlert,
} from 'lucide-react';
import {
  cleanDoi,
  cleanOrcid,
  createPublication,
  DEPARTMENTS,
  fetchOrcidWorks,
  fetchPublicationByDoi,
  findDuplicateCandidates,
  formatAuthors,
  getErrorMessage,
  isValidDoi,
  isValidOrcid,
  validatePublicationFields,
} from '../services/publicationApi';
import { useToast } from '../components/Toast';
import Select from '../components/Select';

const EMPTY = {
  title: '',
  authors: '',
  doi: '',
  year: '',
  department: '',
  journal: '',
};

const MODES = [
  {
    id: 'doi',
    label: 'Fetch using DOI',
    icon: Link2,
    description: 'Paste a DOI and the details are filled in from Crossref. Review them, choose a department, then submit.',
  },
  {
    id: 'orcid',
    label: 'Import from ORCID',
    icon: IdCard,
    description: 'Enter an ORCID iD to list a researcher’s public works and import several at once.',
  },
  {
    id: 'manual',
    label: 'Enter manually',
    icon: PenLine,
    description: 'Type the publication details yourself. A DOI is optional.',
  },
];

const DEPARTMENT_OPTIONS = DEPARTMENTS.map((department) => ({ value: department, label: department }));

const FIELD_LABELS = {
  title: 'Title',
  authors: 'Authors',
  year: 'Publication year',
  department: 'Department',
  doi: 'DOI',
};

// ['title', 'authors', 'year'] -> "title, authors and year"
function listWords(words, joiner) {
  if (words.length <= 1) return words.join('');
  return `${words.slice(0, -1).join(', ')} ${joiner} ${words[words.length - 1]}`;
}

const STATUS_ICONS = {
  success: CircleCheck,
  error: CircleX,
  warning: TriangleAlert,
  info: LoaderCircle,
  pending: CircleDashed,
};

function StatusCard({ tone, title, children, role }) {
  const Icon = STATUS_ICONS[tone] || CircleCheck;
  return (
    <div className={`status-card status-card-${tone}`} role={role || (tone === 'error' ? 'alert' : 'status')}>
      <div className="status-card-title">
        <Icon size={18} strokeWidth={1.8} aria-hidden="true" className={tone === 'info' ? 'spin' : undefined} />
        <strong>{title}</strong>
      </div>
      {children}
    </div>
  );
}

function CheckRow({ tone, title, detail }) {
  const Icon = STATUS_ICONS[tone] || CircleDashed;
  return (
    <li className={`check-row check-row-${tone}`}>
      <Icon size={18} strokeWidth={1.8} aria-hidden="true" className={tone === 'info' ? 'spin' : undefined} />
      <div>
        <strong>{title}</strong>
        {detail && <span className="check-row-detail">{detail}</span>}
      </div>
    </li>
  );
}

export default function SubmitPublication() {
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedMode = searchParams.get('mode');
  const activeTab = MODES.some((mode) => mode.id === requestedMode) ? requestedMode : 'doi';
  const activeMode = MODES.find((mode) => mode.id === activeTab);

  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [touched, setTouched] = useState({});
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [source, setSource] = useState('FORM');
  const [fetching, setFetching] = useState(false);
  const [fetchStatus, setFetchStatus] = useState(null);
  const [fetchedMeta, setFetchedMeta] = useState(null);
  const [fetchError, setFetchError] = useState(null);
  const [fieldOrigins, setFieldOrigins] = useState({});
  const [didFetch, setDidFetch] = useState(false);
  const [dupCheck, setDupCheck] = useState({ state: 'idle', exact: null, similar: [] });
  const dupRequestId = useRef(0);
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
    setSearchParams(mode === 'doi' ? {} : { mode });
  }

  function markTouched(field) {
    setTouched((current) => (current[field] ? current : { ...current, [field]: true }));
  }

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    if (didFetch) {
      setFieldOrigins((current) => ({ ...current, [field]: 'manual' }));
    }
  }

  function originLabel(field) {
    if (!didFetch) return null;
    const origin = fieldOrigins[field];
    if (origin === 'manual' && !String(form[field] || '').trim()) return null;
    if (origin === 'crossref') return 'Fetched from Crossref';
    if (origin === 'orcid') return 'Imported from ORCID';
    if (origin === 'manual') return 'Manually entered';
    return null;
  }

  function applyCleanedDoi(raw) {
    const cleaned = cleanDoi(raw);
    if (cleaned && isValidDoi(cleaned) && cleaned !== raw.trim().toLowerCase()) {
      return cleaned;
    }
    return raw;
  }

  // Fetch publication details using DOI and populate form fields.
  async function handleDoiLookup() {
    const doi = cleanDoi(form.doi);
    if (!doi) return;
    if (!isValidDoi(doi)) {
      setErrors((current) => ({ ...current, doi: 'DOI format is invalid' }));
      setFetchStatus('invalid');
      return;
    }
    setForm((current) => ({ ...current, doi }));
    setFetching(true);
    setFetchStatus('loading');
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
      setFieldOrigins({
        doi: 'crossref',
        title: data.title ? 'crossref' : 'manual',
        authors: data.authors?.length ? 'crossref' : 'manual',
        journal: data.journal ? 'crossref' : 'manual',
        year: data.year ? 'crossref' : 'manual',
        department: 'manual',
      });
      setDidFetch(true);
      // Only required fields count as "missing" (journal is optional).
      const missing = [];
      if (!data.title) missing.push('title');
      if (!data.authors?.length) missing.push('authors');
      if (!data.year) missing.push('year');
      setFetchedMeta({
        title: data.title || 'Not provided by Crossref',
        authors: data.authors?.length ? formatAuthors(data.authors, 6) : 'Not provided by Crossref',
        year: data.year ? String(data.year) : 'Not provided by Crossref',
        journal: data.journal || 'Not provided by Crossref',
        missing,
      });
      setSource('CROSSREF');
      setFetchStatus('success');
      setFetchError(null);
      setErrors((current) => ({ ...current, doi: undefined, title: undefined, authors: undefined, year: undefined }));
      toast.push(
        'success',
        missing.length > 0
          ? `Crossref is missing the ${listWords(missing, 'and')}. Please fill ${missing.length > 1 ? 'them' : 'it'} in, choose a department, then submit.`
          : 'Details filled from Crossref. Choose a department, then submit.'
      );
    } catch (error) {
      const status = error?.response?.status;
      if (status === 404) {
        setFetchStatus('not_found');
        setFetchError(null);
        toast.push('error', 'DOI not found');
      } else {
        // Pick a heading that says what actually went wrong; the backend message carries the details.
        let title = 'Could not look up this DOI';
        let detail = getErrorMessage(error, 'Please try again, or enter the details by hand.');
        if (!error?.response) {
          title = "Can't reach the portal's server";
          detail = "The backend isn't responding. Make sure it's running, then try again.";
        } else if (status === 504) {
          title = 'Crossref is taking too long';
        } else if (status === 503) {
          title = 'Unable to connect to Crossref';
        } else if (status >= 500) {
          title = 'Crossref is having problems';
        }
        setFetchStatus('failed');
        setFetchError({ title, detail });
        toast.push('error', title);
      }
    } finally {
      setFetching(false);
    }
  }

  // Live duplicate detection: re-check the register shortly after the DOI or title changes.
  const cleanedFormDoi = cleanDoi(form.doi);
  const dupDoi = isValidDoi(cleanedFormDoi) ? cleanedFormDoi : '';
  const dupTitle = form.title.trim();
  useEffect(() => {
    if (activeTab === 'orcid') return undefined;
    if (!dupDoi && dupTitle.length < 4) {
      dupRequestId.current++;
      setDupCheck({ state: 'idle', exact: null, similar: [] });
      return undefined;
    }
    const controller = new AbortController();
    const requestId = ++dupRequestId.current;
    setDupCheck((current) => ({ ...current, state: 'checking' }));
    const timer = setTimeout(async () => {
      try {
        const result = await findDuplicateCandidates({ doi: dupDoi, title: dupTitle }, controller.signal);
        if (requestId === dupRequestId.current) setDupCheck({ state: 'done', ...result });
      } catch {
        if (controller.signal.aborted) return;
        if (requestId === dupRequestId.current) setDupCheck({ state: 'error', exact: null, similar: [] });
      }
    }, 600);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [dupDoi, dupTitle, activeTab]);

  async function onSubmit(event) {
    event.preventDefault();
    setSubmitAttempted(true);
    const { errors: next, authors, doi } = validatePublicationFields(form);
    setErrors({});
    if (Object.keys(next).length > 0) {
      toast.push('error', 'Please fix the highlighted fields before submitting.');
      return;
    }

    setSaving(true);
    try {
      // Re-check right before saving so a stale result can never let a duplicate through.
      const requestId = ++dupRequestId.current;
      setDupCheck((current) => ({ ...current, state: 'checking' }));
      let duplicates = null;
      try {
        duplicates = await findDuplicateCandidates({ doi, title: form.title });
        if (requestId === dupRequestId.current) setDupCheck({ state: 'done', ...duplicates });
      } catch {
        // If the check cannot run, the backend still rejects duplicate DOIs (409).
        if (requestId === dupRequestId.current) setDupCheck({ state: 'error', exact: null, similar: [] });
      }
      if (duplicates?.exact) {
        toast.push('error', 'This publication already exists in the register. Submission was not sent.');
        return;
      }

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
      if (error?.response?.status === 409) {
        dupRequestId.current++;
        setDupCheck({
          state: 'done',
          exact: { title: form.title.trim(), doi, matchedOn: 'doi' },
          similar: [],
        });
      }
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
      setOrcidError(
        error?.response
          ? getErrorMessage(error, 'ORCID works could not be retrieved. Please try again.')
          : "Can't reach the portal's server. Make sure the backend is running, then try again."
      );
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
  const liveValidation = validatePublicationFields(form);
  const doiEntered = Boolean(form.doi.trim());
  const missingRequired = ['title', 'authors', 'year', 'department'].filter((field) => liveValidation.errors[field]);
  const doiInvalid = Boolean(liveValidation.errors.doi);
  const exactDuplicate = dupCheck.state === 'done' ? dupCheck.exact : null;
  const similarMatches = dupCheck.state === 'done' && !exactDuplicate ? dupCheck.similar : [];
  const readyToSubmit = missingRequired.length === 0 && !doiInvalid && !exactDuplicate;
  const doiVerified = fetchStatus === 'success' && Boolean(fetchedMeta) && didFetch && fieldOrigins.doi === 'crossref';

  // Inline errors appear once a field has been visited (or after a submit attempt).
  function fieldError(field) {
    if (errors[field]) return errors[field];
    if (touched[field] || submitAttempted) return liveValidation.errors[field];
    return undefined;
  }

  function describeRecord(row) {
    const parts = [row.year, row.department].filter(Boolean).join(' · ');
    return `“${row.title}”${parts ? ` (${parts})` : ''}`;
  }

  function renderDoiField(withFetch) {
    const doiError = fieldError('doi');
    return (
      <label>
        {withFetch ? 'DOI' : 'DOI (optional)'}
        <div className={withFetch ? 'doi-input-row' : undefined}>
          <input
            value={form.doi}
            onChange={(event) => {
              const next = applyCleanedDoi(event.target.value);
              update('doi', next);
              setFetchStatus(null);
              if (!didFetch) setSource('FORM');
            }}
            onBlur={(event) => {
              markTouched('doi');
              const cleaned = cleanDoi(event.target.value);
              if (cleaned && cleaned !== form.doi) {
                setForm((current) => ({ ...current, doi: cleaned }));
              }
            }}
            onKeyDown={(event) => {
              if (withFetch && event.key === 'Enter') {
                event.preventDefault();
                if (doiEntered && !fetching) handleDoiLookup();
              }
            }}
            placeholder="10.1038/nature12373"
            aria-invalid={Boolean(doiError)}
          />
          {withFetch && (
            <button
              type="button"
              className="button button-secondary"
              onClick={handleDoiLookup}
              disabled={fetching || !doiEntered}
            >
              {fetching ? 'Fetching metadata…' : 'Fetch from DOI'}
            </button>
          )}
        </div>
        <span className="hint">Paste a DOI or https://doi.org link.</span>
        {originLabel('doi') && <span className="origin-tag">{originLabel('doi')}</span>}
        {doiError && <span className="field-error">{doiError}</span>}
      </label>
    );
  }

  function renderDoiCheck() {
    if (doiInvalid) {
      return <CheckRow tone="error" title="DOI format is invalid" detail="Use a DOI such as 10.1038/nature12373 or a https://doi.org link." />;
    }
    if (dupDoi) {
      return (
        <CheckRow
          tone="success"
          title="DOI validated"
          detail={doiVerified
            ? 'Format checked and verified with Crossref.'
            : activeTab === 'doi'
              ? 'Format looks correct. Use “Fetch from DOI” to verify it with Crossref.'
              : 'Format looks correct.'}
        />
      );
    }
    return activeTab === 'doi' ? (
      <CheckRow tone="pending" title="No DOI entered" detail="Paste a DOI above and fetch its details, or switch to “Enter manually”." />
    ) : (
      <CheckRow tone="pending" title="No DOI provided" detail="A DOI is optional for manual entries." />
    );
  }

  function renderDuplicateChecks() {
    const rows = [];
    if (dupCheck.state === 'idle') {
      rows.push(
        <CheckRow key="dup" tone="pending" title="Duplicate check" detail="Runs automatically once a title or DOI is entered." />
      );
    } else if (dupCheck.state === 'checking') {
      rows.push(<CheckRow key="dup" tone="info" title="Checking for duplicates…" detail="Comparing DOI and title with existing records." />);
    } else if (dupCheck.state === 'error') {
      rows.push(
        <CheckRow
          key="dup"
          tone="warning"
          title="Duplicate check unavailable"
          detail="The register could not be reached. Duplicate DOIs are still blocked when you submit."
        />
      );
    } else if (exactDuplicate) {
      rows.push(
        <CheckRow
          key="dup"
          tone="error"
          title="Exact duplicate found"
          detail={`${describeRecord(exactDuplicate)} is already in the register (same ${exactDuplicate.matchedOn === 'doi' ? 'DOI' : 'title'}). It can’t be submitted again.`}
        />
      );
    } else {
      rows.push(
        <CheckRow key="dup" tone="success" title="No exact duplicate found" detail="We checked DOI and publication title against existing records." />
      );
    }

    if (similarMatches.length > 0) {
      rows.push(
        <CheckRow
          key="similar"
          tone="warning"
          title={similarMatches.length === 1 ? 'Similar publication detected' : `${similarMatches.length} similar publications detected`}
          detail={
            <>
              Make sure this isn’t the same work before submitting:
              <ul className="similar-list">
                {similarMatches.map((row) => (
                  <li key={row.id}>
                    {describeRecord(row)}
                    {row.doi ? <span className="cell-sub"> DOI {row.doi}</span> : null}
                  </li>
                ))}
              </ul>
            </>
          }
        />
      );
    }
    return rows;
  }

  return (
    <div className={activeTab === 'orcid' ? 'submit-wide' : 'narrow'}>
      <p className="eyebrow">Register</p>
      <h1>Submit a publication</h1>
      <p className="lede">
        Choose how you want to add a publication: fetch its details with a DOI, import works from an ORCID profile, or enter them yourself.
      </p>

      <div className="tab-bar" role="tablist" aria-label="Submission method">
        {MODES.map((mode) => {
          const Icon = mode.icon;
          const selected = activeTab === mode.id;
          return (
            <button
              key={mode.id}
              type="button"
              role="tab"
              aria-selected={selected}
              className={selected ? 'tab-button active' : 'tab-button'}
              onClick={() => switchTab(mode.id)}
            >
              <Icon size={16} strokeWidth={1.8} aria-hidden="true" />
              {mode.label}
            </button>
          );
        })}
      </div>
      <p className="mode-description">{activeMode.description}</p>

      {activeTab !== 'orcid' ? (
        <form className="form-card" onSubmit={onSubmit} noValidate>
          {activeTab === 'doi' && renderDoiField(true)}

          {activeTab === 'doi' && fetchStatus === 'loading' && (
            <StatusCard tone="info" title="Fetching metadata…">
              <span className="status-card-kicker">Looking up this DOI on Crossref.</span>
            </StatusCard>
          )}
          {activeTab === 'doi' && fetchStatus === 'not_found' && (
            <StatusCard tone="error" title="DOI not found">
              <span className="status-card-kicker">No Crossref record matched this DOI. You can still enter details manually.</span>
            </StatusCard>
          )}
          {activeTab === 'doi' && fetchStatus === 'failed' && fetchError && (
            <StatusCard tone="error" title={fetchError.title}>
              <span className="status-card-kicker">{fetchError.detail}</span>
            </StatusCard>
          )}
          {activeTab === 'doi' && fetchStatus === 'invalid' && (
            <StatusCard tone="error" title="DOI format is invalid">
              <span className="status-card-kicker">Use a DOI such as 10.1038/nature12373 or a https://doi.org link.</span>
            </StatusCard>
          )}
          {activeTab === 'doi' && fetchStatus === 'success' && fetchedMeta && (
            <StatusCard
              tone={fetchedMeta.missing.length ? 'warning' : 'success'}
              title={fetchedMeta.missing.length ? 'Some details are missing' : 'Metadata fetched successfully'}
            >
              <span className="status-card-kicker">
                {fetchedMeta.missing.length
                  ? `Crossref found this DOI but has no ${listWords(fetchedMeta.missing, 'or')}. Please fill ${fetchedMeta.missing.length > 1 ? 'them' : 'it'} in below.`
                  : 'Source: Crossref · DOI verified'}
              </span>
              <div className="status-card-meta">
                <div><strong>Title:</strong> {fetchedMeta.title}</div>
                <div><strong>Authors:</strong> {fetchedMeta.authors}</div>
                <div><strong>Year:</strong> {fetchedMeta.year}</div>
                <div><strong>Journal:</strong> {fetchedMeta.journal}</div>
              </div>
            </StatusCard>
          )}

          <label>
            Title*
            <input
              value={form.title}
              onChange={(event) => update('title', event.target.value)}
              onBlur={() => markTouched('title')}
              aria-invalid={Boolean(fieldError('title'))}
              maxLength={500}
            />
            {originLabel('title') && <span className="origin-tag">{originLabel('title')}</span>}
            {fieldError('title') && <span className="field-error">{fieldError('title')}</span>}
          </label>
          <label>
            Authors*
            <input
              value={form.authors}
              onChange={(event) => update('authors', event.target.value)}
              onBlur={() => markTouched('authors')}
              placeholder="Separate names with commas"
              aria-invalid={Boolean(fieldError('authors'))}
            />
            <span className="hint">Example: Ada Lovelace, Alan Turing</span>
            {originLabel('authors') && <span className="origin-tag">{originLabel('authors')}</span>}
            {fieldError('authors') && <span className="field-error">{fieldError('authors')}</span>}
          </label>
          <div className="split">
            <label>
              Publication Year*
              <input
                type="number"
                min="1900"
                max="2100"
                step="1"
                value={form.year}
                onChange={(event) => update('year', event.target.value)}
                onBlur={() => markTouched('year')}
                aria-invalid={Boolean(fieldError('year'))}
              />
              {originLabel('year') && <span className="origin-tag">{originLabel('year')}</span>}
              {fieldError('year') && <span className="field-error">{fieldError('year')}</span>}
            </label>
            <label>
              Department*
              <Select
                value={form.department}
                onChange={(next) => {
                  update('department', next);
                  markTouched('department');
                }}
                onBlur={() => markTouched('department')}
                aria-invalid={Boolean(fieldError('department'))}
                placeholder="Select a department"
                options={DEPARTMENT_OPTIONS}
              />
              {originLabel('department') && <span className="origin-tag">{originLabel('department')}</span>}
              {fieldError('department') && <span className="field-error">{fieldError('department')}</span>}
            </label>
          </div>
          <label>
            Journal / Conference
            <input
              value={form.journal}
              onChange={(event) => update('journal', event.target.value)}
              maxLength={300}
            />
            {originLabel('journal') && <span className="origin-tag">{originLabel('journal')}</span>}
          </label>

          {activeTab === 'manual' && renderDoiField(false)}

          <section className="checks-panel" aria-label="Submission checks" aria-live="polite">
            {readyToSubmit ? (
              <StatusCard tone="success" title="Ready to submit">
                <span className="status-card-kicker">All required fields are complete.</span>
              </StatusCard>
            ) : (
              <p className="checks-heading">Submission checks</p>
            )}
            <ul className="check-list">
              {renderDoiCheck()}
              {missingRequired.length === 0 ? (
                <CheckRow tone="success" title="Required information completed" detail="Title, authors, year and department are filled in." />
              ) : (
                <CheckRow
                  tone={submitAttempted ? 'error' : 'pending'}
                  title="Required information incomplete"
                  detail={`Still needed: ${missingRequired.map((field) => FIELD_LABELS[field]).join(', ')}.`}
                />
              )}
              {renderDuplicateChecks()}
            </ul>
          </section>

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
                  <Select
                    value={batchDepartment}
                    onChange={applyBatchDepartment}
                    placeholder="Select a department"
                    options={DEPARTMENT_OPTIONS}
                  />
                </label>
                <div className="orcid-toolbar-actions">
                  <button
                    type="button"
                    className="button button-secondary"
                    disabled={importableCount === 0 || importing}
                    onClick={() => selectAllImportable(selectedCount < importableCount)}
                  >
                    {importableCount === 0 || selectedCount < importableCount ? 'Select all new' : 'Deselect all'}
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
                            <Select
                              value={item.department}
                              disabled={disabled || importing}
                              onChange={(next) => updateOrcidWork(item.key, { department: next })}
                              placeholder="Select department"
                              options={DEPARTMENT_OPTIONS}
                            />
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
