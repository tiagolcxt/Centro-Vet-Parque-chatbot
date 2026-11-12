# Visão geral da arquitetura — estado implementado

> Reflete o código ao fim da **Etapa 1 — Fundação** (2026-11-11). A arquitetura-alvo completa está na [proposta técnica](../plans/etapa-0-analise-e-proposta.md).

## O que existe

| Camada | Implementado na Etapa 1 | Ainda não existe |
|---|---|---|
| HTTP | `GlobalExceptionHandler` (ProblemDetail) | Endpoints (Etapa 2) |
| Aplicação | — | Casos de uso, porta `AssistantModel` (Etapa 2) |
| Domínio | `Conversation`, `Message`, `Intent`, `ConversationExpirationPolicy`, `Tutor`, `Pet`, `ServiceRequest` e as portas de repositório | Prioridade clínica, encaminhamento (E4), proveniência (E5) |
| Persistência | Flyway `V1`, entidades JPA e adaptadores de cada porta | Colunas de E4 e E5 |
| IA | Só o BOM do Spring AI 2.0.0-M4, sem uso | Adaptador `ChatClient` (Etapa 2) |

## Módulos e pacotes

```
com.clinicavet.assistant
├── AssistantApplication
├── conversation
│   ├── domain                 Conversation, ConversationStatus, CloseReason, ConversationExpirationPolicy,
│   │                          Message, MessageAuthor, Intent, ids, ConversationRepository, MessageRepository
│   └── infrastructure/persistence   ConversationEntity, MessageEntity, Spring Data, adaptadores JPA
├── tutor
│   ├── domain                 Tutor, Pet, Species, ids, TutorRepository, PetRepository
│   └── infrastructure/persistence
├── servicerequest
│   ├── domain                 ServiceRequest, RequestType, RequestStatus, RequestField, ServiceRequestRepository
│   └── infrastructure/persistence
└── shared
    ├── domain                 Channel, ContactIdentity, Timestamps, Texts (usados por mais de um módulo)
    ├── exception              exceções de domínio + GlobalExceptionHandler
    └── config                 ClockConfig (America/Sao_Paulo)
```

Regras verificáveis no código:
- `*.domain` não importa Spring, JPA nem Spring AI. Só `java.*` e outros pacotes `domain`/`shared.domain`/`shared.exception` de domínio.
- Entidades JPA, repositórios Spring Data e adaptadores são **package-private** na infraestrutura. O resto da aplicação só enxerga as portas do domínio.
- Nenhum código chama `Instant.now()`: o tempo vem do `Clock` (DM-06).

## Tecnologia efetivamente usada

| Item | Versão / artefato | Observação |
|---|---|---|
| Java | 25 (toolchain Gradle) | |
| Gradle | 9.5.1 (wrapper; checksum do jar conferido com o publicado pela Gradle) | |
| Spring Boot | 4.0.5 | `spring-boot-starter-webmvc` (o `-web` está depreciado no Boot 4) |
| Flyway | 11.14.1 via `spring-boot-starter-flyway` + `flyway-mysql` | No Boot 4 a autoconfiguração do Flyway exige o starter |
| Hibernate | 7.2.7 | `ddl-auto=validate` |
| MySQL | 9.6 (compose e Testcontainers), Connector/J 9.6.0 | |
| Testcontainers | 2.0.4 (`testcontainers-mysql`, pacote `org.testcontainers.mysql`) | |
| Spring AI | BOM 2.0.0-M4, sem starter | Evita exigir `OPENAI_API_KEY` antes da Etapa 2 |

## Execução

Ver [README](../../README.md). Resumo: `./gradlew test` (Docker em execução) e `./gradlew bootRun`, que sobe o MySQL do `compose.yml` com as credenciais do `.env`.
