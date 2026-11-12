# ADR-0009 — Ciclo de vida da conversa e expiração por inatividade

- **Status:** Aceito, com pendências (P-05, P-06)
- **Data:** 2026-11-11
- **Decisão:** DA-13

## Contexto

A proposta original só criava conversas por `POST` explícito e não definia quando uma conversa termina (revisão técnica, I2). No WhatsApp o tutor simplesmente escreve. Sem regra, a mesma conversa duraria indefinidamente, misturando estado, histórico e solicitações de atendimentos diferentes.

## Decisão

1. **Uma conversa representa um atendimento contínuo** de um contato (`channel` + `externalContactId`).
2. **No máximo uma conversa ativa** (`OPEN` ou `WAITING_HUMAN`) por contato. A regra é garantida pela aplicação dentro de transação. O mecanismo no banco (constraint com coluna gerada ou lock) será verificado na Etapa 2.
3. **Uma conversa nova começa quando**, ao chegar uma mensagem (ou um `POST /api/conversations`) de um contato:
   - não há conversa ativa; **ou**
   - a conversa ativa está **expirada por inatividade**: `now > lastActivityAt + prazo(status)`. Nesse caso, ela é encerrada (`CLOSED`, `closeReason = EXPIRED`) antes de abrir a nova.
4. **Conversa encerrada não é reaberta.** Ela termina por expiração (`EXPIRED`) ou por ação da equipe (`CLOSED_BY_STAFF`, ADR-0011).
5. **A expiração é avaliada quando o contato volta a escrever** (ou no `POST`). Não há job agendado. Uma conversa `WAITING_HUMAN` sem retorno do tutor continua aparecendo nas listagens até a equipe agir. É o comportamento desejado, porque ainda não foi atendida.
6. `POST /api/conversations` devolve a conversa ativa existente (`200`), se houver e não estiver expirada, ou cria uma nova (`201`).
7. **O que passa para a conversa nova:**

   | Informação | Passa? | Como |
   |---|---|---|
   | Cadastro do tutor e dos pets | Sim | Com proveniência "cadastrada" ou "a reconfirmar", **nunca** como confirmação atual (ADR-0010) |
   | Mensagens da conversa anterior | Não | — |
   | Solicitações da conversa anterior | Não | Não são carregadas no contexto nem alteradas a partir da conversa nova; continuam visíveis para a equipe |
   | Motivo, sintomas, preferências de horário, estado operacional | Não | Pertencem à conversa ou solicitação anterior |

8. Os prazos de expiração por status (`OPEN`, `WAITING_HUMAN`) são **configuração obrigatória sem valor padrão**. **Valores pendentes (P-05).** Testes usam valores explícitos.

## Alternativas consideradas

- **Conversa perpétua por contato:** acumula estado de atendimentos diferentes; rejeitada.
- **Job agendado para encerrar conversas inativas:** mais um mecanismo; encerraria `WAITING_HUMAN` não atendidas, escondendo-as da equipe. Rejeitada no MVP.
- **Mostrar solicitações anteriores em aberto no contexto da conversa nova:** evitaria duplicidade, mas mistura atendimentos. Fica para avaliação junto com P-06.
- **Nova conversa a cada mensagem após X minutos, independentemente do status:** perderia o vínculo com o encaminhamento em andamento.

## Consequências

- Um tutor que volta depois do prazo, com uma solicitação anterior ainda `PENDING`, pode gerar uma solicitação duplicada. A equipe vê as duas. É aceito no MVP.
- O comportamento definitivo depende dos prazos (P-05). Até lá, só testes e desenvolvimento com valores provisórios.
- Regras para várias solicitações na mesma conversa: **P-06**.
