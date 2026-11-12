# ADR-0014 — Barreira entre desenvolvimento e uso com dados reais (LGPD)

- **Status:** Aceito, com pendências (P-03, P-04, P-16, P-17, P-18, P-19, P-20)
- **Data:** 2026-11-11
- **Decisões:** DA-16, DA-17

> Este ADR **não** contém conclusões jurídicas. Ele lista o que precisa ser definido pela clínica e, quando indicado, validado com orientação jurídica antes de qualquer uso com dados reais.

## Contexto

O sistema tratará dados pessoais de tutores (nome, telefone, conteúdo livre de mensagens, que pode incluir informações sensíveis) e os enviará a um provedor externo de IA, com possível transferência internacional. A revisão técnica (9.8) concluiu que a proposta original não tratava isso de forma adequada.

## Decisão

1. **Durante desenvolvimento e testes, só dados fictícios** (DA-17). Isso vale para fixtures, cenários de avaliação com IA real (que enviam dados à OpenAI), demonstrações e bancos locais. Nada de conversas reais, nem de respostas reais da clínica usadas como dados de teste.
2. **Barreira explícita:** a aplicação **não** pode ser usada com dados reais enquanto todos os itens abaixo não estiverem definidos, aprovados e, quando técnicos, implementados. A verificação é o critério de saída da **Etapa 7**.

| # | Item | Define / aprova | Validação jurídica recomendada | Pendência |
|---|---|---|---|---|
| G1 | Aviso de transparência sobre o uso de IA, apresentado ao tutor (conteúdo e momento) | Clínica | Sim | P-04 |
| G2 | Finalidade e base legal de cada tratamento | Clínica | Sim | P-16 |
| G3 | Dados compartilhados com provedores externos de IA, condições de uso e retenção do provedor e eventual transferência internacional | Clínica + responsável técnico | Sim | P-04 |
| G4 | Política de retenção e exclusão (prazos por tipo de dado), implementada | Clínica | Sim | P-03 |
| G5 | Processo para atender pedidos de acesso e exclusão do titular, com procedimento técnico testado | Clínica + responsável técnico | Sim | P-03 |
| G6 | Controle de acesso: autenticação individual, autorização por perfil, auditoria (ADR-0012) | Clínica (perfis) + responsável técnico | — | P-17 |
| G7 | Logs, backups e exposição de dados: logs sem conteúdo pessoal verificados, política de backup e acesso ao banco definidos | Responsável técnico | — | P-18 |
| G8 | Política de emergência aprovada, no lugar do *placeholder* (ADR-0007) | Clínica | — | P-01 |
| G9 | Conteúdo institucional real carregado e revisado pela clínica (ADR-0003) | Clínica | — | P-11 |
| G10 | Prazo de atendimento humano **por nível de prioridade** e comportamento sem funcionário disponível (ADR-0007, ADR-0018) | Clínica | — | P-02 |
| G11 | Critérios clínicos da hierarquia de prioridades (sinais de P1, critérios de P2, regra de P3 e lista de expressões de alerta) aprovados, no lugar da lista ilustrativa (ADR-0018, ADR-0019) | **Equipe veterinária** | — | P-20 |
| G12 | Diretrizes de Atendimento e Conduta aprovadas como texto institucional | Clínica | — | P-19 |

3. **Verificação automática:** no ambiente real, a aplicação recusa iniciar se encontrar *placeholder* (`[PENDENTE: ...]`) ou valor marcado como provisório na configuração (política de emergência, prazos de encaminhamento e expiração, conteúdo institucional, critérios clínicos de prioridade).
4. **Princípios técnicos aplicados desde a Etapa 1**, independentemente da barreira:
   - logs sem conteúdo de mensagens nem dados pessoais;
   - `org.springframework.ai` fora de `DEBUG`;
   - o contexto do modelo contém só os dados do tutor da conversa;
   - coleta limitada ao necessário para a solicitação.

## Alternativas consideradas

- **Tratar LGPD só no fim, sem barreira formal:** risco de um piloto "informal" com dados reais; rejeitada.
- **Resolver as definições jurídicas agora:** depende da clínica (controladora) e de orientação jurídica; fora do alcance técnico.

## Consequências

- A Etapa 7 tem itens que não são código (definições da clínica e validação jurídica) e pode atrasar o uso real mesmo com o software pronto.
- O "pronto" das Etapas 1 a 6 é sempre "pronto com dados fictícios".
