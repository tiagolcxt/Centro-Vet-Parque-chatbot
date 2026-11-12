# ADR-0005 — Versionamento de schema com Flyway

- **Status:** Mantido (proposta original; não contestado na revisão)
- **Data:** 2026-11-11
- **Decisão:** DM-04

## Contexto

O briefing pede para não usar `ddl-auto=update` como estratégia definitiva e para avaliar Flyway ou equivalente. A referência usa `ddl-auto=update`.

## Decisão

1. O schema é versionado com **Flyway** (`db/migration/V{n}__descricao.sql`), escrito para MySQL.
2. Hibernate em `ddl-auto=validate`: a aplicação não sobe se as entidades divergirem do schema.
3. Migrations **nunca são editadas** depois de aplicadas em qualquer ambiente compartilhado; mudanças viram nova versão.
4. O schema evolui **por etapa**: cada etapa adiciona só as tabelas e colunas que usa. Por exemplo, as colunas de proveniência de tutor/pet entram na Etapa 5 (ADR-0010).
5. Identificadores UUID em `BINARY(16)`; timestamps em UTC (`DATETIME(6)`); conversão para `America/Sao_Paulo` na apresentação e no contexto do modelo.

## Alternativas consideradas

- **Liquibase:** equivalente; Flyway é mais simples para SQL puro.
- **`ddl-auto=update`:** vetado pelo briefing para evolução definitiva.

## Consequências

- **Verificado na Etapa 1:** no Spring Boot 4.0.5 a autoconfiguração do Flyway exige `spring-boot-starter-flyway`; com ele, `flyway-mysql` (Flyway 11.14.1, gerenciado pelo Boot). `V1__initial_schema.sql` aplicada e validada pelo Hibernate (`ddl-auto=validate`) em MySQL 9.6, tanto no Testcontainers quanto no `compose.yml`.
- As entidades declaram `columnDefinition` explícito (`BINARY(16)`, `DATETIME(6)`, `VARCHAR(n)` para enums, `TEXT`), para que a validação do Hibernate compare com os tipos exatos da migration. Isso previne a divergência conhecida do Hibernate 6+ com MySQL, que espera colunas `enum` nativas para `@Enumerated`. A variante sem `columnDefinition` não foi testada.
- Detalhes variáveis da solicitação em **colunas próprias anuláveis** de `service_request`, sem coluna JSON (DA-19, decidida em 2026-11-11). Um novo tipo de solicitação com detalhes próprios exige nova migration.
