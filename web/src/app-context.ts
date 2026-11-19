import { createContext, useContext, useEffect, useRef, useState } from "react";
import type { Department, Me, Session } from "./types";

export interface AppState {
  session: Session;
  me: Me;
  departments: Department[];
  /** Incrementa a cada aviso em tempo real: as telas recarregam quando muda. */
  liveTick: number;
  can: (permission: string) => boolean;
  departmentName: (id: string | null | undefined) => string;
  reloadMe: () => Promise<void>;
  logout: () => Promise<void>;
}

export const AppContext = createContext<AppState | null>(null);

export function useApp(): AppState {
  const state = useContext(AppContext);
  if (!state) {
    throw new Error("useApp fora do AppContext");
  }
  return state;
}

/** Rota atual pelo hash (#/atendimento/ID). Sem roteador: menos dependências (ADR-0022). */
export function useRoute(): string[] {
  const read = () => window.location.hash.replace(/^#\/?/, "").split("?")[0].split("/").filter(Boolean);
  const [route, setRoute] = useState<string[]>(read);
  useEffect(() => {
    const onChange = () => setRoute(read());
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return route;
}

export function go(path: string): void {
  window.location.hash = "#/" + path;
}

export function queryParam(name: string): string | null {
  const query = window.location.hash.split("?")[1] ?? "";
  return new URLSearchParams(query).get(name);
}

/**
 * Canal de avisos (SSE, RF-S29). O navegador reconecta sozinho se cair. Cada aviso só diz "algo mudou": a tela
 * busca de novo pela API, que aplica a permissão.
 */
export function useLiveTick(enabled: boolean): number {
  const [tick, setTick] = useState(0);
  const source = useRef<EventSource | null>(null);
  useEffect(() => {
    if (!enabled || typeof EventSource === "undefined") {
      return;
    }
    const es = new EventSource("/app/api/events", { withCredentials: true });
    es.addEventListener("change", () => setTick((t) => t + 1));
    source.current = es;
    return () => es.close();
  }, [enabled]);
  return tick;
}

/** Carrega dados e recarrega quando {@code deps} mudam. */
export function useLoad<T>(loader: () => Promise<T>, deps: unknown[]): {
  data: T | undefined;
  error: string | null;
  reload: () => void;
  loading: boolean;
} {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [n, setN] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    loader()
      .then((value) => {
        if (active) {
          setData(value);
          setError(null);
        }
      })
      .catch((e: Error) => active && setError(e.message))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, n]);
  return { data, error, loading, reload: () => setN((x) => x + 1) };
}
