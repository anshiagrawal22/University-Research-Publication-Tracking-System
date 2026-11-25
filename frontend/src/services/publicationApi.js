import axios from 'axios';

export const DEPARTMENTS = [
  'Computer Science',
  'Biology',
  'Physics',
  'Economics',
  'Mechanical Engineering',
];

// Base URL defaults to same-origin Vite proxy; overridden when VITE_API_URL is set.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
});

// Strip common DOI URL/label prefixes and trailing slashes before validation or lookup.
export function cleanDoi(raw) {
  if (!raw) return '';
  let doi = raw.trim().toLowerCase();
  if (!doi) return '';
  let stripped = true;
  const prefixes = [
    'https://dx.doi.org/',
    'http://dx.doi.org/',
    'https://doi.org/',
    'http://doi.org/',
    'doi:',
  ];
  while (stripped) {
    stripped = false;
    for (const prefix of prefixes) {
      if (doi.startsWith(prefix)) {
        doi = doi.slice(prefix.length).trim();
        stripped = true;
        break;
      }
    }
  }
  while (doi.endsWith('/')) {
    doi = doi.slice(0, -1).trim();
  }
  return doi;
}

// Display helper: large collaborations can list 1,000+ authors, so show the first few
// and summarise the rest ("A, B, C + 1,009 more"). Use the full list for editing/saving.
export function formatAuthors(authors, max = 3) {
  const list = (authors || []).filter(Boolean);
  if (list.length <= max + 1) return list.join(', ');
  return `${list.slice(0, max).join(', ')} + ${(list.length - max).toLocaleString()} more`;
}

export function isValidDoi(doi) {
  return /^10\.\d{4,9}\/\S+$/.test(doi);
}

// Normalize ORCID iD URLs or bare IDs to canonical 0000-0000-0000-0000 format.
export function cleanOrcid(raw) {
  if (!raw) return '';
  let value = raw.trim().toUpperCase();
  const prefixes = [
    'HTTPS://ORCID.ORG/',
    'HTTP://ORCID.ORG/',
    'ORCID:',
  ];
  for (const prefix of prefixes) {
    if (value.startsWith(prefix)) {
      value = value.slice(prefix.length).trim();
      break;
    }
  }
  while (value.endsWith('/')) {
    value = value.slice(0, -1).trim();
  }
  return value;
}

export function isValidOrcid(orcid) {
  return /^\d{4}-\d{4}-\d{4}-\d{3}[\dX]$/.test(orcid);
}

// Validate required publication fields and optional DOI format on the client.
export function validatePublicationFields(values) {
  const errors = {};
  if (!values.title.trim()) errors.title = 'Title is required';
  const authors = values.authors.split(',').map((name) => name.trim()).filter(Boolean);
  if (authors.length === 0) errors.authors = 'Authors are required';
  const yearNum = Number(values.year);
  if (!values.year) errors.year = 'Valid publication year required';
  else if (!Number.isInteger(yearNum) || yearNum < 1900 || yearNum > 2100) {
    errors.year = 'Valid publication year required';
  }
  if (!values.department) errors.department = 'Department must be selected';
  const doi = cleanDoi(values.doi);
  if (doi && !isValidDoi(doi)) errors.doi = 'DOI format is invalid';
  return { errors, authors, doi, yearNum };
}

// Fetch publication metadata from Crossref using DOI.
export async function fetchPublicationByDoi(rawDoi) {
  const doi = cleanDoi(rawDoi);
  const path = doi.split('/').map(encodeURIComponent).join('/');
  const { data } = await api.get(`/api/crossref/${path}`);
  return data;
}

// Fetch public works for an ORCID researcher (enriched with Crossref metadata by the backend).
export async function fetchOrcidWorks(orcidId) {
  const { data } = await api.get(`/api/orcid/${encodeURIComponent(orcidId)}/works`);
  return data;
}

export async function fetchPublications(params, signal) {
  const { data } = await api.get('/api/publications', { params, signal });
  return data;
}

const TITLE_STOPWORDS = new Set([
  'a', 'an', 'and', 'are', 'as', 'at', 'by', 'for', 'from', 'in', 'into', 'is', 'of', 'on',
  'or', 'the', 'to', 'with', 'via', 'using', 'its', 'their', 'towards', 'toward', 'based',
]);

// Lowercase, strip punctuation and collapse whitespace so titles compare reliably.
export function normalizeTitle(title) {
  return String(title || '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}

function titleTokens(title) {
  return normalizeTitle(title)
    .split(' ')
    .filter((word) => word.length > 1 && !TITLE_STOPWORDS.has(word));
}

// Dice coefficient over significant title words (0 = unrelated, 1 = same words).
export function titleSimilarity(a, b) {
  const left = new Set(titleTokens(a));
  const right = new Set(titleTokens(b));
  if (left.size === 0 || right.size === 0) return 0;
  let shared = 0;
  left.forEach((word) => {
    if (right.has(word)) shared++;
  });
  return (2 * shared) / (left.size + right.size);
}

export const SIMILAR_TITLE_THRESHOLD = 0.6;

// Check existing records for an exact duplicate (same DOI or same title) and similar titles.
// Uses the existing list/search endpoint, so no backend changes are required.
export async function findDuplicateCandidates({ doi, title, excludeId } = {}, signal) {
  const cleanedDoi = doi && isValidDoi(cleanDoi(doi)) ? cleanDoi(doi) : '';
  const trimmedTitle = String(title || '').trim();
  const queries = new Set();
  if (cleanedDoi) queries.add(cleanedDoi);
  if (trimmedTitle) {
    queries.add(trimmedTitle);
    // Also search on the most distinctive words so reworded titles are still found.
    [...new Set(titleTokens(trimmedTitle))]
      .sort((a, b) => b.length - a.length)
      .slice(0, 2)
      .filter((word) => word.length >= 4)
      .forEach((word) => queries.add(word));
  }
  if (queries.size === 0) return { exact: null, similar: [] };

  const pages = await Promise.all(
    [...queries].map((search) => fetchPublications({ search, size: 50, page: 0 }, signal))
  );
  const byId = new Map();
  pages.forEach((page) => (page?.content || []).forEach((row) => byId.set(row.id, row)));
  if (excludeId != null) byId.delete(excludeId);

  const normalized = normalizeTitle(trimmedTitle);
  let exact = null;
  const similar = [];
  byId.forEach((row) => {
    const sameDoi = cleanedDoi && row.doi && cleanDoi(row.doi) === cleanedDoi;
    const sameTitle = normalized && normalizeTitle(row.title) === normalized;
    if (sameDoi || sameTitle) {
      if (!exact || sameDoi) exact = { ...row, matchedOn: sameDoi ? 'doi' : 'title' };
      return;
    }
    if (trimmedTitle) {
      const score = titleSimilarity(trimmedTitle, row.title);
      if (score >= SIMILAR_TITLE_THRESHOLD) similar.push({ ...row, score });
    }
  });
  similar.sort((a, b) => b.score - a.score);
  return { exact, similar: similar.slice(0, 3) };
}

// Load distinct filter dropdown options from the database and merge default departments.
export async function fetchFilterOptions() {
  const { data } = await api.get('/api/publications/filters');
  const dbDepts = data?.departments || [];
  const departments = Array.from(new Set([...DEPARTMENTS, ...dbDepts])).sort((a, b) => a.localeCompare(b));
  return {
    departments,
    years: data?.years || [],
    faculties: data?.faculties || [],
  };
}

export async function createPublication(payload) {
  const { data } = await api.post('/api/publications', payload);
  return data;
}

export async function updatePublication(id, payload) {
  const { data } = await api.put(`/api/publications/${id}`, payload);
  return data;
}

export async function deletePublication(id) {
  await api.delete(`/api/publications/${id}`);
}

// Lightweight calls used by the home page overview.
export async function fetchDashboardSummary(signal) {
  const { data } = await api.get('/api/dashboard/summary', { signal });
  return data;
}

export async function fetchRecentPublications(signal) {
  const { data } = await api.get('/api/dashboard/recent', { signal });
  return data;
}

// Retrieve verified publication totals and chart breakdowns for the dashboard.
export async function fetchDashboardData(params, signal) {
  const [summaryRes, deptRes, yearRes, facultyRes, recentRes] = await Promise.all([
    api.get('/api/dashboard/summary', { params, signal }),
    api.get('/api/dashboard/by-department', { params, signal }),
    api.get('/api/dashboard/by-year', { params, signal }),
    api.get('/api/dashboard/by-faculty', { params, signal }),
    api.get('/api/dashboard/recent', { params, signal }),
  ]);
  return {
    summary: summaryRes.data,
    departments: deptRes.data,
    years: yearRes.data,
    faculty: facultyRes.data,
    recent: recentRes.data,
  };
}

export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  const data = error?.response?.data;
  if (data?.fieldErrors?.length) {
    return data.fieldErrors.map((item) => item.message).join(' ');
  }
  if (typeof data?.message === 'string' && data.message) {
    return data.message;
  }
  if (error?.code === 'ECONNABORTED') {
    return 'The request timed out. Please try again.';
  }
  return fallback;
}
