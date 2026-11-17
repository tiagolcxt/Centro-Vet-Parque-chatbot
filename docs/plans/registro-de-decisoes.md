# Registro de decisões

> Fonte única de verdade sobre **o que foi decidido, o que foi mantido e o que está em aberto**.
> Atualizado em 2026-11-17 (modelo de informações → DA-39; P-10, parte técnica → DA-38; valores provisórios de P-06, P-07, P-08, P-13 e P-15 na Etapa 5; PT-09 → DA-35; PT-12 → DA-36; PT-15 → DA-37; PT-10 → DA-30; PT-14 → DA-31; PT-02 → DA-32; PT-06 → DA-33; PT-08 → DA-34). Em 2026-11-16: P-09 → DA-28; PT-11 → DA-29. Antes, em 2026-11-11: PT-01, PT-03, PT-04 e PT-05 decididas; Diretrizes de Atendimento incorporadas como DA-22 a DA-26, após a [revisão técnica](etapa-0-revisao-tecnica.md) e a consolidação de decisões pelo responsável pelo projeto.
> Uma recomendação da revisão **só** aparece como decisão se foi aprovada explicitamente; as demais estão na seção 4.

**Convenções:**
- `DA-xx`: decisão aprovada nesta rodada.
- `DM-xx`: decisão mantida da proposta original (não alterada nem contestada).
- `P-xx`: pendência que depende da clínica, de orientação jurídica ou de dado externo.
- `PT-xx`: pendência técnica que depende do responsável pelo projeto.
- Etapas: ver [roadmap](implementation-roadmap.md). A Etapa 7 é a barreira para dados reais.

---

## 1. Decisões aprovadas

| ID | Decisão | Origem | ADR |
|---|---|---|---|
| DA-01 | Uma chamada principal ao modelo por turno, com **saída estruturada** e **reconciliação** entre texto e metadados. Recusas, respostas truncadas, inválidas ou inconsistentes são **falhas**. | D1 + revisão 9.2 | 0002 |
| DA-02 | O modelo **não acessa** repositórios nem executa persistência; nenhuma tool de escrita no MVP. O backend valida e controla todas as alterações. | D1 + briefing 7 | 0002 |
| DA-03 | Conhecimento institucional em **YAML**, com estruturas **tipadas**, chaves estáveis e **validação na inicialização**. Desconhecido representado **explicitamente** (`null` = DESCONHECIDO; item ausente = erro). | D2 + revisão 7/D2 | 0003 |
| DA-04 | Tutor e Pet no **mesmo módulo** inicialmente. | D3 | 0001 |
| DA-05 | **Testcontainers + MySQL real** em todos os testes de integração com persistência; **estratégia única** (sem repositórios em memória); **Docker é pré-requisito**. | D4 + revisão 11.5 | 0006 |
| DA-06 | **API Key** apenas em testes controlados com dados fictícios. **Autenticação individual, autorização e auditoria** obrigatórias antes de dados reais. | D5 + revisão 9.1-bis | 0012 |
| DA-07 | Atualização de `ServiceRequest` **separada** do encerramento ou reabertura de conversa. **Solicitação de atendimento ≠ agendamento confirmado.** | D6 + revisão 9.7 | 0011 |
| DA-08 | **Sem Lombok.** | D7 | 0001 |
| DA-09 | **Repositório Git próprio**; questionários preenchidos, dados reais da clínica, credenciais e dados pessoais **não versionados**. | D8 | 0013 |
| DA-10 | Histórico: **últimas 20 mensagens**, respeitando também um **limite de tokens**; **sem sumarização automática**. | D9 + revisão 9.3 | 0004 |
| DA-11 | Encaminhamento humano: **detecção determinística** de pedido explícito; estado persistido; `WAITING_HUMAN` = **sinalizada para revisão**, não notificação; nunca afirmar que alguém foi avisado; **mensagens em `WAITING_HUMAN` continuam avaliadas para possível emergência**; orientação conforme **política aprovada pela clínica**; comportamento para **prazo vencido** definido como mecanismo (conteúdo em P-02). | Prompt 3.1 + revisão 9.5 | 0007 |
| DA-12 | Proteção contra informação inventada baseada em **fontes institucionais declaradas** (`knowledgeRefs`) e na verificação de que o conteúdo está **autorizado** pelas fontes; **limites documentados**; sem promessa de garantia absoluta; testes desde a introdução. | Prompt 3.2 + revisão 9.1 | 0008 |
| DA-13 | **Ciclo de vida da conversa** explícito: uma conversa ativa por contato, nova conversa após expiração por inatividade ou encerramento, nada de atendimento anterior reutilizado como contexto além do cadastro com proveniência. | Prompt 3.3 + revisão I2 | 0009 |
| DA-14 | **Proveniência** das informações de tutor e pet: MENCIONADA, CONFIRMADA, CADASTRADA, A_RECONFIRMAR; informação antiga nunca vale como confirmação atual. | Prompt 3.4 + revisão 9.2 | 0010 |
| DA-15 | **Limites do atendimento**: não diagnosticar; não prescrever medicamentos ou dosagens; não inventar preços, horários, serviços ou políticas; encaminhar questões clínicas, reclamações, incertezas e pedidos de humano conforme as regras; nunca afirmar agendamento confirmado. | Prompt 3.5 + briefing 5 | 0007, 0008, 0011 |
| DA-16 | **Barreira para dados reais**: lista de condições G1–G12 obrigatórias antes de qualquer uso com dados reais (Etapa 7). | Prompt 4 + revisão 9.8 | 0014 |
| DA-17 | Desenvolvimento e testes **exclusivamente com dados fictícios**. | Prompt 4 | 0014 |
| DA-18 | Histórico **montado pela própria aplicação** a partir do banco (sem `ChatMemory`/`MessageChatMemoryAdvisor`). | PT-01, decidida em 2026-11-11 | 0015 |
| DA-19 | Detalhes da solicitação em **colunas próprias anuláveis** na tabela `service_request` (sem coluna JSON). | PT-03, decidida em 2026-11-11 | 0005 |
| DA-20 | Repositório Git em **subpasta** `assistant-backend/` (código + `docs/`); questionários e material da clínica **fora** do repositório. | PT-04, decidida em 2026-11-11 | 0013 |
| DA-21 | Mensagens simultâneas na mesma conversa: conflito → **`409`**. A mensagem do tutor fica registrada sem resposta; quem chamou **não reenvia** (ela entra no histórico do turno seguinte). Serialização fica para o adaptador WhatsApp. | PT-05, decidida em 2026-11-11 | — |
| DA-22 | **Diretrizes Institucionais de Atendimento e Conduta** adotadas como requisitos do sistema (não como sugestão de estilo); texto institucional e conteúdo clínico sujeitos à aprovação da clínica e da equipe veterinária (P-19, P-20) | Diretrizes, 2026-11-11 | — ([diretrizes](../product/diretrizes-de-atendimento.md)) |
| DA-23 | **Hierarquia de prioridades P1–P6**, reavaliada a cada mensagem (inclusive em `WAITING_HUMAN`), independente do estado operacional, sem dados comerciais, com pisos determinísticos, nível mais urgente prevalecendo e **rebaixamento só por funcionário** | Diretrizes 2 e 6 | 0018 |
| DA-24 | **Regras de precedência**: segurança do animal > eficiência; bem-estar > conveniência comercial; pedido do tutor por humano > automação; informação verificada > rapidez | Diretrizes, seção final | — ([diretrizes, seção 0](../product/diretrizes-de-atendimento.md)) |
| DA-25 | **Encaminhamento ampliado**: prioridade P1–P2 (P3 conforme P-20), insatisfação, decisão de funcionário; oferta (ou encaminhamento, P-12) em sofrimento emocional, confusão e conversa improdutiva; sem pedir justificativa nem retomar o fluxo automatizado | Diretrizes 3.1, 3.3 | 0007 |
| DA-26 | **Validação V6 ampliada**: reprova notificação inexistente, promessa de contato ou retorno sem processo aprovado e afirmação de solicitação resolvida | Diretrizes 1.3, 1.4 | 0008 |
| DA-27 | **Lista de expressões de alerta** aprovada pela equipe veterinária usada como **verificação determinística** que impõe piso P1/P2: só eleva a prioridade, nunca rebaixa, não diagnostica; ausência de correspondência não significa situação leve | PT-13, decidida em 2026-11-11 (opção A) | 0019 |
| DA-28 | **Modelo e parâmetros** (ADR-0016): `gpt-6-luna`; `reasoning_effort` `low` (principal e modo restrito); `temperature` não enviada; saída máx. 2.000 / 500 tokens; histórico 20 mensagens + 4.000 tokens; mensagem do tutor até 2.000 caracteres; timeout 30 s / 15 s; no máximo 1 nova tentativa por turno (recusa não repete; modo restrito sem retry); testes com IA 10×, aprovação ≥ 9/10, proibidos 0/10 | P-09, aprovada em 2026-11-16 | 0016 |
| DA-29 | **Lista do validador** (ADR-0008, seção 3): formatos ampliados (dinheiro, horário, "24 horas", dias, datas, telefone, e-mail/site/@perfil, CEP, desconto e parcelamento, prazos); contexto operacional (data/hora) como fonte autorizada; expressões de V5 e V6 em arquivo versionado, com texto normalizado e regra de negação (até 3 palavras); **V7 nova: doses de medicamento reprovadas** (eco do tutor permitido) | PT-11, aprovada em 2026-11-16 (opção A + doses) | 0008 |
| DA-30 | **Pedido explícito de humano** (ADR-0007, item 1): lista de expressões em arquivo versionado, texto normalizado (minúsculas, sem acento e pontuação), regra de negação de 3 palavras; grupos: pedido de falar/conversar com pessoa, transferência, atendimento humano, recusa do robô, pedido de ligação e mensagem curta inteira (até 4 palavras); "vc" e "pfv" reconhecidos; frases de controle versionadas como testes | PT-10, aprovada em 2026-11-17 (opção A) | 0007 |
| DA-31 | **Conversa improdutiva** (ADR-0007, item 10): nas últimas 10 mensagens, três contagens (falhas seguidas, mensagem do tutor repetida, resposta do assistente repetida, por igualdade após normalização); oferece atendimento humano na 2ª ocorrência e encaminha automaticamente na 3ª (motivo `UNPRODUCTIVE_CONVERSATION`); turno válido zera as falhas; uma oferta por conversa; não roda em `WAITING_HUMAN`; resposta segura marcada em coluna própria da mensagem | PT-14, aprovada em 2026-11-17 (opção A) | 0007 |
| DA-32 | **Orquestração do turno entre módulos** (ADR-0017): o caso de uso do turno usa diretamente as portas de repositório e os objetos de domínio de `servicerequest` e `tutor`, numa única transação; casos de uso desses módulos só para entradas externas (endpoints da equipe); nenhum módulo acessa infraestrutura de outro | PT-02, aprovada em 2026-11-17 (opção A) | 0017 |
| DA-33 | **Intenções secundárias**: `AssistantTurn` ganha `secondaryIntents` (até 2); a principal segue a precedência da DA-24 (clínico antes do administrativo); `HandoffPolicy` considera principal e secundárias; intenção corrente e V4 usam só a principal; secundárias gravadas na mensagem do assistente | PT-06, aprovada em 2026-11-17 (opção B) | 0002 |
| DA-34 | **Desfecho da solicitação**: ao resolver ou cancelar, desfecho obrigatório de lista fixa (`SCHEDULED_BY_STAFF`, `INFORMATION_PROVIDED`, `HANDLED_OUTSIDE`, `NO_AVAILABILITY`, `CUSTOMER_GAVE_UP`, `NO_RESPONSE`, `DUPLICATE`, `OTHER`) + observação opcional de até 500 caracteres (obrigatória com `OTHER`), sem dados de saúde ou pessoais além do necessário; o assistente não lê o desfecho no MVP | PT-08, aprovada em 2026-11-17 (opção A) | 0011 |
| DA-35 | **Proveniência por campo e correspondência de pets** (ADR-0010): tabela própria (`subject` TUTOR/PET, `subject_id`, `field`, `status`, `conversation_id`, `confirmed_at`, `updated_at`); MENCIONADA e CONFIRMADA valem só para a conversa gravada; em conversa nova, CADASTRADA ou A_RECONFIRMAR é calculado por `confirmed_at` e pelo prazo de validade (P-08), sem gravar. Correspondência só entre os pets do tutor da conversa, por igualdade do nome normalizado (minúsculas, sem acento, espaços colapsados); nome sem correspondência, homônimos ou espécie divergente → o assistente pergunta e o pet não é vinculado; sem similaridade aproximada no MVP | PT-09, aprovada em 2026-11-17 (opção A) | 0010 |
| DA-36 | **Criação do tutor** (ADR-0010): o `Tutor` só é criado no primeiro turno em que o backend aceita um dado normalizado de tutor ou pet; a conversa é vinculada nesse momento; contato que já tem cadastro tem a conversa vinculada na abertura, pelo identificador do canal (minimização de dados) | PT-12, aprovada em 2026-11-17 (opção A) | 0010 |
| DA-37 | **Referência a solicitações anteriores** (ADR-0009): a camada operacional de uma conversa nova lista até 3 solicitações do mesmo tutor em `PENDING` ou `IN_PROGRESS`, só com tipo, data de abertura e status, marcadas "atendimento anterior — não reutilizar dados"; sem resumo, motivo, sintomas, preferências, mensagens ou solicitações encerradas; o assistente reconhece o pedido sem afirmar andamento ou prazo e oferece encaminhar; a conversa nova nunca altera a solicitação anterior | PT-15, aprovada em 2026-11-17 (opção A) | 0009 |
| DA-38 | **Idioma e formato** (parte técnica da P-10): sempre pt-BR (mensagem em outro idioma recebe resposta em português simples e oferta de atendimento da equipe); até 3 frases curtas (~500 caracteres) e no máximo uma pergunta por mensagem, sem contar os textos do sistema; texto simples, sem markdown (lista só para serviços ou horários, até 5 itens com "- "); sem emojis; tom cordial, objetivo e acolhedor, sem gírias, diminutivos ou excesso de exclamações; na primeira resposta da conversa, apresenta-se como assistente virtual da clínica (garantido pelo backend quando o modelo não se apresenta, exceto em P1/P2) | P-10 (parte do responsável), aprovada em 2026-11-17 (opção A) | — |
| DA-39 | **Modelo de informações da clínica e validade** (ADR-0003, revisão): formulário-base para clínica com petshop (classes A estável, B periódica, C volátil, D clínica, [modelo](../product/modelo-informacoes-clinica.md)); schema v2 com seções `review`, `emergency`, `petshop-hours`, `petshop` e campos novos; validade por seção (vencida = DESCONHECIDA, avaliada a cada consulta); feriados com data; preço por porte; "aberta agora" calculado pelo backend (`OpeningHours`) e enviado ao modelo; nada de classe C sem integração | Proposta do responsável, aprovada em 2026-11-17 | 0003 |

## 2. Decisões mantidas da proposta original

| ID | Decisão | ADR / seção da proposta |
|---|---|---|
| DM-01 | Arquitetura por módulo com princípios hexagonais; domínio sem Spring, JPA ou Spring AI; interfaces só onde há substituição real | 0001 / 2 |
| DM-02 | Stack: Java 25, Spring Boot 4.0.5, Spring AI 2.0.0-M4, OpenAI, Spring Data JPA/Hibernate, MySQL, Gradle, JUnit 5; sem upgrade sem verificação de compatibilidade | Proposta 1.5 |
| DM-03 | Remoção de áudio, transcrição, TTS, `SpeechModel` e controllers de experimentação da referência | Proposta 1.3 |
| DM-04 | Flyway; `ddl-auto=validate` | 0005 |
| DM-05 | Prompts externos `.st`; leitura e configuração em UTF-8 explícito | Proposta 4 |
| DM-06 | `Clock` injetável com `America/Sao_Paulo`; timestamps em UTC no banco | 0005 / Proposta 6.2 |
| DM-07 | Sem RAG, banco vetorial, embeddings, sumarização ou multiagentes | 0003, 0004 |
| DM-08 | Contexto em cinco camadas (identidade, institucional, tutor/pet, conversa, operacional) | Proposta 4 |
| DM-09 | Enum `Intent` com as 11 intenções do briefing; valor inválido → `OTHER`; intenção orienta, mas não impede mudança de assunto | Proposta 3.4 |
| DM-10 | Comando neutro `IncomingMessage`, sem interface de canal; identificador do contato vem do canal, nunca do texto | 0001 |
| DM-11 | API REST com DTOs próprios, Bean Validation e `ProblemDetail`; entidades nunca expostas | Proposta 6.1 |
| DM-12 | Logs sem conteúdo de mensagens ou dados pessoais; `org.springframework.ai` fora de `DEBUG`; chaves só por variável de ambiente | 0014 |
| DM-13 | Testes com IA real marcados (`@Tag("ai")`), executados só com `OPENAI_API_KEY`, em task separada | 0006 |
| DM-14 | Identificadores UUID tipados | Proposta 3.1 |
| DM-15 | Turno em duas transações: mensagem do tutor persistida **antes** da IA; chamada ao modelo **fora** de transação | Proposta 5 |
| DM-16 | Lock otimista na `Conversation` (comportamento no conflito: DA-21) | Proposta 5 |

## 3. Pendências

### 3.1 Dependem da clínica, de orientação jurídica ou de dado externo

| ID | O que precisa ser decidido | Quem fornece / aprova | Necessária na etapa | O que fica bloqueado enquanto não houver decisão |
|---|---|---|---|---|
| P-01 | **Política de emergência** (inclui o que dizer quando a clínica **não puder receber** o animal e a orientação curta de P2, diretrizes): a clínica atende emergências? Em que horário? Qual telefone ou parceiro indicar? Texto exato da orientação; se ela aparece em toda resposta de `WAITING_HUMAN` ou só quando há indício | Clínica (responsável técnico veterinário) | 4 (*placeholder*) / 7 (definitiva) | Texto definitivo de emergência (até lá, *placeholder* explícito); aceite da Etapa 4 com conteúdo real; **barreira G8** |
| P-02 | **Prazo esperado de atendimento humano, por nível de prioridade P1–P6** (diretrizes), e comportamento quando ele vence, fora do expediente e sem funcionário disponível | Clínica | 4 (valor de teste) / 7 (definitiva) | Valor do prazo e texto da orientação de prazo vencido (mecanismo implementável com valor de teste); **barreira G10** |
| P-03 | **Retenção e exclusão**: prazos por tipo de dado (mensagens, cadastro, solicitações) e processo de exclusão | Clínica, com orientação jurídica | 7 | Rotina de expurgo; procedimento de exclusão; **barreiras G4, G5** |
| P-04 | **Aviso de transparência** sobre uso de IA (texto e momento) e **condições de uso de provedores externos de IA**: dados enviados, retenção pelo provedor, transferência internacional | Clínica, com orientação jurídica; responsável técnico informa o que é enviado e as opções do provedor | 7 | Mecanismo de aviso (texto); uso de dados reais com a OpenAI; **barreiras G1, G3** |
| P-05 | **Prazo de expiração** de conversa inativa, por status (`OPEN`, `WAITING_HUMAN`) | Responsável pelo projeto, ouvindo a clínica | 2 (provisório) / 7 (definitivo) | Comportamento definitivo de "conversa nova" (implementação usa configuração obrigatória e valores de teste) |
| P-06 | **Múltiplas solicitações na mesma conversa** (ex.: consulta para um pet e informação sobre vacina): permitidas em paralelo? Uma "em andamento" por vez? | Clínica (como trabalham hoje) + responsável | 5 | Regra de "solicitação em andamento" no contexto operacional; aceite da Etapa 5. **Provisório na E5 (2026-11-17, só dados fictícios):** uma solicitação de agendamento em coleta por conversa; informação e atendimento humano em paralelo |
| P-07 | **Pedidos envolvendo vários pets** ("marcar para os dois") | Clínica | 5 | Relação solicitação × pet (um ou vários). Pode exigir migration; afeta o schema da Etapa 5. **Provisório na E5:** um pet por solicitação; "os dois" = dois pedidos, um de cada vez (sem migration) |
| P-08 | **Critérios para considerar uma informação confirmada**; prazo de validade de `CADASTRADA`; status mínimo exigido para registrar dados numa solicitação | Responsável pelo projeto + clínica | 5 | Transição MENCIONADA → CONFIRMADA; `submit()` da solicitação de agendamento. **Provisório na E5:** confirmado = o assistente repetiu o valor e o tutor concordou (o backend confere); CADASTRADA vale 365 dias; agendamento exige nome e espécie do pet confirmados na conversa |
| P-09 | ~~Modelo, parâmetros e resiliência~~ | **Decidida → DA-28** (ADR-0016) | — | — |
| P-10 | **Persona** (idioma e formato decididos na DA-38): **nome do assistente** (nome próprio × "assistente virtual da clínica"); **tratamento** (você × senhor/senhora); revisão dos textos padrão do sistema (resposta segura, encaminhamento, recebimento, oferta de atendimento humano, pedido registrado). Antes: idioma, formato e persona: responder sempre em pt-BR?; tamanho máximo da resposta; uso de emojis; nome do assistente; tratamento (você × senhor/senhora) | Clínica (persona, tratamento) + responsável (idioma, formato) | 2 (provisório) / 6 (final) | Texto final do `system-message.st` e dos textos padrão; aceite qualitativo da Etapa 6 |
| P-11 | **Informações institucionais**: identificação, horários por dia, feriados, atendimento fora do expediente, serviços e especialidades, banho e tosa, **se preços podem ser divulgados** e quais, formas de pagamento, políticas de cancelamento e falta, FAQ, tipos de atendimento que podem ser solicitados | Clínica | 3 (estrutura) / 7 (conteúdo real) | Conteúdo do YAML real; regra de preço (se a clínica não divulgar preços, nenhum valor monetário é autorizado); **barreira G9** |
| P-12 | **Situações exclusivas de atendimento humano**; encaminhar automaticamente × apenas oferecer em: sintomas, **sofrimento emocional, luto, vulnerabilidade financeira** (diretrizes); texto de recusa sobre medicamentos, doses e exames | Clínica (questionário Q8, Q12) | 4 (*placeholder*) / 7 (definitiva) | Condições 3 e 7 da `HandoffPolicy`. **Provisório (CT-19, 2026-11-17):** assunto sensível (eutanásia, doença grave ou terminal) = oferta; na IA real o modelo encaminhou em 10/10 |

| P-13 | **Dados obrigatórios** de uma solicitação de agendamento (pet, motivo, preferência, nome e contato do tutor) | Clínica (questionário Q5, Q6) | 5 | `missingFields()` / `submit()` de `APPOINTMENT`. **Provisório na E5:** pet (nome e espécie), motivo e nome do tutor; preferência opcional |
| P-14 | **Quem acompanha** as solicitações e conversas sinalizadas, com que frequência e por qual meio; **quem recebe reclamações e feedbacks** e se existe processo real de retorno ao tutor (sem processo, o assistente não promete contato) | Clínica (questionário Q10) | 4 (provisório) / 7 (definitiva) | Validade operacional do encaminhamento sem notificação; avaliação de necessidade futura de notificação |
| P-15 | Resposta do assistente quando a equipe **resolveu por fora** (ex.: confirmou por telefone) e o tutor volta a perguntar | Clínica | 5 (provisório) / 7 (definitiva) | Regra no prompt e cenário de teste correspondente. **Provisório na E5:** o assistente não lê o desfecho; diz que o pedido está com a equipe e que dia e horário só valem depois de confirmados; não afirma confirmação nem resolução |
| P-16 | **Finalidade e base legal** de cada tratamento de dados | Clínica, com orientação jurídica | 7 | **Barreira G2** |
| P-17 | **Perfis de acesso** (quem da equipe vê o quê) e mecanismo de autenticação individual | Clínica (perfis) + responsável (mecanismo) | 7 | Autorização e auditoria; **barreira G6** |
| P-18 | **Logs, backups e acesso ao banco** em ambiente real: onde roda, quem acessa, retenção de backups | Responsável pelo projeto (+ clínica, se a infraestrutura for dela) | 7 | **Barreira G7** |
| P-19 | **Aprovação das Diretrizes de Atendimento e Conduta** como texto institucional, incluindo as recomendações para a equipe humana | Clínica | 7 | Uso das diretrizes como política oficial; aceite da Etapa 7 |
| P-20 | **Critérios clínicos da hierarquia**: sinais de P1, critérios de P2, quando P3 exige profissional e a **lista de expressões de alerta** (DA-27) | **Equipe veterinária** (responsável técnico) | 4 (lista ilustrativa) / 7 (aprovada) | Critérios reais de prioridade; ambiente real (recusa iniciar sem lista aprovada) |

### 3.2 Pendências técnicas (responsável pelo projeto)

| ID | O que precisa ser decidido | Opções registradas | Necessária na etapa | O que fica bloqueado |
|---|---|---|---|---|
| PT-01 | ~~Montagem do histórico~~ | **Decidida → DA-18** | — | — |
| PT-02 | ~~Orquestração do turno entre módulos~~ | **Decidida → DA-32** (opção A, ADR-0017) | — | — |
| PT-03 | ~~Detalhes variáveis da solicitação~~ | **Decidida → DA-19** | — | — |
| PT-04 | ~~Organização de pastas~~ | **Decidida → DA-20** | — | — |
| PT-05 | ~~Mensagens simultâneas~~ | **Decidida → DA-21** | — | — |
| PT-06 | ~~Intenções secundárias~~ | **Decidida → DA-33** (opção B) | — | — |
| PT-07 | Limitação de taxa (*rate limiting*) por chave ou contato | Implementar na Etapa 7 × antes, se houver exposição fora de `localhost` | 7 (ou antes de expor) | Proteção de custo contra abuso |
| PT-08 | ~~Desfecho da solicitação~~ | **Decidida → DA-34** (opção A) | — | — |
| PT-09 | ~~Granularidade da proveniência e correspondência de pets~~ | **Decidida → DA-35** (opção A) | — | — |
| PT-10 | ~~Expressões de pedido explícito de humano~~ | **Decidida → DA-30** (opção A) | — | — |
| PT-11 | ~~Lista do validador~~ | **Decidida → DA-29** (opção A + doses) | — | — |
| PT-12 | ~~Momento de criação do registro do tutor~~ | **Decidida → DA-36** (opção A) | — | — |
| PT-13 | ~~Lista de expressões de alerta~~ | **Decidida → DA-27** (opção A) | — | — |
| PT-14 | ~~Limiares de conversa improdutiva~~ | **Decidida → DA-31** (opção A) | — | — |
| PT-15 | ~~Referência a solicitações anteriores~~ | **Decidida → DA-37** (opção A) | — | — |

## 4. Recomendações da revisão técnica não aprovadas nesta rodada

Registradas para não se perderem. **Não** fazem parte do escopo até decisão explícita.

| Recomendação (seção da revisão) | Situação |
|---|---|
| Duas chamadas ao modelo (classificar e depois redigir) — 9.2 | Não adotada; alternativa a reavaliar se a Etapa 6 medir inconsistência alta (ADR-0002) |
| Observações estruturadas `notes[]` para preservar informação livre — 9.3 | Não adotada; reavaliar com as métricas da Etapa 6 (ADR-0004) |
| Respostas institucionais por template no backend — 9.1 | Não adotada; possível evolução (ADR-0008) |
| Intenções secundárias — 9.2 | Em aberto: PT-06 |
| Rate limiting — 9.1-bis | Em aberto: PT-07 |
| Campo de desfecho da solicitação — 9.7 | Em aberto: PT-08 |
| Não enviar o telefone do tutor ao modelo (minimização) — 9.8 | Em aberto: verificado no item G3 da barreira (P-04) |
| Autor `STAFF` nas mensagens; idempotência por `externalMessageId`; serialização de rajadas — I6, I7, I8 | Fora do MVP: necessários no adaptador WhatsApp. Concorrência no MVP: DA-21 |
| Métricas por turno (intenção, encaminhamento, reprovação, tokens, latência, sem conteúdo) — 12 | Incluídas no roadmap como entrega da Etapa 6, por serem necessárias para medir os critérios de DA-10 e DA-12 |
| Nenhuma demonstração à clínica antes da Etapa 3 — I9 | Coberto por DA-17 (só dados fictícios); demonstração com conteúdo fictício é permitida |

## 5. Rastreabilidade com documentos anteriores

| Origem | Mapeamento |
|---|---|
| D1 | DA-01, DA-02 |
| D2 | DA-03 |
| D3 | DA-04 |
| D4 | DA-05 |
| D5 | DA-06 |
| D6 | DA-07 |
| D7 | DA-08 |
| D8 | DA-09, DA-20 (layout) |
| D9 | DA-10 |
| C1, C2, C7, C12 | P-11 |
| C3, C4 | P-12 |
| C5 | P-01 |
| C6 | P-13 |
| C8 | P-02 |
| C9 | P-10 |
| C10 | P-03, P-04 |
| C11 | P-02, P-14 |
| C13 | P-05 (mecanismo: DA-13) |
| C14 | P-15 |
| C15 | P-04 |
| C16 | P-07 |
| I1, I12 | DA-28 (ADR-0016) |
| I2 | DA-13, P-05 |
| I3 | P-06 |
| I4 | PT-09 |
| I5 | PT-12 |
| I6, I7, I8 | Seção 4; DA-21 |
| I10 | PT-06 |
| I11 | DA-03 (formato tipado) |
| I13 | Matriz de riscos (R-13) |
| I14 | P-10 |
| ADR 004 (`ChatMemory`) da proposta original | DA-18 (ADR-0015) |
| Teste de repositório em memória (revisão 11.5) | Resolvido por DA-05 |
