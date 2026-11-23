// Contratos da API da equipe. Espelham os DTOs do backend (DM-11); só os campos que a tela usa.

export type Priority =
  | "P1_POSSIBLE_EMERGENCY"
  | "P2_URGENT"
  | "P3_CLINICAL_FOLLOW_UP"
  | "P4_SERVICE_REQUEST"
  | "P5_ADMINISTRATIVE"
  | "P6_RELATIONSHIP";

export type ConversationStatus = "OPEN" | "WAITING_HUMAN" | "ASSIGNED" | "CLOSED";
export type RequestStatus = "COLLECTING" | "PENDING" | "IN_PROGRESS" | "RESOLVED" | "CANCELLED";
export type RequestType = "APPOINTMENT" | "INFORMATION" | "FOLLOW_UP" | "HUMAN_CONTACT";
export type Outcome =
  | "SCHEDULED_BY_STAFF"
  | "INFORMATION_PROVIDED"
  | "HANDLED_OUTSIDE"
  | "NO_AVAILABILITY"
  | "CUSTOMER_GAVE_UP"
  | "NO_RESPONSE"
  | "DUPLICATE"
  | "OTHER";

export interface Session {
  userId: string;
  clinicId: string;
  email: string;
  roleId: string;
  sectorScope: "OWN" | "ALL";
  permissions: string[];
}

/** D9 (revisão pré-piloto 3.2): risco clínico, prioridade da fila e tipo da demanda, separados. */
export type ClinicalRisk = "POSSIBLE_EMERGENCY" | "URGENT" | "CLINICAL_FOLLOW_UP" | "NONE_IDENTIFIED" | "NOT_ASSESSED";
export type QueuePriority = "IMMEDIATE" | "HIGH" | "NORMAL" | "LOW";
export type DemandType =
  | "APPOINTMENT"
  | "CLINICAL_QUESTION"
  | "ADMINISTRATIVE_INFO"
  | "COMMERCIAL"
  | "GROOMING"
  | "COMPLAINT"
  | "HUMAN_CONTACT"
  | "OTHER";

export interface Me {
  id: string;
  displayName: string;
  email: string;
  availability: "AVAILABLE" | "AWAY";
  departmentIds: string[];
}

export interface Department {
  id: string;
  name: string;
  kind: string;
  active: boolean;
  isDefault: boolean;
}

export interface InboxItem {
  id: string;
  status: ConversationStatus;
  priority: Priority;
  clinicalRisk: ClinicalRisk;
  operationalPriority: QueuePriority;
  intent: string | null;
  handoffReason: string | null;
  handoffAt: string | null;
  waitingMinutes: number | null;
  overdue: boolean;
  lastActivityAt: string;
  tutorId: string | null;
  tutorName: string | null;
  departmentId: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
}

export type DeliveryStatus = "PENDING" | "SENT" | "DELIVERED" | "READ" | "FAILED";

export interface MessageItem {
  id: string;
  /** BUSINESS_APP: respondida pelo app do WhatsApp Business no celular (coexistência). */
  author: "CUSTOMER" | "ASSISTANT" | "STAFF" | "BUSINESS_APP";
  authorName: string;
  content: string;
  fallback: boolean;
  at: string;
  /** Só em conversas do WhatsApp: situação do envio da resposta. */
  delivery: { status: DeliveryStatus; attempts: number; errorCode: string | null } | null;
  /** Arquivo que o tutor mandou; `ref` abre por /app/api/channel/media/{ref}. */
  attachment: { kind: "IMAGE" | "DOCUMENT" | "AUDIO" | "VIDEO" | "OTHER"; mime: string | null; name: string | null; ref: string } | null;
}

export interface Note {
  id: string;
  authorName: string | null;
  text: string;
  at: string;
}

export interface ConversationDetail {
  id: string;
  status: ConversationStatus;
  priority: Priority;
  clinicalRisk: ClinicalRisk;
  operationalPriority: QueuePriority;
  operationalOverride: QueuePriority | null;
  intent: string | null;
  handoffReason: string | null;
  handoffAt: string | null;
  departmentId: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  tutor: {
    id: string;
    name: string | null;
    phone: string | null;
    pets: { id: string; name: string; species: string | null; breed: string | null }[];
  } | null;
  messages: MessageItem[];
  notes: Note[];
  requests: { id: string; type: RequestType; status: RequestStatus; summary: string | null; assigneeName: string | null }[];
  channel: "API" | "WHATSAPP";
  /** WhatsApp: depois disso a equipe não pode mais responder livremente (24 horas desde a última mensagem do tutor). */
  replyWindowEndsAt: string | null;
}

export interface RequestItem {
  id: string;
  conversationId: string;
  type: RequestType;
  status: RequestStatus;
  summary: string | null;
  tutorName: string | null;
  petName: string | null;
  reason: string | null;
  preferredPeriod: string | null;
  outcome: Outcome | null;
  departmentId: string;
  assigneeId: string | null;
  assigneeName: string | null;
  priority: Priority | null;
  clinicalRisk: ClinicalRisk;
  operationalPriority: QueuePriority;
  demandType: DemandType;
  createdAt: string;
  nextAction: string | null;
}

export interface RequestDetail {
  request: RequestItem;
  events: { type: string; actorName: string | null; from: string | null; to: string | null; reason: string | null; at: string }[];
  notes: Note[];
}

export interface CustomerItem {
  id: string;
  name: string | null;
  phone: string | null;
  pets: string[];
  lastSeenAt: string;
}

export interface CustomerDetail {
  id: string;
  name: string | null;
  phone: string | null;
  channel: string;
  pets: { id: string; name: string; species: string | null; breed: string | null; age: string | null }[];
  provenance: { subjectId: string; field: string; status: string; confirmedAt: string | null }[];
  conversations: { id: string; status: ConversationStatus; priority: Priority; startedAt: string }[];
  requests: { id: string; type: RequestType; status: RequestStatus; departmentId: string; createdAt: string }[];
}
