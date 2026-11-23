import { describe, expect, it } from "vitest";
import { cardContext } from "./Inbox";
import type { InboxItem } from "../types";

// Cartão da lista de conversas: uma linha de contexto, sem repetir a aba nem o selo (dados fictícios).
const base: InboxItem = {
  id: "c1",
  status: "WAITING_HUMAN",
  priority: "P2_URGENT",
  clinicalRisk: "URGENT",
  operationalPriority: "HIGH",
  intent: null,
  handoffReason: "CLINICAL_PRIORITY",
  handoffAt: null,
  waitingMinutes: 30,
  overdue: false,
  lastActivityAt: "2026-11-23T12:00:00Z",
  tutorId: null,
  tutorName: "Fernanda Fictícia",
  departmentId: null,
  assigneeId: null,
  assigneeName: null,
};

describe("cartão da conversa", () => {
  it("não repete o motivo que o selo de risco já mostra, nem a aba", () => {
    expect(cardContext(base, "WAITING")).toBe("");
  });

  it("mostra o motivo quando o selo não diz a mesma coisa", () => {
    expect(cardContext({ ...base, clinicalRisk: "NOT_ASSESSED", handoffReason: "EXPLICIT_REQUEST" }, "WAITING")).toBe(
      "Pediu atendimento humano",
    );
  });

  it("fora da aba Aguardando, diz com quem está a conversa", () => {
    const assigned = { ...base, status: "ASSIGNED" as const, handoffReason: "COMPLAINT", assigneeName: "Carla Fictícia" };
    expect(cardContext(assigned, "ALL")).toBe("Reclamação · com Carla");
    expect(cardContext(assigned, "MINE")).toBe("Reclamação");
  });

  it("conversa só com o assistente", () => {
    expect(cardContext({ ...base, status: "OPEN", clinicalRisk: "NONE_IDENTIFIED", handoffReason: null }, "ALL")).toBe(
      "Assistente atendendo",
    );
  });
});
