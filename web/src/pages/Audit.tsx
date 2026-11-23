import { useState } from "react";
import { api } from "../api";
import { useLoad } from "../app-context";

/** Auditoria (revisão pré-piloto, E2; RF-S07): quem fez o quê e quando. Só leitura. */

interface AuditItem {
  id: string;
  at: string;
  actorId: string | null;
  actorName: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  summary: string | null;
}

interface AuditPage {
  items: AuditItem[];
  page: number;
  size: number;
  total: number;
}

/** O que cada ação significa, em linguagem da clínica. Ação sem rótulo aparece com o código. */
const ACTION_LABEL: Record<string, string> = {
  "auth.login": "entrou no sistema",
  "auth.login_failed": "teve uma tentativa de entrada com senha errada na conta",
  "auth.invite_accepted": "aceitou o convite e criou a senha",
  "auth.password_changed": "trocou a senha",
  "conversation.assumed": "assumiu o atendimento da conversa",
  "conversation.closed": "encerrou a conversa",
  "conversation.priority_changed": "mudou o risco clínico da conversa",
  "conversation.returned": "devolveu a conversa ao assistente",
  "conversation.transferred": "transferiu a conversa",
  "request.assigned": "assumiu ou atribuiu a solicitação",
  "request.transferred": "transferiu a solicitação",
  "request.status_changed": "mudou a situação da solicitação",
  "request.reclassified": "corrigiu o tipo da solicitação",
  "conversation.queue_priority_changed": "mudou a prioridade de atendimento da conversa",
  "customer.viewed": "abriu a ficha do tutor",
  "config.published": "publicou a configuração",
  "config.restored": "restaurou uma versão da configuração",
  "config.draft-discarded": "descartou o rascunho da configuração",
  "config.template-applied": "aplicou um modelo de negócio",
  "config.imported": "importou dados para a configuração",
  "config.services-imported": "importou serviços de planilha",
  "config.faq-added": "adicionou perguntas frequentes sugeridas",
  "channel.updated": "alterou o canal do WhatsApp",
  "quick-reply.created": "criou uma resposta rápida",
  "quick-reply.changed": "alterou uma resposta rápida",
  "quick-reply.deleted": "apagou uma resposta rápida",
  "user.invited": "convidou uma pessoa",
  "user.invite_reissued": "gerou um novo link de acesso",
  "user.disabled": "desativou uma conta",
  "user.enabled": "reativou uma conta",
  "user.role_changed": "trocou o perfil de uma pessoa",
  "employee.updated": "alterou os dados de uma pessoa",
  "role.created": "criou um perfil",
  "role.updated": "alterou um perfil",
  "department.created": "criou um setor",
  "department.renamed": "renomeou um setor",
  "department.activated": "ativou um setor",
  "department.deactivated": "desativou um setor",
};

const FILTERS: [string, string][] = [
  ["-auth", "Tudo, menos entradas no sistema"],
  ["", "Todas as ações"],
  ["conversation.", "Conversas"],
  ["request.", "Solicitações"],
  ["customer.", "Tutores"],
  ["config.", "Configuração"],
  ["channel.", "WhatsApp"],
  ["quick-reply.", "Respostas rápidas"],
  ["user.", "Contas e convites"],
  ["employee.", "Dados da equipe"],
  ["role.", "Perfis"],
  ["department.", "Setores"],
  ["auth.", "Entradas no sistema"],
];

const TARGET_LINK: Record<string, string> = {
  conversation: "#/atendimento/",
  request: "#/solicitacoes/",
  tutor: "#/clientes/",
};

const PAGE_SIZE = 50;

function isoDay(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

export function Audit() {
  const today = new Date();
  const [from, setFrom] = useState(isoDay(new Date(today.getTime() - 29 * 86400000)));
  const [to, setTo] = useState(isoDay(today));
  const [action, setAction] = useState("-auth");
  const [actor, setActor] = useState<{ id: string; name: string } | null>(null);
  const [page, setPage] = useState(0);

  const query = new URLSearchParams({ from, to, page: String(page), size: String(PAGE_SIZE) });
  if (action === "-auth") query.set("withoutLogins", "true");
  else if (action) query.set("action", action);
  if (actor) query.set("actorId", actor.id);
  const data = useLoad(() => api.get<AuditPage>("/audit?" + query.toString()), [query.toString()]);
  const pages = data.data ? Math.max(1, Math.ceil(data.data.total / PAGE_SIZE)) : 1;

  return (
    <div className="page">
      <header className="page-header">
        <h1>Registro de atividades</h1>
        <p className="muted">Quem fez o quê e quando. Os registros não podem ser alterados nem apagados.</p>
      </header>
      <section className="card config-section">
        <div className="form-grid">
          <div className="field">
            <label htmlFor="audit-from">De</label>
            <input id="audit-from" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
          </div>
          <div className="field">
            <label htmlFor="audit-to">Até</label>
            <input id="audit-to" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
          </div>
          <div className="field">
            <label htmlFor="audit-action">Tipo de ação</label>
            <select id="audit-action" value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }}>
              {FILTERS.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
        </div>
        {actor && (
          <p className="inline-row small">
            Só ações de <strong>{actor.name}</strong>
            <button className="small-button" onClick={() => { setActor(null); setPage(0); }}>
              Ver de todos
            </button>
          </p>
        )}
        {data.error && <p className="error">{data.error}</p>}
        <ul className="list version-list audit-list">
          {(data.data?.items ?? []).map((item) => {
            const link = item.targetType && item.targetId ? TARGET_LINK[item.targetType] : undefined;
            return (
              <li key={item.id}>
                <span className="muted small audit-time">{new Date(item.at).toLocaleString("pt-BR")}</span>
                <span className="grow">
                  {item.actorName && item.actorId ? (
                    <button className="link" onClick={() => { setActor({ id: item.actorId!, name: item.actorName! }); setPage(0); }}>
                      <strong>{item.actorName}</strong>
                    </button>
                  ) : (
                    <strong>{item.actorName ?? "Assistente ou sistema"}</strong>
                  )}{" "}
                  {ACTION_LABEL[item.action] ?? item.action}
                  {link && (
                    <>
                      {" · "}
                      <a href={link + item.targetId}>abrir</a>
                    </>
                  )}
                  {item.summary && <span className="muted small block">{item.summary}</span>}
                </span>
              </li>
            );
          })}
        </ul>
        {data.data && data.data.items.length === 0 && <p className="empty">Nada registrado nesse período.</p>}
        {data.data && data.data.total > PAGE_SIZE && (
          <div className="inline-row">
            <button className="small-button" disabled={page === 0} onClick={() => setPage(page - 1)}>
              Anteriores
            </button>
            <span className="muted small">
              Página {page + 1} de {pages} · {data.data.total} registros
            </span>
            <button className="small-button" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>
              Seguintes
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
