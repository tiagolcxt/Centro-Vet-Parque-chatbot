# Assistente Virtual de Atendimento Veterinário — backend

Primeira camada de atendimento de uma clínica veterinária: conversa com tutores, responde dúvidas institucionais com informação verificada, organiza solicitações e encaminha para a equipe humana. Não diagnostica, não prescreve e não confirma agendamentos.

> **Somente dados fictícios.** O uso com dados reais exige a Etapa 7 (barreira LGPD, [ADR-0014](docs/architecture/adr/ADR-0014-barreira-dados-reais-lgpd.md)).

## Estado atual

**Etapa 2 — Conversação concluída** (aguardando aceite). Há API de conversa autenticada por API Key, conversa com o modelo (`gpt-6-luna`) com histórico persistido, ciclo de vida da conversa e tratamento de falhas. O assistente **ainda não conhece nenhuma informação da clínica** (Etapa 3) e não encaminha para a equipe (Etapa 4). Roadmap em [docs/plans/implementation-roadmap.md](docs/plans/implementation-roadmap.md).

## Pré-requisitos

- JDK 25 (o Gradle encontra pela toolchain; o Java padrão do terminal pode ser outro, a partir do 17)
- **Docker em execução** (testes de integração com Testcontainers e banco de desenvolvimento)

## Configurar o ambiente de desenvolvimento

```bash
cp .env.example .env
```

No `.env`, defina:

| Variável | Para quê |
|---|---|
| `MYSQL_*` | Senhas do banco de desenvolvimento (`compose.yml`) |
| `ASSISTANT_API_KEY` | Chave exigida no cabeçalho `X-Api-Key`. Pelo menos 32 caracteres aleatórios (`openssl rand -hex 32`). Sem ela, a aplicação não sobe |
| `OPENAI_API_KEY` | Chave da OpenAI. Sem ela, a aplicação sobe, mas toda mensagem recebe a resposta segura padrão |

O `.env` não é versionado. As credenciais só existem nele (e, fora do desenvolvimento, em variáveis de ambiente).

## Comandos

```bash
./gradlew test       # unitários + integração (MySQL real via Testcontainers, modelo falso)
./gradlew aiTest     # testes com a OpenAI real e dados fictícios (precisa de OPENAI_API_KEY)
./gradlew bootRun    # sobe o MySQL do compose.yml e a aplicação na porta 8080
```

No Windows (PowerShell), use `.\gradlew.bat`.

## Testar a API

Com a aplicação no ar (`bootRun`) e a chave do `.env` em `$KEY`:

```bash
curl -s -X POST localhost:8080/api/conversations -H "X-Api-Key: $KEY" -H "Content-Type: application/json" -d '{"externalContactId":"ficticio-001"}'
```

```bash
curl -s -X POST localhost:8080/api/conversations/ID/messages -H "X-Api-Key: $KEY" -H "Content-Type: application/json" -d '{"text":"Oi! Meu gato se chama Mingau."}'
```

```bash
curl -s localhost:8080/api/conversations/ID/messages -H "X-Api-Key: $KEY"
```

Troque `ID` pelo `id` devolvido no primeiro comando. Contratos e códigos de erro: [architecture-overview.md](docs/architecture/architecture-overview.md#api-etapa-2).

## Documentação

| Assunto | Onde |
|---|---|
| Proposta técnica | [docs/plans/etapa-0-analise-e-proposta.md](docs/plans/etapa-0-analise-e-proposta.md) |
| Decisões e pendências | [docs/plans/registro-de-decisoes.md](docs/plans/registro-de-decisoes.md) |
| ADRs | [docs/architecture/adr/](docs/architecture/adr/README.md) |
| Arquitetura implementada | [docs/architecture/architecture-overview.md](docs/architecture/architecture-overview.md) |
| O que vai ao modelo | [docs/architecture/context-management.md](docs/architecture/context-management.md) |
| Modelo de domínio | [docs/architecture/domain-model.md](docs/architecture/domain-model.md) |
| Diretrizes de atendimento | [docs/product/diretrizes-de-atendimento.md](docs/product/diretrizes-de-atendimento.md) |
