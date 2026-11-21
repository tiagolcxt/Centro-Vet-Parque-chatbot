// Mapa das telas de configuração sobre o documento do modelo de informações (ADR-0026). O servidor valida tudo; aqui
// só há rótulos, ajuda e a conversão entre o que a pessoa digita e o formato do documento. null = "não informado".

export type Doc = Record<string, unknown>;
export type FieldType =
  | "text"
  | "textarea"
  | "phone"
  | "email"
  | "url"
  | "handle"
  | "zip"
  | "bool"
  | "hours"
  | "days"
  | "money"
  | "prices"
  | "date";

export interface Field {
  key: string;
  label: string;
  type: FieldType;
  help?: string;
}

export interface Section {
  id: string;
  title: string;
  intro: string;
  fields: Field[];
  /** Seção com "válido até" (review.valid-until). */
  validity?: boolean;
}

const WEEK: [string, string][] = [
  ["monday", "Segunda"],
  ["tuesday", "Terça"],
  ["wednesday", "Quarta"],
  ["thursday", "Quinta"],
  ["friday", "Sexta"],
  ["saturday", "Sábado"],
  ["sunday", "Domingo"],
];

const HOURS_HELP = 'Ex.: 08:00-12:00, 14:00-18:00. Escreva "fechado" se não abre. Vazio = não informado.';

export const SECTIONS: Section[] = [
  {
    id: "identification",
    title: "Dados da clínica",
    intro: "Como o tutor encontra e fala com vocês.",
    validity: true,
    fields: [
      { key: "name", label: "Nome", type: "text" },
      { key: "address", label: "Endereço", type: "text" },
      { key: "location-reference", label: "Ponto de referência", type: "text" },
      { key: "access", label: "Acesso e estacionamento", type: "text" },
      { key: "zip-code", label: "CEP", type: "zip" },
      { key: "phone", label: "Telefone", type: "phone" },
      { key: "whatsapp", label: "WhatsApp", type: "phone" },
      { key: "email", label: "E-mail", type: "email" },
      { key: "website", label: "Site", type: "url" },
      { key: "instagram", label: "Instagram", type: "handle", help: "No formato @perfil." },
    ],
  },
  {
    id: "hours",
    title: "Horários",
    intro: "Fora destes horários o assistente orienta a ligar, em vez de prometer retorno.",
    validity: true,
    fields: [
      ...WEEK.map(([key, label]) => ({ key, label, type: "hours" as const, help: HOURS_HELP })),
      {
        key: "holidays",
        label: "Feriados e dias especiais",
        type: "days",
        help: 'Uma data por linha: "25/12/2026: fechado" ou "24/12/2026: 08:00-12:00". Vazio = não informado.',
      },
      { key: "twenty-four-hours", label: "Atendimento 24 horas", type: "bool" },
    ],
  },
  {
    id: "emergency",
    title: "Emergência",
    intro: "O que dizer quando parece urgente.",
    validity: true,
    fields: [
      { key: "attended", label: "Vocês atendem emergência?", type: "textarea" },
      { key: "after-hours-referral", label: "Fora do horário, para onde orientar?", type: "textarea" },
    ],
  },
  {
    id: "petshop-hours",
    title: "Horários do pet shop",
    intro: "Se o banho e tosa ou a loja têm horário próprio.",
    validity: true,
    fields: WEEK.map(([key, label]) => ({ key, label, type: "hours" as const, help: HOURS_HELP })),
  },
  {
    id: "petshop",
    title: "Pet shop e banho e tosa",
    intro: "Regras da loja e da estética.",
    validity: true,
    fields: [
      { key: "grooming-requirements", label: "Exigências para banho e tosa", type: "textarea" },
      { key: "pickup-delivery", label: "Busca e entrega", type: "textarea" },
      { key: "boarding", label: "Hotel e creche", type: "textarea" },
      { key: "products", label: "Produtos", type: "textarea" },
      { key: "orders", label: "Pedidos", type: "textarea" },
    ],
  },
  {
    id: "payment",
    title: "Pagamento",
    intro: 'Os preços só são informados ao tutor com "divulgar preços" marcado.',
    validity: true,
    fields: [
      { key: "prices-disclosed", label: "Divulgar preços aos tutores", type: "bool" },
      { key: "methods", label: "Formas de pagamento", type: "textarea" },
      { key: "installments", label: "Parcelamento", type: "textarea" },
      { key: "health-plans", label: "Planos de saúde", type: "textarea" },
      { key: "estimates", label: "Orçamentos", type: "textarea" },
    ],
  },
  {
    id: "policies",
    title: "Políticas",
    intro: "Agendamento, cancelamento, faltas e transporte.",
    validity: true,
    fields: [
      { key: "scheduling", label: "Agendamento", type: "textarea" },
      { key: "cancellation", label: "Cancelamento", type: "textarea" },
      { key: "no-show", label: "Faltas", type: "textarea" },
      { key: "transport", label: "Transporte do animal", type: "textarea" },
    ],
  },
  {
    id: "approved-texts",
    title: "Textos aprovados",
    intro: "Usados exatamente como escritos, sem passar pela IA. Revise com o responsável técnico.",
    fields: [
      { key: "emergency-policy", label: "Orientação em possível emergência", type: "textarea" },
      { key: "direct-contact", label: "Como falar direto com a equipe", type: "textarea" },
    ],
  },
];

export const SERVICE_FIELDS: Field[] = [
  { key: "name", label: "Nome", type: "text" },
  { key: "offered", label: "Oferecido", type: "bool" },
  { key: "department", label: "Setor", type: "text" },
  { key: "price-cents", label: "Preço", type: "money", help: "Em reais, ex.: 150,00." },
  { key: "price-by-size", label: "Preço por porte", type: "prices", help: 'Uma linha por porte: "pequeno: 60,00".' },
  { key: "price-note", label: "Observação do preço", type: "text" },
  { key: "species", label: "Espécies", type: "text" },
  { key: "booking", label: "Como agendar", type: "text" },
  { key: "duration", label: "Duração", type: "text" },
  { key: "description", label: "Descrição", type: "textarea" },
];

export const SERVICE_SECTION = { id: "services", title: "Serviços e preços", validity: true };
export const FAQ_SECTION = { id: "faq", title: "Perguntas frequentes", validity: true };

export const VALIDATED = ["identification", "hours", "emergency", "services", "petshop-hours", "petshop", "payment",
  "policies", "faq"];

export const SECTION_TITLE: Record<string, string> = {
  ...Object.fromEntries(SECTIONS.map((s) => [s.id, s.title])),
  services: SERVICE_SECTION.title,
  faq: FAQ_SECTION.title,
  review: "Revisão",
  triage: "Alertas de triagem",
};

export const DEPARTMENT_KINDS: [string, string][] = [
  ["", "Recepção (padrão)"],
  ["CLINICAL", "Clínica"],
  ["PETSHOP", "Pet shop / banho e tosa"],
  ["RECEPTION", "Recepção"],
  ["OTHER", "Outro"],
];

// ---------- conversões: valor do documento ⇄ texto do campo ----------

export function centsToText(cents: unknown): string {
  if (typeof cents !== "number") {
    return "";
  }
  return (cents / 100).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** "1.500,00", "150", "150,5" → centavos; vazio → null; inválido → undefined. */
export function textToCents(text: string): number | null | undefined {
  const clean = text.replace(/R\$|\s/g, "");
  if (clean === "") {
    return null;
  }
  if (!/^[0-9.,]+$/.test(clean)) {
    return undefined;
  }
  const normalized = clean.includes(",") ? clean.replace(/\./g, "").replace(",", ".") : clean.replace(/\.(?=\d{3}(\D|$))/g, "");
  const value = Number(normalized);
  return Number.isFinite(value) ? Math.round(value * 100) : undefined;
}

export function hoursToText(value: unknown): string {
  if (!Array.isArray(value)) {
    return "";
  }
  return value.length === 0 ? "fechado" : value.join(", ");
}

export function textToHours(text: string): string[] | null {
  const clean = text.trim();
  if (clean === "") {
    return null;
  }
  if (/^fechad[oa]$/i.test(clean)) {
    return [];
  }
  return clean.split(/[,;]|\se\s/).map((i) => i.trim().replace(/[–—]/g, "-").replace(/\s/g, "")).filter(Boolean);
}

function isoToBr(iso: string): string {
  const [y, m, d] = iso.split("-");
  return `${d}/${m}/${y}`;
}

function brToIso(br: string): string {
  const match = br.trim().match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (!match) {
    return br.trim();
  }
  return `${match[3]}-${match[2].padStart(2, "0")}-${match[1].padStart(2, "0")}`;
}

export function daysToText(value: unknown): string {
  if (!value || typeof value !== "object") {
    return "";
  }
  return Object.entries(value as Record<string, string[]>)
    .map(([date, hours]) => `${isoToBr(date)}: ${hoursToText(hours)}`)
    .join("\n");
}

export function textToDays(text: string): Record<string, string[]> | null {
  const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
  if (lines.length === 0) {
    return null;
  }
  const result: Record<string, string[]> = {};
  for (const line of lines) {
    const at = line.indexOf(":");
    const date = at < 0 ? line : line.slice(0, at);
    result[brToIso(date)] = textToHours(at < 0 ? "fechado" : line.slice(at + 1)) ?? [];
  }
  return result;
}

export function pricesToText(value: unknown): string {
  if (!value || typeof value !== "object") {
    return "";
  }
  return Object.entries(value as Record<string, number>).map(([label, c]) => `${label}: ${centsToText(c)}`).join("\n");
}

export function textToPrices(text: string): Record<string, number> | null | undefined {
  const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
  if (lines.length === 0) {
    return null;
  }
  const result: Record<string, number> = {};
  for (const line of lines) {
    const at = line.lastIndexOf(":");
    const cents = at < 0 ? undefined : textToCents(line.slice(at + 1));
    if (at < 0 || cents == null) {
      return undefined;
    }
    result[line.slice(0, at).trim()] = cents;
  }
  return result;
}

export function dateToText(value: unknown): string {
  return typeof value === "string" ? value : "";
}

/** Valor do documento → texto do campo. */
export function display(type: FieldType, value: unknown): string {
  switch (type) {
    case "money":
      return centsToText(value);
    case "hours":
      return hoursToText(value);
    case "days":
      return daysToText(value);
    case "prices":
      return pricesToText(value);
    case "bool":
      return value === true ? "true" : value === false ? "false" : "";
    default:
      return typeof value === "string" ? value : "";
  }
}

/** Texto do campo → valor do documento. {@code undefined} = não deu para entender (a tela avisa). */
export function parse(type: FieldType, text: string): unknown {
  switch (type) {
    case "money":
      return textToCents(text);
    case "hours":
      return textToHours(text);
    case "days":
      return textToDays(text);
    case "prices":
      return textToPrices(text);
    case "bool":
      return text === "true" ? true : text === "false" ? false : null;
    default:
      return text.trim() === "" ? null : text.trim();
  }
}

// ---------- documento ----------

export function clone(doc: Doc): Doc {
  return JSON.parse(JSON.stringify(doc)) as Doc;
}

export function section(doc: Doc, id: string): Record<string, unknown> {
  const value = doc[id];
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

export function entries(doc: Doc, collection: "services" | "faq"): [string, Record<string, unknown>][] {
  const value = doc[collection];
  return value && typeof value === "object"
    ? Object.entries(value as Record<string, Record<string, unknown>>)
    : [];
}

/** Id de entrada a partir do texto (mesma regra do servidor). */
export function slug(text: string): string {
  const s = text
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
    .slice(0, 40)
    .replace(/-$/, "");
  return s || "item";
}

export function uniqueId(base: string, taken: string[]): string {
  let id = slug(base);
  let n = 2;
  while (taken.includes(id)) {
    id = `${slug(base)}-${n++}`;
  }
  return id;
}

/** Problemas do servidor que pertencem a um prefixo de chave ("hours.", "services.banho."). */
export function problemsFor(problems: string[], prefix: string): string[] {
  return problems.filter((p) => p.startsWith(prefix));
}

export function formatDate(iso: string | null | undefined): string {
  return iso ? isoToBr(iso) : "";
}
