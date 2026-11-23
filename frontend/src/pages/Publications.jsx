import { useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { api, errorMessage } from '../api';
import { DEPARTMENTS } from '../constants';
import StatusChip from '../components/StatusChip';

const STATUSES = [
  { value: '', label: 'All statuses' },
  { value: 'VERIFIED', label: 'Verified' },
  { value: 'IN_REVIEW', label: 'In review' },
  { value: 'REJECTED', label: 'Rejected' },
];

export default function Publications() {
  const [search, setSearch] = useState('');
  const [debounced, setDebounced] = useState('');
  const [department, setDepartment] = useState('');
  const [year, setYear] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    setPage(0);
  }, [debounced, department, year, status]);

  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      setLoading(true);
      setError('');
      try {
        const params = { page, size: 8 };
        if (debounced) params.search = debounced;
        if (department) params.department = department;
        if (year) params.year = year;
        if (status) params.status = status;
        const response = await api.get('/api/publications', { params, signal: controller.signal });
        setData(response.data);
      } catch (err) {
        if (err.code !== 'ERR_CANCELED') {
          setError(errorMessage(err, 'The register could not be loaded.'));
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [debounced, department, year, status, page, reload]);

  const rows = data?.content || [];
  const total = data?.totalElements || 0;
  const from = total === 0 ? 0 : page * (data?.size || 8) + 1;
  const to = Math.min(total, (page + 1) * (data?.size || 8));

  return (
    <div>
      <p className="eyebrow">Register</p>
      <h1>Publications</h1>
      <p className="lede">Every submitted record is listed here, including those still in review or rejected.</p>

      <div className="filter-bar">
        <label className="search-field">
          <span className="sr-only">Search title, author, or DOI</span>
          <Search size={16} strokeWidth={1.5} aria-hidden="true" />
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search title, author, or DOI"
          />
        </label>
        <label>
          <span className="sr-only">Department</span>
          <select value={department} onChange={(event) => setDepartment(event.target.value)}>
            <option value="">All departments</option>
            {DEPARTMENTS.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </label>
        <label>
          <span className="sr-only">Year</span>
          <input
            type="number"
            min="1900"
            max="2100"
            step="1"
            value={year}
            onChange={(event) => setYear(event.target.value)}
            placeholder="All years"
          />
        </label>
        <label>
          <span className="sr-only">Status</span>
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            {STATUSES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
          </select>
        </label>
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
            <p>Try a broader search, or clear one of the filters.</p>
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
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td data-label="Title">
                    <strong>{row.title}</strong>
                    {row.journal && <span className="cell-sub">{row.journal}</span>}
                    {row.doi && <span className="cell-sub">DOI {row.doi}</span>}
                  </td>
                  <td data-label="Authors">{row.authors.join(', ')}</td>
                  <td data-label="Department"><span className="tag">{row.department}</span></td>
                  <td data-label="Year">{row.year}</td>
                  <td data-label="Status"><StatusChip status={row.status} /></td>
                </tr>
              ))}
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
    </div>
  );
}
