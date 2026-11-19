# ADR-0022 — Aplicação web em React servida pelo Spring

- **Status:** Aceito (opção A, 2026-11-18)
- **Decisão:** DA-44
- **Data:** 2026-11-18

## Contexto

O MVP precisa de uma aplicação web responsiva para a recepção, os setores e a administração (briefing 10.1 e 11). A caixa de entrada e as filas mudam o tempo todo e precisam funcionar no celular. Hoje o projeto não tem front-end.

## Decisão

1. SPA em **React com TypeScript (Vite)**, em `assistant-backend/web/`, no mesmo repositório.
2. Em produção, o build é servido pelo Spring como conteúdo estático, no mesmo domínio da API: sem CORS, com sessão por cookie.
3. **Tempo real** por Server-Sent Events. O evento traz só ids; a tela busca os dados pela API, que aplica a permissão.
4. **Testes:** Vitest nos componentes; Playwright num caminho de ponta a ponta.
5. As versões exatas das bibliotecas do front-end são fixadas no início da Etapa S2, com as versões estáveis daquele momento.

## Alternativas

| Opção | Vantagem | Desvantagem |
|---|---|---|
| (A) React SPA | Interface rica e responsiva; tempo real natural; ecossistema grande | Segunda linguagem e um build de front-end para manter |
| (B) Thymeleaf com htmx | Só Java; sem build de front-end | Interações complexas (três colunas, atualização ao vivo, celular) ficam mais difíceis |

## Consequências

- O build do Gradle passa a chamar o build do front-end (ou o CI faz os dois).
- A API REST é tratada como contrato da aplicação web: DTOs próprios e `ProblemDetail` (DM-11).
