# ADR-0006 — Testcontainers com MySQL como estratégia única de teste de persistência

- **Status:** Aceito
- **Data:** 2026-11-11
- **Decisão:** DA-05

## Contexto

Os testes de integração precisam exercitar as mesmas migrations Flyway, tipos (`BINARY(16)`, `DATETIME(6)`) e índices do MySQL. A proposta original mencionava, ao mesmo tempo, repositórios "em memória" e Testcontainers, ou seja, duas estratégias concorrentes (revisão técnica, 11.5).

## Decisão

1. **Testes que envolvem persistência usam Testcontainers com MySQL real**, na mesma versão major usada no `compose.yml`. Isso vale para os testes dos adaptadores de persistência e para os testes de integração de fluxo (caso de uso + banco + `AssistantModel` falso).
2. **Não serão criadas implementações de repositório em memória.** As regras de domínio são testadas por testes unitários puros, sem repositório.
3. **Docker é pré-requisito** da Etapa 1 em diante: na máquina de desenvolvimento e em qualquer CI.
4. O container é compartilhado entre classes de teste da mesma execução, para reduzir o tempo.

## Implementação (Etapa 1)

- Testcontainers 2.0.4 (`testcontainers-mysql`, classe `org.testcontainers.mysql.MySQLContainer`), conectado por `@ServiceConnection`.
- Um único `@TestConfiguration` (`TestcontainersConfiguration`) e uma anotação `@IntegrationTest`. Como todos os testes de integração usam a mesma configuração, o Spring reaproveita o contexto e o contêiner.
- Cada teste usa um contato fictício próprio (`Fixtures.newContact()`), então não há dependência de limpeza entre testes.
- Testes com IA real (`@Tag("ai")`) são excluídos da task `test`.

## Alternativas consideradas

- **H2 em modo MySQL:** rápido, mas não reproduz `BINARY(16)`, colunas JSON e diferenças de DDL; geraria falsos verdes.
- **Banco do `compose.yml` compartilhado com o desenvolvimento:** estado compartilhado e testes frágeis.
- **Repositórios em memória para testes de aplicação + Testcontainers para adaptadores:** duas implementações a manter; rejeitada.

## Consequências

- Os testes de integração são mais lentos (inicialização do container) e falham se o Docker não estiver rodando. O critério "testes passam sem banco instalado" significa "com Docker em execução".
- Três categorias de teste:
  - **unitários** (sem Spring, sem banco);
  - **integração** (Testcontainers, sem OpenAI);
  - **IA real** (`@Tag("ai")`, só com `OPENAI_API_KEY`, sem dados reais).
