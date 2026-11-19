# Roadmap do SaaS

> Versão 1 — 2026-11-18. Decisão de ordem: DA-45. Base: [PRD](../product/saas/prd.md), [arquitetura](../architecture/saas-modulos-e-arquitetura.md) e [backlog](saas-backlog.md).
> O [roadmap do assistente](implementation-roadmap.md) (Etapas 0 a 7) continua como histórico. As Etapas 0 a 5 dele estão concluídas e são a base do assistente. A Etapa 6 (validação) passa a fazer parte da S5, e a Etapa 7 (barreira para dados reais) da S6.

**Regras gerais:** as mesmas do roadmap do assistente.

- **Ordem.** Uma etapa começa com a anterior aceita e as pendências de entrada decididas.
- **Toda etapa termina com:**
  - testes executados;
  - documentação atualizada;
  - ADRs revistos;
  - aceite do responsável.
- **Dados.** Só dados fictícios até a S6 (DA-17).

**Ordem e paralelismo:**

```
S0 Descoberta ─────────────── (em paralelo a S1 e S2) ───────────┐
S1 Fundação multi-tenant ─► S2 Caixa de entrada e solicitações ─► S3 Configuração web ─► S4 WhatsApp ─► S5 Assistente no SaaS ─► S6 Piloto
        └─ pedido de verificação na Meta (corre em paralelo até a S4)
```

**Prazos.** Este roadmap não tem datas: elas dependem do ritmo de desenvolvimento e das respostas da clínica. Cada etapa, ao começar, recebe uma estimativa a partir do backlog.

---

## S0 — Descoberta e definição operacional

**Objetivo:** conhecer a rotina real da clínica piloto antes de fixar telas e regras.

**Tarefas:**

1. Questionário de casos recorrentes, já criado: frequência e forma de tratar cada caso.
2. Entrevista ou observação da recepção:
   - como as mensagens chegam;
   - quem responde;
   - como passam de uma pessoa para outra;
   - onde a informação se perde.
3. Setores, pessoas, perfis e quem pode ver o quê (P-17).
4. Serviços, horários, preços que podem ser divulgados e emergência (P-01, P-11).
5. Situações sensíveis e protocolos aprovados pelo veterinário responsável (P-12, P-20).
6. Expectativa de prazo de resposta (P-02) e quem acompanha cada fila (P-14).
7. **Linha de base:** o que puder ser medido hoje (mensagens por dia, tempo de resposta, pedidos perdidos), mesmo de forma manual. Sem isso, o piloto não tem com o que se comparar.

**Entregável e aceite:**

- mapa operacional aprovado pela clínica;
- protocolos clínicos aprovados pelo veterinário responsável;
- linha de base registrada.

---

## S1 — Fundação multi-tenant

**Pré-requisitos:** DA-42, DA-43, DA-49 (decididas); PT-16.

**Tarefas:**

- V5: tabela `clinic` e `clinic_id` em todas as tabelas, com testes de isolamento (ADR-0021).
- `platform`: login, sessão, perfis, catálogo de permissões e auditoria.
- `team`: funcionários e setores.
- Setores padrão.
- Teste de arquitetura das fronteiras.
- **Fora do código:** pedido de verificação do negócio e acesso como *Tech Provider* na Meta (DA-46).

**Aceite:**

- [x] O administrador cria perfis, convida funcionários e define setores pela API (`StaffAccessIT`).
- [x] Testes provam que a clínica A não vê nada da B, inclusive nas consultas nativas (`ClinicIsolationIT`, `TenancyRulesTest`).
- [x] Um perfil sem permissão recebe 403; um id de outra clínica recebe 404.
- [x] Os testes anteriores continuam verdes com `clinic_id`: 523 no total em 2026-11-18. A bateria com IA real não foi rodada nesta etapa.
- [ ] ⚑ Pedido de verificação na Meta enviado, com data registrada. Depende do responsável: dados da empresa.

**Situação (2026-11-18): implementada, aguardando aceite.**

O que foi feito:

- V5 (clínica e `clinic_id`) e V6 (setores, perfis, contas, funcionários, auditoria);
- módulos `platform` e `team`;
- login com sessão e CSRF;
- Spring Modulith (DA-52).

Detalhes nos ADRs 0020, 0021 e 0024.

Ficou para depois:

- envio do convite por e-mail (S1-7): na S1, o link volta para o administrador;
- itens que dependem das filas: escopo de setor e RN-S07 (S2).

---

## S2 — Caixa de entrada e solicitações por setor

**Pré-requisitos:** S1 aceita; DA-44.

**Tarefas:**

- Aplicação web: login, Início, Atendimento, Solicitações em abas por setor, Clientes e pets.
- Conversa: assumir (estado `ASSIGNED`), devolver, transferir, responder (autor `STAFF`), nota interna e encerrar.
- Solicitação: setor, responsável, eventos, notas e transferência.
- Atualização em tempo real (SSE).
- O canal de teste continua sendo a API e o `chat.ps1`, sem WhatsApp.

**Aceite:**

- [ ] Um funcionário trabalha uma conversa e uma solicitação do início ao fim pela web.
- [ ] O tutor de teste vê "encaminhado" e depois "assumido por Ana".
- [ ] Dois funcionários clicam "assumir" ao mesmo tempo: um ganha e o outro vê o aviso.
- [ ] O tosador não vê as notas clínicas; a recepção não vê os setores que não são dela.
- [ ] Uma possível emergência em conversa assumida sobe para o topo.
- [ ] Caminho de ponta a ponta (Playwright) verde.

---

## S3 — Configuração pela web

**Pré-requisitos:** S2 aceita.

**Tarefas:**

- `config_version`: rascunho, publicação, histórico e restauração (ADR-0026).
- Formulários do modelo de informações.
- Serviços com setor e regras de encaminhamento.
- Assistente de configuração inicial.
- Simulador.
- Importação do YAML.
- Avisos de validade.

**Aceite:**

- [ ] O administrador muda um preço, testa no simulador e publica. A próxima mensagem do assistente usa o novo valor e a auditoria registra.
- [ ] A clínica fictícia foi migrada por importação, e o carregamento por arquivo sai do caminho principal.
- [ ] Uma configuração inválida não é publicada, e a aplicação não cai.

---

## S4 — WhatsApp

**Pré-requisitos:** S3 aceita; acesso da Meta liberado. Sem ele, a etapa espera, ou se reavalia a opção B da DA-46.

**Tarefas:**

- `channel`: webhook com assinatura, `inbound_message`, *outbox* com nova tentativa, status e janela de 24 horas.
- `channel_account` com o token cifrado.
- Mídia sem texto.
- Serialização por contato.

**Aceite:**

- [ ] Mensagens de um número de **teste**, com dados fictícios, chegam e são respondidas pelo assistente e pela equipe.
- [ ] Uma duplicata não gera turno.
- [ ] Uma falha simulada da Meta é reenviada e aparece na conversa depois de N tentativas.
- [ ] Fora da janela de 24 horas, a resposta livre fica bloqueada na tela.

---

## S5 — Assistente no SaaS e validação

**Pré-requisitos:** S4 aceita; P-10 (persona) definida.

**Tarefas:**

- Setor escolhido pelas regras.
- Textos de "encaminhado" e "assumido".
- Níveis de ativação (ADR-0025).
- Bateria de IA com a configuração do piloto e os casos do questionário.
- Indicadores (RF-S71).
- Os itens da antiga Etapa 6: métricas por turno, cenários de segurança e relatório.

**Aceite:**

- [ ] Bateria de IA verde nos três níveis (≥ 9/10 por cenário; 0 comportamento proibido).
- [ ] Indicadores visíveis na tela Início.
- [ ] Relatório de validação, com as limitações conhecidas.

---

## S6 — Piloto

**Pré-requisitos:**

- S5 aceita;
- **barreira para dados reais** completa: G1–G12 do ADR-0014 mais os itens da DA-50;
- revisão jurídica registrada;
- todas as pendências P definitivas.

**Tarefas:**

1. Instalação.
2. Configuração da clínica real pelo assistente de configuração.
3. Treinamento da equipe.
4. Ativação no **nível 1** e subida de nível por critério.
5. Revisão semanal das conversas e das filas.
6. Correções.
7. Coleta de opinião da equipe e dos tutores.

**Entregável:**

- operação piloto em uso;
- relatório comparando com a linha de base da S0 ([plano de validação](saas-plano-de-validacao.md)).

---

## ADRs por etapa

| ADR | Criado | Revisto na |
|---|---|---|
| 0020 Forma do produto | 2026-11-18 | S1 (PT-16), S2 |
| 0021 Multi-tenant | 2026-11-18 | S1 |
| 0022 Aplicação web | 2026-11-18 | S2 |
| 0023 WhatsApp | 2026-11-18 | S4 |
| 0024 Identidade e permissões | 2026-11-18 | S1, S6 (perfis reais) |
| 0025 Níveis de ativação | 2026-11-18 | S5, S6 |
| 0026 Configuração versionada | 2026-11-18 | S3 |
| 0007 Encaminhamento (revisão DA-47) | 2026-11-18 | S2, S5 |
| 0014 Barreira (revisão DA-50) | 2026-11-18 | S6 |

## Pendências por etapa

| Etapa | Entrada | Podem seguir com valor provisório |
|---|---|---|
| S1 | PT-16 | P-17 (perfis-modelo do briefing) |
| S2 | — | P-02, P-14 |
| S3 | — | P-11 (base fictícia) |
| S4 | Acesso da Meta | PT-07 (antes de expor fora de `localhost`) |
| S5 | P-10 | P-01, P-12, P-20 (fictícios) |
| S6 | P-01 a P-20 definitivas, PT-07, revisão jurídica | — |
