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
  if (authors.length === 0) errors.authors = 'At least one author is required';
  const yearNum = Number(values.year);
  if (!values.year) errors.year = 'Year is required';
  else if (!Number.isInteger(yearNum) || yearNum < 1900 || yearNum > 2100) {
    errors.year = 'Year must be a whole number between 1900 and 2100';
  }
  if (!values.department) errors.department = 'Department is required';
  const doi = cleanDoi(values.doi);
  if (doi && !isValidDoi(doi)) errors.doi = 'DOI must look like 10.xxxx/suffix';
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
