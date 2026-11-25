import { useEffect, useState } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import Select from '../components/Select';
import { DEPARTMENTS, fetchDashboardData, fetchFilterOptions, formatAuthors, getErrorMessage } from '../services/publicationApi';

const CHART_COLOR = '#1F3A4D';
const CHART_FILL = '#7A9E8E';
const AXIS_TICK = { fill: '#5B6770', fontSize: 12 };

const tooltipStyle = {
  background: '#ffffff',
  border: '1px solid #E4E0D6',
  borderRadius: 8,
  color: '#2B2F33',
  boxShadow: '0 8px 24px rgba(31, 58, 77, 0.06)',
};

function useReducedMotion() {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const update = () => setReduced(media.matches);
    update();
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);
  return reduced;
}

export default function Dashboard() {
  const reducedMotion = useReducedMotion();
  const [department, setDepartment] = useState('');
  const [faculty, setFaculty] = useState('');
  const [fromYear, setFromYear] = useState('');
  const [toYear, setToYear] = useState('');
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [summary, setSummary] = useState(null);
  const [departments, setDepartments] = useState([]);
  const [years, setYears] = useState([]);
  const [facultyCounts, setFacultyCounts] = useState([]);
  const [recent, setRecent] = useState([]);

  const [filterOptions, setFilterOptions] = useState({
    departments: DEPARTMENTS,
    years: [],
    faculties: [],
  });

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
      const params = {};
      if (department) params.department = department;
      if (faculty) params.faculty = faculty;
      if (fromYear) params.fromYear = fromYear;
      if (toYear) params.toYear = toYear;
      try {
        const result = await fetchDashboardData(params, controller.signal);
        setSummary(result.summary);
        setDepartments(result.departments);
        setYears(result.years);
        setFacultyCounts(result.faculty);
        setRecent(result.recent);
      } catch (err) {
        if (err.code !== 'ERR_CANCELED') {
          setError(getErrorMessage(err, 'The dashboard could not be loaded.'));
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [department, faculty, fromYear, toYear, reload]);

  const animate = !reducedMotion;
  const empty = !loading && !error && summary && summary.totalPublications === 0;
  const hasFilters = Boolean(department || faculty || fromYear || toYear);

  return (
    <div>
      <div className="page-head">
        <p className="eyebrow">Research Office</p>
        <h1>Research Dashboard</h1>
        <p className="lede">Verified publication records of the university</p>
        <p className="note">Only records with status Verified are counted. In review and rejected records stay in the register.</p>
      </div>

      <div className="filter-bar dash-filters" id="overview">
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
          <span>From year</span>
          <Select
            value={fromYear}
            onChange={setFromYear}
            options={[{ value: '', label: 'Any' }, ...[...filterOptions.years].sort((a, b) => a - b).map((item) => ({ value: String(item), label: String(item) }))]}
          />
        </label>
        <label>
          <span>To year</span>
          <Select
            value={toYear}
            onChange={setToYear}
            options={[{ value: '', label: 'Any' }, ...filterOptions.years.map((item) => ({ value: String(item), label: String(item) }))]}
          />
        </label>
        <div className="filter-reset">
          <button
            type="button"
            className="button button-ghost"
            disabled={!hasFilters}
            onClick={() => {
              setDepartment('');
              setFaculty('');
              setFromYear('');
              setToYear('');
            }}
          >
            Clear filters
          </button>
        </div>
      </div>

      <div className="dash-main">
          {error && (
            <div className="banner banner-error" role="alert">
              <p>{error}</p>
              <button type="button" className="button button-secondary" onClick={() => setReload((current) => current + 1)}>
                Try again
              </button>
            </div>
          )}

          {loading && !summary ? (
            <DashboardSkeleton />
          ) : summary ? (
            <>
              <section className="stat-strip" aria-label="Summary" aria-busy={loading}>
                <Stat value={summary?.totalPublications ?? 0} label="Verified publications" />
                <Stat value={summary?.departments ?? 0} label="Departments" />
                <Stat value={summary?.authors ?? 0} label="Authors" />
                <Stat value={summary?.thisYear ?? 0} label={`Published in ${new Date().getFullYear()}`} />
              </section>

              {empty ? (
                <div className="empty card-empty">
                  <h2>No verified publications in this view</h2>
                  <p>Widen the year range or clear the department and faculty filters. New submissions appear here once they are verified.</p>
                </div>
              ) : (
                <>
                  <div className="chart-grid">
                    <section className="panel" id="by-department">
                      <h2>Publications by department</h2>
                      <ChartFrame empty={departments.length === 0} emptyText="No departments in this range.">
                        <ResponsiveContainer width="100%" height={300}>
                          <BarChart data={departments} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
                            <CartesianGrid stroke="#ECE8DF" vertical={false} />
                            <XAxis dataKey="department" interval={0} angle={-28} textAnchor="end" height={78} tick={AXIS_TICK} />
                            <YAxis allowDecimals={false} width={32} tick={AXIS_TICK} />
                            <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'rgba(31, 58, 77, 0.04)' }} />
                            <Bar dataKey="count" name="Publications" fill={CHART_COLOR} radius={[4, 4, 0, 0]} maxBarSize={40} isAnimationActive={animate} animationDuration={400} />
                          </BarChart>
                        </ResponsiveContainer>
                      </ChartFrame>
                    </section>

                    <section className="panel" id="by-year">
                      <h2>Publications by year</h2>
                      <ChartFrame empty={years.length === 0} emptyText="No years in this range.">
                        <ResponsiveContainer width="100%" height={300}>
                          <AreaChart data={years} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                            <CartesianGrid stroke="#ECE8DF" vertical={false} />
                            <XAxis dataKey="year" tick={AXIS_TICK} />
                            <YAxis allowDecimals={false} width={32} tick={AXIS_TICK} />
                            <Tooltip contentStyle={tooltipStyle} />
                            <Area
                              type="monotone"
                              dataKey="count"
                              name="Publications"
                              stroke={CHART_COLOR}
                              fill={CHART_FILL}
                              fillOpacity={0.22}
                              strokeWidth={2}
                              isAnimationActive={animate}
                              animationDuration={400}
                            />
                          </AreaChart>
                        </ResponsiveContainer>
                      </ChartFrame>
                    </section>
                  </div>

                  <div className="chart-grid lower-grid">
                    <section className="panel" id="by-faculty">
                      <h2>Top faculty</h2>
                      <p className="panel-note">Authors with the most verified publications. Limited to ten.</p>
                      <ChartFrame empty={facultyCounts.length === 0} emptyText="No faculty in this range." height={Math.max(220, facultyCounts.length * 36)}>
                        <ResponsiveContainer width="100%" height={Math.max(220, facultyCounts.length * 36)}>
                          <BarChart data={facultyCounts} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 4 }}>
                            <CartesianGrid stroke="#ECE8DF" horizontal={false} />
                            <XAxis type="number" allowDecimals={false} tick={AXIS_TICK} />
                            <YAxis type="category" dataKey="faculty" width={148} tick={{ fill: '#2B2F33', fontSize: 12 }} />
                            <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'rgba(31, 58, 77, 0.04)' }} />
                            <Bar dataKey="count" name="Publications" fill={CHART_COLOR} radius={[0, 4, 4, 0]} maxBarSize={16} isAnimationActive={animate} animationDuration={400} />
                          </BarChart>
                        </ResponsiveContainer>
                      </ChartFrame>
                    </section>

                    <section className="panel" id="recent">
                      <h2>Recently added publications</h2>
                      {recent.length === 0 ? (
                        <p className="empty-inline">Nothing has been verified in this range yet.</p>
                      ) : (
                        <ul className="recent-list">
                          {recent.map((item) => (
                            <li key={item.id}>
                              <div>
                                <h3>{item.title}</h3>
                                <p title={item.authors.length > 4 ? `${item.authors.length.toLocaleString()} authors` : undefined}>{formatAuthors(item.authors)}</p>
                                <p className="cell-sub">{[item.journal || 'Journal not recorded', item.year, item.department].filter(Boolean).join(' · ')}</p>
                              </div>
                            </li>
                          ))}
                        </ul>
                      )}
                    </section>
                  </div>
                </>
              )}
            </>
          ) : null}
      </div>
    </div>
  );
}

function Stat({ value, label }) {
  return (
    <div className="stat">
      <p className="stat-value">{value}</p>
      <p className="stat-label">{label}</p>
    </div>
  );
}

function ChartFrame({ empty, emptyText, children }) {
  if (empty) return <p className="empty-inline">{emptyText}</p>;
  return children;
}

function DashboardSkeleton() {
  return (
    <div aria-hidden="true">
      <div className="skeleton stat-strip-skeleton" />
      <div className="chart-grid">
        <div className="skeleton chart-skeleton" />
        <div className="skeleton chart-skeleton" />
      </div>
    </div>
  );
}
