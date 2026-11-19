import { useCallback, useEffect, useMemo, useState } from "react";
import { api, ApiError, resetCsrf } from "./api";
import { AppContext, type AppState, go, useApp, useLiveTick, useRoute } from "./app-context";
import type { Department, Me, Session } from "./types";
import { Login } from "./pages/Login";
import { AcceptInvite } from "./pages/AcceptInvite";
import { Home } from "./pages/Home";
import { Inbox } from "./pages/Inbox";
import { Requests } from "./pages/Requests";
import { Customers } from "./pages/Customers";

interface Loaded {
  session: Session;
  me: Me;
  departments: Department[];
}

const NAV = [
  { path: "inicio", label: "Início", permission: null },
  { path: "atendimento", label: "Atendimento", permission: "conversation:view" },
  { path: "solicitacoes", label: "Solicitações", permission: "request:view" },
  { path: "clientes", label: "Clientes e pets", permission: "customer:view" },
] as const;

export function App() {
  const route = useRoute();
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [checking, setChecking] = useState(true);

  const load = useCallback(async () => {
    try {
      const [session, me, departments] = await Promise.all([
        api.get<Session>("/auth/session"),
        api.get<Me>("/me"),
        api.get<Department[]>("/departments"),
      ]);
      setLoaded({ session, me, departments });
    } catch (e) {
      if (!(e instanceof ApiError) || e.status !== 401) {
        console.error(e);
      }
      setLoaded(null);
    } finally {
      setChecking(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const expired = () => {
      resetCsrf();
      setLoaded(null);
    };
    window.addEventListener("session-expired", expired);
    return () => window.removeEventListener("session-expired", expired);
  }, []);

  const liveTick = useLiveTick(loaded !== null);

  const state = useMemo<AppState | null>(() => {
    if (!loaded) return null;
    const names = new Map(loaded.departments.map((d) => [d.id, d.name]));
    const defaultName = loaded.departments.find((d) => d.isDefault)?.name ?? "Recepção";
    return {
      ...loaded,
      liveTick,
      can: (p) => loaded.session.permissions.includes(p),
      departmentName: (id) => (id ? (names.get(id) ?? "Setor") : defaultName),
      reloadMe: load,
      logout: async () => {
        await api.post("/auth/logout");
        resetCsrf();
        setLoaded(null);
        go("");
      },
    };
  }, [loaded, liveTick, load]);

  if (route[0] === "convite") {
    return <AcceptInvite />;
  }
  if (checking) {
    return <p className="center muted">Carregando…</p>;
  }
  if (!state) {
    return (
      <Login
        onLogin={async () => {
          resetCsrf();
          await load();
          go("inicio");
        }}
      />
    );
  }

  const page = route[0] ?? "inicio";
  const visibleNav = NAV.filter((n) => n.permission === null || state.can(n.permission));

  return (
    <AppContext.Provider value={state}>
      <div className="shell">
        <header className="topbar">
          <span className="brand">Atendimento</span>
          <nav className="nav" aria-label="Menu principal">
            {visibleNav.map((n) => (
              <a key={n.path} href={"#/" + n.path} aria-current={page === n.path ? "page" : undefined}>
                {n.label}
              </a>
            ))}
          </nav>
          <UserMenu />
        </header>
        <main className="content">
          {page === "atendimento" && state.can("conversation:view") ? (
            <Inbox selectedId={route[1]} />
          ) : page === "solicitacoes" && state.can("request:view") ? (
            <Requests selectedId={route[1]} />
          ) : page === "clientes" && state.can("customer:view") ? (
            <Customers selectedId={route[1]} />
          ) : (
            <Home />
          )}
        </main>
      </div>
    </AppContext.Provider>
  );
}

function UserMenu() {
  const { me, reloadMe, logout } = useApp();
  const [busy, setBusy] = useState(false);
  const toggle = async () => {
    setBusy(true);
    try {
      await api.patch("/me/availability", { availability: me.availability === "AVAILABLE" ? "AWAY" : "AVAILABLE" });
      await reloadMe();
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="user">
      <button
        className={"chip " + (me.availability === "AVAILABLE" ? "chip-good" : "chip-muted")}
        onClick={toggle}
        disabled={busy}
        title="Ausentes não recebem atribuições"
      >
        {me.availability === "AVAILABLE" ? "● Disponível" : "○ Ausente"}
      </button>
      <span className="user-name">{me.displayName}</span>
      <button className="link" onClick={() => void logout()}>
        Sair
      </button>
    </div>
  );
}
