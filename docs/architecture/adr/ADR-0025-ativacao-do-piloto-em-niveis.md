# ADR-0025 — Ativação do assistente em três níveis por clínica

- **Status:** Aceito (opção A, 2026-11-18). Os critérios numéricos de subida serão definidos com a linha de base da Etapa S0
- **Decisão:** DA-48
- **Data:** 2026-11-18

## Contexto

O assistente foi validado com dados fictícios. Com tutores reais, um erro custa caro, e a clínica ainda não tem uma linha de base para comparar. Ativar tudo de uma vez mistura dois riscos: a equipe aprender a ferramenta e a IA errar com clientes reais.

## Decisão

Cada clínica tem um `activation_level`:

| Nível | O assistente | O tutor recebe |
|---|---|---|
| 1. Organiza | Classifica, extrai dados e abre a solicitação no setor. Não responde | Texto de recebimento aprovado pela clínica. A equipe responde pela web |
| 2. Informa | Responde só perguntas sobre a clínica (horário, serviços, preços divulgados). Pedidos e assuntos clínicos são registrados e encaminhados | Resposta informativa ou "encaminhei para a Recepção" + próximo passo |
| 3. Completo | Todo o comportamento atual (coleta de dados, encaminhamento resolutivo) | — |

**Regras comuns:**

- A prioridade, os sinais de alerta e a orientação de emergência valem **em todos os níveis**. No nível 1, a orientação de emergência aprovada é a única resposta automática além do recebimento.
- A subida de nível é feita pelo administrador da plataforma, com critério escrito: um mínimo de conversas revisadas e zero comportamento proibido. A data não é critério.

## Alternativas

| Opção | Vantagem | Desvantagem |
|---|---|---|
| (A) Três níveis | Reduz o risco; mede a equipe antes da IA; gera a linha de base | Mais um parâmetro de comportamento para testar |
| (B) Tudo de uma vez | Mais simples | Erros da IA e da adoção se misturam; sem comparação |

## Consequências

- Os testes de IA rodam nos três níveis.
- O nível aparece na tela da clínica, para a equipe saber o que o assistente está fazendo.
