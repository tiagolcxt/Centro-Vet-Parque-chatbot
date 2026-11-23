import { useState } from "react";
import { Plus, UserCheck, UserX } from "lucide-react";
import { api } from "../api";
import { go, useApp, useLoad } from "../app-context";
import { CopyButton } from "../config/tools";
import type { Department } from "../types";
import { Dialog, useToast } from "../ui";

/** Equipe (revisão pré-piloto, E1; RF-S03 a RF-S06): pessoas, perfis e setores, sem depender da plataforma. */

interface Employee {
  id: string;
  displayName: string;
  email: string;
  roleId: string;
  status: "INVITED" | "ACTIVE" | "DISABLED";
  jobTitle: string | null;
  professionalRegistration: string | null;
  availability: "AVAILABLE" | "AWAY";
  departmentIds: string[];
}

interface Role {
  id: string;
  name: string;
  sectorScope: "OWN" | "ALL";
  template: string | null;
  permissions: string[];
}

interface Invite {
  token: string;
  expiresAt: string;
}

const STATUS_LABEL: Record<Employee["status"], string> = {
  INVITED: "Convite pendente",
  ACTIVE: "Ativa",
  DISABLED: "Desativada",
};

/** O que cada permissão deixa fazer, em linguagem da clínica. Mesmos códigos de Permission.java. */
const PERMISSION_LABEL: Record<string, string> = {
  "conversation:view": "Ver conversas",
  "conversation:assume": "Assumir e encerrar conversas",
  "conversation:reply": "Responder ao tutor",
  "conversation:transfer": "Transferir e devolver ao assistente",
  "request:view": "Ver solicitações",
  "request:assign": "Pegar e atribuir solicitações",
  "request:close": "Resolver ou cancelar solicitações",
  "customer:view": "Ver clientes e pets",
  "customer:edit": "Editar clientes e pets",
  "customer:export": "Exportar dados (planilhas)",
  "customer:erase": "Apagar dados de cliente",
  "clinical-notes:view": "Ver notas clínicas",
  "config:edit": "Editar a configuração e os setores",
  "config:publish": "Publicar a configuração e ligar o WhatsApp",
  "team:manage": "Administrar a equipe e os perfis",
  "audit:view": "Ver a auditoria",
};

const KIND_LABEL: Record<string, string> = {
  RECEPTION: "Recepção",
  CLINICAL: "Clínico",
  PETSHOP: "Pet shop / banho e tosa",
  OTHER: "Outro",
};

type Tab = "pessoas" | "perfis" | "setores";

export function Team({ tab }: { tab?: string }) {
  const { can } = useApp();
  const tabs: [Tab, string][] = [
    ["pessoas", "Pessoas"],
    ["perfis", "Perfis e permissões"],
    ...(can("config:edit") ? ([["setores", "Setores"]] as [Tab, string][]) : []),
  ];
  const current: Tab = tabs.some(([id]) => id === tab) ? (tab as Tab) : "pessoas";
  return (
    <div className="page config">
      <header className="page-header">
        <h1>Equipe</h1>
        <p className="muted">Cada pessoa tem o próprio login. Desativar tira o acesso, mas mantém o nome dela no histórico.</p>
      </header>
      <nav className="tabs config-tabs" role="tablist" aria-label="Partes da equipe">
        {tabs.map(([id, label]) => (
          <button key={id} role="tab" aria-selected={current === id} onClick={() => go("equipe/" + id)}>
            {label}
          </button>
        ))}
      </nav>
      <div className="config-body">
        {current === "pessoas" && <People />}
        {current === "perfis" && <Roles />}
        {current === "setores" && <Departments />}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- pessoas

function People() {
  const { departments, me } = useApp();
  const list = useLoad(() => api.get<Employee[]>("/employees"), []);
  const roles = useLoad(() => api.get<Role[]>("/roles"), []);
  const [editing, setEditing] = useState<Employee | "new" | null>(null);
  const [invite, setInvite] = useState<{ name: string; link: Invite } | null>(null);
  const roleName = (id: string) => roles.data?.find((r) => r.id === id)?.name ?? "—";
  const deptNames = (ids: string[]) =>
    ids.map((id) => departments.find((d) => d.id === id)?.name).filter(Boolean).join(", ") || "Nenhum setor";

  return (
    <section className="card config-section">
      <header className="inline-row">
        <h2 className="grow">Pessoas</h2>
        <button className="primary" onClick={() => setEditing("new")}>
          <Plus size={16} aria-hidden /> Convidar pessoa
        </button>
      </header>
      {list.error && <p className="error">{list.error}</p>}
      <ul className="list version-list">
        {(list.data ?? []).map((e) => (
          <li key={e.id} className={e.status === "DISABLED" ? "muted" : ""}>
            <span className="grow">
              <strong>{e.displayName}</strong>
              {e.id === me.id && <span className="muted small"> (você)</span>}
              <span className="muted small block">
                {e.email} · {roleName(e.roleId)} · {deptNames(e.departmentIds)}
              </span>
            </span>
            <span className={"badge" + (e.status === "INVITED" ? " badge-warning" : "")}>{STATUS_LABEL[e.status]}</span>
            {e.status === "ACTIVE" && (
              <span className="muted small">{e.availability === "AVAILABLE" ? "Disponível" : "Ausente"}</span>
            )}
            <button className="small-button" onClick={() => setEditing(e)}>
              Editar
            </button>
          </li>
        ))}
      </ul>

      <EmployeeDialog
        employee={editing}
        roles={roles.data ?? []}
        departments={departments}
        onClose={() => setEditing(null)}
        onSaved={(result) => {
          setEditing(null);
          list.reload();
          if (result) setInvite(result);
        }}
      />
      <Dialog title="Link de acesso" open={!!invite} onClose={() => setInvite(null)}>
        <div className="dialog-body">{invite && <InviteLinkBox name={invite.name} invite={invite.link} />}</div>
      </Dialog>
    </section>
  );
}

function inviteUrl(token: string): string {
  return `${window.location.origin}/#/convite?token=${encodeURIComponent(token)}`;
}

function InviteLinkBox({ name, invite }: { name: string; invite: Invite }) {
  const url = inviteUrl(invite.token);
  return (
    <div className="stack">
      <p>
        Envie este link para <strong>{name}</strong> (por WhatsApp ou e-mail). Com ele a pessoa cria a própria senha.
      </p>
      <div className="inline-row">
        <code className="grow ellipsis">{url}</code>
        <CopyButton text={url} label="Copiar link" />
      </div>
      <p className="muted small">
        Vale até {new Date(invite.expiresAt).toLocaleString("pt-BR")} e só pode ser usado uma vez. Este link não aparece de
        novo: se perder, gere outro.
      </p>
    </div>
  );
}

function EmployeeDialog({
  employee,
  roles,
  departments,
  onClose,
  onSaved,
}: {
  employee: Employee | "new" | null;
  roles: Role[];
  departments: Department[];
  onClose: () => void;
  onSaved: (invite: { name: string; link: Invite } | null) => void;
}) {
  const toast = useToast();
  const { me } = useApp();
  const isNew = employee === "new";
  const current = isNew || employee === null ? null : employee;
  const [form, setForm] = useState({ displayName: "", email: "", roleId: "", jobTitle: "", professionalRegistration: "" });
  const [sectors, setSectors] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);

  const key = isNew ? "new" : (current?.id ?? null);
  if (key !== loadedFor) {
    setLoadedFor(key);
    setError(null);
    setForm({
      displayName: current?.displayName ?? "",
      email: current?.email ?? "",
      roleId: current?.roleId ?? roles.find((r) => r.template === "RECEPTION")?.id ?? roles[0]?.id ?? "",
      jobTitle: current?.jobTitle ?? "",
      professionalRegistration: current?.professionalRegistration ?? "",
    });
    setSectors(new Set(current?.departmentIds ?? departments.filter((d) => d.isDefault).map((d) => d.id)));
  }

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const save = () =>
    run(async () => {
      const body = {
        displayName: form.displayName,
        roleId: form.roleId,
        departmentIds: [...sectors],
        jobTitle: form.jobTitle || null,
        professionalRegistration: form.professionalRegistration || null,
      };
      if (isNew) {
        const result = await api.post<{ invite: Invite }>("/employees", { ...body, email: form.email });
        toast("Convite criado.");
        onSaved({ name: form.displayName, link: result.invite });
      } else if (current) {
        await api.put(`/employees/${current.id}`, body);
        toast("Alterações salvas.");
        onSaved(null);
      }
    });

  const newLink = () =>
    run(async () => {
      if (!current) return;
      const link = await api.post<Invite>(`/employees/${current.id}/invite`);
      onSaved({ name: current.displayName, link });
    });

  const disable = () =>
    run(async () => {
      if (!current || !window.confirm(`Desativar ${current.displayName}? A pessoa perde o acesso na hora. O que estava com ela volta para o setor.`)) return;
      await api.post(`/employees/${current.id}/disable`);
      toast("Conta desativada.");
      onSaved(null);
    });

  const enable = () =>
    run(async () => {
      if (!current) return;
      await api.post(`/employees/${current.id}/enable`);
      toast("Conta reativada.");
      onSaved(null);
    });

  const role = roles.find((r) => r.id === form.roleId);
  return (
    <Dialog title={isNew ? "Convidar pessoa" : "Editar pessoa"} open={employee !== null} onClose={onClose}>
      <div className="dialog-body">
      <div className="form-grid">
        <div className="field">
          <label htmlFor="emp-name">Nome</label>
          <input id="emp-name" maxLength={120} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="emp-email">E-mail (login)</label>
          <input id="emp-email" type="email" maxLength={254} disabled={!isNew} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="emp-role">Perfil</label>
          <select id="emp-role" value={form.roleId} onChange={(e) => setForm({ ...form, roleId: e.target.value })}>
            {roles.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
          {role && (
            <p className="muted small">{role.sectorScope === "ALL" ? "Vê todos os setores." : "Vê só os setores marcados abaixo."}</p>
          )}
        </div>
        <div className="field">
          <label htmlFor="emp-job">Cargo (opcional)</label>
          <input id="emp-job" maxLength={80} value={form.jobTitle} onChange={(e) => setForm({ ...form, jobTitle: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="emp-reg">Registro profissional, ex.: CRMV (opcional)</label>
          <input id="emp-reg" maxLength={40} value={form.professionalRegistration} onChange={(e) => setForm({ ...form, professionalRegistration: e.target.value })} />
        </div>
      </div>
      <fieldset className="field">
        <legend>Setores</legend>
        {departments.filter((d) => d.active).map((d) => (
          <label key={d.id} className="inline-row">
            <input
              type="checkbox"
              checked={sectors.has(d.id)}
              onChange={(e) => {
                const next = new Set(sectors);
                if (e.target.checked) next.add(d.id);
                else next.delete(d.id);
                setSectors(next);
              }}
            />
            {d.name}
          </label>
        ))}
      </fieldset>
      {error && <p className="error">{error}</p>}
      <div className="inline-row">
        <button className="primary" disabled={busy || !form.displayName.trim() || (isNew && !form.email.trim()) || !form.roleId} onClick={() => void save()}>
          {isNew ? "Convidar" : "Salvar"}
        </button>
        {current && current.status !== "DISABLED" && (
          <button className="small-button" disabled={busy} onClick={() => void newLink()}>
            {current.status === "INVITED" ? "Gerar novo convite" : "Gerar link para nova senha"}
          </button>
        )}
        {current && current.status !== "DISABLED" && current.id !== me.id && (
          <button className="small-button" disabled={busy} onClick={() => void disable()}>
            <UserX size={14} aria-hidden /> Desativar
          </button>
        )}
        {current && current.status === "DISABLED" && (
          <button className="small-button" disabled={busy} onClick={() => void enable()}>
            <UserCheck size={14} aria-hidden /> Reativar
          </button>
        )}
      </div>
      </div>
    </Dialog>
  );
}

// ---------------------------------------------------------------- perfis

function Roles() {
  const toast = useToast();
  const roles = useLoad(() => api.get<Role[]>("/roles"), []);
  const permissions = useLoad(() => api.get<string[]>("/permissions"), []);
  const [editing, setEditing] = useState<Role | "new" | null>(null);
  const [name, setName] = useState("");
  const [scope, setScope] = useState<Role["sectorScope"]>("OWN");
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  const open = (role: Role | "new") => {
    setEditing(role);
    setError(null);
    setName(role === "new" ? "" : role.name);
    setScope(role === "new" ? "OWN" : role.sectorScope);
    setChosen(new Set(role === "new" ? ["conversation:view", "request:view", "customer:view"] : role.permissions));
  };

  const save = async () => {
    try {
      const body = { name, sectorScope: scope, permissions: [...chosen] };
      if (editing === "new") await api.post("/roles", body);
      else if (editing) await api.put(`/roles/${editing.id}`, body);
      toast("Perfil salvo. Vale na próxima ação de quem tem esse perfil.");
      setEditing(null);
      roles.reload();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <section className="card config-section">
      <header className="inline-row">
        <span className="grow">
          <h2>Perfis e permissões</h2>
          <p className="muted small">O perfil diz o que a pessoa pode fazer. Os modelos prontos podem ser ajustados.</p>
        </span>
        <button className="primary" onClick={() => open("new")}>
          <Plus size={16} aria-hidden /> Novo perfil
        </button>
      </header>
      <ul className="list version-list">
        {(roles.data ?? []).map((r) => (
          <li key={r.id}>
            <span className="grow">
              <strong>{r.name}</strong>
              <span className="muted small block">
                {r.sectorScope === "ALL" ? "Todos os setores" : "Só os próprios setores"} ·{" "}
                {r.permissions.map((p) => PERMISSION_LABEL[p] ?? p).join(", ")}
              </span>
            </span>
            <button className="small-button" onClick={() => open(r)}>
              Editar
            </button>
          </li>
        ))}
      </ul>
      <Dialog title={editing === "new" ? "Novo perfil" : "Editar perfil"} open={editing !== null} onClose={() => setEditing(null)}>
        <div className="dialog-body">
        <div className="field">
          <label htmlFor="role-name">Nome</label>
          <input id="role-name" maxLength={60} value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="role-scope">O que a pessoa vê</label>
          <select id="role-scope" value={scope} onChange={(e) => setScope(e.target.value as Role["sectorScope"])}>
            <option value="OWN">Só os setores dela</option>
            <option value="ALL">Todos os setores</option>
          </select>
        </div>
        <fieldset className="field">
          <legend>Pode</legend>
          {(permissions.data ?? []).map((p) => (
            <label key={p} className="inline-row">
              <input
                type="checkbox"
                checked={chosen.has(p)}
                onChange={(e) => {
                  const next = new Set(chosen);
                  if (e.target.checked) next.add(p);
                  else next.delete(p);
                  setChosen(next);
                }}
              />
              {PERMISSION_LABEL[p] ?? p}
            </label>
          ))}
        </fieldset>
        {error && <p className="error">{error}</p>}
        <button className="primary" disabled={!name.trim()} onClick={() => void save()}>
          Salvar perfil
        </button>
        </div>
      </Dialog>
    </section>
  );
}

// ---------------------------------------------------------------- setores

function Departments() {
  const toast = useToast();
  const { reloadMe } = useApp();
  const list = useLoad(() => api.get<Department[]>("/departments"), []);
  const [name, setName] = useState("");
  const [kind, setKind] = useState("OTHER");
  const [error, setError] = useState<string | null>(null);

  const act = async (fn: () => Promise<unknown>, done: string) => {
    try {
      await fn();
      toast(done);
      setError(null);
      list.reload();
      await reloadMe();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <section className="card config-section">
      <header>
        <h2>Setores</h2>
        <p className="muted small">
          Setores são as filas da clínica. O padrão (Recepção) recebe o que não tem regra e não pode ser desativado. O tipo
          ajuda o assistente a escolher o setor (assunto de saúde vai para o setor clínico).
        </p>
      </header>
      <ul className="list version-list">
        {(list.data ?? []).map((d) => (
          <li key={d.id} className={d.active ? "" : "muted"}>
            <span className="grow">
              <strong>{d.name}</strong>
              <span className="muted small block">
                {KIND_LABEL[d.kind] ?? d.kind}
                {d.isDefault ? " · padrão" : ""}
                {d.active ? "" : " · desativado"}
              </span>
            </span>
            <button
              className="small-button"
              onClick={() => {
                const next = window.prompt("Novo nome do setor", d.name);
                if (next && next.trim() && next.trim() !== d.name) {
                  void act(() => api.patch(`/departments/${d.id}`, { name: next.trim() }), "Setor renomeado.");
                }
              }}
            >
              Renomear
            </button>
            {!d.isDefault && (
              <button className="small-button" onClick={() => void act(() => api.patch(`/departments/${d.id}`, { active: !d.active }), d.active ? "Setor desativado." : "Setor ativado.")}>
                {d.active ? "Desativar" : "Ativar"}
              </button>
            )}
          </li>
        ))}
      </ul>
      <div className="form-grid">
        <div className="field">
          <label htmlFor="dept-name">Novo setor</label>
          <input id="dept-name" maxLength={60} value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="dept-kind">Tipo</label>
          <select id="dept-kind" value={kind} onChange={(e) => setKind(e.target.value)}>
            {Object.entries(KIND_LABEL)
              .filter(([k]) => k !== "RECEPTION")
              .map(([k, label]) => (
                <option key={k} value={k}>
                  {label}
                </option>
              ))}
          </select>
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      <button
        className="primary"
        disabled={!name.trim()}
        onClick={() => void act(() => api.post("/departments", { name: name.trim(), kind }).then(() => setName("")), "Setor criado.")}
      >
        Criar setor
      </button>
    </section>
  );
}
