# Architecture Decision Records

Formato: contexto, decisão, alternativas consideradas, consequências. Os IDs de decisão (`DA-xx`, `DM-xx`) e de pendência (`P-xx`, `PT-xx`) estão em [registro-de-decisoes.md](../../plans/registro-de-decisoes.md).

**Status possíveis:**
- **Aceito**: decisão aprovada pelo responsável.
- **Aceito, com pendências**: o mecanismo foi aprovado, mas parâmetros ou conteúdos dependem de pendências listadas.
- **Mantido**: decisão da proposta original não alterada nem contestada na revisão.
- **Superado**: decisão substituída por outro ADR.
- **Proposto**: decisão redigida, aguardando aprovação do responsável.

| ADR | Título | Status | Decisões | Pendências ligadas |
|---|---|---|---|---|
| [0001](ADR-0001-arquitetura-modular-hexagonal.md) | Arquitetura modular com princípios hexagonais | Aceito | DM-01, DA-04, DA-08, DA-32 | — |
| [0002](ADR-0002-ia-chamada-unica-saida-estruturada.md) | IA: chamada única, saída estruturada, reconciliação, sem acesso à persistência | Aceito | DA-01, DA-02, DA-33, DA-28 | — |
| [0003](ADR-0003-conhecimento-institucional-yaml.md) | Conhecimento institucional em YAML tipado e validado | Aceito, com pendências; armazenamento superado pelo 0026 | DA-03 | P-11 |
| [0004](ADR-0004-janela-de-historico.md) | Janela de histórico: 20 mensagens + orçamento de tokens | Aceito | DA-10, DA-28 | — |
| [0005](ADR-0005-flyway.md) | Versionamento de schema com Flyway | Mantido | DM-04, DA-19 | — |
| [0006](ADR-0006-testcontainers.md) | Testcontainers com MySQL como estratégia única de teste de persistência | Aceito | DA-05 | — |
| [0007](ADR-0007-encaminhamento-humano-e-emergencia.md) | Encaminhamento humano e tratamento de possível emergência | Aceito, com pendências | DA-11, DA-25, DA-30, DA-31, DA-47 | P-01, P-02, P-12, P-14, P-20 |
| [0008](ADR-0008-validacao-de-respostas-e-fontes.md) | Validação de respostas e rastreamento de fontes institucionais | Aceito | DA-12, DA-26, DA-28, DA-29 | — |
| [0009](ADR-0009-ciclo-de-vida-da-conversa.md) | Ciclo de vida da conversa e expiração por inatividade | Aceito, com pendências | DA-13, DA-37 | P-05, P-06 |
| [0010](ADR-0010-proveniencia-dados-tutor-pet.md) | Proveniência das informações de tutor e pet | Aceito, com pendências | DA-14, DA-35, DA-36 | P-08 |
| [0011](ADR-0011-servicerequest-separado-da-conversa.md) | ServiceRequest separado da conversa; solicitação ≠ agendamento | Aceito, com pendências | DA-07, DA-34 | P-15 |
| [0012](ADR-0012-autenticacao.md) | Autenticação: API Key em testes controlados, autenticação individual para dados reais | Aceito, com pendências | DA-06, DA-49 | P-17, PT-07 |
| [0013](ADR-0013-repositorio-e-versionamento.md) | Repositório Git próprio e política de não versionamento | Aceito | DA-09, DA-20 | — |
| [0014](ADR-0014-barreira-dados-reais-lgpd.md) | Barreira entre desenvolvimento e uso com dados reais (LGPD) | Aceito, com pendências | DA-16, DA-17, DA-50 | P-03, P-04, P-16, P-17, P-18, P-19, P-20 |
| [0015](ADR-0015-historico-montado-pela-aplicacao.md) | Histórico montado pela aplicação, sem `ChatMemory` | Aceito | DA-18 | — |
| [0016](ADR-0016-modelo-parametros-timeout-retry.md) | Modelo, parâmetros, timeout e retry | Aceito | DA-28 | — |
| [0017](ADR-0017-orquestracao-do-turno-entre-modulos.md) | Orquestração do turno entre módulos | Aceito | DA-32 | — |
| [0018](ADR-0018-prioridade-clinica.md) | Prioridade clínica separada do estado operacional | Aceito, com pendências | DA-23 | P-02, P-20 |
| [0019](ADR-0019-sinais-de-alerta-aprovados.md) | Lista de sinais de alerta aprovada como verificação determinística | Aceito, com pendências | DA-27 | P-20 |
| [0020](ADR-0020-forma-do-produto-saas.md) | Forma do produto SaaS: setores como configuração e mapa de módulos | Aceito | DA-42, DA-52 | — |
| [0021](ADR-0021-multi-tenant.md) | Multi-tenant: banco e schema únicos com `clinic_id` | Aceito | DA-43 | — |
| [0022](ADR-0022-aplicacao-web-react.md) | Aplicação web em React servida pelo Spring | Aceito | DA-44 | — |
| [0023](ADR-0023-integracao-whatsapp.md) | Integração com o WhatsApp pela Cloud API oficial | Aceito | DA-46 | — |
| [0024](ADR-0024-identidade-perfis-permissoes.md) | Identidade, perfis editáveis e permissões | Aceito, com pendências | DA-49 | P-17 |
| [0025](ADR-0025-ativacao-do-piloto-em-niveis.md) | Ativação do assistente em três níveis por clínica | Aceito | DA-48 | — |
| [0026](ADR-0026-configuracao-versionada-no-banco.md) | Configuração da clínica no banco, com rascunho, publicação e versões | Aceito | DA-42, DA-45 | — |

Não há ADR reservado no momento. A etapa em que cada ADR é exigido está no [roadmap](../../plans/implementation-roadmap.md#adrs-por-etapa).
