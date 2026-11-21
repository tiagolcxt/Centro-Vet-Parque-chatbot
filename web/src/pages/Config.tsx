import { AlertTriangle, CheckCircle2, ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { api } from "../api";
import { go, useApp, useLoad } from "../app-context";
import { useToast } from "../ui";
import { DraftProvider, useDraft, type Change, type Overview, type Version } from "../config/draft";
import { FaqEditor, ReviewEditor, SaveBar, SectionForm, ServicesEditor } from "../config/editors";
import { formatDate, SECTION_TITLE, SECTIONS } from "../config/schema";
import { ChangeList, History, Imports, QuickReplies, Simulator } from "../config/tools";

const TABS: [string, string][] = [
  ["inicio", "Visão geral"],
  ["primeiros-passos", "Primeiros passos"],
  ["clinica", "Clínica e horários"],
  ["servicos", "Serviços e preços"],
  ["perguntas", "Perguntas frequentes"],
  ["testar", "Testar"],
  ["importar", "Importar"],
  ["respostas", "Respostas prontas"],
  ["historico", "Histórico"],
];

/** Configuração da clínica pela web (Etapa S3, RF-S10 a RF-S19). */
export function Config({ tab }: { tab?: string }) {
  const { can } = useApp();
  const current = TABS.some(([id]) => id === tab) ? tab! : "inicio";
  if (!can("config:edit")) {
    return (
      <div className="page">
        <p className="muted">Seu perfil não edita a configuração.</p>
      </div>
    );
  }
  return (
    <DraftProvider>
      <div className="page config">
        <header className="page-header">
          <h1>Configuração</h1>
          <p className="muted">O assistente só usa o que está publicado. Edite à vontade: nada muda para o tutor até publicar.</p>
        </header>
        <nav className="tabs config-tabs" role="tablist" aria-label="Partes da configuração">
          {TABS.map(([id, label]) => (
            <button key={id} role="tab" aria-selected={current === id} onClick={() => go("configuracao/" + id)}>
              {label}
            </button>
          ))}
        </nav>
        <div className="config-body">
          {current === "inicio" && <ConfigHome />}
          {current === "primeiros-passos" && <Wizard />}
          {current === "clinica" && (
            <>
              {SECTIONS.map((s) => (
                <SectionForm key={s.id} def={s} />
              ))}
              <ReviewEditor />
            </>
          )}
          {current === "servicos" && <ServicesEditor />}
          {current === "perguntas" && <FaqEditor />}
          {current === "testar" && <Simulator />}
          {current === "importar" && <Imports />}
          {current === "respostas" && <QuickReplies />}
          {current === "historico" && <History />}
        </div>
        <SaveBar />
      </div>
    </DraftProvider>
  );
}

function ConfigHome() {
  const { can } = useApp();
  const draft = useDraft();
  const toast = useToast();
  const overview = useLoad(() => api.get<Overview>("/config/overview"), [draft.summary?.revision]);
  const changes = useLoad(() => api.get<Change[]>("/config/draft/changes"), [draft.summary?.revision]);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const o = overview.data;

  const publish = async () => {
    if (draft.dirty && !(await draft.save())) {
      return;
    }
    setBusy(true);
    try {
      await api.post<Version>("/config/publish", { note: note || undefined });
      toast("Publicado. A próxima mensagem do assistente já usa esta versão.");
      setNote("");
      await draft.reload();
      overview.reload();
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  const discard = async () => {
    await api.delete("/config/draft");
    await draft.reload();
    toast("Rascunho descartado: voltou a ser igual ao publicado.");
  };

  return (
    <>
      <section className="card config-section">
        <header>
          <h2>Situação</h2>
        </header>
        {o && (
          <p>
            {o.published ? (
              <>
                <CheckCircle2 size={16} aria-hidden /> No ar: versão {o.published.number}, publicada em{" "}
                {new Date(o.published.publishedAt ?? o.published.updatedAt).toLocaleString("pt-BR")}.
              </>
            ) : (
              <>
                <AlertTriangle size={16} aria-hidden /> Nada publicado ainda: o assistente não sabe nada da clínica e
                encaminha tudo à equipe. Comece pelos <a href="#/configuracao/primeiros-passos">primeiros passos</a>.
              </>
            )}
          </p>
        )}
        <ValidityWarnings overview={o} />
      </section>

      <section className="card config-section">
        <header>
          <h2>O que muda ao publicar</h2>
          <p className="muted small">Diferenças entre o rascunho salvo e o que está no ar.</p>
        </header>
        {draft.dirty && <p className="muted small">Há alterações não salvas: elas entram ao salvar.</p>}
        <ChangeList changes={changes.data ?? []} />
        {(changes.data?.length ?? 0) > 0 && (
          <div className="publish-row">
            <p className="small">
              Antes de publicar, <a href="#/configuracao/testar">teste no simulador</a>.
            </p>
            {can("config:publish") ? (
              <>
                <input
                  aria-label="Nota da publicação"
                  placeholder="Nota (opcional), ex.: preço da consulta de 2027"
                  maxLength={200}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                />
                <button className="primary" disabled={busy} onClick={() => void publish()}>
                  Publicar
                </button>
              </>
            ) : (
              <p className="muted small">Peça a quem tem permissão de publicar.</p>
            )}
            <button className="small-button" onClick={() => void discard()}>
              Descartar rascunho
            </button>
          </div>
        )}
      </section>
    </>
  );
}

export function ValidityWarnings({ overview }: { overview: Overview | undefined }) {
  if (!overview) {
    return null;
  }
  const expired = overview.validity.filter((v) => v.expired);
  const soon = overview.validity.filter((v) => v.expiringSoon);
  if (expired.length === 0 && soon.length === 0) {
    return null;
  }
  return (
    <ul className="warnings">
      {expired.map((v) => (
        <li key={v.section} className="warning-critical">
          <AlertTriangle size={14} aria-hidden /> <strong>{SECTION_TITLE[v.section]}</strong> venceu em{" "}
          {formatDate(v.validUntil)}: o assistente não está informando esta parte. Revise e publique.
        </li>
      ))}
      {soon.map((v) => (
        <li key={v.section}>
          <AlertTriangle size={14} aria-hidden /> <strong>{SECTION_TITLE[v.section]}</strong> vence em{" "}
          {formatDate(v.validUntil)}.
        </li>
      ))}
    </ul>
  );
}

// ---------------------------------------------------------------- primeiros passos

interface Template {
  type: string;
  name: string;
  description: string;
  services: { name: string }[];
  questions: string[];
}

const STEPS = ["Tipo de negócio", "Dados da clínica", "Horários", "Serviços e preços", "Perguntas frequentes", "Testar", "Publicar"];

/** Assistente de configuração inicial em 7 passos (S3-8), partindo de um modelo (S3-13). */
function Wizard() {
  const draft = useDraft();
  const toast = useToast();
  const [step, setStep] = useState(0);
  const templates = useLoad(() => api.get<Template[]>("/config/templates"), []);
  const [applying, setApplying] = useState<string | null>(null);

  const next = async () => {
    if (draft.dirty && !(await draft.save())) {
      return;
    }
    setStep((s) => Math.min(s + 1, STEPS.length - 1));
  };

  const apply = async (template: Template) => {
    if (draft.dirty && !(await draft.save())) {
      return;
    }
    setApplying(template.type);
    try {
      draft.replace(await api.post<Version>(`/config/templates/${template.type}/apply`));
      toast(`Modelo "${template.name}" aplicado. Agora preencha os dados.`);
      setStep(1);
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setApplying(null);
    }
  };

  const identification = SECTIONS.find((s) => s.id === "identification")!;
  const hours = SECTIONS.find((s) => s.id === "hours")!;
  const emergency = SECTIONS.find((s) => s.id === "emergency")!;
  const payment = SECTIONS.find((s) => s.id === "payment")!;

  return (
    <>
      <ol className="stepper" aria-label="Passos">
        {STEPS.map((label, i) => (
          <li key={label} aria-current={i === step ? "step" : undefined} className={i < step ? "done" : ""}>
            <button onClick={() => setStep(i)}>
              <span className="step-n">{i + 1}</span> {label}
            </button>
          </li>
        ))}
      </ol>

      {step === 0 && (
        <section className="card config-section">
          <header>
            <h2>Qual é o tipo do negócio?</h2>
            <p className="muted small">
              O modelo liga os setores certos e sugere serviços e perguntas. Ele não preenche preço, horário nem respostas:
              isso é com vocês. Dá para trocar depois.
            </p>
          </header>
          <div className="template-grid">
            {(templates.data ?? []).map((t) => (
              <button key={t.type} className="template-card" disabled={!!applying} onClick={() => void apply(t)}>
                <strong>{t.name}</strong>
                <span className="muted small">{t.description}</span>
                <span className="small">{t.services.map((s) => s.name).join(", ")}</span>
              </button>
            ))}
          </div>
        </section>
      )}
      {step === 1 && <SectionForm def={identification} />}
      {step === 2 && (
        <>
          <SectionForm def={hours} />
          <SectionForm def={emergency} />
        </>
      )}
      {step === 3 && (
        <>
          <p className="muted small">
            Tem uma planilha? <a href="#/configuracao/importar">Importe os serviços</a>.
          </p>
          <ServicesEditor />
          <SectionForm def={payment} />
        </>
      )}
      {step === 4 && (
        <>
          <p className="muted small">
            Tem conversas antigas no WhatsApp? <a href="#/configuracao/importar">Sugerir perguntas a partir delas</a>.
          </p>
          <FaqEditor />
        </>
      )}
      {step === 5 && <Simulator />}
      {step === 6 && <ConfigHome />}

      <div className="wizard-nav">
        <button disabled={step === 0} onClick={() => setStep((s) => Math.max(0, s - 1))}>
          <ChevronLeft size={16} aria-hidden /> Voltar
        </button>
        {step > 0 && step < STEPS.length - 1 && (
          <button className="primary" onClick={() => void next()}>
            Salvar e continuar <ChevronRight size={16} aria-hidden />
          </button>
        )}
      </div>
    </>
  );
}
