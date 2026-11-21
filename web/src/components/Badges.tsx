import { AlertTriangle, Bot, CircleDot, Clock, UserCheck, CheckCircle2, XCircle } from "lucide-react";
import { conversationStatusLabel, priorityLabel, priorityShort, priorityTone, requestStatusLabel } from "../labels";
import type { ConversationStatus, Priority, RequestStatus } from "../types";

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
