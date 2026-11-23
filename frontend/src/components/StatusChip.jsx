const LABELS = {
  VERIFIED: 'Verified',
  IN_REVIEW: 'In review',
  REJECTED: 'Rejected',
};

export default function StatusChip({ status }) {
  const key = status || 'IN_REVIEW';
  return <span className={`chip chip-${key.toLowerCase()}`}>{LABELS[key] || key}</span>;
}
