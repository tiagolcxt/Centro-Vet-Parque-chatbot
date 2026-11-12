# ADR-0015 — Histórico montado pela aplicação, sem `ChatMemory` do Spring AI

- **Status:** Aceito
- **Data:** 2026-11-11
- **Decisão:** DA-18 (resolve PT-01)

## Contexto

As mensagens são persistidas pela aplicação na tabela `message` (DM-15) e o histórico enviado ao modelo precisa respeitar duas regras: 20 mensagens **e** orçamento de tokens (ADR-0004). Além disso, deve conter só a conversa atual (ADR-0009). O Spring AI oferece `ChatMemory`, `MessageWindowChatMemory`, `ChatMemoryRepository` e `MessageChatMemoryAdvisor`, que guardam e injetam o histórico automaticamente.

## Decisão

1. O histórico é **montado pela própria aplicação**: o `ContextAssembler` lê a janela recente da tabela `message` e o adaptador `SpringAiAssistantModel` a converte em mensagens `user`/`assistant` do Spring AI.
2. **Não** são usados `ChatMemory`, `ChatMemoryRepository` nem `MessageChatMemoryAdvisor`.
3. A tabela `message` é a **única fonte de verdade** das mensagens.

## Alternativas consideradas

- **`MessageChatMemoryAdvisor` + `JdbcChatMemoryRepository`:** menos código próprio, mas cria uma segunda cópia das mensagens (outra tabela e outro esquema), a janela padrão conta mensagens e não tokens, e o controle de qual conversa entra no contexto fica dentro do advisor.
- **`ChatMemory` com repositório próprio adaptado à tabela `message`:** evita a duplicação, mas acopla o modelo de persistência à API do Spring AI, que é *milestone* (R-13) e pode mudar.

## Consequências

- A lógica de janela e orçamento de tokens é código da aplicação, testável sem Spring AI (unitário) e com Testcontainers (integração).
- Uma futura migração para a memória do Spring AI exigiria só a troca da montagem no adaptador; o domínio não muda.
