import { BarChart3, BookOpen, Library } from 'lucide-react';
import { Link } from 'react-router-dom';

const CARDS = [
  {
    to: '/submit',
    icon: BookOpen,
    title: 'Submit a publication',
    text: 'Add a record by hand, fetch details from a Crossref DOI, or import works from ORCID.',
  },
  {
    to: '/publications',
    icon: Library,
    title: 'Browse the register',
    text: 'Search by title, author, or DOI, and filter by department, year, and status.',
  },
  {
    to: '/dashboard',
    icon: BarChart3,
    title: 'Research dashboard',
    text: 'Review verified output by department, year, and faculty.',
  },
];

export default function Home() {
  return (
    <div className="home">
      <p className="eyebrow">Research Office</p>
      <h1>A quiet record of the university’s scholarship</h1>
      <p className="lede">
        The research office keeps one register of scholarly work across departments.
        Submit a publication, browse what has been recorded, or read the verified totals on the dashboard.
      </p>
      <div className="info-grid">
        {CARDS.map((card) => {
          const Icon = card.icon;
          return (
            <Link key={card.to} to={card.to} className="info-card">
              <Icon size={20} strokeWidth={1.5} aria-hidden="true" />
              <h2>{card.title}</h2>
              <p>{card.text}</p>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
