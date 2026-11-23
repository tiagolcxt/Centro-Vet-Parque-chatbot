# ADR-0027 — Risco clínico, tipo da demanda e prioridade da fila separados

- **Status:** Aceito (D9, 2026-11-23)
- **Decisão:** D9 da [revisão pré-piloto](../../plans/revisao-pre-piloto.md), seção 3.2
- **Complementa:** ADR-0018 (prioridade clínica), que continua valendo como motor interno

## Contexto

O nível P1–P6 do ADR-0018 mistura três coisas diferentes:
- o risco para o animal (P1 a P3);
- o assunto (P4 serviço, P5 administrativo, P6 relacionamento);
- a ordem da fila.

A revisão pré-piloto pediu que a equipe veja essas dimensões separadas, e que a fila seja ordenada por uma
prioridade operacional que a clínica pode ajustar.

## Decisão

1. **Risco clínico (conversa):**
   - Valores: possível emergência, urgente, acompanhamento clínico, sem sinal clínico, não avaliado.
   - É **derivado** do nível P1–P6: P1 vira possível emergência, P2 urgente, P3 acompanhamento, e P4–P6 viram "sem sinal clínico".
   - "Não avaliado" vale quando ainda não houve turno válido do modelo, expressão de alerta nem decisão de uma pessoa (`risk_assessed`).
   - O P1–P6 continua sendo o motor das regras de segurança: detectores, `PriorityPolicy`, encaminhamento, prazos e a saída do modelo. Nada disso mudou.
2. **Tipo da demanda (solicitação):**
   - Valores: agendamento, dúvida clínica, informação administrativa, produto/serviço do pet shop, banho e tosa, reclamação, atendimento humano, outros.
   - O backend classifica a partir da intenção, do motivo do encaminhamento e do setor do serviço citado.
   - A equipe corrige pela tela, e a correção fica na auditoria.
   - Sem classificação, o tipo é deduzido do fluxo (`RequestType`).
3. **Prioridade da fila:**
   - Valores: imediata, alta, normal, baixa.
   - Regra inicial (`OperationalPriority.derive`), provisória até a clínica revisar:
     - possível emergência é imediata;
     - urgente é alta;
     - o resto é normal;
     - "sem sinal clínico" em demanda de pouco impacto é baixa;
     - prazo vencido sobe um nível, sem nunca virar imediata.
   - Na conversa, a equipe pode fixar a prioridade ou voltar para a automática. A mudança é auditada e não altera o risco.
   - As filas de conversas e de solicitações ordenam por ela e, depois, pelo tempo de espera.
4. **Mudança de risco pela equipe:** é feita pelo nome do risco. O nível interno equivalente é gravado no histórico de prioridade (rebaixar exige uma pessoa, H4).

## Alternativa adiada

Trocar o campo `priority` da saída do modelo por `clinicalRisk` (`AssistantTurn` v4, item E4.3 da revisão).

Ficou para depois porque a conversão de P1–P6 é determinística e mudar o prompt antes da bateria de IA real arrisca as
regras de segurança sem ganho visível. A troca será reavaliada com o resultado da bateria (E8).

## Consequências

- A API mantém `priority` (P1–P6) por compatibilidade e acrescenta `clinicalRisk`, `operationalPriority` e `demandType`, além de `operationalOverride` no detalhe da conversa.
- V11 acrescenta `conversation.risk_assessed`, `conversation.operational_override` e `service_request.demand_type`, com preenchimento dos dados existentes.
- Os indicadores por tipo da demanda ficam para a revisão dos indicadores.
