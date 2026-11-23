import axios from 'axios';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
});

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

export function crossrefUrl(raw) {
  const doi = cleanDoi(raw);
  const path = doi.split('/').map(encodeURIComponent).join('/');
  return `/api/crossref/${path}`;
}

export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
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
