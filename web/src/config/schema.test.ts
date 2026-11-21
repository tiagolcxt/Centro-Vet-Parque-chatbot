import { describe, expect, it } from "vitest";
import { display, parse, slug, textToCents, textToDays, textToHours, textToPrices, uniqueId } from "./schema";

describe("conversões da configuração", () => {
  it("preço em reais vira centavos, e vazio é não informado", () => {
    expect(textToCents("150,00")).toBe(15000);
    expect(textToCents("R$ 1.500,50")).toBe(150050);
    expect(textToCents("150")).toBe(15000);
    expect(textToCents("1.500")).toBe(150000);
    expect(textToCents("")).toBeNull();
    expect(textToCents("cem")).toBeUndefined();
    expect(display("money", 15000)).toBe("150,00");
  });

  it("horário aceita vários intervalos, fechado e não informado", () => {
    expect(textToHours("08:00-12:00, 14:00 - 18:00")).toEqual(["08:00-12:00", "14:00-18:00"]);
    expect(textToHours("08:00–12:00 e 14:00-18:00")).toEqual(["08:00-12:00", "14:00-18:00"]);
    expect(textToHours("Fechado")).toEqual([]);
    expect(textToHours("  ")).toBeNull();
    expect(display("hours", [])).toBe("fechado");
  });

  it("feriados por linha, com data brasileira", () => {
    expect(textToDays("25/12/2026: fechado\n24/12/2026: 08:00-12:00")).toEqual({
      "2026-12-25": [],
      "2026-12-24": ["08:00-12:00"],
    });
    expect(display("days", { "2026-12-25": [] })).toBe("25/12/2026: fechado");
  });

  it("preço por porte", () => {
    expect(textToPrices("pequeno: 60,00\ngrande: 90")).toEqual({ pequeno: 6000, grande: 9000 });
    expect(textToPrices("pequeno sessenta")).toBeUndefined();
  });

  it("sim, não e não informado são diferentes", () => {
    expect(parse("bool", "true")).toBe(true);
    expect(parse("bool", "false")).toBe(false);
    expect(parse("bool", "")).toBeNull();
    expect(parse("text", "  ")).toBeNull();
  });

  it("id de entrada igual ao do servidor", () => {
    expect(slug("Tosa higiênica")).toBe("tosa-higienica");
    expect(uniqueId("Banho", ["banho"])).toBe("banho-2");
  });
});

describe("rótulos das diferenças", () => {
  it("mostra nomes da tela, não chaves", async () => {
    const { changeLabel } = await import("./tools");
    const doc = { services: { consulta: { name: "Consulta clínica" } } };
    expect(changeLabel("services.consulta.price-cents", doc)).toBe("Serviços e preços › Consulta clínica › Preço");
    expect(changeLabel("hours.saturday")).toBe("Horários › Sábado");
    expect(changeLabel("review.valid-until.hours")).toBe("Revisão › Válido até › Horários");
  });
});
