import { useState } from "react";
import { api } from "../api";
import { go, useApp, useLoad } from "../app-context";
import { PriorityBadge, RequestStatusBadge } from "../components/Badges";
import { outcomeLabel, requestStatusLabel, requestTypeLabel, timeLabel } from "../labels";
import type { Outcome, RequestDetail, RequestItem } from "../types";
import { TransferForm } from "./Inbox";

/** Solicitações em abas por setor (DA-42, RF-S30 a RF-S36). Cada pessoa vê só as abas dos setores dela. */
export function Requests({ selectedId }: { selectedId?: string }) {
  const { session, me, departments, liveTick } = useApp();
  const visible = departments.filter((d) => session.sectorScope === "ALL" || me.departmentIds.includes(d.id));
  const [tab, setTab] = useState<string>("mine");
  const [status, setStatus] = useState<"open" | "closed">("open");
  const statuses = status === "open" ? "PENDING,IN_PROGRESS" : "RESOLVED,CANCELLED";
  const query =
    tab === "mine"
      ? `?mine=true&status=${statuses}`
      : tab === "unassigned"
        ? `?unassigned=true&status=PENDING`
        : `?departmentId=${tab}&status=${statuses}`;
  const list = useLoad(() => api.get<RequestItem[]>("/service-requests" + query), [query, liveTick]);

  return (
    <div className={"split" + (selectedId ? " has-detail" : "")}>
      <aside className="split-list">
        <div className="tabs" role="tablist">
          <button role="tab" aria-selected={tab === "mine"} onClick={() => setTab("mine")}>
            Comigo
          </button>
          <button role="tab" aria-selected={tab === "unassigned"} onClick={() => setTab("unassigned")}>
            Sem dono
          </button>
          {visible.map((d) => (
            <button key={d.id} role="tab" aria-selected={tab === d.id} onClick={() => setTab(d.id)}>
              {d.name}
            </button>
          ))}
        </div>
        {tab !== "unassigned" && (
          <div className="segmented pad-x">
            <label>
              <input type="radio" checked={status === "open"} onChange={() => setStatus("open")} /> Em aberto
            </label>
            <label>
              <input type="radio" checked={status === "closed"} onChange={() => setStatus("closed")} /> Encerradas
            </label>
          </div>
        )}
        {list.error && <p className="error">{list.error}</p>}
        {list.data?.length === 0 && <p className="muted pad">Nenhuma solicitação aqui.</p>}
        <ul className="list">
          {list.data?.map((r) => (
            <li key={r.id}>
              <a href={"#/solicitacoes/" + r.id} className={"row" + (r.id === selectedId ? " selected" : "")}>
                <PriorityBadge priority={r.priority} />
                <span className="grow">
                  <strong>{requestTypeLabel[r.type]}</strong> · {r.petName ?? r.tutorName ?? "—"}
                  <span className="muted small block">
                    {r.assigneeName ?? "sem responsável"} · desde {timeLabel(r.createdAt)}
                  </span>
                </span>
                <span className="right">
                  <RequestStatusBadge status={r.status} />
                  {r.nextAction && <span className="muted small block">{r.nextAction}</span>}
                </span>
              </a>
            </li>
          ))}
        </ul>
      </aside>
      <section className="split-detail">
        {selectedId ? <Request id={selectedId} onChanged={list.reload} /> : <p className="muted pad">Escolha uma solicitação.</p>}
      </section>
    </div>
  );
}

function Request({ id, onChanged }: { id: string; onChanged: () => void }) {
  const { me, can, departments, departmentName, liveTick } = useApp();
  const { data, error, reload } = useLoad(() => api.get<RequestDetail>("/service-requests/" + id), [id, liveTick]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [panel, setPanel] = useState<"none" | "transfer" | "close">("none");
  const [note, setNote] = useState("");

  const act = async (fn: () => Promise<unknown>) => {
    setActionError(null);
    try {
      await fn();
      setPanel("none");
      reload();
      onChanged();
    } catch (e) {
      setActionError((e as Error).message);
    }
  };

  if (error) {
    return (
      <div className="pad">
        <button className="link back" onClick={() => go("solicitacoes")}>
          ← Solicitações
        </button>
        <p className="error">{error}</p>
      </div>
    );
  }
  if (!data) return <p className="muted pad">Carregando…</p>;
  const r = data.request;
  const open = r.status !== "RESOLVED" && r.status !== "CANCELLED";

  return (
    <div className="pad request">
      <button className="link back" onClick={() => go("solicitacoes")}>
        ← Solicitações
      </button>
      <h2>
        {requestTypeLabel[r.type]} · {r.petName ?? r.tutorName ?? "sem pet"}
      </h2>
      <div className="badges">
        <PriorityBadge priority={r.priority} long />
        <RequestStatusBadge status={r.status} />
        <span className="badge">{departmentName(r.departmentId)}</span>
        <span className="badge">{r.assigneeName ? "com " + r.assigneeName : "sem responsável"}</span>
      </div>

      <dl className="facts">
        {r.tutorName && (
          <>
            <dt>Tutor</dt>
            <dd>{r.tutorName}</dd>
          </>
        )}
        {r.reason && (
          <>
            <dt>Motivo</dt>
            <dd>{r.reason}</dd>
          </>
        )}
        {r.preferredPeriod && (
          <>
            <dt>Preferência</dt>
            <dd>{r.preferredPeriod} (não é horário confirmado)</dd>
          </>
        )}
        {r.summary && (
          <>
            <dt>Resumo</dt>
            <dd>{r.summary}</dd>
          </>
        )}
        {r.outcome && (
          <>
            <dt>Resultado</dt>
            <dd>{outcomeLabel[r.outcome]}</dd>
          </>
        )}
        <dt>Conversa</dt>
        <dd>
          <a href={"#/atendimento/" + r.conversationId}>abrir conversa</a>
        </dd>
      </dl>

      {actionError && (
        <p className="error" role="alert">
          {actionError}
        </p>
      )}
      {open && (
        <div className="actions">
          {can("request:assign") && r.assigneeId !== me.id && (
            <button className="primary" onClick={() => act(() => api.post(`/service-requests/${id}/assign`, {}))}>
              Atribuir a mim
            </button>
          )}
          {can("request:assign") && r.status === "PENDING" && (
            <button onClick={() => act(() => api.patch(`/service-requests/${id}`, { status: "IN_PROGRESS" }))}>Iniciar</button>
          )}
          {can("request:close") && (
            <button onClick={() => setPanel(panel === "close" ? "none" : "close")}>Resolver ou cancelar</button>
          )}
          {can("request:assign") && <button onClick={() => setPanel(panel === "transfer" ? "none" : "transfer")}>Transferir</button>}
        </div>
      )}
      {panel === "transfer" && (
        <TransferForm
          departments={departments.filter((d) => d.active && d.id !== r.departmentId)}
          onSubmit={(departmentId, reason) => act(() => api.post(`/service-requests/${id}/transfer`, { departmentId, reason }))}
        />
      )}
      {panel === "close" && (
        <CloseForm
          inProgress={r.status === "IN_PROGRESS"}
          onSubmit={(status, outcome, outcomeNote) => act(() => api.patch(`/service-requests/${id}`, { status, outcome, outcomeNote }))}
        />
      )}

      <section>
        <h3>Notas internas</h3>
        <ul className="plain notes">
          {data.notes.map((n) => (
            <li key={n.id}>
              <span className="muted small">
                {n.authorName ?? "Sistema"} · {timeLabel(n.at)}
              </span>
              <span className="block">{n.text}</span>
            </li>
          ))}
        </ul>
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault();
            if (note.trim()) void act(() => api.post(`/service-requests/${id}/notes`, { text: note.trim() })).then(() => setNote(""));
          }}
        >
          <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2} maxLength={2000} placeholder="Nova nota interna…" />
          <button>Salvar nota</button>
        </form>
      </section>

      <section>
        <h3>Histórico</h3>
        <ul className="plain timeline">
          {data.events.map((e, i) => (
            <li key={i}>
              <span className="muted small">{timeLabel(e.at)}</span> {e.actorName ?? "Sistema"}: {eventText(e, departmentName)}
            </li>
          ))}
          {data.events.length === 0 && <li className="muted small">Sem mudanças ainda.</li>}
        </ul>
      </section>
    </div>
  );
}

function eventText(
  e: { type: string; from: string | null; to: string | null; reason: string | null },
  departmentName: (id: string) => string,
): string {
  switch (e.type) {
    case "ASSIGNED":
      return "atribuiu";
    case "UNASSIGNED":
      return "devolveu ao setor (conta desativada)";
    case "TRANSFERRED":
      return `transferiu de ${departmentName(e.from ?? "")} para ${departmentName(e.to ?? "")} — ${e.reason ?? ""}`;
    case "STATUS_CHANGED":
      return `${requestStatusLabel[e.from as keyof typeof requestStatusLabel] ?? e.from} → ${
        requestStatusLabel[e.to as keyof typeof requestStatusLabel] ?? e.to
      }`;
    default:
      return e.type;
  }
}

function CloseForm({
  inProgress,
  onSubmit,
}: {
  inProgress: boolean;
  onSubmit: (status: "RESOLVED" | "CANCELLED", outcome: Outcome, note: string) => Promise<void>;
}) {
  const [status, setStatus] = useState<"RESOLVED" | "CANCELLED">(inProgress ? "RESOLVED" : "CANCELLED");
  const [outcome, setOutcome] = useState<Outcome>("HANDLED_OUTSIDE");
  const [note, setNote] = useState("");
  return (
    <form
      className="card inline-form"
      onSubmit={(e) => {
        e.preventDefault();
        void onSubmit(status, outcome, note);
      }}
    >
      <label>
        Encerrar como
        <select value={status} onChange={(e) => setStatus(e.target.value as "RESOLVED" | "CANCELLED")}>
          <option value="RESOLVED" disabled={!inProgress}>
            Resolvida{inProgress ? "" : " (inicie antes)"}
          </option>
          <option value="CANCELLED">Cancelada</option>
        </select>
      </label>
      <label>
        Resultado
        <select value={outcome} onChange={(e) => setOutcome(e.target.value as Outcome)}>
          {(Object.keys(outcomeLabel) as Outcome[]).map((o) => (
            <option key={o} value={o}>
              {outcomeLabel[o]}
            </option>
          ))}
        </select>
      </label>
      <label className="grow">
        Observação {outcome === "OTHER" ? "(obrigatória)" : "(opcional)"}
        <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} required={outcome === "OTHER"} />
      </label>
      <button className="primary">Confirmar</button>
    </form>
  );
}
