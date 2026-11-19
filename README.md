# Assistente Virtual de Atendimento Veterinário — backend

Primeira camada de atendimento de uma clínica veterinária: conversa com tutores, responde dúvidas institucionais com informação verificada, organiza solicitações e encaminha para a equipe humana. Não diagnostica, não prescreve e não confirma agendamentos.

> **Somente dados fictícios.** O uso com dados reais exige a Etapa 7 (barreira LGPD, [ADR-0014](docs/architecture/adr/ADR-0014-barreira-dados-reais-lgpd.md)).

## Estado atual

**Etapa 5 — Coleta de dados e solicitações de agendamento concluída e aceita**. Há API de conversa autenticada por API Key, conversa com o modelo (`gpt-6-luna`) com histórico persistido e ciclo de vida, base institucional em YAML validada na inicialização e um validador que barra valores sem fonte, confirmação de agendamento, promessas sem processo e doses. Dúvidas que a base não responde viram pedidos de informação para a equipe. Pedidos de atendimento humano, possíveis emergências, sintomas e reclamações vão para a fila da equipe, com prioridade clínica (P1 a P6) que só a equipe rebaixa. A equipe tem rotas para a fila, para devolver ou encerrar conversas e para resolver solicitações. O assistente coleta tutor e pet com proveniência por campo (mencionada, confirmada, cadastrada, a reconfirmar) e organiza pedidos de agendamento que só ficam registrados com pet, motivo e nome do tutor, sem nunca confirmar dia nem horário. Textos de emergência e prazos ainda são provisórios (P-01, P-02). As regras de coleta e agendamento usam valores provisórios (P-06, P-07, P-08, P-13). Roadmap em [docs/plans/implementation-roadmap.md](docs/plans/implementation-roadmap.md).

**Próximo passo: SaaS multi-clínica (planejamento aprovado em 2026-11-18, DA-42 a DA-51).** O assistente vira parte de uma plataforma com aplicação web, filas por setor, configuração sem YAML e WhatsApp oficial. Ver [Product Blueprint](docs/product/saas/product-blueprint.md) e [roadmap do SaaS](docs/plans/saas-roadmap.md).

**Etapa S1 — Fundação multi-tenant implementada, aguardando aceite (2026-11-18).**

- Todos os dados pertencem a uma clínica (`clinic_id`), e uma clínica não enxerga a outra (ADR-0021).
- Funcionários têm login individual, com perfis editáveis e permissões (ADR-0024).
- Cada clínica tem setores (Recepção, Clínica, Pet shop) e auditoria.
- As fronteiras entre módulos são verificadas pelo Spring Modulith (DA-52).

Ainda não há aplicação web: a equipe usa a API `/app/api` (S2).

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
| `ASSISTANT_KNOWLEDGE_FILE` | Base institucional. Opcional: sem ela, vale o modelo todo `null` e o assistente não sabe nada da clínica. Para desenvolver, `file:config/clinic-knowledge.ficticia.yml` (clínica inventada) |

O `.env` não é versionado. As credenciais só existem nele (e, fora do desenvolvimento, em variáveis de ambiente).

## Comandos

```bash
./gradlew test       # unitários + integração (MySQL real via Testcontainers, modelo falso)
./gradlew aiTest     # testes com a OpenAI real e dados fictícios (precisa de OPENAI_API_KEY)
./gradlew bootRun    # sobe o MySQL do compose.yml e a aplicação na porta 8080
```

No Windows (PowerShell), use `.\gradlew.bat`.

## Conversar pelo terminal (Windows)

Com a aplicação no ar (`.\gradlew.bat bootRun`), em outro terminal na pasta `assistant-backend`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\chat.ps1
```

O script lê a `ASSISTANT_API_KEY` do `.env`, envia em UTF-8 (acentos funcionam) e mostra, depois de cada resposta, o status, a prioridade, a intenção e o motivo do encaminhamento. Comandos: `/pedidos` (solicitações da conversa), `/historico`, `/fila` (fila da equipe), `/devolver` e `/encerrar` (simulam a equipe), `/nova` (outro contato) e `/sair`. Para voltar como o mesmo cliente fictício em outra execução, use `-Contato ficticio-ana`.

**Somente dados fictícios:** as mensagens vão para a OpenAI, e o banco de desenvolvimento não tem as proteções da Etapa 7. Para o assistente conhecer a clínica fictícia, o `.env` precisa de `ASSISTANT_KNOWLEDGE_FILE=file:config/clinic-knowledge.ficticia.yml`.

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

A chave do `.env` pertence à clínica fictícia criada pela V5 (`ASSISTANT_API_KEY_CLINIC` troca a clínica).

## Login da equipe (Etapa S1)

As rotas da equipe ficam em `/app/api` e usam sessão por cookie, com CSRF. A chave de serviço não abre essas rotas.

**1. Criar o primeiro administrador.** Só funciona numa clínica ainda sem contas. A resposta traz o `inviteToken`:

```bash
curl -s -X POST localhost:8080/api/clinic/first-admin -H "X-Api-Key: $KEY" -H "Content-Type: application/json" -d '{"displayName":"Admin Fictício","email":"admin@ficticio.test"}'
```

**2. Definir a senha e entrar.** O `curl` guarda os cookies em `cookies.txt` e manda o token CSRF de volta no cabeçalho:

```bash
curl -s -c cookies.txt localhost:8080/app/api/auth/csrf
XSRF=$(grep XSRF-TOKEN cookies.txt | awk '{print $7}')
curl -s -b cookies.txt -c cookies.txt -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" -X POST localhost:8080/app/api/auth/accept-invite -d '{"token":"TOKEN","password":"uma-senha-ficticia"}'
curl -s -b cookies.txt -c cookies.txt -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" -X POST localhost:8080/app/api/auth/login -d '{"email":"admin@ficticio.test","password":"uma-senha-ficticia"}'
```

**3. Usar a sessão.** Por exemplo, para ver perfis, setores e o próprio cadastro:

```bash
curl -s -b cookies.txt localhost:8080/app/api/roles
curl -s -b cookies.txt localhost:8080/app/api/departments
curl -s -b cookies.txt localhost:8080/app/api/me
```

As demais rotas estão no [ADR-0024](docs/architecture/adr/ADR-0024-identidade-perfis-permissoes.md#implementação-etapa-s1-2026-11-18): convidar funcionários (`POST /app/api/employees`) e criar perfis (`POST /app/api/roles`).

## Documentação

| Assunto | Onde |
|---|---|
| SaaS: Blueprint, PRD, fluxos e telas | [docs/product/saas/](docs/product/saas/product-blueprint.md) — [PRD](docs/product/saas/prd.md), [fluxos](docs/product/saas/fluxos-operacionais.md), [UX](docs/product/saas/ux-navegacao.md) |
| SaaS: arquitetura e dados | [módulos](docs/architecture/saas-modulos-e-arquitetura.md), [modelo de dados](docs/architecture/saas-modelo-de-dados.md) |
| SaaS: roadmap, backlog e validação | [roadmap](docs/plans/saas-roadmap.md), [backlog](docs/plans/saas-backlog.md), [plano de validação](docs/plans/saas-plano-de-validacao.md) |
| Proposta técnica | [docs/plans/etapa-0-analise-e-proposta.md](docs/plans/etapa-0-analise-e-proposta.md) |
| Decisões e pendências | [docs/plans/registro-de-decisoes.md](docs/plans/registro-de-decisoes.md) |
| ADRs | [docs/architecture/adr/](docs/architecture/adr/README.md) |
| Arquitetura implementada | [docs/architecture/architecture-overview.md](docs/architecture/architecture-overview.md) |
| O que vai ao modelo | [docs/architecture/context-management.md](docs/architecture/context-management.md) |
| Regras de encaminhamento e prioridade | [docs/architecture/business-rules.md](docs/architecture/business-rules.md) |
| Modelo de domínio | [docs/architecture/domain-model.md](docs/architecture/domain-model.md) |
| Diretrizes de atendimento | [docs/product/diretrizes-de-atendimento.md](docs/product/diretrizes-de-atendimento.md) |
