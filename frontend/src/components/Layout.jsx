import { NavLink, Outlet } from 'react-router-dom';

const LINKS = [
  { to: '/', label: 'Home', end: true },
  { to: '/submit', label: 'Submit Publication' },
  { to: '/publications', label: 'Publications' },
  { to: '/dashboard', label: 'Dashboard' },
];

export default function Layout() {
  return (
    <div className="shell">
      <a className="skip-link" href="#content">Skip to content</a>
      <header className="site-header">
        <div className="header-inner">
          <NavLink to="/" className="brand" end>
            <svg className="crest" viewBox="0 0 48 48" aria-hidden="true">
              <path
                d="M24 3.5 42 10.2v12.4c0 10.2-7.1 17.6-18 21.9C13.1 40.2 6 32.8 6 22.6V10.2L24 3.5Z"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
              />
              <path
                d="M15 23.2c2.6.9 5.4.9 9 0 3.6.9 6.4.9 9 0"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.4"
                strokeLinecap="round"
              />
              <path
                d="M15 23.2v8.2c2.6.9 5.4.9 9 0 3.6.9 6.4.9 9 0v-8.2"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.4"
                strokeLinejoin="round"
              />
              <path d="M24 23.2v8.2" stroke="currentColor" strokeWidth="1.4" />
            </svg>
            <span className="brand-text">
              <span className="brand-kicker">University Research Office</span>
              <span className="brand-title">University Research Publication Portal</span>
            </span>
          </NavLink>
          <nav className="site-nav" aria-label="Primary">
            {LINKS.map((link) => (
              <NavLink key={link.to} to={link.to} end={link.end}>
                {link.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>
      <main id="content" className="page">
        <Outlet />
      </main>
      <footer className="site-footer">
        <p>© {new Date().getFullYear()} University Research Office</p>
      </footer>
    </div>
  );
}
