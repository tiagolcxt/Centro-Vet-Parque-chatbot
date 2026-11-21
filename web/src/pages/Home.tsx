import { AlertTriangle, ChevronRight, Clock } from "lucide-react";
import { api } from "../api";
import { useApp, useLoad } from "../app-context";
import { PriorityBadge } from "../components/Badges";
import { handoffReasonLabel, minutesLabel, priorityShort, requestTypeLabel, timeLabel } from "../labels";
import type { InboxItem, RequestItem } from "../types";

/** "O que eu preciso fazer agora?" (RF-S70). Urgentes primeiro; depois três cartões com número grande. */
export function Home() {
  const { me, can, liveTick, departmentName } = useApp();
  const inbox = useLoad(
    () => (can("conversation:view") ? api.get<InboxItem[]>("/conversations") : Promise.resolve([])),
    [liveTick],
  );
  const mine = useLoad(
    () => (can("request:view") ? api.get<RequestItem[]>("/service-requests?mine=true") : Promise.resolve([])),
    [liveTick],
  );
  const unassigned = useLoad(
    () => (can("request:view") ? api.get<RequestItem[]>("/service-requests?unassigned=true") : Promise.resolve([])),
    [liveTick],
  );

  const conversations = inbox.data ?? [];
  const urgent = conversations.filter(
    (c) => (c.priority === "P1_POSSIBLE_EMERGENCY" || c.priority === "P2_URGENT") && c.status === "WAITING_HUMAN",
  );
  const waiting = conversations.filter((c) => c.status === "WAITING_HUMAN");
  const mineConversations = conversations.filter((c) => c.assigneeId === me.id);
  const mineCount = mineConversations.length + (mine.data?.length ?? 0);
  const firstName = me.displayName.split(" ")[0];

  return (
    <div className="page home">
      <header className="page-header">
        <h1>Olá, {firstName}</h1>
        <p className="muted">{urgent.length > 0 ? "Comece pelos urgentes." : "Nada urgente agora."}</p>
      </header>
      {(inbox.error || mine.error) && <p className="error">{inbox.error ?? mine.error}</p>}

      {urgent.length > 0 && (
        <section className="urgent-strip" aria-label="Urgentes">
          {urgent.map((c) => (
            <a key={c.id} href={"#/atendimento/" + c.id} className={"urgent-row tone-" + priorityShort(c.priority).toLowerCase()}>
              <PriorityBadge priority={c.priority} long />
              <strong>{c.tutorName ?? "Contato sem nome"}</strong>
              <span className="muted grow ellipsis">
                {c.handoffReason ? handoffReasonLabel[c.handoffReason] ?? c.handoffReason : ""}
              </span>
              <span className="urgent-wait">
                <Clock size={14} aria-hidden /> esperando há {minutesLabel(c.waitingMinutes ?? 0)}
              </span>
              <ChevronRight size={16} aria-hidden />
            </a>
          ))}
        </section>
      )}

      <div className="card-grid">
        <section className="card stat-card">
          <header>
            <h2>Comigo</h2>
            <span className="stat">{mineCount}</span>
          </header>
          {mineCount === 0 && <p className="muted small">Nada com você.</p>}
          <ul className="mini-list">
            {mineConversations.slice(0, 5).map((c) => (
              <ConversationLine key={c.id} c={c} />
            ))}
            {(mine.data ?? []).slice(0, 5).map((r) => (
              <RequestLine key={r.id} r={r} departmentName={departmentName} />
            ))}
          </ul>
        </section>
        <section className="card stat-card">
          <header>
            <h2>Aguardando a equipe</h2>
            <span className="stat">{waiting.length}</span>
          </header>
          {waiting.length === 0 && <p className="muted small">Ninguém esperando. Bom trabalho!</p>}
          <ul className="mini-list">
            {waiting.slice(0, 5).map((c) => (
              <ConversationLine key={c.id} c={c} />
            ))}
          </ul>
        </section>
        <section className="card stat-card">
          <header>
            <h2>Pedidos sem dono</h2>
            <span className="stat">{unassigned.data?.length ?? 0}</span>
          </header>
          {(unassigned.data?.length ?? 0) === 0 && <p className="muted small">Todos têm responsável.</p>}
          <ul className="mini-list">
            {(unassigned.data ?? []).slice(0, 5).map((r) => (
              <RequestLine key={r.id} r={r} departmentName={departmentName} />
            ))}
          </ul>
        </section>
      </div>
      {urgent.length === 0 && conversations.some((c) => c.priority === "P1_POSSIBLE_EMERGENCY") && (
        <p className="muted small">
          <AlertTriangle size={14} aria-hidden /> Há conversa P1 já assumida: veja em "Comigo" ou em Atendimento.
        </p>
      )}
    </div>
  );
}

function ConversationLine({ c }: { c: InboxItem }) {
  return (
    <li>
      <a href={"#/atendimento/" + c.id} className="mini-row">
        <PriorityBadge priority={c.priority} />
        <span className="grow">
          <strong>{c.tutorName ?? "Contato sem nome"}</strong>
          <span className="muted small block">
            {c.status === "WAITING_HUMAN" ? `Aguardando a equipe · ${minutesLabel(c.waitingMinutes ?? 0)}` : "Assumida"}
          </span>
        </span>
      </a>
    </li>
  );
}

function RequestLine({ r, departmentName }: { r: RequestItem; departmentName: (id: string) => string }) {
  return (
    <li>
      <a href={"#/solicitacoes/" + r.id} className="mini-row">
        <PriorityBadge priority={r.priority} />
        <span className="grow">
          <strong>
            {requestTypeLabel[r.type]} · {r.petName ?? r.tutorName ?? "—"}
          </strong>
          <span className="muted small block">
            {departmentName(r.departmentId)} · desde {timeLabel(r.createdAt)}
          </span>
        </span>
      </a>
    </li>
  );
}
