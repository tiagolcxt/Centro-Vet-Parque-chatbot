import { useState } from "react";
import { ArrowLeft, ArrowRightLeft, CheckCircle2, Clock, Lock, MessageSquare, Play, UserPlus, X } from "lucide-react";
import { api } from "../api";
import { go, useApp, useLoad } from "../app-context";
import { QueueBadge, RequestStatusBadge, RiskBadge } from "../components/Badges";
import { outcomeLabel, requestStatusLabel, requestTypeLabel, timeLabel, demandTypeLabel, readableSummary } from "../labels";
import type { Outcome, RequestDetail, RequestItem, DemandType } from "../types";
import { useToast } from "../ui";
import { TransferPanel } from "./Inbox";
import { CopyButton } from "../config/tools";
import { requestSummaryText } from "../labels";

/** Solicitações em abas por setor (DA-42, RF-S30 a RF-S36). Cada pessoa vê só as abas dos setores dela. */
export function Requests({ selectedId }: { selectedId?: string }) {
  const { session, me, departments, liveTick, can } = useApp();
  const toast = useToast();
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

  const assignToMe = async (id: string) => {
    try {
      await api.post(`/service-requests/${id}/assign`, {});
      toast("Solicitação assumida por você");
      list.reload();
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  return (
    <div className={"split" + (selectedId ? " has-detail" : "")}>
      <aside className="split-list">
        <div className="tabs" role="tablist">
          <button role="tab" aria-selected={tab === "mine"} onClick={() => setTab("mine")}>
            Minhas
          </button>
          <button role="tab" aria-selected={tab === "unassigned"} onClick={() => setTab("unassigned")}>
            Sem responsável
          </button>
          {visible.map((d) => (
            <button key={d.id} role="tab" aria-selected={tab === d.id} onClick={() => setTab(d.id)}>
              {d.name}
            </button>
          ))}
        </div>
        {can("customer:export") && (
          <a className="small-button export-link" href="/app/api/service-requests/export.csv" download>
            Baixar planilha das solicitações
          </a>
        )}
        {tab !== "unassigned" && (
          <div className="chips pad-x filters">
            <button className={"chip" + (status === "open" ? " chip-selected" : "")} onClick={() => setStatus("open")}>
              Abertas
            </button>
            <button className={"chip" + (status === "closed" ? " chip-selected" : "")} onClick={() => setStatus("closed")}>
              Encerradas
            </button>
          </div>
        )}
        {list.error && <p className="error">{list.error}</p>}
        {list.data?.length === 0 && <p className="empty">Nenhuma solicitação aqui.</p>}
        <ul className="list">
          {list.data?.map((r) => (
            <li key={r.id} className={"req-item" + (r.id === selectedId ? " selected" : "")}>
              <a href={"#/solicitacoes/" + r.id} className="req-link">
                <div className="conv-item-top">
                  <RiskBadge risk={r.clinicalRisk} />
                  <QueueBadge priority={r.operationalPriority} />
                  <RequestStatusBadge status={r.status} assignee={r.assigneeName} />
                </div>
                <strong>
                  {demandTypeLabel[r.demandType]} · {r.petName ?? r.tutorName ?? "—"}
                </strong>
                <span className="muted small">
                  <Clock size={12} aria-hidden /> desde {timeLabel(r.createdAt)} · {r.assigneeName ?? "sem responsável"}
                </span>
              </a>
              {r.nextAction === "atribuir" && can("request:assign") && (
                <button className="small-button" onClick={() => void assignToMe(r.id)}>
                  <UserPlus size={14} aria-hidden /> Assumir solicitação
                </button>
              )}
            </li>
          ))}
        </ul>
      </aside>
      <section className="split-detail">
        {selectedId ? <Request id={selectedId} onChanged={list.reload} /> : <p className="empty center-v">Escolha uma solicitação na lista.</p>}
      </section>
    </div>
  );
}

function Request({ id, onChanged }: { id: string; onChanged: () => void }) {
  const { me, can, departments, departmentName, liveTick } = useApp();
  const toast = useToast();
  const { data, error, reload } = useLoad(() => api.get<RequestDetail>("/service-requests/" + id), [id, liveTick]);
  const [panel, setPanel] = useState<"none" | "transfer" | "close">("none");
  const [note, setNote] = useState("");

  const act = async (fn: () => Promise<unknown>, done: string) => {
    try {
      await fn();
      setPanel("none");
      toast(done);
      reload();
      onChanged();
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  if (error) {
    return (
      <div className="pad">
        <button className="link back" onClick={() => go("solicitacoes")}>
          <ArrowLeft size={16} aria-hidden /> Solicitações
        </button>
        <p className="error">{error}</p>
      </div>
    );
  }
  if (!data) return <p className="empty center-v">Carregando…</p>;
  const r = data.request;
  const open = r.status !== "RESOLVED" && r.status !== "CANCELLED";

  return (
    <div className="page detail">
      <div className="conv-title-row">
        <button className="icon-button back" onClick={() => go("solicitacoes")} aria-label="Voltar para as solicitações">
          <ArrowLeft size={18} />
        </button>
        <div className="grow">
          <h2>
            {requestTypeLabel[r.type]} · {r.petName ?? r.tutorName ?? "sem pet"}
          </h2>
          <div className="badges">
            <RiskBadge risk={r.clinicalRisk} />
            <QueueBadge priority={r.operationalPriority} />
            <RequestStatusBadge status={r.status} assignee={r.assigneeName} />
            <span className="badge">{departmentName(r.departmentId)}</span>
          </div>
        </div>
        <CopyButton text={requestSummaryText(r, departmentName(r.departmentId))} label="Copiar resumo" />
      </div>
      <div className="inline-row small demand-row">
        <label htmlFor="demand-type" className="muted">
          Tipo da demanda
        </label>
        {can("request:assign") || can("request:close") ? (
          <select
            id="demand-type"
            value={r.demandType}
            onChange={(e) => act(() => api.patch(`/service-requests/${id}/demand-type`, { demandType: e.target.value }), "Tipo da demanda alterado")}
          >
            {(Object.keys(demandTypeLabel) as DemandType[]).map((t) => (
              <option key={t} value={t}>
                {demandTypeLabel[t]}
              </option>
            ))}
          </select>
        ) : (
          <span>{demandTypeLabel[r.demandType]}</span>
        )}
      </div>

      {open && (
        <div className="actions">
          {can("request:assign") && r.assigneeId !== me.id && (
            <button className="primary" onClick={() => act(() => api.post(`/service-requests/${id}/assign`, {}), "Solicitação assumida por você")}>
              <UserPlus size={16} aria-hidden /> Assumir solicitação
            </button>
          )}
          {can("request:assign") && r.status === "PENDING" && (
            <button
              className={r.assigneeId === me.id ? "primary" : ""}
              onClick={() => act(() => api.patch(`/service-requests/${id}`, { status: "IN_PROGRESS" }), "Solicitação marcada em andamento")}
            >
              <Play size={16} aria-hidden /> Marcar em andamento
            </button>
          )}
          {can("request:close") && (
            <button className={r.status === "IN_PROGRESS" ? "primary" : ""} onClick={() => setPanel(panel === "close" ? "none" : "close")}>
              <CheckCircle2 size={16} aria-hidden /> Concluir ou cancelar
            </button>
          )}
          {can("request:assign") && (
            <button onClick={() => setPanel(panel === "transfer" ? "none" : "transfer")}>
              <ArrowRightLeft size={16} aria-hidden /> Transferir para outro setor
            </button>
          )}
        </div>
      )}
      {panel === "transfer" && (
        <TransferPanel
          departments={departments.filter((d) => d.active && d.id !== r.departmentId)}
          onCancel={() => setPanel("none")}
          onSubmit={(departmentId, reason) => act(() => api.post(`/service-requests/${id}/transfer`, { departmentId, reason }), "Solicitação transferida")}
        />
      )}
      {panel === "close" && (
        <ClosePanel
          inProgress={r.status === "IN_PROGRESS"}
          onCancel={() => setPanel("none")}
          onSubmit={(status, outcome, outcomeNote) =>
            act(() => api.patch(`/service-requests/${id}`, { status, outcome, outcomeNote }), status === "RESOLVED" ? "Solicitação concluída" : "Solicitação cancelada")
          }
        />
      )}

      <section className="card">
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
              <dd>
                {r.preferredPeriod} <span className="muted small">(não é horário confirmado)</span>
              </dd>
            </>
          )}
          {r.summary && (
            <>
              <dt>Resumo</dt>
              <dd>{readableSummary(r.summary)}</dd>
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
            <a href={"#/atendimento/" + r.conversationId}>
              <MessageSquare size={14} aria-hidden /> abrir conversa
            </a>
          </dd>
        </dl>
      </section>

      <section className="notes-box">
        <h3>
          <Lock size={14} aria-hidden /> Notas internas
        </h3>
        <p className="small">Só a equipe vê.</p>
        {data.notes.map((n) => (
          <div key={n.id} className="note">
            <span className="small note-meta">
              {n.authorName ?? "Sistema"} · {timeLabel(n.at)}
            </span>
            <span className="block">{n.text}</span>
          </div>
        ))}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (note.trim()) void act(() => api.post(`/service-requests/${id}/notes`, { text: note.trim() }), "Nota salva").then(() => setNote(""));
          }}
        >
          <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2} maxLength={2000} placeholder="Escreva uma nota para a equipe" />
          {note.trim() && <button className="small-button">Salvar nota</button>}
        </form>
      </section>

      <section>
        <h3 className="eyebrow">O que aconteceu</h3>
        <ol className="timeline">
          {data.events.map((e, i) => (
            <li key={i}>
              <span className="muted small">{timeLabel(e.at)}</span> <strong>{e.actorName ?? "Assistente ou sistema"}</strong> {eventText(e, departmentName)}
            </li>
          ))}
          {data.events.length === 0 && <li className="muted small">Sem mudanças ainda.</li>}
        </ol>
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
      return "assumiu ou atribuiu a solicitação";
    case "UNASSIGNED":
      return "devolveu ao setor (conta desativada)";
    case "TRANSFERRED":
      return `transferiu de ${departmentName(e.from ?? "")} para ${departmentName(e.to ?? "")}: ${e.reason ?? ""}`;
    case "STATUS_CHANGED":
      return `mudou de ${requestStatusLabel[e.from as keyof typeof requestStatusLabel] ?? e.from} para ${
        requestStatusLabel[e.to as keyof typeof requestStatusLabel] ?? e.to
      }`;
    default:
      return e.type;
  }
}

function ClosePanel({
  inProgress,
  onSubmit,
  onCancel,
}: {
  inProgress: boolean;
  onSubmit: (status: "RESOLVED" | "CANCELLED", outcome: Outcome, note: string) => Promise<void>;
  onCancel: () => void;
}) {
  const [status, setStatus] = useState<"RESOLVED" | "CANCELLED">(inProgress ? "RESOLVED" : "CANCELLED");
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [note, setNote] = useState("");
  return (
    <form
      className="inline-panel"
      onSubmit={(e) => {
        e.preventDefault();
        if (outcome) void onSubmit(status, outcome, note);
      }}
    >
      <div className="inline-panel-head">
        <strong>Concluir ou cancelar a solicitação</strong>
        <button type="button" className="icon-button" onClick={onCancel} aria-label="Cancelar">
          <X size={16} />
        </button>
      </div>
      <div className="chips">
        <button type="button" className={"chip" + (status === "RESOLVED" ? " chip-selected" : "")} disabled={!inProgress} onClick={() => setStatus("RESOLVED")} title={inProgress ? "" : "Marque a solicitação em andamento antes"}>
          Concluída
        </button>
        <button type="button" className={"chip" + (status === "CANCELLED" ? " chip-selected" : "")} onClick={() => setStatus("CANCELLED")}>
          Cancelada
        </button>
      </div>
      <span className="muted small">Resultado</span>
      <div className="chips">
        {(Object.keys(outcomeLabel) as Outcome[]).map((o) => (
          <button type="button" key={o} className={"chip" + (outcome === o ? " chip-selected" : "")} onClick={() => setOutcome(o)}>
            {outcomeLabel[o]}
          </button>
        ))}
      </div>
      <div className="inline-row">
        <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} placeholder={outcome === "OTHER" ? "Observação (obrigatória)" : "Observação (opcional)"} required={outcome === "OTHER"} />
        <button className="primary" disabled={!outcome}>
          Confirmar
        </button>
      </div>
    </form>
  );
}
