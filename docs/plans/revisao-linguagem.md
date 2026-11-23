# Revisão de linguagem da interface — inventário e proposta

> 2026-11-23. **Aprovada e aplicada em 2026-11-23** (blocos L1 a L11 da seção 5). Decisões D-L2 (selo "tutor ainda
> não respondeu") e o texto final ao tutor (P-10) seguem pendentes.
>
> Original: Base: varredura de `web/src` (telas, rótulos,
> avisos, estados vazios), das mensagens de erro do backend, da planilha exportada, da auditoria e dos textos enviados
> ao tutor (`application.yml`). Cada nome foi conferido contra o comportamento real do sistema.

**Regra desta revisão:** muda só o que a pessoa lê. Nomes internos (enums, colunas, rotas, campos da API) continuam
iguais, e nenhuma regra de negócio muda.

---

## 1. O que a varredura encontrou

### 1.1 Termos ambíguos ou inadequados

| # | Termo atual | Onde | Problema |
|---|---|---|---|
| A1 | "Aguardando a equipe" | Estado da conversa, cartão do Início, indicador | Não diz que **ninguém assumiu**. Também pode parecer que alguém já está atendendo e vai responder |
| A2 | "Pedidos sem dono" / "Sem dono" | Cartão do Início, aba de Solicitações | Informal, e "pedido" se confunde com compra no pet shop |
| A3 | "Comigo" | Cartão do Início, abas de Conversas e Solicitações | Curto demais: não diz que é o que está **sob sua responsabilidade** |
| A4 | "Pedido" / "pedidos" no lugar de "solicitação" | 28 ocorrências: Início, Conversas, Solicitações, avisos, estados vazios, Configuração | Mistura com o pedido comercial que vai existir no pet shop (E6) |
| A5 | "Clientes e pets", "cliente" | Menu, busca, ficha, Auditoria | Na operação veterinária a pessoa é o **tutor** |
| A6 | "Atendimento" (menu) | Menu | A tela é a lista de **conversas**; "atendimento" também é usado para "atendimento humano" e para o trabalho do funcionário |
| A7 | "Completo" (nível 3) | Início ("Assistente no nível 3 (Completo)") | Sugere que o assistente faz tudo; ele não agenda, não diagnostica e não confirma horário |
| A8 | "Organiza", "Informa" (níveis 1 e 2) | Início | Verbos soltos; não dizem o que o tutor recebe |
| A9 | "Assumida" | Estado da conversa | Não diz por quem nem que está em atendimento |
| A10 | "Resolvida" × "Encerrada" × "Concluída" | Estados de solicitação e conversa, planilha | Três palavras para fins parecidos |
| A11 | "Fila: Imediata / Alta / Baixa" | Selos (D9) | "Fila" é jargão; a equipe pensa em "prioridade" |
| A12 | "Prioridade" (botão) | Conversa | O botão agora muda risco **e** prioridade; o nome não diz que é uma ação |
| A13 | "Atribuir a mim" (solicitação) × "Assumir" (conversa) | Botões | Dois verbos para o mesmo gesto: ficar responsável |
| A14 | "Iniciar" | Solicitação | Não diz o que começa |
| A15 | "Resolver ou cancelar" | Solicitação | Usa "resolver", e o estado final se chama "Resolvida" (ver A10) |
| A16 | "Respostas prontas" | Conversa, Configuração | O app do WhatsApp Business, que a clínica já usa, chama de **"Respostas rápidas"** |
| A17 | "Histórico" (aba da Configuração) × "Histórico" (da solicitação) × "Auditoria" | Configuração, Solicitação, menu | A mesma palavra para coisas diferentes |
| A18 | "Testar", "Importar" | Abas da Configuração | Não dizem testar o quê, importar o quê |
| A19 | "Contato sem nome" | Listas | É um tutor sem nome cadastrado |
| A20 | "Sistema" (autor) | Auditoria, histórico da solicitação | Na maioria das vezes foi o assistente |
| A21 | "Tempo até alguém pegar o pedido" | Indicador | Informal e com "pedido"; além disso mede a solicitação, não a conversa (R9 da revisão pré-piloto) |

### 1.2 Textos que prometem o que o sistema não faz

| # | Texto | Realidade | Gravidade |
|---|---|---|---|
| P1 | Botão Ausente: "Você está ausente: não recebe atribuições" e dica "Ausentes não recebem atribuições" | A disponibilidade **não muda nada** hoje: não existe atribuição automática. É o item E10 da revisão pré-piloto | Alta: induz a equipe a achar que está protegida |
| P2 | Ao tutor: "Seu **pedido** ficou registrado para a equipe da clínica" | É uma solicitação, não uma compra | Média |

### 1.3 Códigos internos visíveis

| # | O que aparece | Onde | Proposta |
|---|---|---|---|
| C1 | `[EXPLICIT_REQUEST] Oi, sou…` | Resumo das solicitações antigas | Já corrigido na origem (H3); faltam os registros antigos: a tela traduz o prefixo `[CÓDIGO]` como rede de segurança |
| C2 | `P2_URGENT -> P1_POSSIBLE_EMERGENCY`, `regra -> HIGH`, `PENDING -> CANCELLED (DUPLICATE)`, `COMPLAINT -> ADMINISTRATIVE_INFO`, `novo perfil 3f2a…` (id) | Resumos da Auditoria | Gravar o resumo já em português, com os nomes da interface; perfil pelo nome |
| C3 | `Conversa: 'responder (assuma a conversa antes)' não é permitido a partir de OPEN`, `departmentId: setor inexistente`, `Funcionário não encontrado: <id>` | Avisos de erro (o aviso mostra a mensagem do servidor) | Texto de erro para pessoa, sem estado interno, campo técnico ou id; o detalhe técnico fica no log |
| C4 | `não enviada (erro 131026)` | Situação da resposta no WhatsApp | Frase curta por erro conhecido ("número não está no WhatsApp", "fora das 24 horas"); o código só na dica |
| C5 | `WHATSAPP_APP_SECRET`, `WHATSAPP_TOKEN_KEY` | Tela do WhatsApp (só administrador) | "Falta configurar no servidor. Peça ao suporte técnico." e o nome da variável só na dica |
| C6 | `[Imagem enviada sem texto]`, `[Áudio transcrito] …` | Mensagem do tutor na conversa | Manter: o colchete marca o que o sistema escreveu. Trocar só "transcrito" por "Áudio (transcrição automática)" |

### 1.4 Conceitos que o pedido sugere, mas o sistema não tem

| Sugestão | Situação | Decisão proposta |
|---|---|---|
| Conversa "Aguardando resposta do tutor" | Não existe esse estado. A conversa só sabe se está com o assistente, aguardando, em atendimento ou encerrada | **Não criar agora** (mudaria regra). Opção futura: um selo calculado ("tutor ainda não respondeu") quando a última mensagem é da equipe (D-L2) |
| Solicitação "Atribuída" como estado | O responsável é um campo à parte; o estado continua "pendente" | A tela **combina** estado e responsável: "Sem responsável", "Com Ana", "Em andamento com Ana" (seção 2.5) |
| Solicitação "Aguardando tutor" | Existe: `COLLECTING`, quando o assistente ainda coleta dados para o agendamento | Renomear para "Aguardando dados do tutor" |

---

## 2. Proposta de nomenclatura

Formato: **atual → proposto** · onde aparece · motivo e benefício.

### 2.1 Menu

| Atual | Proposto | Onde | Motivo e benefício |
|---|---|---|---|
| Início | **Início** | Menu | Já é claro |
| Atendimento | **Conversas** | Menu, título da tela, voltar | Diz o que se encontra (A6); libera "atendimento" para o trabalho da equipe |
| Solicitações | **Solicitações** | Menu | É o termo do vocabulário |
| Clientes e pets | **Tutores e pets** | Menu, busca, ficha, estados vazios | Vocabulário veterinário (A5) |
| Configuração | **Configurações** | Menu, título | Forma usual em aplicativos |
| Equipe | **Equipe** | Menu | Claro |
| Auditoria | **Registro de atividades** | Menu, título | "Auditoria" soa como fiscalização; a tela mostra quem fez o quê e quando (A17) |

### 2.2 Início

| Atual | Proposto | Onde | Motivo e benefício |
|---|---|---|---|
| "O que eu preciso fazer agora?" | **mantém** | Cabeçalho | Já é orientado à ação |
| Comigo | **Minhas pendências** + linha de apoio "Conversas e solicitações sob sua responsabilidade" | Cartão | Diz o conteúdo (A3) |
| Nada com você. | **Nada sob sua responsabilidade agora.** | Estado vazio | Consistente com o título |
| Aguardando a equipe | **Aguardando atendimento** + apoio "Ninguém da equipe assumiu ainda" | Cartão | Diz que ninguém assumiu (A1) |
| Ninguém esperando. Bom trabalho! | **Nenhuma conversa aguardando atendimento.** | Estado vazio | Mais neutro; sem elogio automático |
| Pedidos sem dono | **Solicitações sem responsável** | Cartão | A2, A4 |
| Todos têm responsável. | **Todas as solicitações têm responsável.** | Estado vazio | Completa o sentido |
| Últimos 7 dias | **mantém** | Indicadores | |
| Mensagens recebidas | **mantém** | | |
| Conversas respondidas | **mantém** | | |
| Só pelo assistente | **Respondidas só pelo assistente** | | Diz o que é contado |
| Tempo até a 1ª resposta | **Tempo até a primeira resposta** + apoio "do assistente ou da equipe" | | Sem abreviação; diz quem responde |
| Pedidos abertos na semana | **Solicitações abertas na semana** | | A4 |
| Pedidos resolvidos | **Solicitações concluídas** | | A4, A10 |
| Operação da equipe | **Atendimento da equipe** | Cartão | Mais natural |
| Aguardando a equipe agora | **Aguardando atendimento agora** | | A1 |
| Tempo até alguém pegar o pedido | **Tempo até a solicitação ter responsável** | | A21: diz exatamente o que mede |
| Tempo até resolver | **Tempo até concluir** | | A10 |
| Pedidos abertos: {setor} | **Abertas em {setor}** | | A4 |
| "Assistente no nível 3 (Completo): …" | **"Modo do assistente: {nome do modo}."** + frase (2.7) | | A7 |
| Tempos: mediana dos pedidos… | **Tempos: valor do meio das solicitações abertas nos últimos 7 dias.** | Nota | "Mediana" é termo técnico |

### 2.3 Conversas

| Atual | Proposto | Onde | Motivo e benefício |
|---|---|---|---|
| Abas: Aguardando · Comigo · Todas | **Aguardando atendimento · Meus atendimentos · Todas** | Abas | A1, A3. No celular cabe em duas linhas; abreviar para "Aguardando · Meus · Todas" só se não couber. **23/11:** abreviado na lista de conversas (o nome longo cortava as abas); o nome completo fica na dica |
| Com o assistente | **Assistente atendendo** | Estado | Diz quem está atendendo |
| Aguardando a equipe | **Aguardando atendimento** + dica "Ninguém da equipe assumiu ainda. O assistente responde só dúvidas da clínica." | Estado | A1 |
| Assumida / "Assumida por Ana" | **Em atendimento por Ana** | Estado, listas | A9 |
| Encerrada | **mantém** | Estado | |
| Assumir | **Assumir atendimento** | Botão (atalho A) | Verbo e objeto |
| Transferir | **Transferir para outro setor** | Botão | Diz o destino |
| Devolver ao assistente | **mantém** + dica "O assistente volta a responder o tutor nesta conversa." | Botão | "Reativar assistente" daria a entender que ele estava desligado na clínica inteira |
| Prioridade | **Mudar prioridade** | Botão | A12 |
| Encerrar | **Encerrar conversa** | Botão e confirmação | Verbo e objeto |
| "Encerrar esta conversa? Os pedidos continuam abertos." | **"Encerrar esta conversa? As solicitações dela continuam abertas."** | Confirmação | A4 |
| Painel: Risco clínico / Prioridade na fila | **Risco clínico / Prioridade de atendimento** | Painel | A11 |
| Automática | **Automática (pelo risco e pela espera)** | Painel | Diz a regra |
| "Para responder, assuma a conversa." | **"Para responder ao tutor, assuma o atendimento."** | Caixa de resposta | Consistente com o botão |
| "{Ana} está com esta conversa." | **"Esta conversa está em atendimento por Ana."** | Caixa de resposta | Consistente com o estado |
| Pedidos desta conversa | **Solicitações desta conversa** | Painel lateral | A4 |
| Respostas prontas | **Respostas rápidas** | Botão e dica | A16 |
| Contato sem nome | **Tutor sem nome cadastrado** | Listas | A19 |
| Avisos: Conversa assumida · Conversa transferida · Conversa devolvida ao assistente · Conversa encerrada · Mensagem enviada · Nota salva · Prioridade da fila fixada · Fila voltou para a regra · Risco clínico alterado | **Atendimento assumido · Conversa transferida · Conversa devolvida ao assistente · Conversa encerrada · Mensagem enviada · Nota salva · Prioridade de atendimento definida · Prioridade de atendimento voltou a ser automática · Risco clínico alterado** | Avisos | Consistência com os novos nomes |

### 2.4 Selos de prioridade e risco

| Atual | Proposto | Motivo e benefício |
|---|---|---|
| Fila: Imediata / Alta / Baixa / (fixada) | **Prioridade imediata / Prioridade alta / Prioridade baixa / (definida pela equipe)** | A11 |
| Possível emergência / Urgente / Acompanhamento clínico / Risco não avaliado | **mantém** | Termos do protocolo; aprovados na D9 |

### 2.5 Solicitações

| Atual | Proposto | Onde | Motivo e benefício |
|---|---|---|---|
| Abas: Comigo · Sem dono · {setores} | **Minhas · Sem responsável · {setores}** | Abas | A2, A3 |
| Em aberto · Encerradas | **Abertas · Encerradas** | Filtro | Concordância com "solicitações" |
| Estado `COLLECTING` "Coletando dados" | **Aguardando dados do tutor** | Selo | Diz quem precisa agir |
| `PENDING` "Pendente" | **Sem responsável** (sem responsável) ou **Com Ana** (com responsável) | Selo combinado com o responsável | Separa atribuição de andamento, sem mudar a regra |
| `IN_PROGRESS` "Em andamento" | **Em andamento com Ana** | Selo | Idem |
| `RESOLVED` "Resolvida" | **Concluída** | Selo, filtro, planilha | A10 |
| `CANCELLED` "Cancelada" | **mantém** | | |
| Atribuir a mim | **Assumir solicitação** | Botão | A13: o mesmo verbo da conversa |
| Iniciar | **Marcar em andamento** | Botão | A14 |
| Resolver ou cancelar | **Concluir ou cancelar** | Botão e painel | A15 |
| Encerrar o pedido | **Concluir ou cancelar a solicitação** | Título do painel | A4 |
| Transferir | **Transferir para outro setor** | Botão | Igual à conversa |
| Copiar resumo | **mantém** | | |
| Baixar planilha dos pedidos | **Baixar planilha das solicitações** | Link | A4 |
| Histórico (da solicitação) | **O que aconteceu** | Seção | A17 |
| Avisos: Pedido atribuído a você · em andamento · transferido · resolvido · cancelado · "Inicie o pedido antes" | **Solicitação assumida por você · marcada em andamento · transferida · concluída · cancelada · "Marque a solicitação em andamento antes"** | Avisos | A4 |
| Nenhum pedido aqui. · Escolha um pedido na lista. | **Nenhuma solicitação aqui. · Escolha uma solicitação na lista.** | Estados vazios | A4 |
| "(não é horário confirmado)" | **mantém** | Preferência de horário | Protege a regra do agendamento |
| Resultados: "Informação dada", "Resolvido por fora (telefone, balcão)", "Duplicada" | **"Informação passada ao tutor", "Resolvido fora do sistema (telefone, balcão)", "Solicitação repetida"** | Painel e planilha | Mais claros |

### 2.6 Tutores e pets

| Atual | Proposto | Onde |
|---|---|---|
| Buscar cliente · Nenhum cliente ainda · Escolha um cliente ou busque… · Falaram por último | **Buscar tutor · Nenhum tutor ainda · Escolha um tutor ou busque por nome, telefone ou pet · Falaram por último** | Tela |
| "abriu a ficha do cliente" | **"abriu a ficha do tutor"** | Registro de atividades |
| Busca do topo "Buscar tutor, telefone ou pet" | **mantém** | Topo |

### 2.7 Modos do assistente

O número continua (1, 2, 3) para a equipe da plataforma; a clínica lê o nome e a frase.

| Atual | Nome proposto | Frase de apoio (Início e Configurações) |
|---|---|---|
| Nível 1 · Organiza | **Só organiza** | "O assistente não conversa: registra a mensagem, separa por setor e informa o telefone da clínica. Quem responde é a equipe." |
| Nível 2 · Informa | **Tira dúvidas da clínica** | "Responde horário, serviços e preços cadastrados. Agendamentos e assuntos de saúde vão para a equipe." |
| Nível 3 · Completo | **Tira dúvidas e registra solicitações** | "Também coleta os dados do tutor e do pet e registra pedidos de agendamento. Não confirma horário nem dá orientação clínica." |

### 2.8 Configurações

| Atual | Proposto | Motivo |
|---|---|---|
| Visão geral | **Situação e publicação** | Diz o que a aba faz |
| Primeiros passos | **mantém** | |
| Clínica e horários | **mantém** | |
| Serviços e preços | **mantém** | |
| Perguntas frequentes | **mantém** | |
| Testar | **Testar o assistente** | A18 |
| Importar | **Importar dados** | A18 |
| Respostas prontas | **Respostas rápidas** | A16 |
| Canal WhatsApp | **WhatsApp** | Mais curto, sem jargão |
| Histórico | **Versões publicadas** | A17 |
| "O setor decide para qual fila vão os pedidos do serviço." | **"O setor decide quem recebe as solicitações sobre este serviço."** | A4, A11 |

### 2.9 Equipe, Registro de atividades e disponibilidade

| Atual | Proposto | Motivo |
|---|---|---|
| Disponível / Ausente, com a dica "Ausentes não recebem atribuições" | **Disponível / Ausente**, com a dica **"Mostra para a equipe se você está disponível."** até a E10 dar efeito à disponibilidade | P1: o texto atual promete o que o sistema não faz |
| "Sistema" como autor | **"Assistente ou sistema"** | A20 |
| Resumos com códigos (C2) | Em português: "de Urgente para Possível emergência", "Prioridade de atendimento: automática → alta", "Pendente → Cancelada (Solicitação repetida)", "Reclamação → Informação administrativa", "novo perfil: Secretária" | C2 |

### 2.10 Textos ao tutor (WhatsApp)

São textos provisórios (P-10), que a clínica ainda precisa aprovar. Proponho só o necessário para a consistência:

| Atual | Proposto | Motivo |
|---|---|---|
| "Seu pedido ficou registrado para a equipe da clínica." | **"Sua solicitação ficou registrada para a equipe da clínica."** | P2 |
| "Sua conversa foi encaminhada para o setor Recepção da clínica." | **"Sua conversa foi encaminhada para a equipe da clínica (setor: Recepção)."** | Lê bem com qualquer nome de setor ("setor Pet shop da clínica" fica estranho) |
| Os demais (recebimento, próximo passo, emergência, "Fulano (setor) vai continuar o seu atendimento por aqui") | **mantêm** | Já usam "conversa", "equipe" e "atendimento" no sentido do glossário e não dizem "avisei" |

**Nomes que aparecem também para o tutor:**
- nome do setor (no encaminhamento e no "vai continuar");
- nome do funcionário ("Fulano vai continuar");
- "solicitação" (agendamento registrado);
- "equipe da clínica";
- "recepção" (próximo passo).

Trocar o nome de um setor na tela Equipe muda o texto que o tutor lê. A tela de setores deve avisar isso.

---

## 3. Glossário

| Termo | Significa | Não usar para |
|---|---|---|
| **Tutor** | Pessoa responsável pelo animal, que conversa pelo WhatsApp | — (não usar "cliente" nas telas da operação) |
| **Pet** | Animal do tutor | — |
| **Conversa** | Troca de mensagens entre o tutor e a clínica | Solicitação |
| **Solicitação** | Algo que alguém da equipe precisa fazer ou responder: agendamento, dúvida, contato humano, reclamação, banho e tosa | Pedido de compra |
| **Pedido** | Reservado ao pedido de produto ou compra (pet shop, E6) | Solicitação em geral |
| **Agendamento** | Solicitação de horário; **só vale depois que a equipe confirma** | Horário confirmado pelo assistente |
| **Atendimento** | O trabalho de responder o tutor, feito pelo assistente ou por um funcionário | O nome de uma tela |
| **Responsável** | Funcionário que assumiu a conversa ou a solicitação | "Dono" |
| **Assumir** | Ficar responsável (conversa ou solicitação) | "Atribuir a mim", "pegar" |
| **Setor** | Área da clínica que recebe solicitações (Recepção, Clínica, Pet shop…) | "Fila" |
| **Assistente** | Assistente virtual da clínica | "Bot", "IA" nas telas da operação |
| **Risco clínico** | Possível emergência, urgente, acompanhamento clínico, sem sinal clínico ou não avaliado. Não é diagnóstico | Prioridade |
| **Prioridade de atendimento** | A ordem em que a equipe atende: imediata, alta, normal ou baixa | Risco |
| **Concluir** | Encerrar uma solicitação porque foi resolvida | "Resolver" |
| **Encerrar** | Fechar uma conversa | Concluir solicitação |
| **Modo do assistente** | O quanto o assistente faz na clínica (só organiza; tira dúvidas; tira dúvidas e registra solicitações) | "Nível" nas telas da clínica |

---

## 4. Regras de linguagem

1. Botão = verbo + objeto quando o objeto não for óbvio: "Assumir atendimento", "Transferir para outro setor", "Concluir ou cancelar".
2. Um conceito, um nome, em todas as telas, avisos, planilhas e registros (o glossário manda).
3. Nenhum código interno visível: enum, id, nome de campo, código de erro, variável de ambiente. Quando o código ajuda o suporte, ele vai na dica, nunca no texto principal.
4. Estados dizem **quem precisa agir** ou **quem está agindo**: "Aguardando atendimento", "Em atendimento por Ana", "Aguardando dados do tutor".
5. Estado vazio diz o que significa, sem julgamento: "Nenhuma conversa aguardando atendimento."
6. Nada promete o que o sistema não faz. Nem "avisamos", nem "você não recebe atribuições", nem "confirmado".
7. Clareza antes de economia: se o nome não couber no celular, a tela quebra a linha. Abreviação só com o nome inteiro na dica.
8. Siglas e termos técnicos ("mediana", "fila", "SLA", "token", "webhook") só nas telas técnicas (WhatsApp) e com explicação.
9. Erros dizem o que aconteceu e o que fazer: "Esta conversa já está em atendimento por Ana. Peça para ela transferir."
10. Português do Brasil, frases curtas, tratamento por "você".
11. Textos ao tutor seguem as regras do atendimento (sem "avisei", sem diagnóstico, sem confirmar horário) e passam pela aprovação da clínica (P-10).

---

## 5. Mapa de alterações no código

Nada aqui muda enum, coluna, rota ou regra.

| Bloco | Arquivos | O que muda | Tamanho |
|---|---|---|---|
| L1 · Rótulos centrais | `web/src/labels.ts` | Estados, resultados, selos, modos, tipos; função que combina estado e responsável da solicitação; tradução do prefixo `[CÓDIGO]` antigo (C1) | P |
| L2 · Menu e títulos | `App.tsx` (NAV, busca, disponibilidade P1), títulos das páginas | Seções 2.1 e 2.9 | P |
| L3 · Início | `pages/Home.tsx` | Seção 2.2 e modos (2.7) | P |
| L4 · Conversas | `pages/Inbox.tsx`, `components/Badges.tsx` | Seções 2.3 e 2.4, avisos | P |
| L5 · Solicitações | `pages/Requests.tsx`, `labels.ts` (resumo copiado) | Seção 2.5 | P |
| L6 · Tutores, Equipe, Registro de atividades, Configurações | `Customers.tsx`, `Team.tsx`, `Audit.tsx`, `Config.tsx`, `config/*.tsx` | Seções 2.6, 2.8, 2.9; aviso na tela de setores sobre o nome visto pelo tutor | P |
| L7 · Erros para pessoas | Backend: `GlobalExceptionHandler`, `InvalidStateTransitionException`, `ResourceNotFoundException`, `DomainValidationException` e mensagens de serviço; web: `api.ts` | `detail` sem estado interno, id nem campo; o detalhe técnico vai para o log. Mensagens de regra já em português ficam | M |
| L8 · Resumos da auditoria | Backend: quem grava `audit.record` com códigos | Resumo gravado em português (C2); os registros antigos ficam como estão, porque não se altera auditoria | P |
| L9 · Planilha | `RequestLabels.java` | "Concluída", resultados renomeados (2.5) | P |
| L10 · WhatsApp | `Inbox.tsx` (`DeliveryMark`), `config/channel.tsx` | Frases para os erros conhecidos (C4); servidor sem segredos (C5) | P |
| L11 · Textos ao tutor | `application.yml` (`register`, `handoff-to`); testes que conferem esses textos | Seção 2.10 | P |

Estimativa: 1,5 a 2 dias, com testes e conferência das telas.

---

## 6. Critérios de teste

1. **Glossário automatizado (web, Vitest):** varre os textos de `web/src` e falha se aparecer "sem dono", "Comigo", "pedido(s)" fora do contexto de compra, "cliente" nas telas da operação, ou um código em MAIÚSCULAS com `_` dentro de texto visível.
2. **Rótulo para todo valor:** os mapas de rótulo (`Record<…>`) já obrigam a ter rótulo para cada estado. Acrescentar o teste da função que combina estado e responsável, cobrindo os 5 estados × com e sem responsável.
3. **Textos ao tutor (backend):** o teste lê os textos do `application.yml` e falha se aparecer "pedido", "avisei", "notifiquei" ou "confirmado"; continua valendo a regra RN-S01.
4. **Erros sem código:** os testes de integração das ações que falham (responder sem assumir, setor inexistente) conferem que o `detail` não tem enum, id nem nome de campo.
5. **Auditoria sem código:** o resumo das ações novas não tem `_` em maiúsculas nem id.
6. **Conferência visual:** capturas em 1366 px e 390 px de Início, Conversas, Solicitações, Tutores e Configurações, com os textos novos sem corte.
7. **Leitura por quem não é técnico:** cinco perguntas a uma pessoa da clínica ("o que significa este selo?", "onde vejo o que é seu?"…). Depende da clínica e entra no teste de usabilidade (S3-12).

---

## 7. Decisões pendentes

| ID | Decisão | Recomendação |
|---|---|---|
| D-L1 | Aprovar os nomes das seções 2.1 a 2.9 | Aprovar como estão, ou indicar os que mudam |
| D-L2 | Criar o selo "tutor ainda não respondeu" (calculado, sem estado novo) | Depois do piloto, se a equipe sentir falta |
| D-L3 | Textos ao tutor (2.10) | Aplicar os dois ajustes agora; o texto final é da clínica (P-10) |
| D-L4 | "Registro de atividades" em vez de "Auditoria" | Recomendo trocar; "Auditoria" pode ficar como subtítulo para o gerente |
