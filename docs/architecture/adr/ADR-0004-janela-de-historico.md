# ADR-0004 — Janela de histórico: 20 mensagens + orçamento de tokens

- **Status:** Aceito, com pendências (P-09)
- **Data:** 2026-11-11
- **Decisão:** DA-10

## Contexto

O contexto da conversa precisa ser reconstruído a partir do banco a cada mensagem (nada só em memória). Enviar todo o histórico indefinidamente aumenta custo e latência e pode estourar o contexto do modelo. A revisão técnica (9.3) mostrou que contar só mensagens ignora o tamanho de cada uma e que informação livre (fora dos dados estruturados) pode se perder.

## Decisão

1. O histórico enviado ao modelo é composto pelas **últimas 20 mensagens da conversa atual**, como mensagens reais `user`/`assistant`.
2. Aplica-se também um **orçamento de tokens** para o histórico: mensagens são removidas a partir da mais antiga até o total estimado caber no orçamento. Vale o limite que for atingido primeiro (20 mensagens ou orçamento).
3. **A mensagem atual do tutor nunca é cortada.** Ela é limitada na entrada pelo tamanho máximo de mensagem (P-09).
4. **Sem sumarização automática** neste momento.
5. Somente mensagens da **conversa atual** entram no histórico. Conversas anteriores não entram (ADR-0009).
6. O valor do orçamento de tokens e o método de estimativa (tokenizador do Spring AI ou aproximação por caracteres, a verificar na Etapa 2) são parâmetros configuráveis. Os valores finais estão **pendentes (P-09)**.

## Alternativas consideradas

- **Só N mensagens:** simples, mas ignora o tamanho.
- **Só orçamento de tokens:** controla custo, mas uma conversa de muitas mensagens curtas pode carregar dezenas de trocas.
- **Janela + resumo incremental:** preserva conversas longas, mas adiciona chamada, custo e mais um texto que pode distorcer; adiado.
- **Janela + observações estruturadas (`notes[]`):** alternativa a avaliar se a Etapa 6 medir perda de contexto relevante; não aprovada.
- **Histórico completo:** custo e latência crescentes; rejeitada.

## Consequências

- Informação livre que sai da janela se perde. Os dados estruturados sobrevivem pelas camadas de tutor/pet (ADR-0010) e operacional (solicitação em andamento).
- A Etapa 6 deve **medir** quantas conversas ultrapassam a janela e se houve perda relevante, para decidir sobre evolução.
- O histórico é montado pela própria aplicação, sem `ChatMemory` do Spring AI (DA-18, ADR-0015).
