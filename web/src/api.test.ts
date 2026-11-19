import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, resetCsrf } from "./api";

function respond(status: number, body?: unknown) {
  return Promise.resolve(new Response(body === undefined ? null : JSON.stringify(body), { status }));
}

describe("cliente da API", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
    resetCsrf();
  });

  it("manda o token CSRF do cookie no cabeçalho dos POSTs", async () => {
    document.cookie = "XSRF-TOKEN=token-ficticio";
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(() => respond(200, { ok: true }));

    await api.post("/conversations/x/assume");

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/app/api/conversations/x/assume");
    expect((init?.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("token-ficticio");
    expect(init?.credentials).toBe("same-origin");
  });

  it("transforma ProblemDetail em erro com a mensagem do servidor", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(() =>
      respond(409, { title: "Conflito", detail: "já assumida por Ana", status: 409 }),
    );

    await expect(api.get("/conversations/x")).rejects.toEqual(new ApiError(409, "já assumida por Ana"));
  });
});
