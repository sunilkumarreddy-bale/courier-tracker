let csrf;
export async function request(path, options = {}) {
  const method = options.method || "GET";
  const headers = { ...options.headers };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const response = await fetch("/api/auth/csrf", { credentials: "include" });
    if (!response.ok) throw new Error("Could not establish a secure session.");
    csrf = await response.json();
    headers[csrf.headerName] = csrf.token;
  }
  const response = await fetch(`/api${path}`, {
    ...options,
    method,
    headers,
    credentials: "include",
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  if (response.status === 204) return null;
  const data = await response
    .json()
    .catch(() => ({ message: "The server returned an unexpected response." }));
  if (!response.ok) {
    const error = new Error(data.message || "Request failed.");
    error.status = response.status;
    error.fields = data.fields;
    throw error;
  }
  return data;
}
