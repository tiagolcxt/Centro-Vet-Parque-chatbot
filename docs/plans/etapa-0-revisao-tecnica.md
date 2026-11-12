# Etapa 0 — Documento para revisão técnica

> **Objetivo:** dar a um revisor externo o necessário para avaliar a proposta da Etapa 0 **antes** de aprovar a implementação.
> **Documentos revisados:** [etapa-0-analise-e-proposta.md](etapa-0-analise-e-proposta.md) (chamado aqui de **Proposta**) e [implementation-roadmap.md](implementation-roadmap.md) (**Roadmap**).
> **Regra desta revisão:** nenhuma decisão (D1–D9) foi alterada e nenhuma pendência (C1–C12) foi resolvida. Os problemas encontrados nos documentos originais estão registrados aqui (seção 11), sem alteração nos originais. Onde há recomendação, ela é só recomendação; a decisão é do responsável pelo projeto.

---

## Sumário

1. Resumo executivo
2. Arquitetura e responsabilidades dos módulos
3. Modelo de domínio e relacionamentos
4. Contexto e memória das conversas
5. Fluxo de atendimento e encaminhamento humano
6. Segurança e proteção contra respostas inventadas
7. Decisões D1–D9 (alternativas, prós, contras, consequências)
8. Pendências C1–C12
9. Análise dos pontos de atenção solicitados
10. Decisões tomadas implicitamente
11. Inconsistências entre Proposta e Roadmap
12. Riscos e melhorias possíveis
13. Perguntas a responder antes da implementação

---

## 1. Resumo executivo

**Produto:** backend de um assistente virtual que faz a primeira camada de atendimento de uma clínica veterinária: responde dúvidas institucionais, coleta dados de tutor e animal, organiza pedidos de agendamento e encaminha à equipe humana. O WhatsApp é o canal-alvo, mas o MVP expõe apenas uma API REST.

**Stack (herdada da referência, sem upgrade):** Java 25, Spring Boot 4.0.5, Spring AI 2.0.0-M4 (OpenAI, `gpt-4o-mini` na referência), Spring Data JPA/Hibernate, MySQL, Gradle, JUnit 5. A proposta acrescenta Flyway e Testcontainers.

**Ideia central:** o modelo **interpreta** e o backend **decide e executa**. A cada turno, o modelo devolve uma resposta estruturada (texto ao cliente + metadados: intenção, dados extraídos, pedido de encaminhamento, lacuna de conhecimento). O backend valida esse objeto, aplica regras determinísticas (encaminhamento, completude da solicitação, guarda contra valores inventados) e só então grava e responde. O modelo não tem acesso a repositórios e, no MVP, não recebe nenhuma ferramenta de escrita.

**Contexto em cinco camadas, cada uma com fonte própria:** prompt de identidade (`.st`), conhecimento da clínica (YAML; campo vazio aparece como `DESCONHECIDO`), dados do tutor/animal (banco), janela das últimas 20 mensagens (banco) e estado operacional (conversa + solicitação em andamento).

**Avaliação geral desta revisão:** a direção é sólida e coerente com o briefing. Os pontos mais fracos, detalhados nas seções 9 e 12:
- a **garantia de encaminhamento humano** depende da classificação feita pelo modelo e não há rota de saída do estado de espera nem prazo;
- a **guarda contra invenção** é mais estreita do que a Proposta dá a entender e tem falsos positivos previsíveis;
- **LGPD**: falta tratar a transferência internacional de dados para a OpenAI, a retenção e os direitos do titular;
- **fronteiras da conversa** (quando uma conversa termina e outra começa) não foram definidas, e isso afeta o modelo de domínio;
- há **inconsistências de ordem** no Roadmap (autenticação só na Etapa 4, enquanto dados pessoais já trafegam na Etapa 2).

---

## 2. Arquitetura e responsabilidades dos módulos

Hexagonal "pragmática", organizada por módulo de domínio. Cada módulo tem `domain` (Java puro), `application` (casos de uso e portas estritamente necessárias) e `infrastructure` (HTTP, JPA, Spring AI).

| Módulo | Responsabilidade | Principais elementos propostos |
|---|---|---|
| `conversation` | Núcleo: ciclo de vida da conversa, mensagens e **orquestração do turno** | `Conversation`, `Message`, `Intent`, `HandoffPolicy`; `HandleIncomingMessageUseCase`, `ContextAssembler`, `TurnValidator`, porta `AssistantModel`, `AssistantTurn` |
| `tutor` | Tutor e seus animais (mesmo módulo, ver D3) | `Tutor`, `Pet`, `Species`; `RegisterCollectedDataUseCase`, `FindTutorContextUseCase` |
| `servicerequest` | Solicitações para a equipe e seu ciclo de vida | `ServiceRequest`, `RequestType`, `RequestStatus`, `AppointmentDetails` |
| `clinicknowledge` | Informações institucionais carregadas de YAML | `ClinicKnowledge`, `KnowledgeCategory`, `KnowledgeEntry`, `ClinicKnowledgeProvider` |
| `ai` | Adaptador Spring AI: `ChatClient`, carregamento dos `.st` | `SpringAiAssistantModel`, `PromptTemplates`; `tool/` vazio no MVP |
| `shared` | Configuração transversal | `Clock` (`America/Sao_Paulo`), `GlobalExceptionHandler` (`ProblemDetail`) |

**Interfaces previstas (e por quê):** `AssistantModel` (OpenAI em produção, *double* nos testes) e um repositório por agregado (JPA, isolando o domínio da JPA, como o briefing exige). Nenhuma outra interface está planejada.

**Canal:** não há interface de canal. O caso de uso recebe um comando neutro `IncomingMessage(channel, externalContactId, text, receivedAt)` e responde de forma síncrona. O WhatsApp viria como um novo adaptador.

**Observações do revisor sobre a arquitetura:** ver 9.6.

---

## 3. Modelo de domínio e relacionamentos

```mermaid
erDiagram
    TUTOR ||--o{ PET : "possui"
    TUTOR ||--o{ CONVERSATION : "identificado em (opcional)"
    CONVERSATION ||--o{ MESSAGE : "contém"
    CONVERSATION ||--o{ SERVICE_REQUEST : "origina"
    TUTOR |o--o{ SERVICE_REQUEST : "referente a (opcional)"
    PET |o--o{ SERVICE_REQUEST : "referente a (opcional)"

    TUTOR { uuid id; string name; string phone; enum channel; string externalContactId; datetime firstSeenAt; datetime lastSeenAt }
    PET { uuid id; uuid tutorId; string name; enum species; string breed; string ageDescription }
    CONVERSATION { uuid id; enum channel; string externalContactId; uuid tutorId; enum status; enum currentIntent; string handoffReason; datetime startedAt; datetime lastActivityAt; long version }
    MESSAGE { uuid id; uuid conversationId; enum author; text content; datetime createdAt; enum intent }
    SERVICE_REQUEST { uuid id; uuid conversationId; uuid tutorId; uuid petId; enum type; enum status; string summary; json details; datetime createdAt; datetime updatedAt }
```

- `Conversation.status`: `OPEN` → `WAITING_HUMAN` → `CLOSED`.
- `ServiceRequest.type`: `APPOINTMENT`, `INFORMATION`, `FOLLOW_UP`, `HUMAN_CONTACT`.
- `ServiceRequest.status`: `COLLECTING` → `PENDING` → `IN_PROGRESS` → `RESOLVED`, com `CANCELLED` a partir de qualquer estado não final. `submit()` só é aceito quando `missingFields()` está vazio.
- `AppointmentDetails`: `pet`, `reason`, `preferredPeriod` (texto livre). **Não existe campo de data/hora confirmada.**
- `Message` é agregado separado de `Conversation`, para não carregar o histórico inteiro.
- `ClinicKnowledge` não é entidade persistida: é objeto de valor carregado do YAML.

Lacunas de modelagem encontradas: ver 10 (decisões implícitas) e 9.7 (agendamento × confirmação).

---

## 4. Contexto e memória das conversas

| # | Camada | Fonte | Forma no prompt |
|---|---|---|---|
| 1 | Identidade e comportamento | `system-message.st` | sistema |
| 2 | Institucional | `clinic-knowledge.yml` → `clinic-context.st` | sistema, bloco `<dados_da_clinica>`; vazio = `DESCONHECIDO — confirmar com a equipe` |
| 3 | Tutor e animal | tabelas `tutor`/`pet` | sistema, bloco `<cliente>`; "confirmado nesta conversa" × "registrado anteriormente — confirmar" |
| 4 | Conversa | tabela `message`, últimas **20** mensagens | mensagens `user`/`assistant` reais |
| 5 | Operacional | `Conversation` + `ServiceRequest` em andamento + data/hora do `Clock` | sistema, bloco `<estado_do_atendimento>` com campos faltantes |

Escolhas associadas:
- sem resumo automático de conversa;
- sem `ChatMemory`/`MessageChatMemoryAdvisor` do Spring AI (o histórico tem uma única fonte, o nosso banco);
- sem RAG;
- data/hora fornecida pelo backend, não pelo modelo.

Análise da janela de 20 mensagens: ver 9.3.

---

## 5. Fluxo de atendimento e encaminhamento humano

**Turno normal:**
1. Valida a entrada e grava a mensagem do cliente (transação 1).
2. Se a conversa está `WAITING_HUMAN`, não chama a IA: responde com texto fixo.
3. Monta as camadas de contexto.
4. Chama o modelo fora de transação.
5. Valida o `AssistantTurn`: intenção inválida vira `OTHER`, dados extraídos são normalizados e a guarda verifica preço, horário e confirmação de agendamento. Se a guarda falhar, há uma nova tentativa; se falhar de novo, vai a resposta segura padrão.
6. Aplica as regras e grava tudo (transação 2, com lock otimista).
7. Responde.

**Falha da IA:** a mensagem do cliente já está gravada; o cliente recebe a resposta padrão e o estado não muda.

**Encaminhamento (`HandoffPolicy`, determinística):**
1. `HUMAN_REQUEST`/`COMPLAINT` → encaminha.
2. `MEDICAL_CONCERN`/`TREATMENT_FOLLOW_UP` → encaminha ou oferece (C4).
3. `handoff.required = true` vindo do modelo → encaminha (o backend só acrescenta encaminhamentos, nunca remove).
4. `knowledgeGap` → cria `ServiceRequest(INFORMATION)`.
5. Falha repetida da guarda → encaminha.

**Efeitos do encaminhamento:** `WAITING_HUMAN`, `handoffReason`, `ServiceRequest(HUMAN_CONTACT)`, histórico preservado. Nenhuma notificação é enviada; a equipe consulta por `GET`. A saída do estado depende do `PATCH` proposto em D6.

Análise de quanto isso é "garantido": ver 9.5.

---

## 6. Segurança e proteção contra respostas inventadas

**Proposto:**
- dados de outro tutor nunca entram no contexto;
- tutor identificado pelo canal, nunca por telefone digitado;
- logs sem conteúdo de mensagens e Spring AI fora de `DEBUG`;
- chave da OpenAI só por variável de ambiente;
- conteúdo do cliente delimitado e tratado como dado;
- `X-Api-Key` (D5);
- retenção deixada como pendência (C10).

**Contra invenção:** camada 2 explícita com `DESCONHECIDO`, instrução no prompt e a guarda do `TurnValidator`:
- (a) valores em `R$` precisam existir na base;
- (b) horários citados precisam existir na base;
- (c) expressões de confirmação de agendamento são bloqueadas;
- quando a guarda falha: nova tentativa e, depois, fallback.

Análise da eficácia: ver 9.1, 9.2 e 9.8.

---

## 7. Decisões D1–D9

Para cada decisão: o que foi proposto, as alternativas, vantagens e desvantagens, consequências e a **avaliação do revisor**. A Proposta original registra a maior parte dessas decisões só como uma linha na tabela da seção 8; os detalhes abaixo foram reconstruídos a partir do corpo da Proposta e completados nesta revisão.

### D1 — Tool calling no MVP

- **Proposto:** nenhuma tool de escrita. Saída estruturada (`AssistantTurn`); o backend executa todas as operações.
- **Alternativas:**
  - (A) proposto;
  - (B) tools de escrita pequenas (`registrar_solicitacao`, `solicitar_humano`), como na referência;
  - (C) só uma tool somente leitura (`consultar_informacao_clinica(categoria)`);
  - (D) híbrido: saída estruturada para decisões e tools de leitura.
- **A — vantagens:** o backend controla 100% das escritas; os testes ficam determinísticos com um `AssistantTurn` falso; validação em um só lugar; uma chamada ao modelo por turno.
- **A — desvantagens:** o conteúdo da clínica precisa caber inteiro no prompt; uma única chamada gera texto **e** metadados, que podem sair contraditórios (ver 9.4); não exercita tool calling, que o briefing cita como elemento a "reaproveitar conceitualmente".
- **B:** mais natural para o modelo, mas espalha validação, deixa o modelo decidir quando escrever e cria o risco de chamadas repetidas ou indevidas, que é exatamente o que o briefing pede para evitar.
- **C:** útil quando a base crescer; no MVP adiciona uma ida e volta sem ganho.
- **Consequência de A:** o encaminhamento e a criação de solicitações passam a depender da qualidade dos metadados, o que exige a reconciliação descrita em 9.4.
- **Avaliação do revisor:** A é adequada e coerente com "o backend decide". **Recomendo manter A**, com a ressalva de reconciliar texto e metadados (9.4) e um critério explícito de quando migrar para C (ex.: base institucional acima de ~3–4 mil tokens).

### D2 — Fonte do conhecimento institucional

- **Proposto:** YAML externo ao `jar` (`spring.config.import`), recarregado no restart, todo `null` no início.
- **Alternativas:** (A) YAML; (B) tabelas no MySQL + seed via Flyway; (C) Markdown/texto livre injetado no prompt; (D) RAG.
- **A — vantagens:** simples, versionável, sem interface administrativa, o conteúdo da clínica entra por "copiar e colar", a estrutura tipada permite que a guarda compare preços e horários.
- **A — desvantagens:**
  - quem edita é um técnico (a clínica não mexe sozinha);
  - um erro de sintaxe derruba a inicialização, a menos que haja validação;
  - qualquer alteração exige restart;
  - não há histórico de "quem mudou o preço" fora do Git;
  - em produção, o arquivo precisa viver fora da imagem.
- **B:** prepara o painel administrativo futuro, mas sem tela hoje vira "editar por SQL", que é pior que YAML.
- **C:** mais flexível para FAQ, mas impede a guarda estruturada.
- **D:** proibido pelo briefing sem necessidade comprovada.
- **Consequência:** a guarda (9.1) só funciona se os preços e horários estiverem em campos estruturados, não em texto livre. A Proposta define `services[].price` mas não diz o formato (número? "a partir de R$ 120"? faixa?), e isso precisa ser definido.
- **Avaliação do revisor:** **recomendo manter A**, acrescentando validação do arquivo na inicialização (falhar cedo com mensagem clara) e definindo o formato de preço (valor numérico em centavos + texto livre de observação).

### D3 — Tutor e Pet no mesmo módulo

- **Proposto:** módulo `tutor` com as duas entidades.
- **Alternativas:** (A) mesmo módulo; (B) módulos separados, como no briefing.
- **A — vantagens:** o pet não existe sem tutor; consultas e regras de privacidade são as mesmas; evita dependência cruzada.
- **A — desvantagens:** foge da estrutura sugerida no briefing; se o pet ganhar prontuário ou vacinas, haverá refatoração.
- **Consequência:** baixa; a mudança é localizada.
- **Avaliação do revisor:** decisão razoável e de baixo risco. **Recomendo A.** Ponto em aberto que **não** está na Proposta: se `Pet` é parte do agregado `Tutor` (salvo pelo repositório do tutor) ou um agregado próprio com `tutorId`. Isso afeta a transação 2 e a deduplicação (ver 10).

### D4 — Banco nos testes de integração

- **Proposto:** Testcontainers com MySQL real.
- **Alternativas:** (A) Testcontainers; (B) H2 em modo MySQL; (C) MySQL do `compose.yml` compartilhado com o desenvolvimento.
- **A — vantagens:** executa os mesmos scripts Flyway, tipos (`BINARY(16)`, `JSON`, `DATETIME(6)`) e índices de produção.
- **A — desvantagens:** exige Docker em toda máquina e CI; testes mais lentos (~segundos para subir o container, mitigável com container reutilizável); no Windows, depende do Docker Desktop estar rodando.
- **B:** rápido, mas o modo de compatibilidade não cobre `JSON`, `BINARY(16)` e diferenças de DDL, o que daria falsos verdes.
- **C:** frágil, com estado compartilhado entre testes e dev.
- **Consequência:** o critério "testes passam sem banco local" do Roadmap passa a significar "sem banco instalado, mas com Docker rodando".
- **Avaliação do revisor:** **recomendo A.** O texto do Roadmap deveria dizer explicitamente que o Docker é pré-requisito (11.6).

### D5 — Autenticação da API no MVP

- **Proposto:** cabeçalho `X-Api-Key` com valor em variável de ambiente.
- **Alternativas:**
  - (A) chave estática única;
  - (B) chaves distintas por tipo de cliente (canal de teste × equipe);
  - (C) Spring Security HTTP Basic com usuários da equipe;
  - (D) OAuth2/OIDC;
  - (E) sem autenticação, só `localhost`/rede privada.
- **Vantagens e desvantagens:** ver análise completa em **9.1-bis**.
- **Consequência relevante:** no canal API é o chamador que informa o `externalContactId`. Quem tem a chave pode, portanto, se passar por qualquer contato e ver o contexto dele. A segurança do "tutor identificado pelo canal" (6.3 da Proposta) **depende inteiramente** da proteção dessa chave.
- **Avaliação do revisor:** suficiente **apenas** enquanto a API for usada pelo próprio desenvolvedor em ambiente controlado. Recomendação em 9.1-bis.

### D6 — Endpoint `PATCH /api/service-requests/{id}`

- **Proposto:** a equipe muda o status da solicitação e, opcionalmente, a conversa volta a `OPEN` ou vai para `CLOSED`.
- **Alternativas:**
  - (A) proposto;
  - (B) endpoints separados por agregado (`PATCH /service-requests/{id}` e `POST /conversations/{id}/close|reopen`);
  - (C) não ter nada no MVP.
- **A — vantagens:** um só endpoint.
- **A — desvantagens:** acopla dois agregados num só comando, com regras de qual combinação é válida (ex.: resolver a solicitação mas manter a conversa em espera?); não diz o que acontece com várias solicitações abertas na mesma conversa.
- **B:** mais claro e cada agregado mantém suas regras; um endpoint a mais.
- **C:** inviável, porque a conversa ficaria em `WAITING_HUMAN` para sempre.
- **Consequência:** sem algum mecanismo de saída, o encaminhamento vira beco sem saída (9.5).
- **Avaliação do revisor:** a necessidade é real; **recomendo B** pela separação de responsabilidades. Em qualquer opção, falta definir quem pode chamar (D5) e o que o assistente diz quando a conversa é reaberta.

### D7 — Lombok

- **Proposto:** remover; usar `record` e classes simples.
- **Alternativas:** (A) remover; (B) manter como na referência; (C) manter só em entidades JPA.
- **A — vantagens:** sem processamento de anotações; evita `@Data` em entidade JPA (problema real na referência); `record` cobre DTOs e objetos de valor; Java 25 reduz o boilerplate.
- **A — desvantagens:** entidades JPA e de domínio com getters escritos à mão (mais código); diverge do estilo da referência.
- **Consequência:** baixa.
- **Avaliação do revisor:** **recomendo A.** É preferência de estilo de baixo impacto, que pode ser decidida sem revisão externa.

### D8 — Local do código e repositório

- **Proposto:** projeto Gradle na raiz da pasta *Chatbot Clinica Veterinaria*, questionários em `docs/levantamento/`, `git init` próprio.
- **Fato verificado:** a pasta está dentro de um repositório Git cuja raiz é a pasta do usuário (`C:\Users\t1460`), que registra Desktop, Documents, `.ssh` etc. como não rastreados.
- **Alternativas:** (A) proposto; (B) subpasta `assistant-backend/`, com os questionários na raiz; (C) usar o repositório existente.
- **A — vantagens:** um repositório limpo e dedicado ao projeto, documentação junto do código.
- **A — desvantagens:** o PDF/DOCX dos questionários (binários, com respostas da clínica no futuro) entram no Git; as respostas da clínica são dados de terceiros e **não deveriam ir para um repositório remoto público**.
- **B:** separa o "material do cliente" do código; mais limpo se o repositório for publicado (ex.: portfólio).
- **C:** um commit acidental poderia incluir `.ssh` ou outros dados pessoais. **Desaconselhado.**
- **Avaliação do revisor:** **recomendo B** (ou A com `docs/levantamento/` no `.gitignore`), porque os questionários respondidos conterão dados da clínica. Independentemente da opção: o repositório na pasta do usuário é um risco à parte, que convém tratar fora deste projeto.

### D9 — Janela de histórico

- **Proposto:** últimas 20 mensagens, configurável; sem resumo.
- **Alternativas:**
  - (A) N mensagens;
  - (B) orçamento de tokens;
  - (C) janela + resumo incremental gravado na conversa;
  - (D) histórico completo.
- **Vantagens e desvantagens:** ver 9.3.
- **Avaliação do revisor:** ver recomendação em 9.3.

---

## 8. Pendências C1–C12

Todas dependem da clínica, exceto onde indicado. "Bloqueia" = a etapa não fica correta sem a resposta (ela pode ser implementada com *placeholder*, mas não validada).

| # | Pendência | Pergunta objetiva à clínica | Depende da clínica? | Questionário | Bloqueia |
|---|---|---|---|---|---|
| C1 | Dados institucionais | Nome, endereço, telefones, redes, horários por dia, feriados, serviços, especialidades, banho e tosa | Sim | Não coberto | Etapa 3 (validação) |
| C2 | Divulgação de preços | O assistente pode informar preços? Todos, só alguns, só faixa, nenhum? | Sim | Q2 cita "preços" como motivo, sem dizer se pode informar | Etapa 3 |
| C3 | Situações exclusivas de humano | Quais assuntos o assistente nunca deve conduzir? | Sim | **Q8** | Etapa 4 |
| C4 | Sintomas | Encaminhar automaticamente ou só oferecer? | Sim | Q8, Q12 | Etapa 4 |
| C5 | Emergência | Atende emergência? Em que horário? Há telefone de plantão ou parceiro 24h? Qual texto usar? | Sim | Q2 (só como motivo) | Etapa 4. **Crítico para segurança** |
| C6 | Dados obrigatórios do pedido de consulta | Pet, motivo, preferência, nome e telefone do tutor: quais são obrigatórios? | Sim | Q5, Q6 | Etapa 4 |
| C7 | Tipos de atendimento solicitáveis | Consulta, vacina, retorno, banho e tosa, exames: quais podem ser pedidos pelo assistente? | Sim | Q2, Q11 | Etapa 4 |
| C8 | Comportamento após encaminhar e fora do expediente | Silêncio, resposta fixa, informar horário de retorno? | Sim | Q10, Q12 | Etapa 4 |
| C9 | Persona | Nome do assistente, você/senhor(a), emojis | Sim | — | Etapa 2 (pode usar *placeholder*) |
| C10 | Retenção e aviso de privacidade | Por quanto tempo guardar conversas? Aviso na primeira mensagem? | Sim, **com apoio jurídico** (a clínica é a controladora dos dados) | — | Antes de qualquer uso real |
| C11 | Quem acompanha as pendências | Quem lê, com que frequência e como é avisado | Sim | Q10 | Etapa 4 (define se `GET` basta) |
| C12 | Cancelamento e faltas | Política de cancelamento, no-show, antecedência | Sim | Q7 | Etapa 3 |

**Pendências que esta revisão acrescenta (não estão na Proposta), para decisão do responsável:**

| # | Pendência | Depende de |
|---|---|---|
| C13 | Quando uma conversa termina (inatividade, conclusão, fim do dia?) e o que acontece se o cliente volta | Clínica (expectativa) + técnica |
| C14 | O que fazer quando a equipe já resolveu fora do sistema (ex.: confirmou por telefone) e o cliente volta a perguntar ao assistente | Clínica |
| C15 | Aceite e contratação da OpenAI como operadora de dados (transferência internacional) | Clínica + jurídico |
| C16 | Pedido com mais de um animal ("marcar para os dois") | Clínica (como agendam hoje) |

---

## 9. Pontos de atenção solicitados

### 9.1-bis `X-Api-Key` é suficiente para o MVP?

**O que a chave resolve:** impede acesso anônimo casual e é trivial de implementar (um filtro) e de usar em Postman/curl.

**Limitações:**
1. **Sem identidade:** todos que usam a chave são "a mesma pessoa". Não há auditoria de quem da equipe leu os dados de qual tutor, e a LGPD espera rastreabilidade de acesso a dados pessoais.
2. **Sem papéis:** a mesma chave dá acesso às rotas de "cliente" (enviar mensagem) e às de "equipe" (listar todas as pendências com dados de todos os tutores).
3. **Personificação:** no canal API é o chamador que informa o `externalContactId`, então quem tem a chave pode se passar por qualquer contato e ler o contexto dele (6.3 da Proposta depende disso).
4. **Vazamento = acesso total:** chave estática, sem expiração; a rotação é manual e exige restart.
5. **Depende de HTTPS:** sem TLS, a chave trafega em claro.
6. **Detalhes de implementação** que precisam constar: comparação em tempo constante e a chave nunca logada (incluindo logs de acesso do servidor).
7. **Não limita custo:** sem *rate limiting*, quem tem a chave pode gerar chamadas ilimitadas à OpenAI.
8. **Não serve para o WhatsApp:** o webhook da Meta se autentica por assinatura HMAC (`X-Hub-Signature-256`), outro mecanismo.

**Alternativas:**

| Opção | Quando serve | Custo |
|---|---|---|
| (E) Sem autenticação, só `localhost` | Só o desenvolvedor, na própria máquina | Zero |
| (A) Chave única | Testes do desenvolvedor em servidor de homologação | Muito baixo |
| (B) Duas chaves: "canal" e "equipe" | Primeiros testes com alguém da clínica consultando pendências | Baixo |
| (C) HTTP Basic + usuários da equipe (Spring Security) | Equipe real acessando dados reais | Médio: cadastro de usuários e hash de senha |
| (D) OAuth2/OIDC | Painel administrativo, vários usuários | Alto para o MVP |

**Recomendação do revisor:** A (ou B) é aceitável **somente** enquanto não houver dados reais de clientes e o acesso for do desenvolvedor. Antes de qualquer dado real, passar a C com usuário por pessoa e log de acesso. Também recomendo implementar a chave **já na Etapa 2**, quando os dados pessoais começam a trafegar, e não na Etapa 4 (ver 11.1), e incluir *rate limiting* simples por chave.

### 9.1 A validação impede mesmo preços, horários e informações inventadas?

**Resposta curta: não impede; reduz.** A guarda é uma rede de segurança estreita. A principal proteção continua sendo a camada 2 explícita + instruções + avaliação (Etapa 5).

**O que a guarda proposta detecta:** valores no formato `R$ 120` ausentes da base; horários no formato `8h`/`08:00` ausentes da base; expressões fixas de confirmação.

**Falsos negativos (invenção que passa):**
- preço por extenso ou sem `R$`: "cento e vinte reais", "120 reais", "uns 100", "a partir de 90";
- **valor certo atribuído ao serviço errado**: a base tem vacina a R$ 100 e o modelo diz "a consulta custa R$ 100". O valor existe, então passa;
- horário em linguagem natural: "abrimos às oito", "até meio-dia", "funcionamos de manhã", "sábado estamos abertos";
- **dias e feriados**: "abrimos no feriado" não é verificado;
- **serviços inventados**: "fazemos ultrassom", "temos dermatologista";
- endereço, telefone e formas de pagamento inventados: não verificados;
- confirmação parafraseada: "já reservei seu horário", "te espero amanhã às 10", "pode vir terça".

**Falsos positivos (resposta correta bloqueada):**
- **eco da preferência do cliente**: "anotei sua preferência por terça às 10h" contém um horário que não está na base e seria bloqueado. É exatamente o fluxo de agendamento (5.3 do briefing), então o bloqueio vai disparar com frequência;
- cliente cita um preço ("vi que é R$ 80") e o assistente responde "esse valor eu preciso confirmar";
- horários de medicação ou de sintomas citados pelo cliente ("ele vomitou às 3h").

**Custos:** cada bloqueio gera uma nova chamada (latência ~2x e custo ~2x naquele turno) e, na segunda falha, uma resposta genérica, que piora a experiência.

**Alternativas e complementos:**
1. **Lista de permissão por origem:** valores e horários que aparecem nas mensagens do cliente ou no `preferredPeriod` são permitidos. Resolve os falsos positivos principais. Baixo custo.
2. **Citações estruturadas:** o `AssistantTurn` passa a incluir `usedKnowledgeKeys` (ex.: `services.vacina-v10.price`), e o backend verifica que cada valor citado pertence a uma chave citada. Resolve o "valor certo no serviço errado". Custo médio.
3. **Normalização de números por extenso** antes da verificação. Custo baixo a médio; não cobre todas as formas.
4. **Política de produto mais restritiva (C2):** se a clínica não quiser divulgar preços, a guarda vira "nenhum valor monetário permitido", que é bem mais robusto.
5. **Respostas institucionais por template:** para perguntas puramente factuais (horário, endereço), o texto é montado pelo backend a partir da base e o modelo só escolhe a categoria. É a proteção mais forte, com perda de naturalidade.
6. **Avaliação contínua:** o conjunto de cenários da Etapa 5 com "perguntas armadilha" (serviço inexistente, preço não cadastrado, feriado) é o que mede a taxa real de invenção.

**Recomendação do revisor:** adotar 1 como obrigatório, 2 como desejável e 6 como critério de aceite. Avaliar 5 para horário e endereço. **E atualizar a redação da Proposta**, que hoje sugere que a guarda "garante" o que não garante.

### 9.2 Saída estruturada é robusta para controlar o fluxo?

**Pontos fortes:** com JSON Schema estrito, o *formato* fica praticamente garantido; o backend trata a intenção como dica e aplica as regras; a falha de *parse* já cai no fluxo de falha.

**Fragilidades:**
1. **Formato válido ≠ conteúdo correto.** O modelo pode devolver JSON perfeito com `intent = OTHER` para uma reclamação ou `handoff.required = false` para um pedido claro de atendente. O controle do fluxo herda os erros de classificação.
2. **Texto e metadados gerados juntos podem se contradizer:**
   - `reply` diz "vou te passar para a equipe" e `handoff.required = false`: o cliente espera um humano que nunca vem;
   - `handoff.required = true`, mas o `reply` continua a conversa normalmente;
   - o backend decide encaminhar por regra (ex.: `COMPLAINT`), mas o `reply` do modelo não diz isso.
3. **Restrições do modo estrito da OpenAI:** todos os campos obrigatórios (opcionais precisam ser `nullable`), sem `oneOf` livre, profundidade limitada. O suporte no Spring AI 2.0.0-M4 ainda precisa ser verificado (a Proposta já registra isso).
4. **Recusas e cortes:** a resposta pode vir como recusa do modelo ou truncada por limite de tokens. A Proposta não diz explicitamente que isso entra no fluxo de falha.
5. **Uma intenção por turno:** "quero marcar consulta e saber o preço da vacina" tem duas intenções. O enum único força escolher uma.
6. **Extração errada vira dado persistido:** se o modelo extrai `species = CAT` para "a Mel é uma cachorra", o erro é gravado e reapresentado como "confirmado nesta conversa".

**Alternativas e complementos:**
- **Reconciliação no backend (recomendado):** quando o backend decide encaminhar, o texto final é **composto** pelo backend (`reply` do modelo, se compatível, + frase fixa de encaminhamento) ou substituído por um texto padrão. Se o `reply` menciona transferência e o flag é falso, tratar como encaminhamento.
- **Duas chamadas** (classificar e depois responder com a decisão já tomada): metadados mais confiáveis e texto coerente com a decisão, ao custo de ~2x latência e custo por turno.
- **`secondaryIntents[]`** no `AssistantTurn`, ou intenção mantida só como dica.
- **"Confirmado" = confirmado pelo cliente:** dados extraídos só passam a "confirmados" quando o assistente os repete e o cliente não contesta; até lá, ficam como "informado nesta conversa".

**Recomendação do revisor:** manter uma chamada só (D1), com reconciliação obrigatória e com recusa/truncamento tratados explicitamente como falha. Reavaliar duas chamadas se a Etapa 5 mostrar inconsistência acima de um limite combinado.

### 9.3 Limitar a 20 mensagens causa perda de contexto?

**Sim, em casos previsíveis.**
- **O uso real do WhatsApp é fragmentado:** "oi" / "bom dia" / "tudo bem?" / "queria ver uma coisa" / "da minha gata". Vinte mensagens podem ser só 10 trocas, cobrindo poucos minutos.
- **O que se perde:** só os dados estruturados sobrevivem nas camadas 3 e 5 (nome do pet, espécie, motivo, preferência). Informações livres somem quando saem da janela:
  - "ela é alérgica a dipirona";
  - "prefiro que me liguem";
  - "já falei com a doutora ontem";
  - "estou sem carro";
  - o próprio pedido do cliente para ser tratado de certa forma, que o briefing (5.1) manda respeitar.
- **Contagem por mensagem ignora tamanho:** uma mensagem pode ter 1.500 caracteres e outra, "ok". O custo e o limite de contexto são em tokens.
- **Risco inverso:** nas conversas curtas, típicas do MVP, 20 mensagens resolvem bem, e um resumo automático acrescentaria custo e um novo ponto de invenção (o resumo pode distorcer).

**Alternativas:**

| Opção | Vantagem | Desvantagem |
|---|---|---|
| (A) 20 mensagens (proposto) | Simples, previsível | Perde informação livre; ignora tamanho |
| (B) Orçamento de tokens (ex.: ~3k tokens de histórico) | Controla custo de verdade | Precisa estimar tokens (aproximação por caracteres basta) |
| (C) Janela + resumo incremental gravado | Mantém o essencial em conversas longas | Uma chamada extra; o resumo pode distorcer; mais um texto a proteger |
| (D) Janela + "observações" estruturadas no `AssistantTurn` (`notes[]` curtas), gravadas na conversa | Preserva o essencial sem um resumo livre | Mais um campo a validar; o modelo pode anotar demais |

**Recomendação do revisor:** começar com A **combinada com limite de tokens** (o que estourar primeiro) e **medir** na Etapa 5 quantas conversas ultrapassam a janela. Se o problema aparecer, preferir D a C, por ser estruturada e verificável. Também vale definir C13 (fronteira da conversa), porque a janela só faz sentido dentro de uma conversa bem delimitada.

### 9.4 Texto × metadados (complemento de 9.2)

Tratado em 9.2. Registrado aqui como ponto independente porque afeta diretamente a experiência ("prometeu humano e não veio") e a confiança.

### 9.5 O encaminhamento humano é efetivamente garantido?

**O que a aplicação garante:** quando a decisão de encaminhar é tomada, o estado muda, a solicitação é criada e o histórico é preservado, de forma determinística e testável.

**O que não é garantido:**
1. **A decisão em si depende do modelo** nas regras 1, 2 e 3: "quero falar com uma pessoa" classificado como `OTHER` com `handoff = false` não encaminha. O briefing diz que o cliente deve poder pedir humano **a qualquer momento**, e hoje isso depende de uma classificação probabilística.
2. **Ninguém é avisado.** Sem notificação (C11), `WAITING_HUMAN` só funciona se alguém consultar o `GET`. Do ponto de vista do cliente, o encaminhamento pode equivaler a abandono.
3. **Não há prazo nem plano B.** Se ninguém atender em X horas, nada acontece: o cliente fica recebendo a resposta fixa indefinidamente.
4. **Fora do expediente** (C8): o cliente não é informado de quando terá retorno.
5. **Risco de segurança em `WAITING_HUMAN`:** a IA não é chamada, então uma mensagem como "ele está convulsionando agora" recebe a mesma resposta fixa ("recebi sua mensagem"). **Este é o risco mais grave encontrado na revisão.**
6. **Saída do estado:** depende de D6, que ainda não está aprovado.

**Alternativas e complementos:**
- **Detecção determinística de pedido explícito de humano**, antes da IA, com termos como "atendente", "falar com alguém", "pessoa", "humano". Não é tabela médica; é sobre o canal de atendimento, e o briefing não proíbe. Funciona mesmo se o modelo errar ou falhar.
- **A resposta fixa em `WAITING_HUMAN` sempre inclui a orientação de emergência (C5)**, ou a conversa em espera continua passando por uma chamada mínima de segurança (custo; o briefing pede para evitar continuar "como se o atendimento humano já tivesse ocorrido", não proíbe orientar emergência).
- **Prazo configurável:** sem retorno em X horas, informar o telefone da clínica ao cliente (sem prometer retorno).
- **Encaminhamento também quando a IA falha** repetidamente (hoje é C, item 7 do fluxo).

**Recomendação do revisor:** tratar o item 5 como **requisito de segurança obrigatório**, independentemente das outras decisões. Adotar a detecção determinística de pedido de humano. Registrar explicitamente na documentação do produto que, no MVP, "encaminhado" significa "registrado para consulta" e não "alguém foi avisado". O briefing já pede para não prometer notificação.

### 9.6 A arquitetura está simples o suficiente para um MVP?

**Adequado:**
- poucas interfaces (`AssistantModel` e repositórios), cada uma com motivo;
- sem interface de canal;
- sem RAG;
- sem `ChatMemory` paralelo.

**Pontos de possível excesso ou acoplamento:**
1. **Casos de uso chamando casos de uso de outros módulos:** `HandleIncomingMessageUseCase` precisaria orquestrar `RegisterCollectedDataUseCase`, `OpenOrUpdateRequestUseCase` e `FindTutorContextUseCase` numa mesma transação. Isso cria acoplamento entre módulos e transações aninhadas. Alternativa mais simples: o caso de uso do turno usa diretamente os **repositórios e objetos de domínio** dos outros módulos, e os "use cases" dos outros módulos ficam só para o que tem entrada externa (HTTP).
2. **Módulo `ai` com uma classe e meia:** poderia ser `conversation/infrastructure/ai` até haver um segundo consumidor. Baixo impacto; a separação atual também se justifica pela intenção de isolar o Spring AI.
3. **Duplicação domínio × JPA:** 5 agregados × (classe de domínio + entidade + mapeador + repositório Spring Data + adaptador) ≈ 25 classes só de persistência. É o preço da exigência "domínio sem JPA" do briefing. Aceitável, mas vale estar ciente.
4. **Duas estratégias de teste para repositório:** a Proposta menciona "repositórios (JPA ↔ teste em memória)" **e** Testcontainers. Ter as duas dobra a manutenção. Escolher uma: em memória para testes de aplicação e Testcontainers só para os adaptadores, **ou** só Testcontainers.
5. **`details` em coluna JSON:** flexível, mas o mapeamento JSON no Hibernate 7 + MySQL tem particularidades. Com um único tipo com detalhes (`APPOINTMENT`), colunas normais anuláveis seriam mais simples.

**Recomendação do revisor:** a arquitetura é proporcional ao briefing. Recomendo deixar explícita a regra de orquestração entre módulos (item 1) e escolher uma única estratégia de teste para repositórios (item 4).

### 9.7 Solicitação de agendamento × agendamento confirmado

**Bem resolvido:**
- `ServiceRequest(APPOINTMENT)` não tem campo de data/hora confirmada, então "consulta marcada" não pode ser representado;
- a guarda bloqueia frases de confirmação;
- os estados falam de solicitação, não de agenda.

**Lacunas:**
1. **`RESOLVED` é ambíguo:** não diferencia "a equipe marcou" de "não havia horário", "cliente desistiu" ou "atendido por telefone". Quando o assistente voltar a falar com o cliente, não saberá o desfecho. Sugestão: `resolution` (texto ou enum) preenchido pela equipe.
2. **O cliente pergunta "está confirmado?":** não há regra para isso. O comportamento correto ("a equipe vai confirmar com você; não tenho essa informação") precisa estar no prompt e num teste (C14).
3. **A guarda de confirmação é estreita** (paráfrases passam, ver 9.1). A proteção estrutural (não existe dado de confirmação) é a mais forte; vale reforçar com cenários na Etapa 5.
4. **Uma solicitação "em andamento" por conversa:** a Proposta fala no singular. Dois pedidos na mesma conversa (vacina para um, consulta para outro) não têm tratamento definido (C16).
5. **Linguagem na API:** os nomes de campos e respostas devem evitar "appointment" como se fosse agenda. `appointmentRequest` é mais seguro, porque a equipe também lê esses dados.

**Recomendação do revisor:** a separação conceitual está correta. Recomendo acrescentar `resolution` e uma regra explícita para "está confirmado?".

### 9.8 Armazenamento e retenção adequados à LGPD?

**Ainda não.** A Proposta trata bem a minimização no contexto (não misturar tutores, logs sem conteúdo), mas deixa lacunas relevantes:

1. **Transferência internacional e operador:** cada turno envia mensagens e dados do tutor para a OpenAI (EUA). Pela LGPD isso é compartilhamento com operador e **transferência internacional** (arts. 33–36), que precisa de base e transparência (aviso ao titular). A Proposta não menciona. Também vale verificar a política de retenção de dados da API da OpenAI e a possibilidade de retenção zero.
2. **Retenção indefinida por padrão:** sem C10, mensagens ficam para sempre, e mensagens livres podem conter CPF, endereço, dados de cartão ou de saúde do próprio tutor ("estou grávida e com medo de pegar algo da gata").
3. **Direitos do titular:** não há como **exportar** ou **excluir** os dados de um tutor (arts. 18). No MVP, ao menos um procedimento documentado (script ou endpoint administrativo) seria necessário antes de dados reais.
4. **Aviso de privacidade:** não há mensagem informando que o atendimento é feito por assistente automatizado e que os dados são tratados (C10). Também é uma questão de transparência ao usuário (saber que fala com uma IA).
5. **Duplicação de dado pessoal:** `tutor.phone` e `externalContactId` guardam o mesmo número no WhatsApp. É preciso definir qual é a fonte.
6. **Minimização no prompt:** o telefone do tutor não precisa ir ao modelo. Só nome e dados do pet são úteis para a conversa.
7. **Segurança do banco:** a Proposta não trata criptografia em repouso, backups, nem quem acessa o MySQL. Para o MVP local isso é aceitável; antes de dados reais, não.
8. **Dados de teste:** os cenários com IA real e o *fixture* da base devem usar apenas dados fictícios, já que são enviados à OpenAI.
9. **Papéis:** a clínica é a controladora e o desenvolvedor/sistema é operador. Isso define quem decide C10 e C15.

**Recomendação do revisor:** sem impedir a implementação com dados fictícios, estabelecer como **condição para qualquer uso com dados reais**: C10 e C15 resolvidos, aviso na primeira mensagem, procedimento de exclusão por tutor, expurgo por prazo e autenticação por usuário (9.1-bis, opção C).

---

## 10. Decisões tomadas implicitamente

Decisões que a Proposta assume sem registrar como decisão nem justificar:

| # | Decisão implícita | Onde aparece | Por que importa | Sugestão |
|---|---|---|---|---|
| I1 | **Modelo `gpt-4o-mini`** (herdado da referência) | Só na análise da referência | Custo, latência e qualidade de classificação e de saída estruturada dependem dele | Registrar como decisão, com critério de troca e versão fixada |
| I2 | **Fronteira da conversa**: uma conversa só começa por `POST` explícito e não há expiração | Seção 6.1 | No WhatsApp não existe "criar conversa": o cliente simplesmente escreve. Sem regra de expiração, a mesma conversa duraria anos, com estado e janela misturados | Decidir agora (C13): *get-or-create* por contato + expiração por inatividade |
| I3 | **Uma solicitação em andamento por conversa** | Seção 4.5 (singular) | Pedidos múltiplos ou com dois animais (C16) | Decidir se a limitação é aceita no MVP |
| I4 | **Deduplicação de pet por nome** dentro do tutor | 4.3 | "Mel" × "Mell" × "a Mel" duplicam; dois pets com o mesmo nome se fundem | Definir a regra de correspondência e quando perguntar ao cliente |
| I5 | **Tutor criado automaticamente por contato** | 4.3 | Cria registro pessoal mesmo para quem só perguntou o endereço (minimização) | Criar o tutor só quando houver dado do tutor/pet a guardar |
| I6 | **Autor da mensagem só `CUSTOMER`/`ASSISTANT`** | 3.1 | No WhatsApp, a equipe responde no mesmo chat, e essas mensagens precisam entrar no histórico | Prever `STAFF` (ou registrar como limitação consciente) |
| I7 | **Resposta síncrona e sem idempotência** | 2.3, 6.1 | O webhook do WhatsApp entrega "pelo menos uma vez" (duplicatas), e mensagens em rajada geram conflito de lock otimista (409) e várias respostas seguidas | Registrar que o MVP não trata e que o adaptador WhatsApp exigirá `externalMessageId` único + serialização por conversa |
| I8 | **Lock otimista resolve concorrência** | Seção 5, item 6 | Na rajada, a segunda mensagem falha em vez de esperar; a mensagem do cliente fica gravada sem resposta | Definir o comportamento no conflito (fila por conversa, ou reprocessar) |
| I9 | **Etapa 2 sem base institucional e sem guarda** | Roadmap | Durante as Etapas 2–3 o assistente pode inventar livremente | Aceitável em desenvolvimento; registrar que nenhuma demonstração para a clínica deve ocorrer antes da Etapa 3 |
| I10 | **Uma intenção por turno** | 3.4 | Ver 9.2, item 5 | Registrar a limitação ou aceitar `secondaryIntents` |
| I11 | **Formato do preço e dos horários no YAML** | 4.2 (só `price?`) | A guarda depende disso | Ver D2 |
| I12 | **Política de timeout e retry com a OpenAI** | Seção 5, item 7 | Latência para o cliente e custo | Definir timeout (ex.: 20–30 s) e número de tentativas |
| I13 | **Spring AI em versão *milestone*** | 1.5 | Milestones podem mudar de API e não têm suporte de produção | Imposto pelo briefing; registrar o risco e o plano de atualização para GA |
| I14 | **Idioma** | 4.1 | O cliente que escreve em espanhol ou inglês | Definir: responder em pt-BR sempre? |

---

## 11. Inconsistências entre Proposta e Roadmap

| # | Inconsistência | Proposta | Roadmap | Efeito |
|---|---|---|---|---|
| 11.1 | **Momento da autenticação** | D5 recomendado para "antes de qualquer deploy" | `X-Api-Key` só na **Etapa 4** | Etapas 2–3 trafegam mensagens e dados de contato sem autenticação |
| 11.2 | **Momento dos ADRs** | Seção 8: "ADRs a registrar na Etapa 1" (001–006) | ADRs distribuídos: 001/004/005 na Etapa 1, 003 na Etapa 3, 002/006 na Etapa 4 | Contradição direta; a do Roadmap é mais coerente com "documentar o que foi implementado" |
| 11.3 | **Intenção antecipada** | Identificação de intenções pertence à Etapa 4 (briefing) | `AssistantTurn` com `intent` já na **Etapa 2** | Contraria "não implementar funcionalidades de etapas futuras". A justificativa (evitar reescrita) é razoável, mas precisa constar como exceção consciente |
| 11.4 | **ADR 004 "se aprovado"** | Histórico próprio é apresentado como decisão (4.4) e não aparece na lista D1–D9 | Etapa 1 cita "ADR 004 (se aprovado)" | A decisão de não usar `ChatMemory` não está na lista submetida à aprovação |
| 11.5 | **Estratégia de teste de repositório** | "repositórios (JPA ↔ teste em memória)" (2.1) | Só Testcontainers | Ambiguidade sobre o que será construído (ver 9.6, item 4) |
| 11.6 | **"Sem banco local"** | D4: Testcontainers | Etapa 1: "passa sem banco local" | Correto, mas omite que o Docker é pré-requisito |
| 11.7 | **Regra 5 da `HandoffPolicy`** (falha repetida da guarda) | Seção 3.5 | A guarda nasce na Etapa 3 e a `HandoffPolicy` na Etapa 4 | Na Etapa 3, a "falha repetida" cai num fallback sem encaminhamento. Aceitável, mas vale registrar |
| 11.8 | **`PATCH` (D6)** | Listado na tabela da API como se fosse parte do escopo | Etapa 4, condicionado a D6 | Ok, desde que fique claro que depende de aprovação |
| 11.9 | **"Garantia" da guarda** | Texto da Proposta sugere bloqueio de invenções | Etapa 3 testa só os formatos simples | Expectativa maior do que a entrega real (ver 9.1) |

---

## 12. Riscos e melhorias possíveis

Classificação: **probabilidade × impacto** (A/M/B).

| # | Risco | Categoria | P | I | Mitigação sugerida |
|---|---|---|---|---|---|
| R1 | Mensagem de emergência em `WAITING_HUMAN` recebe resposta fixa sem orientação | Segurança do animal | M | **A** | Orientação de emergência sempre presente na resposta fixa (9.5) |
| R2 | Pedido de humano não detectado pelo modelo | UX / requisito | M | A | Detecção determinística prévia (9.5) |
| R3 | Encaminhamento sem ninguém para atender (sem notificação ou prazo) | UX | A | A | C11, prazo com orientação de contato, transparência no texto |
| R4 | Invenção de preço, serviço ou horário não capturada pela guarda | Confiabilidade | M | A | 9.1 (lista de permissão, citações, avaliação, C2) |
| R5 | Falsos positivos da guarda no eco da preferência do cliente | UX | **A** | M | Lista de permissão por origem (9.1) |
| R6 | Texto e metadados contraditórios ("vou passar para a equipe" sem encaminhar) | UX / confiança | M | A | Reconciliação no backend (9.2) |
| R7 | Envio de dados pessoais à OpenAI sem base ou aviso | LGPD | **A** (é o comportamento padrão) | A | C10/C15, aviso, minimização no prompt (9.8) |
| R8 | Chave da API vazada dá acesso a todos os dados | Segurança | B–M | A | 9.1-bis; antecipar para a Etapa 2; HTTPS |
| R9 | Conversas sem fronteira (I2) misturam estado antigo e novo | Correção | A (no WhatsApp) | M | C13 antes da Etapa 2 |
| R10 | Rajada de mensagens gera 409 ou respostas múltiplas | UX | A (no WhatsApp) | M | Registrar agora, resolver no adaptador (I7/I8) |
| R11 | Dado extraído errado gravado como "confirmado" | Correção | M | M | Distinguir "informado" de "confirmado" (9.2) |
| R12 | Testes de IA instáveis (não determinísticos) | Qualidade | A | M | `temperature` baixa, repetição de cenários com limite de aprovação (ex.: ≥ 9/10), asserções sobre metadados |
| R13 | Breaking changes no Spring AI milestone | Manutenção | M | M | Fixar versão, isolar no adaptador `ai` (já previsto) |
| R14 | Custo e latência (até 2 chamadas por turno com retry, prompt com base inteira) | Operação | M | B–M | Medir tokens por turno na Etapa 5; timeout; limite de tamanho de mensagem |
| R15 | Erro no YAML derruba a aplicação | Operação | M | M | Validação na inicialização (D2) |
| R16 | Prompt injection via mensagem do cliente | Segurança | M | M (o modelo não tem tools de escrita, então o impacto é limitado ao texto) | Já tratado; incluir cenários na Etapa 5 (já previsto) |
| R17 | Questionários respondidos versionados no Git | Privacidade | M | M | D8 opção B ou `.gitignore` |
| R18 | Resposta longa ou robótica, menus repetidos | UX | M | M | Limite de tamanho no prompt; avaliação qualitativa com alguém da clínica na Etapa 5 |

**Melhorias de baixo custo que podem ser consideradas (sem alterar as decisões):**
- métrica por turno: intenção, encaminhou?, guarda disparou?, tokens, latência (sem conteúdo), para basear a Etapa 5 em dados;
- limite de tamanho da mensagem do cliente definido (ex.: 2.000 caracteres);
- `temperature` baixa e versão do modelo fixada em configuração;
- texto de transparência ("sou a assistente virtual da clínica") na primeira resposta, ligado a C9 e C10.

---

## 13. Perguntas a responder antes da implementação

### Para o responsável pelo projeto (técnicas)

1. Aprova D1–D9 como estão ou com as alternativas apontadas em 7? Em especial **D5** (quando e qual autenticação), **D6** (endpoint único ou separado) e **D8** (questionários fora do repositório?).
2. A decisão de **não usar `ChatMemory`** (ADR 004) entra formalmente na lista de decisões? (11.4)
3. A antecipação do campo `intent` para a Etapa 2 é uma exceção aceita? (11.3)
4. Qual estratégia de teste de repositório: em memória + Testcontainers, ou só Testcontainers? (11.5)
5. Como tratar a **fronteira da conversa** (I2/C13) já no MVP: expiração por inatividade? De quanto tempo?
6. Aceita a **detecção determinística de pedido de humano** como complemento ao modelo? (9.5)
7. Adota **reconciliação** entre `reply` e decisão do backend? (9.2)
8. Janela por mensagens, por tokens ou ambos? (9.3)
9. O modelo (`gpt-4o-mini`) e os parâmetros (temperatura, timeout) ficam como estão ou são reavaliados? (I1/I12)
10. Até que etapa o sistema roda **só com dados fictícios**, e quais são as condições para dados reais? (9.8)

### Para a clínica (complementam C1–C12 e o questionário)

11. Dados institucionais completos (C1) e o **formato dos preços**, se puderem ser divulgados (C2).
12. **Emergências:** atendem? Em que horário? Qual telefone ou parceiro indicar? (C5) *Prioridade alta, porque está ligada ao risco R1.*
13. Quem acompanha os pedidos registrados, com que frequência, e qual o prazo aceitável de retorno? (C11, R3)
14. Quando a equipe confirma algo por fora (telefone), como o assistente deve responder se o cliente perguntar? (C14)
15. Como tratam pedidos para mais de um animal? (C16)
16. Concordam com o uso de um serviço de IA externo e com um aviso de privacidade na primeira mensagem? Por quanto tempo querem guardar as conversas? (C10, C15)

### Para o revisor externo (onde a opinião dele mais ajuda)

17. A combinação "uma chamada com saída estruturada + reconciliação" é suficiente, ou recomenda separar classificação e resposta? (9.2)
18. A guarda contra invenção, com os complementos de 9.1, é um investimento proporcional para o MVP?
19. A divisão em módulos e a regra de orquestração entre módulos (9.6) estão adequadas?
20. Há algo na análise de LGPD (9.8) que precise de parecer jurídico antes da Etapa 1?
