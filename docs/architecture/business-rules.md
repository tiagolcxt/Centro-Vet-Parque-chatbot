# Regras de negócio implementadas — encaminhamento, prioridade e limites

> Reflete o código ao fim da **Etapa 4 — Encaminhamento humano e segurança** (2026-11-17). Decisões: ADR-0007, ADR-0017, ADR-0018, ADR-0019, DA-15, DA-25, DA-30, DA-31, DA-33, DA-34. Requisitos: [requisitos-diretrizes.md](../specs/requisitos-diretrizes.md). Cenários: [cenarios-diretrizes.md](../specs/cenarios-diretrizes.md).
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
| Não confirmar agendamento | Prompt; V5 |
| Não afirmar notificação, contato ou resolução | Prompt; V6; textos do backend nunca afirmam |
| Não anunciar transferência que não aconteceu | Reconciliação (seção 4) |

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
