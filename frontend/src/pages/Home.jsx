import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, IdCard, Link2, PenLine } from 'lucide-react';
import { fetchDashboardSummary, fetchRecentPublications, formatAuthors } from '../services/publicationApi';

const ADD_OPTIONS = [
  {
    to: '/submit',
    icon: Link2,
    title: 'Fetch using DOI',
    text: 'Paste a DOI and the details come from Crossref.',
  },
  {
    to: '/submit?mode=orcid',
    icon: IdCard,
    title: 'Import from ORCID',
    text: 'Pull several public works from a researcher’s profile.',
  },
  {
    to: '/submit?mode=manual',
    icon: PenLine,
    title: 'Enter manually',
    text: 'For records without a DOI or ORCID entry.',
  },
];

export default function Home() {
  const [summary, setSummary] = useState(null);
  const [recent, setRecent] = useState([]);
  const [state, setState] = useState('loading');

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([fetchDashboardSummary(controller.signal), fetchRecentPublications(controller.signal)])
      .then(([summaryData, recentData]) => {
        setSummary(summaryData);
        setRecent(recentData || []);
        setState('ready');
      })
      .catch((error) => {
        if (error?.code !== 'ERR_CANCELED') setState('error');
      });
    return () => controller.abort();
  }, []);

  const stats = [
    { label: 'Verified publications', value: summary?.totalPublications },
    { label: 'Departments', value: summary?.departments },
    { label: 'Authors', value: summary?.authors },
    { label: `Published in ${new Date().getFullYear()}`, value: summary?.thisYear },
  ];

  return (
    <div className="home">
      <section className="home-intro">
        <div>
          <p className="eyebrow">Research Office</p>
          <h1>Research Publication Register</h1>
          <p className="lede">
            Record new publications, look up what has already been submitted, and follow verified output by department.
          </p>
        </div>
        <div className="home-actions">
          <Link to="/publications" className="button button-primary">Browse the register</Link>
          <Link to="/dashboard" className="button button-secondary">Open dashboard</Link>
        </div>
      </section>

      <section className="stat-strip" aria-label="Register at a glance" aria-busy={state === 'loading'}>
        {stats.map((stat) => (
          <div key={stat.label} className="stat">
            <p className="stat-value">
              {state === 'loading' ? <span className="skeleton stat-skeleton" /> : state === 'error' ? '–' : stat.value ?? 0}
            </p>
            <p className="stat-label">{stat.label}</p>
          </div>
        ))}
      </section>
      {state === 'error' && (
        <p className="note home-note">Counts are unavailable right now. The register service could not be reached.</p>
      )}

      <div className="home-grid">
        <section className="panel">
          <div className="panel-head">
            <h2>Recently verified</h2>
            <Link to="/publications" className="text-link">
              View all <ArrowRight size={15} strokeWidth={1.8} aria-hidden="true" />
            </Link>
          </div>
          {state === 'loading' ? (
            <div className="skeleton-stack">
              <div className="skeleton skeleton-row" />
              <div className="skeleton skeleton-row" />
              <div className="skeleton skeleton-row" />
            </div>
          ) : recent.length === 0 ? (
            <p className="empty-inline">
              {state === 'error' ? 'Recent publications could not be loaded.' : 'Nothing has been verified yet.'}
            </p>
          ) : (
            <ul className="recent-list">
              {recent.map((item) => (
                <li key={item.id}>
                  <div>
                    <h3>{item.title}</h3>
                    <p title={item.authors.length > 4 ? `${item.authors.length.toLocaleString()} authors` : undefined}>{formatAuthors(item.authors)}</p>
                    <p className="cell-sub">
                      {[item.journal, item.year, item.department].filter(Boolean).join(' · ')}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <aside className="panel add-panel" aria-labelledby="add-heading">
          <h2 id="add-heading">Add a publication</h2>
          <ul className="add-list">
            {ADD_OPTIONS.map((option) => {
              const Icon = option.icon;
              return (
                <li key={option.title}>
                  <Link to={option.to} className="add-option">
                    <Icon size={18} strokeWidth={1.7} aria-hidden="true" />
                    <span>
                      <strong>{option.title}</strong>
                      <span>{option.text}</span>
                    </span>
                    <ArrowRight className="add-arrow" size={16} strokeWidth={1.8} aria-hidden="true" />
                  </Link>
                </li>
              );
            })}
          </ul>
        </aside>
      </div>
    </div>
  );
}
