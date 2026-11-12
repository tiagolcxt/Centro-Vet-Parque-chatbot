# Assistente Virtual de Atendimento Veterinário — backend

Primeira camada de atendimento de uma clínica veterinária: conversa com tutores, responde dúvidas institucionais com informação verificada, organiza solicitações e encaminha para a equipe humana. Não diagnostica, não prescreve e não confirma agendamentos.

> **Somente dados fictícios.** O uso com dados reais exige a Etapa 7 (barreira LGPD, [ADR-0014](docs/architecture/adr/ADR-0014-barreira-dados-reais-lgpd.md)).

## Estado atual

**Etapa 1 — Fundação concluída.** Há domínio, schema e persistência, mas ainda não há endpoints nem IA. Roadmap em [docs/plans/implementation-roadmap.md](docs/plans/implementation-roadmap.md).

## Pré-requisitos

- JDK 25 (o Gradle encontra pela toolchain; o Java padrão do terminal pode ser outro, a partir do 17)
- **Docker em execução** (testes de integração com Testcontainers e banco de desenvolvimento)

## Configurar o banco de desenvolvimento

```bash
cp .env.example .env   # e troque as senhas
```

O `.env` não é versionado. As credenciais só existem nele (e, fora do desenvolvimento, em variáveis de ambiente `SPRING_DATASOURCE_*`).

## Comandos

```bash
./gradlew test       # testes unitários + integração (MySQL real via Testcontainers)
./gradlew bootRun    # sobe o MySQL do compose.yml e a aplicação na porta 8080
```

No Windows (PowerShell), use `.\gradlew.bat`.

## Documentação

| Assunto | Onde |
|---|---|
| Proposta técnica | [docs/plans/etapa-0-analise-e-proposta.md](docs/plans/etapa-0-analise-e-proposta.md) |
| Decisões e pendências | [docs/plans/registro-de-decisoes.md](docs/plans/registro-de-decisoes.md) |
| ADRs | [docs/architecture/adr/](docs/architecture/adr/README.md) |
| Arquitetura implementada | [docs/architecture/architecture-overview.md](docs/architecture/architecture-overview.md) |
| Modelo de domínio | [docs/architecture/domain-model.md](docs/architecture/domain-model.md) |
| Diretrizes de atendimento | [docs/product/diretrizes-de-atendimento.md](docs/product/diretrizes-de-atendimento.md) |
