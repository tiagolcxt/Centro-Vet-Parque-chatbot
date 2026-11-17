# ADR-0011 — ServiceRequest separado da conversa; solicitação ≠ agendamento

- **Status:** Aceito, com pendências (P-15, provisório na Etapa 5). PT-08 decidida em 2026-11-17 (DA-34); revisado na Etapa 5
- **Data:** 2026-11-11
- **Decisão:** DA-07

## Contexto

A proposta original tinha um único `PATCH /api/service-requests/{id}` que também mudava o estado da conversa (D6), acoplando dois agregados. O MVP não tem integração com agenda: o assistente só organiza pedidos. Nunca pode afirmar que uma consulta está marcada.

## Decisão

1. **`ServiceRequest` e `Conversation` são agregados independentes**, cada um com suas operações:

   | Operação | Endpoint | Efeito | Não faz |
   |---|---|---|---|
   | Atualizar solicitação | `PATCH /api/service-requests/{id}` | Transição de status pela equipe: `IN_PROGRESS`, `RESOLVED`, `CANCELLED` | Não altera a conversa |
   | Encerrar conversa | `POST /api/conversations/{id}/close` | `CLOSED`, `closeReason = CLOSED_BY_STAFF` | Não resolve solicitações |
   | Devolver ao assistente | `POST /api/conversations/{id}/return-to-assistant` | `WAITING_HUMAN` → `OPEN` | Não resolve solicitações |

2. **Ciclo de vida da solicitação:**
   ```
   COLLECTING ──submit()──► PENDING ──► IN_PROGRESS ──► RESOLVED
       └──────────────────────┴─────────────┴──────────► CANCELLED
   ```
   - `COLLECTING`: dados ainda em coleta pelo assistente.
   - `submit()` só é aceito com os dados obrigatórios completos (P-13).
   - Tipos: `APPOINTMENT`, `INFORMATION`, `FOLLOW_UP`, `HUMAN_CONTACT`.
3. **Solicitação de agendamento não é agendamento.**
   - O modelo de dados **não tem** data/hora confirmada: só `preferredPeriod` em texto livre, como informado pelo tutor.
   - Nomes na API e no código: `appointmentRequest`, nunca `appointment` sozinho.
   - O assistente **nunca** afirma que uma consulta foi marcada ou confirmada. A validação bloqueia essas expressões (ADR-0008, V5).
   - Se o tutor perguntar se está confirmado, o assistente diz que o pedido foi registrado para a equipe e que a confirmação de dia e horário é feita pela equipe. Ele não tem como saber o desfecho.
4. Encerrar uma conversa com solicitações abertas é permitido. Elas continuam na listagem `GET /api/service-requests?status=...`.

## Pendências ligadas

- **Desfecho (DA-34, resolve PT-08):** ao resolver (`RESOLVED`) ou cancelar (`CANCELLED`), a equipe informa um desfecho obrigatório de uma lista fixa: `SCHEDULED_BY_STAFF` (agendado pela equipe), `INFORMATION_PROVIDED` (informação passada), `HANDLED_OUTSIDE` (atendido por telefone ou presencialmente), `NO_AVAILABILITY` (sem horário disponível), `CUSTOMER_GAVE_UP` (tutor desistiu), `NO_RESPONSE` (sem retorno do tutor), `DUPLICATE` (outra solicitação já cobria) ou `OTHER`. Uma observação opcional de até 500 caracteres acompanha o desfecho e é obrigatória com `OTHER`. O contrato da API avisa para não escrever dados de saúde nem dados pessoais além do necessário. O assistente não lê o desfecho no MVP (P-15).
- **P-15:** o que o assistente deve dizer quando a equipe resolveu por fora (telefone) e o tutor volta a perguntar.

## Alternativas consideradas

- **Um endpoint que altera os dois agregados** (proposta original): combinações ambíguas; rejeitada.
- **Estado de agendamento confirmado no MVP:** exigiria integração com agenda (fora do escopo) ou registro manual que o assistente poderia repetir; rejeitada.

## Consequências

- A equipe faz duas ações quando quer resolver a solicitação e encerrar a conversa. É mais explícito e um pouco mais trabalhoso sem um painel.
- A separação conceitual fica garantida pelo próprio modelo de dados, não só por instrução ao modelo de IA.

## Revisão (Etapa 5, 2026-11-17): pedido de agendamento

Com valores **provisórios** de P-06, P-07 e P-13 (só dados fictícios). Regras completas: [business-rules.md, seção 10.5](../business-rules.md#105-pedido-de-agendamento-p-06-p-07-p-13-provisórios-adr-0011).
- Colunas próprias (DA-19, migration V4): `reason` (até 300) e `preferred_period` (até 120, texto livre). Uma CHECK impede esses campos fora do tipo `APPOINTMENT`. Continua **sem data/hora confirmada**.
- `RequestField.REASON` passa a poder ser exigido no `submit()`.
- Uma solicitação de agendamento em coleta por conversa (P-06); um pet por solicitação (P-07).
- Registro (`PENDING`) só com pet (nome e espécie), motivo e nome do tutor (P-13), e com nome e espécie do pet confirmados na conversa (P-08 provisório).
- O aviso de registro ao tutor é texto do sistema e diz que dia e horário só valem depois de confirmados pela equipe.
- **P-15 (provisório):** o assistente não lê o desfecho; pedido encerrado aparece ao modelo só como "encerrado pela equipe". Ele não afirma confirmação nem resolução (V5, V6).
- A resposta da equipe (`GET /api/service-requests`) passou a trazer `tutorId`, `petId`, `reason` e `preferredPeriod`.
