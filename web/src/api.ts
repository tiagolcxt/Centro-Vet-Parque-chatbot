// Cliente da API da equipe (/app/api, ADR-0024): sessão por cookie e CSRF no padrão de SPA (cookie XSRF-TOKEN,
// cabeçalho X-XSRF-TOKEN). Erros chegam como ProblemDetail e viram ApiError com a mensagem em português do servidor.

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    /** Problemas de um documento inválido (configuração), no formato "secao.campo: motivo". */
    readonly problems: string[] = [],
  ) {
    super(message);
  }
}

function xsrfToken(): string | null {
  const match = document.cookie.split("; ").find((c) => c.startsWith("XSRF-TOKEN="));
  return match ? decodeURIComponent(match.substring("XSRF-TOKEN=".length)) : null;
}

let csrfReady: Promise<void> | null = null;

/** Garante o cookie CSRF antes do primeiro POST. */
export function ensureCsrf(): Promise<void> {
  if (xsrfToken()) {
    return Promise.resolve();
  }
  csrfReady ??= fetch("/app/api/auth/csrf", { credentials: "same-origin" }).then(() => undefined);
  return csrfReady;
}

/** Chamado depois do login e do logout: a sessão nova pede um token novo. */
export function resetCsrf(): void {
  csrfReady = null;
}

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (method !== "GET") {
    await ensureCsrf();
    const token = xsrfToken();
    if (token) {
      headers["X-XSRF-TOKEN"] = token;
    }
  }
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const response = await fetch("/app/api" + path, {
    method,
    headers,
    credentials: "same-origin",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const json = text ? JSON.parse(text) : undefined;
  if (response.status === 401 && !path.startsWith("/auth/")) {
    // Sessão encerrada (servidor reiniciado, conta desativada): a aplicação volta ao login.
    window.dispatchEvent(new Event("session-expired"));
  }
  if (!response.ok) {
    const detail = json?.detail ?? json?.title ?? `Erro ${response.status}`;
    const fields: string[] | undefined = json?.fields;
    throw new ApiError(response.status, fields?.length ? `${detail} (${fields.join("; ")})` : detail, json?.problems ?? []);
  }
  return json as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body: unknown = {}) => request<T>("POST", path, body),
  put: <T>(path: string, body: unknown) => request<T>("PUT", path, body),
  patch: <T>(path: string, body: unknown) => request<T>("PATCH", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
