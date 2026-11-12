# Checklist para início da implementação

> Versão 2026-11-11. Condições para **iniciar a Etapa 1**. Os pré-requisitos de cada etapa seguinte estão no [roadmap](implementation-roadmap.md#pendências-por-etapa).

## 1. Aprovação da documentação

- [ ] Proposta técnica v2 ([etapa-0-analise-e-proposta.md](etapa-0-analise-e-proposta.md)) aprovada.
- [ ] Roadmap v2 aprovado, incluindo a divisão em Etapas 1–7 e a Etapa 7 como barreira obrigatória.
- [ ] ADR-0001 a ADR-0015, ADR-0018 e ADR-0019 aceitos (ou ajustados) pelo responsável.
- [ ] Revisão técnica externa concluída e as observações registradas no [registro de decisões](registro-de-decisoes.md).

## 2. Decisões que bloqueiam a Etapa 1

- [x] **PT-03**: colunas próprias (DA-19).
- [x] **PT-04**: repositório na subpasta `assistant-backend/`; material da clínica fora (DA-20).

## 3. Ambiente

- [ ] JDK 25 instalado e reconhecido pelo Gradle (toolchain).
- [ ] **Docker instalado e em execução** (Testcontainers e `compose.yml`).
- [ ] Repositório Git próprio na subpasta `assistant-backend/`, **separado** do repositório existente na pasta do usuário (ADR-0013, DA-20). É criado na primeira tarefa da Etapa 1.
- [ ] `.gitignore` com as regras do ADR-0013 criado **antes** do primeiro commit.
- [ ] Questionários preenchidos (quando existirem) e qualquer material da clínica armazenados fora das áreas versionadas.
- [ ] A Etapa 1 **não** precisa de `OPENAI_API_KEY`. Quando existir, a chave fica só em variável de ambiente, nunca em arquivo versionado.

## 4. Compromissos de trabalho

- [ ] Apenas **dados fictícios** em fixtures, testes, bancos locais e demonstrações (DA-17).
- [ ] Nenhuma pendência (`P-xx`/`PT-xx`) será resolvida por suposição durante a implementação. Valores provisórios são marcados como tais na configuração e no registro.
- [ ] Cada etapa termina com testes executados, resultados informados, documentação e ADRs da etapa atualizados, e aceite antes de iniciar a próxima.

## 5. Pendências que não bloqueiam a Etapa 1, mas devem ser encaminhadas desde já

Elas têm prazo longo, porque dependem da clínica ou de orientação jurídica.

| Encaminhar para | Pendências |
|---|---|
| Clínica | P-01 (emergência, **prioritária**), P-02, P-11, P-12, P-13, P-14 (parte do questionário já enviado cobre P-12, P-13 e P-14) |
| Equipe veterinária | P-20 e itens V1–V7 de [aprovacoes-equipe-veterinaria.md](../product/aprovacoes-equipe-veterinaria.md) (**V5, protocolo de emergência, é prioritário**) |
| Clínica (diretrizes) | P-19 e itens C1–C7 de [aprovacoes-equipe-veterinaria.md](../product/aprovacoes-equipe-veterinaria.md) |
| Clínica + orientação jurídica | P-03, P-04, P-16 |
| Responsável pelo projeto (antes da Etapa 2) | P-09 (PT-01 e PT-05 já decididas) |

---

## Anexo — Barreira para dados reais (resumo)

Nenhum dado real antes de **todos** os itens G1–G12 do [ADR-0014](../architecture/adr/ADR-0014-barreira-dados-reais-lgpd.md) estarem satisfeitos e da Etapa 7 aceita:

- [ ] G1 Aviso de transparência sobre IA
- [ ] G2 Finalidade e base legal
- [ ] G3 Provedores externos de IA e transferência internacional
- [ ] G4 Retenção e exclusão implementadas
- [ ] G5 Processo de acesso e exclusão do titular
- [ ] G6 Autenticação individual, autorização e auditoria
- [ ] G7 Logs, backups e acesso ao banco
- [ ] G8 Política de emergência aprovada
- [ ] G9 Conteúdo institucional real revisado pela clínica
- [ ] G10 Prazo de atendimento humano por nível e comportamento sem funcionário disponível
- [ ] G11 Critérios clínicos da hierarquia de prioridades aprovados pela equipe veterinária
- [ ] G12 Diretrizes de Atendimento e Conduta aprovadas pela clínica
