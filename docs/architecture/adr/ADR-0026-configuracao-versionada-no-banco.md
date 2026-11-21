# ADR-0026 — Configuração da clínica no banco, com rascunho, publicação e versões

- **Status:** Aceito (escopo do MVP aprovado em 2026-11-18 com as DA-42 e DA-45). Substitui o ADR-0003 quanto ao **armazenamento**; o modelo tipado e a validade continuam
- **Decisão:** DA-42, DA-45; briefing 5.9 e 5.10
- **Data:** 2026-11-18

## Contexto

Hoje as informações da clínica ficam em um YAML tipado e validado na inicialização (ADR-0003, DA-39). O SaaS exige que a clínica edite sozinha, pela web, sem YAML, com rascunho, publicação, histórico e simulador (briefing 5). Cada clínica tem a própria configuração.

## Decisão

1. **Armazenamento.** A configuração vira uma linha `config_version` por versão e por clínica. O conteúdo é um documento JSON com a **mesma estrutura do schema v2** (DA-39), mais serviços com setor e regras de encaminhamento.
2. **Estados.**
   - DRAFT, PUBLISHED e ARCHIVED.
   - No máximo um rascunho e exatamente uma publicada por clínica.
   - Publicar arquiva a anterior na mesma transação.
3. **Validação.** O `KnowledgeSchema` atual valida o documento ao salvar e ao publicar.
4. **Leitura pelo assistente.** O assistente lê só a publicada, por meio do `ClinicKnowledge` de hoje, com cache por clínica invalidado ao publicar. A validade por seção continua calculada a cada consulta.
5. **Setores.** Ficam em tabela própria (`department`), não versionada, porque solicitações e funcionários apontam para eles.
6. **Migração do YAML.** O YAML vira formato de **importação**: a clínica fictícia é migrada por importação (RF-S19). O carregamento por arquivo sai do caminho principal.
7. **Simulador.** Monta um `ClinicKnowledge` a partir do rascunho e roda o mesmo pipeline do assistente, sem canal e sem gravar solicitações.

## Alternativas

| Opção | Vantagem | Desvantagem |
|---|---|---|
| (A) Documento por versão | Reaproveita o validador; publicação atômica; histórico e diferenças simples | Sem busca por SQL dentro do conteúdo (desnecessária no MVP) |
| (B) Uma linha por item, com versão por item | Consultas por item | Publicação e histórico complexos; reescreve o validador |

## Consequências

- O ADR-0003 fica **superado quanto ao armazenamento** (passa a "Aceito, armazenamento superado pelo ADR-0026"). Tipos, `null` como desconhecido e validação continuam.
- O risco R-15 (erro no YAML derruba a aplicação) deixa de existir no caminho principal: um erro de validação impede a publicação, não a inicialização.

## Implementação (Etapa S3, 2026-11-21)

- **Leitura pelo assistente:** o bean `ClinicKnowledge` é dinâmico (`ClinicKnowledge.dynamic`). Cada leitura vai à versão publicada da clínica atual (cache por clínica, limpo depois do commit da publicação). Quem usa a base não guarda valores lidos dela entre turnos.
- **Unicidade:** a coluna gerada `live_slot` vale `DRAFT` ou `PUBLISHED` e fica nula nas arquivadas; o índice único `(clinic_id, live_slot)` garante no máximo um rascunho e uma publicada.
- **Revisão do rascunho:** salvar exige a revisão lida (`revision`); outra revisão gera 409, para duas pessoas não se sobrescreverem.
- **Simulador:** `SimulatedKnowledge` troca a base pelo rascunho e `SimulatedTime` fixa o horário, só na thread do simulador. O turno roda numa transação marcada para desfazer: nada é gravado e os avisos em tempo real, que só saem depois de um commit, não saem.
- **Carga inicial:** `assistant.knowledge.location` deixou de ser obrigatório. Se estiver definido e a clínica de `assistant.knowledge.bootstrap-clinic` não tiver versão publicada, o arquivo é importado e publicado na inicialização. Arquivo inválido vai para o log; a aplicação sobe.
- **Setor do serviço:** `services.<id>.department` é opcional (arquivos antigos continuam válidos) e fica fora do prompt e dos valores autorizados.
- **Diferenças:** comparam os itens validados (preço em reais, horário legível). Serviço ou pergunta que entrou ou saiu inteiro aparece numa linha só.
