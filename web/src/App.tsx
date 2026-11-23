import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  ClipboardList,
  Home as HomeIcon,
  Keyboard,
  LogOut,
  MessageSquare,
  Moon,
  MoreHorizontal,
  PanelLeftClose,
  PanelLeftOpen,
  PawPrint,
  Search,
  Settings,
  ShieldCheck,
  Stethoscope,
  Sun,
  UserCog,
  Users,
} from "lucide-react";
import { api, ApiError, resetCsrf } from "./api";
import { AppContext, type AppState, go, useApp, useLiveTick, useLoad, useRoute } from "./app-context";
import type { CustomerItem, Department, InboxItem, Me, Session } from "./types";
import { Login } from "./pages/Login";
import { AcceptInvite } from "./pages/AcceptInvite";
import { Home } from "./pages/Home";
import { Inbox } from "./pages/Inbox";
import { Requests } from "./pages/Requests";
import { Customers } from "./pages/Customers";
import { Config } from "./pages/Config";
import { Team } from "./pages/Team";
import { Audit } from "./pages/Audit";
import { Dialog, SHORTCUTS, ToastProvider, useHotkeys, useTheme, useToast } from "./ui";

interface Loaded {
  session: Session;
  me: Me;
  departments: Department[];
}

const NAV = [
  { path: "inicio", label: "Início", icon: HomeIcon, permission: null },
  { path: "atendimento", label: "Atendimento", icon: MessageSquare, permission: "conversation:view" },
  { path: "solicitacoes", label: "Solicitações", icon: ClipboardList, permission: "request:view" },
  { path: "clientes", label: "Clientes e pets", icon: Users, permission: "customer:view" },
  { path: "configuracao", label: "Configuração", icon: Settings, permission: "config:edit" },
  { path: "equipe", label: "Equipe", icon: UserCog, permission: "team:manage" },
  { path: "auditoria", label: "Auditoria", icon: ShieldCheck, permission: "audit:view" },
] as const;

export function App() {
  return (
    <ToastProvider>
      <Root />
    </ToastProvider>
  );
}

function Root() {
  const route = useRoute();
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [checking, setChecking] = useState(true);
  useTheme();

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
  return (
    <AppContext.Provider value={state}>
      <Shell route={route} />
    </AppContext.Provider>
  );
}

function Shell({ route }: { route: string[] }) {
  const state = useApp();
  const page = route[0] ?? "inicio";
  const visibleNav = NAV.filter((n) => n.permission === null || state.can(n.permission));
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem("sidebar") === "collapsed";
    } catch {
      return false;
    }
  });
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [moreOpen, setMoreOpen] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);

  useHotkeys({
    "/": () => searchRef.current?.focus(),
    "?": () => setShortcutsOpen(true),
  });

  const toggleSidebar = () => {
    const next = !collapsed;
    setCollapsed(next);
    try {
      localStorage.setItem("sidebar", next ? "collapsed" : "open");
    } catch {
      // modo privado: vale só nesta aba
    }
  };

  return (
    <div className={"shell" + (collapsed ? " collapsed" : "")}>
      <aside className="sidebar" aria-label="Menu principal">
        <div className="sidebar-brand">
          <span className="brand-mark" aria-hidden>
            <Stethoscope size={18} />
          </span>
          <span className="brand-name">Atendimento da clínica</span>
        </div>
        <nav className="sidebar-nav">
          {visibleNav.map((n) => (
            <a key={n.path} href={"#/" + n.path} aria-current={page === n.path ? "page" : undefined} title={n.label}>
              <n.icon size={18} aria-hidden />
              <span>{n.label}</span>
            </a>
          ))}
        </nav>
        <div className="sidebar-foot">
          <button className="sidebar-item" onClick={() => setShortcutsOpen(true)} title="Atalhos (?)">
            <Keyboard size={18} aria-hidden />
            <span>Atalhos (?)</span>
          </button>
          <button className="sidebar-item" onClick={toggleSidebar} title={collapsed ? "Expandir menu" : "Recolher menu"}>
            {collapsed ? <PanelLeftOpen size={18} aria-hidden /> : <PanelLeftClose size={18} aria-hidden />}
            <span>Recolher</span>
          </button>
        </div>
      </aside>

      <div className="main">
        <Topbar searchRef={searchRef} />
        <main className="content">
          {page === "atendimento" && state.can("conversation:view") ? (
            <Inbox selectedId={route[1]} />
          ) : page === "solicitacoes" && state.can("request:view") ? (
            <Requests selectedId={route[1]} />
          ) : page === "clientes" && state.can("customer:view") ? (
            <Customers selectedId={route[1]} />
          ) : page === "configuracao" && state.can("config:edit") ? (
            <Config tab={route[1]} />
          ) : page === "equipe" && state.can("team:manage") ? (
            <Team tab={route[1]} />
          ) : page === "auditoria" && state.can("audit:view") ? (
            <Audit />
          ) : (
            <Home />
          )}
        </main>
      </div>

      <nav className="bottom-nav" aria-label="Menu principal">
        {visibleNav.slice(0, 3).map((n) => (
          <a key={n.path} href={"#/" + n.path} aria-current={page === n.path ? "page" : undefined}>
            <n.icon size={20} aria-hidden />
            <span>{n.label.split(" ")[0]}</span>
          </a>
        ))}
        <button onClick={() => setMoreOpen(true)}>
          <MoreHorizontal size={20} aria-hidden />
          <span>Mais</span>
        </button>
      </nav>

      <Dialog title="Mais" open={moreOpen} onClose={() => setMoreOpen(false)}>
        <div className="menu-list">
          {visibleNav.slice(3).map((n) => (
            <a key={n.path} href={"#/" + n.path} onClick={() => setMoreOpen(false)}>
              <n.icon size={18} aria-hidden /> {n.label}
            </a>
          ))}
          <button onClick={() => void state.logout()}>
            <LogOut size={18} aria-hidden /> Sair
          </button>
        </div>
      </Dialog>

      <Dialog title="Atalhos de teclado" open={shortcutsOpen} onClose={() => setShortcutsOpen(false)}>
        <dl className="shortcuts">
          {SHORTCUTS.map(([key, label]) => (
            <div key={key}>
              <dt>{label}</dt>
              <dd>
                <kbd>{key}</kbd>
              </dd>
            </div>
          ))}
        </dl>
      </Dialog>
    </div>
  );
}

function Topbar({ searchRef }: { searchRef: React.RefObject<HTMLInputElement | null> }) {
  const { me, can, liveTick, reloadMe, logout } = useApp();
  const toast = useToast();
  const [theme, toggleTheme] = useTheme();
  const [busy, setBusy] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  // Urgentes = P1/P2 aguardando alguém da equipe (sem responsável). Assumidas aparecem em "Comigo".
  const inbox = useLoad(
    () => (can("conversation:view") ? api.get<InboxItem[]>("/conversations?filter=WAITING") : Promise.resolve([])),
    [liveTick],
  );
  const urgent = (inbox.data ?? []).filter((c) => c.priority === "P1_POSSIBLE_EMERGENCY" || c.priority === "P2_URGENT");

  const toggleAvailability = async () => {
    setBusy(true);
    try {
      const next = me.availability === "AVAILABLE" ? "AWAY" : "AVAILABLE";
      await api.patch("/me/availability", { availability: next });
      await reloadMe();
      toast(next === "AVAILABLE" ? "Você está disponível" : "Você está ausente: não recebe atribuições");
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <header className="topbar">
      {can("customer:view") ? <GlobalSearch inputRef={searchRef} /> : <span className="grow" />}
      {urgent.length > 0 && (
        <button className="urgent-button" onClick={() => go("atendimento/" + urgent[0].id)} title="Abrir a mais urgente">
          <AlertTriangle size={16} aria-hidden />
          {urgent.length} {urgent.length === 1 ? "urgente" : "urgentes"}
        </button>
      )}
      <button
        className={"availability " + (me.availability === "AVAILABLE" ? "is-available" : "is-away")}
        onClick={toggleAvailability}
        disabled={busy}
        title="Ausentes não recebem atribuições"
      >
        <span className="dot" aria-hidden />
        <span className="availability-text">{me.availability === "AVAILABLE" ? "Disponível" : "Ausente"}</span>
      </button>
      <button className="icon-button" onClick={toggleTheme} aria-label={theme === "dark" ? "Tema claro" : "Tema escuro"}>
        {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
      </button>
      <div className="user-menu">
        <button className="user-button" onClick={() => setMenuOpen(!menuOpen)} aria-expanded={menuOpen}>
          {me.displayName}
        </button>
        {menuOpen && (
          <div className="popover" onMouseLeave={() => setMenuOpen(false)}>
            <span className="muted small">{me.email}</span>
            <button onClick={() => void logout()}>
              <LogOut size={16} aria-hidden /> Sair
            </button>
          </div>
        )}
      </div>
    </header>
  );
}

/** Busca global de cliente ou pet (atalho "/"): resultados enquanto digita. */
function GlobalSearch({ inputRef }: { inputRef: React.RefObject<HTMLInputElement | null> }) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<CustomerItem[]>([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const text = q.trim();
    if (text.length < 2) {
      setResults([]);
      return;
    }
    const timer = window.setTimeout(() => {
      api
        .get<CustomerItem[]>("/customers?q=" + encodeURIComponent(text))
        .then(setResults)
        .catch(() => setResults([]));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [q]);

  const pick = (id: string) => {
    go("clientes/" + id);
    setQ("");
    setOpen(false);
    inputRef.current?.blur();
  };

  return (
    <div className="search">
      <Search size={16} className="search-icon" aria-hidden />
      <input
        ref={inputRef}
        value={q}
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => window.setTimeout(() => setOpen(false), 150)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && results[0]) pick(results[0].id);
          if (e.key === "Escape") inputRef.current?.blur();
        }}
        placeholder="Buscar tutor, telefone ou pet"
        aria-label="Buscar tutor, telefone ou pet"
      />
      <kbd className="search-kbd">/</kbd>
      {open && q.trim().length >= 2 && (
        <ul className="popover search-results" role="listbox">
          {results.length === 0 && <li className="muted small pad-s">Ninguém encontrado.</li>}
          {results.map((c) => (
            <li key={c.id}>
              <button onMouseDown={() => pick(c.id)}>
                <strong>{c.name ?? "Sem nome"}</strong>
                <span className="muted small">
                  <PawPrint size={12} aria-hidden /> {c.pets.join(", ") || "sem pets"}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
