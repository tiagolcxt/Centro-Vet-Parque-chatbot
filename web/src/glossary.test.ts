import { describe, expect, it } from "vitest";
import { requestStateLabel } from "./labels";

/**
 * Glossário da interface (docs/plans/revisao-linguagem.md, seção 6): os termos aposentados não voltam, e nenhum código
 * interno aparece como texto. Lê os textos entre aspas e os textos de JSX de web/src, sem comentários.
 */

/** Fontes da aplicação como texto (Vite), sem os testes. */
const SOURCES = Object.entries(
  import.meta.glob(["./**/*.ts", "./**/*.tsx", "!./**/*.test.ts"], { query: "?raw", import: "default", eager: true }) as Record<string, string>,
);

function visibleTexts(code: string): string[] {
  const noComments = code.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:])\/\/.*$/gm, "$1");
  const quoted = [...noComments.matchAll(/"([^"\n]*)"|`([^`]*)`/g)].map((m) => m[1] ?? m[2] ?? "");
  const jsx = [...noComments.matchAll(/>([^<>{}]*[A-Za-zÀ-ú][^<>{}]*)</g)].map((m) => m[1]);
  return [...quoted, ...jsx]
    .map((t) => t.trim())
    // rotas, chaves de API, classes CSS, importações e permissões não são texto para pessoas
    .filter((t) => t && !/^[#/.]/.test(t) && !/^[a-z-]+(:[a-z-]+)?\/?$/.test(t) && !t.startsWith("@") && !/===|&&|\?\s*\(/.test(t));
}

const RETIRED: [RegExp, string][] = [
  [/sem dono/i, "use \"sem responsável\""],
  [/\bComigo\b/, "use \"Minhas pendências\", \"Meus atendimentos\" ou \"Minhas\""],
  [/\bpedidos?\b(?! de produtos?)/i, "\"pedido\" é só compra; use \"solicitação\""],
  [/\bclientes?\b/i, "use \"tutor\""],
  [/Aguardando a equipe/, "use \"Aguardando atendimento\""],
  [/Respostas? prontas?/i, "use \"Respostas rápidas\""],
  [/Assumida por/, "use \"Em atendimento por\""],
  [/não recebe atribuiç/i, "a disponibilidade ainda não muda as atribuições"],
];

describe("glossário da interface", () => {
  it("não usa termos aposentados em textos visíveis", () => {
    expect(SOURCES.length).toBeGreaterThan(10);
    const hits: string[] = [];
    for (const [file, code] of SOURCES) {
      for (const text of visibleTexts(code)) {
        for (const [pattern, advice] of RETIRED) {
          if (pattern.test(text)) hits.push(`${file}: "${text}" (${advice})`);
        }
      }
    }
    expect(hits).toEqual([]);
  });

  it("mostra o estado da solicitação junto com o responsável", () => {
    expect(requestStateLabel("PENDING", null)).toBe("Sem responsável");
    expect(requestStateLabel("PENDING", "Ana Fictícia")).toBe("Com Ana");
    expect(requestStateLabel("IN_PROGRESS", "Ana Fictícia")).toBe("Em andamento com Ana");
    expect(requestStateLabel("COLLECTING", null)).toBe("Aguardando dados do tutor");
    expect(requestStateLabel("RESOLVED", "Ana")).toBe("Concluída");
    expect(requestStateLabel("CANCELLED", null)).toBe("Cancelada");
  });
});
