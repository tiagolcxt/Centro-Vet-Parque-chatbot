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

## Implementação (Etapa S2, 2026-11-19)

**Versões fixadas.** Todas foram publicadas pelo menos duas semanas antes:

- React 19.3.1;
- Vite 8.3.5 e `@vitejs/plugin-react` 6.2.1;
- TypeScript 7.0.2;
- Vitest 5.1.0, `@testing-library/react` 16.3.3 e jsdom 30.1.3.

O jsdom pede Node 24.15 ou superior e avisa no Node 24.11 da máquina, mas os testes rodam.

**Navegação.** A navegação é por hash (`#/atendimento/ID`), sem biblioteca de rotas: menos dependências, e o servidor não precisa de rota de volta para o `index.html`. Os links de convite são `#/convite?token=…`. O token fica no fragmento, que o navegador não manda ao servidor.

**Build.**

- `npm run build` em `web/` gera `web/dist`.
- O `processResources` do Gradle copia `web/dist` para `static/`. Se o build da web não existir, a API funciona normalmente, só que a raiz fica sem página.
- `web/node_modules` e `web/dist` ficam fora do Git.
- O Gradle ainda **não** chama o `npm` sozinho: fica para quando houver CI.

**Desenvolvimento.** `npm run dev` sobe a aplicação na porta 5173, com proxy de `/app/api` para a 8080. Assim o navegador fica no mesmo domínio da API, e o cookie de sessão e o CSRF funcionam.

**Tempo real.**

- `GET /app/api/events` abre um `EventSource` por aba.
- Cada aviso traz só o tipo e o id; a tela busca os dados de novo pela API, que aplica a permissão.
- Os avisos são emitidos depois do commit: pelo turno do tutor, pelas ações sobre a conversa e pelas ações sobre a solicitação.

**Sessão encerrada.** Uma resposta 401 (servidor reiniciado, conta desativada) faz a aplicação voltar ao login.

**Telas:**

- Início;
- Atendimento: lista, conversa e painel do tutor;
- Solicitações: abas por setor visível, mais "Comigo" e "Sem dono";
- Clientes e pets: busca e ficha;
- Login e definição de senha.

No celular, a lista e o detalhe aparecem um de cada vez.

**Testes:**

- Vitest: rótulos de prioridade sempre com texto, e o cliente da API com CSRF e `ProblemDetail`.
- Caminho de ponta a ponta conferido no navegador embutido, em 2026-11-19, com uma instância separada (porta 8081, schema `assistant_e2e`):
  - definir senha e entrar;
  - assumir, com o texto ao tutor;
  - responder;
  - mensagem de emergência subindo para P1, com aviso em tempo real;
  - atribuir a solicitação;
  - largura de celular.
- O Playwright automatizado ficou para depois: exige baixar navegadores.
