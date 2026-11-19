# UX e navegação

> Versão 1 — 2026-11-18. Base: [PRD](prd.md) e DA-42 (filas como abas de Solicitações), DA-44 (React, responsivo).
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
