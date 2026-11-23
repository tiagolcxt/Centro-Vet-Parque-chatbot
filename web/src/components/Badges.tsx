import { AlertTriangle, ArrowDown, ArrowUp, Bot, ChevronsUp, CircleDot, Clock, HelpCircle, Stethoscope, UserCheck, CheckCircle2, XCircle } from "lucide-react";
import { conversationStatusLabel, priorityLabel, priorityShort, priorityTone, queueLabel, requestStatusLabel, riskLabel } from "../labels";
import type { ClinicalRisk, ConversationStatus, Priority, QueuePriority, RequestStatus } from "../types";

/**
 * Risco clínico (D9) com ícone, texto e cor (RNF-S11: nunca só a cor). "Sem sinal clínico" não aparece: não é
 * informação útil na fila.
 */
export function RiskBadge({ risk }: { risk: ClinicalRisk | null | undefined }) {
  if (!risk || risk === "NONE_IDENTIFIED") return null;
  const tone = risk === "POSSIBLE_EMERGENCY" ? "critical" : risk === "URGENT" ? "warning" : "neutral";
  const Icon = risk === "NOT_ASSESSED" ? HelpCircle : risk === "CLINICAL_FOLLOW_UP" ? Stethoscope : AlertTriangle;
  return (
    <span className={"badge badge-" + tone} title={"Risco clínico: " + riskLabel[risk]}>
      <Icon size={12} aria-hidden />
      {riskLabel[risk]}
    </span>
  );
}

/** Prioridade da fila (D9). Normal não aparece; "fixada" quando a equipe escolheu. */
export function QueueBadge({ priority, fixed = false }: { priority: QueuePriority | null | undefined; fixed?: boolean }) {
  if (!priority || (priority === "NORMAL" && !fixed)) return null;
  const Icon = priority === "IMMEDIATE" ? ChevronsUp : priority === "HIGH" ? ArrowUp : priority === "LOW" ? ArrowDown : CircleDot;
  return (
    <span className={"badge" + (priority === "IMMEDIATE" ? " badge-critical" : "")} title="Prioridade na fila">
      <Icon size={12} aria-hidden />
      Fila: {queueLabel[priority]}
      {fixed ? " (fixada)" : ""}
    </span>
  );
}

/** Prioridade com ícone, texto e cor (RNF-S11: nunca só a cor). */
export function PriorityBadge({ priority, long = false }: { priority: Priority | null; long?: boolean }) {
  if (!priority) return <span className="badge">—</span>;
  const tone = priorityTone(priority);
  return (
    <span className={"badge badge-" + tone} title={priorityLabel[priority]}>
      {tone === "critical" && <AlertTriangle size={12} aria-hidden />}
      {long ? priorityLabel[priority] : priorityShort(priority)}
    </span>
  );
}

const conversationIcon = {
  OPEN: Bot,
  WAITING_HUMAN: Clock,
  ASSIGNED: UserCheck,
  CLOSED: CheckCircle2,
};

export function StatusBadge({ status, assignee }: { status: ConversationStatus; assignee?: string | null }) {
  const Icon = conversationIcon[status];
  return (
    <span className={"badge badge-status-" + status.toLowerCase()}>
      <Icon size={12} aria-hidden />
      {status === "ASSIGNED" && assignee ? `Assumida por ${assignee.split(" ")[0]}` : conversationStatusLabel[status]}
    </span>
  );
}

const requestIcon = {
  COLLECTING: CircleDot,
  PENDING: Clock,
  IN_PROGRESS: UserCheck,
  RESOLVED: CheckCircle2,
  CANCELLED: XCircle,
};

export function RequestStatusBadge({ status }: { status: RequestStatus }) {
  const Icon = requestIcon[status];
  return (
    <span className={"badge badge-status-" + status.toLowerCase()}>
      <Icon size={12} aria-hidden />
      {requestStatusLabel[status]}
    </span>
  );
}
