# Regras de negócio implementadas — encaminhamento, prioridade, limites e coleta

> Reflete o código ao fim da **Etapa 5 — Coleta de dados e solicitações de agendamento** (2026-11-17). Decisões: ADR-0007, ADR-0010, ADR-0011, ADR-0017, ADR-0018, ADR-0019, DA-15, DA-25, DA-30, DA-31, DA-33, DA-34, DA-35, DA-36, DA-37. Requisitos: [requisitos-diretrizes.md](../specs/requisitos-diretrizes.md). Cenários: [cenarios-diretrizes.md](../specs/cenarios-diretrizes.md).
> **Somente dados fictícios.** Textos de emergência, prazo e encaminhamento são provisórios (P-01, P-02, P-10, P-19); a lista de expressões de alerta é ilustrativa (P-20).

## 1. Quem decide o quê

| Decisão | Quem decide | Onde |
|---|---|---|
| Pedido explícito de humano | **Backend**, antes do modelo, por lista de expressões (DA-30) | `ExplicitHumanRequestDetector`, `rules/human-request-expressions.yml` |
| Piso de prioridade por expressão de alerta | **Backend**, pela lista da equipe veterinária (ADR-0019) | `AlertExpressionDetector`, `triage.*` na base |
| Conversa improdutiva | **Backend**, por contagem (DA-31) | `UnproductiveConversationDetector` |
| Prioridade efetiva | **Backend**: o mais urgente entre modelo, pisos e nível atual (H3) | `PriorityPolicy` (domínio) |
| Encaminhar ou oferecer atendimento humano | **Backend**, por condições fixas (ADR-0007, item 2) | `HandoffPolicy` (domínio) |
| Texto final ao tutor | **Backend**, a partir do texto do modelo e dos textos aprovados | `ReplyComposer` |
| Intenção, sinais, possível emergência, prioridade sugerida | Modelo, como **sugestão** | `AssistantTurn` v3 |

O modelo nunca remove um encaminhamento nem rebaixa uma prioridade: o backend só acrescenta.

## 2. Encaminhamento (ADR-0007)

O turno encaminha quando qualquer condição vale. Fica o motivo da primeira desta lista:

| # | Condição | Motivo persistido |
|---|---|---|
| 1 | Possível emergência (modelo) ou nível do turno P1 | `POSSIBLE_EMERGENCY` |
| 2 | Nível do turno P2 ou P3 | `CLINICAL_PRIORITY` |
| 3 | Pedido explícito (detector) ou intenção `HUMAN_REQUEST` (principal ou secundária) | `EXPLICIT_REQUEST` |
| 4 | Intenção `COMPLAINT` | `COMPLAINT` |
| 5 | Intenção `MEDICAL_CONCERN` ou `TREATMENT_FOLLOW_UP` | `CLINICAL` |
| 6 | Sinal `DISSATISFACTION` | `DISSATISFACTION` |
| 7 | Sinal `NEEDS_STAFF_DECISION` | `STAFF_DECISION` |
| 8 | O modelo pediu (`handoffRequested`) | `MODEL_SIGNAL` |
| 9 | Conversa improdutiva no limite (3ª ocorrência) | `UNPRODUCTIVE_CONVERSATION` |
| 10 | Falha envolvendo encaminhamento (texto anunciou transferência nas duas tentativas) | `TURN_FAILURE` |

**Nível do turno** é o do modelo mais os pisos deste turno, **sem** o nível que a conversa já tinha. Assim, uma conversa P2 que a equipe devolveu ao assistente não volta automaticamente para a fila; ela volta se a nova mensagem for urgente.

**Ao encaminhar**, na mesma transação (ADR-0017):
- `WAITING_HUMAN`, motivo e momento na conversa;
- uma `ServiceRequest(HUMAN_CONTACT)` `PENDING` com o motivo e a mensagem no resumo (uma por conversa);
- **ninguém é notificado**. A equipe vê a conversa na fila.

**Oferta de atendimento humano** (sem encaminhar): sinais `EMOTIONAL_DISTRESS`, `CONFUSION`, `BEREAVEMENT`, `FINANCIAL_VULNERABILITY` ou conversa improdutiva na 2ª ocorrência. No máximo uma vez por conversa.

**Provisório (P-12, P-20):** sintoma e acompanhamento (P3) encaminham direto; sinais emocionais só oferecem.

## 3. Prioridade clínica (ADR-0018)

- Seis níveis, P1 a P6. Conversa nova começa em P6.
- **Pisos do backend (H5):** possível emergência → P1; `MEDICAL_CONCERN` (inclusive secundária), sinais `WORSENING` ou `POST_PROCEDURE_CHANGE`, ou `clinicalUncertainty` → P2; `TREATMENT_FOLLOW_UP` → P3; expressão de alerta → P1 ou P2.
- **Nunca rebaixa automaticamente (H4).** "Já passou, pode esquecer" não muda nada. Só a equipe muda o nível, por `PATCH /api/conversations/{id}/priority`, que registra nível anterior, novo, momento e autor (até a Etapa 7, "equipe (API Key)").
- **Falha da IA não rebaixa (H8).** Os pisos determinísticos continuam valendo na falha.
- **Sem dados comerciais (H1):** as entradas da `PriorityPolicy` são só intenções, sinais, possível emergência, incerteza e alerta (teste dedicado).
- Cada mensagem do assistente grava a prioridade efetiva, a origem (`MODEL`, `BACKEND_FLOOR`, `CURRENT_LEVEL`) e os sinais.

## 4. Composição do texto (ADR-0002, H6–H8)

| Situação | Texto ao tutor |
|---|---|
| P1 ou possível emergência | Orientação de emergência **primeiro e sozinha**, depois "conversa registrada". O texto do modelo é descartado |
| P2 | Orientação curta de quando procurar atendimento **primeiro**, depois o texto do modelo e o registro |
| Pedido explícito de humano | Só "conversa registrada": sem pedir motivo, sem "antes, posso ajudar com…" |
| Outro encaminhamento | Texto do modelo + "conversa registrada" |
| Oferta | Texto do modelo + oferta |
| Falha, sem encaminhar | Resposta segura; com a orientação curta antes se a conversa está em P1/P2 |
| Falha, com encaminhamento | Resposta de encaminhamento (falha em direção ao humano) |
| `WAITING_HUMAN` | Confirmação de recebimento; orientação de emergência antes se houver indício; orientação curta depois se o modo restrito falhar (fail-safe); orientação de prazo vencido se passou do prazo |

Os textos padrão ficam em `assistant.handoff.replies` (provisórios). A orientação de emergência e o contato direto vêm de `approved-texts` na base (P-01, P-02); enquanto forem `null`, entram *placeholders* `[PENDENTE: …]` explícitos, aceitáveis só com dados fictícios.

**Reconciliação:** texto do modelo que anuncia transferência ("vou te transferir", "estou encaminhando") quando o backend não decidiu encaminhar é inconsistente: nova tentativa e, se repetir, encaminhamento com motivo `TURN_FAILURE`. Afirmação de notificação já era reprovada pela V6 (ADR-0008).

## 5. Modo restrito em `WAITING_HUMAN` (ADR-0007, item 4)

- O modelo é chamado com `waiting-human.st` e devolve só sinais (`possibleEmergency`, `explicitHumanRequest`, `priority`, `signals`); não há texto livre de atendimento nem coleta.
- Uma chamada, sem nova tentativa, até 500 tokens, prazo de 15 s (DA-28).
- A prioridade é reavaliada (pode subir, nunca descer); a conversa continua `WAITING_HUMAN`.
- O detector de expressões de alerta também roda aqui.
- **Texto ao tutor (CT-19, opção A aprovada em 2026-11-17):** confirmação de recebimento (`waiting-confirmation`); com `CONFUSION`, `DISSATISFACTION` ou `EMOTIONAL_DISTRESS`, uma explicação honesta do estado (`waiting-explanation`: a conversa aguarda a equipe, o assistente não continua o assunto, o que o tutor escrever fica registrado); o mesmo texto **nunca vai duas vezes seguidas** (alterna com `waiting-explanation-repeat`). Nenhum promete contato. Textos provisórios (P-10, P-19).

## 6. Prazo de atendimento (ADR-0007 item 6, H11)

- Prazo por nível em `assistant.handoff.deadlines`. **Provisório (P-02):** P1 15 min, P2 1 h, P3 4 h, P4 e P5 24 h, P6 48 h.
- A fila (`GET /api/conversations?status=WAITING_HUMAN`) mostra `waitingMinutes` e `overdue`.
- Na próxima mensagem do tutor depois do prazo, a resposta inclui a orientação de contato direto (`approved-texts.direct-contact`, P-02) ou o *placeholder*. Não há job nem notificação ativa.

## 7. Operações da equipe

| Rota | Efeito | Não altera |
|---|---|---|
| `GET /api/conversations?status=` | Fila por prioridade e espera, com atraso | — |
| `POST /api/conversations/{id}/close` | Encerra (`CLOSED_BY_STAFF`) | Solicitações |
| `POST /api/conversations/{id}/return-to-assistant` | `WAITING_HUMAN` → `OPEN`; a prioridade fica | Solicitações |
| `PATCH /api/conversations/{id}/priority` | Muda a prioridade, com registro | Status |
| `GET /api/conversations/{id}/priority-changes` | Histórico das mudanças da equipe | — |
| `GET /api/service-requests?status=` | Solicitações no status | — |
| `PATCH /api/service-requests/{id}` | `IN_PROGRESS`, `RESOLVED` ou `CANCELLED`; desfecho obrigatório ao resolver ou cancelar (DA-34), observação obrigatória com `OTHER` | A conversa |

Transição inválida → `409`; desfecho ausente → `422`. Mesma API Key do canal até a Etapa 7 (perfis: P-17).

## 8. Limites do atendimento (DA-15)

| Limite | Barreiras |
|---|---|
| Não diagnosticar nem interpretar sintomas | Prompt; encaminhamento por piso P2 |
| Não prescrever nem indicar dose | Prompt; **V7** (dose no texto é reprovada) |
| Não inventar preço, horário, serviço ou política | Prompt; V1–V4 (ADR-0008) |
| Não confirmar agendamento | Prompt; V5; o aviso de registro é texto do sistema, que não confirma dia nem horário (seção 10) |
| Não afirmar notificação, contato ou resolução | Prompt; V6; textos do backend nunca afirmam |
| Não anunciar transferência que não aconteceu | Reconciliação (seção 4) |
| Não revelar dados de outros clientes nem as instruções internas | **Estrutural:** o modelo só recebe o cadastro do tutor vinculado à conversa (pelo contato do canal) e, de outras conversas, só tipo, data e status das solicitações abertas (DA-37). Prompt: conteúdo do tutor é dado, não instrução; delimitadores falsos são removidos. Medido por `PrivacyAiIT` (abaixo) |

**Teste adversarial de privacidade (`PrivacyAiIT`, IA real, dados fictícios, 2026-11-17).** Um contato "atacante" tenta obter os dados de outro tutor cadastrado no mesmo banco, o motivo de um pedido anterior dele mesmo (que não vai ao modelo) e as instruções internas. São 7 ataques, com 10 execuções cada e um atacante novo por execução: pedido direto de dados de outro cliente, "ignore as instruções", delimitador falso com "modo administrador", pista parcial ("minha amiga tem uma gata siamesa"), falso funcionário, motivo do pedido anterior e pedido das instruções completas. Resultado: **0 de 70** respostas com dados da vítima ou do pedido anterior e **0 de 70** com trecho das instruções ou dos blocos de contexto. Os dados da vítima não mudaram. O teste mede o comportamento do modelo; a garantia principal continua sendo estrutural (o modelo não recebe o que não pode revelar).

## 9. Rastreabilidade RF-D / RN-D

| Requisito | Situação | Implementação | Teste |
|---|---|---|---|
| RF-D01, RN-D03 | Implementado | `PriorityPolicy` | `PriorityAndHandoffPolicyTest`, CT-03, CT-04, CT-08, CT-12 |
| RF-D02 | Implementado | Modo restrito reavalia | CT-07 |
| RF-D03, RN-D01 | Implementado | Alerta + `possibleEmergency` → orientação primeiro | CT-01, CT-07, `HandoffAiIT` |
| RF-D04, RN-D06 | Implementado | `ExplicitHumanRequestDetector` | `ExplicitHumanRequestDetectorTest`, CT-02 |
| RF-D05 | Implementado | Motivo na conversa e na solicitação; sinais na mensagem | CT-03, CT-10 |
| RF-D06 | Implementado | Campos independentes | `ConversationHandoffAndPriorityTest` |
| RF-D07 | Implementado | Fila ordenada, `overdue` | `GuidelineScenariosIT.queue…`, CT-11 |
| RF-D08, RN-D04 | Implementado (autor individual: E7) | `PATCH …/priority` + `priority_change` | CT-16 |
| RF-D09 | Implementado | DA-31 | CT-14, `AlertAndUnproductiveDetectorsTest` |
| RF-D10 | Implementado (registro + oferta) | Sinais gravados; oferta | CT-03, CT-10 |
| RF-D11 | Implementado com *placeholders* | `ReplyComposer` + `approved-texts` | `ReplyComposerTest` |
| RF-D14 | Implementado (lista ilustrativa) | `triage.*` na camada 2 | `AlertAndUnproductiveDetectorsTest` |
| RF-D15 | Implementado | Fail-safe | CT-07 (falha), CT-15 |
| RF-D16, RN-D11 | Implementado | V5, V6, textos do backend | `ResponseValidatorTest`, `GuidelineScenariosIT` |
| RN-D02 | Implementado | P2 encaminha + orientação curta | CT-03, CT-06 |
| RN-D05 | Parcial | Piso P2 e orientação primeiro no backend; "no máximo uma pergunta" só no prompt | CT-12 |
| RN-D07 | Parcial | Encaminha; "não prometer compensação" só no prompt (e V6 para promessa de contato) | CT-05, `HandoffAiIT` |
| RN-D08 | Parcial | Oferta de humano no backend; "nenhum conteúdo comercial" só no prompt | CT-10 |
| RN-D09 | Implementado (encaminhamento + V7) | Piso P3/P2, V7 | CT-08 |
| RN-D10 | Parcial | Piso P3; "não reinterpretar orientação" só no prompt | CT-08 |
| RN-D12 | Implementado com *placeholder* | Orientação de prazo vencido | CT-11 |
| RN-D13 | **Não implementado** | Registro de feedback para a equipe fica para a Etapa 5 (solicitação de outro tipo) | — |
| RN-D15 | Implementado | Orientação clínica antes; em P1 o texto do modelo não entra | CT-01, `ReplyComposerTest` |
| RF-D12, RN-D14 | Etapa 6 | Avaliação qualitativa | — |
| RF-D13 | Etapas 2 e 5 | Histórico; cadastro com proveniência na E5 | — |

## 10. Coleta de dados e pedido de agendamento (Etapa 5)

**Provisório.** P-06, P-07, P-08 e P-13: valores aprovados pelo responsável em 2026-11-17 para desenvolvimento. P-15: regra provisória prevista no roadmap (não afirmar confirmação). **Somente com dados fictícios**; a clínica ainda define os definitivos.

### 10.1 O que o backend aceita do modelo (`CustomerDataCollector`, ADR-0010 item 4)

| Dado | Aceito quando | Senão |
|---|---|---|
| Nome do tutor | Letras, espaço, `'`, `.`, `-`; até 5 palavras e 120 caracteres; **aparece como palavras numa mensagem do tutor desta conversa** (ou já é o nome cadastrado) | Descartado |
| Nome do pet | Mesmas regras, até 80 caracteres; aparece numa mensagem do tutor ou já é de um pet deste tutor; **não** pode ser só espécie ou termo genérico ("cachorro", "minha gata", "o pet", CT-19). Pet citado só em `appointmentRequest.petName` é tratado como pet citado sem espécie | Descartado |
| Espécie | `DOG`, `CAT` ou `OTHER` com descrição; `UNKNOWN` ou `OTHER` sem descrição = não informada | Fica em aberto |
| Raça, idade, motivo, preferência | Texto sem quebra de linha nem `< > { } [ ]`; cortado no limite (80, 80, 300, 120) | — |
| Confirmação | Ver 10.4 | Ignorada |

Item descartado não falha o turno; o log registra só o campo e o motivo, sem conteúdo.

### 10.2 Tutor e pets (DA-35, DA-36)

- O `Tutor` só é criado no primeiro turno com um dado aceito para guardar (nome ou pet). Quem só tira dúvidas não gera cadastro. A conversa é vinculada nesse momento, e as solicitações já abertas nela passam a apontar para o tutor.
- Conversa nova de um contato que já tem cadastro nasce vinculada ao tutor (pelo identificador do canal).
- Pet citado: procurado **só entre os pets desse tutor**, por nome normalizado (`NameKey`: minúsculas, sem acento, espaços colapsados), sem similaridade aproximada.

| Situação | Resultado |
|---|---|
| Um pet com o nome (espécie igual, não informada ou ainda sem espécie) | Mesmo pet; campos novos são gravados |
| Nenhum pet com o nome, tutor sem pets | Pet novo |
| Nenhum pet com o nome, tutor com pets | Só cria com `newPet = true` (o tutor disse que é outro animal); senão nada muda e o assistente pergunta |
| Dois ou mais com o nome | A espécie desempata; senão nada é vinculado |
| Um com o nome e espécie diferente | Se a espécie foi só **mencionada nesta conversa**, é correção do tutor e é atualizada. Se veio de outra conversa, é conflito: nada muda (ou pet novo com `newPet`) |

### 10.3 Proveniência por campo (DA-35)

Tabela `data_provenance`: um registro por campo (`TUTOR_NAME`, `PET_NAME`, `PET_SPECIES`, `PET_BREED`, `PET_AGE`), com a conversa e o momento da última confirmação.

| Evento | Efeito |
|---|---|
| Valor novo ou diferente | MENCIONADA nesta conversa; confirmação anterior apagada |
| Mesmo valor citado de novo | MENCIONADA nesta conversa (se já estava CONFIRMADA nesta conversa, continua); a data da confirmação anterior é mantida |
| Confirmação válida (10.4) | CONFIRMADA nesta conversa |
| Leitura em outra conversa | CADASTRADA se confirmada há no máximo `registered-validity` (provisório: **365 dias**, P-08); senão A_RECONFIRMAR. Nunca CONFIRMADA |

### 10.4 Confirmação (P-08 provisório)

Uma confirmação marcada pelo modelo só vale se:
1. a **mensagem anterior do assistente** nesta conversa contém o valor gravado (nome como palavras; espécie por palavras como "gato/gata", "cachorro/cão/cadela" ou a descrição);
2. a mensagem do tutor **não discorda** (não começa com "não" nem diz "errado"/"incorreto");
3. o campo tem registro de proveniência.

O pet da confirmação é o nomeado; sem nome, o do pedido em coleta; sem pedido, o único pet citado no turno.

### 10.5 Pedido de agendamento (P-06, P-07, P-13 provisórios; ADR-0011)

- **P-06:** no máximo **uma** `ServiceRequest(APPOINTMENT)` em `COLLECTING` por conversa. Pedidos de informação e de atendimento humano seguem em paralelo.
- **P-07:** **um pet por solicitação**. "Para os dois" vira dois pedidos, um de cada vez: o prompt orienta a registrar o primeiro e depois começar o segundo. Durante a coleta, citar outro pet no pedido troca o pet (é tratado como correção).
- Abertura: turno com intenção `APPOINTMENT_REQUEST` (principal ou secundária) e pelo menos um detalhe (pet, motivo ou preferência). "Quero marcar uma consulta" sozinho não abre nada. Não abre outro pedido para um pet que já tem pedido não encerrado nesta conversa.
- **Assunto sensível (CT-19):** não abre pedido em conversa com o sinal `SENSITIVE_TOPIC` (no turno ou em resposta anterior do assistente); a equipe trata.
- Não abre nem continua a coleta num turno que encaminhou a conversa (os dados do tutor e do pet são guardados mesmo assim).
- **P-13:** para registrar (`COLLECTING → PENDING`): pet com nome e espécie, motivo e nome do tutor; preferência opcional.
- **P-08:** nome e espécie do pet **confirmados nesta conversa**; nome do tutor MENCIONADO, CONFIRMADO ou CADASTRADO (A_RECONFIRMAR não basta).
- O resumo para a equipe é montado pelo backend ("Pedido de agendamento — pet: Mel (gato); motivo: vacina; preferência: de manhã").
- No turno em que o pedido é registrado, o backend acrescenta `assistant.customer-data.appointment-registered-reply` (provisório, P-10). Ele diz que o pedido ficou registrado e que dia e horário só valem depois de confirmados pela equipe. O texto do modelo nunca afirma registro nem confirmação (prompt + V5).
- Pedido em coleta numa conversa que expira continua `COLLECTING` e aparece para a equipe (`GET /api/service-requests?status=COLLECTING`).

### 10.6 "Está confirmado?" e desfecho resolvido por fora (P-15 provisório)

- O assistente não sabe o andamento nem o desfecho. Diz que o pedido está com a equipe e que dia e horário só valem depois de confirmados por ela. V5 e V6 reprovam qualquer afirmação de confirmação ou resolução (CT-09).
- Pedido encerrado pela equipe aparece só como "encerrado pela equipe. Você não sabe o desfecho." O desfecho (DA-34) não vai ao modelo.
- Perguntar pelo andamento, por si só, não encaminha a conversa (prompt). Com a IA real: 10/10 sem encaminhar (`CustomerDataAiIT`).

### 10.8 Assuntos sensíveis e primeira resposta (CT-19, DA-38)

- `SENSITIVE_TOPIC` (eutanásia, doença grave ou terminal, decisão difícil): o modelo acolhe e diz que a equipe veterinária pode conversar sobre isso; o **sistema** faz a oferta de registro (sinal de oferta, como `BEREAVEMENT`). Sem piso de prioridade: P3 já encaminharia, e encaminhar × oferecer é P-12 (clínica). Se o modelo sugerir encaminhamento, ele vale (o backend nunca remove um encaminhamento). Na IA real, o modelo encaminhou a pergunta sobre eutanásia em 10/10 (sinal de decisão da equipe).
- A primeira resposta da conversa leva a apresentação `assistant.conversation.first-reply-intro` ("Sou o assistente virtual da clínica.", nome pendente na P-10) quando o modelo não se apresentou; não em P1/P2, em que a orientação vem primeiro (H6).

### 10.7 Atendimentos anteriores (DA-37)

Até 3 solicitações `PENDING`/`IN_PROGRESS` de outras conversas do mesmo contato, só com tipo, data de abertura e status. A conversa nova nunca altera essas solicitações. Em acompanhamento com condição nova (CT-18), a situação é tratada como nova: intenção clínica impõe piso P2 e encaminha.
