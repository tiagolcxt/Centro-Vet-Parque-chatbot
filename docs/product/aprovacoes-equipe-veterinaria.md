# Informações que precisam de aprovação antes de produção

> Versão 1 — 2026-11-11. Esta lista pode ser enviada à clínica.
> **Nada desta lista será inventado pelo projeto.** Enquanto não houver aprovação, o sistema roda só com dados fictícios e *placeholders* explícitos, e o ambiente real recusa iniciar (ADR-0014).

## A. Equipe veterinária (responsável técnico)

| # | O que precisa ser aprovado | Por que o sistema precisa | Pendência | Necessária na etapa |
|---|---|---|---|---|
| V1 | **Sinais de possível emergência (P1)**: validar e completar a lista ilustrativa (dificuldade respiratória, convulsão, colapso, hemorragia intensa, suspeita de intoxicação, trauma grave, incapacidade súbita de se mover, dor intensa) | Critério que o assistente usa para identificar P1 | P-20 | 4 (ilustrativa) / 7 |
| V2 | **Critérios de urgência (P2)**: validar e completar a lista ilustrativa (piora, dor relevante, alteração de comportamento, recusa de comida ou água, alteração pós-procedimento) | Critério de P2 | P-20 | 4 / 7 |
| V3 | Se acompanhamentos (P3) exigem **sempre** avaliação profissional ou só em alguns casos | Regra de encaminhamento em P3 | P-20 | 4 / 7 |
| V4 | **Lista de expressões de alerta** usada como verificação automática (ADR-0019, adotada): quais expressões indicam P1 e quais indicam P2 | Proteção adicional contra erro do modelo: a lista só eleva a prioridade | P-20 | 4 (fictícia) / 7 |
| V5 | **Protocolo de emergência**: texto exato da orientação; se a clínica atende emergências e em que horários; o que dizer **quando a clínica não puder receber** (fora do expediente, lotação); parceiro ou plantão a indicar | Texto que o backend envia em P1, inclusive em `WAITING_HUMAN` | P-01 | 4 (*placeholder*) / 7 |
| V6 | **Orientação curta para P2**: quando o tutor deve procurar atendimento imediato | Texto incluído em P2 e nas situações ambíguas | P-01 | 4 / 7 |
| V7 | Como tratar perguntas sobre **medicamentos, doses e exames** (texto de recusa e encaminhamento) | Texto padrão em RN-D09 | P-12 | 4 / 7 |

## B. Clínica (gestão e atendimento)

| # | O que precisa ser aprovado | Pendência | Necessária na etapa |
|---|---|---|---|
| C1 | As **Diretrizes de Atendimento e Conduta** como texto institucional, incluindo as recomendações para a equipe humana (seção 6) | P-19 | 7 |
| C2 | **Prazo de atendimento humano por nível** (P1 a P6) e o que dizer quando o prazo vence | P-02 | 4 (valor de teste) / 7 |
| C3 | **Protocolo de indisponibilidade**: fora do expediente, sem funcionário disponível | P-02 | 4 / 7 |
| C4 | Em **sofrimento emocional**, **luto** e **vulnerabilidade financeira**: oferecer o atendimento humano ou encaminhar direto? | P-12 | 4 / 7 |
| C5 | Quem recebe **reclamações e feedbacks**, e se existe processo real de **retorno ao tutor**. Sem processo, o assistente não promete contato | P-14 | 4 / 7 |
| C6 | **Condições de pagamento** que podem ser informadas (parcelamento, formas), se houver | P-11 | 3 / 7 |
| C7 | Persona, tratamento, tamanho das respostas e uso de emojis | P-10 | 2 / 6 |

## C. Já pendentes em outros documentos (lembrete)

P-03 (retenção), P-04 (transparência sobre IA e provedores externos), P-11 (informações institucionais), P-13 (dados obrigatórios do agendamento), P-16 (base legal), P-17 (perfis de acesso). Estão no [registro de decisões](../plans/registro-de-decisoes.md).
