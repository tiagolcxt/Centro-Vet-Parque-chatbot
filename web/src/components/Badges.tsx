import { conversationStatusLabel, priorityLabel, priorityShort, priorityTone, requestStatusLabel } from "../labels";
import type { ConversationStatus, Priority, RequestStatus } from "../types";

/** Prioridade com texto e cor (RNF-S11: nunca só a cor). */
export function PriorityBadge({ priority, long = false }: { priority: Priority | null; long?: boolean }) {
  if (!priority) return <span className="badge">—</span>;
  return (
    <span className={"badge badge-" + priorityTone(priority)} title={priorityLabel[priority]}>
      {long ? priorityLabel[priority] : priorityShort(priority)}
    </span>
  );
}

export function StatusBadge({ status }: { status: ConversationStatus }) {
  return <span className={"badge badge-status-" + status.toLowerCase()}>{conversationStatusLabel[status]}</span>;
}

export function RequestStatusBadge({ status }: { status: RequestStatus }) {
  return <span className={"badge badge-status-" + status.toLowerCase()}>{requestStatusLabel[status]}</span>;
}
