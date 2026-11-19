# PRD — MVP da plataforma de atendimento

> Versão 1 — 2026-11-18. Base: [Product Blueprint](product-blueprint.md) e decisões DA-42 a DA-51 do [registro](../../plans/registro-de-decisoes.md).
> Os requisitos do assistente que já estão implementados (diretrizes, prioridade, validação, proveniência) continuam em [requisitos-diretrizes](../../specs/requisitos-diretrizes.md) e [business-rules](../../architecture/business-rules.md). Este PRD os referencia, sem repeti-los.

**Convenções:**

- `RF-Sxx` = requisito funcional; `RNF-Sxx` = não funcional; `RN-Sxx` = regra de negócio.
- **Etapa** = etapa do [roadmap SaaS](../../plans/saas-roadmap.md) em que o item é entregue.
- Todos os requisitos valem por clínica (tenant). Nenhum dado de uma clínica aparece para outra.

---

## 1. Perfis de usuário

Os perfis são **modelos editáveis** (DA-49), não papéis fixos. A clínica pode criar outros a partir do catálogo de permissões.

| Perfil inicial | Trabalha em | Permissões típicas |
|---|---|---|
| Administrador | Tudo | Configurar a clínica, a equipe e os perfis; publicar a configuração; ver a auditoria |
| Gerente | Todos os setores | Ver e atribuir em todos os setores; ver indicadores; editar a configuração sem publicar |
| Secretária (recepção) | Recepção | Caixa de entrada; clientes e pets; solicitações da recepção; transferir para outros setores |
| Veterinário | Clínica | Solicitações da clínica; conversas encaminhadas a ele; notas internas |
| Atendente do pet shop | Pet shop | Solicitações do pet shop; dados do pet necessários ao serviço |
| Tosador | Pet shop (banho e tosa) | Solicitações de banho e tosa atribuídas a ele |

**Fora da aplicação:**

- O **tutor** não tem conta. Fala pelo WhatsApp.
- A **equipe da plataforma** (nós) opera a instalação, mas não tem acesso ao conteúdo das clínicas no uso normal (RN-S12).

## 2. Requisitos funcionais

### 2.1 Plataforma: clínica, identidade e auditoria

| ID | Requisito | Etapa |
|---|---|---|
| RF-S01 | Cadastrar uma clínica (tenant) com nome, fuso horário e administrador inicial. No MVP, o cadastro é feito pela equipe da plataforma | S1 |
| RF-S02 | Login individual por e-mail e senha; sair; trocar a senha; redefinir a senha por e-mail | S1 |
| RF-S03 | O administrador convida, edita e desativa funcionários. A desativação bloqueia o acesso e preserva a autoria do histórico | S1 |
| RF-S04 | Perfis editáveis: nome, permissões do catálogo, escopo de setor ("meus setores" ou "todos"). A clínica começa com os seis modelos da seção 1 | S1 |
| RF-S05 | Funcionário com cargo (texto), registro profissional opcional (ex.: CRMV), setores em que trabalha e perfil | S1 |
| RF-S06 | Disponibilidade: o próprio funcionário se marca "disponível" ou "ausente". Ausentes não recebem atribuições automáticas nem aparecem como sugestão | S2 |
| RF-S07 | Auditoria: login; alteração de configuração; publicação; mudança de perfil e de permissão; desativação; assumir, transferir e encerrar; acesso a cadastro de cliente; exportação e eliminação. Cada registro guarda quem, quando, o quê e um resumo, nunca o conteúdo das mensagens | S1 |
| RF-S08 | Tela de auditoria filtrável por pessoa, tipo e período, só para quem tem a permissão | S3 |

### 2.2 Configuração da clínica

| ID | Requisito | Etapa |
|---|---|---|
| RF-S10 | Assistente de configuração inicial em etapas: dados da clínica; horários; setores; serviços e preços; perguntas frequentes; equipe; revisão e publicação. Cada etapa pode ser salva e retomada | S3 |
| RF-S11 | Editar as informações da clínica pelo [modelo de informações](../modelo-informacoes-clinica.md) (seções, tipos, "válido até"), em formulários. Nenhuma edição de YAML, JSON ou prompt | S3 |
| RF-S12 | Setores: criar, renomear, ativar e desativar. Recepção é obrigatória; Clínica e Pet shop vêm prontos | S1 (cadastro), S3 (tela) |
| RF-S13 | Serviços: nome, setor responsável, descrição, preço (opcional, respeitando "divulgar preços"), dados que o assistente deve coletar | S3 |
| RF-S14 | Regras de encaminhamento em linguagem de negócio: "tipo de pedido ou serviço → setor". Toda regra tem setor de destino; sem regra, vai para a Recepção | S3 |
| RF-S15 | Rascunho e publicação: toda edição vai para um rascunho. Publicar cria uma versão nova e arquiva a anterior. O assistente usa só a versão publicada | S3 |
| RF-S16 | Histórico de versões: ver quem publicou, quando e o que mudou; restaurar uma versão anterior como rascunho | S3 |
| RF-S17 | Simulador: conversar com o assistente usando o **rascunho**, sem WhatsApp e sem criar solicitações reais. Mostra intenção, prioridade, setor escolhido e fontes usadas | S3 |
| RF-S18 | Avisos de validade: seções vencidas ou a vencer em 7 dias aparecem na visão geral do administrador | S3 |
| RF-S19 | Importar a configuração do YAML atual (`config/clinic-knowledge.*.yml`) como rascunho, para migrar a clínica fictícia | S3 |

### 2.3 Atendimento (caixa de entrada)

| ID | Requisito | Etapa |
|---|---|---|
| RF-S20 | Lista de conversas com tutor, pet, intenção, prioridade clínica, estado, setor, responsável e tempo de espera. Ordenada por prioridade e depois por espera | S2 |
| RF-S21 | Conversa aberta: histórico completo, com as mensagens da IA identificadas, e painel do tutor e do pet (com proveniência) e das solicitações | S2 |
| RF-S22 | **Assumir** uma conversa: o assistente para de responder nela, e a próxima resposta automática ao tutor diz quem assumiu. Dois funcionários não podem assumir ao mesmo tempo (RN-S05) | S2 |
| RF-S23 | **Responder manualmente**: o funcionário escreve e a mensagem sai pelo canal com o seu nome registrado | S2 (canal de teste), S4 (WhatsApp) |
| RF-S24 | **Devolver ao assistente**: a conversa volta a ser atendida pela IA (o `/devolver` de hoje), com o motivo registrado | S2 |
| RF-S25 | **Transferir** para outro setor ou funcionário, com motivo obrigatório | S2 |
| RF-S26 | **Nota interna**: visível só para a equipe, nunca enviada ao tutor nem ao modelo | S2 |
| RF-S27 | Encerrar a conversa com motivo | S2 |
| RF-S28 | Uma nova mensagem com sinal de possível emergência em conversa assumida **sobe a prioridade e destaca a conversa**, mesmo com a IA parada (RN-S03) | S2 |
| RF-S29 | Atualização em tempo real da lista e da conversa aberta, sem recarregar a página | S2 |

### 2.4 Solicitações e filas por setor

| ID | Requisito | Etapa |
|---|---|---|
| RF-S30 | Tela única de Solicitações com abas por setor ativo. Cada um vê só as abas dos seus setores, salvo escopo "todos" | S2 |
| RF-S31 | Colunas: tipo, tutor, pet, prioridade, estado, responsável, tempo desde o recebimento, próxima ação | S2 |
| RF-S32 | Filtros: prioridade, estado, responsável ("comigo", "sem responsável"), tipo | S2 |
| RF-S33 | Atribuir a si ou a outro funcionário do setor; transferir para outro setor com motivo | S2 |
| RF-S34 | Mudar estado e encerrar com resultado. Usa os resultados atuais (`SCHEDULED_BY_STAFF`, `INFORMATION_PROVIDED` etc.) | S2 |
| RF-S35 | Notas internas e histórico de eventos da solicitação | S2 |
| RF-S36 | Banho e tosa: registrar pet, serviço, data ou período desejado, responsável, estado e pendências, sem agenda | S2 |
| RF-S37 | O assistente cria a solicitação já com o setor escolhido pelas regras (RF-S14). Sem regra, vai para a Recepção | S5 |

### 2.5 Clientes e pets

| ID | Requisito | Etapa |
|---|---|---|
| RF-S40 | Ficha do tutor: dados básicos, contatos, pets, conversas, solicitações e histórico de relacionamento | S2 |
| RF-S41 | Editar dados do tutor e do pet. A edição pela equipe marca a proveniência como CADASTRADA (ADR-0010) | S2 |
| RF-S42 | Buscar por nome, telefone ou nome do pet | S2 |
| RF-S43 | Pedidos do titular (LGPD): exportar os dados de um tutor e eliminá-los ou anonimizá-los, com registro na auditoria | S6 |
| RF-S44 | Informação sensível (notas clínicas, motivo de solicitação clínica) visível só a quem tem a permissão. O pet shop vê os dados do pet, não as notas clínicas | S2 |

### 2.6 Canal WhatsApp

| ID | Requisito | Etapa |
|---|---|---|
| RF-S50 | Receber mensagens de texto pelo webhook da Cloud API, identificar a clínica pelo número de destino e o contato pelo número de origem | S4 |
| RF-S51 | Mensagem duplicada (mesmo id da Meta) não gera turno novo | S4 |
| RF-S52 | Enviar respostas do assistente e da equipe pela *outbox*, com nova tentativa e status (enviada, entregue, lida, falhou) | S4 |
| RF-S53 | Janela de 24 horas: fora dela, resposta livre é bloqueada na tela, com o aviso "fora da janela do WhatsApp". Modelos aprovados ficam fora do MVP | S4 |
| RF-S54 | Mensagem que não é texto (áudio, imagem, figurinha) recebe uma resposta aprovada pedindo texto, e a equipe vê que chegou mídia | S4 |
| RF-S55 | Falha prolongada no envio aparece na conversa para a equipe | S4 |

### 2.7 Assistente

O comportamento atual (intenção, prioridade, sinais de alerta, validação, proveniência, encaminhamento resolutivo, assuntos sensíveis, formato) continua valendo. O SaaS acrescenta:

| ID | Requisito | Etapa |
|---|---|---|
| RF-S60 | Usar a configuração publicada da clínica do tenant, não um arquivo | S3 |
| RF-S61 | Escolher o setor da solicitação pelas regras (RF-S14) | S5 |
| RF-S62 | Textos de encaminhamento conforme o estado real: "encaminhado para a Recepção" até alguém assumir; "Ana, da Recepção, está com o seu pedido" depois. Nenhum texto diz "avisei" ou "notifiquei" (RN-S01) | S5 |
| RF-S63 | Não responder em conversa assumida. Mensagens novas ainda são avaliadas para prioridade (RF-S28) | S2 |
| RF-S64 | Nível de ativação por clínica (DA-48): 1 = só organiza; 2 = responde só informações da clínica; 3 = completo | S5 |

### 2.8 Visão geral

| ID | Requisito | Etapa |
|---|---|---|
| RF-S70 | "O que eu preciso fazer agora": atribuídos a mim, urgentes dos meus setores, conversas aguardando funcionário | S2 |
| RF-S71 | Indicadores simples do dia e da semana: tempo até a primeira resposta, aguardando funcionário, solicitações pendentes por setor, tempo até atribuição e até resolução | S5 |
| RF-S72 | Para o administrador: seções vencidas (RF-S18) e rascunho não publicado | S3 |

## 3. Regras de negócio

| ID | Regra |
|---|---|
| RN-S01 | Nunca dizer ao tutor que alguém foi avisado ou notificado. No MVP, a equipe só é avisada dentro da aplicação (DA-47) |
| RN-S02 | Conversa ≠ solicitação ≠ agendamento. Uma conversa pode ter várias solicitações; nenhuma é agendamento confirmado (DA-07) |
| RN-S03 | A prioridade clínica é reavaliada a cada mensagem do tutor, em qualquer estado, inclusive com a conversa assumida. Ela só sobe automaticamente; rebaixar exige uma pessoa e fica registrado (ADR-0018) |
| RN-S04 | Prioridade clínica, prioridade operacional, estado da conversa e estado da solicitação são campos separados. Insistência, valor comercial e quantidade de mensagens não mudam a prioridade |
| RN-S05 | Uma conversa tem no máximo um responsável humano por vez. Uma solicitação tem no máximo um responsável e exatamente um setor |
| RN-S06 | Transferir exige motivo. A transferência fica no histórico, com quem fez, de onde e para onde |
| RN-S07 | Funcionário desativado não pode ser atribuído. As atribuições abertas dele voltam para o setor, sem responsável, e o gerente vê isso na visão geral |
| RN-S08 | O assistente lê só a versão publicada. Rascunhos nunca chegam ao tutor |
| RN-S09 | Informação vencida (fora do "válido até") é tratada como desconhecida (DA-39) |
| RN-S10 | Nota interna nunca vai ao tutor nem ao modelo |
| RN-S11 | Quem tem escopo "meus setores" não vê solicitações nem conversas de outros setores, salvo as que já assumiu |
| RN-S12 | A equipe da plataforma não acessa o conteúdo das clínicas no uso normal. Acesso de suporte exige pedido da clínica e fica registrado na auditoria da clínica |
| RN-S13 | Dados de desenvolvimento e teste são sempre fictícios (DA-17) |

## 4. Casos de uso principais

O passo a passo de cada um está nos [fluxos operacionais](fluxos-operacionais.md).

| UC | Ator | Resumo | Critério de aceitação |
|---|---|---|---|
| UC-01 | Tutor | Pergunta o horário pelo WhatsApp | Resposta com o horário publicado e válido, sem solicitação aberta |
| UC-02 | Tutor | Pede uma consulta | Solicitação APPOINTMENT na Recepção com pet, motivo e período; tutor informado de que é um pedido, não um agendamento |
| UC-03 | Tutor | Relata sinal de emergência | Prioridade P1, conversa no topo, orientação de emergência conforme o horário; nunca promessa de retorno |
| UC-04 | Recepção | Assume uma conversa e responde | IA para; resposta sai com o nome do funcionário; tutor vê quem assumiu |
| UC-05 | Recepção | Transfere para a Clínica | Solicitação muda de setor com motivo; aparece na aba Clínica; histórico registra |
| UC-06 | Veterinário | Resolve uma solicitação clínica | Atribui a si, anota, encerra com resultado; tempo até resolução medido |
| UC-07 | Tosador | Trabalha um pedido de banho | Vê na aba Pet shop, com os dados do pet e sem as notas clínicas; muda o estado |
| UC-08 | Administrador | Muda um preço | Edita no rascunho, testa no simulador, publica; o assistente passa a usar o novo preço; a auditoria registra |
| UC-09 | Administrador | Cadastra um funcionário | Convite enviado; perfil e setores definidos; primeiro login funciona |
| UC-10 | Administrador | Desativa um funcionário | Acesso bloqueado; atribuições abertas voltam ao setor; autoria do histórico preservada |
| UC-11 | Tutor | Pede para falar com uma pessoa | Pedido reconhecido de forma determinística; encaminhado sem insistência; texto honesto sobre o que acontece |
| UC-12 | Tutor | Pede eutanásia ou relata perda | Tom acolhedor; nenhuma oferta comercial; encaminhado à Clínica com prioridade adequada (CT-19) |
| UC-13 | Tutor | Faz uma reclamação | Encaminhada à Recepção ou à gerência conforme a regra; sem justificativa automática |
| UC-14 | Titular | Pede a exclusão dos dados | Administrador elimina ou anonimiza; auditoria registra; dado some das telas e da IA |

## 5. Requisitos não funcionais

| ID | Requisito |
|---|---|
| RNF-S01 | **Isolamento entre clínicas:** toda consulta filtrada pela clínica da requisição; acesso cruzado devolve 404; teste com duas clínicas em todo teste de API (DA-43) |
| RNF-S02 | **Segurança:** senha com hash forte; bloqueio após tentativas; sessão com cookie seguro e CSRF; segredos fora do repositório; webhook da Meta verificado por assinatura |
| RNF-S03 | **Responsivo:** a caixa de entrada e as solicitações funcionam no computador da recepção e no celular (largura mínima de 360 px) |
| RNF-S04 | **Tempo real:** mudança na fila aparece em outra sessão em até 3 s, em condições normais |
| RNF-S05 | **Idempotência:** mensagem recebida duas vezes gera um turno; envio repetido não duplica a mensagem ao tutor |
| RNF-S06 | **Resiliência:** falha do modelo ou da Meta não perde mensagem; a equipe continua trabalhando pela caixa de entrada (princípio de produto) |
| RNF-S07 | **Desempenho do turno:** os limites atuais (DA-28: timeout de 30 s e 15 s, no máximo uma nova tentativa) continuam; latência medida por turno |
| RNF-S08 | **Logs:** sem conteúdo de mensagem nem dado pessoal; identificadores e métricas apenas |
| RNF-S09 | **Testes:** unitários, de caso de uso, de integração com Testcontainers, de isolamento entre clínicas, de permissão, de contrato do webhook e de IA real opcional (DA-05, DA-28) |
| RNF-S10 | **Fronteiras de módulo** verificadas por teste de arquitetura (ADR-0020; ferramenta em PT-16) |
| RNF-S11 | **Acessibilidade:** navegação por teclado na caixa de entrada; contraste AA; prioridade nunca indicada só por cor |
| RNF-S12 | **Idioma:** interface em português do Brasil |

## 6. Restrições

- Mesma pilha do repositório: Java 25, Spring Boot 4.0.5, Spring AI 2.0.0-M4, MySQL, Flyway, Gradle. Nada é atualizado sem justificativa.
- Front-end em React com TypeScript (Vite), servido pelo Spring (DA-44).
- Monólito modular, um banco e um schema (DA-43).
- WhatsApp só pela API oficial (DA-46). Sem bibliotecas não oficiais.
- Nada de dado real antes da barreira (ADR-0014) e da revisão jurídica (DA-50).
- O modelo nunca grava no banco (DA-02).

## 7. Fora do MVP

Tudo o que está na seção 10.7 do briefing e no [Blueprint](product-blueprint.md#7-escopo-do-mvp).
