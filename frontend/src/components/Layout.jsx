import { NavLink, Outlet } from 'react-router-dom';
import Crest from './Crest';

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
            <Crest />
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
