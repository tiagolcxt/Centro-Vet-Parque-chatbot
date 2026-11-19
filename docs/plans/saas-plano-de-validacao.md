# Plano de validação do MVP SaaS

> Versão 1 — 2026-11-18. Base: briefing seção 15, [PRD](../product/saas/prd.md) e ADR-0025 (níveis de ativação).
> **Não há benchmarks inventados.** As metas numéricas são definidas com a linha de base medida na Etapa S0 e confirmadas com a clínica antes da S6.

## 1. O que queremos provar

1. A clínica consegue **configurar** a própria operação sem ajuda técnica.
2. As mensagens **chegam ao setor certo** e têm um responsável.
3. A equipe **acompanha até a resolução** pela aplicação.
4. O assistente **reduz trabalho repetitivo** sem pôr o tutor nem o animal em risco.

## 2. Indicadores

| Grupo | Indicador | Como medir | Meta |
|---|---|---|---|
| Atendimento | Tempo até a primeira resposta | Mensagem do tutor → primeira resposta (IA ou equipe) | Menor que a linha de base |
| Atendimento | Mensagens sem resposta | Conversas com a última mensagem do tutor sem resposta há mais de X min no horário de funcionamento | X e meta definidos na S0 |
| Atendimento | Conversas aguardando funcionário | WAITING_HUMAN por setor, e o tempo nesse estado | Prazo por prioridade (P-02) |
| Atendimento | Taxa de encaminhamento | Conversas que foram para WAITING_HUMAN ÷ total | Acompanhar, sem meta: encaminhar não é falha |
| Atendimento | Taxa de resolução | Solicitações encerradas com resultado ÷ abertas no período | Definida na S0 |
| IA | Correção da classificação | Amostra semanal revisada pela equipe (intenção e setor certos?) | ≥ 90% na amostra para subir do nível 1 para o 2 (provisório) |
| IA | Respostas corrigidas pela equipe | Conversas em que a equipe assumiu para corrigir a IA ÷ conversas com resposta da IA | Queda semana a semana |
| IA | Informação inventada ou não verificada | Revisão da amostra mais as reprovações do validador | **0 aprovada com fato errado** |
| IA | Falhas de extração | Dados do pet ou do tutor corrigidos pela equipe | Acompanhar |
| IA | Custo por conversa | Tokens por turno (métricas existentes) × preço do modelo, mais o custo por mensagem do WhatsApp | Teto definido com o preço do SaaS |
| IA | Latência | Por turno (métricas existentes) | Dentro do DA-28 |
| IA | Intervenção humana | Conversas com mensagem STAFF ÷ total | Acompanhar por nível |
| Operação | Solicitações pendentes por setor | Contagem diária | Acompanhar |
| Operação | Tempo até atribuição | PENDING → primeiro ASSIGNED | Definido na S0 |
| Operação | Tempo até resolução | Abertura → encerramento | Menor que a linha de base |
| Operação | Encaminhamento para o setor errado | Transferências motivadas por "setor errado" ÷ solicitações | Queda; serve para ajustar as regras |
| Operação | Volume por setor | Contagem | Acompanhar |
| Experiência | Satisfação do tutor | Pergunta curta opcional ao encerrar, se a clínica aprovar | Definida na S0 |
| Experiência | Satisfação da equipe | Questionário quinzenal curto | Melhora em relação à 1ª rodada |
| Experiência | Facilidade de configuração | Tempo para o administrador concluir o assistente de configuração e fazer uma mudança simples, observado | Sem ajuda técnica |
| Experiência | Trabalho repetitivo | Perguntas respondidas só pela IA (níveis 2 e 3), revisadas | Acompanhar |

## 3. Situações críticas: critério próprio

Estes critérios **não** entram na média. Qualquer ocorrência para a subida de nível e abre uma correção.

| Situação | Critério |
|---|---|
| Possível emergência (P1/P2) | 100% reconhecidas na amostra revisada; orientação aprovada enviada; conversa no topo |
| Pedido de pessoa | 100% atendidos, sem insistência |
| Eutanásia, luto, assunto sensível | 0 oferta comercial; 0 informação clínica; tom revisado pela equipe |
| Notificação | 0 frase dizendo que alguém foi avisado sem ter sido |
| Agendamento | 0 confirmação de agendamento pela IA |
| Dados de outro tutor | 0 vazamento |

## 4. Como validar

| Etapa | Validação |
|---|---|
| S1 a S5 | Testes automatizados (unidade, integração, isolamento, permissão, contrato) mais a bateria de IA real com 10 repetições (DA-28) |
| S3 | Teste de usabilidade: alguém da clínica configura a clínica fictícia sozinho, observado, com tempo e dúvidas registrados |
| S5 | Simulação de um dia de trabalho com a equipe, usando conversas fictícias tiradas do questionário |
| S6, nível 1 | 2 semanas: equipe usando a caixa de entrada; IA só organiza. Mede a correção da classificação e gera a linha de base com a ferramenta |
| S6, nível 2 | Começa quando a classificação estiver no critério e não houver situação crítica. Revisão semanal |
| S6, nível 3 | Mesmo critério no nível 2, mais o aceite do veterinário responsável |

A duração de 2 semanas do nível 1 é provisória, a ajustar com o volume real.

## 5. Revisão semanal no piloto

Responsáveis: nós e alguém da clínica, em uma conversa de 30 minutos. Pauta:

- indicadores da semana;
- amostra de 20 conversas (critério de escolha: todas as P1/P2, todas as transferências por setor errado e o resto aleatório);
- situações críticas;
- decisões de ajuste nas regras de encaminhamento, nos textos e no nível.

As conversas são revisadas **dentro da aplicação**, por quem tem permissão. Nada é exportado para fora.
