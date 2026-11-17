# Visão geral da arquitetura — estado implementado

> Reflete o código ao fim da **Etapa 5 — Coleta de dados e solicitações de agendamento** (2026-11-17). Regras de encaminhamento, prioridade, coleta e agendamento: [business-rules.md](business-rules.md). A arquitetura-alvo completa está na [proposta técnica](../plans/etapa-0-analise-e-proposta.md). O que vai ao modelo está em [context-management.md](context-management.md).

## O que existe

| Camada | Implementado (E1 a E5) | Ainda não existe |
|---|---|---|
| HTTP | `ConversationController` (canal), `StaffConversationController` e `ServiceRequestController` (equipe), filtro `X-Api-Key`, `GlobalExceptionHandler` (ProblemDetail) | Autenticação individual e perfis (E7) |
| Aplicação | `OpenConversationUseCase`, `HandleIncomingMessageUseCase` (duas transações), `ConversationLifecycle`, `ContextAssembler` + `HistoryWindow`, `TurnExecutor` (1 nova tentativa), `TurnResultClassifier`, `ResponseValidator` (V1–V7) com `FactExtractor` e `ExpressionRules`; lacuna → `ServiceRequest(INFORMATION)`; `TurnDecider`, `ReplyComposer`, detectores de pedido de humano, alerta e conversa improdutiva; modo restrito; `StaffConversationService`, `StaffServiceRequestService`; **E5:** `CustomerDataCollector` (dados do tutor e do pet, proveniência, confirmação, pedido de agendamento), `CustomerDataNormalizer`, `AppointmentRequirements`, camadas 3 e 5 no `ContextAssembler`; portas `AssistantModel` e `TokenEstimator` | Métricas por turno (E6) |
| Domínio | `Conversation` (com prioridade e encaminhamento), `Message` (com metadados do turno), `PriorityPolicy`, `HandoffPolicy`, `PriorityChange`, `ServiceRequest` (com desfecho e detalhes de agendamento), `Tutor`, `Pet` (espécie opcional), `Provenance`, `PetMatcher`, `NameKey` e as portas de repositório | — |
| Persistência | Flyway `V1`, `V2` (unicidade da conversa ativa) e `V3` (prioridade, encaminhamento, metadados, desfecho, `priority_change`) e `V4` (`data_provenance`, espécie opcional, motivo e preferência), entidades JPA, adaptadores com lock pessimista, paginação e fila | — |
| Base institucional | `ClinicKnowledge` (domínio), `KnowledgeFileLoader` + `KnowledgeSchema` (YAML validado na inicialização) | Conteúdo real (P-11, Etapa 7) |
| IA | `SpringAiAssistantModel` (`ChatClient`, JSON Schema estrito `assistant_turn_v4` e `waiting_human_signals_v1`, sem tools), `PromptTemplates` (`system-message.st`, `waiting-human.st`, `clinic-context.st`, `tutor-context.st`, `operational-context.st`), `JTokkitTokenEstimator` | — |

## API (Etapa 2)

Todas as rotas exigem `X-Api-Key`. Somente dados fictícios.

| Método | Rota | Resposta |
|---|---|---|
| `POST` | `/api/conversations` `{"externalContactId": "..."}` | `201` conversa nova; `200` a ativa do contato |
| `POST` | `/api/conversations/{id}/messages` `{"text": "..."}` | `200` com `conversationId`, `newConversation`, `reply`, `status`, `intent`, `fallback`; `409` em mensagem simultânea (mensagem registrada, sem resposta); `422` texto vazio ou com mais de 2.000 caracteres; `404` conversa inexistente |
| `GET` | `/api/conversations/{id}/messages?page=0&size=50` | Histórico em ordem cronológica, paginado (`size` até 100) |

**Rotas da equipe (Etapa 4):** `GET /api/conversations?status=` (fila), `POST /api/conversations/{id}/close`, `POST /api/conversations/{id}/return-to-assistant`, `PATCH /api/conversations/{id}/priority`, `GET /api/conversations/{id}/priority-changes`, `GET /api/service-requests?status=`, `PATCH /api/service-requests/{id}`. Detalhes em [business-rules.md](business-rules.md#7-operações-da-equipe). A resposta do turno passou a trazer também `priority` e `handoffReason`. **Etapa 5:** as solicitações da equipe trazem também `tutorId`, `petId`, `reason` e `preferredPeriod`.

Sem `X-Api-Key` ou com chave errada: `401`, sem tocar no banco nem no modelo.

## Módulos e pacotes

```
com.clinicavet.assistant
├── AssistantApplication
├── conversation
│   ├── api                    ConversationController e DTOs (package-private)
│   ├── application            casos de uso, ciclo de vida, montagem do contexto, classificação,
│   │                          novas tentativas; portas AssistantModel e TokenEstimator; ConversationSettings
│   │                          E5: CustomerDataCollector, CustomerDataNormalizer, CustomerRecords, AppointmentRequirements
│   │   ├── validation         ResponseValidator, FactExtractor, Fact, ExpressionRules
│   │   └── rules              ExplicitHumanRequestDetector, AlertExpressionDetector, UnproductiveConversationDetector
│   ├── domain                 Conversation, ConversationStatus, CloseReason, ConversationExpirationPolicy,
│   │                          Message, MessageAuthor, Intent, ids, ConversationRepository, MessageRepository
│   └── infrastructure/persistence   ConversationEntity, MessageEntity, Spring Data, adaptadores JPA
├── ai                         SpringAiAssistantModel, PromptTemplates, JTokkitTokenEstimator (package-private)
├── clinicknowledge
│   ├── domain                 ClinicKnowledge, KnowledgeItem, ItemType
│   └── infrastructure         KnowledgeFileLoader, KnowledgeSchema, ClinicKnowledgeConfig
├── tutor
│   ├── domain                 Tutor, Pet, Species, ids, Provenance, ProvenanceStatus, DataField, DataSubject,
│   │                          NameKey, PetMatcher, TutorRepository, PetRepository, ProvenanceRepository
│   └── infrastructure/persistence
├── servicerequest
│   ├── api                    ServiceRequestController (equipe)
│   ├── application            StaffServiceRequestService
│   ├── domain                 ServiceRequest, RequestType, RequestStatus, RequestField, RequestOutcome, ServiceRequestRepository
│   └── infrastructure/persistence
└── shared
    ├── domain                 Channel, ContactIdentity, Timestamps, Texts (usados por mais de um módulo)
    ├── exception              exceções de domínio + GlobalExceptionHandler
    ├── security               ApiKeyFilter, ApiKeyConfig
    └── config                 ClockConfig (America/Sao_Paulo)
```

Regras verificáveis no código:
- `*.domain` não importa Spring, JPA nem Spring AI. Só `java.*` e outros pacotes `domain`/`shared.domain`/`shared.exception` de domínio.
- `conversation.application` não importa Spring AI: o adaptador `ai` implementa as portas dela.
- Entidades JPA, repositórios Spring Data, adaptadores, controller e DTOs são **package-private**. O resto da aplicação só enxerga portas e casos de uso.
- Nenhum código chama `Instant.now()`: o tempo vem do `Clock` (DM-06). Nos testes, `TestClock`.
- A chamada ao modelo acontece **fora de transação**, entre a transação que grava a mensagem do tutor e a que grava a resposta.

## Configuração obrigatória

| Chave | Onde | Sem ela |
|---|---|---|
| `ASSISTANT_API_KEY` (≥ 32 caracteres) | `.env` ou variável de ambiente | A aplicação não sobe |
| `OPENAI_API_KEY` | `.env` ou variável de ambiente | A aplicação sobe; toda chamada falha com 401 e o tutor recebe a resposta segura |
| `ASSISTANT_KNOWLEDGE_FILE` | `.env` ou variável de ambiente (opcional) | Usa `config/clinic-knowledge.example.yml` (tudo DESCONHECIDO). Arquivo inválido: a aplicação não sobe |
| `assistant.conversation.*` | `application.yml` | A aplicação não sobe. Prazos de expiração **provisórios** (P-05) e texto da resposta segura **provisório** (P-10) |
| `assistant.customer-data.*` | `application.yml` | A aplicação não sobe. Validade de CADASTRADA **provisória** (P-08) e texto do aviso de pedido registrado **provisório** (P-10) |

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
| Spring AI | 2.0.0-M4, `spring-ai-starter-model-openai` | Retry próprio desligado (`max-attempts: 0`); cliente HTTP efetivo: Reactor Netty; timeout por `spring.http.clients.read-timeout` (ADR-0016) |
| Modelo | `gpt-6-luna`, `reasoning_effort` `low`, até 2.000 tokens de saída | ADR-0016 |

## Testes

| Task | O que roda | Requisitos |
|---|---|---|
| `./gradlew test` | Unitários e integração (MySQL real, servidor HTTP real, modelo falso, servidor local no lugar da OpenAI) | Docker em execução |
| `./gradlew aiTest` | Testes `@Tag("ai")` com a OpenAI real e dados fictícios | Docker + `OPENAI_API_KEY`; sem a chave, aparecem como desabilitados |

## Execução

Ver [README](../../README.md).
