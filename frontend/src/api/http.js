/**
 * Talking to the Spring Boot API over fetch.
 *
 * The session is an HttpOnly cookie the browser manages, so this code never sees a token. The one
 * thing it adds is the CSRF token: the server sets an XSRF-TOKEN cookie and expects its value back in
 * the X-XSRF-TOKEN header on every change. Another site can make a browser send cookies, but can't
 * read them to copy into a header.
 */

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/** A failed request, with the server's own sentence for the UI in `message`. */
export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/** The human-readable message from anything an API call threw. */
export function errorMessage(err) {
  if (err instanceof ApiError) return err.message;
  return err instanceof Error ? err.message : 'Something went wrong.';
}

function readCookie(name) {
  const match = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : '';
}

/**
 * Sends one request to the server and resolves to its parsed JSON body (or undefined for 204), or
 * rejects with an ApiError. Status 0 means the server couldn't be reached at all.
 */
export async function fetchTransport(method, url, body) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (!SAFE_METHODS.has(method)) headers['X-XSRF-TOKEN'] = readCookie('XSRF-TOKEN');

  let response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
    });
  } catch {
    throw new ApiError(0, "Can't reach the Brew Focus server.");
  }

  if (response.status === 204) return undefined;
  const text = await response.text();
  let data;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    data = undefined;
  }
  if (!response.ok) throw new ApiError(response.status, data?.error ?? `Request failed (${response.status})`);
  return data;
}
