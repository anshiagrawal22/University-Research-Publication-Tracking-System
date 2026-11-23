import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, cleanDoi, crossrefUrl, errorMessage, isValidDoi } from '../api';
import { DEPARTMENTS } from '../constants';
import { useToast } from '../components/Toast';

const EMPTY = {
  title: '',
  authors: '',
  doi: '',
  year: '',
  department: '',
  journal: '',
};

export default function Submit() {
  const toast = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [source, setSource] = useState('FORM');
  const [fetching, setFetching] = useState(false);
  const [saving, setSaving] = useState(false);

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  function validate(values) {
    const next = {};
    if (!values.title.trim()) next.title = 'Title is required';
    const authors = values.authors.split(',').map((name) => name.trim()).filter(Boolean);
    if (authors.length === 0) next.authors = 'At least one author is required';
    if (!values.year) next.year = 'Year is required';
    else if (!Number.isInteger(Number(values.year)) || Number(values.year) < 1900 || Number(values.year) > 2100) {
      next.year = 'Year must be a whole number between 1900 and 2100';
    }
    if (!values.department) next.department = 'Department is required';
    const doi = cleanDoi(values.doi);
    if (doi && !isValidDoi(doi)) next.doi = 'DOI must look like 10.xxxx/suffix';
    return { next, authors, doi };
  }

  async function fetchDoi() {
    const doi = cleanDoi(form.doi);
    if (!isValidDoi(doi)) {
      setErrors((current) => ({ ...current, doi: 'Enter a DOI such as 10.1038/nature12373' }));
      return;
    }
    setFetching(true);
    try {
      const { data } = await api.get(crossrefUrl(doi));
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
      toast.push('success', 'Details filled from Crossref. Choose a department, then submit.');
    } catch (error) {
      toast.push('error', errorMessage(error, 'Crossref could not be reached. You can still enter the details.'));
    } finally {
      setFetching(false);
    }
  }

  async function onSubmit(event) {
    event.preventDefault();
    const { next, authors, doi } = validate(form);
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setSaving(true);
    try {
      await api.post('/api/publications', {
        title: form.title.trim(),
        authors,
        doi: doi || null,
        year: Number(form.year),
        department: form.department,
        journal: form.journal.trim() || null,
        source,
      });
      toast.push('success', 'Publication added to the register.');
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
      toast.push('error', errorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="narrow">
      <p className="eyebrow">Register</p>
      <h1>Submit a publication</h1>
      <p className="lede">
        Enter the record yourself, or fetch the title, authors, journal, and year from Crossref with a DOI.
        Department is always chosen here, because Crossref does not know it.
      </p>
      <form className="form-card" onSubmit={onSubmit} noValidate>
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
        <div className="doi-row">
          <label>
            DOI
            <input
              value={form.doi}
              onChange={(event) => {
                update('doi', event.target.value);
                setSource('FORM');
              }}
              placeholder="10.xxxx/suffix"
              aria-invalid={Boolean(errors.doi)}
            />
            {errors.doi && <span className="field-error">{errors.doi}</span>}
          </label>
          <button type="button" className="button button-secondary" onClick={fetchDoi} disabled={fetching}>
            {fetching ? 'Fetching…' : 'Fetch from DOI'}
          </button>
        </div>
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
    </div>
  );
}
