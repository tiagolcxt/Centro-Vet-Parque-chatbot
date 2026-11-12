# ADR-0007 — Encaminhamento humano e tratamento de possível emergência

- **Status:** Aceito, com pendências (P-01, P-02, P-12, P-14, P-20, PT-10, PT-14)
- **Data:** 2026-11-11
- **Decisões:** DA-11, DA-25 (ampliação pelas Diretrizes de Atendimento)

## Contexto

O tutor deve poder pedir atendimento humano a qualquer momento, e o sistema deve encaminhar reclamações, questões clínicas, incertezas e situações fora dos limites do assistente. A revisão técnica (9.5) encontrou quatro problemas na proposta original:
- a decisão de encaminhar dependia só da classificação do modelo;
- ninguém é notificado no MVP;
- não havia comportamento para prazo vencido;
- em `WAITING_HUMAN` a IA não era chamada, então uma mensagem de emergência receberia uma resposta fixa cega.

## Decisão

### 1. Detecção determinística de pedido explícito
- Um detector de domínio (`ExplicitHumanRequestDetector`) roda **antes** da chamada ao modelo, em qualquer estado da conversa. Ele compara o texto normalizado (minúsculas, sem acentos, espaços colapsados) com uma lista configurável de **expressões** (frases, não palavras soltas como "pessoa").
- Quando detecta, o encaminhamento é decidido **independentemente** do modelo, da validação ou de falhas técnicas.
- A lista inicial de expressões é **pendente (PT-10)**.

### 2. Decisão de encaminhamento (`HandoffPolicy`, determinística)
Encaminha quando **qualquer** condição for verdadeira (o backend pode adicionar encaminhamentos, nunca removê-los):
1. pedido explícito detectado pelo detector;
2. intenção `HUMAN_REQUEST` ou `COMPLAINT`;
3. intenção `MEDICAL_CONCERN` ou `TREATMENT_FOLLOW_UP`: encaminhar ou só oferecer depende de **P-12**;
4. o modelo sinalizou necessidade de encaminhamento;
5. o modelo sinalizou **possível emergência** (ver 5);
6. falha do turno envolvendo encaminhamento (ADR-0002, item 5);
7. situações exclusivas de humano definidas pela clínica (**P-12**);
8. *(acrescentado pelas Diretrizes, DA-25)* prioridade clínica efetiva **P1 ou P2** (ADR-0018); **P3** quando exigir julgamento profissional (critério: P-20);
9. *(DA-25)* sinais `DISSATISFACTION` ou `NEEDS_STAFF_DECISION`;
10. *(DA-25)* `EMOTIONAL_DISTRESS`, `CONFUSION` e `UNPRODUCTIVE_CONVERSATION` (detector do backend, limiares PT-14). Nesses casos o encaminhamento é **oferecido** e passa a automático quando combinado com conteúdo clínico ou ao atingir o limite de conversa improdutiva. Oferecer ou encaminhar direto: **P-12**.

O motivo persistido passa a incluir os valores `CLINICAL_PRIORITY`, `DISSATISFACTION`, `EMOTIONAL_DISTRESS`, `UNPRODUCTIVE_CONVERSATION` e `STAFF_DECISION`, além da lista de sinais do turno. Depois de um pedido explícito, o assistente não pede justificativa, não tenta convencer o tutor a continuar com a IA e não retoma o fluxo automatizado (diretriz 3.3).

Lacuna de conhecimento (`knowledgeGap`) gera solicitação `INFORMATION` (ADR-0008), mas não encaminha a conversa por si só.

### 3. Persistência e significado de `WAITING_HUMAN`
- Ao encaminhar:
  - `Conversation.status = WAITING_HUMAN`;
  - `handoffReason` (enum: `EXPLICIT_REQUEST`, `COMPLAINT`, `CLINICAL`, `POSSIBLE_EMERGENCY`, `MODEL_SIGNAL`, `TURN_FAILURE`, `CLINIC_RULE`);
  - `handoffAt`;
  - `ServiceRequest(HUMAN_CONTACT)` criado na mesma transação;
  - histórico preservado.
- **`WAITING_HUMAN` significa "conversa sinalizada para revisão humana".** Não significa que um funcionário foi notificado: **não existe mecanismo de notificação no MVP**.
- Nenhum texto do sistema afirma que alguém foi avisado, notificado ou que "já vai responder". A validação trata essas afirmações como inconsistência (ADR-0002, ADR-0008). Os textos padrão dizem que a conversa "fica registrada para a equipe".

### 4. Mensagens recebidas em `WAITING_HUMAN`
- A mensagem é persistida normalmente.
- O modelo é chamado em **modo restrito** (prompt `waiting-human.st`). A saída estruturada contém apenas sinais (`possibleEmergency`, `explicitHumanRequest` e, pelas Diretrizes, `priority` e `signals[]`), não um texto livre de atendimento. A prioridade é **reavaliada** com a `PriorityPolicy` (ADR-0018) e pode subir enquanto a conversa aguarda.
- O texto ao tutor é **composto pelo backend**: confirmação de recebimento (sem prometer notificação) e, se houver indício de possível emergência, a **orientação da política aprovada** (P-01).
- Se o modo restrito falhar (erro, timeout, resultado inválido), o backend responde com a confirmação de recebimento **acrescida da orientação curta de emergência** (fail-safe).
- O assistente não retoma coleta de dados nem responde como se o atendimento humano tivesse ocorrido.

### 5. Possível emergência em qualquer estado
- O indício vem do modelo (`possibleEmergency`). **Não** existe tabela de sintomas nem classificação de gravidade no sistema (vetado pelo briefing).
- Quando há indício, o backend **insere a orientação da política aprovada no início** do texto final e encaminha (condição 5). Não se fazem perguntas adicionais antes da orientação.
- O conteúdo da orientação vem do YAML institucional (ADR-0003). **Enquanto P-01 não for aprovada**, o texto é um *placeholder* explícito (`[PENDENTE: política de emergência da clínica]`). Isso é aceitável apenas com dados fictícios e bloqueia o uso com dados reais (ADR-0014).

### 6. Encaminhamento sem atendimento no prazo
- O prazo esperado é configurável **por nível de prioridade** (ADR-0018). **Valores e comportamento são pendentes (P-02).**
- Mecanismo:
  - a conversa é considerada **atrasada** quando `now > handoffAt + prazo` sem ação da equipe;
  - a listagem para a equipe expõe esse indicador;
  - na próxima mensagem do tutor, o texto composto pelo backend inclui a orientação definida pela clínica para esse caso (ex.: contato direto). Não há job agendado nem notificação ativa.
- Fora do expediente e sem funcionário disponível: o comportamento depende de **P-02**.

### 7. Saída de `WAITING_HUMAN`
Por ação explícita da equipe: devolver a conversa ao assistente ou encerrá-la (ADR-0011). Expiração por inatividade: ADR-0009.

## Alternativas consideradas

- **Só o modelo decide:** pedido explícito pode não ser detectado; rejeitada.
- **Resposta fixa sem IA em `WAITING_HUMAN`:** mais barata, mas trata uma possível emergência como qualquer mensagem; rejeitada.
- **Chamada completa ao modelo em `WAITING_HUMAN`:** o assistente continuaria atendendo como se nada tivesse acontecido; rejeitada.
- **Orientação de emergência em toda resposta de `WAITING_HUMAN`:** mais segura e sem depender do modelo, mas repetitiva. Hoje é usada só como fail-safe; a clínica pode preferir sempre (P-01).
- **Notificação ativa (e-mail/WhatsApp para a equipe):** fora do escopo do MVP.

## Consequências

- O detector pode encaminhar em excesso (falso positivo). É aceito por ser falha em direção ao humano.
- Cada mensagem em `WAITING_HUMAN` gera uma chamada curta ao modelo (custo baixo, mas não zero).
- Sem notificação, o encaminhamento só é efetivo se alguém consultar as listagens. Quem acompanha e com que frequência é **P-14**.
