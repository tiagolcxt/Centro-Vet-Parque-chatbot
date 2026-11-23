# Roadmap do SaaS

> Versão 2 — 2026-11-21: implantação mais fácil e adaptável (DA-53) — modelos por tipo de negócio, importação, respostas prontas, indicadores e integração leve na S3; coexistência na S4; áudio na S5; lembretes como primeiro item depois do MVP. Versão 1 — 2026-11-18. Decisão de ordem: DA-45. Base: [PRD](../product/saas/prd.md), [arquitetura](../architecture/saas-modulos-e-arquitetura.md) e [backlog](saas-backlog.md).
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

**Situação: implementada em 2026-11-18 e aceita pelo responsável em 2026-11-19.**

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

- [x] Um funcionário trabalha uma conversa e uma solicitação do início ao fim pela web (`InboxIT`; conferido no navegador em 2026-11-19).
- [x] O tutor de teste vê "encaminhado" e depois "assumido por Ana" ("Ana (Recepção) vai continuar o seu atendimento por aqui").
- [x] Dois funcionários tentam assumir: o segundo recebe 409 com o nome de quem já está com a conversa.
- [x] A recepção não vê os setores que não são dela; o tosador só vê o pet shop. Notas clínicas separadas por permissão ficam para quando houver nota clínica (hoje as notas internas são da conversa e da solicitação, e seguem o escopo de setor).
- [x] Uma possível emergência em conversa assumida sobe a prioridade e vai para o topo, sem o assistente responder.
- [ ] Caminho de ponta a ponta automatizado (Playwright): **não feito**. O caminho foi conferido no navegador embutido (login, assumir, responder, aviso em tempo real, solicitação, celular). O Playwright exige baixar navegadores e fica para a S3, com a sua aprovação.

**Situação: implementada e aceita pelo responsável em 2026-11-19.**

O que foi feito:

- V7: estado `ASSIGNED`, autor `STAFF`, setor e responsável da solicitação, histórico e notas internas;
- caixa de entrada e solicitações por setor em `/app/api`, com escopo de setor e RN-S07;
- avisos em tempo real (SSE);
- busca e ficha de clientes;
- aplicação web em React (`web/`).

As rotas antigas da equipe em `/api` (fila, encerrar, devolver, solicitações) saíram. O `chat.ps1` ficou só com o lado do tutor e ganhou `/atualizar`.

Ficou para depois:

- edição de cliente e pet pela equipe (RF-S41): a proveniência hoje exige a conversa de origem e pede migração (S3);
- indicadores da tela Início (RF-S71, S5);
- Playwright (acima).

---

## S3 — Configuração pela web

**Pré-requisitos:** S2 aceita.

**Objetivo revisto (DA-53):** uma clínica nova entra no ar em uma sessão de cerca de 15 minutos, partindo de um modelo pronto e do que ela já tem, sem técnico.

**Tarefas:**

- `config_version`: rascunho, publicação, histórico e restauração (ADR-0026).
- Formulários do modelo de informações.
- Serviços com setor e regras de encaminhamento.
- **Modelos por tipo de negócio.** Na primeira tela, a clínica escolhe:
  - clínica pequena (só atendimento clínico);
  - clínica com pet shop e banho e tosa;
  - só pet shop ou banho e tosa;
  - hospital 24 horas.

  Cada modelo já traz setores ativos, perfis, regras de encaminhamento, perguntas frequentes sugeridas e tom do assistente. A clínica ajusta em vez de montar do zero.
- **Assistente de configuração inicial** em etapas, a partir do modelo escolhido.
- **Importação do que a clínica já tem:**
  - serviços e preços a partir de uma planilha (CSV ou Excel);
  - conversas exportadas do WhatsApp, analisadas para sugerir as perguntas mais frequentes e as respostas, que a clínica aprova uma a uma. A análise usa só o arquivo enviado e não guarda as conversas;
  - YAML atual, para migrar a clínica fictícia.
- **Respostas prontas** para a equipe usar ao responder ("como chegar", "preço do banho"), editáveis pela clínica.
- **Indicadores básicos** na tela Início, para a clínica ver o valor desde a primeira semana:
  - mensagens recebidas e respondidas;
  - tempo até a primeira resposta;
  - pedidos abertos e resolvidos.
- **Integração leve com o sistema de gestão:** botão "copiar resumo do pedido" (texto pronto para colar no SimplesVet ou em outro sistema) e exportação dos pedidos em planilha.
- Simulador.
- Avisos de validade.

**Aceite:**

- [ ] Uma clínica fictícia nova é configurada do zero, por alguém que não é técnico, em até 15 minutos, partindo de um modelo. O tempo é medido no teste de usabilidade (S3-12): **não feito**, depende de alguém da clínica.
- [x] Os quatro modelos geram configurações válidas, que o simulador aceita (`ConfigIT`).
- [x] A importação de planilha cria os serviços. A análise de conversas exportadas sugere perguntas frequentes sem gravar as conversas (`ConfigIT`, `ConfigImportsTest`; conferido no navegador).
- [x] O administrador muda um preço, testa no simulador e publica. A próxima mensagem do assistente usa o novo valor e a auditoria registra (`ConfigIT`; conferido no navegador em 2026-11-21, com o modelo real respondendo "R$ 170,00" no simulador).
- [x] A clínica fictícia foi migrada por importação, e o carregamento por arquivo sai do caminho principal.
- [x] Uma configuração inválida não é publicada, e a aplicação não cai.
- [x] A equipe usa respostas prontas na caixa de entrada. A tela Início mostra os indicadores básicos (`TeamToolsIT`; conferido no navegador).

**Situação: implementada e aceita pelo responsável em 2026-11-21**, com as três decisões de implementação abaixo. O teste de usabilidade (S3-12) segue pendente.

O que foi feito:

- V8: `config_version` (rascunho, publicada e arquivada, com no máximo um rascunho e uma publicada por clínica, garantido por índice único) e `quick_reply`;
- o assistente lê a versão publicada da clínica atual a cada mensagem (`ClinicKnowledge` dinâmico, com cache por clínica limpo ao publicar). Clínica sem versão publicada: base vazia, e o assistente encaminha;
- carga inicial: o YAML de `ASSISTANT_KNOWLEDGE_FILE` é importado e publicado só se a clínica fictícia ainda não tem versão publicada. Arquivo inválido vai para o log e não impede a aplicação de subir;
- rascunho, publicação, histórico, diferenças legíveis e restauração, com auditoria;
- simulador: o mesmo turno do assistente, sobre o rascunho, com data e hora simuladas, numa transação desfeita no fim (nada é gravado e nenhum aviso em tempo real sai);
- setor de cada serviço (`services.<id>.department`, opcional): usado pelo encaminhamento da S5, fora do prompt;
- quatro modelos por tipo de negócio: ligam os setores e sugerem serviços e perguntas, **sem** preço, horário nem respostas;
- importação de planilha CSV de serviços (o mesmo nome atualiza o serviço), de conversas exportadas do WhatsApp (leitura em memória, sem IA, sem gravar nada) e do YAML;
- respostas prontas: cadastro na configuração e botão na caixa de resposta (o texto entra para revisão, nunca é enviado direto);
- indicadores dos últimos 7 dias na tela Início e avisos de validade e de rascunho não publicado;
- "Copiar resumo" no pedido e planilha dos pedidos (CSV que o Excel abre, com proteção contra fórmulas; exige `customer:export`);
- telas: Configuração com visão geral, primeiros passos em 7 passos, formulários por seção, serviços, perguntas, testar, importar, respostas prontas e histórico.

Decisões de implementação, para confirmar na aceitação:

- **Excel por CSV:** a planilha entra como CSV ("Salvar como" no Excel). Ler `.xlsx` direto pediria uma biblioteca a mais.
- **Conversas do WhatsApp sem IA:** a análise agrupa por palavras de cada assunto. Assim nenhuma conversa de tutor sai da aplicação. O agrupamento é mais simples que o de um modelo.
- **"Tom do assistente" nos modelos:** ficou de fora, porque o modelo de informações não tem esse campo. Entra se a S5 criar o campo.

Ficou para depois:

- tela de equipe e setores (S3-10) e tela de auditoria (S3-11): a API existe desde a S1, faltam as telas;
- teste de usabilidade com alguém da clínica (S3-12);
- Playwright (aprovação para baixar navegadores) e edição de cliente e pet (RF-S41).

---

## S4 — WhatsApp

**Pré-requisitos:** S3 aceita; acesso da Meta liberado. Sem ele, a etapa espera, ou se reavalia a opção B da DA-46.

**Tarefas:**

- `channel`: webhook com assinatura, `inbound_message`, *outbox* com nova tentativa, status e janela de 24 horas.
- `channel_account` com o token cifrado.
- **Coexistência (DA-53).** A clínica mantém o número e o app WhatsApp Business do celular: o mesmo número funciona no app e na API.
  - O onboarding de coexistência da Meta é o caminho padrão.
  - As mensagens respondidas pelo celular aparecem na conversa, marcadas como "respondida pelo celular".
  - Confirmar, no início da etapa, o que a Meta permite e as limitações da coexistência na documentação oficial.
- Mídia sem texto: imagens e documentos ficam visíveis para a equipe. O áudio é transcrito na S5.
- Serialização por contato.

**Aceite:**

- [ ] Um número de **teste** é ligado por coexistência, sem perder o uso no app do celular. **Não feito:** a coexistência exige Embedded Signup como *Tech Provider* (ADR-0023, implementação). O eco das respostas dadas pelo celular já está pronto e testado.
- [ ] Mensagens de um número de **teste**, com dados fictícios, chegam e são respondidas pelo assistente e pela equipe. **Feito com a Cloud API falsa e corpos da documentação da Meta**; o ensaio com número real espera uma conta com número brasileiro (o número de teste da Meta dá erro 130497 para o Brasil).
- [x] Uma duplicata não gera turno.
- [x] Uma falha simulada da Meta é reenviada e aparece na conversa depois de N tentativas (5, com espera crescente), com a opção "Tentar enviar de novo".
- [x] Fora da janela de 24 horas, a resposta livre fica bloqueada na tela e recusada pelo servidor.

**Situação (2026-11-22): implementada, aguardando aceite.** O que falta depende da Meta: número brasileiro numa conta do WhatsApp Business, webhook público com HTTPS e, para a coexistência, o Embedded Signup. Antes do piloto, comparar Cloud API direta e provedor intermediário (opção B da DA-46). Detalhes no ADR-0023, seção "Implementação".

---

## S5 — Assistente no SaaS e validação

**Pré-requisitos:** S4 aceita; P-10 (persona) definida.

**Tarefas:**

- Setor escolhido pelas regras.
- Textos de "encaminhado" e "assumido".
- **Áudio (DA-53)**, antes fora do MVP:
  - áudio do tutor transcrito e tratado como texto pelo assistente;
  - a equipe vê a transcrição e pode ouvir o original;
  - o provedor de transcrição entra na lista de suboperadores (DA-50);
  - custo por minuto medido junto com o custo da IA.
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

## Depois do MVP: ordem combinada (DA-53)

1. **Lembretes de vacina e de retorno** pelo WhatsApp, com modelos aprovados pela Meta (versão B). É o recurso que o mercado mais pede e o argumento do plano mais caro.
2. **Integração com o sistema de gestão** que a clínica piloto usa, quando a integração leve da S3 não bastar.
3. O resto da evolução futura do [Blueprint](../product/saas/product-blueprint.md#8-evolução-futura).

**Fora de escopo**, para não virar concorrente do sistema de gestão nem uma plataforma genérica:

- agenda, prontuário e financeiro próprios;
- editor de robôs ou fluxos para a clínica montar;
- outros tipos de negócio além de clínica veterinária com ou sem pet shop, até ter cerca de 20 clínicas.

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
