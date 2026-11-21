import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { useDraft } from "./draft";
import { ConfigField } from "./fields";
import {
  centsToText,
  entries,
  FAQ_SECTION,
  problemsFor,
  SECTION_TITLE,
  SERVICE_FIELDS,
  SERVICE_SECTION,
  section,
  uniqueId,
  VALIDATED,
  type Doc,
  type Section,
} from "./schema";

/** Barra de salvar: aparece só com alterações não salvas e lista os problemas que o servidor apontou. */
export function SaveBar() {
  const { dirty, saving, save, problems, error } = useDraft();
  if (!dirty && problems.length === 0) {
    return null;
  }
  return (
    <div className="save-bar" role="region" aria-label="Salvar rascunho">
      <div className="grow">
        {problems.length > 0 ? (
          <>
            <strong className="error">Não salvo: {problems.length === 1 ? "1 problema" : `${problems.length} problemas`}</strong>
            <ul className="problems">
              {problems.slice(0, 6).map((p) => (
                <li key={p}>{readableProblem(p)}</li>
              ))}
            </ul>
          </>
        ) : (
          <span>{error && !dirty ? error : "Alterações ainda não salvas no rascunho."}</span>
        )}
      </div>
      <button className="primary" disabled={saving} onClick={() => void save()}>
        {saving ? "Salvando…" : "Salvar rascunho"}
      </button>
    </div>
  );
}

/** "hours.monday: horário…" → "Horários › monday: horário…" */
export function readableProblem(problem: string): string {
  const [key, ...rest] = problem.split(":");
  const sectionId = key.split(".")[0];
  const title = SECTION_TITLE[sectionId];
  return title ? `${title} › ${key.slice(sectionId.length + 1) || sectionId}:${rest.join(":")}` : problem;
}

export function SectionForm({ def }: { def: Section }) {
  const { doc, edit, problems } = useDraft();
  if (!doc) {
    return null;
  }
  const values = section(doc, def.id);
  return (
    <section className="card config-section">
      <header>
        <h2>{def.title}</h2>
        <p className="muted small">{def.intro}</p>
      </header>
      <div className="form-grid">
        {def.fields.map((field) => (
          <ConfigField
            key={field.key}
            field={field}
            value={values[field.key]}
            problems={problemsFor(problems, `${def.id}.${field.key}`)}
            onChange={(value) => edit((d) => setIn(d, def.id, field.key, value))}
          />
        ))}
      </div>
      {def.validity && <ValidityLine sectionId={def.id} />}
    </section>
  );
}

function setIn(doc: Doc, sectionId: string, key: string, value: unknown) {
  const current = doc[sectionId];
  const target = current && typeof current === "object" ? (current as Record<string, unknown>) : {};
  target[key] = value;
  doc[sectionId] = target;
}

/** "Válido até" da seção: passada a data, o assistente trata a seção como desconhecida. */
export function ValidityLine({ sectionId }: { sectionId: string }) {
  const { doc, edit, problems } = useDraft();
  if (!doc) {
    return null;
  }
  const review = section(doc, "review");
  const validity = (review["valid-until"] ?? {}) as Record<string, string | null>;
  return (
    <div className="validity-line">
      <ConfigField
        field={{
          key: sectionId,
          label: "Válido até",
          type: "date",
          help: "Data no formato aaaa-mm-dd. Depois dela o assistente deixa de informar esta parte até alguém revisar.",
        }}
        value={validity[sectionId] ?? null}
        problems={problemsFor(problems, `review.valid-until.${sectionId}`)}
        onChange={(value) =>
          edit((d) => {
            const r = section(d, "review");
            r["valid-until"] = { ...((r["valid-until"] as object) ?? {}), [sectionId]: value };
            d.review = r;
          })
        }
      />
    </div>
  );
}

export function ServicesEditor() {
  const { doc, edit, problems } = useDraft();
  const [newName, setNewName] = useState("");
  if (!doc) {
    return null;
  }
  const list = entries(doc, "services");
  const add = () => {
    const name = newName.trim();
    if (!name) {
      return;
    }
    edit((d) => {
      const services = (d.services && typeof d.services === "object" ? d.services : {}) as Record<string, unknown>;
      const entry: Record<string, unknown> = Object.fromEntries(SERVICE_FIELDS.map((f) => [f.key, null]));
      entry.name = name;
      entry.offered = true;
      services[uniqueId(name, Object.keys(services))] = entry;
      d.services = services;
    });
    setNewName("");
  };
  return (
    <section className="card config-section">
      <header>
        <h2>{SERVICE_SECTION.title}</h2>
        <p className="muted small">
          Preço vazio = o assistente diz que não sabe e encaminha. O setor decide para qual fila vão os pedidos do serviço.
        </p>
      </header>
      {doc.services === null && <p className="muted">Nenhum serviço cadastrado ainda.</p>}
      {list.map(([id, entry]) => (
        <details key={id} className="entry" open={list.length <= 3 || problemsFor(problems, `services.${id}.`).length > 0}>
          <summary>
            <strong>{(entry.name as string) || id}</strong>
            <span className="muted small">
              {typeof entry["price-cents"] === "number" ? "R$ " + centsToText(entry["price-cents"]) : "preço não informado"}
            </span>
            {problemsFor(problems, `services.${id}.`).length > 0 && <span className="badge badge-critical">revisar</span>}
            {entry.offered === false && <span className="badge">não oferecido</span>}
          </summary>
          <div className="form-grid">
            {SERVICE_FIELDS.map((field) => (
              <ConfigField
                key={field.key}
                field={field}
                value={entry[field.key]}
                problems={problemsFor(problems, `services.${id}.${field.key}`)}
                onChange={(value) =>
                  edit((d) => {
                    (d.services as Record<string, Record<string, unknown>>)[id][field.key] =
                      field.key === "department" && value === "" ? null : value;
                  })
                }
              />
            ))}
          </div>
          <button
            className="small-button danger"
            onClick={() =>
              edit((d) => {
                delete (d.services as Record<string, unknown>)[id];
              })
            }
          >
            <Trash2 size={14} aria-hidden /> Remover serviço
          </button>
        </details>
      ))}
      <div className="inline-row">
        <input
          aria-label="Nome do novo serviço"
          placeholder="Novo serviço, ex.: Vacina antirrábica"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && add()}
        />
        <button onClick={add}>
          <Plus size={16} aria-hidden /> Adicionar
        </button>
      </div>
      <ValidityLine sectionId="services" />
    </section>
  );
}

export function FaqEditor() {
  const { doc, edit, problems } = useDraft();
  const [question, setQuestion] = useState("");
  if (!doc) {
    return null;
  }
  const list = entries(doc, "faq");
  const add = () => {
    const q = question.trim();
    if (!q) {
      return;
    }
    edit((d) => {
      const faq = (d.faq && typeof d.faq === "object" ? d.faq : {}) as Record<string, unknown>;
      faq[uniqueId(q, Object.keys(faq))] = { question: q, answer: null };
      d.faq = faq;
    });
    setQuestion("");
  };
  return (
    <section className="card config-section">
      <header>
        <h2>{FAQ_SECTION.title}</h2>
        <p className="muted small">Sem resposta, o assistente não responde a pergunta e encaminha à equipe.</p>
      </header>
      {list.map(([id, entry]) => (
        <div key={id} className="faq-entry">
          <ConfigField
            field={{ key: "question", label: "Pergunta", type: "text" }}
            value={entry.question}
            problems={problemsFor(problems, `faq.${id}.question`)}
            onChange={(value) => edit((d) => ((d.faq as Record<string, Record<string, unknown>>)[id].question = value))}
          />
          <ConfigField
            field={{ key: "answer", label: "Resposta", type: "textarea" }}
            value={entry.answer}
            problems={problemsFor(problems, `faq.${id}.answer`)}
            onChange={(value) => edit((d) => ((d.faq as Record<string, Record<string, unknown>>)[id].answer = value))}
          />
          <button
            className="icon-button"
            aria-label={"Remover pergunta " + String(entry.question ?? id)}
            onClick={() =>
              edit((d) => {
                delete (d.faq as Record<string, unknown>)[id];
              })
            }
          >
            <Trash2 size={16} aria-hidden />
          </button>
        </div>
      ))}
      <div className="inline-row">
        <input
          aria-label="Nova pergunta"
          placeholder="Nova pergunta, ex.: Vocês atendem aos domingos?"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && add()}
        />
        <button onClick={add}>
          <Plus size={16} aria-hidden /> Adicionar
        </button>
      </div>
      <ValidityLine sectionId="faq" />
    </section>
  );
}

/** Revisão: quem revisou, quando, e o "válido até" de todas as seções num lugar só. */
export function ReviewEditor() {
  const { doc, edit, problems } = useDraft();
  if (!doc) {
    return null;
  }
  const review = section(doc, "review");
  return (
    <section className="card config-section">
      <header>
        <h2>Revisão e validade</h2>
        <p className="muted small">
          Cada parte pode ter um prazo. Vencido, o assistente deixa de informar aquela parte até alguém revisar: melhor não
          informar do que informar errado.
        </p>
      </header>
      <div className="form-grid">
        <ConfigField
          field={{ key: "reviewed-at", label: "Revisado em", type: "date", help: "aaaa-mm-dd" }}
          value={review["reviewed-at"]}
          problems={problemsFor(problems, "review.reviewed-at")}
          onChange={(value) => edit((d) => (section(d, "review")["reviewed-at"] = value))}
        />
        <ConfigField
          field={{ key: "reviewed-by", label: "Revisado por", type: "text" }}
          value={review["reviewed-by"]}
          problems={problemsFor(problems, "review.reviewed-by")}
          onChange={(value) => edit((d) => (section(d, "review")["reviewed-by"] = value))}
        />
      </div>
      <div className="form-grid">
        {VALIDATED.map((id) => (
          <div key={id}>
            <p className="eyebrow">{SECTION_TITLE[id]}</p>
            <ValidityLine sectionId={id} />
          </div>
        ))}
      </div>
    </section>
  );
}
