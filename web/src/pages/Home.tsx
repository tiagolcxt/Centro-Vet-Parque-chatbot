import { AlertTriangle, ChevronRight, Clock } from "lucide-react";
import { api } from "../api";
import { useApp, useLoad } from "../app-context";
import { QueueBadge, RiskBadge } from "../components/Badges";
import { handoffReasonLabel, minutesLabel, priorityShort, requestTypeLabel, timeLabel, activationModeHint, activationModeLabel } from "../labels";
import type { InboxItem, RequestItem } from "../types";
import type { Overview } from "../config/draft";
import { ValidityWarnings } from "./Config";

interface BasicMetrics {
  received: number;
  conversations: number;
  answered: number;
  answeredByAssistant: number;
  medianFirstReplySeconds: number | null;
  requestsCreated: number;
  requestsResolved: number;
  requestsOpen: number;
}

interface Operations {
  waitingNow: number;
  medianAssignSeconds: number | null;
  medianResolveSeconds: number | null;
  openByDepartment: { departmentId: string; name: string; open: number }[];
  activationLevel: number;
  activationLabel: string;
}

interface Usage {
  audioMessages: number;
  audioMinutes: number;
}


/** "2 min", "40 s", "1 h 5 min". */
export function durationLabel(seconds: number | null): string {
  if (seconds === null) return "—";
  if (seconds < 60) return `${seconds} s`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min`;
  return `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
}

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

  const metrics = useLoad(() => api.get<BasicMetrics>("/metrics/basic?days=7"), [liveTick]);
  const overview = useLoad(() => api.get<Overview>("/config/overview"), []);
  const operations = useLoad(() => api.get<Operations>("/metrics/operations?days=7"), [liveTick]);
  const usage = useLoad(() => api.get<Usage>("/channel/usage?days=7").catch(() => null), [liveTick]);

  const conversations = inbox.data ?? [];
  const urgent = conversations.filter(
    (c) => (c.priority === "P1_POSSIBLE_EMERGENCY" || c.priority === "P2_URGENT") && c.status === "WAITING_HUMAN",
  );
  const waiting = conversations.filter((c) => c.status === "WAITING_HUMAN");
  // "Atendimento humano" de quem já está esperando no chat é o mesmo trabalho: aparece só no quadro de conversas.
  const waitingIds = new Set(waiting.map((c) => c.id));
  const unassignedWork = (unassigned.data ?? []).filter((r) => !(r.type === "HUMAN_CONTACT" && waitingIds.has(r.conversationId)));
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
              <RiskBadge risk={c.clinicalRisk} />
              <strong>{c.tutorName ?? "Tutor sem nome"}</strong>
              <span className="muted grow ellipsis">
                {/* O selo já diz a urgência: o motivo só aparece quando acrescenta algo. */}
                {c.handoffReason && c.handoffReason !== "CLINICAL_PRIORITY" && c.handoffReason !== "POSSIBLE_EMERGENCY"
                  ? handoffReasonLabel[c.handoffReason] ?? c.handoffReason
                  : ""}
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
            <h2>Minhas pendências</h2>
            <span className="stat">{mineCount}</span>
          </header>
          <p className="muted small">Conversas e solicitações sob sua responsabilidade.</p>
          {mineCount === 0 && <p className="muted small">Nada sob sua responsabilidade agora.</p>}
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
            <h2>Tutores esperando resposta</h2>
            <span className="stat">{waiting.length}</span>
          </header>
          <p className="muted small">O tutor está no chat esperando alguém da equipe escrever. Assuma a conversa para responder.</p>
          {waiting.length === 0 && <p className="muted small">Nenhum tutor esperando a equipe.</p>}
          <ul className="mini-list">
            {waiting.slice(0, 5).map((c) => (
              <ConversationLine key={c.id} c={c} />
            ))}
          </ul>
        </section>
        <section className="card stat-card">
          <header>
            <h2>Solicitações sem responsável</h2>
            <span className="stat">{unassignedWork.length}</span>
          </header>
          <p className="muted small">
            Tarefas para a equipe (agendar, passar uma informação) que ninguém pegou. O tutor não está esperando no chat.
          </p>
          {unassignedWork.length === 0 && <p className="muted small">Todas as solicitações têm responsável.</p>}
          <ul className="mini-list">
            {unassignedWork.slice(0, 5).map((r) => (
              <RequestLine key={r.id} r={r} departmentName={departmentName} />
            ))}
          </ul>
        </section>
      </div>
      {can("config:edit") && overview.data && (
        <>
          <ValidityWarnings overview={overview.data} />
          {overview.data.draftDiffers && (
            <p className="small">
              Há um rascunho da configuração ainda não publicado. <a href="#/configuracao/inicio">Revisar e publicar</a>
            </p>
          )}
          {!overview.data.published && (
            <p className="small">
              O assistente ainda não tem informações da clínica. <a href="#/configuracao/primeiros-passos">Configurar agora</a>
            </p>
          )}
        </>
      )}

      {metrics.data && (
        <section className="card metrics" aria-label="Últimos 7 dias">
          <header>
            <h2>Últimos 7 dias</h2>
          </header>
          <dl className="metric-grid">
            <div>
              <dt>Mensagens recebidas</dt>
              <dd>{metrics.data.received}</dd>
            </div>
            <div>
              <dt>Conversas respondidas</dt>
              <dd>
                {metrics.data.answered} de {metrics.data.conversations}
              </dd>
            </div>
            <div>
              <dt>Respondidas só pelo assistente</dt>
              <dd>{metrics.data.answeredByAssistant}</dd>
            </div>
            <div>
              <dt title="Do assistente ou da equipe">Tempo até a primeira resposta</dt>
              <dd>{durationLabel(metrics.data.medianFirstReplySeconds)}</dd>
            </div>
            <div>
              <dt>Solicitações abertas na semana</dt>
              <dd>{metrics.data.requestsCreated}</dd>
            </div>
            <div>
              <dt>Solicitações concluídas</dt>
              <dd>{metrics.data.requestsResolved}</dd>
            </div>
          </dl>
        </section>
      )}

      {operations.data && (
        <section className="card metrics" aria-label="Atendimento da equipe">
          <header>
            <h2>Atendimento da equipe</h2>
            <p className="muted small">
              Modo do assistente: <strong>{activationModeLabel[operations.data.activationLevel] ?? operations.data.activationLabel}</strong>.{" "}
              {activationModeHint[operations.data.activationLevel] ?? ""}
            </p>
          </header>
          <dl className="metric-grid">
            <div>
              <dt>Tutores esperando resposta agora</dt>
              <dd>{operations.data.waitingNow}</dd>
            </div>
            <div>
              <dt>Tempo até a solicitação ter responsável</dt>
              <dd>{durationLabel(operations.data.medianAssignSeconds)}</dd>
            </div>
            <div>
              <dt>Tempo até concluir</dt>
              <dd>{durationLabel(operations.data.medianResolveSeconds)}</dd>
            </div>
            {operations.data.openByDepartment.map((d) => (
              <div key={d.departmentId}>
                <dt>Abertas em {d.name}</dt>
                <dd>{d.open}</dd>
              </div>
            ))}
            {(usage.data?.audioMessages ?? 0) > 0 && (
              <div>
                <dt>Áudios transcritos</dt>
                <dd>
                  {usage.data!.audioMessages} · {usage.data!.audioMinutes.toLocaleString("pt-BR")} min
                </dd>
              </div>
            )}
          </dl>
          <p className="muted small">Tempos: valor do meio das solicitações abertas nos últimos 7 dias.</p>
        </section>
      )}
      {urgent.length === 0 && conversations.some((c) => c.priority === "P1_POSSIBLE_EMERGENCY") && (
        <p className="muted small">
          <AlertTriangle size={14} aria-hidden /> Há possível emergência já em atendimento: veja em "Minhas pendências" ou em Conversas.
        </p>
      )}
    </div>
  );
}

/** Mesmo formato do cartão da tela Conversas: nome e espera; um selo e o motivo. */
function ConversationLine({ c }: { c: InboxItem }) {
  const strong = c.clinicalRisk === "POSSIBLE_EMERGENCY" || c.clinicalRisk === "URGENT";
  return (
    <li>
      <a href={"#/atendimento/" + c.id} className="mini-row mini-card">
        <span className="mini-top">
          <strong className="ellipsis">{c.tutorName ?? "Tutor sem nome"}</strong>
          <span className={"wait" + (c.overdue ? " overdue" : "")}>
            {c.overdue ? <AlertTriangle size={12} aria-hidden /> : <Clock size={12} aria-hidden />}
            {c.status === "WAITING_HUMAN" ? shortWait(c.waitingMinutes ?? 0) : "em atendimento"}
            {c.overdue && <span className="sr-only"> · passou do prazo</span>}
          </span>
        </span>
        <span className="mini-bottom">
          {strong ? <RiskBadge risk={c.clinicalRisk} /> : <QueueBadge priority={c.operationalPriority} />}
          <span className="muted small ellipsis">
            {c.handoffReason && !(strong && (c.handoffReason === "CLINICAL_PRIORITY" || c.handoffReason === "POSSIBLE_EMERGENCY"))
              ? handoffReasonLabel[c.handoffReason] ?? c.handoffReason
              : ""}
          </span>
        </span>
      </a>
    </li>
  );
}

function shortWait(minutes: number): string {
  return minutes >= 1440 ? `${Math.floor(minutes / 1440)} d` : minutesLabel(minutes);
}

function RequestLine({ r, departmentName }: { r: RequestItem; departmentName: (id: string) => string }) {
  const who = r.petName ?? r.tutorName;
  const strong = r.clinicalRisk === "POSSIBLE_EMERGENCY" || r.clinicalRisk === "URGENT";
  return (
    <li>
      <a href={"#/solicitacoes/" + r.id} className="mini-row mini-card">
        <span className="mini-top">
          <strong className="ellipsis">
            {requestTypeLabel[r.type]}
            {who ? ` · ${who}` : ""}
          </strong>
          <span className="wait" title="Desde quando existe">
            <Clock size={12} aria-hidden />
            {timeLabel(r.createdAt)}
          </span>
        </span>
        <span className="mini-bottom">
          {strong && <RiskBadge risk={r.clinicalRisk} />}
          <span className="muted small ellipsis">{departmentName(r.departmentId)}</span>
        </span>
      </a>
    </li>
  );
}
