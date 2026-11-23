import { useState } from "react";
import { api } from "../api";
import { go, useApp, useLoad } from "../app-context";
import { RequestStatusBadge, RiskBadge, StatusBadge } from "../components/Badges";
import { requestTypeLabel, riskFromLevel, speciesLabel, timeLabel } from "../labels";
import type { CustomerDetail, CustomerItem } from "../types";

const FIELD_LABEL: Record<string, string> = {
  TUTOR_NAME: "nome do tutor",
  PET_NAME: "nome do pet",
  PET_SPECIES: "espécie",
  PET_BREED: "raça",
  PET_AGE: "idade",
};

/** Clientes e pets (RF-S40, RF-S42). Edição pela equipe fica para a S3 (precisa de migração da proveniência). */
export function Customers({ selectedId }: { selectedId?: string }) {
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  // Sem busca, a lista mostra quem falou por último (revisão pré-piloto, H3).
  const list = useLoad(
    () => api.get<CustomerItem[]>(search.length >= 2 ? "/customers?q=" + encodeURIComponent(search) : "/customers"),
    [search],
  );

  return (
    <div className={"split" + (selectedId ? " has-detail" : "")}>
      <aside className="split-list">
        <form
          className="pad list-search"
          onSubmit={(e) => {
            e.preventDefault();
            setSearch(q.trim());
          }}
        >
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Nome, telefone ou nome do pet" aria-label="Buscar cliente" />
          <button>Buscar</button>
        </form>
        {list.error && <p className="error">{list.error}</p>}
        {list.data?.length === 0 && (
          <p className="empty">{search.length >= 2 ? "Ninguém encontrado." : "Nenhum cliente ainda."}</p>
        )}
        {search.length < 2 && (list.data?.length ?? 0) > 0 && <p className="muted small pad-x">Falaram por último</p>}
        <ul className="list">
          {list.data?.map((c) => (
            <li key={c.id}>
              <a href={"#/clientes/" + c.id} className={"conv-item" + (c.id === selectedId ? " selected" : "")}>
                <span className="grow">
                  <strong>{c.name ?? "Sem nome"}</strong>
                  <span className="muted small block">{c.pets.join(", ") || "sem pets"}</span>
                </span>
                <span className="muted small">{timeLabel(c.lastSeenAt)}</span>
              </a>
            </li>
          ))}
        </ul>
      </aside>
      <section className="split-detail">{selectedId ? <Customer id={selectedId} /> : <p className="empty center-v">Escolha um cliente ou busque por nome, telefone ou pet.</p>}</section>
    </div>
  );
}

function Customer({ id }: { id: string }) {
  const { departmentName } = useApp();
  const { data, error } = useLoad(() => api.get<CustomerDetail>("/customers/" + id), [id]);
  if (error) return <p className="error pad">{error}</p>;
  if (!data) return <p className="muted pad">Carregando…</p>;
  const origin = (subjectId: string, field: string) =>
    data.provenance.find((p) => p.subjectId === subjectId && p.field === field)?.status === "CONFIRMED" ? "confirmado" : "mencionado";

  return (
    <div className="page detail">
      <button className="link back" onClick={() => go("clientes")}>
        ← Clientes
      </button>
      <h2>{data.name ?? "Sem nome"}</h2>
      <p className="muted small">
        {data.phone ?? "sem telefone"} · canal {data.channel} · nome {origin(data.id, "TUTOR_NAME")}
      </p>

      <section>
        <h3>Pets</h3>
        <ul className="plain">
          {data.pets.map((p) => (
            <li key={p.id}>
              🐾 <strong>{p.name}</strong> {[speciesLabel(p.species), p.breed, p.age].filter(Boolean).join(" · ")}
              <span className="muted small block">
                {Object.entries(FIELD_LABEL)
                  .filter(([f]) => f.startsWith("PET_") && data.provenance.some((x) => x.subjectId === p.id && x.field === f))
                  .map(([f, label]) => `${label}: ${origin(p.id, f)}`)
                  .join(" · ")}
              </span>
            </li>
          ))}
          {data.pets.length === 0 && <li className="muted small">Nenhum pet informado.</li>}
        </ul>
      </section>

      <section>
        <h3>Conversas</h3>
        <ul className="plain">
          {data.conversations.map((c) => (
            <li key={c.id}>
              <a href={"#/atendimento/" + c.id}>{timeLabel(c.startedAt)}</a> <StatusBadge status={c.status} />{" "}
              <RiskBadge risk={riskFromLevel(c.priority)} />
            </li>
          ))}
        </ul>
      </section>

      <section>
        <h3>Solicitações</h3>
        <p className="muted small">Só as dos setores que você vê.</p>
        <ul className="plain">
          {data.requests.map((r) => (
            <li key={r.id}>
              <a href={"#/solicitacoes/" + r.id}>{requestTypeLabel[r.type]}</a> <RequestStatusBadge status={r.status} />{" "}
              <span className="muted small">{departmentName(r.departmentId)}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
