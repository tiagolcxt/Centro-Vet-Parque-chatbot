import { useEffect, useRef, useState, type FormEvent } from "react";
import { api } from "../api";
import { go, useApp, useLoad } from "../app-context";
import { PriorityBadge, RequestStatusBadge, StatusBadge } from "../components/Badges";
import { handoffReasonLabel, minutesLabel, priorityLabel, requestTypeLabel, speciesLabel, timeLabel } from "../labels";
import type { ConversationDetail, InboxItem, Priority } from "../types";

type Filter = "ALL" | "WAITING" | "MINE";
const FILTERS: { id: Filter; label: string }[] = [
  { id: "WAITING", label: "Aguardando" },
  { id: "MINE", label: "Comigo" },
  { id: "ALL", label: "Todas" },
];

/** Caixa de entrada (RF-S20 a RF-S29): lista, conversa e painel do tutor. No celular, uma coisa por vez. */
export function Inbox({ selectedId }: { selectedId?: string }) {
  const { liveTick } = useApp();
  const [filter, setFilter] = useState<Filter>("WAITING");
  const list = useLoad(() => api.get<InboxItem[]>("/conversations?filter=" + filter), [filter, liveTick]);

  return (
    <div className={"split" + (selectedId ? " has-detail" : "")}>
      <aside className="split-list">
        <div className="tabs" role="tablist">
          {FILTERS.map((f) => (
            <button key={f.id} role="tab" aria-selected={filter === f.id} onClick={() => setFilter(f.id)}>
              {f.label}
            </button>
          ))}
        </div>
        {list.error && <p className="error">{list.error}</p>}
        {list.data?.length === 0 && <p className="muted pad">Nenhuma conversa aqui.</p>}
        <ul className="list">
          {list.data?.map((c) => (
            <li key={c.id}>
              <a
                href={"#/atendimento/" + c.id}
                className={"row conv" + (c.id === selectedId ? " selected" : "") + (c.overdue ? " overdue" : "")}
              >
                <PriorityBadge priority={c.priority} />
                <span className="grow">
                  <strong>{c.tutorName ?? "Contato sem nome"}</strong>
                  <span className="muted small block">
                    {c.handoffReason ? handoffReasonLabel[c.handoffReason] ?? c.handoffReason : "Com o assistente"}
                    {c.assigneeName ? ` · ${c.assigneeName}` : ""}
                  </span>
                </span>
                <span className="right">
                  <StatusBadge status={c.status} />
                  <span className="muted small block">
                    {c.waitingMinutes !== null ? minutesLabel(c.waitingMinutes) : timeLabel(c.lastActivityAt)}
                    {c.overdue ? " · atrasada" : ""}
                  </span>
                </span>
              </a>
            </li>
          ))}
        </ul>
      </aside>
      <section className="split-detail">
        {selectedId ? <Conversation id={selectedId} onChanged={list.reload} /> : <p className="muted pad">Escolha uma conversa.</p>}
      </section>
    </div>
  );
}

function Conversation({ id, onChanged }: { id: string; onChanged: () => void }) {
  const { me, can, departments, departmentName, liveTick } = useApp();
  const { data, error, reload, loading } = useLoad(() => api.get<ConversationDetail>("/conversations/" + id), [id, liveTick]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [panel, setPanel] = useState<"none" | "transfer" | "note" | "priority">("none");
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [data?.messages.length]);

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
        <button className="link back" onClick={() => go("atendimento")}>
          ← Conversas
        </button>
        <p className="error">{error}</p>
      </div>
    );
  }
  if (!data) return <p className="muted pad">{loading ? "Carregando…" : ""}</p>;

  const mine = data.status === "ASSIGNED" && data.assigneeId === me.id;
  const active = data.status !== "CLOSED";

  return (
    <div className="conversation">
      <div className="conv-main">
        <header className="conv-header">
          <button className="link back" onClick={() => go("atendimento")}>
            ← Conversas
          </button>
          <div className="conv-title">
            <h2>{data.tutor?.name ?? "Contato sem nome"}</h2>
            <div className="badges">
              <PriorityBadge priority={data.priority} long />
              <StatusBadge status={data.status} />
              <span className="badge">{departmentName(data.departmentId)}</span>
              {data.assigneeName && <span className="badge">com {data.assigneeName}</span>}
            </div>
          </div>
          <div className="actions">
            {active && can("conversation:assume") && !mine && (
              <button className="primary" onClick={() => act(() => api.post(`/conversations/${id}/assume`))}>
                Assumir
              </button>
            )}
            {active && can("conversation:transfer") && data.status !== "OPEN" && (
              <button onClick={() => act(() => api.post(`/conversations/${id}/return-to-assistant`, { reason: null }))}>
                Devolver ao assistente
              </button>
            )}
            {active && can("conversation:transfer") && data.status !== "OPEN" && (
              <button onClick={() => setPanel(panel === "transfer" ? "none" : "transfer")}>Transferir</button>
            )}
            {active && can("conversation:assume") && (
              <button onClick={() => setPanel(panel === "priority" ? "none" : "priority")}>Prioridade</button>
            )}
            {active && can("conversation:assume") && (
              <button
                className="danger"
                onClick={() => window.confirm("Encerrar esta conversa? As solicitações continuam abertas.") && act(() => api.post(`/conversations/${id}/close`))}
              >
                Encerrar
              </button>
            )}
          </div>
        </header>

        {actionError && (
          <p className="error pad" role="alert">
            {actionError}
          </p>
        )}
        {panel === "transfer" && (
          <TransferForm
            departments={departments.filter((d) => d.active)}
            onSubmit={(departmentId, reason) => act(() => api.post(`/conversations/${id}/transfer`, { departmentId, reason }))}
          />
        )}
        {panel === "priority" && (
          <PriorityForm
            current={data.priority}
            onSubmit={(priority, note) => act(() => api.patch(`/conversations/${id}/priority`, { priority, note }))}
          />
        )}

        <div className="messages" aria-live="polite">
          {data.messages.map((m) => (
            <div key={m.id} className={"msg msg-" + m.author.toLowerCase() + (m.fallback ? " msg-fallback" : "")}>
              <div className="msg-meta">
                {m.authorName} · {timeLabel(m.at)}
                {m.fallback ? " · resposta segura" : ""}
              </div>
              <div className="msg-text">{m.content}</div>
            </div>
          ))}
          <div ref={endRef} />
        </div>

        {mine && can("conversation:reply") ? (
          <ReplyBox onSend={(text) => act(() => api.post(`/conversations/${id}/reply`, { text }))} />
        ) : (
          active && (
            <p className="muted pad small">
              {data.status === "ASSIGNED"
                ? `${data.assigneeName ?? "Outra pessoa"} está com esta conversa.`
                : "Para responder, assuma a conversa. Enquanto ninguém assume, o assistente responde o que é da clínica."}
            </p>
          )
        )}
      </div>

      <aside className="conv-side">
        <section>
          <h3>Tutor</h3>
          {data.tutor ? (
            <>
              <p>
                <a href={"#/clientes/" + data.tutor.id}>{data.tutor.name ?? "Sem nome"}</a>
                {data.tutor.phone && <span className="muted small block">{data.tutor.phone}</span>}
              </p>
              <ul className="plain">
                {data.tutor.pets.map((p) => (
                  <li key={p.id}>
                    🐾 {p.name} <span className="muted small">{[speciesLabel(p.species), p.breed].filter(Boolean).join(" · ")}</span>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p className="muted small">Ainda sem cadastro: o assistente cria quando o tutor informa algum dado.</p>
          )}
        </section>
        <section>
          <h3>Solicitações</h3>
          {data.requests.length === 0 && <p className="muted small">Nenhuma.</p>}
          <ul className="plain">
            {data.requests.map((r) => (
              <li key={r.id}>
                <a href={"#/solicitacoes/" + r.id}>{requestTypeLabel[r.type]}</a> <RequestStatusBadge status={r.status} />
                {r.summary && <span className="muted small block">{r.summary}</span>}
              </li>
            ))}
          </ul>
        </section>
        <section>
          <h3>Notas internas</h3>
          <p className="muted small">Só a equipe vê. Nunca vão ao tutor nem ao assistente.</p>
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
          {active && <NoteBox onSave={(text) => act(() => api.post(`/conversations/${id}/notes`, { text }))} />}
        </section>
      </aside>
    </div>
  );
}

function ReplyBox({ onSend }: { onSend: (text: string) => Promise<void> }) {
  const [text, setText] = useState("");
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!text.trim()) return;
    await onSend(text.trim());
    setText("");
  };
  return (
    <form className="reply" onSubmit={submit}>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="Responder ao tutor…"
        rows={2}
        maxLength={2000}
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            void submit(e);
          }
        }}
      />
      <button className="primary">Enviar</button>
    </form>
  );
}

function NoteBox({ onSave }: { onSave: (text: string) => Promise<void> }) {
  const [text, setText] = useState("");
  return (
    <form
      className="stack"
      onSubmit={async (e) => {
        e.preventDefault();
        if (!text.trim()) return;
        await onSave(text.trim());
        setText("");
      }}
    >
      <textarea value={text} onChange={(e) => setText(e.target.value)} placeholder="Nova nota interna…" rows={2} maxLength={2000} />
      <button>Salvar nota</button>
    </form>
  );
}

export function TransferForm({
  departments,
  onSubmit,
}: {
  departments: { id: string; name: string }[];
  onSubmit: (departmentId: string, reason: string) => Promise<void>;
}) {
  const [department, setDepartment] = useState(departments[0]?.id ?? "");
  const [reason, setReason] = useState("");
  return (
    <form
      className="card inline-form"
      onSubmit={(e) => {
        e.preventDefault();
        void onSubmit(department, reason);
      }}
    >
      <label>
        Para o setor
        <select value={department} onChange={(e) => setDepartment(e.target.value)}>
          {departments.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
      </label>
      <label className="grow">
        Motivo (obrigatório)
        <input value={reason} onChange={(e) => setReason(e.target.value)} required maxLength={300} placeholder="Ex.: pergunta sobre sintoma" />
      </label>
      <button className="primary">Transferir</button>
    </form>
  );
}

function PriorityForm({ current, onSubmit }: { current: Priority; onSubmit: (p: Priority, note: string) => Promise<void> }) {
  const [priority, setPriority] = useState<Priority>(current);
  const [note, setNote] = useState("");
  return (
    <form
      className="card inline-form"
      onSubmit={(e) => {
        e.preventDefault();
        void onSubmit(priority, note);
      }}
    >
      <label>
        Prioridade
        <select value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
          {(Object.keys(priorityLabel) as Priority[]).map((p) => (
            <option key={p} value={p}>
              {priorityLabel[p]}
            </option>
          ))}
        </select>
      </label>
      <label className="grow">
        Motivo (fica registrado)
        <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
      </label>
      <button className="primary">Salvar</button>
    </form>
  );
}
