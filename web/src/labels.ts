import type { ConversationStatus, Outcome, Priority, RequestItem, RequestStatus, RequestType, ClinicalRisk, DemandType, QueuePriority } from "./types";

// Textos da interface em português (RNF-S12). A prioridade sempre aparece com texto, nunca só com cor (RNF-S11).


export const riskLabel: Record<ClinicalRisk, string> = {
  POSSIBLE_EMERGENCY: "Possível emergência",
  URGENT: "Urgente",
  CLINICAL_FOLLOW_UP: "Acompanhamento clínico",
  NONE_IDENTIFIED: "Sem sinal clínico",
  NOT_ASSESSED: "Risco não avaliado",
};

/** Modo do assistente (ADR-0025), como a clínica lê. O número fica para a equipe da plataforma. */
export const activationModeLabel: Record<number, string> = {
  1: "Só organiza",
  2: "Tira dúvidas da clínica",
  3: "Tira dúvidas e registra solicitações",
};

export const activationModeHint: Record<number, string> = {
  1: "O assistente não conversa: registra a mensagem, separa por setor e informa o telefone da clínica. Quem responde é a equipe.",
  2: "Responde horário, serviços e preços cadastrados. Agendamentos e assuntos de saúde vão para a equipe.",
  3: "Também coleta os dados do tutor e do pet e registra solicitações de agendamento. Não confirma horário nem dá orientação clínica.",
};

export const queueLabel: Record<QueuePriority, string> = {
  IMMEDIATE: "Imediata",
  HIGH: "Alta",
  NORMAL: "Normal",
  LOW: "Baixa",
};

export const demandTypeLabel: Record<DemandType, string> = {
  APPOINTMENT: "Agendamento",
  CLINICAL_QUESTION: "Dúvida clínica",
  ADMINISTRATIVE_INFO: "Informação administrativa",
  COMMERCIAL: "Produto ou serviço do pet shop",
  GROOMING: "Banho e tosa",
  COMPLAINT: "Reclamação",
  HUMAN_CONTACT: "Atendimento humano",
  OTHER: "Outros",
};

/** Nível interno P1–P6 → risco clínico (para telas que só recebem o nível, como a ficha do cliente). */
export function riskFromLevel(p: Priority | null | undefined): ClinicalRisk {
  if (p === "P1_POSSIBLE_EMERGENCY") return "POSSIBLE_EMERGENCY";
  if (p === "P2_URGENT") return "URGENT";
  if (p === "P3_CLINICAL_FOLLOW_UP") return "CLINICAL_FOLLOW_UP";
  return "NONE_IDENTIFIED";
}

export function priorityShort(p: Priority | null | undefined): string {
  return p ? p.substring(0, 2) : "—";
}

export function priorityTone(p: Priority | null | undefined): "critical" | "warning" | "neutral" {
  if (p === "P1_POSSIBLE_EMERGENCY") return "critical";
  if (p === "P2_URGENT") return "warning";
  return "neutral";
}

// Revisão de linguagem (2026-11-23): os estados dizem quem precisa agir ou quem está agindo. Glossário em
// docs/plans/revisao-linguagem.md.
export const conversationStatusLabel: Record<ConversationStatus, string> = {
  OPEN: "Assistente atendendo",
  WAITING_HUMAN: "Aguardando atendimento",
  ASSIGNED: "Em atendimento",
  CLOSED: "Encerrada",
};

/** Dica do estado da conversa: o que ele significa na prática. */
export const conversationStatusHint: Record<ConversationStatus, string> = {
  OPEN: "O assistente está respondendo o tutor.",
  WAITING_HUMAN: "Ninguém da equipe assumiu ainda. O assistente responde só dúvidas da clínica.",
  ASSIGNED: "Um funcionário assumiu e responde o tutor. O assistente não responde.",
  CLOSED: "Conversa encerrada. Se o tutor escrever de novo, começa outra.",
};

export const requestStatusLabel: Record<RequestStatus, string> = {
  COLLECTING: "Aguardando dados do tutor",
  PENDING: "Pendente",
  IN_PROGRESS: "Em andamento",
  RESOLVED: "Concluída",
  CANCELLED: "Cancelada",
};

/**
 * Estado da solicitação como a equipe lê: junta o estado com o responsável, sem mudar a regra (atribuir e andamento
 * continuam separados). Ex.: "Sem responsável", "Com Ana", "Em andamento com Ana".
 */
export function requestStateLabel(status: RequestStatus, assigneeName?: string | null): string {
  const first = assigneeName ? assigneeName.split(" ")[0] : null;
  switch (status) {
    case "PENDING":
      return first ? `Com ${first}` : "Sem responsável";
    case "IN_PROGRESS":
      return first ? `Em andamento com ${first}` : "Em andamento";
    default:
      return requestStatusLabel[status];
  }
}

/**
 * Resumos antigos começavam com o código do motivo ("[EXPLICIT_REQUEST] texto"). Rede de segurança: mostra o rótulo
 * em português. Os novos já são gravados assim.
 */
export function readableSummary(summary: string | null | undefined): string {
  if (!summary) return "";
  return summary.replace(/^\[([A-Z_]+)\]\s*/, (match, code: string) =>
    handoffReasonLabel[code] ? handoffReasonLabel[code] + ": " : match);
}

export const requestTypeLabel: Record<RequestType, string> = {
  APPOINTMENT: "Agendamento",
  INFORMATION: "Informação",
  FOLLOW_UP: "Acompanhamento",
  HUMAN_CONTACT: "Atendimento humano",
};

export const outcomeLabel: Record<Outcome, string> = {
  SCHEDULED_BY_STAFF: "Agendado pela equipe",
  INFORMATION_PROVIDED: "Informação passada ao tutor",
  HANDLED_OUTSIDE: "Resolvido fora do sistema (telefone, balcão)",
  NO_AVAILABILITY: "Sem disponibilidade",
  CUSTOMER_GAVE_UP: "Tutor desistiu",
  NO_RESPONSE: "Tutor não respondeu",
  DUPLICATE: "Solicitação repetida",
  OTHER: "Outro (explicar)",
};

export const handoffReasonLabel: Record<string, string> = {
  POSSIBLE_EMERGENCY: "Possível emergência",
  CLINICAL_PRIORITY: "Prioridade clínica",
  EXPLICIT_REQUEST: "Pediu atendimento humano",
  COMPLAINT: "Reclamação",
  CLINICAL: "Assunto clínico",
  DISSATISFACTION: "Insatisfação",
  STAFF_DECISION: "Decisão da equipe",
  MODEL_SIGNAL: "Sinal do assistente",
  EMOTIONAL_DISTRESS: "Sofrimento emocional",
  UNPRODUCTIVE_CONVERSATION: "Conversa sem avanço",
  TURN_FAILURE: "Falha do assistente",
  CLINIC_RULE: "Regra da clínica",
  SENSITIVE_TOPIC: "Assunto sensível",
};

export function minutesLabel(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) return "";
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  return h < 24 ? `${h} h ${minutes % 60} min` : `${Math.floor(h / 24)} d ${h % 24} h`;
}

export function timeLabel(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  const today = new Date();
  const sameDay = d.toDateString() === today.toDateString();
  const time = d.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
  return sameDay ? time : `${d.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" })} ${time}`;
}

export function speciesLabel(species: string | null | undefined): string | null {
  switch (species) {
    case "DOG":
      return "cão";
    case "CAT":
      return "gato";
    case "OTHER":
      return "outra espécie";
    default:
      return species ?? null;
  }
}

/**
 * "Copiar resumo do pedido" (S3-18): texto pronto para colar no sistema de gestão da clínica (SimplesVet ou outro).
 * Só o que a equipe já vê na tela; uma linha por dado, sem linhas vazias.
 */
export function requestSummaryText(r: RequestItem, department: string): string {
  const created = new Date(r.createdAt).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
  return [
    `${requestTypeLabel[r.type]} — ${requestStateLabel(r.status, r.assigneeName)}`,
    r.tutorName && `Tutor: ${r.tutorName}`,
    r.petName && `Pet: ${r.petName}`,
    r.summary && `Resumo: ${readableSummary(r.summary)}`,
    r.reason && `Motivo: ${r.reason}`,
    r.preferredPeriod && `Período preferido: ${r.preferredPeriod}`,
    `Setor: ${department}`,
    r.assigneeName && `Responsável: ${r.assigneeName}`,
    r.outcome && `Resultado: ${outcomeLabel[r.outcome]}`,
    `Solicitação de ${created}`,
  ]
    .filter(Boolean)
    .join("\n");
}
