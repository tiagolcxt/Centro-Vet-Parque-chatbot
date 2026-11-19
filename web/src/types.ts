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

export interface MessageItem {
  id: string;
  author: "CUSTOMER" | "ASSISTANT" | "STAFF";
  authorName: string;
  content: string;
  fallback: boolean;
  at: string;
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
  requests: { id: string; type: RequestType; status: RequestStatus; summary: string | null }[];
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
