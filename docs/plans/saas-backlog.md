# Backlog do MVP SaaS

> Versão 1 — 2026-11-18. Ordem: [roadmap SaaS](saas-roadmap.md). Requisitos: [PRD](../product/saas/prd.md).
> **Tamanho:** P (até 1 dia), M (2 a 3 dias), G (4 dias ou mais: quebrar antes de começar). É uma estimativa relativa, não um prazo.
> Cada item só é "feito" com testes, documentação e aceite (regras do roadmap). Itens marcados ⚑ dependem de algo fora do código. ✅ = implementado; ◐ = parcial (o resto está descrito no item).

## S0 — Descoberta

| # | Item | Tamanho | Depende de | Requisitos |
|---|---|---|---|---|
| S0-1 ⚑ | Aplicar o questionário de casos recorrentes e consolidar as respostas | M | Clínica | P-06, P-12, P-13 |
| S0-2 ⚑ | Entrevista ou observação da recepção (como chegam, quem responde, onde se perde) | M | Clínica | — |
| S0-3 ⚑ | Levantar setores, pessoas e quem vê o quê | P | Clínica | P-17 |
| S0-4 ⚑ | Levantar horários, serviços, preços divulgáveis e emergência | P | Clínica | P-01, P-11 |
| S0-5 ⚑ | Protocolos e sinais de alerta aprovados pelo veterinário responsável | M | Veterinário | P-20 |
| S0-6 ⚑ | Linha de base: volume, tempo de resposta, pedidos perdidos (amostra manual de uma semana) | M | Clínica | Plano de validação |
| S0-7 | Mapa operacional escrito e aprovado | P | S0-1 a S0-6 | — |

## S1 — Fundação multi-tenant

| # | Item | Tamanho | Depende de | Requisitos |
|---|---|---|---|---|
| S1-1 ✅ | Decidir a PT-16 (Spring Modulith × ArchUnit) com uma prova de conceito no Boot 4.0.5 | P | — | RNF-S10 |
| S1-2 ✅ | Confirmar que o `@TenantId` cobre as consultas do projeto (ADR-0021, consequências) | P | — | RNF-S01 |
| S1-3 ✅ | V5: tabela `clinic`, clínica fictícia, `clinic_id` em todas as tabelas, chaves únicas por clínica | M | S1-2 | RNF-S01 |
| S1-4 ✅ | `TenantContext` e resolver; `@TenantId` nas entidades; consultas nativas com `clinic_id`; teste que lista as consultas nativas | M | S1-3 | RNF-S01 |
| S1-5 ✅ | Suporte de teste com duas clínicas e adaptação dos testes atuais | M | S1-4 | RNF-S01, RNF-S09 |
| S1-6 ✅ | `platform`: `user_account`, sessão Spring Security, login e logout, CSRF, hash de senha, bloqueio | G → quebrar em sessão / login / bloqueio | S1-3 | RF-S02, RNF-S02 |
| S1-7 ◐ | Convite e definição de senha: link de uso único devolvido ao administrador (feito); **envio por e-mail** (porta `Mailer`) adiado | M | S1-6 | RF-S02, RF-S03 |
| S1-8 ✅ | Catálogo de permissões, `role`, os seis perfis-modelo e a verificação por ação | M | S1-6 | RF-S04 |
| S1-9 ✅ | `team`: `employee`, setores do funcionário, cargo e registro; desativação | M | S1-8 | RF-S03, RF-S05 |
| S1-10 ✅ | `department` com os setores padrão; Recepção como padrão | P | S1-3 | RF-S12 |
| S1-11 ✅ | `audit_event` e `AuditLog`, usados por login, perfis e equipe | M | S1-6 | RF-S07 |
| S1-12 ◐ | API Key restrita a testes e integrações (ADR-0012 revisto): não abre `/app/api` (feito); as rotas da equipe que ainda estão em `/api` mudam na S2 | P | S1-6 | — |
| S1-13 ✅ | Teste de arquitetura das fronteiras e do `@TenantId` | P | S1-1, S1-4 | RNF-S10 |
| S1-14 ⚑ | Pedido de verificação do negócio e acesso como *Tech Provider* na Meta | P (+ espera) | Dados da empresa | DA-46 |

## S2 — Caixa de entrada e solicitações por setor

| # | Item | Tamanho | Depende de | Requisitos |
|---|---|---|---|---|
| S2-1 ✅ | Esqueleto da aplicação web: Vite, React e TypeScript, build no Gradle, servida pelo Spring, login | M | S1 | DA-44 |
| S2-2 ✅ | Layout responsivo, menu por permissão, tela Início | M | S2-1 | RF-S70, RNF-S03 |
| S2-3 ✅ | Migração: `ASSIGNED`, responsável e setor na conversa; autor `STAFF` na mensagem | M | S1 | Modelo de dados §4 |
| S2-4 ✅ | Casos de uso: assumir (condicional), devolver, transferir, encerrar; texto "assumido por" no próximo turno | M | S2-3 | RF-S22, S24, S25, S27 |
| S2-5 ✅ | Resposta manual pelo canal de teste e nota interna da conversa | M | S2-3 | RF-S23, RF-S26 |
| S2-6 ✅ | Reavaliação de prioridade em conversa assumida, sem resposta da IA | M | S2-4 | RF-S28, RF-S63 |
| S2-7 ✅ | Migração: setor, responsável, eventos e notas da solicitação | M | S1 | Modelo de dados §3 |
| S2-8 ✅ | Casos de uso: atribuir, transferir, mudar estado e encerrar, com eventos; escopo de setor | M | S2-7 | RF-S30 a RF-S36 |
| S2-9 ✅ | SSE por usuário, com eventos depois do commit | M | S2-4, S2-8 | RF-S29, RNF-S04 |
| S2-10 ✅ | Tela Atendimento (lista, conversa, painel do tutor e do pet) | G → quebrar em lista / conversa / painel | S2-2, S2-4 | RF-S20, RF-S21 |
| S2-11 ✅ | Tela Solicitações com abas por setor, filtros e detalhe | G → quebrar em lista / detalhe | S2-2, S2-8 | RF-S30 a RF-S35 |
| S2-12 ◐ | Clientes e pets: busca e ficha (feito); edição (proveniência CADASTRADA) adiada para a S3: a proveniência exige a conversa de origem | M | S2-2 | RF-S40 a RF-S42 |
| S2-13 ◐ | Restrição por escopo de setor (feito: o tosador só vê o pet shop); permissão `clinical-notes:view` para notas clínicas fica para quando houver nota clínica | P | S2-8, S2-12 | RF-S44 |
| S2-14 ✅ | Disponibilidade (disponível ou ausente) e retorno das atribuições ao desativar | P | S1-9 | RF-S06, RN-S07 |
| S2-15 ◐ | Ponta a ponta: conferido no navegador embutido; Playwright automatizado adiado (download de navegadores) | M | S2-10 | RNF-S09 |

## S3 — Configuração pela web

| # | Item | Tamanho | Depende de | Requisitos |
|---|---|---|---|---|
| S3-1 ✅ | `config_version` (rascunho, publicada, arquivada), validação pelo `KnowledgeSchema`, publicação atômica | M | S1 | RF-S15, ADR-0026 |
| S3-2 ✅ | `ClinicKnowledge` lido da versão publicada, com cache por clínica | M | S3-1 | RF-S60 |
| S3-3 ✅ | Importação do YAML como rascunho; clínica fictícia migrada | P | S3-1 | RF-S19 |
| S3-4 ✅ | Serviços com setor e regras de encaminhamento no documento | M | S3-1 | RF-S13, RF-S14 |
| S3-5 ✅ | Formulários: dados, horários (intervalos e feriados), emergência, pagamento, políticas, perguntas frequentes, validade | G → uma tela por seção | S3-1 | RF-S11 |
| S3-6 ✅ | Diferenças legíveis, histórico e restauração | M | S3-1 | RF-S16 |
| S3-7 ✅ | Simulador sobre o rascunho, com horário simulado | M | S3-2 | RF-S17 |
| S3-8 ✅ | Assistente de configuração inicial em 7 passos | M | S3-5 | RF-S10 |
| S3-9 ✅ | Avisos de validade e de rascunho não publicado na tela Início | P | S3-1 | RF-S18, RF-S72 |
| S3-10 | Tela Equipe e tela de setores | M | S1-9 | RF-S03, RF-S12 |
| S3-11 | Tela de auditoria | P | S1-11 | RF-S08 |
| S3-12 ⚑ | Teste de usabilidade com alguém da clínica (configuração fictícia) | P | S3-8 | Plano de validação |
| S3-13 ✅ | Modelos por tipo de negócio (clínica pequena, clínica com pet shop, só pet shop ou banho e tosa, hospital 24 h): setores, perfis, regras, perguntas sugeridas e tom | M | S3-1, S3-4 | DA-53 |
| S3-14 ✅ | Importar serviços e preços de planilha (CSV/Excel) | M | S3-4 | DA-53 |
| S3-15 ✅ | Analisar conversas exportadas do WhatsApp e sugerir perguntas frequentes para aprovação, sem gravar as conversas | G → quebrar em leitura do arquivo / agrupamento / tela de aprovação | S3-5 | DA-53 |
| S3-16 ✅ | Respostas prontas da equipe na caixa de entrada | M | S2-10 | DA-53 |
| S3-17 ✅ | Indicadores básicos na tela Início (recebidas, respondidas, 1ª resposta, pedidos) | M | S2 | DA-53, RF-S71 |
| S3-18 ✅ | "Copiar resumo do pedido" e exportação de pedidos em planilha | P | S2-11 | DA-53 |

## S4 — WhatsApp

| # | Item | Tamanho | Depende de | Requisitos |
|---|---|---|---|---|
| S4-1 ✅ | `channel_account` com o token cifrado; tela Canal | M | S1 | DA-46 |
| S4-2 ✅ | Webhook: verificação (GET), assinatura, `inbound_message` com deduplicação, resposta 200 | M | S4-1 | RF-S50, RF-S51 |
| S4-3 ✅ | Processamento fora da requisição, em série por contato | M | S4-2 | DA-21 |
| S4-4 ✅ | *Outbox*: gravação no turno, despachante com nova tentativa, status pelo webhook | G → quebrar em gravação / despacho / status | S4-1 | RF-S52, RF-S55 |
| S4-5 ✅ | Janela de 24 horas na tela e no envio | P | S4-4 | RF-S53 |
| S4-6 ✅ | Mídia sem texto | P | S4-2 | RF-S54 |
| S4-7 ✅ | Testes de contrato com cargas de exemplo da documentação da Meta | M | S4-2, S4-4 | RNF-S09 |
| S4-8 ⚑ | Número de teste conectado; ensaio com dados fictícios | P | Acesso da Meta | — |
| S4-9 (parcial) | Coexistência: mesmo número no app WhatsApp Business e na API; mensagens respondidas pelo celular aparecem na conversa. Eco pronto; ligar o número exige Embedded Signup (ADR-0023) | M | S4-2 | DA-53 |
| S4-10 ✅ | Confirmar na documentação da Meta as regras e limitações da coexistência | P | — | DA-53 |

## S5 — Assistente no SaaS e validação

| # | Item | Tamanho | Depende de | Requisitos |
|---|---|---|---|---|
| S5-1 ✅ | Setor da solicitação pelas regras publicadas | M | S3-4 | RF-S37, RF-S61 |
| S5-2 ✅ | Textos de "encaminhado para o setor" e "assumido por"; revisão dos textos sem "avisei" | P | S2-4 | RF-S62, RN-S01 |
| S5-3 ✅ | Níveis de ativação 1, 2 e 3 | M | S5-1 | RF-S64, ADR-0025 |
| S5-4 | Bateria de IA com a configuração do piloto (fictícia) e os casos do questionário, nos três níveis | G | S0-1, S5-3 | Plano de validação |
| S5-5 (parcial: operação da equipe; custo de IA pendente) | Indicadores completos na tela Início (os básicos entram na S3-17) | M | S3-17, S4 | RF-S71 |
| S5-6 | Itens da antiga Etapa 6: métricas por turno, cenários de segurança, relatório | M | S5-4 | — |
| S5-7 ✅ | Áudio: transcrição tratada como texto pelo assistente; equipe vê a transcrição e ouve o original; provedor na lista de suboperadores | G → quebrar em transcrição / tela / custo | S4-6 | DA-53, DA-50 |

## S6 — Piloto

| # | Item | Tamanho | Depende de | Requisitos |
|---|---|---|---|---|
| S6-1 ⚑ | Contrato de tratamento, suboperadores, aviso de IA, retenção; revisão jurídica | — | Clínica, advogado | DA-50 |
| S6-2 | Exportação e eliminação ou anonimização de dados do titular | M | S2-12 | RF-S43 |
| S6-3 | Rotina de retenção conforme a P-03 | M | S6-1 | P-03 |
| S6-4 | *Rate limiting* (PT-07), backups e logs conforme a P-18 | M | — | PT-07, P-18 |
| S6-5 | Checklist G1–G12 com responsável e data | P | S6-1 a S6-4 | ADR-0014 |
| S6-6 ⚑ | Configuração da clínica real, treinamento, ativação no nível 1 | M | S6-5 | ADR-0025 |
| S6-7 ⚑ | Revisões semanais e subida de nível por critério | contínuo | S6-6 | Plano de validação |

## Depois do MVP

Ordem combinada (DA-53): 1º lembretes de vacina e retorno; 2º integração com o sistema de gestão da clínica piloto. Os demais itens ficam registrados, sem ordem.

- Autenticação em dois fatores.
- Embedded Signup (antes da segunda clínica).
- Aviso à equipe fora da aplicação.
- Modelos aprovados do WhatsApp (lembretes e retorno, versão B).
- Unidades.
- Integração com o sistema de gestão da clínica piloto (versão A).
