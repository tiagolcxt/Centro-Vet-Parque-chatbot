import { api } from "../api";
import { useApp, useLoad } from "../app-context";
import { PriorityBadge, RequestStatusBadge, StatusBadge } from "../components/Badges";
import { minutesLabel, requestTypeLabel } from "../labels";
import type { InboxItem, RequestItem } from "../types";

/** "O que eu preciso fazer agora?" (RF-S70). */
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
    (c) => (c.priority === "P1_POSSIBLE_EMERGENCY" || c.priority === "P2_URGENT") && c.status !== "OPEN",
  );
  const waiting = conversations.filter((c) => c.status === "WAITING_HUMAN");
  const mineConversations = conversations.filter((c) => c.assigneeId === me.id);

  return (
    <div className="home">
      <h1>Olá, {me.displayName.split(" ")[0]}</h1>
      {(inbox.error || mine.error) && <p className="error">{inbox.error ?? mine.error}</p>}

      <section className="card">
        <h2>Urgentes ({urgent.length})</h2>
        {urgent.length === 0 ? <p className="muted">Nenhuma conversa urgente agora.</p> : <ConversationList items={urgent} />}
      </section>

      <div className="grid-3">
        <section className="card">
          <h2>Comigo ({mineConversations.length + (mine.data?.length ?? 0)})</h2>
          <ConversationList items={mineConversations} />
          <RequestList items={mine.data ?? []} departmentName={departmentName} />
          {mineConversations.length + (mine.data?.length ?? 0) === 0 && <p className="muted">Nada com você.</p>}
        </section>
        <section className="card">
          <h2>Aguardando a equipe ({waiting.length})</h2>
          {waiting.length === 0 ? <p className="muted">Ninguém esperando.</p> : <ConversationList items={waiting} />}
        </section>
        <section className="card">
          <h2>Solicitações sem dono ({unassigned.data?.length ?? 0})</h2>
          {(unassigned.data?.length ?? 0) === 0 ? (
            <p className="muted">Todas têm responsável.</p>
          ) : (
            <RequestList items={unassigned.data ?? []} departmentName={departmentName} />
          )}
        </section>
      </div>
    </div>
  );
}

function ConversationList({ items }: { items: InboxItem[] }) {
  return (
    <ul className="list">
      {items.slice(0, 8).map((c) => (
        <li key={c.id}>
          <a href={"#/atendimento/" + c.id} className={"row" + (c.overdue ? " overdue" : "")}>
            <PriorityBadge priority={c.priority} />
            <span className="grow">{c.tutorName ?? "Contato sem nome"}</span>
            <StatusBadge status={c.status} />
            {c.waitingMinutes !== null && <span className="muted small">{minutesLabel(c.waitingMinutes)}</span>}
          </a>
        </li>
      ))}
    </ul>
  );
}

function RequestList({ items, departmentName }: { items: RequestItem[]; departmentName: (id: string) => string }) {
  return (
    <ul className="list">
      {items.slice(0, 8).map((r) => (
        <li key={r.id}>
          <a href={"#/solicitacoes/" + r.id} className="row">
            <PriorityBadge priority={r.priority} />
            <span className="grow">
              {requestTypeLabel[r.type]} · {r.petName ?? r.tutorName ?? "—"}
            </span>
            <span className="muted small">{departmentName(r.departmentId)}</span>
            <RequestStatusBadge status={r.status} />
          </a>
        </li>
      ))}
    </ul>
  );
}
