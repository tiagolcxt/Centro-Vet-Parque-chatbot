# UX e navegação

> Versão 2 — 2026-11-19 (seção 8: protótipo visual do Lovable como referência). Versão 1 — 2026-11-18. Base: [PRD](prd.md) e DA-42 (filas como abas de Solicitações), DA-44 (React, responsivo).
> São wireframes em texto. O desenho visual vem na Etapa S2, validado com a clínica.

## 1. Mapa de navegação

O briefing propõe nove itens de menu (seção 11). A proposta aprovada (DA-42) junta Recepção, Clínica e Pet shop dentro de **Solicitações**, em abas por setor. Assim o menu tem seis itens e não há duas telas para a mesma fila.

```
Início ("o que eu preciso fazer agora")
Atendimento (caixa de entrada)
Solicitações  ── abas: Recepção | Clínica | Pet shop | (outros setores ativos)
Clientes e pets
Equipe                (só com team:manage)
Configurações         (só com config:*)
   ├─ Dados da clínica, Horários, Serviços e preços, Perguntas frequentes
   ├─ Setores e regras de encaminhamento
   ├─ Simulador
   ├─ Versões publicadas
   ├─ Canal (WhatsApp)
   └─ Auditoria       (só com audit:view)
```

**O menu se adapta à permissão.** Um tosador vê Início, Solicitações (só a aba Pet shop) e Clientes e pets (dados do pet).

**No celular:** barra inferior com Início, Atendimento e Solicitações. O resto fica em "Mais".

## 2. Telas principais

### Início

```
┌ Olá, Ana · Recepção ·  [● Disponível ▾] ─────────────────────────┐
│ URGENTES (2)                                                     │
│  🔴 P1  Rex (cão) · "não está respirando direito" · há 2 min     │
│  🟠 P2  Mia (gata) · "vomitou sangue"             · há 9 min     │
│ COMIGO (3)            AGUARDANDO FUNCIONÁRIO (4)   SEM DONO (5)  │
│  ...                   ...                          ...          │
│ Hoje: 1ª resposta média 40 s · 12 solicitações · 7 resolvidas    │
└──────────────────────────────────────────────────────────────────┘
```

A prioridade aparece com cor **e** texto (P1, P2), nunca só com cor (RNF-S11).

### Atendimento (caixa de entrada)

Três colunas no computador. No celular são três telas em sequência.

| Lista | Conversa | Painel |
|---|---|---|
| Filtros: meus setores, aguardando, comigo, todas. Cada item tem tutor, pet, prioridade, estado, setor, responsável e espera | Histórico, com as mensagens da IA marcadas "assistente", as da equipe com o nome da pessoa e as notas em amarelo. Barra de ações: **Assumir**, **Devolver ao assistente**, **Transferir**, **Encerrar**. Campo de resposta (só depois de assumir) e campo de nota | Tutor e pet com a proveniência de cada campo (ícone: mencionado, confirmado, cadastrado), solicitações da conversa, intenção e sinais do último turno |

**Estados vazios e mensagens de sistema:**

- "Fora da janela de 24 horas do WhatsApp: não é possível responder livremente" (RF-S53).
- "Envio falhou: tentando de novo" (RF-S55).

### Solicitações

Uma aba por setor ativo. Colunas:

- prioridade;
- tipo;
- tutor e pet;
- estado;
- responsável;
- desde quando;
- próxima ação.

A próxima ação é calculada pelo estado: "atribuir", "falta motivo", "encerrar com resultado".

Ações rápidas na linha: **atribuir a mim** e **transferir**.

O detalhe abre ao lado, com dados, histórico de eventos, notas e o link para a conversa.

### Clientes e pets

- Busca por nome, telefone ou nome do pet.
- A ficha tem abas: Dados, Pets, Conversas, Solicitações e Histórico.
- Exportar e eliminar (LGPD) só com a permissão, e com confirmação dupla.

## 3. Áreas de trabalho por perfil

| Perfil | Página inicial | O que vê |
|---|---|---|
| Secretária | Início | Atendimento e Solicitações dos seus setores; Clientes e pets |
| Veterinário | Solicitações → Clínica | Conversas encaminhadas à Clínica; notas clínicas |
| Atendente do pet shop e tosador | Solicitações → Pet shop | Dados do pet necessários ao serviço; sem notas clínicas |
| Gerente | Início, com todos os setores | Tudo, exceto publicar |
| Administrador | Início, com avisos de configuração | Tudo |

## 4. Assistente de configuração inicial (RF-S10)

Sete passos, com barra de progresso. Cada passo salva no rascunho e pode ser retomado.

1. **Dados da clínica:** nome, endereço, telefone da recepção, WhatsApp.
2. **Horários:** grade semanal (vários intervalos por dia), feriados com data, atendimento de emergência fora do horário.
3. **Setores:** Recepção (obrigatória); Clínica e Pet shop ligados por padrão e desligáveis; "Banho e tosa fica em qual setor?".
4. **Serviços e preços:** lista com setor; preço opcional; chave "divulgar preços"; "válido até".
5. **Perguntas frequentes:** sugestões prontas para responder ou pular (vacinas, formas de pagamento, estacionamento).
6. **Equipe:** convites, com perfil e setores.
7. **Revisão:**
   - checklist do que falta;
   - seções sem "válido até";
   - botão **Testar no simulador** e depois **Publicar**.

Cada campo desconhecido aceita "não sei / não informar". Isso grava DESCONHECIDO, e o assistente diz que não tem a informação (DA-03), em vez de inventar.

## 5. Edição e publicação

- O topo de Configurações mostra uma faixa fixa: "Você tem alterações não publicadas (3)", com **Ver diferenças**, **Testar** e **Publicar**.
- **Ver diferenças:** lista legível por seção ("Preço da consulta: R$ 150 → R$ 170"). Não é diff de JSON.
- **Versões publicadas:** número, quem publicou, quando e resumo das mudanças, com o botão **Restaurar como rascunho**.

## 6. Simulador (RF-S17)

```
┌ Simulador · usando o RASCUNHO · horário simulado: [agora ▾] ─────┐
│ Tutor (você): Quanto custa a consulta?                           │
│ Assistente:   A consulta custa R$ 170...                         │
│   ↳ intenção INFORMATION · P5 · setor: — · fontes: services.cons │
│ Tutor (você): quero marcar para meu cão                          │
│ Assistente:   ...                                                │
│   ↳ intenção APPOINTMENT · P4 · setor: Recepção (regra 2)        │
│ [Recomeçar] [Simular como clínica fechada]                       │
└──────────────────────────────────────────────────────────────────┘
```

- Nenhuma mensagem sai pelo WhatsApp.
- Nenhuma solicitação real é criada.
- A conversa simulada não entra nos indicadores.

## 7. Cadastro de funcionário

Formulário em uma tela:

- nome;
- e-mail;
- perfil, com a pré-visualização "este perfil pode: …";
- setores;
- cargo;
- registro (opcional).

Ao salvar, aparece "Convite enviado para …". A lista da Equipe mostra o estado de cada pessoa (convidado, ativo, desativado) e a disponibilidade.

## 8. Referência visual: protótipo do Lovable (2026-11-19)

O protótipo navegável feito no Lovable, a partir do resumo de UX, passa a ser a **referência visual** da aplicação web. É só front-end, com dados fictícios. Foi revisado no navegador em desktop (1440 px), em notebook (1024 px) e no celular (375 px).

### 8.1 O que adotamos

| Item | No protótipo | Situação no app (S2) |
|---|---|---|
| Barra lateral fixa, recolhível; topo com busca global, contador de urgentes, disponibilidade, tema e usuário | Sim | Menu no topo: **trocar** |
| Barra inferior no celular (Início, Atendimento, Solicitações, Mais) | Sim | Menu no topo que vira linha própria: **trocar** |
| Início: faixa de urgentes com motivo e espera; três cartões com número grande ("Comigo", "Aguardando a equipe", "Pedidos sem dono"); indicadores do dia | Sim | Mesma estrutura, visual mais simples; indicadores na S5 |
| Atendimento em 3 colunas no desktop; painel do tutor vira gaveta em telas médias; uma coluna por vez no celular | Sim | 3 colunas; o painel cai para baixo da conversa em telas médias: **trocar pela gaveta** |
| Lista com selo de prioridade em texto, motivo, estado, espera e destaque "passou do prazo" | Sim | Equivalente |
| Um único botão primário (Assumir); Encerrar discreto | Sim | Vários botões com o mesmo peso: **ajustar** |
| Campo de resposta que vira "Para responder, assuma a conversa" + Assumir | Sim | Só o texto: **acrescentar o botão** |
| Transferir em painel na própria conversa: setor em chips + motivo obrigatório (2 cliques + motivo) | Sim | Formulário com lista suspensa: **trocar por chips** |
| Toast de confirmação ("Conversa assumida") | Sim | Não tem: **acrescentar** |
| Proveniência como "confirmado" ao lado do dado | Sim | Na ficha do cliente, em texto: **trazer para o painel** |
| Notas internas em fundo amarelo-claro, com "Só a equipe vê" | Sim | Equivalente |
| Atalhos de teclado (/, J/K, A, R, T, N, Esc, ?) e diálogo de atalhos | Sim | Não tem: **acrescentar** (S3) |
| Modo escuro com alternância manual | Sim | Segue só o sistema: **acrescentar a alternância** |

### 8.2 O que o protótipo ainda não cobre

O protótipo serve de referência só para Início e Atendimento. Até o Lovable gerar o resto, as demais telas seguem as seções 2 a 7.

- **Solicitações** e **Clientes e pets** dão erro 404.
- **Configurações** e as telas de login e definição de senha não existem.
- Os estados de carregando, vazio e erro só aparecem na lista vazia.

### 8.3 Divergências que o app NÃO copia

1. **Texto do assistente.** No dado fictício, o assistente diz "Vou chamar alguém da equipe agora". Isso promete contato e contraria a regra de nunca dizer que alguém foi avisado (RN-S01, ADR-0007). No app, os textos continuam os do backend.
2. **Menu por perfil.** Ao trocar para "Bia · Tosadora", o menu continua mostrando Atendimento. No app, o menu segue as permissões, como já faz: o tosador não tem `conversation:view`.
3. **Transferir e Devolver antes de assumir.** No protótipo, os dois ficam desabilitados até alguém assumir. No app, uma conversa aguardando a equipe pode ser transferida ou devolvida sem assumir, como a recepção faz na triagem. Os botões ficam habilitados.
4. **Contador de urgentes.** Some quando a urgência é assumida. No app, o contador mostra P1/P2 **sem responsável**. As urgências assumidas aparecem em "Comigo", ainda destacadas.
5. **Trocador de perfil no topo.** É um recurso de demonstração do protótipo e não entra no app: o perfil vem do login.

### 8.4 Plano de adoção

O visual é aplicado no front-end existente (`web/`), sem mudar a API. É uma etapa de interface, com aceite próprio.

1. **Estrutura:**
   - barra lateral recolhível;
   - topo com busca global (atalho `/`), contador de urgentes sem dono, disponibilidade, tema claro ou escuro e usuário;
   - barra inferior no celular.
2. **Componentes:**
   - selos de prioridade e de estado com ícone e texto;
   - botões com hierarquia (um primário por área);
   - chips, toast, gaveta lateral e diálogo de atalhos.
3. **Atendimento:**
   - Assumir como único botão primário, com Encerrar discreto;
   - botão Assumir no lugar do campo de resposta;
   - transferência por chips;
   - painel do tutor em gaveta abaixo de 1280 px;
   - "confirmado" ao lado dos dados.
4. **Atalhos de teclado:** J/K, A, R, T, N, Esc e ?.
5. **Busca global:** usa `GET /app/api/customers?q=`, que já existe.

Solicitações, Clientes e Configurações recebem o mesmo sistema visual. Vale pedir ao Lovable essas telas também, para manter a referência completa.

### 8.5 Implementação (2026-11-19)

O visual do protótipo foi aplicado ao front-end (`web/`), sem mudar a API.

- **Cores.** Tokens copiados do protótipo (OKLCH), com tema claro e escuro. O tema segue o sistema até a pessoa escolher; a escolha fica no navegador. Ícones da biblioteca lucide-react 1.68.0.
- **Estrutura:**
  - barra lateral recolhível, que vira só ícones abaixo de 1024 px;
  - topo com busca global (atalho `/`, resultados enquanto digita), contador de urgentes, disponibilidade, tema e menu do usuário;
  - barra inferior com "Mais" abaixo de 768 px.
- **Contador de urgentes.** Conta as P1 e P2 aguardando a equipe, sem responsável (divergência 4).
- **Atendimento:**
  - "Assumir" é o único botão primário;
  - "Transferir" e "Devolver" ficam habilitados também em "Aguardando a equipe" (divergência 3);
  - transferência por botões de setor, sem o setor atual, com motivo obrigatório;
  - prioridade escolhida em botões;
  - campo de resposta com foco ao assumir;
  - painel do tutor em gaveta abaixo de 1280 px;
  - avisos de confirmação no canto superior direito.
- **Atalhos:** `/`, J/K, A, R, T, N, Esc e `?`. Não valem dentro de campos de texto, exceto Esc.
- **Solicitações:**
  - "Atribuir a mim" direto na linha;
  - filtros e resultado do encerramento em botões;
  - histórico em linha do tempo.
- **Conferido no navegador** com dados fictícios, em 1440 px e 375 px:
  - Início no tema escuro;
  - abrir a urgência pelo topo;
  - assumir com A;
  - transferir;
  - Solicitações;
  - gaveta no celular.

O teste revelou e corrigiu três coisas: a Recepção aparecia como destino da transferência, o foco não ia para o motivo e o aviso cobria o campo de resposta.
