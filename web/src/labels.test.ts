import { describe, expect, it } from "vitest";
import { minutesLabel, priorityLabel, priorityShort, priorityTone } from "./labels";

describe("rótulos", () => {
  it("prioridade sempre tem texto, não só cor (RNF-S11)", () => {
    for (const label of Object.values(priorityLabel)) {
      expect(label).toMatch(/^P[1-6] · \S/);
    }
    expect(priorityShort("P1_POSSIBLE_EMERGENCY")).toBe("P1");
    expect(priorityTone("P1_POSSIBLE_EMERGENCY")).toBe("critical");
    expect(priorityTone("P2_URGENT")).toBe("warning");
    expect(priorityTone("P4_SERVICE_REQUEST")).toBe("neutral");
  });

  it("tempo de espera legível", () => {
    expect(minutesLabel(5)).toBe("5 min");
    expect(minutesLabel(75)).toBe("1 h 15 min");
    expect(minutesLabel(60 * 26)).toBe("1 d 2 h");
    expect(minutesLabel(null)).toBe("");
  });
});

describe("resumo do pedido", () => {
  it("leva só os dados preenchidos, um por linha", async () => {
    const { requestSummaryText } = await import("./labels");
    const text = requestSummaryText(
      {
        id: "1",
        conversationId: "c",
        type: "APPOINTMENT",
        status: "PENDING",
        summary: null,
        tutorName: "Tutora Fictícia",
        petName: "Rex",
        reason: "vacina",
        preferredPeriod: "manhã",
        outcome: null,
        departmentId: "d",
        assigneeId: null,
        assigneeName: null,
        priority: null,
    clinicalRisk: "NONE_IDENTIFIED",
    operationalPriority: "NORMAL",
    demandType: "APPOINTMENT",
        createdAt: "2026-11-21T12:00:00Z",
        nextAction: null,
      },
      "Clínica",
    );
    expect(text.split("\n")).toEqual([
      "Agendamento — Pendente",
      "Tutor: Tutora Fictícia",
      "Pet: Rex",
      "Motivo: vacina",
      "Período preferido: manhã",
      "Setor: Clínica",
      expect.stringMatching(/^Pedido de /),
    ]);
  });
});
