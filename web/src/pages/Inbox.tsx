import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { AlertTriangle, ArrowLeft, ArrowRightLeft, Bot, Check, CheckCheck, Clock, Flag, Lock, PanelRight, Paperclip, PawPrint, RotateCw, Send, X, CheckCircle2, Zap } from "lucide-react";
import { api } from "../api";
import { go, useApp, useLoad } from "../app-context";
import { QueueBadge, RequestStatusBadge, RiskBadge, StatusBadge } from "../components/Badges";
import { handoffReasonLabel, minutesLabel, queueLabel, requestTypeLabel, riskLabel, speciesLabel, timeLabel } from "../labels";
import type { ClinicalRisk, ConversationDetail, InboxItem, MessageItem, QueuePriority } from "../types";
import { Drawer, useHotkeys, useToast } from "../ui";
import type { QuickReply } from "../config/tools";

type Filter = "WAITING" | "MINE" | "ALL";
const FILTERS: { id: Filter; label: string }[] = [
  { id: "WAITING", label: "Aguardando" },
  { id: "MINE", label: "Comigo" },
  { id: "ALL", label: "Todas" },
];

const ATTACHMENTS: Record<NonNullable<MessageItem["attachment"]>["kind"], string> = {
  IMAGE: "Imagem",
  DOCUMENT: "Documento",
  AUDIO: "Áudio",
  VIDEO: "Vídeo",
  OTHER: "Arquivo",
};

function attachmentLabel(kind: NonNullable<MessageItem["attachment"]>["kind"]): string {
  return ATTACHMENTS[kind] ?? "Arquivo";
}

/** Situação do envio pelo WhatsApp, no canto da mensagem (RF-S55). */
function DeliveryMark({ delivery }: { delivery: NonNullable<MessageItem["delivery"]> }) {
  const label = {
    PENDING: delivery.attempts > 0 ? `enviando (tentativa ${delivery.attempts + 1})` : "enviando",
    SENT: "enviada",
    DELIVERED: "entregue",
    READ: "lida",
    FAILED: "não enviada" + (delivery.errorCode ? ` (erro ${delivery.errorCode})` : ""),
  }[delivery.status];
  const icon =
    delivery.status === "FAILED" ? <AlertTriangle size={12} aria-hidden />
    : delivery.status === "PENDING" ? <Clock size={12} aria-hidden />
    : delivery.status === "SENT" ? <Check size={12} aria-hidden />
    : <CheckCheck size={12} aria-hidden />;
  return (
    <span className={"delivery delivery-" + delivery.status.toLowerCase()} title={label}>
      {" · "}
      {icon} {label}
    </span>
  );
}

/** Caixa de entrada (RF-S20 a RF-S29), no visual do protótipo (ux-navegacao.md, seção 8). */
export function Inbox({ selectedId }: { selectedId?: string }) {
  const { liveTick } = useApp();
  const [filter, setFilter] = useState<Filter>("WAITING");
  const lists = {
    WAITING: useLoad(() => api.get<InboxItem[]>("/conversations?filter=WAITING"), [liveTick]),
    MINE: useLoad(() => api.get<InboxItem[]>("/conversations?filter=MINE"), [liveTick]),
    ALL: useLoad(() => api.get<InboxItem[]>("/conversations?filter=ALL"), [liveTick]),
  };
  const list = lists[filter];
  const items = list.data ?? [];
  const reloadAll = () => Object.values(lists).forEach((l) => l.reload());

  // J/K percorrem a lista (atalhos do protótipo).
  const move = (delta: number) => {
    if (items.length === 0) return;
    const index = items.findIndex((c) => c.id === selectedId);
    const next = items[Math.min(items.length - 1, Math.max(0, index < 0 ? 0 : index + delta))];
    go("atendimento/" + next.id);
  };
  useHotkeys({ j: () => move(1), k: () => move(-1) });

  return (
    <div className={"split" + (selectedId ? " has-detail" : "")}>
      <aside className="split-list">
        <div className="tabs" role="tablist">
          {FILTERS.map((f) => (
            <button key={f.id} role="tab" aria-selected={filter === f.id} onClick={() => setFilter(f.id)}>
              {f.label} <span className="count">{lists[f.id].data?.length ?? ""}</span>
            </button>
          ))}
        </div>
        {list.error && <p className="error">{list.error}</p>}
        {list.loading && !list.data && <SkeletonList />}
        {list.data && items.length === 0 && <p className="empty">Nenhuma conversa aqui.</p>}
        <ul className="list">
          {items.map((c) => (
            <li key={c.id}>
              <a
                href={"#/atendimento/" + c.id}
                className={"conv-item" + (c.id === selectedId ? " selected" : "")}
                aria-current={c.id === selectedId ? "true" : undefined}
              >
                <div className="conv-item-top">
                  <RiskBadge risk={c.clinicalRisk} />
                  <QueueBadge priority={c.operationalPriority} />
                  <span className={"wait" + (c.overdue ? " overdue" : "")}>
                    <Clock size={12} aria-hidden />
                    {c.waitingMinutes !== null ? minutesLabel(c.waitingMinutes) : timeLabel(c.lastActivityAt)}
                    {c.overdue ? " · passou do prazo" : ""}
                  </span>
                </div>
                <strong>{c.tutorName ?? "Contato sem nome"}</strong>
                <span className="muted small">
                  {c.handoffReason ? handoffReasonLabel[c.handoffReason] ?? c.handoffReason : "Com o assistente"}
                </span>
                <span className="muted small">
                  {c.status === "ASSIGNED" ? `Assumida por ${c.assigneeName ?? "alguém"}` : c.status === "WAITING_HUMAN" ? "Aguardando a equipe" : "Com o assistente"}
                </span>
              </a>
            </li>
          ))}
        </ul>
      </aside>
      <section className="split-detail">
        {selectedId ? (
          <Conversation id={selectedId} onChanged={reloadAll} />
        ) : (
          <p className="empty center-v">
            Escolha uma conversa na lista.
            <br />
            Use J e K para navegar.
          </p>
        )}
      </section>
    </div>
  );
}

function SkeletonList() {
  return (
    <ul className="list" aria-hidden>
      {[0, 1, 2].map((i) => (
        <li key={i} className="skeleton-row">
          <span className="skeleton w-40" />
          <span className="skeleton w-70" />
          <span className="skeleton w-50" />
        </li>
      ))}
    </ul>
  );
}

function Conversation({ id, onChanged }: { id: string; onChanged: () => void }) {
  const { me, can, departments, departmentName, liveTick } = useApp();
  const toast = useToast();
  const { data, error, reload } = useLoad(() => api.get<ConversationDetail>("/conversations/" + id), [id, liveTick]);
  const [panel, setPanel] = useState<"none" | "transfer" | "priority">("none");
  const [sideOpen, setSideOpen] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);
  const replyRef = useRef<HTMLTextAreaElement>(null);
  const noteRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [data?.messages.length]);
  useEffect(() => setPanel("none"), [id]);

  const act = useCallback(
    async (fn: () => Promise<unknown>, done: string) => {
      try {
        await fn();
        setPanel("none");
        toast(done);
        reload();
        onChanged();
      } catch (e) {
        toast((e as Error).message, "error");
      }
    },
    [toast, reload, onChanged],
  );

  const mine = data?.status === "ASSIGNED" && data.assigneeId === me.id;
  const active = !!data && data.status !== "CLOSED";
  const canAssume = active && can("conversation:assume") && !mine && data?.status !== "ASSIGNED";
  // Aguardando a equipe também pode ser transferida ou devolvida sem assumir (triagem da recepção).
  const canTransfer = active && can("conversation:transfer") && data?.status !== "OPEN" && (mine || data?.status === "WAITING_HUMAN");
  const assume = () => canAssume && act(() => api.post(`/conversations/${id}/assume`), "Conversa assumida");
  const windowClosed = !!data?.replyWindowEndsAt && Date.parse(data.replyWindowEndsAt) < Date.now();

  useHotkeys({
    a: () => void assume(),
    r: () => replyRef.current?.focus(),
    t: () => canTransfer && setPanel("transfer"),
    n: () => {
      if (window.matchMedia("(max-width: 1279px)").matches) setSideOpen(true);
      window.setTimeout(() => noteRef.current?.focus(), 50);
    },
    Escape: () => {
      setPanel("none");
      setSideOpen(false);
    },
  });

  if (error) {
    return (
      <div className="pad">
        <button className="link back" onClick={() => go("atendimento")}>
          <ArrowLeft size={16} aria-hidden /> Conversas
        </button>
        <p className="error">{error}</p>
      </div>
    );
  }
  if (!data) return <p className="empty center-v">Carregando…</p>;

  const side = <SidePanel data={data} noteRef={noteRef} onNote={(text) => act(() => api.post(`/conversations/${id}/notes`, { text }), "Nota salva")} active={active} />;

  return (
    <div className="conversation">
      <div className="conv-main">
        <header className="conv-header">
          <div className="conv-title-row">
            <button className="icon-button back" onClick={() => go("atendimento")} aria-label="Voltar para as conversas">
              <ArrowLeft size={18} />
            </button>
            <div className="grow">
              <h2>{data.tutor?.name ?? "Contato sem nome"}{data.tutor?.pets[0] ? ` · ${data.tutor.pets[0].name}` : ""}</h2>
              <div className="badges">
                <RiskBadge risk={data.clinicalRisk} />
                <QueueBadge priority={data.operationalPriority} fixed={!!data.operationalOverride} />
                <StatusBadge status={data.status} assignee={data.assigneeName} />
                <span className="badge">{departmentName(data.departmentId)}</span>
                {data.channel === "WHATSAPP" && <span className="badge badge-whatsapp">WhatsApp</span>}
              </div>
            </div>
            <button className="icon-button side-toggle" onClick={() => setSideOpen(true)} aria-label="Tutor, pet e notas">
              <PanelRight size={18} />
            </button>
          </div>
          {active && (
            <div className="actions">
              {canAssume && (
                <button className="primary" onClick={() => void assume()}>
                  Assumir <kbd>A</kbd>
                </button>
              )}
              {can("conversation:transfer") && data.status !== "OPEN" && (
                <button disabled={!canTransfer} onClick={() => setPanel(panel === "transfer" ? "none" : "transfer")}>
                  <ArrowRightLeft size={16} aria-hidden /> Transferir
                </button>
              )}
              {can("conversation:transfer") && data.status !== "OPEN" && (
                <button
                  disabled={!canTransfer}
                  onClick={() => act(() => api.post(`/conversations/${id}/return-to-assistant`, { reason: null }), "Conversa devolvida ao assistente")}
                >
                  <Bot size={16} aria-hidden /> Devolver ao assistente
                </button>
              )}
              {can("conversation:assume") && (
                <button onClick={() => setPanel(panel === "priority" ? "none" : "priority")}>
                  <Flag size={16} aria-hidden /> Prioridade
                </button>
              )}
              {can("conversation:assume") && (
                <button
                  className="quiet"
                  onClick={() =>
                    window.confirm("Encerrar esta conversa? Os pedidos continuam abertos.") &&
                    act(() => api.post(`/conversations/${id}/close`), "Conversa encerrada")
                  }
                >
                  Encerrar
                </button>
              )}
            </div>
          )}
          {panel === "transfer" && (
            <TransferPanel
              departments={departments.filter(
                (d) => d.active && d.id !== (data.departmentId ?? departments.find((x) => x.isDefault)?.id),
              )}
              onCancel={() => setPanel("none")}
              onSubmit={(departmentId, reason) =>
                act(() => api.post(`/conversations/${id}/transfer`, { departmentId, reason }), "Conversa transferida")
              }
            />
          )}
          {panel === "priority" && (
            <PriorityPanel
              risk={data.clinicalRisk}
              queue={data.operationalOverride}
              onCancel={() => setPanel("none")}
              onRisk={(risk, note) => act(() => api.patch(`/conversations/${id}/priority`, { risk, note }), "Risco clínico alterado")}
              onQueue={(priority) =>
                act(() => api.patch(`/conversations/${id}/queue-priority`, { priority }), priority ? "Prioridade da fila fixada" : "Fila voltou para a regra")
              }
            />
          )}
        </header>

        <div className="messages" aria-live="polite">
          {data.messages.map((m) => (
            <div
              key={m.id}
              className={"msg msg-" + m.author.toLowerCase().replace("_", "-") + (m.fallback ? " msg-fallback" : "") + (m.delivery?.status === "FAILED" ? " msg-failed" : "")}
            >
              <div className="msg-meta">{m.authorName}</div>
              <div className="msg-text">{m.content}</div>
              {m.attachment && (
                <a className="msg-attachment" href={`/app/api/channel/media/${m.attachment.ref}`} target="_blank" rel="noreferrer">
                  <Paperclip size={14} aria-hidden /> {attachmentLabel(m.attachment.kind)}
                  {m.attachment.name ? `: ${m.attachment.name}` : ""}
                </a>
              )}
              <div className="msg-time">
                {m.fallback ? "resposta segura · " : ""}
                {timeLabel(m.at)}
                {m.delivery && <DeliveryMark delivery={m.delivery} />}
              </div>
              {m.delivery?.status === "FAILED" && can("conversation:reply") && (
                <button
                  className="link msg-retry"
                  onClick={() => act(() => api.post(`/channel/messages/${m.id}/resend`), "Vamos tentar enviar de novo")}
                >
                  <RotateCw size={13} aria-hidden /> Tentar enviar de novo
                </button>
              )}
            </div>
          ))}
          <div ref={endRef} />
        </div>

        {active && mine && windowClosed ? (
          <div className="reply reply-locked">
            <span className="muted">
              <Clock size={14} aria-hidden /> Passaram 24 horas desde a última mensagem do tutor. O WhatsApp não deixa
              responder por aqui agora: ligue para o tutor ou espere ele escrever de novo.
            </span>
          </div>
        ) : active &&
          (mine && can("conversation:reply") ? (
            <ReplyBox inputRef={replyRef} onSend={(text) => act(() => api.post(`/conversations/${id}/reply`, { text }), "Mensagem enviada")} />
          ) : (
            <div className="reply reply-locked">
              <span className="muted">
                {data.status === "ASSIGNED"
                  ? `${data.assigneeName ?? "Outra pessoa"} está com esta conversa.`
                  : "Para responder, assuma a conversa."}
              </span>
              {canAssume && (
                <button className="primary" onClick={() => void assume()}>
                  Assumir
                </button>
              )}
            </div>
          ))}
      </div>

      <aside className="conv-side">{side}</aside>
      <Drawer title="Tutor e pet" open={sideOpen} onClose={() => setSideOpen(false)}>
        {side}
      </Drawer>
    </div>
  );
}

function SidePanel({
  data,
  noteRef,
  onNote,
  active,
}: {
  data: ConversationDetail;
  noteRef: React.RefObject<HTMLTextAreaElement | null>;
  onNote: (text: string) => Promise<void>;
  active: boolean;
}) {
  const [note, setNote] = useState("");
  return (
    <div className="side-sections">
      <section>
        <h3 className="eyebrow">Tutor</h3>
        {data.tutor ? (
          <>
            <a href={"#/clientes/" + data.tutor.id} className="strong-link">
              {data.tutor.name ?? "Sem nome"}
            </a>
            {data.tutor.phone && <span className="muted small block">{data.tutor.phone}</span>}
          </>
        ) : (
          <p className="muted small">Ainda sem cadastro: o assistente cria quando o tutor informa algum dado.</p>
        )}
      </section>
      {data.tutor && data.tutor.pets.length > 0 && (
        <section>
          <h3 className="eyebrow">Pets</h3>
          {data.tutor.pets.map((p) => (
            <div key={p.id} className="pet-card">
              <PawPrint size={16} aria-hidden />
              <div>
                <strong>{p.name}</strong>
                <span className="muted small block">{[speciesLabel(p.species), p.breed].filter(Boolean).join(" · ")}</span>
              </div>
            </div>
          ))}
        </section>
      )}
      <section>
        <h3 className="eyebrow">Pedidos desta conversa</h3>
        {data.requests.length === 0 && <p className="muted small">Nenhum.</p>}
        {data.requests.map((r) => (
          <a key={r.id} href={"#/solicitacoes/" + r.id} className="request-chip">
            <span className="grow">{requestTypeLabel[r.type]}</span>
            <RequestStatusBadge status={r.status} />
          </a>
        ))}
      </section>
      <section className="notes-box">
        <h3>
          <Lock size={14} aria-hidden /> Notas internas
        </h3>
        <p className="small">Só a equipe vê. Nunca vão ao tutor nem ao assistente.</p>
        {data.notes.map((n) => (
          <div key={n.id} className="note">
            <span className="small note-meta">
              {n.authorName ?? "Sistema"} · {timeLabel(n.at)}
            </span>
            <span className="block">{n.text}</span>
          </div>
        ))}
        {active && (
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              if (!note.trim()) return;
              await onNote(note.trim());
              setNote("");
            }}
          >
            <textarea
              ref={noteRef}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Escreva uma nota para a equipe (N)"
              rows={2}
              maxLength={2000}
            />
            {note.trim() && <button className="small-button">Salvar nota</button>}
          </form>
        )}
      </section>
    </div>
  );
}

function ReplyBox({ onSend, inputRef }: { onSend: (text: string) => Promise<void>; inputRef: React.RefObject<HTMLTextAreaElement | null> }) {
  const [text, setText] = useState("");
  const [picking, setPicking] = useState(false);
  const quick = useLoad(() => api.get<QuickReply[]>("/quick-replies").catch(() => []), []);
  useEffect(() => {
    inputRef.current?.focus();
  }, [inputRef]);
  // Resposta pronta (S3-16): entra no texto para revisar antes de enviar, nunca é enviada direto.
  const insert = (reply: QuickReply) => {
    setText((current) => (current.trim() ? current.trimEnd() + "\n" + reply.body : reply.body));
    setPicking(false);
    inputRef.current?.focus();
  };
  const submit = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!text.trim()) return;
    await onSend(text.trim());
    setText("");
  };
  return (
    <form className="reply" onSubmit={submit}>
      {(quick.data?.length ?? 0) > 0 && (
        <div className="quick-replies">
          <button
            type="button"
            className="icon-button"
            aria-label="Respostas prontas"
            title="Respostas prontas"
            aria-expanded={picking}
            onClick={() => setPicking((p) => !p)}
          >
            <Zap size={18} aria-hidden />
          </button>
          {picking && (
            <div className="popover quick-menu" role="menu">
              {quick.data!.map((r) => (
                <button type="button" role="menuitem" key={r.id} onClick={() => insert(r)} title={r.body}>
                  <strong>{r.title}</strong>
                  <span className="muted small ellipsis block">{r.body}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
      <textarea
        ref={inputRef}
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="Escreva para o tutor. Enter envia, Shift+Enter quebra linha"
        rows={1}
        maxLength={2000}
        aria-label="Resposta ao tutor"
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            void submit();
          }
        }}
      />
      <button className="primary icon-only" aria-label="Enviar">
        <Send size={18} />
      </button>
    </form>
  );
}

/** Transferir: setor em botões + motivo obrigatório (2 cliques + motivo). */
export function TransferPanel({
  departments,
  onSubmit,
  onCancel,
}: {
  departments: { id: string; name: string }[];
  onSubmit: (departmentId: string, reason: string) => Promise<void>;
  onCancel: () => void;
}) {
  const [department, setDepartment] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const reasonRef = useRef<HTMLInputElement>(null);
  return (
    <form
      className="inline-panel"
      onSubmit={(e) => {
        e.preventDefault();
        if (department && reason.trim()) void onSubmit(department, reason.trim());
      }}
    >
      <div className="inline-panel-head">
        <strong>Transferir para</strong>
        <button type="button" className="icon-button" onClick={onCancel} aria-label="Cancelar">
          <X size={16} />
        </button>
      </div>
      <div className="chips" role="radiogroup" aria-label="Setor">
        {departments.map((d) => (
          <button
            type="button"
            key={d.id}
            role="radio"
            aria-checked={department === d.id}
            className={"chip" + (department === d.id ? " chip-selected" : "")}
            onClick={() => {
              setDepartment(d.id);
              reasonRef.current?.focus();
            }}
          >
            {d.name}
          </button>
        ))}
      </div>
      {department && (
        <div className="inline-row">
          <input
            ref={reasonRef}
            autoFocus
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Motivo (obrigatório)"
            maxLength={300}
            aria-label="Motivo da transferência"
          />
          <button className="primary" disabled={!reason.trim()}>
            Transferir
          </button>
        </div>
      )}
    </form>
  );
}

const RISK_CHOICES: ClinicalRisk[] = ["POSSIBLE_EMERGENCY", "URGENT", "CLINICAL_FOLLOW_UP", "NONE_IDENTIFIED"];
const QUEUE_CHOICES: QueuePriority[] = ["IMMEDIATE", "HIGH", "NORMAL", "LOW"];

/**
 * D9: duas coisas diferentes. O risco clínico (rebaixar exige motivo e fica registrado, H4) e a prioridade da fila
 * (só a ordem do atendimento; "automática" segue a regra).
 */
function PriorityPanel({
  risk,
  queue,
  onRisk,
  onQueue,
  onCancel,
}: {
  risk: ClinicalRisk;
  queue: QueuePriority | null;
  onRisk: (risk: ClinicalRisk, note: string) => Promise<void>;
  onQueue: (priority: QueuePriority | null) => Promise<void>;
  onCancel: () => void;
}) {
  const [chosen, setChosen] = useState<ClinicalRisk>(risk);
  const [note, setNote] = useState("");
  return (
    <form
      className="inline-panel"
      onSubmit={(e) => {
        e.preventDefault();
        void onRisk(chosen, note);
      }}
    >
      <div className="inline-panel-head">
        <strong>Risco clínico</strong>
        <button type="button" className="icon-button" onClick={onCancel} aria-label="Cancelar">
          <X size={16} />
        </button>
      </div>
      <div className="chips">
        {RISK_CHOICES.map((r) => (
          <button type="button" key={r} className={"chip" + (chosen === r ? " chip-selected" : "")} onClick={() => setChosen(r)}>
            {riskLabel[r]}
          </button>
        ))}
      </div>
      <div className="inline-row">
        <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} placeholder="Motivo (fica registrado)" aria-label="Motivo da mudança" />
        <button className="primary" disabled={chosen === risk}>
          <CheckCircle2 size={16} aria-hidden /> Salvar risco
        </button>
      </div>
      <div className="inline-panel-head">
        <strong>Prioridade na fila</strong>
      </div>
      <p className="muted small">Muda só a ordem do atendimento. Automática segue o risco e o tempo de espera.</p>
      <div className="chips">
        <button type="button" className={"chip" + (queue === null ? " chip-selected" : "")} onClick={() => void onQueue(null)}>
          Automática
        </button>
        {QUEUE_CHOICES.map((q) => (
          <button type="button" key={q} className={"chip" + (queue === q ? " chip-selected" : "")} onClick={() => void onQueue(q)}>
            {queueLabel[q]}
          </button>
        ))}
      </div>
    </form>
  );
}
