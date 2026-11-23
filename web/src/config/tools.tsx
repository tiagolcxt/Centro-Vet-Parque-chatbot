import { Copy, FileUp, RotateCcw, Send, Trash2 } from "lucide-react";
import { useRef, useState } from "react";
import { api } from "../api";
import { useLoad } from "../app-context";
import { riskFromLevel, riskLabel } from "../labels";
import type { Priority } from "../types";
import { useToast } from "../ui";
import { useDraft, type Change, type Version, type VersionSummary } from "./draft";
import { readableProblem } from "./editors";
import { SECTION_TITLE, SECTIONS, SERVICE_FIELDS } from "./schema";

// ---------------------------------------------------------------- simulador

interface SimLine {
  from: "TUTOR" | "ASSISTANT";
  text: string;
  meta?: string;
}

interface SimResult {
  reply: string;
  priority: Priority;
  handoff: boolean;
  staffRequest: boolean;
  fallback: boolean;
}

/** Testa o rascunho salvo como se fosse um tutor (S3-7). Nada é gravado. */
export function Simulator() {
  const { dirty, save } = useDraft();
  const [lines, setLines] = useState<SimLine[]>([]);
  const [text, setText] = useState("");
  const [at, setAt] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const send = async () => {
    const message = text.trim();
    if (!message) {
      return;
    }
    if (dirty && !(await save())) {
      setError("Corrija o rascunho antes de testar.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const previous = lines.slice(-10).map(({ from, text: t }) => ({ from, text: t }));
      const result = await api.post<SimResult>("/config/simulate", { text: message, previous, at: at || undefined });
      const meta = [
        "risco: " + riskLabel[riskFromLevel(result.priority)].toLowerCase(),
        result.handoff ? "encaminharia à equipe" : null,
        result.staffRequest ? "registraria uma solicitação" : null,
        result.fallback ? "resposta segura (a IA errou ou faltou informação)" : null,
      ]
        .filter(Boolean)
        .join(" · ");
      setLines((l) => [...l, { from: "TUTOR", text: message }, { from: "ASSISTANT", text: result.reply, meta }]);
      setText("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card config-section">
      <header>
        <h2>Testar o assistente</h2>
        <p className="muted small">
          Responde com o <strong>rascunho</strong>, do jeito que vai responder depois de publicar. Nada é gravado e nenhum
          tutor recebe nada.
        </p>
      </header>
      <div className="inline-row">
        <label className="muted small" htmlFor="sim-at">
          Simular data e hora
        </label>
        <input id="sim-at" type="datetime-local" value={at} onChange={(e) => setAt(e.target.value)} />
        {at && (
          <button className="small-button" onClick={() => setAt("")}>
            Agora
          </button>
        )}
      </div>
      <div className="messages sim-messages" aria-live="polite">
        {lines.length === 0 && <p className="muted center pad">Pergunte algo como um tutor: "Quanto custa a consulta?"</p>}
        {lines.map((line, i) => (
          <div key={i} className={"msg " + (line.from === "TUTOR" ? "msg-customer" : "msg-assistant")}>
            <p>{line.text}</p>
            {line.meta && <span className="msg-meta">{line.meta}</span>}
          </div>
        ))}
      </div>
      {error && <p className="error">{error}</p>}
      <div className="inline-row">
        <input
          aria-label="Mensagem de teste"
          placeholder="Escreva como um tutor…"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && !busy && void send()}
        />
        <button className="primary" disabled={busy} onClick={() => void send()}>
          <Send size={16} aria-hidden /> {busy ? "Pensando…" : "Enviar"}
        </button>
        {lines.length > 0 && (
          <button className="small-button" onClick={() => setLines([])}>
            Recomeçar
          </button>
        )}
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- diferenças

export function ChangeList({ changes }: { changes: Change[] }) {
  const { doc } = useDraft();
  if (changes.length === 0) {
    return <p className="muted">Nenhuma diferença.</p>;
  }
  return (
    <table className="changes">
      <thead>
        <tr>
          <th>Item</th>
          <th>Antes</th>
          <th>Depois</th>
        </tr>
      </thead>
      <tbody>
        {changes.map((c) => (
          <tr key={c.key}>
            <td>{changeLabel(c.key, doc)}</td>
            {isWholeEntry(c.key) ? (
              <>
                <td className="muted">{c.before === null ? "—" : "existia"}</td>
                <td>{c.after === null ? "(removido)" : "incluído"}</td>
              </>
            ) : (
              <>
                <td className="muted">{changeValue(c.before, "—")}</td>
                <td>{changeValue(c.after, "(removido)")}</td>
              </>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** Serviço ou pergunta inteira ("services.banho"): o servidor manda uma linha só quando entra ou sai. */
function isWholeEntry(key: string): boolean {
  const parts = key.split(".");
  return parts.length === 2 && (parts[0] === "services" || parts[0] === "faq");
}

const UNKNOWN = "DESCONHECIDO — confirmar com a equipe";

/** Valor da diferença como a pessoa lê: "não informado" no lugar do marcador técnico. */
function changeValue(value: string | null, fallback: string): string {
  if (value === null) {
    return fallback;
  }
  return value === UNKNOWN ? "não informado" : value;
}

/** "services.consulta.price-cents" → "Serviços e preços › Consulta clínica › Preço". */
export function changeLabel(key: string, doc?: Record<string, unknown> | null): string {
  const [sectionId, ...rest] = key.split(".");
  const parts = [SECTION_TITLE[sectionId] ?? sectionId];
  if (sectionId === "services" || sectionId === "faq") {
    const [id, field] = rest;
    const entry = (doc?.[sectionId] as Record<string, Record<string, unknown>> | undefined)?.[id];
    if (id) parts.push(String(entry?.name ?? entry?.question ?? id));
    if (field) parts.push(FIELD_LABEL[field] ?? field);
  } else if (sectionId === "review" && rest[0] === "valid-until") {
    parts.push("Válido até", SECTION_TITLE[rest[1]] ?? rest[1]);
  } else if (rest.length) {
    parts.push(FIELD_LABEL[`${sectionId}.${rest[0]}`] ?? FIELD_LABEL[rest[0]] ?? rest.join(" › "));
  }
  return parts.join(" › ");
}

const FIELD_LABEL: Record<string, string> = {
  ...Object.fromEntries(SECTIONS.flatMap((s) => s.fields.map((f) => [`${s.id}.${f.key}`, f.label]))),
  ...Object.fromEntries(SERVICE_FIELDS.map((f) => [f.key, f.label])),
  question: "Pergunta",
  answer: "Resposta",
  "reviewed-at": "Revisado em",
  "reviewed-by": "Revisado por",
};

// ---------------------------------------------------------------- histórico

export function History() {
  const { replace } = useDraft();
  const toast = useToast();
  const history = useLoad(() => api.get<VersionSummary[]>("/config/versions"), []);
  const [diff, setDiff] = useState<{ title: string; changes: Change[] } | null>(null);
  const list = history.data ?? [];

  const compare = async (index: number) => {
    const to = list[index];
    const from = list[index + 1];
    if (!from) {
      return;
    }
    const changes = await api.get<Change[]>(`/config/versions/${from.id}/diff/${to.id}`);
    setDiff({ title: `Versão ${from.number} → ${to.number}`, changes });
  };

  const restore = async (version: VersionSummary) => {
    replace(await api.post<Version>(`/config/versions/${version.id}/restore`));
    toast(`Versão ${version.number} copiada para o rascunho. Revise e publique.`);
    history.reload();
  };

  return (
    <section className="card config-section">
      <header>
        <h2>Histórico</h2>
        <p className="muted small">Toda publicação fica guardada. Restaurar copia a versão para o rascunho; nada muda até publicar.</p>
      </header>
      {history.error && <p className="error">{history.error}</p>}
      <ul className="list version-list">
        {list.map((v, i) => (
          <li key={v.id}>
            <span className="grow">
              <strong>Versão {v.number}</strong>{" "}
              <span className={"badge" + (v.status === "PUBLISHED" ? " badge-status-resolved" : "")}>
                {v.status === "PUBLISHED" ? "no ar" : v.status === "DRAFT" ? "rascunho" : "arquivada"}
              </span>
              <span className="muted small block">
                {new Date(v.publishedAt ?? v.updatedAt).toLocaleString("pt-BR")}
                {v.note ? " · " + v.note : ""}
              </span>
            </span>
            {i + 1 < list.length && (
              <button className="small-button" onClick={() => void compare(i)}>
                O que mudou
              </button>
            )}
            {v.status === "ARCHIVED" && (
              <button className="small-button" onClick={() => void restore(v)}>
                <RotateCcw size={14} aria-hidden /> Restaurar
              </button>
            )}
          </li>
        ))}
      </ul>
      {diff && (
        <div>
          <h3>{diff.title}</h3>
          <ChangeList changes={diff.changes} />
        </div>
      )}
    </section>
  );
}

// ---------------------------------------------------------------- importação

interface ServicesResult {
  draft: Version;
  added: number;
  updated: number;
  problems: string[];
}

interface FaqSuggestion {
  topic: string;
  question: string;
  count: number;
  examples: string[];
  suggestedAnswer: string | null;
}

interface ChatAnalysis {
  clinicSender: string;
  senders: string[];
  messages: number;
  questions: number;
  suggestions: FaqSuggestion[];
}

function readFiles(list: FileList | null): Promise<string[]> {
  return Promise.all(Array.from(list ?? []).map((file) => file.text()));
}

export function Imports() {
  const { replace, dirty } = useDraft();
  const toast = useToast();
  const [result, setResult] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [chats, setChats] = useState<string[] | null>(null);
  const [analysis, setAnalysis] = useState<ChatAnalysis | null>(null);
  const [approved, setApproved] = useState<Record<number, { question: string; answer: string } | undefined>>({});
  const yamlInput = useRef<HTMLInputElement>(null);

  const guard = () => {
    if (dirty) {
      setError("Salve o rascunho antes de importar: a importação parte do rascunho salvo.");
      return false;
    }
    setError(null);
    return true;
  };

  const importSheet = async (files: FileList | null) => {
    if (!guard()) {
      return;
    }
    const [content] = await readFiles(files);
    if (!content) {
      return;
    }
    try {
      const r = await api.post<ServicesResult>("/config/import/services", { content });
      replace(r.draft);
      setResult([`${r.added} serviços novos, ${r.updated} atualizados.`, ...r.problems]);
      toast("Planilha importada para o rascunho.");
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const importYaml = async (files: FileList | null) => {
    if (!guard()) {
      return;
    }
    const [content] = await readFiles(files);
    if (!content) {
      return;
    }
    try {
      replace(await api.post<Version>("/config/import/yaml", { content }));
      toast("Arquivo importado para o rascunho.");
    } catch (e) {
      const problems = (e as { problems?: string[] }).problems ?? [];
      setError([(e as Error).message, ...problems.slice(0, 5).map(readableProblem)].join(" "));
    }
  };

  const analyze = async (files: string[], clinicSender?: string) => {
    try {
      const a = await api.post<ChatAnalysis>("/config/import/whatsapp", { files, clinicSender });
      setAnalysis(a);
      setApproved({});
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const addApproved = async () => {
    if (!guard()) {
      return;
    }
    const items = Object.values(approved).filter((i): i is { question: string; answer: string } => !!i);
    if (items.length === 0) {
      return;
    }
    try {
      replace(await api.post<Version>("/config/import/faq", { items }));
      toast(`${items.length} perguntas adicionadas ao rascunho.`);
      setApproved({});
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <>
      {error && <p className="error">{error}</p>}
      <section className="card config-section">
        <header>
          <h2>Serviços e preços de uma planilha</h2>
          <p className="muted small">
            Primeira linha com os nomes das colunas: <strong>nome</strong> (obrigatória), preço, setor, espécies, duração,
            descrição, observação. No Excel, use "Salvar como" &gt; CSV. Serviço com o mesmo nome é atualizado.
          </p>
        </header>
        <label className="file-button">
          <FileUp size={16} aria-hidden /> Escolher planilha (.csv)
          <input type="file" accept=".csv,.txt,text/csv" onChange={(e) => void importSheet(e.target.files)} />
        </label>
        {result && (
          <ul className="problems">
            {result.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        )}
      </section>

      <section className="card config-section">
        <header>
          <h2>Perguntas a partir de conversas do WhatsApp</h2>
          <p className="muted small">
            No WhatsApp da clínica: abra uma conversa &gt; Mais &gt; Exportar conversa &gt; Sem mídia. Pode escolher vários
            arquivos. A leitura acontece só agora, sem IA, e <strong>nada das conversas é guardado</strong>: só entram as
            perguntas que você aprovar.
          </p>
        </header>
        <label className="file-button">
          <FileUp size={16} aria-hidden /> Escolher conversas (.txt)
          <input
            type="file"
            accept=".txt,text/plain"
            multiple
            onChange={async (e) => {
              const files = await readFiles(e.target.files);
              setChats(files);
              if (files.length) {
                void analyze(files);
              }
            }}
          />
        </label>
        {analysis && chats && (
          <div className="analysis">
            <p className="small">
              {analysis.messages} mensagens, {analysis.questions} perguntas de tutores. Respostas tiradas de{" "}
              <select
                aria-label="Quem é a clínica nas conversas"
                value={analysis.clinicSender}
                onChange={(e) => void analyze(chats, e.target.value)}
              >
                {analysis.senders.map((s) => (
                  <option key={s}>{s}</option>
                ))}
              </select>
              .
            </p>
            {analysis.suggestions.map((s, i) => {
              const choice = approved[i];
              return (
                <div key={s.topic} className={"suggestion" + (choice ? " suggestion-approved" : "")}>
                  <p className="eyebrow">
                    {s.topic} · perguntado {s.count} {s.count === 1 ? "vez" : "vezes"}
                  </p>
                  <ul className="muted small">
                    {s.examples.map((ex) => (
                      <li key={ex}>"{ex}"</li>
                    ))}
                  </ul>
                  {choice ? (
                    <>
                      <input
                        aria-label="Pergunta"
                        value={choice.question}
                        onChange={(e) => setApproved({ ...approved, [i]: { ...choice, question: e.target.value } })}
                      />
                      <textarea
                        aria-label="Resposta"
                        rows={2}
                        placeholder="Resposta que o assistente vai usar"
                        value={choice.answer}
                        onChange={(e) => setApproved({ ...approved, [i]: { ...choice, answer: e.target.value } })}
                      />
                      <button className="small-button" onClick={() => setApproved({ ...approved, [i]: undefined })}>
                        Desfazer
                      </button>
                    </>
                  ) : (
                    <button
                      className="small-button"
                      onClick={() =>
                        setApproved({ ...approved, [i]: { question: s.question, answer: s.suggestedAnswer ?? "" } })
                      }
                    >
                      Usar esta pergunta
                    </button>
                  )}
                </div>
              );
            })}
            <button className="primary" disabled={!Object.values(approved).some(Boolean)} onClick={() => void addApproved()}>
              Adicionar aprovadas ao rascunho
            </button>
          </div>
        )}
      </section>

      <section className="card config-section">
        <header>
          <h2>Arquivo de configuração (YAML)</h2>
          <p className="muted small">Para quem já tem o arquivo do modelo de informações. Substitui o rascunho inteiro.</p>
        </header>
        <label className="file-button">
          <FileUp size={16} aria-hidden /> Escolher arquivo (.yml)
          <input ref={yamlInput} type="file" accept=".yml,.yaml" onChange={(e) => void importYaml(e.target.files)} />
        </label>
      </section>
    </>
  );
}

// ---------------------------------------------------------------- respostas prontas

export interface QuickReply {
  id: string;
  title: string;
  body: string;
}

export function QuickReplies() {
  const toast = useToast();
  const list = useLoad(() => api.get<QuickReply[]>("/quick-replies"), []);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [editing, setEditing] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    try {
      if (editing) {
        await api.put(`/quick-replies/${editing}`, { title, body });
      } else {
        await api.post("/quick-replies", { title, body });
      }
      toast(editing ? "Resposta rápida alterada." : "Resposta rápida criada.");
      setTitle("");
      setBody("");
      setEditing(null);
      setError(null);
      list.reload();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <section className="card config-section">
      <header>
        <h2>Respostas rápidas</h2>
        <p className="muted small">
          Textos que a equipe insere com um clique ao responder na caixa de entrada. Valem na hora, sem publicar.
        </p>
      </header>
      <ul className="list version-list">
        {(list.data ?? []).map((r) => (
          <li key={r.id}>
            <span className="grow">
              <strong>{r.title}</strong>
              <span className="muted small block ellipsis">{r.body}</span>
            </span>
            <button
              className="small-button"
              onClick={() => {
                setEditing(r.id);
                setTitle(r.title);
                setBody(r.body);
              }}
            >
              Editar
            </button>
            <button
              className="icon-button"
              aria-label={"Apagar " + r.title}
              onClick={async () => {
                await api.delete(`/quick-replies/${r.id}`);
                list.reload();
              }}
            >
              <Trash2 size={16} aria-hidden />
            </button>
          </li>
        ))}
      </ul>
      {list.data?.length === 0 && <p className="muted">Nenhuma ainda. Comece por "Como chegar" e "Formas de pagamento".</p>}
      <div className="form-grid">
        <div className="field">
          <label htmlFor="qr-title">Título</label>
          <input id="qr-title" maxLength={60} value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="qr-body">Texto</label>
          <textarea id="qr-body" rows={3} maxLength={2000} value={body} onChange={(e) => setBody(e.target.value)} />
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      <div className="inline-row">
        <button className="primary" disabled={!title.trim() || !body.trim()} onClick={() => void submit()}>
          {editing ? "Salvar alteração" : "Criar resposta rápida"}
        </button>
        {editing && (
          <button
            className="small-button"
            onClick={() => {
              setEditing(null);
              setTitle("");
              setBody("");
            }}
          >
            Cancelar
          </button>
        )}
      </div>
    </section>
  );
}

export function CopyButton({ text, label = "Copiar" }: { text: string; label?: string }) {
  const toast = useToast();
  return (
    <button
      className="small-button"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          toast("Copiado.");
        } catch {
          toast("Não foi possível copiar.", "error");
        }
      }}
    >
      <Copy size={14} aria-hidden /> {label}
    </button>
  );
}
