# ADR-0018 — Prioridade clínica separada do estado operacional

- **Status:** Aceito, com pendências (P-02, P-20)
- **Data:** 2026-11-11
- **Decisão:** DA-23

## Contexto

As [Diretrizes de Atendimento](../../product/diretrizes-de-atendimento.md) exigem que as mensagens sejam organizadas por risco ao animal e urgência, não por ordem de chegada; que a prioridade seja reavaliada a cada mensagem, inclusive em conversas que já aguardam a equipe; e que a hierarquia não dependa só da interpretação do modelo. O briefing original proíbe criar classificação médica de gravidade própria.

## Decisão

1. **Seis níveis** (`ClinicalPriority`): `P1_POSSIBLE_EMERGENCY`, `P2_URGENT`, `P3_CLINICAL_FOLLOW_UP`, `P4_SERVICE_REQUEST`, `P5_ADMINISTRATIVE`, `P6_RELATIONSHIP`. A definição está na [hierarquia de prioridades](../../product/hierarquia-de-prioridades.md).
2. **`Conversation.clinicalPriority` é independente de `Conversation.status`.** Uma conversa `WAITING_HUMAN` pode subir de P4 para P1 com uma nova mensagem.
3. **`PriorityPolicy` (domínio, determinística)** calcula o nível efetivo de cada turno como o **mais urgente** entre:
   - (a) o nível sugerido pelo modelo;
   - (b) os pisos do backend (H5): possível emergência → P1; `MEDICAL_CONCERN`, agravamento, alteração pós-procedimento ou incerteza clínica → no máximo P2; `TREATMENT_FOLLOW_UP` → no máximo P3; correspondência na lista aprovada de expressões de alerta → P1 ou P2 (ADR-0019, DA-27);
   - (c) o nível atual da conversa.
4. **Rebaixamento automático é proibido.** Só um funcionário rebaixa, por `PATCH /api/conversations/{id}/priority`, com registro de nível anterior, novo nível, momento e autor (autor individual só a partir da E7). Uma conversa nova (ADR-0009) começa em P6.
5. **A prioridade não usa dados comerciais.** Nenhum dado de valor, compras ou perfil de cliente entra no cálculo.
6. **A fila da equipe** é ordenada por prioridade e, dentro do nível, por tempo de espera desde o encaminhamento. O **atraso** é calculado com o prazo de cada nível (P-02).
7. **Os critérios clínicos não são do sistema.** Os sinais de P1/P2 entregues ao modelo vêm do conhecimento institucional aprovado pela equipe veterinária (P-20). Até lá, só a lista ilustrativa das diretrizes é usada, e apenas com dados fictícios. O ambiente real recusa iniciar sem a lista aprovada (ADR-0014).
8. Cada mensagem do assistente registra a prioridade efetiva, a origem (`MODEL`, `BACKEND_FLOOR`, `CURRENT_LEVEL`) e os sinais do turno, para auditoria e métricas.

## Alternativas consideradas

- **Prioridade como novo valor de `status`** (ex.: `EMERGENCY`): mistura urgência com fila; uma conversa não pode estar "em emergência" e "aguardando humano" ao mesmo tempo. Rejeitada.
- **Prioridade decidida só pelo modelo:** contraria a diretriz de que a hierarquia não depende só do modelo. Rejeitada.
- **Rebaixamento automático quando o tutor diz que melhorou:** contraria "não minimizar sinais" e "incerteza não é segurança". Rejeitada.
- **Classificação de gravidade própria do sistema:** vetada pelo briefing; os critérios são da equipe veterinária.

## Consequências

- Excesso de prioridade alta (super-triagem) é preferível a sub-triagem, mas pode cansar a equipe. A Etapa 6 mede a distribuição por nível e a frequência de rebaixamento pela equipe.
- O modo restrito de `WAITING_HUMAN` passa a devolver `priority` e `signals[]`.
- Migration na Etapa 4: colunas de prioridade na `conversation` e na `message`.

## Implementação (Etapa 4, 2026-11-17)

- `ClinicalPriority`, `PrioritySource` e `PriorityPolicy` no domínio do módulo `conversation`; migration `V3` com `clinical_priority` (padrão P6), `CHECK` dos valores e índice da fila `(status, clinical_priority, handoff_at)`.
- **Nível do turno × nível efetivo:** a política devolve os dois. O efetivo (mais urgente entre turno e conversa) fica na conversa; o do turno decide encaminhamento e composição, para que uma conversa devolvida pela equipe não volte à fila por causa do nível antigo.
- **Origem (item 8):** `MODEL`, `BACKEND_FLOOR` (piso igual ou mais urgente que a sugestão) ou `CURRENT_LEVEL` (o nível já existente prevaleceu). Gravada em cada mensagem do assistente, com os sinais.
- **Rebaixamento (item 4):** `PATCH /api/conversations/{id}/priority` + tabela `priority_change` (nível anterior, novo, nota, momento, autor). Autor = "equipe (API Key)" até a Etapa 7.
- **Fila (item 6):** ordenação pelo nome do enum (P1 antes de P6) e por `handoff_at`; o atraso usa o prazo do nível atual (P-02, provisório).
- **Sem dados comerciais (item 5):** as entradas da política são intenções, sinais, possível emergência, incerteza clínica e piso de alerta; um teste confere a lista de componentes.
- Regras completas: [business-rules.md](../business-rules.md).
